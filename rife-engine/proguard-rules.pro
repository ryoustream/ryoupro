# Keep everything JNI calls into by name/signature - R8 renaming breaks native lookups.
-keepclasseswithmembers class com.devson.nvplayer.rife.RifeInterpolator {
    native <methods>;
}
-keep class com.devson.nvplayer.rife.RifeConfig { *; }
-keep class com.devson.nvplayer.rife.RifeModel { *; }
-keep class com.devson.nvplayer.rife.RifeScale { *; }
