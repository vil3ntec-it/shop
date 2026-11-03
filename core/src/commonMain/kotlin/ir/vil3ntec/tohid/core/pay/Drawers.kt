package ir.vil3ntec.tohid.core.pay

/**
 *  PAY-17 (§۶.۲، فاز ۶؛ D-14) — «چند کشو یا چند صندوقدار، و شیفتِ شناور (ادامه روی دستگاه دیگر)». منطقِ خالص، commonMain
 *  (گوشی، کامپیوتر و آیفون)؛ سیم‌کشی در `data/CashShift` و `data/PayFlow`، کلید در `data/TillDrawer`.
 *
 *  ── کشوی اصلی و کشوی نام‌دار ─────────────────────────────────────────────────────────
 *   کشوی اصلی  همان صندوقِ امروز (`shift_open` بی `drawer`): همهٔ ردیف‌های **بی مهر** ِ پنجرهٔ بازبودنش. بی تنظیمِ
 *              «چند کشو» هیچ ردیفی مهر ندارد ⇒ حساب بایت‌به‌بایت همان امروز.
 *   کشوی نام‌دار  `shift_open` با `drawer` (نامِ کشو یا صندوقدار، ۱ تا [LABEL_MAX] نویسه): فقط ردیف‌هایی که مهرِ همین
 *              شیفت را دارند (`shiftId` ِ فروش، برگشت و رسیدی که دستگاهِ روی همین کشو ثبت کرد) + ورود/خروجِ نقد و
 *              کشو بی فروشِ صریحِ خودش (`refId`). ⛔ مصارف، پرداخت به تأمین‌کننده، خرید، کارتِ هدیه و بازپرداختِ
 *              سفارشِ ویژه/امانی **مالِ کشوی اصلی‌اند** — کشوی نام‌دار فقط با «خروجِ نقدِ» صریح پول می‌دهد (قاعدهٔ #237).
 *   شناور      مهر شناسهٔ **شیفت** است، نه دستگاه: هر دستگاهی که «ادامه روی این دستگاه» را بزند همان شیفت را ادامه
 *              می‌دهد (ساعتِ دستگاه‌ها مهم نیست — شمردن با مهر است، نه پنجرهٔ زمان) و ردیفِ `shift_resume` سابقه می‌ماند.
 *
 *  ── مالکِ یک ردیف ([owner]) ──────────────────────────────────────────────────────────
 *   ردیفِ مهرخورده مالِ همان شیفتِ نام‌دار است، **مگر** آن شیفت پیش از ردیف بسته شده باشد (دستگاهی که هنوز بستن را
 *   نگرفته بود) یا شیفتِ نام‌داری با آن شناسه نباشد ⇒ مالِ کشوی اصلی (پول گم نمی‌شود و Z ِ شیفتِ بسته ثابت می‌ماند).
 */
object Drawers {

  const val LABEL_MAX = 40

  /** شیفتِ نام‌دار: شناسهٔ `shift_open` ⇐ لحظهٔ بستن (`null` ⇒ هنوز باز) */
  class Named(private val closeAt: Map<String, Long?>) {
    operator fun contains(id: String?): Boolean = id != null && closeAt.containsKey(id)
    fun closedAt(id: String): Long? = closeAt[id]
    val ids: Set<String> get() = closeAt.keys
  }

  /** نامِ کشو: یک خط، بی نویسهٔ کنترلی، ۱ تا [LABEL_MAX] نویسه؛ وگرنه null */
  fun label(raw: String?): String? {
    val s = raw.orEmpty().map { if (it.isISOControl()) ' ' else it }.joinToString("").trim().replace(Regex("\\s+"), " ")
    return s.takeIf { it.isNotEmpty() && it.length <= LABEL_MAX }
  }

  /**
   *  مالکِ ردیفی با مهرِ [stamp] و لحظهٔ [at]: شناسهٔ شیفتِ نام‌دار، یا `null` یعنی کشوی اصلی.
   */
  fun owner(stamp: String?, at: Long, named: Named): String? {
    if (stamp == null || stamp !in named) return null
    val closed = named.closedAt(stamp)
    return if (closed != null && at > closed) null else stamp
  }

  /** این ردیف در حسابِ شیفتِ [shiftId] می‌آید؟ [isNamed] ⇒ شیفتِ نام‌دار (بی پنجرهٔ زمان)؛ وگرنه کشوی اصلی (با پنجره) */
  fun counts(stamp: String?, at: Long, shiftId: String, isNamed: Boolean, named: Named, from: Long, to: Long): Boolean =
    if (isNamed) owner(stamp, at, named) == shiftId
    else at in from..to && owner(stamp, at, named) == null

  /** شیفتِ باز برای مهرِ دستگاهی که [picked] را برگزیده؛ برگزیدهٔ بسته یا ناشناس ⇒ null (فروش بی مهر = کشوی اصلی) */
  fun stamp(picked: String?, named: Named): String? =
    picked?.takeIf { it in named && named.closedAt(it) == null }

  // ── «همهٔ کشوها با هم» (X/Z ِ روز، PAY-17) ─────────────────────────────────────────────

  /**
   *  یک کشو در گزارشِ «همهٔ کشوها»، به پول (۱/۱۰۰ افغانی). [label] `null` ⇒ کشوی اصلی. [countedMinor] فقط کشوی بسته
   *  (Z)؛ کشوی باز (X) فقط «موردِ انتظار» دارد و در جمعِ شمرده و اختلاف نمی‌آید.
   */
  data class Sum(val id: String, val label: String?, val open: Boolean, val openingMinor: Long, val expectedMinor: Long, val countedMinor: Long?)

  /** جمعِ همهٔ کشوها؛ [varianceMinor] = جمعِ (شمرده − موردِ انتظار) فقط برای کشوهای بستهٔ شمرده‌شده */
  data class Combined(
    val drawers: List<Sum>,
    val openingMinor: Long,
    val expectedMinor: Long,
    val countedMinor: Long,
    val varianceMinor: Long,
    val open: Int,
    val closed: Int,
  )

  /** کشوی امروز: هنوز باز، یا بسته از [dayStart] به بعد (کشوی دیروز که امروز بسته شد هم) */
  fun inDay(closeAt: Long?, dayStart: Long): Boolean = closeAt == null || closeAt >= dayStart

  /**
   *  جمعِ همهٔ کشوها — کشوی اصلی اول، سپس نام‌دارها به ترتیبِ نام؛ هر کشو یک بار (شناسهٔ تکراری نادیده). جمعِ «موردِ
   *  انتظار» همهٔ کشوهاست (باز و بسته)؛ «شمرده» و «اختلاف» فقط از کشوهای بسته — کشوی باز هنوز شمرده نشده، پس اختلافش
   *  صفر حساب نمی‌شود و جمع را دروغ نمی‌کند.
   */
  fun combine(list: List<Sum>): Combined {
    val seen = HashSet<String>()
    val rows = list.filter { seen.add(it.id) }
      .sortedWith(compareBy<Sum>({ it.label != null }, { it.label.orEmpty() }, { it.id }))
    val counted = rows.filter { !it.open && it.countedMinor != null }
    return Combined(
      drawers = rows,
      openingMinor = rows.sumOf { it.openingMinor },
      expectedMinor = rows.sumOf { it.expectedMinor },
      countedMinor = counted.sumOf { it.countedMinor!! },
      varianceMinor = counted.sumOf { it.countedMinor!! - it.expectedMinor },
      open = rows.count { it.open },
      closed = rows.count { !it.open },
    )
  }
}
