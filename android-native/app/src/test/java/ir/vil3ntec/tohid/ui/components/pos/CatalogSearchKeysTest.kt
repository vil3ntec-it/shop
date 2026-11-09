package ir.vil3ntec.tohid.ui.components.pos

import ir.vil3ntec.tohid.core.text.TextNormalizer
import ir.vil3ntec.tohid.data.Product
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/**
 *  POS-42 — `CatalogSearch.index` کلیدهای نرمالِ کالاها را فقط با تغییرِ **کالاها** از نو می‌سازد (نه با هر فروش). این
 *  آزمون نمایهٔ تازه را با رونوشتِ مو‌به‌موی ساختنِ پیشین می‌سنجد — همان ردیف‌ها به همان ترتیب، و همان نتیجهٔ جست‌وجو — در
 *  همهٔ راه‌های عوض شدنِ ورودی: همان فهرست با فروشِ دیگر، فهرستِ برابرِ دیگر، کالای عوض‌شده، رفت‌وبرگشت میانِ دو فهرست و
 *  فهرستی که درجا عوض شد.
 */
class CatalogSearchKeysTest {

  /** رونوشتِ ساختنِ پیشین (پیش از POS-42) — دست نخورد */
  private fun oldEntries(products: List<Product>, popularity: Map<String, Int>): List<CatalogSearch.Entry> =
    products.asSequence()
      .filter { it.active }
      .map { p ->
        CatalogSearch.Entry(
          id = p.id,
          category = p.category,
          nameKey = TextNormalizer.compareKey(p.name),
          codes = p.barcodes.map { TextNormalizer.compareKey(it) }.filter { it.isNotEmpty() },
          sold = popularity[p.id] ?: 0,
        )
      }
      .sortedWith(compareByDescending<CatalogSearch.Entry> { it.sold }.thenBy { it.nameKey }.thenBy { it.id })
      .toList()

  private val words = listOf("برنج", "روغن آفتاب‌گردان", "چاي", "كيك", "شیر ۵ لیتری", "Coca Cola", "صابون", "برنج هندی", "قروت")
  private val cats = listOf("خوراکی", "نوشیدنی", "بهداشتی")

  private fun products(seed: Int, n: Int = 300): List<Product> {
    val r = Random(seed)
    return List(n) { i ->
      Product(
        id = "p${r.nextInt(1_000_000)}-$i",
        name = "${words[r.nextInt(words.size)]} ${r.nextInt(40)}",
        category = cats[r.nextInt(cats.size)],
        barcodes = when (r.nextInt(4)) {
          0 -> emptyList()
          1 -> listOf("626${100_000 + i}")
          2 -> listOf("۶۲۶${i}", " ")
          else -> listOf("626${100_000 + i}", "590$i")
        },
        active = r.nextInt(10) != 0,
      )
    }
  }

  private fun popularity(products: List<Product>, seed: Int): Map<String, Int> {
    val r = Random(seed)
    return products.filter { r.nextInt(3) != 0 }.associate { it.id to r.nextInt(5) }
  }

  private val queries = listOf("", "بر", "برنج", "چای", "کیک", "626", "۶۲۶۱", "cola", "۵", "قروت ۳")

  private fun same(products: List<Product>, pop: Map<String, Int>, why: String) {
    val want = oldEntries(products, pop)
    val got = CatalogSearch.index(products, pop)
    assertEquals(why, want, got.entries)
    val ref = CatalogSearch.Index(want)
    for (q in queries) for (c in listOf(null, "خوراکی")) {
      assertEquals("$why q=$q c=$c", CatalogSearch.search(ref, q, c), CatalogSearch.search(got, q, c))
    }
  }

  @Test fun `همان نمایه و همان جست‌وجو در هر راهِ عوض شدنِ کالاها و فروش‌ها`() {
    for (seed in 1..40) {
      val a = products(seed)
      same(a, popularity(a, seed), "seed=$seed نخستین")
      //  فروشِ تازه، همان کالاها (کلیدها از خانه)
      same(a, popularity(a, seed + 1000), "seed=$seed فروشِ دیگر")
      //  فهرستِ تازه با همان کالاها
      same(a.toList(), popularity(a, seed), "seed=$seed فهرستِ برابر")
      //  یک کالا عوض شد: نام، بارکد، فعال بودن
      val k = seed % a.size
      val renamed = a.mapIndexed { i, p -> if (i == k) p.copy(name = p.name + " نو") else p }
      same(renamed, popularity(renamed, seed), "seed=$seed نامِ عوض‌شده")
      val recoded = a.mapIndexed { i, p -> if (i == k) p.copy(barcodes = listOf("999$i")) else p }
      same(recoded, popularity(recoded, seed), "seed=$seed بارکدِ عوض‌شده")
      val toggled = a.mapIndexed { i, p -> if (i == k) p.copy(active = !p.active) else p }
      same(toggled, popularity(toggled, seed), "seed=$seed فعال/غیرفعال")
      //  رفت‌وبرگشت میانِ دو فهرست
      val b = products(seed + 500, n = 200)
      same(b, popularity(b, seed), "seed=$seed فهرستِ دیگر")
      same(a, popularity(a, seed), "seed=$seed بازگشت")
      //  همان شیءِ فهرست، درجا عوض شد
      val m = a.toMutableList()
      same(m, popularity(m, seed), "seed=$seed پیش از تغییرِ درجا")
      m[k] = m[k].copy(name = "دیگر")
      m.removeAt(m.size - 1)
      same(m, popularity(m, seed), "seed=$seed پس از تغییرِ درجا")
    }
    same(emptyList(), emptyMap(), "فهرستِ خالی")
  }
}
