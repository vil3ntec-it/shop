package ir.vil3ntec.tohid.ui.components.pos

import ir.vil3ntec.tohid.core.text.TextNormalizer
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem

/**
 *  جست‌وجو و ترتیبِ کاتالوگِ صندوقِ گوشی (نسخهٔ ۳) — خالص، بی Compose.
 *
 *  ── چرا نمایه ─────────────────────────────────────────────────────────
 *  سنجشِ کارایی برای هر کلید ۲۶۴ تا ۴۴۹ میلی‌ثانیه (p95) دیده بود، با بودجهٔ ۱۵۰: هر
 *  حرف همهٔ نام‌ها را از نو نرمال می‌کرد. حالا کلیدهای نرمال‌شده (`TextNormalizer`، همتای
 *  `text-core.js`) فقط **یک بار با هر تغییرِ کاتالوگ** ساخته می‌شوند (`index`) و هر حرف
 *  فقط چند `contains` روی رشته‌های آماده است.
 *
 *  ── ترتیب ────────────────────────────────────────────────────────────
 *  پرفروش‌ترین اول (شمارِ فاکتورهایی که کالا در آن‌ها فروخته شده؛ فاکتورِ لغوشده نه)،
 *  هم بی‌جست‌وجو و هم در نتیجه‌ها. فقط بارکدِ کامل (اسکن یا تایپِ کاملِ کد) پیش از
 *  همه می‌نشیند — فروشنده دقیقاً همان را می‌خواهد. برابری ⇐ نام.
 *
 *  ── جورشدن ───────────────────────────────────────────────────────────
 *  «ي/ی»، «ك/ک»، «ۀ/ه»، «أ/ا»، رقم‌های فارسی/عربی/لاتین، نیم‌فاصله و اعراب یکی‌اند.
 *  هر کلمهٔ پرس‌وجو باید جایی در نام باشد (به هر ترتیب)، یا پرس‌وجو بخشی از یک بارکد
 *  باشد (از سه رقم به بالا تا «۵» همهٔ بارکدها را نگیرد).
 *
 *  ── مترادف (POS-04) ──────────────────────────────────────────────────
 *  [Synonyms] ِ فهرستِ مرکزی (`TillSynonyms`): هر کلمه (یا کلِ پرس‌وجوی چندکلمه‌ای) با هر واژهٔ همان گروه هم جور
 *  است — همان قاعدهٔ `TextNormalizer.matches` و `text-core.matches` ِ ویترین. فهرستِ تهی ⇒ همان جست‌وجوی پیشین.
 */
object CatalogSearch {

  /**
   *  گروه‌های مترادف، یک بار نرمال‌شده (کلیدِ مقایسه ⇐ همهٔ کلیدهای همان گروه) تا هر حرف فقط یک نگاه به نقشه باشد.
   */
  class Synonyms private constructor(private val alts: Map<String, List<String>>) {
    /** کلیدِ نرمال ⇐ خودش و هم‌گروه‌هایش (خودش اول) */
    fun of(key: String): List<String> = alts[key] ?: listOf(key)
    val isEmpty: Boolean get() = alts.isEmpty()

    companion object {
      val NONE = Synonyms(emptyMap())

      fun of(groups: List<List<String>>): Synonyms {
        if (groups.isEmpty()) return NONE
        val out = HashMap<String, MutableList<String>>()
        for (g in groups) {
          val keys = g.map { TextNormalizer.compareKey(it) }.filter { it.isNotEmpty() }.distinct()
          if (keys.size < 2) continue
          for (k in keys) {
            val list = out.getOrPut(k) { mutableListOf(k) }
            for (o in keys) if (o !in list) list += o
          }
        }
        return if (out.isEmpty()) NONE else Synonyms(out)
      }
    }
  }

  /** یک کالای آمادهٔ جست‌وجو */
  data class Entry(
    val id: String,
    val category: String,
    /** نامِ نرمال بی فاصله (`compareKey`) */
    val nameKey: String,
    /** بارکدهای نرمال */
    val codes: List<String>,
    /** شمارِ فاکتورهای فروش */
    val sold: Int,
  )

  /** نمایهٔ آماده، از پیش به ترتیبِ پرفروش‌ترین */
  class Index internal constructor(val entries: List<Entry>) {
    val size: Int get() = entries.size
  }

  /** شمارِ فاکتورهای (لغونشدهٔ) هر کالا — ردیفِ آزاد (بی کالا) حساب نمی‌شود */
  fun popularity(sales: List<Sale>, items: List<SaleItem>): Map<String, Int> = Counted.build(sales, items).counts

  /** همان [popularity] با خانهٔ یکتای [counts] */
  private class Counted(
    val sales: List<Sale>,
    val items: List<SaleItem>,
    val counts: Map<String, Int>,
    /** لغوشده‌ها و فاکتورهایی که قلمی در دفتر دارند — مالِ همین خانه، بیرون داده نمی‌شوند */
    val cancelled: HashSet<String>,
    val itemSales: HashSet<String>,
  ) {
    companion object {
      fun build(sales: List<Sale>, items: List<SaleItem>): Counted {
        val cancelled = HashSet<String>()
        for (s in sales) if (s.status == "cancelled") cancelled += s.id
        val itemSales = HashSet<String>()
        val seen = HashSet<Pair<String, String>>()
        val out = HashMap<String, Int>()
        for (it in items) {
          itemSales += it.saleId
          if (it.productId.isBlank() || it.saleId in cancelled) continue
          //  دو ردیفِ یک کالا در یک فاکتور یک فروش است
          if (!seen.add(it.saleId to it.productId)) continue
          out[it.productId] = (out[it.productId] ?: 0) + 1
        }
        return Counted(sales, items, out, cancelled, itemSales)
      }
    }

    /**
     *  POS-42 — همین شمارش برای دفترِ پس از یک فروش، بی گذشتن از همهٔ اقلام: فقط وقتی فاکتورها و اقلام تنها به ته افزوده
     *  شده‌اند (`AppendTail`)، هیچ فاکتورِ تازهٔ لغوشده‌ای قلمِ پیشین ندارد و هیچ قلمِ تازه‌ای به فاکتوری نمی‌خورد که
     *  پیش‌تر قلم داشت (یکی‌شدنِ «یک کالا در یک فاکتور» فقط میانِ قلم‌های تازه است). هر چیزِ دیگر ⇒ `null` ⇒ ساختنِ کامل.
     *  نگاشتِ پیشین دست نمی‌خورد (صفحه‌ای که نمایهٔ پیشین را دارد همان را می‌بیند).
     */
    fun grow(sales: List<Sale>, items: List<SaleItem>): Counted? {
      val newSales = ir.vil3ntec.tohid.core.ledger.AppendTail.of(this.sales, sales) ?: return null
      val newItems = ir.vil3ntec.tohid.core.ledger.AppendTail.of(this.items, items) ?: return null
      val newCancelled = newSales.filter { it.status == "cancelled" }.map { it.id }
      if (newCancelled.any { it in itemSales }) return null
      if (newItems.any { it.saleId in itemSales }) return null
      //  از این‌جا به بعد هیچ بازگشتی نیست ⇒ دو مجموعهٔ همین خانه درجا بزرگ می‌شوند (خانهٔ پیشین دیگر خوانده نمی‌شود)
      cancelled += newCancelled
      if (newItems.isEmpty()) return Counted(sales, items, counts, cancelled, itemSales)
      val out = HashMap(counts)
      val seen = HashSet<Pair<String, String>>()
      for (it in newItems) {
        itemSales += it.saleId
        if (it.productId.isBlank() || it.saleId in cancelled) continue
        if (!seen.add(it.saleId to it.productId)) continue
        out[it.productId] = (out[it.productId] ?: 0) + 1
      }
      return Counted(sales, items, out, cancelled, itemSales)
    }
  }

  private var counted: Counted? = null

  /**
   *  POS-42 — [popularity] برای نمایهٔ صندوقِ گوشی (`CatalogModel.index`) که با **هر فروش** از نو ساخته می‌شود: شمارِ همهٔ
   *  ۱۵۰ هزار قلم نیمی از «نخستین اسکن پس از فروش» بود. یک خانه (یک دفتر روی صفحه)؛ دفترِ همان خانه به‌اضافهٔ فروش‌های
   *  تازه ⇒ فقط اقلامِ تازه شمرده می‌شوند، وگرنه کامل. نتیجه مو‌به‌مو همان [popularity] است (`CatalogCountsTest`).
   */
  fun counts(sales: List<Sale>, items: List<SaleItem>): Map<String, Int> = locked {
    val next = counted?.grow(sales, items) ?: Counted.build(sales, items)
    counted = next
    next.counts
  }

  /** یک قفل برای هر دو خانهٔ این شیء ([counts] و کلیدهای نرمال) — همان قفلِ پیشینِ `keys` */
  @Synchronized
  private fun <T> locked(block: () -> T): T = block()

  /**
   *  فقط کالاهای فعال؛ یک بار با هر تغییرِ کالاها یا فروش‌ها. [codes] (POS-01/PRD-03، پشتِ `ProductInfoPrefs`): کدِ کالا (SKU)
   *  هم مثلِ بارکد — کامل اول، بخشی از آن در نتیجه‌ها؛ خاموش ⇒ همان بارکدها.
   */
  fun index(products: List<Product>, popularity: Map<String, Int>, codes: Boolean = false): Index {
    val entries = keys(products, codes)
      .map { it.copy(sold = popularity[it.id] ?: 0) }
      .sortedWith(compareByDescending<Entry> { it.sold }.thenBy { it.nameKey }.thenBy { it.id })
    return Index(entries)
  }

  /*
   *  POS-42 — کلیدهای نرمالِ کالاهای فعال (`sold` = ۰، به ترتیبِ فهرستِ کالا) فقط با تغییرِ **کالاها** از نو: نمایه با هر
   *  فروش هم از نو ساخته می‌شود (پرفروش‌ترین اول)، ولی نامِ ۲۰ هزار کالا با فروش عوض نمی‌شود و نرمال کردنشان نیمی از
   *  «نخستین اسکن پس از فروش» در گوشی بود. یک خانه بس است (یک دفتر روی صفحه). کلیدِ خانه رونوشتِ همان فهرست است و
   *  برابری محتوایی سنجیده می‌شود (همان `remember(d.products)` ِ صفحه): فهرستِ دیگری با همان کالاها ⇒ همان کلیدها؛ هر کالای
   *  عوض‌شده ⇒ از نو. نتیجه مو‌به‌مو همان ساختنِ کامل است (`CatalogSearchTest`).
   */
  private var keyedFrom: List<Product>? = null
  private var keyedCodes = false
  private var keyed: List<Entry> = emptyList()

  private fun keys(products: List<Product>, sku: Boolean): List<Entry> = locked { keysLocked(products, sku) }

  private fun keysLocked(products: List<Product>, sku: Boolean): List<Entry> {
    if (keyedFrom == products && keyedCodes == sku) return keyed
    val fresh = products.filter { it.active }.map { p ->
      val raw = if (sku) p.barcodes + listOfNotNull(p.sku?.takeIf { it.isNotBlank() }) else p.barcodes
      Entry(
        id = p.id,
        category = p.category,
        nameKey = TextNormalizer.compareKey(p.name),
        codes = raw.map { TextNormalizer.compareKey(it) }.filter { it.isNotEmpty() },
        sold = 0,
      )
    }
    keyedFrom = products.toList()
    keyedCodes = sku
    keyed = fresh
    return fresh
  }

  /** پرفروش‌ترین‌ها برای ردیفِ «پرفروش‌ها» — کالایی که هرگز فروخته نشده نه */
  fun top(index: Index, n: Int = 8): List<String> =
    index.entries.asSequence().filter { it.sold > 0 }.take(n).map { it.id }.toList()

  /**
   *  شناسه‌های جورشده به ترتیبِ نمایش. `category` = null ⇐ همهٔ دسته‌ها؛ پرس‌وجوی خالی
   *  ⇐ همهٔ کالاهای همان دسته به ترتیبِ پرفروش‌ترین.
   */
  fun search(index: Index, query: String, category: String?, synonyms: Synonyms = Synonyms.NONE): List<String> {
    val q = TextNormalizer.searchKey(query).trim()
    val inCategory = index.entries.asSequence().filter { category == null || it.category == category }
    if (q.isEmpty()) return inCategory.map { it.id }.toList()
    val whole = q.replace(" ", "")
    val tokens = q.split(' ').filter { it.isNotEmpty() }
    //  POS-04: هر کلمه با هم‌گروه‌هایش؛ بی فهرست هر کدام فقط خودش (همان جست‌وجوی پیشین)
    val wholeAlts = synonyms.of(TextNormalizer.compareKey(whole))
    val tokenAlts = tokens.map { synonyms.of(TextNormalizer.compareKey(it)) }
    val exact = ArrayList<String>()
    val rest = ArrayList<String>()
    for (e in inCategory) {
      when {
        e.codes.any { it == whole } -> exact += e.id
        e.nameKey.contains(whole) || tokens.all { e.nameKey.contains(it) } -> rest += e.id
        !synonyms.isEmpty && (wholeAlts.any { e.nameKey.contains(it) } || tokenAlts.all { a -> a.any { e.nameKey.contains(it) } }) ->
          rest += e.id
        whole.length >= 3 && e.codes.any { it.contains(whole) } -> rest += e.id
      }
    }
    return exact + rest
  }
}

/**
 *  ورودیِ صفحه‌کلیدِ مقدار/وزن — خالص. فقط رقم، یک «.» (اگر `decimals`)، تا سه رقمِ
 *  اعشار و ۷ رقمِ درست؛ «⌫» یکی پاک می‌کند. صفرِ بی‌معنیِ آغاز («007») برداشته می‌شود.
 */
object KeypadInput {
  const val BACKSPACE = '⌫'

  fun press(text: String, key: Char, decimals: Boolean): String {
    if (key == BACKSPACE) return text.dropLast(1)
    if (key == '.') {
      if (!decimals || text.contains('.')) return text
      return if (text.isEmpty()) "0." else "$text."
    }
    if (!key.isDigit()) return text
    val dot = text.indexOf('.')
    if (dot >= 0 && text.length - dot - 1 >= 3) return text
    if (dot < 0 && text.length >= 7) return text
    if (text == "0") return key.toString()
    return text + key
  }

  /** مقدارِ متن؛ خالی یا «.» ⇐ ۰ */
  fun value(text: String): Double = text.toDoubleOrNull() ?: 0.0
}
