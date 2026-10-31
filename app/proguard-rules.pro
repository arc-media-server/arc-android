# libass (ass-media) calls back into its Kotlin classes from native code: keep them whole.
-keep class io.github.peerless2012.ass.** { *; }
-keepclasseswithmembernames class * { native <methods>; }

# Crash reports stay readable: line numbers, with the source file name hidden.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
