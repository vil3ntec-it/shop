package ir.vil3ntec.tohid.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import ir.vil3ntec.tohid.core.i18n.StrLoyalty
import ir.vil3ntec.tohid.core.i18n.StrPurchaseOrders
import ir.vil3ntec.tohid.core.i18n.StrPurchasePages
import ir.vil3ntec.tohid.core.i18n.StrSupplierBills
import ir.vil3ntec.tohid.data.PoForms
import ir.vil3ntec.tohid.data.PoPermsDto
import ir.vil3ntec.tohid.data.PurchaseOrderBook
import ir.vil3ntec.tohid.data.PurchaseOrderDto
import ir.vil3ntec.tohid.desktop.shots.MoneyDemo
import ir.vil3ntec.tohid.ui.v3.money.poAfn
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  PUR-14 — صفحه‌های مسیرِ «سفارش ⇔ رسید ⇔ فاکتور» و «فاکتورهای تأمین‌کننده» ِ نسخهٔ ۳ (`purchasing/orders/{id}/match`،
 *  `purchasing/bills`، `…/{id}`، `…/{id}/new`) با `ShellContext` ِ واقعی ([RouteUiHarness]) و سرورِ ساختگی ([FakePurchaseOrders]):
 *   • تطبیق: هر ردیفی که هسته (`PoForms.matchView` روی `PurchaseOrderBook.match`) اختلاف‌دار می‌داند با برچسبِ «اختلاف» و متنِ
 *     همان اختلاف، شمارِ اختلاف‌ها و جمع‌ها؛ سفارشِ هم‌خوان ⇒ «هم‌خوان»، بی برچسبِ اختلاف؛
 *   • فهرستِ فاکتورها فقط سفارش‌های فاکتورگیر (پیش‌نویس نه)؛ شاگرد ⇒ «فقط مدیر»، بی هیچ خواندنِ سفارش؛
 *   • فاکتورِ تازه بی شماره نمی‌رود؛ با شماره یک بار می‌رود و جزئیات آن را نشان می‌دهد؛
 *   • «باطل» فقط با دلیل، یک بار، و فاکتور «باطل: …» می‌شود.
 */
@OptIn(ExperimentalTestApi::class)
class SupplierBillRoutesUiTest {

  @get:Rule val rule = createComposeRule()

  private lateinit var h: RouteUiHarness
  private lateinit var fake: FakePurchaseOrders
  private val data = MoneyDemo.full()

  @Before fun up() {
    h = RouteUiHarness(rule)
    fake = FakePurchaseOrders(h.server, seed = PoDemoOrders.all)
  }

  @After fun down() = h.close()

  private fun title(o: PurchaseOrderDto) = StrSupplierBills.orderTitle(o.no, o.supplierName)

  @Test fun matchFlagsEveryRowTheCoreFindsWithItsIssueAndTotals() {
    val o = fake.order("po-p")!!
    val m = PoForms.matchView(data, o)
    assertTrue("نمونه باید اختلاف داشته باشد", m.flaggedCount > 0 && !m.matched && m.supplier)
    h.show("purchasing/orders/po-p/match", data)
    h.await(StrPurchaseOrders.differences(m.flaggedCount))
    assertTrue(h.has(StrPurchaseOrders.sideSupplier))
    assertTrue(h.has(StrPurchaseOrders.matchTotals(poAfn(m.orderedMinor), poAfn(m.acceptedMinor), poAfn(m.billedMinor))))
    assertEquals(m.flaggedCount, rule.onAllNodesWithTextCount(StrPurchaseOrders.flagged))
    for (r in m.rows.filter { it.flagged }) {
      assertTrue(r.name, h.has(r.name))
      for (t in r.issues) assertTrue(t, h.has(t))
    }
    assertTrue(fake.writes().isEmpty())
  }

  @Test fun aMatchingOrderSaysMatchedWithNoDifferenceLabel() {
    val m = PoForms.matchView(data, fake.order("po-c")!!)
    assertTrue(m.matched)
    h.show("purchasing/orders/po-c/match", data)
    h.await(PurchaseOrderBook.matchTitle(true))
    assertEquals(0, rule.onAllNodesWithTextCount(StrPurchaseOrders.flagged))
  }

  @Test fun billsListShowsOnlyOrdersThatTakeBills() {
    h.show("purchasing/bills", data)
    h.await(title(PoDemoOrders.partial), substring = true)
    assertTrue(h.has(title(PoDemoOrders.sent), substring = true))
    assertTrue(h.has(title(PoDemoOrders.complete), substring = true))
    assertFalse(h.has(title(PoDemoOrders.draft), substring = true))
  }

  @Test fun staffIsTurnedAwayFromSupplierBillsWithoutReadingAnyOrder() {
    fake.perms = PoPermsDto(manage = false, receive = true)
    h.show("purchasing/bills", data)
    h.await(StrSupplierBills.managerOnly)
    assertEquals(0, h.server.hits(FakePurchaseOrders.ROOT))
  }

  @Test fun aNewBillNeedsItsNumberThenIsSavedOnceAndShownOnTheOrder() {
    h.show("purchasing/bills/po-s/new", data)
    h.await(StrPurchasePages.saveBill)
    h.click(StrPurchasePages.saveBill)
    h.await(StrPurchasePages.billNoNeeded)
    assertTrue(fake.writes("/po-s/bills").isEmpty())

    h.type(StrPurchasePages.billNo, "INV-77")
    h.click(StrPurchasePages.saveBill)
    h.awaitThat { fake.writes("/po-s/bills").isNotEmpty() }
    h.await("INV-77", substring = true)
    val w = fake.writes("/po-s/bills")
    assertEquals(1, w.size)
    assertEquals("INV-77", w.single().body["billNo"]!!.jsonPrimitive.content)
    assertEquals(2, w.single().body["lines"]!!.jsonArray.size)
    assertEquals(1, fake.order("po-s")!!.billCount)
  }

  @Test fun voidingABillNeedsAReasonAndGoesOnce() {
    h.show("purchasing/bills/po-p", data)
    h.await(StrPurchasePages.voidBtn)
    h.click(StrPurchasePages.voidBtn)
    h.await(StrPurchasePages.voidIt)
    //  بی دلیل دکمهٔ «باطل کن» بسته است
    rule.onNodeWithText(StrPurchasePages.voidIt).assertIsNotEnabled()
    assertTrue(fake.writes("/bills/b1/void").isEmpty())
    h.type(StrLoyalty.adjustReason, "شمارهٔ اشتباه")
    h.click(StrPurchasePages.voidIt)
    h.await(StrPurchasePages.voidedWhy("شمارهٔ اشتباه"), substring = true)
    val w = fake.writes("/bills/b1/void")
    assertEquals(1, w.size)
    assertEquals("شمارهٔ اشتباه", w.single().body["reason"]!!.jsonPrimitive.content)
    assertFalse(fake.order("po-p")!!.bills.single().active)
  }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String): Int =
  onAllNodes(androidx.compose.ui.test.hasText(text, substring = false)).fetchSemanticsNodes().size
