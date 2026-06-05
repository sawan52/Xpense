# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Preserve line numbers for readable crash stack traces from release builds.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room maps DB columns onto these data classes by field; keep them intact so R8 can't
# rename/strip fields the generated Room code relies on. (Room ships most rules itself;
# this is belt-and-suspenders for the entity model.)
-keep class com.example.xpense.data.entity.** { *; }