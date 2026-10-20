package ir.vil3ntec.tohid.ui.components.common

/**
 *  چیدمانِ نوارِ پایینِ گوشی — مدلِ خالص (بی Compose و بی اندروید)، phone v3 §F/§F2.
 *
 *  شکل همیشه همان r10 است: دایرهٔ شروع + قرصِ ۳ تا ۵ جایی + دایرهٔ پایان. این‌که
 *  **چه چیزی** در هر جا بنشیند انتخابِ خودِ کاربر است و برای هر کاربر روی همین
 *  دستگاه ذخیره می‌شود (`key`). قاعده‌ها:
 *   • «بیشتر» (≡) همیشه در قرص است، تا هیچ بخشی دست‌نیافتنی نشود؛
 *   • قرص ۳ تا ۵ جا؛ هر دایرهٔ کناری یک مورد یا خاموش؛
 *   • تکرار نه؛ موردی که نقشِ کاربر اجازه‌اش را ندارد پیشنهاد هم نمی‌شود؛
 *   • شناسهٔ ناشناخته (پس از به‌روزرسانی) بی‌صدا کنار می‌رود.
 *  همهٔ این‌ها در `normalize` است و هر چیدمانی که به صفحه می‌رسد از آن می‌گذرد.
 */
data class NavLayout(val start: String?, val pill: List<String>, val end: String?) {
  /** همهٔ موردهای نشسته، به ترتیبِ دیدن (شروع، قرص، پایان) */
  val ids: List<String> get() = listOfNotNull(start) + pill + listOfNotNull(end)
}

/** یک موردِ انتخاب‌شدنی: شناسه، برچسب، مقصد (`AppRoot.open`) */
data class NavItemDef(val id: String, val label: String, val route: String)

object NavCatalog {
  const val SEARCH = "search"
  const val HOME = "home"
  const val SALE = "sale"
  const val PRODUCTS = "products"
  const val WAREHOUSE = "warehouse"
  const val DEBTORS = "debtors"
  const val ORDERS = "orders"
  const val REPORTS = "reports"
  const val EXPENSES = "expenses"
  const val PURCHASING = "purchasing"
  const val HISTORY = "history"
  const val TEAM = "team"
  const val ACCOUNT = "account"
  const val SCAN = "scan"
  const val SETTINGS = "settings"
  const val MORE = "more"

  /**
   *  موردهای ساخته‌شده — ترتیبِ فهرستِ ویرایشگر هم همین است. مقصدها مسیرهای
   *  نوع‌دارِ امروزند (`V3Modules`)؛ شناسه‌ها ماندگارند (در چیدمانِ کاربر ذخیره‌اند).
   */
  val BUILT_IN: List<NavItemDef> = listOf(
    NavItemDef(SEARCH, "جست‌وجو", "search"),
    NavItemDef(HOME, "خانه", "home"),
    NavItemDef(SALE, "فروش", "sale"),
    NavItemDef(PRODUCTS, "کالاها", "products"),
    NavItemDef(MORE, "بیشتر", "more"),
    NavItemDef(ACCOUNT, "حساب", "profile"),
    NavItemDef(SCAN, "اسکنِ بارکد", "sale?scan=1"),
    NavItemDef(WAREHOUSE, "انبار", "stock"),
    NavItemDef(DEBTORS, "قرض‌داران", "debtors"),
    NavItemDef(ORDERS, "سفارش‌ها", "orders"),
    NavItemDef(REPORTS, "گزارش‌ها", "reports"),
    NavItemDef(EXPENSES, "مصارف", "expenses"),
    NavItemDef(PURCHASING, "خرید", "purchasing"),
    NavItemDef(HISTORY, "تاریخچهٔ فروش", "sales"),
    NavItemDef(TEAM, "کارمندان", "team"),
    NavItemDef(SETTINGS, "تنظیمات", "settings"),
  )

  /**
   *  موردهای نوارِ ماژول‌های تازه (`ModuleEntry` با `Place.NAV`) — پوسته یک بار
   *  می‌گذارد (`V3Modules`) تا بخشِ تازه بی دست زدن به این فایل در ویرایشگر بیاید.
   */
  @Volatile var extras: () -> List<NavItemDef> = { emptyList() }

  /** همهٔ موردها: ساخته‌شده‌ها و بعد موردهای ماژول‌های تازه */
  val ALL: List<NavItemDef> get() = BUILT_IN + extras().filter { e -> BUILT_IN.none { it.id == e.id } }

  fun of(id: String?): NavItemDef? = id?.let { i -> ALL.firstOrNull { it.id == i } }

  /**
   *  موردهایی که این کاربر می‌تواند باز کند — همان سدهای `AppRoot`/«بیشتر»:
   *  کارمندان و تنظیمات فقط برای مدیر (`settings.write`)، سفارش‌ها فقط وقتی سرور
   *  برای این دکان روشن گفته.
   */
  fun allowed(canManage: Boolean, ordersLive: Boolean): Set<String> =
    ALL.map { it.id }.filter { id ->
      when (id) {
        TEAM, SETTINGS -> canManage
        ORDERS -> ordersLive
        else -> true
      }
    }.toSet()

  /**
   *  موردِ فعالِ نوار برای صفحهٔ باز — اولین موردِ **نشسته** که مقصدش همین
   *  صفحه است (فروش و اسکن هر دو به «فروش» می‌روند؛ هر کدام که نشسته فعال
   *  می‌شود). صفحه‌ای که در نوار نیست ⇒ هیچ.
   */
  fun activeFor(layout: NavLayout, route: String?): String? {
    val base = route?.substringBefore('?') ?: return null
    return layout.ids.firstOrNull { of(it)?.route?.substringBefore('?') == base }
  }
}

object NavLayouts {
  const val MIN_PILL = 3
  const val MAX_PILL = 5
  private const val PREFIX = "phone_nav"
  private const val VERSION = "v1"
  private const val OFF = "-"

  val DEFAULT = NavLayout(
    start = NavCatalog.SEARCH,
    pill = listOf(NavCatalog.HOME, NavCatalog.SALE, NavCatalog.PRODUCTS, NavCatalog.MORE),
    end = NavCatalog.ACCOUNT,
  )

  /** کلیدِ ذخیره، جدا برای هر کاربر: `phone_nav:<کاربر>`؛ بی‌حساب = `local` */
  fun key(userId: String?): String {
    val u = userId.orEmpty().trim()
      .map { if (it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' || it == '@') it else '_' }
      .joinToString("")
      .ifEmpty { "local" }
    return "$PREFIX:$u"
  }

  /** `v1|search|home,sale,products,more|account` — دایرهٔ خاموش = `-` */
  fun encode(l: NavLayout): String =
    listOf(VERSION, l.start ?: OFF, l.pill.joinToString(","), l.end ?: OFF).joinToString("|")

  /** خواندنِ ذخیره؛ خراب یا نسخهٔ ناشناخته ⇒ `null` (یعنی پیش‌فرض) */
  fun decode(raw: String?): NavLayout? {
    val parts = raw?.trim()?.split('|') ?: return null
    if (parts.size != 4 || parts[0] != VERSION) return null
    fun side(s: String) = s.trim().takeIf { it.isNotEmpty() && it != OFF }
    return NavLayout(
      start = side(parts[1]),
      pill = parts[2].split(',').map { it.trim() }.filter { it.isNotEmpty() },
      end = side(parts[3]),
    )
  }

  /**
   *  هر چیدمان ⇐ چیدمانِ درست برای این کاربر. ناشناخته و بی‌اجازه کنار می‌رود،
   *  تکرار حذف می‌شود (قرص بر دایره مقدم است، شروع بر پایان)، «بیشتر» اگر نبود
   *  به تهِ قرص می‌آید، قرصِ بلند کوتاه می‌شود (بی آن‌که «بیشتر» برود) و قرصِ
   *  کوتاه از پیش‌فرض‌ها و بعد از فهرست پر می‌شود.
   */
  fun normalize(l: NavLayout, allowed: Set<String>): NavLayout {
    fun ok(id: String?) = id != null && NavCatalog.of(id) != null && id in allowed
    val pill = l.pill.filter { ok(it) }.distinct().toMutableList()
    if (NavCatalog.MORE !in pill) pill += NavCatalog.MORE
    while (pill.size > MAX_PILL) {
      val drop = pill.indexOfLast { it != NavCatalog.MORE }
      pill.removeAt(drop)
    }
    if (pill.size < MIN_PILL) {
      val fill = (DEFAULT.pill + NavCatalog.ALL.map { it.id })
        .filter { ok(it) && it !in pill && it != l.start && it != l.end }
        .distinct()
      for (id in fill) {
        if (pill.size >= MIN_PILL) break
        //  «بیشتر» تهِ قرص می‌ماند
        pill.add((pill.size - 1).coerceAtLeast(0), id)
      }
    }
    val start = l.start.takeIf { ok(it) && it != NavCatalog.MORE && it !in pill }
    val end = l.end.takeIf { ok(it) && it != NavCatalog.MORE && it !in pill && it != start }
    return NavLayout(start, pill, end)
  }

  fun load(raw: String?, allowed: Set<String>): NavLayout = normalize(decode(raw) ?: DEFAULT, allowed)

  /* ------------------------------ ویرایشگر ------------------------------ */

  /** جاهای نوار؛ `OUT` یعنی «در نوار نیست» */
  enum class Slot { START, PILL, END, OUT }

  /** چرا یک تغییر پذیرفته نشد — متنش همان است که ویرایشگر نشان می‌دهد */
  enum class Problem(val message: String) {
    MORE_LOCKED("«بیشتر» همیشه در قرص می‌ماند تا هیچ بخشی گم نشود"),
    PILL_FULL("قرص حداکثر ${MAX_PILL} جا دارد"),
    PILL_MIN("قرص دست‌کم ${MIN_PILL} جا می‌خواهد"),
    NOT_ALLOWED("این مورد برای نقشِ شما باز نیست"),
  }

  sealed interface Edit {
    data class Ok(val layout: NavLayout) : Edit
    data class Rejected(val problem: Problem) : Edit
  }

  fun slotOf(l: NavLayout, id: String): Slot = when (id) {
    l.start -> Slot.START
    l.end -> Slot.END
    in l.pill -> Slot.PILL
    else -> Slot.OUT
  }

  /**
   *  گذاشتنِ یک مورد در یک جا. موردِ پیشینِ دایرهٔ مقصد بیرون می‌رود؛ در قرص
   *  `index` جای تازه است (بی آن، پیش از «بیشتر»). جابه‌جاییِ درونِ قرص هم همین است.
   */
  fun place(l: NavLayout, id: String, slot: Slot, index: Int? = null, allowed: Set<String>): Edit {
    if (id !in allowed || NavCatalog.of(id) == null) return Edit.Rejected(Problem.NOT_ALLOWED)
    val from = slotOf(l, id)
    if (id == NavCatalog.MORE && slot != Slot.PILL) return Edit.Rejected(Problem.MORE_LOCKED)
    val pill = l.pill.filter { it != id }.toMutableList()
    var start = l.start.takeIf { it != id }
    var end = l.end.takeIf { it != id }
    when (slot) {
      Slot.START -> start = id
      Slot.END -> end = id
      Slot.OUT -> Unit
      Slot.PILL -> {
        if (from != Slot.PILL && l.pill.size >= MAX_PILL) return Edit.Rejected(Problem.PILL_FULL)
        val at = (index ?: pill.indexOf(NavCatalog.MORE).takeIf { it >= 0 } ?: pill.size).coerceIn(0, pill.size)
        pill.add(at, id)
      }
    }
    if (pill.size < MIN_PILL) return Edit.Rejected(Problem.PILL_MIN)
    return Edit.Ok(NavLayout(start, pill, end))
  }

  /**
   *  فهرستِ ویرایشگر: یک ستونِ پیوسته از سرخط‌ها و موردها. کشیدنِ یک ردیف
   *  یعنی جابه‌جاییِ آن در همین ستون؛ جای تازه از سرخطِ بالای آن خوانده می‌شود.
   */
  sealed interface Row {
    data class Header(val slot: Slot) : Row
    data class Item(val id: String) : Row
  }

  fun rows(l: NavLayout, allowed: Set<String>): List<Row> = buildList {
    add(Row.Header(Slot.START)); l.start?.let { add(Row.Item(it)) }
    add(Row.Header(Slot.PILL)); l.pill.forEach { add(Row.Item(it)) }
    add(Row.Header(Slot.END)); l.end?.let { add(Row.Item(it)) }
    add(Row.Header(Slot.OUT))
    NavCatalog.ALL.map { it.id }.filter { it in allowed && it !in l.ids }.forEach { add(Row.Item(it)) }
  }

  /**
   *  کشیدنِ ردیفِ `from` به جای `to` در همان فهرست. جای تازه = بخشِ سرخطی که
   *  بالای آن می‌افتد (سرخطِ نخست ثابت است، پس چیزی بالای آن نمی‌رود).
   */
  fun move(l: NavLayout, rows: List<Row>, from: Int, to: Int, allowed: Set<String>): Edit {
    val item = rows.getOrNull(from) as? Row.Item ?: return Edit.Ok(l)
    val list = rows.toMutableList()
    list.removeAt(from)
    val at = to.coerceIn(1, list.size)
    list.add(at, item)
    val header = list.subList(0, at).lastOrNull { it is Row.Header } as Row.Header
    val slot = header.slot
    val index = if (slot == Slot.PILL) {
      list.subList(list.indexOf(header) + 1, at).count { it is Row.Item }
    } else null
    if (slot == slotOf(l, item.id) && slot != Slot.PILL) return Edit.Ok(l)
    return place(l, item.id, slot, index, allowed)
  }
}
