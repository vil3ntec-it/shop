package ir.vil3ntec.tohid.data

/*
 *  PAY-19 — «کشو بی فروش» ِ «دستگاه‌ها › باز کردنِ کشو» (`PrintSpooler.openDrawer`): قاعدهٔ دلیل، مجوز و
 *  ردیفِ `no_sale`، خالص و بی چاپگر تا با آزمونِ واحد سنجیده شود. ضربه و ذخیره همان `PrintSpooler` است؛
 *  این‌جا هیچ رفتاری عوض نمی‌شود (همان پیام‌ها و همان شکلِ ردیفِ #286).
 */
object DrawerNoSale {

  /** تأییدکننده وقتی نامِ کاربر نیست ولی سیاستِ دکان اجازه داده (هرگز «صاحب/مدیر»ِ ساختگی) */
  const val POLICY_APPROVER = "policy"

  const val NEED_REASON = "دلیل را بنویسید"
  const val NEED_APPROVAL = "باز کردنِ کشو بی فروش تأییدِ صاحب یا مدیر می‌خواهد (APPROVAL_REQUIRED)"

  const val NO_DRAWER_PRINTER = "چاپگرِ کشودار تعریف نشده — تنظیمات › دستگاه‌ها"

  /**
   *  Shift+F8 ِ صندوقِ کامپیوتر (`KeyMap.SALE_DRAWER`) پیش از پنجرهٔ دلیل: بی چاپگرِ پیش‌فرضِ کشودار پیام (پیش از مجوز،
   *  تا شاگرد هم بداند چرا)، بی `drawer.open` همان [NEED_APPROVAL]؛ وگرنه `null` ⇒ پنجرهٔ دلیل و همان
   *  `PrintSpooler.openDrawer` (که [refusal] را دوباره می‌سنجد). کلید هرگز بی پنجره کشو را باز نمی‌کند.
   */
  fun keyGate(hasDrawerPrinter: Boolean, allowed: Boolean): String? = when {
    !hasDrawerPrinter -> NO_DRAWER_PRINTER
    !allowed -> NEED_APPROVAL
    else -> null
  }

  /** پیشِ ضربه: دلیلِ خالی، یا نقشی که سیاستِ دکان اجازه نداده (`drawer.open`) ⇒ پیامِ رد؛ وگرنه `null` */
  fun refusal(reason: String, allowed: Boolean): String? = when {
    reason.trim().isEmpty() -> NEED_REASON
    !allowed -> NEED_APPROVAL
    else -> null
  }

  /**
   *  ردیفِ سابقهٔ یک ضربه که **به چاپگر رسید** (حتی با خطا؛ شاید کشو باز شده باشد): چه کسی (`by`)، کی، چرا،
   *  تأییدکننده و در `notes` کدام دستگاه و کدام چاپگر؛ `refId` صندوقِ بازِ همان لحظه.
   */
  fun entry(
    id: String,
    reason: String,
    by: String?,
    device: String,
    printer: String,
    printerError: String?,
    shiftOpenId: String?,
    today: String,
    now: Long,
  ): AuditEntry {
    val why = reason.trim()
    val approver = by ?: POLICY_APPROVER
    val notes = "کشو بی فروش — $why — دستگاه: $device · چاپگر: $printer" +
      (if (printerError != null) " — خطای چاپگر: $printerError" else "") + " — تأیید: $approver"
    return AuditEntry(
      id = id, type = "no_sale", date = today, refId = shiftOpenId, notes = notes, createdAt = now,
      by = by, reason = why, approvedBy = approver,
    )
  }
}
