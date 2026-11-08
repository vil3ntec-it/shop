package ir.vil3ntec.tohid.ios.prefs

/**
 *  نوعِ اندرویدیِ هر مقدارِ تنظیم در آیفون — پشتِ `SharedPreferences.all` ِ shim
 *  (‎iosMain/…/android/content/SharedPreferences.kt‎). خالص و جدا، تا روی لینوکس آزموده شود (`jvmTest/…/PrefTypesJvmTest`).
 *
 *  ── اشکالی که این می‌بندد (DATA-SAFETY، ۱۴۰۵/۰۸/۱۸) ──────────────────────
 *  `all` ِ shim هر NSNumber را **متن** برمی‌گرداند (`stringValue`). کدِ مشترک نوع را از همین می‌خواند:
 *  `ShopSettingsSync.readLocal` (`ShopSettingsKeys.encode` فقط `Boolean`/`Long`/`Int`/`Float` ِ واقعی را می‌پذیرد) هر
 *  تنظیمِ بولی و عددیِ دکان را «نیست» می‌دید و صاحب/مدیر روی آیفون برای همه‌شان «برداشتن» به سرور می‌فرستاد — همهٔ
 *  دستگاه‌های دکان به پیش‌فرض برمی‌گشتند؛ `ShopSettingsBundle.export` همان‌ها را `"t":"s"` در پشتیبان می‌گذاشت و
 *  بازیابیِ آن روی اندروید (`CloudSettingsRestore`) `putString` می‌کرد ⇒ `getBoolean`/`getLong` ِ اندروید
 *  `ClassCastException`. NSUserDefaults نوعِ Int و Long را جدا نگه نمی‌دارد؛ نوعِ اعلام‌شدهٔ کلید ([declared]، همان
 *  `ShopSettingsKeys.Key.type`: `b`، `s`، `i`، `l`، `f`، `set`) جدایشان می‌کند.
 *  ──────────────────────────────────────────────────────────────────────────
 */
object PrefTypes {

  /**
   *  NSNumber ⇒ همان نوعِ اندروید.
   *  @param objcType `objCType` ِ همان NSNumber: `c`/`B` بولی (CFBoolean ِ `setBool` و `<true/>` ِ plist)، `f`/`d` اعشاری
   *    (`setFloat` و `<real/>`)، بقیه عددِ صحیح (`setInteger` و `<integer/>`)
   *  @param declared نوعِ اعلام‌شدهٔ کلید یا `null`
   */
  fun number(objcType: String?, declared: String?, asLong: Long, asDouble: Double, asBool: Boolean): Any = when (objcType) {
    "c", "B" -> asBool
    "f", "d" -> asDouble.toFloat()
    else -> integer(asLong, declared)
  }

  /** عددِ صحیح: `Int` فقط برای کلیدِ اعلام‌شدهٔ `i` و در بازهٔ Int؛ وگرنه `Long` (NSInteger ِ ۶۴ بیتیِ آیفون) */
  fun integer(value: Long, declared: String?): Any =
    if (declared == "i" && value >= Int.MIN_VALUE && value <= Int.MAX_VALUE) value.toInt() else value

  /**
   *  هر مقدارِ دیگرِ NSUserDefaults که Kotlin/Native به نوعِ Kotlin برگرداند ⇒ همان نوعِ اندروید.
   *  ⛔ متنِ کلیدِ اعلام‌شدهٔ غیرمتنی ([text]): رونوشت‌های پیشینِ همین shim (`SyncV1Keys.move`، بازیابیِ پشتیبانِ
   *  آیفون) عدد و بولی را متن نوشته بودند؛ `getBoolean`/`getLong` ِ آیفون همان را با NSUserDefaults می‌خوانند، پس
   *  `all` هم همان را می‌دهد. شکلِ ناشناخته دست نمی‌خورد (کدِ مشترک نادیده‌اش می‌گیرد، مثلِ اندروید).
   */
  fun typed(value: Any?, declared: String?): Any? = when (value) {
    null -> null
    is Boolean -> value
    is Float -> value
    is Double -> value.toFloat()
    is Int -> if (declared == "l") value.toLong() else value
    is Long -> integer(value, declared)
    is Short -> integer(value.toLong(), declared)
    is Byte -> integer(value.toLong(), declared)
    is String -> text(value, declared)
    is Set<*> -> value
    is List<*> -> strings(value) ?: value
    else -> value
  }

  /** متنِ کلیدِ اعلام‌شدهٔ بولی/عددی ⇒ همان نوع اگر روشن است؛ وگرنه همان متن */
  fun text(value: String, declared: String?): Any {
    val v = value.trim()
    return when (declared) {
      "b" -> when (v.lowercase()) {
        "1", "true", "yes" -> true
        "0", "false", "no" -> false
        else -> value
      }
      "i" -> v.toIntOrNull() ?: value
      "l" -> v.toLongOrNull() ?: value
      "f" -> v.toFloatOrNull()?.takeIf { it.isFinite() } ?: value
      else -> value
    }
  }

  /** NSArray ِ `putStringSet` ⇒ `Set<String>` (همان `getStringSet` ِ اندروید)؛ عضوِ غیرمتنی ⇒ `null` */
  fun strings(items: List<*>): Set<String>? =
    if (items.all { it is String }) items.mapTo(LinkedHashSet()) { it as String } else null
}
