package ir.vil3ntec.tohid.core.i18n

/**
 *  S44 — «بازیابی از پشتیبان» ِ دستگاهِ غیرفعال (گوشیِ نسخهٔ ۳ و کامپیوتر). منطق در `data/LostDeviceRestore`؛ پرچمِ خاموشِ
 *  `shop.lost_device_replay` ⇒ دکمه نیست.
 */
@Suppress("unused")
object StrLostDevice {
  val button: String get() = t("بازیابی از پشتیبان", "Restore from backup", "له بیک اپ څخه بیا راګرځول")
  val title: String get() = t("بازیابی از پشتیبانِ دستگاهِ گم‌شده", "Restore from the lost device's backup", "د ورک شوې وسیلې له بیک اپ څخه بیا راګرځول")
  val body: String get() = t(
    "فایلِ پشتیبانِ همین دستگاه را انتخاب کنید. کارهایی که پیش از گم شدن به سرور نرسیده بودند (مثلاً فروشِ بی‌اینترنت) یک بار به دفترِ دکان می‌رسند؛ دوباره فرستادن چیزی را دو بار ثبت نمی‌کند.",
    "Choose this device's backup file. Work that had not reached the server before it was lost (such as offline sales) reaches the shop's books once; sending again never records anything twice.",
    "د همدې وسیلې د بیک اپ فایل وټاکئ. هغه کارونه چې له ورکېدو مخکې سرور ته نه وو رسېدلي (لکه بې انټرنېټه پلور) یو ځل د دوکان دفتر ته رسېږي؛ بیا لېږل هېڅ شی دوه ځله نه ثبتوي.",
  )
  val pick: String get() = t("انتخابِ فایلِ پشتیبان", "Choose the backup file", "د بیک اپ فایل وټاکئ")
  val reading: String get() = t("در حالِ خواندنِ پشتیبان…", "Reading the backup…", "بیک اپ لوستل کېږي…")
  val none: String get() = t(
    "در این پشتیبان کارِ نرفته‌ای نیست؛ پشتیبانِ تازه‌ترِ همین دستگاه را امتحان کنید",
    "This backup holds no unsent work; try a newer backup of the same device",
    "په دې بیک اپ کې نه لېږل شوی کار نشته؛ د همدې وسیلې نوی بیک اپ وازمویئ",
  )
  fun ready(n: Any): String = t("${asIs(n)} کارِ نرفته در این پشتیبان هست", "$n unsent items in this backup", "په دې بیک اپ کې $n نه لېږل شوي کارونه دي")
  val send: String get() = t("فرستادن به دفترِ دکان", "Send to the shop's books", "د دوکان دفتر ته ولېږئ")
  val sending: String get() = t("در حالِ فرستادن…", "Sending…", "لېږل کېږي…")
  val doneTitle: String get() = t("بازیابی تمام شد", "Restore finished", "بیا راګرځول بشپړ شول")
  fun done(applied: Any, duplicate: Any, rejected: Any): String = t(
    "رسید: ${asIs(applied)} · از پیش بود: ${asIs(duplicate)} · پذیرفته نشد: ${asIs(rejected)}",
    "Added: $applied · Already there: $duplicate · Not accepted: $rejected",
    "ورسېدل: $applied · مخکې وو: $duplicate · ونه منل شول: $rejected",
  )
  val phrase: String get() = t("این پشتیبان رمزدار است؛ عبارتِ بازیابی را بنویسید", "This backup is locked; type the recovery phrase", "دا بیک اپ قلف دی؛ د بیا راګرځولو عبارت ولیکئ")
  val phraseLabel: String get() = t("عبارتِ بازیابی", "Recovery phrase", "د بیا راګرځولو عبارت")
  val open: String get() = t("باز کن", "Open", "پرانیزه")
  val errMismatch: String get() = t("این پشتیبان مالِ همین دستگاه نیست", "This backup is not from this device", "دا بیک اپ د همدې وسیلې نه دی")
  val errNotDisabled: String get() = t("اول این دستگاه را غیرفعال کنید", "Disable this device first", "لومړی دا وسیله غیرفعاله کړئ")
  val errOff: String get() = t("بازیابی از پشتیبان برای این دکان روشن نیست", "Restore from backup is not on for this shop", "له بیک اپ څخه بیا راګرځول د دې دوکان لپاره روښانه نه دي")
  val errRead: String get() = t("فایلِ پشتیبان خوانده نشد", "The backup file could not be read", "د بیک اپ فایل ونه لوستل شو")
}
