package ir.vil3ntec.tohid.ui.v3.sales.cart

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.PromoEngine
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.print.PrintSpooler
import ir.vil3ntec.tohid.scan.ScanFeedback
import ir.vil3ntec.tohid.scan.ScanGate
import ir.vil3ntec.tohid.sync.PhoneScanner
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.ui.PromoLive
import ir.vil3ntec.tohid.ui.components.common.ViewMode
import ir.vil3ntec.tohid.ui.components.pos.HeldDialog
import ir.vil3ntec.tohid.ui.components.pos.rememberHeldInvoices
import ir.vil3ntec.tohid.ui.components.common.ViewModePref
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.products.ProductsPaths
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CheckoutSession
import ir.vil3ntec.tohid.ui.v3.sales.scan.SaleScan

/**
 *  چسبِ مسیرِ `sale` (گوشی و تبلت): سبدِ زنده و جمع با قیمتِ پس از تخفیف‌های خودکار، هر بارکد
 *  از هر راه (برگهٔ اسکنِ پیوسته `SaleScan`، بارکدخوانِ سخت‌افزاری، گوشیِ جفت‌شده) با یک قاعده
 *  (`CartFeedback.scan`)، پیامِ صفِ چاپ (`PrintSpooler.tillNotice`)، و هر کار به مسیرِ خودش.
 *  `sale?scan=1` (دکمهٔ اسکنِ نوار) ⇒ برگهٔ اسکن یک بار.
 */
@Composable
fun RouteScope.SaleRoute() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val lines by cart.lines.collectAsState()
  val debtorId by cart.debtorId.collectAsState()
  val promo = PromoLive.rememberOutcome(data, lines)
  val total = SalesEngine.cartTotal(data, PromoEngine.priced(lines, promo))
  //  کالا یا قرض‌دارِ حذف‌شده در سبد نمی‌ماند
  LaunchedEffect(data.products.size, data.debtors.size) { cart.prune(data) }
  val index = remember(data.products) { ShopStore.barcodeIndex(data) }
  val feedback = remember(cart, toasts, snackbar, scope) {
    CartFeedback(context, cart, toasts, snackbar, scope) { code -> nav.open(ProductsPaths.new(barcode = code)) }
  }
  //  POS-12: چند فاکتورِ هم‌زمان (تنظیمِ «چند فاکتورِ هم‌زمان»، پیش‌فرض خاموش) — جابه‌جایی ⇒ تسویهٔ
  //  نیمه‌کاره مالِ فاکتورِ قبلی بود و از نو آغاز می‌شود
  val loaded by store.loaded.collectAsState()
  val held = rememberHeldInvoices(
    cart, data, snackbar, loaded,
    extras = { HeldSession.extras(cart) },
    onSwitched = { x ->
      HeldSession.kept = cart.snapshot().key?.let { it to x }
      CheckoutSession.reset()
    },
  )
  val gate = remember { ScanGate() }
  val d by rememberUpdatedState(data)
  val codes by rememberUpdatedState(index)

  fun answer(note: CartNote?) {
    note ?: return
    if (SaleScan.open.value) SaleScan.post(note) else feedback.show(note)
  }

  /** یک بارکد از هر راه؛ دوربین سدِ تکرار دارد، دستی و سخت‌افزاری نه */
  fun onCode(raw: String, manual: Boolean, fromPhone: Boolean) {
    val code = raw.trim().takeIf { it.isNotEmpty() } ?: return
    if (!manual && !gate.accept(code)) return
    //  این گوشی بارکدخوانِ کامپیوتر است ⇒ بارکد به سبدِ کامپیوتر می‌رود
    if (!fromPhone && PhoneScanner.sendCode.value != null) {
      if (PhoneScanner.send(code)) {
        ScanFeedback.ok(context)
        answer(CartNote(StrSale.sentToComputer(code), V3Tone.SUCCESS))
        return
      }
      answer(CartNote(StrSale.computerUnavailable, V3Tone.WARNING))
    }
    answer(feedback.scan(d, codes, code, SaleScan.multiplier.intValue.toDouble()))
  }

  LaunchedEffect(Unit) { for (code in PhoneScanner.inbox) onCode(code, manual = true, fromPhone = true) }
  LaunchedEffect(Unit) { for (r in SaleScan.reads) onCode(r.code, r.manual, fromPhone = false) }

  //  HW-02/03: چاپِ خودکار یا کشوی پس از فروش که نشد همین‌جا، سرِ صندوق، گفته می‌شود
  val printNotice by PrintSpooler.tillNotice
  LaunchedEffect(printNotice) {
    printNotice?.let { n ->
      PrintSpooler.consumed(n)
      toasts.show(n.text, V3Tone.WARNING)
    }
  }

  var asked by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    if (args.flag("scan") && !asked) {
      asked = true
      nav.open(SalesPaths.scan(SalesPaths.SCAN_SALE))
    }
  }

  //  صندوق نوارِ پایین ندارد (نوارِ سبد جایش است) ⇒ برگشت همیشه هست؛ در ریشه ⇒ خانه
  val back: () -> Unit = backOrNull ?: { nav.back(); Unit }
  val mode = remember(context) { CatalogMode(context) }
  var listMode by remember { mutableStateOf(mode.list()) }

  SaleScreen(
    d = data,
    cart = lines,
    total = total,
    debtorId = debtorId,
    listMode = listMode,
    promo = promo?.applied.orEmpty(),
    heldStrip = if (held.visible) ({ HeldStrip(held, data) }) else null,
    onHold = if (held.visible) ({ held.askHold() }) else null,
    onHeldList = if (held.visible) ({ held.dialog = HeldDialog.List }) else null,
    modifier = Modifier.hidScanner { onCode(it, manual = true, fromPhone = false) },
    actions = SaleActions(
      tap = { item -> if (item.weighed) nav.open(SalesPaths.keypad(item.id)) else feedback.addOne(data, item.id) },
      hold = { item -> nav.open(SalesPaths.keypad(item.id)) },
      minus = { item -> feedback.minusOne(data, item.id) },
      step = { row, sign ->
        val s = CartModel.step(data, cart.lines.value, row, sign)
        s.lines?.let(cart::set)
        s.note?.let { Haptics.confirm(context); toasts.show(it, V3Tone.WARNING) }
      },
      line = { row -> CartModel.lineIndex(cart.lines.value, row.id)?.let { nav.open(SalesPaths.line(it)) } },
      customer = {
        nav.openForResult(SalesPaths.customer(cart.debtorId.value)) { picked -> if (picked != null) cart.setDebtor(picked.ifEmpty { null }) }
      },
      clearCustomer = { cart.setDebtor(null) },
      //  هر «پرداخت» تسویه را از نو می‌آغازد (فرم، روش‌ها و تأییدِ کهنه نمی‌مانند)
      pay = { CheckoutSession.reset(); nav.open(SalesPaths.checkout()) },
      scan = { nav.open(SalesPaths.scan(SalesPaths.SCAN_SALE)) },
      categories = { nav.open(ProductsPaths.categories()) },
      freeLine = { nav.open(SalesPaths.FREE_LINE) },
      quickSale = { nav.open(SalesPaths.QUICK) },
      history = { nav.open(SalesPaths.HISTORY) },
      back = back,
      toggleMode = { listMode = !listMode; mode.save(listMode) },
    ),
  )
  HeldSheets(held, data)
}

/** کاشی یا فهرستِ کاتالوگ — همان انبارِ `ViewModePref` (کارت = کاشی، جدول = فهرست)، برای هر کاربر */
private class CatalogMode(context: Context) {
  private val store = ViewModePref.Store.of(context.getSharedPreferences(ViewModePref.PREFS, Context.MODE_PRIVATE))
  private val key = ViewModePref.key(runCatching { SyncStore(context).accountId }.getOrDefault(""), "sale-phone")

  fun list(): Boolean = ViewMode.parse(runCatching { store.get(key) }.getOrNull()) == ViewMode.TABLE

  fun save(list: Boolean) {
    runCatching { store.put(key, (if (list) ViewMode.TABLE else ViewMode.CARD).id) }
  }
}
