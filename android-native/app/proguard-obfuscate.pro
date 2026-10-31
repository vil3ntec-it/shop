#  مبهم‌سازیِ کدِ خودِ برنامه — فقط با `-Ptohid.obfuscate=true` (پیش‌فرض خاموش؛ SEC-CLI، ۱۴۰۵/۰۸/۰۹).
#
#  ⛔ امنیت از این نمی‌آید: هر تصمیمِ اشتراک، پول و دسترسی در سرور است و برنامهٔ مبهم هم با
#  کمی وقت (و هوشِ مصنوعی) خوانده می‌شود. این فقط هزینهٔ خواندن را بالا می‌برد.
#  ⚠️ پیش از روشن کردن برای کاربران (کارِ صاحب محصول): ساختِ preview با این ویژگی، نصب روی نسخهٔ
#  پیشین (همان دفتر، همان پشتیبان)، بازیابیِ پشتیبانِ کهنه، همگام‌سازی، چاپ، اسکن و ورود؛ و نگه داشتنِ
#  `mapping.txt` هر نسخه (بی آن گزارشِ خطا خواندنی نیست). تا این سنجیده نشده: NOT-TESTED.
#
#  آن‌چه با نام پیدا می‌شود و باید بماند:
#   • kotlinx.serialization: نامِ کلیدهای JSON در زمانِ کامپایل در سریال‌ساز نوشته می‌شود (نه از نامِ
#     فیلد)، پس تغییرِ نامِ فیلد دفتر را نمی‌شکند؛ ولی خودِ سریال‌سازها با بازتاب پیدا می‌شوند.
#     کتابخانه (۱.۷) قاعدهٔ R8ِ خودش را دارد؛ این‌جا صریح هم نوشته شده، برای همهٔ بسته‌ها نه فقط `data`.
#   • مؤلفه‌های مانیفست (Activity، BackupAgent، FileProvider) را AAPT خودش نگه می‌دارد؛ WorkManager هم.
-keepclassmembers @kotlinx.serialization.Serializable class ir.vil3ntec.tohid.** {
  *** Companion;
  kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class ir.vil3ntec.tohid.**$$serializer { *; }
-keepclassmembers enum ir.vil3ntec.tohid.** { *; }

#  ردِ خطا با شمارهٔ خط (با mapping.txt)، بی نامِ پروندهٔ واقعی
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
