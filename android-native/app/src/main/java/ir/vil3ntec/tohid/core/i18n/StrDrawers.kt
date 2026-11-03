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

  // ── «همهٔ کشوها با هم» (X/Z ِ روز) ──
  val allTitle: String get() = t("همهٔ کشوها با هم", "All drawers together", "ټول دخلونه یوځای")
  val allNote: String get() = t(
    "هر کشوی باز و هر کشوی بستهٔ امروز با «موردِ انتظار» ِ نقد، و جمعِ همه. شمرده و اختلاف فقط از کشوهای بسته؛ کشوی باز هنوز شمرده نشده.",
    "Every open drawer and every drawer closed today with its expected cash, and the total. Counted and difference come only from closed drawers; an open drawer is not counted yet.",
    "هر پرانیستی دخل او د نن هر تړل شوی دخل د تمه شوې نغدې سره، او د ټولو مجموعه. شمېرل شوې او توپیر یوازې له تړل شویو دخلونو؛ پرانیستی دخل لا نه دی شمېرل شوی.",
  )
  fun drawerState(name: String, open: Boolean): String =
    if (open) t("${asIs(name)} — باز", "$name — open", "$name — پرانیستی") else t("${asIs(name)} — بسته", "$name — closed", "$name — تړلی")
  val varianceWord: String get() = t("اختلاف", "Difference", "توپیر")
  val totalOpening: String get() = t("جمعِ اولِ صندوق‌ها", "Total opening cash", "د صندوقونو د پیل مجموعه")
  val totalExpected: String get() = t("جمعِ موردِ انتظار", "Total expected", "د تمې مجموعه")
  val totalCounted: String get() = t("جمعِ شمرده (کشوهای بسته)", "Total counted (closed drawers)", "د شمېرل شوو مجموعه (تړل شوي دخلونه)")
  val totalVariance: String get() = t("جمعِ اختلاف", "Total difference", "د توپیر مجموعه")
}
