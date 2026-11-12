package ir.vil3ntec.tohid.desktop

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import ir.vil3ntec.tohid.core.gateway.Payee
import ir.vil3ntec.tohid.core.gateway.SimpleAccount
import ir.vil3ntec.tohid.core.gateway.TillHesab
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrPay
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.ExpiryPref
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.pay.platform.HesabPayEnv
import ir.vil3ntec.tohid.ui.screens.DeskSaleScreen
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 *  S22 «منقضی فقط با مجوز» پیش از گرفتنِ پولِ حساب‌پی در **پنجرهٔ واقعیِ صندوقِ کامپیوتر** (`DeskSaleScreen`، حالتِ سادهٔ
 *  حساب‌پی: QR ِ خودِ دکان و «پول رسید» ِ صندوقدار). همان ترتیبِ صندوقِ گوشیِ نسخهٔ ۳ (`CheckoutRoute.hesabPay`): سبدی که از
 *  ماندهٔ منقضی برمی‌دارد پیش از باز شدنِ برگهٔ پول پنجرهٔ PIN را باز می‌کند؛ بستن ⇒ نه برگهٔ پول، نه فروش؛ تأیید ⇒ برگهٔ پول
 *  و «پول رسید» همان فروش را با ردیفِ `expired_sale` ثبت می‌کند و پس از پول هیچ پرسشی نمی‌ماند.
 *  ⛔ روی `claude/batch-57` (e69b30114) برگهٔ پول بی پرسش باز می‌شد (پیش‌سنجی بی `expiryGate`)، پنجرهٔ تأیید فقط پس از تسویه
 *  می‌آمد و «انصراف» از آن تسویهٔ گرفته‌شده را دور می‌انداخت (پول بی فروش).
 */
@OptIn(ExperimentalTestApi::class)
class DeskExpiryPayUiTest {

  @get:Rule val rule = createComposeRule()

  private val ctx = DesktopContext
  private val draft get() = File(ctx.filesDir, CartStore.FILE)
  private val milk = Product(id = "xp-milk", name = "شیرِ کهنهٔ حساب‌پی", unit = "عدد", salePrice = 100.0)
  private val data = ShopData(
    products = listOf(milk),
    //  همهٔ شیر منقضی است ⇒ هر دانه تأیید می‌خواهد
    warehouseEntries = listOf(WarehouseEntry(id = "xp-old", productId = milk.id, units = 3.0, expiryDate = "2020-01-01")),
  )
  private val env get() = HesabPayEnv.of(ctx)
  private val cached = HesabPayEnv::class.java.getDeclaredField("cached").apply { isAccessible = true }
  private lateinit var store: ShopStore

  private fun clean() {
    ExpiryPref.setApproval(ctx, false)
    ShopPolicy.setBlockExpired(ctx, false)
    ShopPolicy.setPayV2(ctx, false)
    CartStore.awaitWrites()
    draft.delete()
    ctx.filesDir.listFiles().orEmpty().filter { it.name.startsWith("shop-data.json") }.forEach { it.deleteRecursively() }
    ShopRole.remember(ctx, ShopRole.OWNER)
    env.forget()
  }

  @Before fun up() {
    I18n.source = { Lang.FA }
    clean()
  }

  @After fun down() = clean()

  /** حساب‌پیِ حالتِ ساده برای همین دکان (بی سرور: همان پاسخی که `GET /shop/payments/config` در حافظهٔ یک‌دقیقه‌ای می‌گذارد) */
  private fun simpleHesab() {
    assumeTrue("بی شبکه حساب‌پی پنهان است", env.online())
    cached.set(env, System.currentTimeMillis() to TillHesab(false, "simple", SimpleAccount("0700000000", ""), payee = Payee("دکانِ آزمون", "1234", "fp-xp")))
  }

  private fun open() {
    store = ShopStore(ctx).also { runBlocking { it.load() } }
    assertTrue(runBlocking { store.save(data) })
    val cart = CartStore(ctx)
    assertTrue(cart.set(listOf(SalesEngine.CartLine(milk.id, 1.0))))
    rule.setContent {
      CompositionLocalProvider(
        LocalContext provides DesktopContext,
        LocalView provides remember { android.view.View(DesktopContext) },
        LocalConfiguration provides Configuration(1280, 800),
      ) {
        TohidTheme(ThemeChoice.LIGHT) {
          val live by store.data.collectAsState()
          DeskSaleScreen(store = store, cartStore = cart, d = live, snackbar = remember { SnackbarHostState() }) {}
        }
      }
    }
    rule.waitUntil(15_000) { has(StrPay.payToShop) }
  }

  private fun has(text: String) = rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

  private fun tap(m: SemanticsMatcher) {
    rule.onAllNodes(m)[0].performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
  }

  private fun sales() = store.data.value.sales.size

  @Test fun theExpiredApprovalIsAskedBeforeTheMoneyAndCancellingItTakesNoMoney() {
    ExpiryPref.setApproval(ctx, true)
    simpleHesab()
    open()

    tap(hasText(StrPay.payToShop) and hasClickAction())
    runCatching { rule.waitUntil(10_000) { has(StrPos.expiredSaleTitle) } }
      .onFailure { throw AssertionError("پیش از گرفتنِ پول پنجرهٔ تأییدِ منقضی باز نشد؛ برگهٔ پول باز است: ${has(StrPay.shopHesabTitle)}", it) }
    assertFalse("برگهٔ پول پیش از تأیید باز نمی‌شود", has(StrPay.shopHesabTitle))

    //  انصراف ⇒ نه برگهٔ پول، نه فروش
    tap(hasText(Str.cancel) and hasClickAction() and hasAnySibling(hasText(StrPos.creditApproveSelf)))
    rule.waitUntil(5_000) { !has(StrPos.expiredSaleTitle) }
    assertFalse(has(StrPay.shopHesabTitle))
    assertEquals(0, sales())

    //  دوباره ⇒ همان پنجره؛ صاحبِ پشتِ صندوق با دلیل تأیید می‌کند ⇒ حالا برگهٔ پول، و «پول رسید» همان فروش را می‌نویسد
    tap(hasText(StrPay.payToShop) and hasClickAction())
    rule.waitUntil(10_000) { has(StrPos.expiredSaleTitle) }
    rule.onAllNodes(hasSetTextAction() and (hasContentDescription(StrPos.reasonKept) or hasText(StrPos.reasonKept)))[0].performTextInput("مشتری خودش خواست")
    rule.waitForIdle()
    tap(hasText(StrPos.creditApproveSelf) and hasClickAction())
    rule.waitUntil(5_000) { !has(StrPos.expiredSaleTitle) }
    assertEquals("تأیید خودش چیزی ثبت نمی‌کند", 0, sales())

    tap(hasText(StrPay.payToShop) and hasClickAction())
    rule.waitUntil(10_000) { has(StrPay.shopHesabTitle) }
    tap(hasText(StrPay.moneyArrived) and hasClickAction())
    runCatching { rule.waitUntil(15_000) { sales() == 1 } }
      .onFailure { throw AssertionError("پس از «پول رسید» فروش ثبت نشد؛ پرسشِ منقضی پس از پول: ${has(StrPos.expiredSaleTitle)}", it) }
    assertFalse("پس از گرفتنِ پول پرسشی نمی‌ماند", has(StrPos.expiredSaleTitle))
    val sale = store.data.value.sales.single()
    assertTrue("تسویهٔ حساب‌پی روی همان فاکتور", !sale.payments.isNullOrEmpty())
    val row = store.data.value.auditLog.single { it.type == "expired_sale" }
    assertEquals("مشتری خودش خواست", row.reason)
  }
}
