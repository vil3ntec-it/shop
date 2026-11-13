package ir.vil3ntec.tohid.desktop

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.ExpiryPref
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.TillGuards
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.ui.screens.ReturnsShot
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  فروشِ «تعویض» در پنجرهٔ واقعیِ Compose ِ **گوشیِ نسخهٔ ۳** — مسیرِ `sales/{id}/return` (`ReturnRoute` ⇐ `ReturnFlowSheet`) با
 *  `ShellContext` ِ واقعی ([RouteUiHarness]) و S22 «منقضی فقط با مجوز» ِ روشن: «ثبتِ تعویض» با کالای منقضی ⇒ همان برگهٔ PIN ِ
 *  صندوق (`CreditApprovalSheet`، عنوانِ «فروشِ کالای منقضی») و هیچ ثبت؛ «انصراف» ⇒ پیامِ روشن؛ تأییدِ صاحب با دلیل ⇒ همان تعویض
 *  خودش ثبت می‌شود و ردیفِ سابقهٔ `expired_sale` می‌ماند.
 *  ⛔ روی `claude/batch-56` (0d06aa8d5) برگه `SalesEngine.Checkout(seller = me)` ِ خام می‌ساخت ⇒ تعویض بی پرسش ثبت می‌شد.
 */
@OptIn(ExperimentalTestApi::class)
class ExchangeGuardsPhoneUiTest {

  @get:Rule val rule = createComposeRule()

  private val ctx = DesktopContext
  private val shoe = Product(id = "y-shoe", name = "کفشِ آزمونی", unit = "جفت", salePrice = 1000.0)
  private val milk = Product(id = "y-milk", name = "شیرِ کهنه", unit = "عدد", salePrice = 100.0)
  private val base = ShopData(
    products = listOf(shoe, milk),
    warehouseEntries = listOf(
      WarehouseEntry(id = "y-w1", productId = shoe.id, units = 3.0),
      WarehouseEntry(id = "y-old", productId = milk.id, units = 2.0, expiryDate = "2020-01-01"),
    ),
  )
  private var seq = 0
  private val data = (SalesEngine.record(base, listOf(SalesEngine.CartLine(shoe.id, 1.0)), SalesEngine.Checkout(seller = "صندوقدار"), "2020-01-02", 1L, { "y-${seq++}" }) as SalesEngine.Result.Ok).data
  private val sale = data.sales.single()
  private lateinit var h: RouteUiHarness

  private fun clean() {
    ExpiryPref.setApproval(ctx, false)
    ShopPolicy.setBlockExpired(ctx, false)
    ReturnsShot.open.value = null
    ctx.filesDir.listFiles().orEmpty().filter { it.name.startsWith("shop-data.json") }.forEach { it.deleteRecursively() }
    ShopRole.remember(ctx, ShopRole.OWNER)
  }

  @Before fun up() {
    clean()
    ExpiryPref.setApproval(ctx, true)
    //  «برگشت و تعویض» ِ تازه با یک دانه از قلمِ نخست و دلیلِ نخست از پیش پر (همان عکسِ CI)
    ReturnsShot.open.value = "first"
    h = RouteUiHarness(rule)
  }

  @After fun down() { h.close(); clean() }

  private fun tap(m: SemanticsMatcher) {
    rule.onAllNodes(m)[0].performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
  }

  @Test fun anExpiredExchangeOpensThePinSheetAndOnlyTheApprovalRecordsIt() {
    val store = ShopStore(ctx).also { s -> runBlocking { s.load(); assertTrue(s.replaceAll(data)) } }
    h.show("sales/${sale.id}/return", store)
    h.await(StrSale.exchangeToggle)
    tap(hasText(StrSale.exchangeToggle) and hasClickAction())
    h.await(StrSale.newItemSearch)
    h.type(StrSale.newItemSearch, milk.name)
    val hit = hasText(milk.name) and hasClickAction() and !hasSetTextAction()
    h.awaitThat { rule.onAllNodes(hit).fetchSemanticsNodes().isNotEmpty() }
    tap(hit)
    tap(hasText(StrSale.recordExchange) and hasClickAction())

    runCatching { h.await(StrPos.expiredSaleTitle, ms = 10_000) }
      .onFailure { throw AssertionError("برگهٔ تأییدِ منقضی باز نشد؛ فروش‌ها: ${store.data.value.sales.size}", it) }
    assertEquals("بی تأیید هیچ ثبتی", 1, store.data.value.sales.size)

    //  «انصراف» ⇒ برگشت به همان برگه با پیامِ روشن؛ باز هیچ ثبت
    tap(hasText(StrKit.cancel) and hasClickAction())
    h.await(TillGuards.expiredRefusal(data, mapOf(milk.id to 1_000L)))
    assertEquals(1, store.data.value.sales.size)

    //  دوباره ⇒ همان برگه؛ صاحبِ پشتِ صندوق با دلیل تأیید می‌کند ⇒ همان تعویض خودش ثبت می‌شود
    tap(hasText(StrSale.recordExchange) and hasClickAction())
    h.await(StrPos.expiredSaleTitle)
    h.type(StrPos.reasonKept, "مشتری خودش خواست")
    tap(hasText(StrPos.creditApproveSelf) and hasClickAction())
    runCatching { h.awaitThat { store.data.value.sales.size == 2 } }
      .onFailure { throw AssertionError("تعویض پس از تأیید ثبت نشد؛ سابقه: ${store.data.value.auditLog.map { it.type }}", it) }
    val row = store.data.value.auditLog.single { it.type == "expired_sale" }
    assertEquals("مشتری خودش خواست", row.reason)
  }
}
