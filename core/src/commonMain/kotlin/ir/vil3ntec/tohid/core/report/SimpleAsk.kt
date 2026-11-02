package ir.vil3ntec.tohid.core.report

import ir.vil3ntec.tohid.core.calendar.SolarHijri

/**
 *  RPT-16 — «پرسشِ ساده از داده‌ها»، **بی هوش مصنوعی**.
 *
 *  §۶.۲۲ (AI-09) پرسشِ متنی را با عاملِ هوش مصنوعیِ محلی روی سرور می‌خواهد؛ آن عامل و مدلش هنوز نیست. این‌جا
 *  فقط بخشِ قطعی و بی‌خطر است: پرسش‌های آماده با جای خالی (بازه، کالا، دسته، مشتری، فروشنده) و خوانشِ قاعده‌ایِ یک
 *  جملهٔ کوتاه به یکی از همان‌ها. پاسخ را `data/SimpleAskEngine` از همان موتورهای «گزارش‌ها» می‌دهد، نه این‌جا.
 *
 *  ⛔ هیچ حدسی زده نمی‌شود: نامِ نامعلوم یا دوپهلو جای خالی می‌ماند، واژهٔ ناشناخته پرسش را بی پاسخ می‌گذارد، و
 *  پرسشِ پولی برای نقشی که «عددهای مالی» را نمی‌بیند (`reports.money`) رد می‌شود — همان سدِ بخشِ «سود و فروش».
 */
object SimpleAsk {

  enum class Param { PERIOD, PRODUCT, CATEGORY, CUSTOMER, CASHIER }

  /** همان بازه‌های «گزارش‌ها» (`ReportEngine.Range`)، تا عدد همان عددِ صفحه باشد */
  enum class Span { TODAY, WEEK, MONTH, JMONTH, JYEAR, ALL }

  /** `jy`/`jm` فقط برای ماه یا سالِ خورشیدی */
  data class Period(val span: Span, val jy: Int = 0, val jm: Int = 1)

  /** `money`: عددِ مالی (درآمد، سود، خرید) ⇒ فقط با `reports.money` */
  enum class Template(val id: String, val needs: List<Param>, val money: Boolean) {
    SALES("sales", listOf(Param.PERIOD), true),
    PROFIT("profit", listOf(Param.PERIOD), true),
    EXPENSES("expenses", listOf(Param.PERIOD), true),
    PURCHASES("purchases", listOf(Param.PERIOD), true),
    CASHIER_SALES("cashier_sales", listOf(Param.PERIOD, Param.CASHIER), true),
    CATEGORY_SALES("category_sales", listOf(Param.PERIOD, Param.CATEGORY), true),
    PRODUCT_SALES("product_sales", listOf(Param.PERIOD, Param.PRODUCT), true),
    PRODUCT_STOCK("product_stock", listOf(Param.PRODUCT), false),
    LOW_STOCK("low_stock", emptyList(), false),
    TOP_SELLING("top_selling", emptyList(), false),
    SLOW_SELLING("slow_selling", emptyList(), false),
    CUSTOMER_DEBT("customer_debt", listOf(Param.CUSTOMER), false),
    DEBT_TOTAL("debt_total", emptyList(), false),
    ;

    companion object {
      fun of(id: String): Template? = entries.firstOrNull { it.id == id }
    }
  }

  /** کالا و مشتری با شناسه؛ دسته و فروشنده با همان نام (دفتر شناسهٔ جدا برایشان ندارد) */
  data class Query(
    val template: Template,
    val period: Period? = null,
    val product: String? = null,
    val category: String? = null,
    val customer: String? = null,
    val cashier: String? = null,
  ) {
    fun value(p: Param): String? = when (p) {
      Param.PERIOD -> period?.span?.name
      Param.PRODUCT -> product
      Param.CATEGORY -> category
      Param.CUSTOMER -> customer
      Param.CASHIER -> cashier
    }
  }

  data class Named(val id: String, val name: String)

  /** نام‌هایی که پرسش می‌تواند به آن‌ها اشاره کند — از خودِ دفتر (`SimpleAskEngine.catalog`) */
  data class Catalog(
    val products: List<Named> = emptyList(),
    val categories: List<String> = emptyList(),
    val customers: List<Named> = emptyList(),
    val cashiers: List<String> = emptyList(),
  ) {
    fun named(p: Param): List<Named> = when (p) {
      Param.PRODUCT -> products
      Param.CUSTOMER -> customers
      Param.CATEGORY -> categories.map { Named(it, it) }
      Param.CASHIER -> cashiers.map { Named(it, it) }
      Param.PERIOD -> emptyList()
    }
  }

  enum class Problem { NOT_ALLOWED, MISSING, UNKNOWN, BAD_PERIOD }

  data class Issue(val problem: Problem, val param: Param? = null)

  fun allowed(t: Template, canSeeMoney: Boolean): Boolean = !t.money || canSeeMoney

  /** پرسش‌های آمادهٔ همین نقش — پولی‌ها برای کسی که عددِ مالی نمی‌بیند اصلاً نشان داده نمی‌شوند */
  fun templates(canSeeMoney: Boolean): List<Template> = Template.entries.filter { allowed(it, canSeeMoney) }

  /** ماه/سالِ خورشیدیِ بیرون از تقویم (`SolarHijri`) ⇒ نادرست؛ بقیهٔ بازه‌ها همیشه درست‌اند */
  fun validPeriod(p: Period): Boolean = when (p.span) {
    Span.JMONTH -> p.jm in 1..12 && SolarHijri.fromSolar(p.jy, p.jm, 1) != null
    Span.JYEAR -> SolarHijri.fromSolar(p.jy, 1, 1) != null
    else -> true
  }

  /** خالی ⇒ پرسش پاسخ‌پذیر است. اجازه پیش از هر چیز سنجیده می‌شود تا نامِ کالا هم از پرسشِ ممنوع چیزی نگوید */
  fun validate(q: Query, canSeeMoney: Boolean, catalog: Catalog): List<Issue> {
    if (!allowed(q.template, canSeeMoney)) return listOf(Issue(Problem.NOT_ALLOWED))
    return q.template.needs.mapNotNull { p ->
      if (p == Param.PERIOD) {
        val period = q.period
        when {
          period == null -> Issue(Problem.MISSING, p)
          !validPeriod(period) -> Issue(Problem.BAD_PERIOD, p)
          else -> null
        }
      } else {
        val v = q.value(p)
        when {
          v.isNullOrBlank() -> Issue(Problem.MISSING, p)
          catalog.named(p).none { it.id == v } -> Issue(Problem.UNKNOWN, p)
          else -> null
        }
      }
    }
  }

  /* ------------------------------ خوانشِ جمله ------------------------------ */

  /**
   *  `query` ِ `null` ⇒ پرسش فهمیده نشد (`unknown` واژه‌های ناشناخته، شاید خالی). `ambiguous` ⇒ نامی که به بیش از
   *  یک ردیفِ هم‌اندازه می‌خورد؛ جایش خالی می‌ماند تا کاربر از فهرست برگزیند.
   */
  data class Parsed(val query: Query?, val unknown: List<String> = emptyList(), val ambiguous: Param? = null)

  /** نویسهٔ عربی/فارسیِ هم‌ارز، اعراب، نیم‌فاصله و رقمِ فارسی ⇒ یک شکل؛ نشانه‌ها ⇒ فاصله */
  fun normalize(text: String): String {
    val sb = StringBuilder(text.length)
    for (c in text) {
      val code = c.code
      when {
        code in 0x06F0..0x06F9 -> sb.append('0' + (code - 0x06F0))
        code in 0x0660..0x0669 -> sb.append('0' + (code - 0x0660))
        code in 0x064B..0x065F || code == 0x0670 || code == 0x0640 -> Unit
        c == 'ي' || c == 'ى' -> sb.append('ی')
        c == 'ك' -> sb.append('ک')
        c == 'ة' || c == 'ۀ' -> sb.append('ه')
        c == 'أ' || c == 'إ' -> sb.append('ا')
        c == 'ؤ' -> sb.append('و')
        c.isLetterOrDigit() -> sb.append(c.lowercaseChar())
        else -> sb.append(' ')
      }
    }
    return sb.toString().trim().replace(Regex("\\s+"), " ")
  }

  fun tokens(text: String): List<String> = normalize(text).split(' ').filter { it.isNotEmpty() }

  private class Words(prefix: List<String>, exact: List<String> = emptyList()) {
    private val pre = prefix.map(::normalize)
    private val ex = exact.map(::normalize).toSet()
    fun has(t: String): Boolean = t in ex || pre.any { t.startsWith(it) }
  }

  private val DEBT = Words(listOf("قرض", "بدهکار", "بدهی", "طلب", "debt", "owe", "پوروړ"), listOf("پور"))
  private val TOP = Words(listOf("پرفروش", "bestsell", "topsell"), listOf("top", "best"))
  private val STOCK = Words(listOf("موجود", "stock", "inventory"), listOf("مانده", "باقی", "left", "پاتې"))
  private val LOW = Words(emptyList(), listOf("کم", "کمبود", "تمام", "ختم", "low", "out", "لږ"))
  private val PROFIT = Words(listOf("سود", "فایده", "مفاد", "profit", "ګټ"))
  private val EXPENSE = Words(listOf("مصرف", "مصارف", "خرج", "هزینه", "expens", "لګښت"))
  private val PURCHASE = Words(emptyList(), listOf("خرید", "خریدها", "خریدیم", "خریدم", "purchase", "purchases", "bought", "پېر", "پېرود"))
  private val SALE = Words(listOf("فروش", "فروخت", "sale", "sold", "sell", "revenue", "پلور"))
  private val CATEGORY_MARK = Words(listOf("دسته", "category", "categor", "ډل"))
  private val CASHIER_MARK = Words(listOf("فروشنده", "صندوقدار", "کاربر", "شاگرد", "cashier", "seller", "پلورونک"))
  private val SLOW = Words(listOf("کمفروش", "slow", "least"))
  private val GROUPS = listOf(DEBT, TOP, SLOW, STOCK, LOW, PROFIT, EXPENSE, PURCHASE, SALE, CATEGORY_MARK, CASHIER_MARK)

  private val TODAY = Words(emptyList(), listOf("امروز", "today", "نن"))
  private val WEEK = Words(listOf("هفته", "week", "اونۍ", "اونی"))
  private val MONTH = Words(emptyList(), listOf("ماه", "month", "میاشت", "میاشتې", "میاشتی"))
  private val YEAR = Words(emptyList(), listOf("سال", "امسال", "year", "کال"))
  private val LAST_YEAR = Words(emptyList(), listOf("پارسال", "پارکال"))
  private val PREV = Words(emptyList(), listOf("پیش", "گذشته", "قبل", "قبلی", "last", "previous", "تېر", "تېره"))
  private val ALL = Words(emptyList(), listOf("همه", "کل", "all", "ever", "حالا", "تاکنون", "آغاز", "ټول"))
  private val PERIOD_WORDS = listOf(TODAY, WEEK, MONTH, YEAR, LAST_YEAR, PREV, ALL)

  /** نامِ ماه‌ها: دری، پشتو و نامِ ایرانی (فروردین…) ⇒ ۱..۱۲ */
  private val MONTH_NAMES: Map<String, Int> by lazy {
    val ir = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
    val out = HashMap<String, Int>()
    for (list in listOf(SolarHijri.MONTHS_FA, SolarHijri.MONTHS_PS, ir)) list.forEachIndexed { i, n -> out[normalize(n)] = i + 1 }
    //  «دی» در پشتو «است» است (‹څومره دی›)؛ ماهِ دی را با «جدی» یا «مرغومی» بپرسید
    out.remove("دی")
    out
  }

  private val STOP = Words(
    emptyList(),
    listOf(
      "چقدر", "چند", "چندتا", "چه", "چی", "چیست", "چطور", "کدام", "کی", "است", "هست", "هستند", "بود", "بوده",
      "شد", "شده", "شدند", "شدیم", "داریم", "دارد", "دارم", "دارند", "داشتیم", "کرد", "کردیم", "کردم", "ما", "من", "را",
      "از", "در", "تا", "به", "با", "و", "یا", "ها", "های", "ی", "ای", "که", "برای", "جمع", "مجموع", "مقدار", "مبلغ",
      "تعداد", "افغانی", "دار", "داران", "ترین", "تر", "کالا", "کالاها", "کالاهای", "جنس", "اجناس", "اجناسی", "چیز",
      "چیزی", "چیزها", "چیزهای", "چیزهایی", "مشتری",
      "این", "همین", "روز", "روزه", "ماهه", "how", "much", "many", "what", "is", "was", "the", "of", "in", "for", "this",
      "by", "total", "our", "we", "did", "do", "on", "hand", "items", "products", "customer", "څومره", "څو",
      "ده", "وه", "دی", "شوې", "شوی", "د", "په", "کې", "دا", "او", "ته", "له", "ټولټال",
    ),
  )

  private class Hit(val named: Named?, val start: Int, val len: Int)

  /** درازترین نامِ کامل (واژه به واژه) که در جمله آمده؛ دو ردیفِ هم‌اندازهٔ متفاوت ⇒ دوپهلو (`named` ِ `null`) */
  private fun match(tk: List<String>, options: List<Named>): Hit? {
    var best: Hit? = null
    var tie = false
    for (o in options) {
      val name = tokens(o.name)
      if (name.isEmpty() || name.size > tk.size) continue
      val at = (0..tk.size - name.size).firstOrNull { i -> name.indices.all { tk[i + it] == name[it] } } ?: continue
      val b = best
      when {
        b == null || name.size > b.len -> { best = Hit(o, at, name.size); tie = false }
        name.size == b.len && b.named?.id != o.id -> tie = true
      }
    }
    val b = best ?: return null
    return if (tie) Hit(null, b.start, b.len) else b
  }

  /**
   *  جملهٔ کوتاه ⇒ یکی از پرسش‌های آماده. `todayJy`/`todayJm` ماه و سالِ خورشیدیِ امروز (برای «حمل»، «امسال»…)؛
   *  بی واژهٔ بازه ⇒ `fallback` (همان بازهٔ انتخاب‌شدهٔ صفحه). نامِ ماهِ بی سال که هنوز نرسیده ⇒ همان ماهِ پارسال.
   */
  fun parse(text: String, catalog: Catalog, todayJy: Int, todayJm: Int, fallback: Period?): Parsed {
    val tk = tokens(text)
    if (tk.isEmpty()) return Parsed(null)
    fun any(w: Words) = tk.any(w::has)
    val hits = Param.entries.filter { it != Param.PERIOD }.associateWith { match(tk, catalog.named(it)) }
    val product = hits[Param.PRODUCT]
    val category = hits[Param.CATEGORY]
    val customer = hits[Param.CUSTOMER]
    val cashier = hits[Param.CASHIER]
    val catMark = any(CATEGORY_MARK)
    val cashMark = any(CASHIER_MARK)

    val chosen: Pair<Template, Param?> = when {
      any(DEBT) -> if (customer != null) Template.CUSTOMER_DEBT to Param.CUSTOMER else Template.DEBT_TOTAL to null
      any(TOP) -> Template.TOP_SELLING to null
      //  «کم‌فروش‌ترین» ⇒ «کم» + «فروش»، نه «موجودیِ کم»
      any(SLOW) || (any(LOW) && any(SALE) && !any(STOCK)) -> Template.SLOW_SELLING to null
      any(STOCK) || any(LOW) -> when {
        product != null -> Template.PRODUCT_STOCK to Param.PRODUCT
        any(LOW) -> Template.LOW_STOCK to null
        else -> Template.PRODUCT_STOCK to null
      }
      any(PROFIT) -> Template.PROFIT to null
      any(EXPENSE) -> Template.EXPENSES to null
      any(PURCHASE) -> Template.PURCHASES to null
      any(SALE) || product != null || category != null || cashier != null -> when {
        catMark && category != null -> Template.CATEGORY_SALES to Param.CATEGORY
        cashMark && cashier != null -> Template.CASHIER_SALES to Param.CASHIER
        product != null -> Template.PRODUCT_SALES to Param.PRODUCT
        category != null -> Template.CATEGORY_SALES to Param.CATEGORY
        cashier != null -> Template.CASHIER_SALES to Param.CASHIER
        catMark -> Template.CATEGORY_SALES to null
        cashMark -> Template.CASHIER_SALES to null
        else -> Template.SALES to null
      }
      else -> return Parsed(null, leftover(tk, emptySet()))
    }
    val (template, param) = chosen
    val hit = param?.let { hits[it] }
    val used = HashSet<Int>()
    if (hit != null) for (i in hit.start until hit.start + hit.len) used += i
    val (period, periodUsed) = period(tk, used, todayJy, todayJm)
    used += periodUsed
    val unknown = leftover(tk, used)
    if (unknown.isNotEmpty()) return Parsed(null, unknown)

    val id = hit?.named?.id
    val q = Query(
      template = template,
      period = if (Param.PERIOD in template.needs) period ?: fallback else null,
      product = id.takeIf { param == Param.PRODUCT },
      category = id.takeIf { param == Param.CATEGORY },
      customer = id.takeIf { param == Param.CUSTOMER },
      cashier = id.takeIf { param == Param.CASHIER },
    )
    return Parsed(q, ambiguous = param.takeIf { hit != null && hit.named == null })
  }

  /** واژه‌هایی که نه کلید‌واژه‌اند، نه واژهٔ بازه، نه واژهٔ بی‌معنا و نه بخشی از نامِ برگزیده */
  private fun leftover(tk: List<String>, used: Set<Int>): List<String> = tk.filterIndexed { i, t ->
    i !in used && GROUPS.none { it.has(t) } && PERIOD_WORDS.none { it.has(t) } && !STOP.has(t) && t !in MONTH_NAMES &&
      !(t == "7" && "روز" in tk)
  }

  private fun period(tk: List<String>, used: Set<Int>, jy: Int, jm: Int): Pair<Period?, Set<Int>> {
    val free = tk.indices.filter { it !in used }
    val words = free.map { tk[it] }
    val mine = HashSet<Int>()
    fun has(w: Words) = words.any(w::has)
    var year: Int? = null
    var month: Int? = null
    for (i in free) {
      val t = tk[i]
      val n = t.toIntOrNull()
      if (t.length == 4 && n != null && n in 1300..1499) { year = n; mine += i }
      MONTH_NAMES[t]?.let { month = it; mine += i }
    }
    val m = month
    val prev = has(PREV)
    val p = when {
      m != null -> Period(Span.JMONTH, year ?: if (m > jm) jy - 1 else jy, m)
      has(TODAY) -> Period(Span.TODAY)
      has(WEEK) || ("7" in words && "روز" in words) -> Period(Span.WEEK)
      has(MONTH) && prev -> if (jm == 1) Period(Span.JMONTH, jy - 1, 12) else Period(Span.JMONTH, jy, jm - 1)
      has(MONTH) -> Period(Span.MONTH)
      has(LAST_YEAR) || (has(YEAR) && prev) -> Period(Span.JYEAR, jy - 1)
      year != null || has(YEAR) -> Period(Span.JYEAR, year ?: jy)
      has(ALL) -> Period(Span.ALL)
      else -> null
    }
    return p to mine
  }
}
