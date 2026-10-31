#  کوچک کردنِ برنامه پیش از ساخت.
#
#  هدف فقط حجم است: کتابخانه‌هایی مثل Compose و CameraX و ML Kit بخشِ
#  بزرگی دارند که این برنامه اصلاً صدا نمی‌زند و بی‌جهت در فایلِ نصبی
#  می‌نشیند — و هر بار روی اینترنتِ کاربر دانلود می‌شود.
#
#  ولی کدِ خودِ برنامه دست‌نخورده می‌ماند — آن قاعده (`-keep class ir.vil3ntec.tohid.**`) از
#  SEC-CLI (۱۴۰۵/۰۸/۰۹) در `proguard-keep-app.pro` است و `build.gradle.kts` آن را می‌افزاید مگر
#  `-Ptohid.obfuscate=true` (پیش‌فرض خاموش ⇒ همین قاعده‌ها، بی هیچ تغییری). شرح: `proguard-obfuscate.pro`.

#  سریال‌سازها هم همین‌طور — این‌ها با بازتاب پیدا می‌شوند، نه با صدا زدن
-keepclassmembers class ir.vil3ntec.tohid.data.** {
  *** Companion;
  kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class ir.vil3ntec.tohid.data.**$$serializer { *; }

#  هشدارهای کتابخانه‌ها دربارهٔ کلاس‌هایی که در اندروید نیستند
-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**
