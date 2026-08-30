# JSON با org.json خوانده می‌شود که داخل خود اندروید است — چیزی برای نگه داشتن نیست.
-keepattributes SourceFile,LineNumberTable

#  کتابخانه‌ی رمزگذاری (Tink، زیرِ security-crypto) به چند حاشیه‌نویسیِ
#  زمانِ کامپایل اشاره می‌کند که اصلاً در برنامه نیستند و لازم هم نیستند.
#  R8 نبودشان را خطا می‌گیرد؛ این دو خط می‌گویند «می‌دانیم، مهم نیست».
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn javax.annotation.concurrent.**
