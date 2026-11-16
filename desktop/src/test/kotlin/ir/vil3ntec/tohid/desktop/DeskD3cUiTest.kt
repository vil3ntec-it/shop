package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.LayoutDirection
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrCustomerPages
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrLayaway
import ir.vil3ntec.tohid.core.i18n.StrLoyalty
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.i18n.StrPlaces
import ir.vil3ntec.tohid.core.i18n.StrPurchaseOrders
import ir.vil3ntec.tohid.core.i18n.StrPurchasePages
import ir.vil3ntec.tohid.core.i18n.StrReturnPages
import ir.vil3ntec.tohid.core.i18n.StrShift
import ir.vil3ntec.tohid.data.CashShift
import ir.vil3ntec.tohid.data.PoBillDto
import ir.vil3ntec.tohid.data.PurchaseBook
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.test.assertIsNotEnabled
import ir.vil3ntec.tohid.desktop.shots.MoneyDemo
import ir.vil3ntec.tohid.ui.components.common.BulkConfirmDialog
import ir.vil3ntec.tohid.ui.components.common.BulkPreviewRow
import ir.vil3ntec.tohid.ui.screens.DeskCreditLimitDialog
import ir.vil3ntec.tohid.ui.screens.DeskPurchasingScreen
import ir.vil3ntec.tohid.ui.screens.PoBillVoidDialog
import ir.vil3ntec.tohid.ui.screens.ShiftPanelV2
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  `claude/e-desk-d3c` — فرم‌های خرید و پنجره‌های کامپیوتر روی `ui/desk`، با گره‌های واقعیِ Compose (چیدمانِ کامپیوتر، راست‌به‌چپ):
 *   • «فاکتورِ خریدِ تازه»: کالا با نام + Enter، مقدار، فی، پرداخت‌شده و «ثبتِ فاکتور» ⇐ ردیف‌های دفتر **همان** خروجیِ
 *     `PurchaseBook.record` با همان ورودی (یعنی ظاهر عوض شد، داده نه)؛
 *   • سفارشِ خرید روی کامپیوتر: ساختن از «همهٔ کالاهای این تأمین‌کننده» (یک درخواست با یک کلید)، فرستادن، دریافتِ همهٔ
 *     باقی‌مانده و ثبتِ فاکتورِ خریدِ دفتر؛ لغو با پنجرهٔ دلیل؛
 *   • پنجره‌ها: Esc انصراف، Enter کارِ اصلی، کارِ پاک‌کننده با فوکوسِ آغاز روی «انصراف» (Enter ⇒ انصراف)؛ سقفِ اعتبار، باطل کردنِ
 *     فاکتورِ تأمین‌کننده، پیش‌نمایشِ کارِ گروهی و باز کردنِ صندوق همان کار و همان داده.
 */
@OptIn(ExperimentalTestApi::class)
class DeskD3cUiTest {

  @get:Rule val rule = createComposeRule()

  //  صفحه‌ای که در دفتر می‌نویسد ⇒ دفترِ واقعیِ روی پوشهٔ دادهٔ آزمون (همان راهِ `CatalogPhoneP2UiTest`)
  private fun clean() { DesktopContext.filesDir.listFiles().orEmpty().filter { it.name.startsWith("shop-data.json") }.forEach { it.deleteRecursively() } }
  private fun diskStore(d: ShopData): ShopStore = ShopStore(DesktopContext).also { s -> runBlocking { s.load(); assertTrue(s.save(d)) } }

  @Before fun up() { clean(); I18n.source = { Lang.FA }; ShopRole.remember(DesktopContext, ShopRole.OWNER) }
  @After fun down() { clean(); I18n.source = { Lang.FA } }

  private fun show(content: @Composable () -> Unit) {
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext, LocalDeskLayout provides true) {
        TohidTheme(ThemeChoice.LIGHT) {
          CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { Box(Modifier.fillMaxSize()) { content() } }
        }
      }
    }
    rule.waitForIdle()
  }

  private fun has(text: String, substring: Boolean = false) = rule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
  private fun await(text: String, substring: Boolean = false, ms: Long = 15_000) = rule.waitUntil(ms) { has(text, substring) }
  private fun clickable(text: String, substring: Boolean = false) = rule.onAllNodes(hasText(text, substring = substring) and hasClickAction())
  private fun tap(text: String, substring: Boolean = false, last: Boolean = false) {
    val all = clickable(text, substring)
    val n = all.fetchSemanticsNodes().size
    assertTrue("دکمه/ردیفِ «$text» نیست", n > 0)
    all[if (last) n - 1 else 0].performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
  }
  private fun field(desc: String, i: Int = 0): SemanticsNodeInteraction = rule.onAllNodes(hasSetTextAction() and hasContentDescription(desc))[i]
  private fun pane(title: String) = rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title))

  /* ------------------------------ فاکتورِ خریدِ تازه ------------------------------ */

  @Test fun newPurchaseInvoiceSavesExactlyWhatPurchaseBookRecords() {
    val store = diskStore(MoneyDemo.full())
    val d = store.data.value
    show {
      val live by store.data.collectAsState()
      DeskPurchasingScreen(store, live, remember { SnackbarHostState() })
    }
    await(StrPurchasePages.newPurchaseKey)
    tap(StrPurchasePages.newPurchaseKey)
    await(StrPurchasePages.newPurchaseNo(PurchaseBook.nextNo(d)))
    //  نامِ کالا + Enter ⇒ همان کالا، مقدارِ ۱ و قیمتِ خرید
    val rice = d.products.first { it.id == "p-rice" }
    field(StrPurchasePages.itemNameBarcode).performTextInput(rice.name)
    field(StrPurchasePages.itemNameBarcode).performKeyInput { pressKey(Key.Enter) }
    rule.waitForIdle()
    assertTrue(has(ir.vil3ntec.tohid.money(950.0)))
    field(StrPlaces.amount).performTextReplacement("12")
    field(StrReturnPages.unitPriceAfn).performTextReplacement("900")
    field(StrPurchasePages.paidAfn).performTextInput("5000")
    rule.waitForIdle()
    assertTrue(has(StrPurchasePages.invoiceTotal(ir.vil3ntec.tohid.money(10800.0))))
    tap(StrPurchasePages.saveInvoiceKey)
    rule.waitUntil(10_000) { store.data.value.purchases.size > d.purchases.size }

    //  همان ورودی به خودِ موتور ⇒ همان ردیف‌ها (شناسه و زمان جدا)
    val sid = d.suppliers.maxByOrNull { s -> d.purchases.filter { it.supplierId == s.id }.maxOfOrNull { it.createdAt } ?: 0L }!!.id
    val today = ir.vil3ntec.tohid.todayIso()
    var n = 0
    val want = (PurchaseBook.record(d, sid, listOf(PurchaseBook.Line("p-rice", 12.0, 900.0, expiryDate = "")), 5000.0, today, "", today, 0L, { "x${n++}" }) as PurchaseBook.Result.Ok).data
    fun shape(x: ShopData) = x.purchases.filter { p -> d.purchases.none { it.id == p.id } }
      .map { listOf(it.productId, it.supplierId, it.quantity, it.unit, it.purchasePrice, it.totalAmount, it.date, it.notes, it.paidAmount, it.debt, it.invoiceNo) }
    val got = store.data.value
    assertEquals(shape(want), shape(got))
    assertEquals(1, shape(got).size)
    assertEquals(want.warehouseEntries.size, got.warehouseEntries.size)
    assertEquals(ir.vil3ntec.tohid.data.ShopStore.stock(want, "p-rice"), ir.vil3ntec.tohid.data.ShopStore.stock(got, "p-rice"), 0.0)
    //  پس از ثبت، فرم بسته شد
    assertFalse(has(StrPurchasePages.saveInvoiceKey))
  }

  /* ------------------------------ سفارشِ خرید ------------------------------ */

  @Test fun purchaseOrderIsCreatedSentAndReceivedOnTheDesktop() {
    val backend = TestBackend()
    try {
      val fake = FakePurchaseOrders(backend.server)
      val store = diskStore(MoneyDemo.full())
      val d = store.data.value
      show {
        val live by store.data.collectAsState()
        DeskPurchasingScreen(store, live, remember { SnackbarHostState() })
      }
      rule.waitUntil(15_000) { clickable(StrPurchasePages.purchaseOrders).fetchSemanticsNodes().isNotEmpty() }
      tap(StrPurchasePages.purchaseOrders)
      await(StrPurchasePages.newPo)
      tap(StrPurchasePages.newPo)
      await(StrPurchasePages.newPoTitle)
      //  تأمین‌کننده از کادرِ کشویی
      val supplier = "شرکت پخش البرز"
      tap(StrMoney.supplierLabel, substring = true)
      tap(supplier, last = true)
      tap(StrPurchasePages.allFromSupplier)
      await("برنج سیلا ۵ کیلویی", substring = true)
      val save = clickable(StrPurchasePages.saveDraft)[0]
      save.performSemanticsAction(SemanticsActions.OnClick)
      runCatching { save.performSemanticsAction(SemanticsActions.OnClick) }
      rule.waitUntil(10_000) { fake.writes().any { it.path == FakePurchaseOrders.ROOT && it.method == "POST" } }
      await("#2001", substring = true)
      val creates = fake.writes().filter { it.path == FakePurchaseOrders.ROOT }
      assertEquals(1, creates.size)
      assertTrue(!creates.single().key.isNullOrBlank())
      assertEquals(MoneyDemo.SUPPLIER, fake.order("po-new-1")!!.supplierId)

      tap(StrPurchasePages.sendToSupplier)
      rule.waitUntil(10_000) { fake.order("po-new-1")!!.state == "sent" }
      assertEquals(1, fake.writes("/po-new-1/send").size)
      await(StrPurchasePages.receiveGoods)
      tap(StrPurchasePages.receiveGoods)
      await(StrPurchaseOrders.receiveAll)
      tap(StrPurchaseOrders.receiveAll)
      tap(StrLayaway.payConfirm)
      rule.waitUntil(10_000) { fake.writes("/po-new-1/receipts").isNotEmpty() }
      //  رسیدِ سرور ⇒ فاکتورِ خریدِ دفتر (همان `PurchaseOrderBook.post`)
      rule.waitUntil(10_000) { store.data.value.purchases.size > d.purchases.size }
      assertEquals(1, fake.writes("/po-new-1/receipts").size)
      assertEquals("complete", fake.order("po-new-1")!!.status)
    } finally { backend.close() }
  }

  /* ------------------------------ پنجره‌ها ------------------------------ */

  @Test fun destructiveConfirmStartsOnCancelSoEnterCancelsAndConfirmClosesThenActs() {
    val log = ArrayList<String>()
    var open by mutableStateOf(true)
    show { if (open) V3ConfirmSheet("پاک شود؟", "برگشت ندارد", "پاک کن", onConfirm = { log += "confirm" }, onDismiss = { log += "dismiss"; open = false }) }
    await("پاک شود؟")
    //  کارِ پاک‌کننده: فوکوسِ آغاز روی «انصراف» ⇒ Enter همان «انصراف»
    val cancel = clickable(StrKit.cancel)[0]
    rule.waitUntil(5_000) { runCatching { cancel.assertIsFocused(); true }.getOrDefault(false) }
    cancel.performKeyInput { pressKey(Key.Enter) }
    rule.waitForIdle()
    assertEquals(listOf("dismiss"), log)
    //  دوباره: «پاک کن» اول می‌بندد، بعد کار (ترتیبِ برگهٔ گوشی)
    log.clear(); open = true
    rule.waitForIdle()
    tap("پاک کن")
    assertEquals(listOf("dismiss", "confirm"), log)
  }

  @Test fun escapeCancelsAndEnterConfirmsANonDestructiveDialog() {
    val log = ArrayList<String>()
    var open by mutableStateOf(true)
    show { if (open) V3ConfirmSheet("بفرستم؟", "", "بفرست", onConfirm = { log += "confirm" }, onDismiss = { log += "dismiss"; open = false }, danger = false) }
    await("بفرستم؟")
    pane("بفرستم؟").performSemanticsAction(SemanticsActions.RequestFocus)
    pane("بفرستم؟").performKeyInput { pressKey(Key.Escape) }
    rule.waitForIdle()
    assertEquals(listOf("dismiss"), log)
    log.clear(); open = true
    rule.waitForIdle()
    pane("بفرستم؟").performSemanticsAction(SemanticsActions.RequestFocus)
    pane("بفرستم؟").performKeyInput { pressKey(Key.Enter) }
    rule.waitForIdle()
    assertEquals(listOf("dismiss", "confirm"), log)
  }

  @Test fun creditLimitDialogSavesThroughTheSameEngine() {
    val d = MoneyDemo.full()
    val debtor = d.debtors.first()
    var saved: ShopData? = null
    var closed = 0
    show { DeskCreditLimitDialog(d, debtor, onToast = {}, onDismiss = { closed++ }, onSaved = { saved = it }) }
    await(StrCustomerPages.creditLimitOf(debtor.name))
    field(StrMoney.creditLimitField).performTextReplacement("25000")
    tap(StrKit.save)
    rule.waitUntil(5_000) { saved != null }
    val want = (ir.vil3ntec.tohid.data.DebtorEngine.setCreditLimit(d, debtor.id, 25000.0) as ir.vil3ntec.tohid.data.DebtorEngine.Result.Ok).data
    assertEquals(want.debtors.first { it.id == debtor.id }.creditLimit, saved!!.debtors.first { it.id == debtor.id }.creditLimit)
    assertEquals(25000.0, saved!!.debtors.first { it.id == debtor.id }.creditLimit!!, 0.0)
    //  Esc ⇒ انصراف
    pane(StrCustomerPages.creditLimitOf(debtor.name)).performSemanticsAction(SemanticsActions.RequestFocus)
    pane(StrCustomerPages.creditLimitOf(debtor.name)).performKeyInput { pressKey(Key.Escape) }
    rule.waitForIdle()
    assertEquals(1, closed)
  }

  @Test fun voidingASupplierBillNeedsAReasonAndSendsItTrimmed() {
    val bill = PoBillDto(id = "b1", billNo = "F-77")
    val got = ArrayList<String>()
    show { PoBillVoidDialog(bill, busy = false, onDismiss = {}, onConfirm = { got += it }) }
    await(StrPurchasePages.voidAsk("F-77"))
    //  بی دلیل دکمه خاموش است
    clickable(StrPurchasePages.voidIt)[0].assertIsNotEnabled()
    field(StrLoyalty.adjustReason).performTextInput("  تکراری  ")
    tap(StrPurchasePages.voidIt)
    assertEquals(listOf("تکراری"), got)
  }

  @Test fun destructiveBulkPreviewNeedsAReasonAndEnterNeverConfirmsIt() {
    val rows = listOf(BulkPreviewRow("a", "چای", changes = true, note = ""), BulkPreviewRow("b", "نمک", changes = false, note = ""))
    val got = ArrayList<String>()
    var closed = 0
    show { BulkConfirmDialog("پاک کردنِ ۲ کالا", "پاک کن", rows, onConfirm = { got += it }, onDismiss = { closed++ }, destructive = true) }
    await("پاک کردنِ ۲ کالا")
    assertTrue(has("چای") && has("نمک"))
    clickable("پاک کن")[0].assertIsNotEnabled()
    field(ir.vil3ntec.tohid.core.i18n.Str.reasonRequired).performTextInput("کالای منقضی شده")
    field(ir.vil3ntec.tohid.core.i18n.Str.reasonRequired).performKeyInput { pressKey(Key.Enter) }
    rule.waitForIdle()
    assertTrue("Enter کارِ پاک‌کننده را نمی‌زند", got.isEmpty())
    tap("پاک کن")
    assertEquals(listOf("کالای منقضی شده"), got)
    assertEquals(0, closed)
  }

  @Test fun openingTheRegisterFromTheDeskDialogWritesTheSameShift() {
    val store = diskStore(MoneyDemo.full())
    val d = store.data.value
    val before = CashShift.list(d).size
    show {
      val live by store.data.collectAsState()
      ShiftPanelV2(live, store, "علی", toast = {})
    }
    await(StrShift.openRegister)
    tap(StrShift.openRegister)
    await(StrShift.openRegisterTitle)
    field(StrShift.openingCash).performTextInput("1500")
    tap(StrShift.openIt)
    rule.waitUntil(10_000) { CashShift.list(store.data.value).size > before }
    assertEquals(before + 1, CashShift.list(store.data.value).size)
    assertFalse(has(StrShift.openRegisterTitle))
  }
}
