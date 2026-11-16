package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.LayoutDirection
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrDeskData
import ir.vil3ntec.tohid.core.i18n.StrInvoiceOcr
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.i18n.StrPurchasePages
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.desktop.shots.MoneyDemo
import ir.vil3ntec.tohid.ui.screens.DeskPurchasingScreen
import ir.vil3ntec.tohid.ui.screens.LocalInvoicePhotoPicker
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  PUR-12 / AI-10 — «از عکسِ فاکتور» روی «فاکتورِ خریدِ تازه» ِ کامپیوتر با پاسخِ ساختگیِ سرور (`TestBackend`) و عکس‌گیرِ ساختگی
 *  (`LocalInvoicePhotoPicker`): بازبینیِ ردیف‌به‌ردیف در `DeskDialog`، هیچ ردیفی بی تأیید نمی‌رود، ردیفِ ناخوانا فقط پس از نوشتنِ
 *  مقدار و فی تأییدشدنی است، و تا «ثبتِ فاکتور» ِ همیشگی هیچ چیز در دفتر نمی‌نشیند. حالِ «نصب نیست» و پرچمِ خاموش هم.
 */
class InvoiceOcrDeskUiTest {

  @get:Rule val rule = createComposeRule()

  private companion object {
    const val CONFIG = "/api/v1/shop/purchase-ocr/config"
    const val READ = "/api/v1/shop/purchase-ocr"
    /** همان شکلِ خروجیِ Tesseract (رقمِ عربیِ «٩» و جای جابه‌جای فی و جمع در ردیفِ یک) با کالاهای `MoneyDemo` */
    val LINES = """{"lines":[
      {"text":"شرکت پخش البرز - فاکتور فروش","conf":90},
      {"text":"ردیف شرح کالا تعداد فی مبلغ","conf":88},
      {"text":"۱ برنج سیلا ۵ کیلویی ۱۰ ٩,۵۰۰ ٩۵۰","conf":85},
      {"text":"۲ روغن آفتابگردان ۴ ۳۱۰ ۱,۲۴۰","conf":90},
      {"text":"۳ چای سبز ۶ ۴۲۰ ۲۵۲۰۰","conf":89},
      {"text":"جمع کل ۱۳,۲۶۰","conf":93}
    ],"lang":"fas+eng","ms":400,"notice":"x"}"""
    fun config(enabled: Boolean = true, available: Boolean = true) =
      """{"enabled":$enabled,"permitted":true,"paid":true,"available":$available,"lang":"fas+eng","missing":${if (available) "[]" else "[\"tesseract-ocr-fas\"]"}}"""
  }

  private fun clean() { DesktopContext.filesDir.listFiles().orEmpty().filter { it.name.startsWith("shop-data.json") }.forEach { it.deleteRecursively() } }
  private fun diskStore(d: ShopData): ShopStore = ShopStore(DesktopContext).also { s -> runBlocking { s.load(); assertTrue(s.save(d)) } }

  @Before fun up() { clean(); I18n.source = { Lang.FA }; ShopRole.remember(DesktopContext, ShopRole.OWNER) }
  @After fun down() { clean(); I18n.source = { Lang.FA } }

  /** عکس‌گیرِ ساختگی: باز شد ⇒ بسته و چند بایت (سرورِ ساختگی محتوا را نمی‌خواند) */
  private val fakePicker: @Composable (Boolean, () -> Unit, (ByteArray?) -> Unit) -> Unit = { open, dismiss, bytes ->
    LaunchedEffect(open) { if (open) { dismiss(); bytes(byteArrayOf(-1, -40, -1, -32, 1, 2, 3, 4)) } }
  }

  private fun show(store: ShopStore) {
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext, LocalDeskLayout provides true, LocalInvoicePhotoPicker provides fakePicker) {
        TohidTheme(ThemeChoice.LIGHT) {
          CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Box(Modifier.fillMaxSize()) {
              val live by store.data.collectAsState()
              DeskPurchasingScreen(store, live, remember { SnackbarHostState() })
            }
          }
        }
      }
    }
    rule.waitForIdle()
  }

  private fun has(text: String, substring: Boolean = false) = rule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
  private fun await(text: String, substring: Boolean = false, ms: Long = 15_000) = rule.waitUntil(ms) { has(text, substring) }
  private fun buttons(text: String) = rule.onAllNodes(hasText(text) and hasClickAction())
  private fun tap(text: String, i: Int = 0) {
    val all = buttons(text)
    assertTrue("دکمهٔ «$text» نیست", all.fetchSemanticsNodes().size > i)
    all[i].performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
  }
  private fun enabled(text: String, i: Int = 0): Boolean =
    buttons(text).fetchSemanticsNodes()[i].config.getOrNull(SemanticsProperties.Disabled) == null
  /** آخرین کادرِ نام‌دار — پنجرهٔ بازبینی پس از جدولِ فرم می‌آید (کادرِ «مقدار» ِ جدول همان نام را دارد) */
  private fun lastField(desc: String): SemanticsNodeInteraction {
    val all = rule.onAllNodes(hasSetTextAction() and hasContentDescription(desc))
    return all[all.fetchSemanticsNodes().size - 1]
  }

  private fun openForm(store: ShopStore) {
    show(store)
    await(StrPurchasePages.newPurchaseKey)
    tap(StrPurchasePages.newPurchaseKey)
    await(StrPurchasePages.newPurchaseNo(ir.vil3ntec.tohid.data.PurchaseBook.nextNo(store.data.value)))
  }

  @Test fun rowsAreConfirmedOneByOneAndOnlyConfirmedRowsReachTheInvoice() {
    val backend = TestBackend()
    try {
      backend.server.on(CONFIG) { 200 to config() }
      backend.server.on(READ) { r -> if (r.method == "POST") 200 to LINES else 404 to "{}" }
      val store = diskStore(MoneyDemo.full())
      val before = store.data.value
      openForm(store)
      await(StrInvoiceOcr.button)
      tap(StrInvoiceOcr.button)
      //  پاسخِ سرور ⇒ برگهٔ بازبینی با سه ردیف؛ هیچ‌کدام تأییدشده نیست و «افزودن» خاموش است
      await(StrInvoiceOcr.notice)
      assertEquals(1, backend.server.hits(READ))
      for (name in listOf("برنج سیلا ۵ کیلویی", "روغن آفتابگردان", "چای سبز")) assertTrue(name, has(name))
      assertTrue(has(StrInvoiceOcr.flagMismatch))
      assertEquals(3, buttons(StrInvoiceOcr.confirm).fetchSemanticsNodes().size)
      assertFalse("ردیفِ ناخوانا تأییدشدنی نیست", enabled(StrInvoiceOcr.confirm, 2))
      assertFalse(enabled(StrInvoiceOcr.addRows(0)))

      tap(StrInvoiceOcr.confirm, 0)                     //  برنج
      tap(StrInvoiceOcr.skip, 0)                        //  روغن ⇒ رد
      assertTrue(has(StrInvoiceOcr.skipped))
      //  چای: مقدار و فی از روی عکس ⇒ تأییدشدنی
      lastField(StrMoney.quantity).performTextReplacement("6")
      lastField(StrMoney.unitPrice).performTextReplacement("420")
      rule.waitForIdle()
      assertTrue(enabled(StrInvoiceOcr.confirm, 0))
      tap(StrInvoiceOcr.confirm, 0)
      assertEquals(2, rule.onAllNodes(hasText(StrInvoiceOcr.confirmed)).fetchSemanticsNodes().size)
      //  تا این‌جا هیچ چیز در دفتر نیست
      assertEquals(before.purchases.size, store.data.value.purchases.size)

      tap(StrInvoiceOcr.addRows(2))
      rule.waitUntil(10_000) { !has(StrInvoiceOcr.notice) }
      await(StrDeskData.rowsShown(2))
      assertEquals("افزودن به فرم چیزی ثبت نمی‌کند", before.purchases.size, store.data.value.purchases.size)
      assertEquals(before.warehouseEntries.size, store.data.value.warehouseEntries.size)

      //  ثبت همان راهِ همیشگی
      tap(StrPurchasePages.saveInvoiceKey)
      rule.waitUntil(10_000) { store.data.value.purchases.size > before.purchases.size }
      val added = store.data.value.purchases.filter { p -> before.purchases.none { it.id == p.id } }
      assertEquals(listOf("p-rice" to 10.0, "p-tea" to 6.0), added.map { it.productId to it.quantity })
      assertEquals(listOf(950.0, 420.0), added.map { it.purchasePrice })
    } finally { backend.close() }
  }

  @Test fun missingEngineIsExplainedWithoutUploading() {
    val backend = TestBackend()
    try {
      backend.server.on(CONFIG) { 200 to config(available = false) }
      backend.server.on(READ) { 200 to LINES }
      openForm(diskStore(MoneyDemo.full()))
      await(StrInvoiceOcr.button)
      tap(StrInvoiceOcr.button)
      await(StrInvoiceOcr.unavailableTitle)
      assertTrue(has(StrInvoiceOcr.unavailable))
      assertEquals(0, backend.server.hits(READ))
      tap(StrInvoiceOcr.close)
      assertFalse(has(StrInvoiceOcr.unavailableTitle))
    } finally { backend.close() }
  }

  @Test fun switchOffShowsNoButton() {
    val backend = TestBackend()
    try {
      backend.server.on(CONFIG) { 200 to config(enabled = false) }
      openForm(diskStore(MoneyDemo.full()))
      rule.waitUntil(10_000) { backend.server.hits(CONFIG) > 0 }
      repeat(5) { rule.waitForIdle(); Thread.sleep(100) }
      assertFalse(has(StrInvoiceOcr.button))
    } finally { backend.close() }
  }
}
