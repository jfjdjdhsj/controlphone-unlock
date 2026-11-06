# Loaded by class name from META-INF/xposed/java_init.list inside the target
# process, so R8 must not rename or strip it.
-keep class io.github.jfjdjdhsj.controlphone.ModuleMain { *; }

# The Xposed API is compileOnly: LSPosed provides it inside the target process.
-dontwarn io.github.libxposed.**

# Readable stack traces in logcat for the module UI.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
