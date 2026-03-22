# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# GMS Ads SDK が API 35 クラスを参照するが minSdk では存在しないため警告を抑制する
-dontwarn android.media.LoudnessCodecController
-dontwarn android.media.LoudnessCodecController$OnLoudnessCodecUpdateListener

# JavascriptInterface メソッドを ProGuard から保護する
# @JavascriptInterface アノテーションだけでは不十分なため、明示的に keep する
-keepclassmembers class jp.webpdf.app.ui.ImageLoadInterface {
   @android.webkit.JavascriptInterface public *;
}

# Keep WebView related classes to prevent obfuscation issues
-keep class android.webkit.WebView { *; }
-keep class android.webkit.WebViewClient { *; }
-keep class android.webkit.WebChromeClient { *; }
-keep class android.webkit.JavascriptInterface { *; }

# スタックトレースでファイル名・行番号を保持する（クラッシュ解析用）
-keepattributes SourceFile,LineNumberTable

# 難読化後もソースファイル名を SourceFile として統一表示
-renamesourcefileattribute SourceFile

# 例外情報を保持する
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod