package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import ir.vil3ntec.tohid.core.nav.AppMode
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.desktop.PurchaseDemo
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.v3.V3Anatomy
import ir.vil3ntec.tohid.ui.v3.V3DataRowSpec
import ir.vil3ntec.tohid.ui.v3.V3NavRowSpec
import ir.vil3ntec.tohid.ui.v3.V3_STATE_BLOCK
import ir.vil3ntec.tohid.ui.v3.nav.RouteKind
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules

/**
 *  ابزارِ مشترکِ ممیزیِ کالبد (`PhoneAnatomyAuditTest`) و نگهبانش (`PageAnatomyUiTest`): فهرستِ همهٔ صفحه‌های `TAB`/`PAGE` ِ
 *  حالتِ دکان از جدولِ یکتای مسیرها با پارامترِ نمایشی و دفترِ نمایشیِ هر بخش، و شمارشِ کالبدِ یک درختِ معنایی از نشان‌های
 *  `V3Anatomy` ِ خودِ اجزای مشترک.
 */
internal object AnatomyProbe {

  /** یک صفحه برای ممیزی: نامِ پرونده، مسیرِ باز، الگو، گونه، ماژول، سربرگ‌کِش و دفترِ نمایشی */
  class Page(val name: String, val path: String, val pattern: String, val kind: RouteKind, val module: String, val chrome: String, val data: () -> ShopData)

  /** کالبدِ شمرده: شمارِ هر نشان، عنوانِ نوار و نوشته‌های درشتِ (≥ ۲۰sp) بیرون از نوار/خلاصه/عنوانِ درشت */
  class Census(val counts: Map<String, Int>, val barTitles: List<String>, val bigText: List<String>) {
    fun n(tag: String) = counts[tag] ?: 0
    val page get() = n(V3Anatomy.PAGE)
    val largeTitle get() = n(V3Anatomy.LARGE_TITLE)
    val summary get() = n(V3Anatomy.SUMMARY)
    val fab get() = n(V3Anatomy.FAB)
    val chips get() = n(V3Anatomy.CHIPS)
    val card get() = n(V3Anatomy.CARD)
    val dataRow get() = n(V3DataRowSpec.ROW_TAG)
    val navRow get() = n(V3NavRowSpec.ROW_TAG)
    val listRow get() = n(V3Anatomy.LIST_ROW)
    val state get() = n(V3_STATE_BLOCK)
  }

  fun merged(): ShopData {
    val a = MoneyDemo.full()
    val b = StockDemo.full()
    return a.copy(
      products = a.products + b.products, productCategories = (a.productCategories + b.productCategories).distinct(),
      productUnits = (a.productUnits + b.productUnits).distinct(), warehouseEntries = a.warehouseEntries + b.warehouseEntries,
      sales = a.sales + b.sales, saleItems = a.saleItems + b.saleItems, stockMovements = a.stockMovements + b.stockMovements,
    )
  }

  /** پارامترِ نمایشیِ هر الگو — شناسه‌های همان دفترها؛ الگوی پرنشده ⇒ null */
  fun fill(pattern: String): String? {
    val id = when {
      pattern.startsWith("products/") || pattern.startsWith("stock/") || pattern.startsWith("storefront/products/") -> StockDemo.RICE
      pattern.startsWith("debtors/") -> MoneyDemo.OWING
      pattern.startsWith("expenses/") -> "e1"
      pattern.startsWith("purchasing/suppliers/") -> PurchaseDemo.ALBORZ
      else -> "1"
    }
    val p = pattern.replace("{id}", id).replace("{saleId}", HistoryDemo.CASH).replace("{docId}", "1").replace("{key}", "1")
      .replace("{i}", "0").replace("{txId}", "t1").replace("{entryId}", "e1").replace("{productId}", StockDemo.RICE)
      .replace("{orderId}", "1").replace("{conversationId}", "1").replace("{keep}", MoneyDemo.OWING).replace("{drop}", MoneyDemo.SETTLED)
      .replace("{old}", "1")
    return p.takeIf { '{' !in it }
  }

  fun dataFor(path: String): () -> ShopData = when {
    path.startsWith("purchasing") -> { { PurchaseDemo.full() } }
    path == "sales" || path.startsWith("sales/") -> { { HistoryDemo.busy() } }
    else -> { { merged() } }
  }

  /** همهٔ صفحه‌ها؛ [only] ⇒ فقط مسیرهایی که یکی از این‌ها را دارند */
  fun pages(only: List<String> = emptyList()): List<Page> {
    val list = ArrayList<Page>()
    val seen = HashSet<String>()
    for (m in V3Modules.ALL.filter { AppMode.SHOP in it.modes }) {
      for (r in m.routes) {
        if (r.kind != RouteKind.TAB && r.kind != RouteKind.PAGE) continue
        val paths = when (r.pattern) {
          "settings/{group}" -> listOf(SettingsLayout.SHOP, SettingsLayout.SALES, SettingsLayout.GOODS, SettingsLayout.DEVICES, SettingsLayout.DATA, SettingsLayout.LOOK)
            .map { "settings/$it" }
          "settings/{group}/{section}" -> emptyList()
          else -> listOfNotNull(fill(r.pattern))
        }
        for (path in paths) {
          if (!seen.add(path)) continue
          val name = path.replace(Regex("[^A-Za-z0-9\\u0600-\\u06FF]+"), "_").trim('_').ifEmpty { "root" }
          list += Page(name, path, r.pattern, r.kind, m.id, r.chrome.name, dataFor(path))
        }
      }
    }
    return list.filter { p -> only.isEmpty() || only.any { p.path.contains(it) } }
  }

  private fun layout(n: SemanticsNode): TextLayoutResult? {
    val get = n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action ?: return null
    val l = ArrayList<TextLayoutResult>()
    return if (runCatching { get(l) }.getOrDefault(false)) l.firstOrNull() else null
  }

  /** کالبدِ همهٔ ریشه‌ها (صفحه و برگه‌های رویش) */
  fun census(roots: List<SemanticsNode>): Census {
    val counts = HashMap<String, Int>()
    val big = ArrayList<String>()
    val bar = ArrayList<String>()
    fun walk(n: SemanticsNode, inside: Set<String>) {
      val tag = n.config.getOrNull(SemanticsProperties.TestTag)
      if (tag != null) counts[tag] = (counts[tag] ?: 0) + 1
      val here = if (tag != null) inside + tag else inside
      val text = n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
      if (!text.isNullOrBlank()) {
        if (V3Anatomy.BAR in here && bar.size < 3 && n.config.getOrNull(SemanticsProperties.Heading) != null) bar += text
        val sp = layout(n)?.layoutInput?.style?.fontSize?.value ?: 0f
        val framed = V3Anatomy.BAR in here || V3Anatomy.SUMMARY in here || V3Anatomy.LARGE_TITLE in here
        if (sp >= 20f && !framed && big.size < 6) big += "$text (${sp.toInt()}sp)"
      }
      n.children.forEach { walk(it, here) }
    }
    roots.forEach { walk(it, emptySet()) }
    return Census(counts, bar, big)
  }
}
