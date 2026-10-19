package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.money.Currency
import ir.vil3ntec.tohid.core.money.Money
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty

/**
 *  دفترِ قرض‌داران — v3 §22.2 (مرحلهٔ A، بندِ ۵) و §21.11.
 *
 *  برای هر برد: تک‌تکِ اقلامِ فاکتورش (کالا، مقدار/وزن، واحد، قیمتِ همان لحظه)،
 *  تخفیفِ فاکتور، پرداختِ همان لحظه، سررسید، پرداخت‌های بعدی که رویش نشسته،
 *  باقیِ همان برد و ماندهٔ حساب پس از هر رویداد؛ برای هر رسید: پیش و پس از آن
 *  و این‌که بابتِ کدام برد بوده. و رسیدِ قابلِ چاپ/فرستادن برای هر ردیف.
 *
 *  ── هیچ چیزِ تازه‌ای ذخیره نمی‌شود ───────────────────────────────────
 *  همه از همان `transactions`، `sales` و `saleItems` حساب می‌شود (CRM-01: مشتری
 *  و قرض‌دار یک ردیف‌اند). تنها دو فیلدِ اختیاریِ تازه — `DebtTransaction.dueDate`
 *  و `Debtor.creditLimit` — وقتی نوشته می‌شوند که کاربر خودش پرشان کند.
 *
 *  ── پول به واحدِ کوچک ─────────────────────────────────────────────────
 *  حسابِ این‌جا با `core/money` است (پول = یک‌صدمِ افغانی، عددِ صحیح)؛ Double فقط
 *  در مرزِ دادهٔ امروز (`minor`) خوانده می‌شود. ماندهٔ آخر همان `ShopStore.debt`
 *  است (آزمونِ مرجعِ `DebtBookTest`)، فقط بی خطای ممیزِ شناور.
 *
 *  ── تخصیصِ پرداخت (§21.11) ────────────────────────────────────────────
 *  ۱) رسیدی که فاکتوری را نام می‌برد («لغو فروش — فاکتور #۱۲»، «مرجوعی کالا —
 *     فاکتور #۱۲» یا شرحی که کاربر خودش نوشته) اول بردِ همان فاکتور را می‌بندد.
 *  ۲) بقیه از قدیمی‌ترین بردِ باز («اول‌آمده، اول‌رفته»، همان `debtSince`).
 *  ۳) آنچه به هیچ بردی ننشست، پیش‌پرداخت است و روی بردِ بعدی می‌نشیند.
 *  این تخصیص فقط نمایش است؛ چیزی را در دفتر جابه‌جا نمی‌کند.
 *
 *  ⛔ هم‌زادِ این پرونده `license/debt-book.js` است و هر دو با یک فایلِ بردارِ
 *  مرجع (`license/test-vectors/debt-book-v1.json`) سنجیده می‌شوند. یکی را عوض
 *  کنی، دیگری هم.
 */
object DebtBook {

  val CURRENCY: Currency = Currency.AFN
  private val ZERO = Money.zero(CURRENCY)

  /** عددِ اعشاریِ دفتر ⇒ واحدِ کوچک. ناعدد صفر — همان کاری که `money()` در نمایش می‌کند. */
  fun minor(v: Double): Money = Money.fromLegacy(if (v.isNaN() || v.isInfinite()) 0.0 else v, CURRENCY)

  /** نمایش با همان `money()`ِ برنامه (گرد به افغانی) */
  fun show(m: Money): String = money(m.minor.toDouble() / CURRENCY.minorPerMajor)

  /** حالِ حساب با همان جمله‌های `DebtorEngine.stateText` */
  fun state(m: Money): String = DebtorEngine.stateText(m.minor.toDouble() / CURRENCY.minorPerMajor)

  /** یک قلمِ فاکتورِ پشتِ برد — قیمت همان که لحظهٔ فروش نوشته شد (`SaleItem.unitPrice`) */
  data class Line(
    val name: String,
    val quantity: Double,
    val unit: String,
    val unitPrice: Money,
    val total: Money,
    val returned: Double,
  )

  /** تکه‌ای از یک رسید که روی یک برد نشسته — از دید هر دو طرف */
  data class Allocation(val txId: String, val amount: Money)

  data class Entry(
    val tx: DebtTransaction,
    /** شمارهٔ ردیف به ترتیبِ زمان، از ۱ */
    val row: Int,
    val give: Boolean,
    val amount: Money,
    val balanceBefore: Money,
    val balanceAfter: Money,
    /** فاکتورِ پشتِ ردیف (فروشِ نسیه، لغو، مرجوعی) — اگر هست */
    val sale: Sale?,
    val invoiceLabel: String?,
    /** اقلامِ فاکتور — فقط برای برد */
    val lines: List<Line>,
    val dueDate: String?,
    /** برد: آنچه از آن هنوز باز است. رسید: آنچه به هیچ بردی ننشست (پیش‌پرداخت). */
    val open: Money,
    /** برد: رسیدهایی که رویش نشستند. رسید: بردهایی که بست. */
    val allocations: List<Allocation>,
    /** سررسید گذشته و هنوز باز */
    val overdue: Boolean,
  ) {
    val paid: Boolean get() = give && open.minor <= 0
  }

  data class Book(
    val debtor: Debtor,
    /** به ترتیبِ زمان — قدیمی‌ترین اول */
    val entries: List<Entry>,
    val balance: Money,
    val overdue: List<Entry>,
    val overdueAmount: Money,
    /** نزدیک‌ترین سررسیدِ نیامده از بردهای باز */
    val nextDue: Entry?,
    val credit: Credit?,
  ) {
    fun entry(txId: String): Entry? = entries.find { it.tx.id == txId }
  }

  /** ترتیبِ زمان — همان `CustomerEngine.ledger` و `ReportCsv.statement` */
  private val ORDER = compareBy<DebtTransaction> { it.date }.thenBy { it.createdAt }

  fun book(d: ShopData, debtorId: String, today: String): Book? {
    val debtor = d.debtors.find { it.id == debtorId } ?: return null
    val mine = d.transactions.filter { it.debtorId == debtorId }.sortedWith(ORDER)
    //  کلید همان شمارهٔ نمایشی است: `A1-5` و `B1-5` دو فاکتورِ جدااند (F0-03)
    val byLabel = d.sales.filter { it.debtorId == debtorId && it.invoiceNumber != null }
      .associateBy { it.invoiceLabel()!! }
    val itemsBySale = d.saleItems.groupBy { it.saleId }
    val units = d.products.associate { it.id to it.unit }

    val open = LinkedHashMap<String, Money>()               // بردِ باز ⇒ باقی، قدیمی‌ترین اول
    val unapplied = ArrayDeque<Pair<String, Money>>()       // رسیدِ ننشسته ⇒ باقی
    val giveOfSale = HashMap<String, String>()              // فاکتور ⇒ بردش
    val links = HashMap<String, MutableList<Allocation>>()
    fun link(receiveId: String, giveId: String, amt: Money) {
      links.getOrPut(giveId) { mutableListOf() } += Allocation(receiveId, amt)
      links.getOrPut(receiveId) { mutableListOf() } += Allocation(giveId, amt)
    }

    var run = ZERO
    data class Raw(val tx: DebtTransaction, val amount: Money, val before: Money, val after: Money, val sale: Sale?, val label: String?)
    val raws = mine.map { tx ->
      val give = tx.type == "give"
      val amt = minor(tx.amount)
      val before = run
      run = if (give) run + amt else run - amt
      val label = CustomerEngine.invoiceLabelOf(tx.notes)
      val sale = label?.let { byLabel[it] }

      if (amt.minor > 0) {
        if (give) {
          if (sale != null) giveOfSale.putIfAbsent(sale.id, tx.id)
          var left = amt
          while (left.minor > 0 && unapplied.isNotEmpty()) {
            val (rid, credit) = unapplied.removeFirst()
            val used = minOf(credit, left)
            link(rid, tx.id, used)
            left -= used
            if (credit > used) unapplied.addFirst(rid to (credit - used))
          }
          open[tx.id] = left
        } else {
          var pay = amt
          fun settle(gid: String) {
            val o = open[gid] ?: return
            if (o.minor <= 0 || pay.minor <= 0) return
            val used = minOf(o, pay)
            open[gid] = o - used
            link(tx.id, gid, used)
            pay -= used
          }
          sale?.let { s -> giveOfSale[s.id]?.let(::settle) }
          for (gid in open.keys.toList()) {
            if (pay.minor <= 0) break
            settle(gid)
          }
          if (pay.minor > 0) unapplied.addLast(tx.id to pay)
        }
      }
      Raw(tx, amt, before, run, sale, label)
    }

    val leftOver = HashMap<String, Money>()
    unapplied.forEach { (rid, m) -> leftOver[rid] = (leftOver[rid] ?: ZERO) + m }

    val entries = raws.mapIndexed { i, r ->
      val give = r.tx.type == "give"
      val due = if (give) r.tx.dueDate?.trim()?.takeIf { it.isNotEmpty() } else null
      val left = if (give) open[r.tx.id] ?: ZERO else leftOver[r.tx.id] ?: ZERO
      Entry(
        tx = r.tx,
        row = i + 1,
        give = give,
        amount = r.amount,
        balanceBefore = r.before,
        balanceAfter = r.after,
        sale = r.sale,
        invoiceLabel = r.label,
        lines = if (give && r.sale != null) itemsBySale[r.sale.id].orEmpty().map { it.line(d, units) } else emptyList(),
        dueDate = due,
        open = left,
        allocations = links[r.tx.id].orEmpty(),
        overdue = give && due != null && due < today && left.minor > 0,
      )
    }
    val late = entries.filter { it.overdue }
    return Book(
      debtor = debtor,
      entries = entries,
      balance = run,
      overdue = late,
      overdueAmount = late.fold(ZERO) { acc, e -> acc + e.open },
      nextDue = entries.filter { it.give && it.open.minor > 0 && it.dueDate != null && it.dueDate >= today }
        .minByOrNull { it.dueDate!! },
      credit = creditOf(debtor, run, ZERO),
    )
  }

  private fun SaleItem.line(d: ShopData, units: Map<String, String>) = Line(
    name = SalesEngine.itemName(d, this),
    quantity = quantity,
    unit = units[productId].orEmpty(),
    unitPrice = minor(unitPrice),
    total = minor(totalPrice),
    returned = returnedQty,
  )

  /* ------------------------------ سقفِ اعتبار (CRM-03) ------------------------------ */

  data class Credit(val limit: Money, val balance: Money, val after: Money) {
    val over: Boolean get() = after > limit
    /** چقدر از سقف بیرون می‌زند — صفر اگر نه */
    val excess: Money get() = if (after > limit) after - limit else Money.zero(limit.currency)
    /** تا سقف چقدر جا هست — صفر اگر پر است */
    val headroom: Money get() = if (limit > balance) limit - balance else Money.zero(limit.currency)
  }

  private fun creditOf(debtor: Debtor, balance: Money, extra: Money): Credit? {
    val limit = debtor.creditLimit?.takeIf { !it.isNaN() && !it.isInfinite() && it >= 0 } ?: return null
    return Credit(minor(limit), balance, balance + extra)
  }

  /**
   *  اگر این مبلغِ تازه برده شود، از سقف می‌گذرد؟ `null` یعنی این مشتری سقف ندارد
   *  (همان رفتارِ همیشه).
   */
  fun creditCheck(d: ShopData, debtorId: String, extra: Money): Credit? {
    val debtor = d.debtors.find { it.id == debtorId } ?: return null
    val balance = d.transactions.filter { it.debtorId == debtorId }
      .fold(ZERO) { acc, t -> if (t.type == "give") acc + minor(t.amount) else acc - minor(t.amount) }
    return creditOf(debtor, balance, extra)
  }

  fun overLimitText(c: Credit): String =
    "${show(c.excess)} افغانی بیش از سقفِ اعتبار (${show(c.limit)}) — ماندهٔ فعلی ${show(c.balance)}"

  /* ------------------------------ سررسید ------------------------------ */

  private val ISO = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")

  /** `yyyy-MM-dd` با ماه و روزِ درست */
  fun isIsoDate(s: String): Boolean {
    val m = ISO.matchEntire(s) ?: return false
    val month = m.groupValues[2].toInt()
    val day = m.groupValues[3].toInt()
    return month in 1..12 && day in 1..31
  }

  /* ------------------------------ رسید ------------------------------ */

  data class Receipt(val title: String, val name: String, val rows: List<Pair<String, String>>) {
    /** برای `ReportPrint` (چاپ/PDF) و اکسل */
    fun sheet(): ReportCsv.Sheet =
      ReportCsv.Sheet(name, listOf(listOf(title, "")) + rows.map { listOf(it.first, it.second) })

    /** برای فرستادن در پیام یا کپی */
    fun text(): String = (listOf(title) + rows.map { (k, v) -> if (v.isEmpty()) k else "$k: $v" }).joinToString("\n")
  }

  private fun safe(name: String) = name.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().ifBlank { "مشتری" }

  /**
   *  رسیدِ یک ردیف — برد با اقلام و پرداخت‌هایش، یا رسیدِ دریافت با پیش و پس.
   *  `null` اگر مشتری یا ردیف نیست.
   */
  fun receipt(d: ShopData, debtorId: String, txId: String, storeName: String, today: String): Receipt? {
    val book = book(d, debtorId, today) ?: return null
    val e = book.entry(txId) ?: return null
    val af = " افغانی"
    val rows = mutableListOf<Pair<String, String>>()
    rows += "مشتری" to book.debtor.name
    if (book.debtor.phone.isNotBlank()) rows += "تلفن" to ir.vil3ntec.tohid.LocaleText.phoneDisplay(book.debtor.phone)
    rows += "تاریخ" to formatDate(e.tx.date)
    e.invoiceLabel?.let { rows += "فاکتور" to "#$it" }

    if (e.give) {
      val s = e.sale
      if (s != null && e.lines.isNotEmpty()) {
        e.lines.forEach { l ->
          val unit = if (l.unit.isNotBlank()) " ${l.unit}" else ""
          rows += "${l.name} — ${qty(l.quantity)}$unit × ${show(l.unitPrice)}" to show(l.total)
          if (l.returned > 0) rows += "   مرجوعی: ${qty(l.returned)}$unit" to ""
        }
        rows += "جمعِ اقلام" to show(minor(s.total)) + af
        if (s.discount > 0) rows += "تخفیف" to show(minor(s.discount)) + af
        rows += "قابلِ پرداخت" to show(minor(s.finalTotal)) + af
        rows += "پرداختِ همان لحظه" to show(minor(s.paidAmount)) + af
      }
      rows += "قرضِ این ردیف" to show(e.amount) + af
      if (e.tx.notes.isNotBlank()) rows += "شرح" to e.tx.notes
      rows += "سررسید" to (e.dueDate?.let(::formatDate) ?: "ندارد")
      e.allocations.forEach { a ->
        val r = book.entry(a.txId)
        rows += "پرداخت ${r?.tx?.date?.let(::formatDate).orEmpty()}".trim() to show(a.amount) + af
      }
      rows += "باقیِ این قرض" to if (e.paid) "تسویه شد" else show(e.open) + af
      if (e.overdue) rows += "وضعیت" to "سررسید گذشته"
    } else {
      rows += "مبلغِ دریافتی" to show(e.amount) + af
      if (e.tx.notes.isNotBlank()) rows += "شرح" to e.tx.notes
      rows += "مانده پیش از دریافت" to state(e.balanceBefore)
      e.allocations.forEach { a ->
        val g = book.entry(a.txId)
        val what = g?.invoiceLabel?.let { "فاکتور #$it" } ?: "برد ${g?.tx?.date?.let(::formatDate).orEmpty()}".trim()
        rows += "بابتِ $what" to show(a.amount) + af
      }
      if (e.open.minor > 0) rows += "پیش‌پرداخت (روی قرضِ بعدی)" to show(e.open) + af
    }
    rows += "مانده پس از این ردیف" to state(e.balanceAfter)
    rows += "ماندهٔ امروزِ حساب" to state(book.balance)

    val kind = if (e.give) "رسیدِ قرض" else "رسیدِ دریافت"
    return Receipt(
      title = "${storeName.trim().ifBlank { "فروشگاه" }} — $kind",
      name = "$kind-${safe(book.debtor.name)}-${e.tx.date}-${e.row}",
      rows = rows,
    )
  }
}
