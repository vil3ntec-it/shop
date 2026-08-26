# پل جاوااسکریپت به اندروید (ذخیره‌ی فایل پشتیبان) نباید نامش عوض شود
-keepclassmembers class af.tohid.shop.MainActivity$SaveBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# OkHttp — هشدارهای بی‌اثر
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
