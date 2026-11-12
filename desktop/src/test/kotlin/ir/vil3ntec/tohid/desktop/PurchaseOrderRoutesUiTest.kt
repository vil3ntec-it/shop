package ir.vil3ntec.tohid.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import ir.vil3ntec.tohid.core.i18n.StrGoods
import ir.vil3ntec.tohid.core.i18n.StrLayaway
import ir.vil3ntec.tohid.core.i18n.StrLoyalty
import ir.vil3ntec.tohid.core.i18n.StrPurchaseOrders
import ir.vil3ntec.tohid.core.i18n.StrPurchasePages
import ir.vil3ntec.tohid.core.i18n.StrSupplierBills
import ir.vil3ntec.tohid.data.PoForms
import ir.vil3ntec.tohid.data.PoPermsDto
import ir.vil3ntec.tohid.desktop.shots.MoneyDemo
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  PUR-02 — صفحه‌های مسیرِ «سفارش‌های خرید» ِ نسخهٔ ۳ (`purchasing/orders`، `…/new`، `…/{id}`، `…/{id}/receive`) با
 *  `ShellContext` ِ واقعی ([RouteUiHarness]) و سرورِ ساختگیِ با حالت ([FakePurchaseOrders]):
 *   • فهرست همهٔ وضعیت‌ها را با برچسبِ خودش نشان می‌دهد و پالایهٔ «پیش‌نویس» فقط پیش‌نویس را؛ «سفارشِ تازه» فقط برای مدیر؛
 *   • فرم: «همهٔ کالاهای این تأمین‌کننده» ⇒ ردیف‌ها، «ذخیرهٔ پیش‌نویس» با دو لمس ⇒ یک درخواست با یک کلیدِ یکتا و برگشت به فهرست؛
 *   • جزئیات: فرستادن یک بار؛ لغوِ فرستاده بی دلیل نمی‌رود و با دلیل یک بار می‌رود؛
 *   • دریافت: «دریافتِ همهٔ باقی‌مانده» ⇒ یک رسید با همهٔ باقی‌مانده‌ها؛
 *   • شاگرد (فقط `receive`): «دریافتِ کالا» دارد ولی لغو و «سفارشِ تازه» نه، و فرمِ ساختن «فقط مدیر» می‌گوید و چیزی نمی‌فرستد.
 */
@OptIn(ExperimentalTestApi::class)
class PurchaseOrderRoutesUiTest {

  @get:Rule val rule = createComposeRule()

  private lateinit var h: RouteUiHarness
  private lateinit var fake: FakePurchaseOrders
  private val data = MoneyDemo.full()
  private val root = FakePurchaseOrders.ROOT

  @Before fun up() {
    h = RouteUiHarness(rule)
    fake = FakePurchaseOrders(h.server, seed = PoDemoOrders.all)
  }

  @After fun down() = h.close()

  private fun title(o: ir.vil3ntec.tohid.data.PurchaseOrderDto) = StrSupplierBills.orderTitle(o.no, o.supplierName)

  @Test fun listShowsEveryStatusFiltersDraftsAndManagerCanStartANewOrder() {
    h.show("purchasing/orders", data)
    h.await(title(PoDemoOrders.partial), substring = true)
    for (o in PoDemoOrders.all) assertTrue(title(o), h.has(title(o), substring = true))
    for (s in listOf(StrGoods.poDraft, StrGoods.poSent, StrGoods.poPartial)) assertTrue(s, h.has(s, substring = true))
    assertTrue(h.has(StrPurchasePages.newPoTitle))

    h.click(PoForms.label(PoForms.Filter.DRAFT))
    //  پالایهٔ تازه فهرست را از نو می‌خواند (میانِ راه هیچ ردیفی نیست) ⇒ تا پیش‌نویس آمد و فرستاده رفت
    h.awaitThat { h.has(title(PoDemoOrders.draft), substring = true) && !h.has(title(PoDemoOrders.sent), substring = true) }
    assertFalse(h.has(title(PoDemoOrders.partial), substring = true))
    assertTrue(fake.writes().isEmpty())
  }

  @Test fun newDraftFromTheSupplierItemsIsCreatedOnceWithOneKeyThenTheListShowsIt() {
    h.show("purchasing/orders/new?supplier=${MoneyDemo.SUPPLIER}", data)
    h.await(StrPurchasePages.allFromSupplier)
    h.click(StrPurchasePages.allFromSupplier)
    h.await("برنج سیلا ۵ کیلویی", substring = true)
    assertTrue(h.has("روغن آفتاب‌گردان", substring = true))

    //  دو لمسِ پشتِ هم: سدِ دو بار زدن ⇒ یک درخواست
    val save = rule.onAllNodes(hasText(StrPurchasePages.saveDraft) and hasClickAction())[0]
    save.performSemanticsAction(SemanticsActions.OnClick)
    runCatching { save.performSemanticsAction(SemanticsActions.OnClick) }
    h.awaitThat { fake.writes().any { it.path == root && it.method == "POST" } }
    val made = "#2001"
    h.await(made, substring = true)
    val creates = fake.writes().filter { it.path == root }
    assertEquals(1, creates.size)
    val w = creates.single()
    assertTrue(!w.key.isNullOrBlank())
    assertEquals(MoneyDemo.SUPPLIER, w.body["supplierId"]!!.jsonPrimitive.content)
    assertEquals(setOf("p-rice", "p-oil"), w.body["lines"]!!.jsonArray.map { it.jsonObject["productId"]!!.jsonPrimitive.content }.toSet())
    //  پس از ساختن صفحه بسته شد و فهرست سفارشِ تازه را از سرور خواند
    assertEquals("draft", fake.order("po-new-1")!!.state)
    assertFalse(h.has(StrPurchasePages.saveDraft))
  }

  @Test fun sendingADraftGoesOnceAndCancellingASentOrderNeedsAReason() {
    h.show("purchasing/orders/${PoDemoOrders.draft.id}", data)
    h.await(StrPurchasePages.sendToSupplier)
    h.click(StrPurchasePages.sendToSupplier)
    h.awaitThat { fake.order("po-d")!!.state == "sent" && !h.has(StrPurchasePages.sendToSupplier) }
    assertEquals(1, fake.writes("/po-d/send").size)
    h.await(StrPurchasePages.receiveGoods)

    h.click(StrPurchasePages.cancelIt)
    h.awaitThat { rule.onAllNodes(hasText(StrPurchasePages.cancelIt) and hasClickAction()).fetchSemanticsNodes().size >= 2 }
    val confirm = { rule.onAllNodes(hasText(StrPurchasePages.cancelIt) and hasClickAction()).let { it[it.fetchSemanticsNodes().size - 1] } }
    confirm().performSemanticsAction(SemanticsActions.OnClick)
    h.await(StrPurchaseOrders.reasonNeeded)
    assertTrue(fake.writes("/po-d/cancel").isEmpty())
    h.type(StrLoyalty.adjustReason, "تأمین‌کننده جنس ندارد")
    confirm().performSemanticsAction(SemanticsActions.OnClick)
    h.awaitThat { fake.order("po-d")!!.state == "cancelled" }
    val c = fake.writes("/po-d/cancel")
    assertEquals(1, c.size)
    assertEquals("تأمین‌کننده جنس ندارد", c.single().body["reason"]!!.jsonPrimitive.content)
    h.await(StrPurchasePages.poCancelled, substring = true)
  }

  @Test fun receiveAllRemainingRecordsOneReceiptWithEveryRemainingQuantity() {
    h.show("purchasing/orders/${PoDemoOrders.sent.id}/receive", data)
    h.await(StrPurchaseOrders.receiveAll)
    h.click(StrPurchaseOrders.receiveAll)
    h.click(StrLayaway.payConfirm)
    h.await(StrPurchasePages.receiptSaved, substring = true)
    val r = fake.writes("/po-s/receipts")
    assertEquals(1, r.size)
    val got = r.single().body["lines"]!!.jsonArray.associate { e ->
      val o = e.jsonObject
      o["lineId"]!!.jsonPrimitive.content to (o["receivedMilli"]!!.jsonPrimitive.long to o["damagedMilli"]!!.jsonPrimitive.long)
    }
    assertEquals(mapOf("l1" to (10_000L to 0L), "l2" to (12_000L to 0L)), got)
    assertEquals("complete", fake.order("po-s")!!.status)
    //  پس از ثبت، دکمهٔ ثبت نیست: همان رسید دوباره فرستاده نمی‌شود
    assertFalse(h.has(StrLayaway.payConfirm))
  }

  @Test fun staffCanReceiveButSeesNoCancelAndNoNewOrder() {
    fake.perms = PoPermsDto(manage = false, receive = true)
    h.show("purchasing/orders/${PoDemoOrders.sent.id}", data)
    h.await(StrPurchasePages.receiveGoods)
    assertFalse(h.has(StrPurchasePages.cancelIt))
    assertFalse(h.has(StrPurchasePages.sendToSupplier))
  }

  @Test fun staffListHasNoNewOrderButton() {
    fake.perms = PoPermsDto(manage = false, receive = true)
    h.show("purchasing/orders", data)
    h.await(title(PoDemoOrders.sent), substring = true)
    assertFalse(h.has(StrPurchasePages.newPoTitle))
  }

  @Test fun staffOpeningTheNewOrderFormIsToldManagersOnlyAndNothingIsSent() {
    fake.perms = PoPermsDto(manage = false, receive = true)
    h.show("purchasing/orders/new?supplier=${MoneyDemo.SUPPLIER}", data)
    h.await(StrPurchaseOrders.manageOnly)
    assertFalse(h.has(StrPurchasePages.saveDraft))
    assertTrue(fake.writes().isEmpty())
  }
}
