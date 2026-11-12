package ir.vil3ntec.tohid.data


import ir.vil3ntec.tohid.core.i18n.StrLedger
/*
 *  PAY-19 — «کشو بی فروش» ِ «دستگاه‌ها › باز کردنِ کشو» (`PrintSpooler.openDrawer`): قاعدهٔ دلیل، مجوز و
 *  ردیفِ `no_sale`، خالص و بی چاپگر تا با آزمونِ واحد سنجیده شود. ضربه و ذخیره همان `PrintSpooler` است؛
 *  این‌جا هیچ رفتاری عوض نمی‌شود (همان پیام‌ها و همان شکلِ ردیفِ #286).
 */
object DrawerNoSale {

  /** تأییدکننده وقتی نامِ کاربر نیست ولی سیاستِ دکان اجازه داده (هرگز «صاحب/مدیر»ِ ساختگی) */
  const val POLICY_APPROVER = "policy"

  val NEED_REASON: String get() = StrLedger.needReason
  val NEED_APPROVAL: String get() = StrLedger.drawerNeedsApproval

  val NO_DRAWER_PRINTER: String get() = StrLedger.noDrawerPrinter

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

  /** «کشو بی فروش» ِ صندوقِ گوشیِ نسخهٔ ۳: ضربه به کشوی چاپگر، فقط ثبتِ دلیل و تأیید، یا بسته */
  enum class PhoneAction { KICK, RECORD, DENIED }

  /**
   *  `sales/drawer/move?mode=no_sale` ِ گوشی، با همان مجوزِ کامپیوتر (`drawer.open`، نه «شاگرد نیست»): بی مجوز بسته حتی با
   *  چاپگر؛ با چاپگرِ پیش‌فرضِ کشودار همان `PrintSpooler.openDrawer` (ردیف فقط وقتی ضربه رسید)؛ بی آن فقط ردیفِ `no_sale`
   *  با دلیل و تأیید (`PayFlow.moveRow`).
   */
  fun phoneAction(hasDrawerPrinter: Boolean, allowed: Boolean): PhoneAction = when {
    !allowed -> PhoneAction.DENIED
    hasDrawerPrinter -> PhoneAction.KICK
    else -> PhoneAction.RECORD
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
    val notes = "کشو بی فروش — $why — دستگاه: $device · چاپگر: $printer" +  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
      (if (printerError != null) " — خطای چاپگر: $printerError" else "") + " — تأیید: $approver"  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
    return AuditEntry(
      id = id, type = "no_sale", date = today, refId = shiftOpenId, notes = notes, createdAt = now,
      by = by, reason = why, approvedBy = approver,
    )
  }
}
