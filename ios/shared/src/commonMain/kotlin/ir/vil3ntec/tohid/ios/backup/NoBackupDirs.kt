package ir.vil3ntec.tohid.ios.backup

/*
 *  پوشه‌های `filesDir` که پشتیبانِ اندروید نمی‌برد، روی آیفون هم بیرون از پشتیبانِ iCloud/Finder — منطقِ خالص (commonMain،
 *  بی java.*؛ آزمون: ‎jvmTest/…/backup/NoBackupDirsJvmTest‎). لایهٔ نازکِ iOS (`NSURLIsExcludedFromBackupKey`):
 *  ‎iosMain/…/ios/backup/IosNoBackupDirs.kt‎.
 *
 *  ── اشکالی که این می‌بندد (DATA-06، ۱۴۰۵/۰۸/۱۸) ───────────────────────────
 *  `filesDir` ِ آیفون (`Application Support/tohid`) به‌طورِ پیش‌فرض در پشتیبانِ iCloud/Finder است ⇒ آیفونی که از پشتیبانِ
 *  آیفونِ دیگر بالا بیاید سایهٔ v1 (`sync-shadow/`) و حالِ Sync v3 ِ هر حساب (`sync-v3/<کلید>/`: cursor، سایه، ساعتِ HLC،
 *  بستگی، ژورنال) را هم می‌گرفت؛ اندروید هر دو را کنار می‌گذارد (`backup_rules.xml` ابری و انتقال، `backup_rules_legacy.xml`،
 *  `TohidBackupAgent.EXCLUDED_DIRS`). cursor ِ گوشیِ پیشین با شناسهٔ تازه opهای خودِ آن را دوباره می‌نشاند.
 *  ──────────────────────────────────────────────────────────────────────────
 *
 *  ویژگیِ `NSURLIsExcludedFromBackupKey` مالِ همان قلم است و روی پوشه همهٔ درونش را بیرون می‌گذارد ⇒ فقط خودِ این پوشه‌ها.
 *  دفتر، `oplog-*` ِ v1 و صفِ v3 (`sync-v3-<کلید>/`، پوشهٔ دیگری کنارِ `sync-v3/`) مثلِ اندروید **با** پشتیبان می‌مانند:
 *  گوشیِ دوم صف را اول می‌فرستد و سرور با op_id «تکراری» می‌بیند. پشتیبانِ گرفته‌شده پیش از این نسخه هنوز این پوشه‌ها را
 *  دارد ⇒ حصار (`V3Fence`، `V1Replica.fence`) سرِ جایش می‌ماند.
 *
 *  همه `internal`: نه API ِ Swift، نه نامی در چارچوبِ ObjC (‎ObjcNamesJvmTest‎).
 */
internal object NoBackupDirs {
  /**
   *  همان `exclude domain="file"` ِ `backup_rules.xml` — آزمون این را با هر دو بخشِ XML، `backup_rules_legacy.xml`،
   *  `TohidBackupAgent.EXCLUDED_DIRS` و `ShadowStore.DIR`/`V3StateDir.DIR` ِ کدِ مشترک می‌سنجد (پوشهٔ تازه‌ای که اندروید کنار
   *  بگذارد و این‌جا نیاید ⇒ آزمونِ سرخ).
   */
  val NAMES: List<String> = listOf("sync-shadow", "sync-v3")

  /** کارِ دیسک روی پوشهٔ `filesDir/<نام>` */
  interface Io {
    /** پوشه هست یا همین حالا ساخته شد */
    fun mkdir(name: String): Boolean

    /** `NSURLIsExcludedFromBackupKey` ِ آن روشن است (ناخوانا ⇒ false: دوباره گذاشته می‌شود) */
    fun excluded(name: String): Boolean

    /** گذاشتن و دوباره خواندن؛ true فقط وقتی خوانده شد */
    fun exclude(name: String): Boolean
  }

  /**
   *  هر پوشه: ساختن (نصبِ تازه ⇒ پیش از نخستین نوشتنِ کدِ مشترک بیرون است) و گذاشتنِ ویژگی اگر نیست (نصبِ پیشین ⇒ در نخستین
   *  اجرای این نسخه). هر پوشه جدا: شکست یا خطای یکی بقیه را نمی‌ایستاند. برمی‌گرداند: پوشه‌هایی که نشد (خالی ⇒ همه بیرون‌اند).
   */
  fun apply(io: Io, names: List<String> = NAMES): List<String> = names.filterNot { n ->
    runCatching { io.mkdir(n) && (io.excluded(n) || io.exclude(n)) }.getOrDefault(false)
  }
}
