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
 */
object CatalogSearch {

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
  fun popularity(sales: List<Sale>, items: List<SaleItem>): Map<String, Int> {
    val cancelled = HashSet<String>()
    for (s in sales) if (s.status == "cancelled") cancelled += s.id
    val seen = HashSet<Pair<String, String>>()
    val out = HashMap<String, Int>()
    for (it in items) {
      if (it.productId.isBlank() || it.saleId in cancelled) continue
      //  دو ردیفِ یک کالا در یک فاکتور یک فروش است
      if (!seen.add(it.saleId to it.productId)) continue
      out[it.productId] = (out[it.productId] ?: 0) + 1
    }
    return out
  }

  /** فقط کالاهای فعال؛ یک بار با هر تغییرِ کالاها یا فروش‌ها */
  fun index(products: List<Product>, popularity: Map<String, Int>): Index {
    val entries = products.asSequence()
      .filter { it.active }
      .map { p ->
        Entry(
          id = p.id,
          category = p.category,
          nameKey = TextNormalizer.compareKey(p.name),
          codes = p.barcodes.map { TextNormalizer.compareKey(it) }.filter { it.isNotEmpty() },
          sold = popularity[p.id] ?: 0,
        )
      }
      .sortedWith(compareByDescending<Entry> { it.sold }.thenBy { it.nameKey }.thenBy { it.id })
      .toList()
    return Index(entries)
  }

  /** پرفروش‌ترین‌ها برای ردیفِ «پرفروش‌ها» — کالایی که هرگز فروخته نشده نه */
  fun top(index: Index, n: Int = 8): List<String> =
    index.entries.asSequence().filter { it.sold > 0 }.take(n).map { it.id }.toList()

  /**
   *  شناسه‌های جورشده به ترتیبِ نمایش. `category` = null ⇐ همهٔ دسته‌ها؛ پرس‌وجوی خالی
   *  ⇐ همهٔ کالاهای همان دسته به ترتیبِ پرفروش‌ترین.
   */
  fun search(index: Index, query: String, category: String?): List<String> {
    val q = TextNormalizer.searchKey(query).trim()
    val inCategory = index.entries.asSequence().filter { category == null || it.category == category }
    if (q.isEmpty()) return inCategory.map { it.id }.toList()
    val whole = q.replace(" ", "")
    val tokens = q.split(' ').filter { it.isNotEmpty() }
    val exact = ArrayList<String>()
    val rest = ArrayList<String>()
    for (e in inCategory) {
      when {
        e.codes.any { it == whole } -> exact += e.id
        e.nameKey.contains(whole) || tokens.all { e.nameKey.contains(it) } -> rest += e.id
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
