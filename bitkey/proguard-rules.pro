# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# The bitkey module exposes interfaces/data classes that are reflected on by the
# DI framework. Hilt-generated code uses code generation rather than reflection,
# but keep the public-facing surface to be safe under consumer minification.
-keep class com.bitwarden.bitkey.protocol.** { *; }
-keep class com.bitwarden.bitkey.model.** { *; }
