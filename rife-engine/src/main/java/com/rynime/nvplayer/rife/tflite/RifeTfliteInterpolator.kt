package com.rynime.nvplayer.rife.tflite

import android.content.Context
import android.util.Log
import com.rynime.nvplayer.rife.NativeTrace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "RifeTfliteInterpolator"
private const val MODEL_ASSET_PATH = "rife_tflite_models/rife-v4.26/flownet.tflite"

// Matches Practical-RIFE's own inference_video.py: tmp = max(128, 128/scale).
// This project only ever runs at scale=1.0 (RifeScale.X2, the only mode the
// TFLite path supports for now), so the padding target is always 128.
// Confirmed empirically: the exported graph was traced/validated at
// multiples of 128 - see the model conversion notes. Note this differs
// from the OLD ncnn path, which padded to 32 internally inside
// rife-ncnn-vulkan's own C++ - that library handled it itself; here padding
// is entirely this class's responsibility since IFNet's forward() does no
// padding of its own (confirmed by reading train_log/IFNet_HDv3.py - no
// pad() calls anywhere in it).
private const val PAD_MULTIPLE = 128

/**
 * RIFE v4.26 frame interpolation via TFLite, instead of the native
 * ncnn/Vulkan RifeInterpolator.
 *
 * Why this exists: RIFE::process() (the ncnn/Vulkan path - both nihui's
 * original and TNTwise's actively-maintained fork) was confirmed via
 * native_trace.txt to silently return an empty output Mat (w=0 h=0 c=0)
 * on a real device (TECNO LH8n / MediaTek) while still reporting success
 * (rc=0), on every single frame. Forcing rife-ncnn-vulkan's own CPU path
 * as a diagnostic crashed outright (SIGSEGV, null pointer) instead of
 * working around it. Both are inside vendored third-party C++ this
 * project doesn't own. This backend sidesteps that code entirely: it's a
 * completely different runtime (TFLite) running a model converted
 * straight from the original PyTorch weights, not going through ncnn or
 * Vulkan compute at all.
 *
 * Acceleration: CPU (XNNPACK, TFLite's default) only, for now - not
 * NNAPI, not GPU delegate. NNAPI is deprecated as of Android 15 and is a
 * separate, not-yet-attempted risk surface; TFLite's own GPU delegate
 * would very plausibly route through the same class of GPU compute path
 * (OpenGL ES / Vulkan compute) that's already suspected of being the
 * actual root cause here, which would risk reproducing the exact bug
 * this backend exists to avoid. CPU is slower but is a correctness
 * guarantee on any device - RIFE's ~5.6M parameters make that tractable.
 * Revisit acceleration once this baseline is confirmed working
 * end-to-end on the reporter's device.
 *
 * Model provenance: converted from the official
 * train_log/flownet.pkl (hzwer/Practical-RIFE v4.26 release) via
 * PyTorch -> ONNX -> TFLite. Verified against the PyTorch reference
 * before being bundled (see conversion notes in the commit history) -
 * matches within numerical noise (~0.00001-0.00003 max per-pixel diff on
 * [0,1]-range output) across multiple resolutions, including non-square
 * ones, with zero custom/Flex ops in the final graph.
 */
class RifeTfliteInterpolator private constructor(
    private val interpreter: Interpreter,
    private val appContext: Context,
) {
    // TFLite Interpreter instances are not thread-safe (see upstream
    // Interpreter.java's own class-level warning) and resizeInput() +
    // allocateTensors() + run() must happen as one atomic sequence per
    // call, since two frames' resize calls could otherwise interleave.
    private val lock = Any()

    private var reusableInputA: ByteBuffer? = null
    private var reusableInputB: ByteBuffer? = null
    private var reusableOutput: ByteBuffer? = null
    private var allocatedPw = -1
    private var allocatedPh = -1

    /**
     * frameA/frameB: tightly-packed NV12 ByteBuffers (Y plane, then
     * interleaved U,V - the same layout RifeExportWorker's
     * imageToNv12()/nv12ToImage() already use for the ncnn backend),
     * width*height*3/2 bytes each, fixed at t=0.5 (this backend only
     * supports RifeScale.X2 for now, matching the model it was traced
     * for). Returns a same-sized NV12 ByteBuffer with the interpolated
     * frame - matches RifeInterpolator.interpolate()'s NV12-in/NV12-out
     * contract so RifeExportWorker's surrounding pipeline (feedEncoder,
     * muxing, PTS math) doesn't need to change for this backend.
     */
    suspend fun interpolate(frameA: ByteBuffer, frameB: ByteBuffer, width: Int, height: Int): ByteBuffer =
        withContext(Dispatchers.Default) {
            val pw = padTo128(width)
            val ph = padTo128(height)

            synchronized(lock) {
                ensureBuffersAllocated(pw, ph)
                val inputA = requireNotNull(reusableInputA)
                val inputB = requireNotNull(reusableInputB)
                val output = requireNotNull(reusableOutput)

                nv12ToPaddedNhwcFloat(frameA, width, height, pw, ph, inputA)
                nv12ToPaddedNhwcFloat(frameB, width, height, pw, ph, inputB)

                if (allocatedPw != pw || allocatedPh != ph) {
                    interpreter.resizeInput(0, intArrayOf(1, ph, pw, 3))
                    interpreter.resizeInput(1, intArrayOf(1, ph, pw, 3))
                    interpreter.allocateTensors()
                    allocatedPw = pw
                    allocatedPh = ph
                }

                inputA.rewind()
                inputB.rewind()
                output.rewind()
                interpreter.runForMultipleInputsOutputs(
                    arrayOf(inputA, inputB),
                    mapOf(0 to output),
                )

                paddedNchwFloatToNv12(output, width, height, pw, ph)
            }
        }

    fun release() {
        interpreter.close()
        reusableInputA = null
        reusableInputB = null
        reusableOutput = null
    }

    private fun ensureBuffersAllocated(pw: Int, ph: Int) {
        if (allocatedPw == pw && allocatedPh == ph && reusableInputA != null) return
        val floatBytes = 1L * ph * pw * 3 * 4
        require(floatBytes < Int.MAX_VALUE) { "Padded frame too large for a single ByteBuffer: ${pw}x$ph" }
        reusableInputA = ByteBuffer.allocateDirect(floatBytes.toInt()).order(ByteOrder.nativeOrder())
        reusableInputB = ByteBuffer.allocateDirect(floatBytes.toInt()).order(ByteOrder.nativeOrder())
        reusableOutput = ByteBuffer.allocateDirect(floatBytes.toInt()).order(ByteOrder.nativeOrder())
    }

    companion object {
        private fun padTo128(dim: Int): Int = ((dim - 1) / PAD_MULTIPLE + 1) * PAD_MULTIPLE

        suspend fun create(context: Context): RifeTfliteInterpolator? =
            withContext(Dispatchers.IO) {
                val appContext = context.applicationContext
                try {
                    NativeTrace.mark(appContext, "RifeTfliteInterpolator.create: BEFORE")
                    val modelBytes = appContext.assets.open(MODEL_ASSET_PATH).use { it.readBytes() }
                    val modelBuffer = ByteBuffer.allocateDirect(modelBytes.size).order(ByteOrder.nativeOrder())
                    modelBuffer.put(modelBytes)
                    modelBuffer.rewind()

                    val options = Interpreter.Options().apply {
                        // XNNPACK (CPU) only - see the class doc for why NNAPI/GPU
                        // delegate aren't used yet. 4 threads is a reasonable
                        // default for a mid-range phone's CPU without needing to
                        // query core count; RifeConfig.numThreads isn't threaded
                        // through here yet since this backend doesn't take a
                        // RifeConfig at all (fixed to the one model/scale it was
                        // converted for).
                        setNumThreads(4)
                    }
                    val interpreter = Interpreter(modelBuffer, options)
                    NativeTrace.mark(appContext, "RifeTfliteInterpolator.create: AFTER (CPU/XNNPACK)")
                    RifeTfliteInterpolator(interpreter, appContext)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create TFLite RIFE interpreter", e)
                    NativeTrace.mark(appContext, "RifeTfliteInterpolator.create: FAILED - ${e.message}")
                    null
                }
            }
    }
}

/**
 * Reads a tightly-packed NV12 buffer (as produced by
 * RifeExportWorker.imageToNv12()) and writes it into [dst] as padded
 * NHWC float32 RGB in [0,1] - the layout/range TFLite's Interpreter
 * expects for this model's inputs (input_details showed [1,H,W,3] with
 * channel-last layout after onnx2tf's NCHW->NHWC conversion; range
 * confirmed against the PyTorch reference during conversion, which
 * outputs [0,1] directly with no separate normalization step).
 * Padding (dst dims pw x ph, always >= src width/height) is zero-filled,
 * matching Practical-RIFE's own F.pad(img, padding) default of zeros.
 * YUV->RGB uses the standard BT.601 full-range matrix.
 */
private fun nv12ToPaddedNhwcFloat(
    nv12: ByteBuffer, width: Int, height: Int, pw: Int, ph: Int, dst: ByteBuffer,
) {
    nv12.rewind()
    val ySize = width * height
    val yBytes = ByteArray(ySize)
    nv12.get(yBytes)
    val uvBytes = ByteArray(ySize / 2)
    nv12.get(uvBytes)
    val chromaWidth = width / 2

    // Only the real width x height region is written; the padding strip
    // (up to pw/ph) is left as-is. ByteBuffer.allocateDirect() is
    // guaranteed zero-initialized, and since a single RifeExportWorker
    // run processes one video at one fixed resolution, this buffer is
    // only (re)allocated once per export - the padding region never
    // needs re-zeroing between frames.
    for (row in 0 until height) {
        val chromaRow = row / 2
        val rowBase = row * width
        val chromaRowBase = chromaRow * chromaWidth * 2
        val dstRowOffset = row * pw * 3 * 4
        for (col in 0 until width) {
            val yVal = yBytes[rowBase + col].toInt() and 0xFF
            val chromaCol = col / 2
            val u = uvBytes[chromaRowBase + chromaCol * 2].toInt() and 0xFF
            val v = uvBytes[chromaRowBase + chromaCol * 2 + 1].toInt() and 0xFF

            val yF = yVal.toFloat()
            val uF = (u - 128).toFloat()
            val vF = (v - 128).toFloat()
            val r = (yF + 1.402f * vF).coerceIn(0f, 255f) / 255f
            val g = (yF - 0.344136f * uF - 0.714136f * vF).coerceIn(0f, 255f) / 255f
            val b = (yF + 1.772f * uF).coerceIn(0f, 255f) / 255f

            val pixelOffset = dstRowOffset + col * 3 * 4
            dst.putFloat(pixelOffset, r)
            dst.putFloat(pixelOffset + 4, g)
            dst.putFloat(pixelOffset + 8, b)
        }
    }
    dst.rewind()
}

/**
 * Reverse of [nv12ToPaddedNhwcFloat]: reads the model's output tensor and
 * crops back to the original width/height (dropping the padding this
 * class added before inference), writing a tightly-packed NV12
 * ByteBuffer matching what RifeExportWorker's feedEncoder()/
 * nv12ToImage() already expect.
 *
 * LAYOUT NOTE: unlike the inputs (NHWC, confirmed via input_details after
 * resize), the output tensor is NCHW - [1,3,ph,pw], channel-planar, not
 * channel-last. Confirmed empirically during model conversion (Python:
 * interp.get_tensor(...).shape read back as (1,3,H,W) after invoke(),
 * consistently across every resolution tested), not assumed from the
 * input's layout - onnx2tf apparently preserved the original ONNX
 * graph's NCHW output shape metadata even though it converts internal
 * computation (and inputs) to NHWC. Indexing this as if it were NHWC
 * would silently read the wrong bytes for every pixel after the first.
 */
private fun paddedNchwFloatToNv12(src: ByteBuffer, width: Int, height: Int, pw: Int, ph: Int): ByteBuffer {
    src.rewind()
    val out = ByteBuffer.allocateDirect(width * height * 3 / 2).order(ByteOrder.nativeOrder())
    val yPlane = ByteArray(width * height)
    val chromaWidth = width / 2
    val chromaHeight = height / 2
    val uvPlane = ByteArray(chromaWidth * chromaHeight * 2)

    val channelStride = ph * pw * 4 // bytes per channel plane
    val rPlaneBase = 0
    val gPlaneBase = channelStride
    val bPlaneBase = channelStride * 2

    for (row in 0 until height) {
        val rowByteOffset = row * pw * 4
        val yRowBase = row * width
        for (col in 0 until width) {
            val colByteOffset = col * 4
            val r = src.getFloat(rPlaneBase + rowByteOffset + colByteOffset) * 255f
            val g = src.getFloat(gPlaneBase + rowByteOffset + colByteOffset) * 255f
            val b = src.getFloat(bPlaneBase + rowByteOffset + colByteOffset) * 255f

            val y = (0.299f * r + 0.587f * g + 0.114f * b).coerceIn(0f, 255f)
            yPlane[yRowBase + col] = y.toInt().toByte()

            // Subsample chroma at even row/col - matches how imageToNv12
            // reads chroma on the way in (chroma plane is already
            // half-resolution for 4:2:0, no separate averaging needed
            // beyond picking the co-located sample).
            if (row % 2 == 0 && col % 2 == 0) {
                val u = (-0.168736f * r - 0.331264f * g + 0.5f * b + 128f).coerceIn(0f, 255f)
                val v = (0.5f * r - 0.418688f * g - 0.081312f * b + 128f).coerceIn(0f, 255f)
                val chromaRow = row / 2
                val chromaCol = col / 2
                val idx = (chromaRow * chromaWidth + chromaCol) * 2
                uvPlane[idx] = u.toInt().toByte()
                uvPlane[idx + 1] = v.toInt().toByte()
            }
        }
    }

    out.put(yPlane)
    out.put(uvPlane)
    out.rewind()
    return out
}
