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
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrInvoices
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.perm.ApproverVault
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.StaffPins
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.sync.v3.SyncChoice
import ir.vil3ntec.tohid.ui.screens.SalesHistoryScreen
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 *  لغو و «اصلاح» از **جدولِ فاکتورهای کامپیوتر** (`SalesHistoryScreen` ⇐ `voidSale`) در پنجرهٔ واقعیِ Compose:
 *  - PRM-02: پس از نشستنِ لغو در دفتر کدهای فاکتور در صفِ آزاد کردنِ همین دستگاه می‌نشینند (`PromoLive.releaseOnVoid`، همان
 *    `HistoryRoutes` ِ نسخهٔ ۳)؛ ⛔ روی `claude/batch-57` (e69b30114) هیچ‌کدام نمی‌نشست و سقفِ کد پس از لغو پس نمی‌آمد.
 *  - STF-02: نقشِ «نیازمندِ تأیید» ِ `sales.cancel` روی دستگاهِ Sync v3 با برگهٔ PIN ِ مدیر لغو می‌کند (`CancelApproval.offered`)
 *    و برگه روی همان فاکتور (`Sale.cancelApproval`) می‌نشیند؛ «اصلاح» برای او همان رد. ⛔ پیش‌تر فقط پیامِ رد.
 */
@OptIn(ExperimentalTestApi::class)
class DeskInvoiceVoidUiTest {

  @get:Rule val rule = createComposeRule()

  private val ctx = DesktopContext
  private val draft get() = File(ctx.filesDir, CartStore.FILE)
  private val promoPrefs get() = ctx.getSharedPreferences("tohid-promos", 0)
  private val pins get() = StaffPins(ctx)
  private val tea = Product(id = "dv-tea", name = "چایِ آزمونِ لغو", unit = "بسته", salePrice = 250.0)
  private var seq = 0
  private val sold = SalesEngine.record(
    ShopData(products = listOf(tea), warehouseEntries = listOf(WarehouseEntry(id = "dv-w", productId = tea.id, units = 10.0))),
    listOf(SalesEngine.CartLine(tea.id, 2.0)), SalesEngine.Checkout(seller = "صندوقدار"), "2026-11-10", 1L, { "dv-${seq++}" },
  ) as SalesEngine.Result.Ok
  //  فاکتوری که کدِ تخفیف داشت ⇒ آزاد کردن لازم است (`PromoRedeem.releaseNeeded`)، بی نیاز به پرچمِ سرور
  private val data = sold.data.copy(sales = sold.data.sales.map { it.copy(coupons = listOf("NOROOZ10")) })
  private val sale = data.sales.single()
  private lateinit var store: ShopStore

  private fun clean() {
    CartStore.awaitWrites()
    draft.delete()
    ctx.filesDir.listFiles().orEmpty().filter { it.name.startsWith("shop-data.json") }.forEach { it.deleteRecursively() }
    promoPrefs.edit().remove("release-v1").commit()
    ShopRole.remember(ctx, ShopRole.OWNER)
    ShopRole.rememberPolicy(ctx, null)
    //  `rememberShopId` خالی را نمی‌نویسد ⇒ شناسهٔ دکانِ آزمون مستقیم برداشته می‌شود
    ctx.getSharedPreferences("tohid-ledger", 0).edit().remove("shop_id").commit()
    SyncChoice.set(ctx, SyncChoice.V1, 0)
    pins.remove("u-mgr-dv")
  }

  @Before fun up() {
    I18n.source = { Lang.FA }
    clean()
  }

  @After fun down() = clean()

  /** دفترِ آزمون با نقشِ صاحب (صندوق‌دار کالا و انبار را نمی‌نویسد: `RoleGate`) */
  private fun seed() {
    store = ShopStore(ctx).also { s -> runBlocking { s.load(); assertTrue(s.save(data)) } }
  }

  private fun show() {
    if (!::store.isInitialized) seed()
    val cart = CartStore(ctx)
    rule.setContent {
      CompositionLocalProvider(
        LocalContext provides DesktopContext,
        LocalView provides remember { android.view.View(DesktopContext) },
        LocalConfiguration provides Configuration(1280, 800),
      ) {
        TohidTheme(ThemeChoice.LIGHT) {
          val live by store.data.collectAsState()
          SalesHistoryScreen(store = store, cartStore = cart, d = live, snackbar = remember { SnackbarHostState() }) {}
        }
      }
    }
    rule.waitUntil(15_000) { rule.onAllNodes(hasContentDescription(StrInvoices.actions)).fetchSemanticsNodes().isNotEmpty() }
  }

  private fun has(text: String) = rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

  private fun tap(m: SemanticsMatcher) {
    rule.onAllNodes(m)[0].performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
  }

  /** منوی ردیفِ فاکتور ⇒ [item] */
  private fun menu(item: String) {
    tap(hasContentDescription(StrInvoices.actions) and hasClickAction())
    rule.waitUntil(5_000) { has(item) }
    tap(hasText(item) and hasClickAction())
  }

  /** کادرِ دلیلِ پنجرهٔ لغو (نه کادرِ جست‌وجوی جدول) */
  private fun reason(text: String) {
    rule.onAllNodes(hasSetTextAction() and (hasText(StrInvoices.cancelReason) or hasContentDescription(StrInvoices.cancelReason)))[0].performTextInput(text)
    rule.waitForIdle()
  }

  private fun live() = store.data.value.sales.single { it.id == sale.id }

  private fun released() = promoPrefs.getString("release-v1", null).orEmpty().split('\n').any { it.endsWith("\t" + sale.id) }

  private fun awaitReleased() = runCatching { rule.waitUntil(15_000) { released() } }
    .onFailure { throw AssertionError("فاکتورِ لغوشده (${live().status}) کدهایش را در صفِ آزاد کردن نگذاشت", it) }

  @Test fun cancellingFromTheDeskTableQueuesTheInvoiceCodesForRelease() {
    show()
    menu(StrInvoices.cancelSale)
    rule.waitUntil(5_000) { has(StrInvoices.cancelSaleTitle) }
    reason("اشتباهِ صندوق")
    tap(hasText(StrInvoices.cancelSale) and hasClickAction())
    rule.waitUntil(10_000) { live().status == "cancelled" }
    awaitReleased()
  }

  @Test fun correctingFromTheDeskTableQueuesTheInvoiceCodesForRelease() {
    show()
    menu(StrInvoices.correctInvoice)
    rule.waitUntil(5_000) { has(StrInvoices.cancelAndToCart) }
    tap(hasText(StrInvoices.cancelAndToCart) and hasClickAction())
    rule.waitUntil(10_000) { live().status == "cancelled" }
    awaitReleased()
  }

  @Test fun aRoleThatNeedsApprovalCancelsWithTheManagerPinSlipOnSyncV3() {
    seed()
    ShopRole.remember(ctx, ShopRole.CASHIER)
    ShopRole.rememberPolicy(ctx, """{"v":1,"layers":[{"sales.cancel":{"mode":"approve"}}]}""")
    ShopRole.rememberShopId(ctx, "shop-dv")
    assertEquals("", SyncChoice.set(ctx, SyncChoice.V3, 0))
    val pin = "582947"
    //  شمارِ کمِ PBKDF2 فقط برای سرعتِ آزمون؛ قاعدهٔ PIN همان
    pins.put(ApproverVault.create("u-mgr-dv", "مدیرِ آزمون", "manager", pin, iterations = 1000).copy(registered = true))
    show()

    //  «اصلاح» برای این نقش همان رد (برگهٔ لغو «اصلاح» را نمی‌پوشاند)
    menu(StrInvoices.correctInvoice)
    assertFalse(has(StrInvoices.cancelAndToCart))

    menu(StrInvoices.cancelSale)
    runCatching { rule.waitUntil(5_000) { has(StrSale.cancelApproval) } }
      .onFailure { throw AssertionError("نقشِ «نیازمندِ تأیید» به برگهٔ تأییدِ لغو نرسید (پنجرهٔ لغو: ${has(StrInvoices.cancelSaleTitle)})", it) }
    assertFalse("بی برگه پنجرهٔ لغو باز نمی‌شود", has(StrInvoices.cancelSaleTitle))
    rule.onAllNodes(hasSetTextAction() and (hasContentDescription(StrPos.reasonKept) or hasText(StrPos.reasonKept)))[0].performTextInput("مشتری پشیمان شد")
    rule.waitForIdle()
    val pad = hasAnyAncestor(hasAnyDescendant(hasText(StrPos.pinOf("مدیرِ آزمون"))))
    pin.forEach { d -> tap(hasText(d.toString()) and hasClickAction() and pad) }

    rule.waitUntil(15_000) { has(StrInvoices.cancelSaleTitle) }
    reason("مشتری پشیمان شد")
    tap(hasText(StrInvoices.cancelSale) and hasClickAction())
    rule.waitUntil(10_000) { live().status == "cancelled" }
    val slip = assertNotNullAnd(live().cancelApproval)
    assertEquals("sales.cancel", slip["perm"]!!.jsonPrimitive.content)
    assertEquals("sales/${sale.id}", slip["doc"]!!.jsonPrimitive.content)
    assertEquals("برگهٔ تخفیف دست نخورد", null, live().approval)
  }

  private fun <T : Any> assertNotNullAnd(v: T?): T { assertNotNull("برگهٔ لغو روی فاکتور نیست", v); return v!! }
}
