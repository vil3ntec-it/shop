package ir.vil3ntec.tohid.desktop

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import ir.vil3ntec.tohid.core.i18n.StrLine
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.StrTill
import ir.vil3ntec.tohid.core.pos.LinePricing
import ir.vil3ntec.tohid.core.pos.PriceApproval
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.LinePolicyPrefs
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.TillUser
import ir.vil3ntec.tohid.desktop.shots.MoneyDemo
import ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlinx.coroutines.runBlocking

/**
 *  S45 در پنجرهٔ واقعیِ Compose ِ **گوشیِ نسخهٔ ۳** — مسیرِ `sale/line/0` (`LineRoute` ⇐ `LineSheet`) با `ShellContext` ِ واقعی
 *  ([RouteUiHarness]) و «قیمت و تخفیفِ ردیف» ِ روشن («فقط بالا»):
 *   • مدیر قیمتِ برنج را پایین می‌آورد ⇒ همان پیامِ کامپیوتر زیرِ قیمت و «درخواستِ تأیید»؛ «ثبت» سبد را دست نمی‌زند؛
 *   • «درخواستِ تأیید» ⇒ برگهٔ «تغییرِ قیمت بیرون از قاعده» ⇒ دلیل و «تأیید با این دلیل» ⇒ «تأیید شد: …» ⇒ «ثبت» قیمت را
 *     می‌نشاند و ردیفِ سابقهٔ `price_override` با تأییدکننده و دلیل نوشته می‌شود؛
 *   • شاگردِ بی تخفیف («تخفیف ممنوع»): پیامِ «ممنوع» و **بی** درخواستِ تأیید.
 *  پیش از `claude/e-rows-31w` برگهٔ گوشی هیچ‌کدام را نداشت و قیمتِ پایین بی‌صدا می‌نشست.
 */
@OptIn(ExperimentalTestApi::class)
class ScenarioS45PhoneUiTest {

  @get:Rule val rule = createComposeRule()

  private val ctx = DesktopContext
  private val data = MoneyDemo.full()
  private val rice = data.products.first { it.id == "p-rice" }
  private lateinit var h: RouteUiHarness

  private fun clean() {
    LinePolicyPrefs.setOn(ctx, false)
    LinePolicyPrefs.setDirection(ctx, LinePricing.Direction.BOTH)
    LinePolicyPrefs.setFreeLineCapMinor(ctx, null)
    File(ctx.filesDir, CartStore.FILE).delete()
    ctx.filesDir.listFiles().orEmpty().filter { it.name.startsWith("shop-data.json") }.forEach { it.deleteRecursively() }
    ShopRole.remember(ctx, ShopRole.OWNER)
  }

  @Before fun up() {
    clean()
    LinePolicyPrefs.setOn(ctx, true)
    LinePolicyPrefs.setDirection(ctx, LinePricing.Direction.UP)
    CartStore(ctx).set(listOf(SalesEngine.CartLine(rice.id, 2.0)))
    h = RouteUiHarness(rule)
  }

  @After fun down() { h.close(); clean() }

  private fun cartLine(): SalesEngine.CartLine? = CartStore(ctx).lines.value.firstOrNull()

  private fun price(value: String) {
    val field: SemanticsMatcher = hasSetTextAction() and (hasContentDescription(StrSale.unitPrice) or hasText(StrSale.unitPrice))
    rule.onAllNodes(field)[0].performTextReplacement(value)
    rule.waitForIdle()
  }

  private fun tap(text: String) {
    rule.onAllNodes(hasText(text) and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
  }

  /** دفترِ بارشده روی دیسک، مثلِ برنامه — ردیفِ سابقه بیرون از برگه با `ShopStore.update` نوشته می‌شود */
  private fun loaded(): ShopStore = ShopStore(ctx).also { s -> runBlocking { s.load(); assertTrue(s.replaceAll(data)) } }

  @Test fun managerLowersAPriceAsksForApprovalAndOnlyThenItIsSavedAndAudited() {
    val store = loaded()
    ShopRole.remember(ctx, ShopRole.MANAGER)
    h.show("sale/line/0", store)
    h.await(StrSale.unitPrice)
    price("900")
    h.await(StrLine.priceDownBlocked)
    assertTrue("درخواستِ تأیید", h.has(StrTill.askApproval))

    //  «ثبت» بی تأیید: سبد همان
    tap(StrSale.submit)
    assertNull("قیمتِ پایین بی تأیید ننشست", cartLine()?.unitPrice)

    tap(StrTill.askApproval)
    h.await(StrTill.priceTitle)
    assertTrue(h.has(StrTill.priceSummary(rice.name, PhoneSaleText.amount(rice.salePrice), PhoneSaleText.amount(900.0))))
    h.type(StrPos.reasonKept, "مشتریِ همیشگی")
    tap(StrPos.creditApproveSelf)
    //  مدیرِ پشتِ صندوق خودش با دلیل تأیید می‌کند (نامِ حساب، وگرنه «مدیر»)
    val by = TillUser.me(ctx).trim().ifBlank { StrNav.roleManager }
    h.await(StrTill.approvedBy(by))
    assertFalse("تأییدشده پیامِ رد ندارد", h.has(StrLine.priceDownBlocked))

    tap(StrSale.submit)
    h.awaitThat { cartLine()?.unitPrice == 900.0 }
    runCatching { h.awaitThat { store.data.value.auditLog.any { it.type == PriceApproval.AUDIT_TYPE } } }
      .onFailure { throw AssertionError("ردیفِ سابقهٔ تأیید نیامد: " + store.data.value.auditLog.map { it.type }, it) }
    val row = store.data.value.auditLog.single { it.type == PriceApproval.AUDIT_TYPE }
    assertEquals(by, row.approvedBy)
    assertEquals("مشتریِ همیشگی", row.reason)
    assertEquals(rice.id, row.refId)
    assertEquals(900.0, row.amount, 0.0)
  }

  @Test fun aStaffWhoMayNotDiscountGetsTheDeniedMessageAndNoApprovalDoor() {
    ShopRole.remember(ctx, ShopRole.STAFF)
    LinePolicyPrefs.setDirection(ctx, LinePricing.Direction.BOTH)
    h.show("sale/line/0", data)
    h.await(StrSale.unitPrice)
    price("900")
    h.await(StrLine.reductionDenied)
    assertFalse("«تخفیف ممنوع» درخواستِ تأیید ندارد", h.has(StrTill.askApproval))
    tap(StrSale.submit)
    assertNull(cartLine()?.unitPrice)
    //  بالا بردن آزاد است
    price("1200")
    h.awaitThat { !h.has(StrLine.reductionDenied) }
    tap(StrSale.submit)
    h.awaitThat { cartLine()?.unitPrice == 1200.0 }
  }
}
