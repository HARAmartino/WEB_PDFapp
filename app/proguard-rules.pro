# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# JavascriptInterface メソッドを ProGuard から保護する
# @JavascriptInterface アノテーションだけでは不十分なため、明示的に keep する
-keepclassmembers class com.example.printedit.ui.ImageLoadInterface {
   @android.webkit.JavascriptInterface public *;
}

# Keep WebView related classes to prevent obfuscation issues
-keep class android.webkit.WebView { *; }
-keep class android.webkit.WebViewClient { *; }
-keep class android.webkit.WebChromeClient { *; }
-keep class android.webkit.JavascriptInterface { *; }

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile