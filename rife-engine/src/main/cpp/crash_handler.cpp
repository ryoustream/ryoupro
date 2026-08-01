// Minimal native crash diagnostics. A Java Thread.UncaughtExceptionHandler
// (see NativeTrace.kt's Kotlin-side counterpart) can NEVER see a SIGSEGV/
// SIGABRT in native code - those terminate the process at the OS level,
// bypassing the JVM entirely. This registers signal handlers that write a
// backtrace to a plain file before the process dies, so it's readable via
// any file manager / adb pull without needing live logcat.
//
// Deliberately minimal: only async-signal-safe calls inside the handler
// (write(), backtrace_symbols_fd() - no malloc, no iostream, no
// __android_log_print which is not guaranteed signal-safe). The fd is
// pre-opened at JNI_OnLoad time, before any crash could happen.

#include <jni.h>
#include <android/log.h>
#include <csignal>
#include <cstdlib>
#include <cstring>
#include <execinfo.h>
#include <fcntl.h>
#include <unistd.h>

#define LOG_TAG "RifeCrashHandler"

namespace {

int g_crashLogFd = -1;
struct sigaction g_prevHandlers[NSIG];

void writeStr(const char* s) {
    if (g_crashLogFd >= 0) {
        write(g_crashLogFd, s, strlen(s));
    }
}

const char* signalName(int sig) {
    switch (sig) {
        case SIGSEGV: return "SIGSEGV (segmentation fault)";
        case SIGABRT: return "SIGABRT (abort)";
        case SIGBUS:  return "SIGBUS (bus error)";
        case SIGFPE:  return "SIGFPE (floating point exception)";
        case SIGILL:  return "SIGILL (illegal instruction)";
        default:      return "unknown signal";
    }
}

void crashHandler(int sig, siginfo_t* info, void* context) {
    writeStr("\n==== rife_engine native crash ====\n");
    writeStr(signalName(sig));
    writeStr("\n");

    void* frames[64];
    const int frameCount = backtrace(frames, 64);
    if (g_crashLogFd >= 0) {
        writeStr("--- backtrace ---\n");
        backtrace_symbols_fd(frames, frameCount, g_crashLogFd);
    }
    writeStr("==== end crash report ====\n");
    if (g_crashLogFd >= 0) {
        fsync(g_crashLogFd);
    }

    // Chain to whatever handler (if any) was installed before us - usually
    // Android's own debuggerd, so a normal tombstone still gets a chance to
    // happen too if the device's logging pipeline is actually working.
    if (sig >= 0 && sig < NSIG && g_prevHandlers[sig].sa_sigaction != nullptr) {
        sigaction(sig, &g_prevHandlers[sig], nullptr);
        raise(sig);
    } else {
        _exit(1);
    }
}

void installHandler(int sig) {
    struct sigaction sa{};
    sa.sa_sigaction = crashHandler;
    sa.sa_flags = SA_SIGINFO;
    sigemptyset(&sa.sa_mask);
    sigaction(sig, &sa, &g_prevHandlers[sig]);
}

} // namespace

extern "C" void rife_install_crash_handler(const char* logFilePath) {
    if (g_crashLogFd < 0 && logFilePath != nullptr) {
        g_crashLogFd = open(logFilePath, O_WRONLY | O_CREAT | O_APPEND, 0644);
    }
    installHandler(SIGSEGV);
    installHandler(SIGABRT);
    installHandler(SIGBUS);
    installHandler(SIGFPE);
    installHandler(SIGILL);
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Native crash handler installed (fd=%d)", g_crashLogFd);
}
