# Keep everything JNI calls into by name/signature - R8 renaming breaks native lookups.
-keepclasseswithmembers class com.rynime.nvplayer.rife.RifeInterpolator {
    native <methods>;
}
-keep class com.rynime.nvplayer.rife.RifeConfig { *; }
-keep class com.rynime.nvplayer.rife.RifeModel { *; }
-keep class com.rynime.nvplayer.rife.RifeScale { *; }
