package ir.vil3ntec.tohid.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrLedger
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrReturnPages
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.StrSaleTax
import ir.vil3ntec.tohid.core.sale.CheckoutRules
import ir.vil3ntec.tohid.core.sale.SaleTax
import ir.vil3ntec.tohid.data.CheckoutRulesPref
import ir.vil3ntec.tohid.data.ExpiryPref
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SaleTaxPref
import ir.vil3ntec.tohid.data.SaleTaxWiring
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.TillGuards
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.screens.ReturnFlowDialog
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 *  فروشِ «تعویض» در پنجرهٔ واقعیِ Compose ِ **برگشتِ کامپیوتر** (`ReturnFlowDialog`، `ReturnsV2`) با سدهای صندوقِ همین دستگاه
 *  (`TillGuards`): S22 «منقضی فقط با مجوز» ⇒ «ثبتِ تعویض» با کالای منقضی همان پنجرهٔ PIN ِ `DeskSale` را باز می‌کند و بی تأیید
 *  هیچ چیز نوشته نمی‌شود (بستن ⇒ پیام)؛ تأیید ⇒ همان ثبت با ردیفِ سابقهٔ `expired_sale`. POS-24 «فاکتورِ صفر» تعویض را هم می‌بندد.
 *  ⛔ روی `claude/batch-56` (0d06aa8d5) دیالوگ `SalesEngine.Checkout(seller = me)` ِ خام می‌ساخت ⇒ تعویض بی پرسش ثبت می‌شد.
 */
@OptIn(ExperimentalTestApi::class)
class ExchangeGuardsDeskUiTest {

  @get:Rule val rule = createComposeRule()

  private val ctx = DesktopContext
  private val shoe = Product(id = "x-shoe", name = "کفشِ آزمونی", unit = "جفت", salePrice = 1000.0)
  private val milk = Product(id = "x-milk", name = "شیرِ کهنه", unit = "عدد", salePrice = 100.0)
  private val gift = Product(id = "x-gift", name = "هدیهٔ رایگان", unit = "عدد", salePrice = 0.0)
  private val base = ShopData(
    products = listOf(shoe, milk, gift),
    warehouseEntries = listOf(
      WarehouseEntry(id = "x-w1", productId = shoe.id, units = 3.0),
      WarehouseEntry(id = "x-w2", productId = gift.id, units = 3.0),
      //  همهٔ شیر منقضی است ⇒ هر دانه تأیید می‌خواهد
      WarehouseEntry(id = "x-old", productId = milk.id, units = 2.0, expiryDate = "2020-01-01"),
    ),
  )
  private val sold = SalesEngine.record(base, listOf(SalesEngine.CartLine(shoe.id, 1.0)), SalesEngine.Checkout(seller = "صندوقدار"), "2020-01-02", 1L, { "x-${seq++}" })
  private val data = (sold as SalesEngine.Result.Ok).data
  private val sale = data.sales.single()
  private lateinit var store: ShopStore
  private val posted = mutableListOf<String>()
  private val notes = mutableListOf<String>()

  private companion object { var seq = 0 }

  private fun clean() {
    ExpiryPref.setApproval(ctx, false)
    ShopPolicy.setBlockExpired(ctx, false)
    CheckoutRulesPref.set(ctx, CheckoutRules.OFF)
    SaleTaxPref.set(ctx, null)
    SaleTaxWiring.serverFlag = false
    ctx.filesDir.listFiles().orEmpty().filter { it.name.startsWith("shop-data.json") }.forEach { it.deleteRecursively() }
    ShopRole.remember(ctx, ShopRole.OWNER)
  }

  @Before fun up() {
    I18n.source = { Lang.FA }
    clean()
  }

  @After fun down() = clean()

  private fun show() {
    store = ShopStore(ctx).also { s -> runBlocking { s.load(); assertTrue(s.replaceAll(data)) } }
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) {
          ReturnFlowDialog(data, sale, store, onDismiss = {}, onPosted = { doc, note -> posted += doc; notes += note }, prefill = true)
        }
      }
    }
    rule.waitUntil(15_000) { has(StrSale.exchangeToggle) }
  }

  private fun has(text: String, substring: Boolean = false) = rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

  private fun tap(m: androidx.compose.ui.test.SemanticsMatcher) {
    rule.onAllNodes(m)[0].performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
  }

  /** تعویضِ یک دانه از [p] به جای کفشِ فاکتور ⇒ «ثبتِ تعویض» */
  private fun exchangeFor(p: Product) {
    pick(p)
    tap(hasText(StrSale.recordExchange) and hasClickAction())
  }

  /** کلیدِ «تعویض» و افزودنِ یک دانه از [p] به سبدِ تعویض */
  private fun pick(p: Product) {
    tap(isToggleable() and hasAnySibling(hasText(StrSale.exchangeToggle)))
    rule.waitUntil(10_000) { rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= 2 }
    rule.onAllNodes(hasSetTextAction()).let { it[it.fetchSemanticsNodes().size - 1] }.performTextInput(p.name)
    rule.waitForIdle()
    rule.waitUntil(10_000) { rule.onAllNodes(hasText(p.name) and hasClickAction() and !hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
    tap(hasText(p.name) and hasClickAction() and !hasSetTextAction())
  }

  private fun sales() = store.data.value.sales.size

  @Test fun anExpiredExchangeAsksForTheManagerPinAndOnlyThenIsRecorded() {
    ExpiryPref.setApproval(ctx, true)
    show()
    exchangeFor(milk)
    runCatching { rule.waitUntil(10_000) { has(StrPos.expiredSaleTitle) } }
      .onFailure { throw AssertionError("پنجرهٔ تأییدِ منقضی باز نشد؛ فروش‌ها: ${sales()}، ثبت: $posted", it) }
    assertEquals("بی تأیید هیچ ثبتی", 1, sales())
    assertTrue(posted.isEmpty())

    //  بستن بی تأیید ⇒ پیامِ روشن و باز هیچ ثبت
    tap(hasText(Str.cancel) and hasClickAction() and hasAnySibling(hasText(StrPos.creditApproveSelf)))
    val refusal = TillGuards.expiredRefusal(data, mapOf(milk.id to 1_000L))
    rule.waitUntil(10_000) { has(refusal) }
    assertEquals(1, sales())

    //  دوباره «ثبتِ تعویض» ⇒ همان پنجره؛ صاحبِ پشتِ صندوق با دلیل تأیید می‌کند ⇒ همان تعویض ثبت می‌شود
    tap(hasText(StrSale.recordExchange) and hasClickAction())
    rule.waitUntil(10_000) { has(StrPos.expiredSaleTitle) }
    rule.onAllNodes(hasSetTextAction() and (hasContentDescription(StrPos.reasonKept) or hasText(StrPos.reasonKept)))[0].performTextInput("مشتری خودش خواست")
    rule.waitForIdle()
    tap(hasText(StrPos.creditApproveSelf) and hasClickAction())
    rule.waitUntil(15_000) { posted.isNotEmpty() }
    assertEquals(2, sales())
    val row = store.data.value.auditLog.single { it.type == "expired_sale" }
    assertEquals("مشتری خودش خواست", row.reason)
    assertTrue(row.approvedBy.orEmpty().isNotBlank())
  }

  /**
   *  POS-37 (`claude/e-rows-37e`): با مالیاتِ روشنِ همین دستگاه پیش‌نمایشِ تعویض ردیفِ «مالیات» و تفاوتِ با مالیات را نشان
   *  می‌دهد و فاکتورِ تعویض همان مالیات را ثبت می‌کند. ⛔ روی e69b30114 نه ردیف بود، نه مالیات: «دریافت از مشتری ۰».
   */
  @Test fun theExchangePreviewAndInvoiceCarryTheTillTax() {
    SaleTaxWiring.serverFlag = true
    SaleTaxPref.set(ctx, SaleTax.Config(SaleTax.Mode.INVOICE, 1_000)) // ۱۰٪
    show()
    pick(shoe)
    runCatching { rule.waitUntil(10_000) { has(StrSaleTax.line) } }
      .onFailure { throw AssertionError("ردیفِ مالیات در پیش‌نمایشِ تعویض نیامد", it) }
    val due = hasText("${money(100.0)} ؋") and hasAnySibling(hasText(StrSale.receiveFromCustomer))
    assertTrue("کفشِ ۱۰۰۰ + ۱۰۰ مالیات − برگشتِ نقدِ ۱۰۰۰ ⇒ دریافتِ ۱۰۰", rule.onAllNodes(due).fetchSemanticsNodes().isNotEmpty())
    tap(hasText(StrSale.recordExchange) and hasClickAction())
    rule.waitUntil(15_000) { posted.isNotEmpty() }
    val ex = store.data.value.sales.last()
    assertEquals(10_000L, ex.taxMinor)
    assertEquals(1_100.0, ex.finalTotal, 0.0)
    assertEquals(StrReturnPages.exchangeReceive(money(100.0)), notes.single())
  }

  @Test fun theZeroTotalRuleBlocksAnExchangeForAFreeItem() {
    CheckoutRulesPref.set(ctx, CheckoutRules.of(null, true))
    show()
    exchangeFor(gift)
    runCatching { rule.waitUntil(10_000) { has(StrLedger.zeroTotalRule) } }
      .onFailure { throw AssertionError("قاعدهٔ «فاکتورِ صفر» نگرفت؛ فروش‌ها: ${sales()}، ثبت: $posted", it) }
    assertEquals(1, sales())
    assertFalse(has(StrPos.expiredSaleTitle))
  }
}
