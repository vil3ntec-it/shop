package ir.vil3ntec.tohid.desktop

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.desktop.shots.MoneyDemo
import java.io.File

/**
 *  کادرِ ردیفِ صندوقِ **گوشیِ نسخهٔ ۳** (`sale/line/{i}` ⇐ `LineRoute` ⇐ `LineSheet`) در پنجرهٔ واقعیِ Compose ([RouteUiHarness]):
 *  سبدِ روی دیسک (`CartStore`) پیش از باز شدن و پس از «ثبت» — همان سبدی که صندوق می‌خواند. پس از هر آزمون [close].
 */
@OptIn(ExperimentalTestApi::class)
internal class LineSheetHarness(private val rule: ComposeContentTestRule) : AutoCloseable {
  val ctx = DesktopContext
  val phone = Product(id = "p-phone-x", name = "گوشیِ آزمونی", unit = "دانه", salePrice = 5000.0, serialTracked = true)
  val data = MoneyDemo.full().let { it.copy(products = it.products + phone) }
  val rice = data.products.first { it.id == "p-rice" }
  val h = RouteUiHarness(rule)

  init { File(ctx.filesDir, CartStore.FILE).delete() }

  /** سبد با همین ردیف‌ها و برگهٔ ردیفِ نخست */
  fun open(vararg lines: SalesEngine.CartLine) {
    seed(lines.toList())
    h.show("sale/line/0", data)
    runCatching { h.await(StrSale.unitPrice) }.onFailure { throw AssertionError("برگهٔ ردیف باز نشد؛ سبد: ${line()} — روی صفحه: ${shown()}", it) }
  }

  /** نوشته‌های روی صفحه (همهٔ ریشه‌ها، برگه‌ها هم) — برای پیامِ شکست */
  fun shown(): List<String> = rule.onAllNodes(SemanticsMatcher("any") { true }, useUnmergedTree = true).fetchSemanticsNodes()
    .mapNotNull { n -> n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } }.filter { it.isNotBlank() }.take(60)

  fun line(): SalesEngine.CartLine? = CartStore(ctx).lines.value.firstOrNull()

  companion object {
    /**
     *  سبدِ روی دیسک با همین ردیف‌ها. ⛔ نوشتنِ پیش‌نویس ناهم‌زمان است (`CartStore.write` روی رشتهٔ نویسنده) ⇒ تا روی دیسک ننشسته
     *  صفحه باز نشود، وگرنه `CartStore` ِ صفحه سبدِ خالی می‌خواند و برگهٔ ردیف «ردیف دیگر نیست» می‌گوید (شکستِ گاه‌به‌گاه).
     */
    fun seed(lines: List<SalesEngine.CartLine>) {
      CartStore(DesktopContext).set(lines)
      val until = System.currentTimeMillis() + 10_000
      while (CartStore(DesktopContext).lines.value != lines && System.currentTimeMillis() < until) Thread.sleep(20)
      check(CartStore(DesktopContext).lines.value == lines) { "سبد روی دیسک ننشست" }
    }
  }

  /** کادرِ نوشتن با همین برچسب (`V3TextField`: توضیحِ محتوا؛ کادرِ Material: نوشتهٔ برچسب) — جای متنِ پیشین */
  fun type(label: String, value: String) {
    val field: SemanticsMatcher = hasSetTextAction() and (hasContentDescription(label) or hasText(label, substring = true))
    rule.onAllNodes(field)[0].performTextReplacement(value)
    rule.waitForIdle()
  }

  fun save() {
    rule.onAllNodes(hasText(StrSale.submit) and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
  }

  override fun close() {
    h.close()
    File(ctx.filesDir, CartStore.FILE).delete()
  }
}
