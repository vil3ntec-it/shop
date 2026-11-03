package ir.vil3ntec.tohid.core.i18n

/**
 *  PAY-17 — «چند کشو یا چند صندوقدار، و شیفتِ شناور» (`ui/v3/sales/drawer`؛ قاعده‌ها `core/pay/Drawers`، `data/TillDrawer`).
 */
@Suppress("unused")
object StrDrawers {

  val settingsTitle: String get() = t("چند کشو و شیفتِ شناور", "Multiple drawers and floating shift", "څو دخلونه او ګرځنده شیفټ")
  val settingsNote: String get() = t(
    "هر صندوقدار یا کشو صندوقِ نام‌دارِ خودش را باز و بسته می‌کند و می‌تواند همان صندوق را روی دستگاهِ دیگری ادامه دهد. در کشوی نام‌دار فقط فروش، برگشت و دریافتِ همان کشو و ورود و خروجِ صریحش شمرده می‌شود؛ مصارف و خرید از کشوی اصلی‌اند. خاموش: همان یک صندوقِ امروز.",
    "Each cashier or drawer opens and closes its own named till and can continue it on another device. A named drawer counts only its own sales, returns, receipts and explicit cash moves; expenses and purchases belong to the main drawer. Off: the single till of today.",
    "هر صندوقدار یا دخل خپل نومول شوی صندوق پرانیزي او تړي او کولای شي همدا صندوق په بله دستګاه دوام کړي. په نومول شوي دخل کې یوازې د همدې دخل پلور، ستنېدنې، ترلاسه کول او څرګند نغدي تګ راتګ شمېرل کېږي؛ لګښتونه او پېرود د اصلي دخل دي. بند: د نن ورځې یو صندوق.",
  )
  val drawers: String get() = t("کشوهای باز", "Open drawers", "پرانیستي دخلونه")
  val drawersNote: String get() = t(
    "هر کشو صندوقِ جدای خودش را دارد؛ «ادامه روی این دستگاه» همان صندوق را این‌جا ادامه می‌دهد.",
    "Each drawer has its own till; “Continue on this device” carries the same till on here.",
    "هر دخل خپل جلا صندوق لري؛ «په دې دستګاه دوام» همدا صندوق دلته دوام کوي.",
  )
  val mainDrawer: String get() = t("کشوی اصلی", "Main drawer", "اصلي دخل")
  val thisDevice: String get() = t("این دستگاه", "This device", "دا دستګاه")
  val continueHere: String get() = t("ادامه روی این دستگاه", "Continue on this device", "په دې دستګاه دوام")
  val useMain: String get() = t("این دستگاه روی کشوی اصلی", "Use the main drawer on this device", "دا دستګاه په اصلي دخل")
  fun since(at: String, by: String): String =
    if (by.isBlank()) t("از ${asIs(at)}", "since $at", "له $at") else t("از ${asIs(at)} — ${asIs(by)}", "since $at — $by", "له $at — $by")
  val newDrawer: String get() = t("بازکردنِ کشوی تازه روی این دستگاه", "Open a new drawer on this device", "په دې دستګاه نوی دخل پرانیزئ")
  val noneOpen: String get() = t("هیچ کشوی بازی نیست.", "No drawer is open.", "هېڅ دخل پرانیستی نه دی.")
  val drawerName: String get() = t("نامِ کشو یا صندوقدار", "Drawer or cashier name", "د دخل یا صندوقدار نوم")
  val drawerNameHelp: String get() = t("مثلاً «کشوی ۲» یا نامِ صندوقدار؛ خالی ⇒ کشوی اصلی.", "For example “Drawer 2” or the cashier's name; empty ⇒ the main drawer.", "لکه «دخل ۲» یا د صندوقدار نوم؛ تش ⇒ اصلي دخل.")
  fun nowOn(name: String): String = t("این دستگاه حالا روی «${asIs(name)}» است.", "This device is now on “$name”.", "دا دستګاه اوس په «$name» دی.")
  fun drawerTitle(name: String): String = t("کشوی «${asIs(name)}»", "Drawer “$name”", "دخل «$name»")
  val namedNote: String get() = t(
    "در این کشو فقط فروش، برگشت و دریافتِ همین کشو و ورود و خروجِ صریحش شمرده می‌شود.",
    "This drawer counts only its own sales, returns, receipts and explicit cash moves.",
    "په دې دخل کې یوازې د همدې دخل پلور، ستنېدنې، ترلاسه کول او څرګند نغدي تګ راتګ شمېرل کېږي.",
  )
}
