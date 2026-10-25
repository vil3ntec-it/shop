package ir.vil3ntec.tohid.ui.v3.sales.checkout

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.data.DiscountGate
import ir.vil3ntec.tohid.data.HeldRules
import ir.vil3ntec.tohid.ui.v3.sales.cart.HeldSession
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.PromoEngine
import ir.vil3ntec.tohid.data.SalePost
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.print.PrintJob
import ir.vil3ntec.tohid.print.Receipts
import ir.vil3ntec.tohid.sync.DocSeries
import ir.vil3ntec.tohid.sync.ShopNews
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.sync.v1.Ulid
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.PromoLive
import ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths
import ir.vil3ntec.tohid.ui.v3.sales.approval.ApprovalAsk
import ir.vil3ntec.tohid.ui.v3.sales.approval.ApprovalSheet
import kotlinx.coroutines.launch

/**
 *  چسبِ `sale/checkout` (`?from=quick` از فروشِ سریع): سبدِ زنده با قیمتِ پس از تخفیف‌های
 *  خودکار، فرم از `CheckoutSession` (رفتن به انتخابِ مشتری یا تأیید و برگشت چیزی را پاک
 *  نمی‌کند)، و ثبت دقیقاً همان راهِ پنجرهٔ پیشین: `CartStore.post` (یک ثبت در هر لحظه با کلیدِ
 *  فاکتور، چاپِ پس از فروش) و `SalesEngine.record`. پاسخ: «ثبت نشد» و ردِ موتور بالای برگه؛
 *  ثبت ⇒ لرزشِ موفقیت، خبرِ فروشِ سریع (`ShopNews.sale`، همان امروز) و رسید در همین برگه.
 */
@Composable
fun RouteScope.CheckoutRoute() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val lines by cart.lines.collectAsState()
  val busy by cart.posting.collectAsState()
  val done = CheckoutSession.done
  val quick = args.query("from") == SalesPaths.FROM_QUICK
  //  ثبت شد و سبد پاک شد ولی رسید هنوز نرسیده: همان فرمِ آخر یک لحظه می‌ماند (نه «۰ ؋»)
  var submitted by remember { mutableStateOf(false) }
  val last = remember { arrayOfNulls<CheckoutView>(1) }
  var error by remember { mutableStateOf<String?>(null) }
  var payError by remember { mutableStateOf<String?>(null) }

  fun close() {
    if (CheckoutSession.done != null) CheckoutSession.reset()
    back()
  }

  val doneSale = done?.let { x -> x.data.sales.find { it.id == x.saleId } }
  if (done != null && doneSale == null) { LaunchedEffect(Unit) { close() }; return }
  //  سبدِ خالی چیزی برای تسویه ندارد (مثلاً پیوندِ مستقیم)
  if (done == null && lines.isEmpty() && !submitted) { LaunchedEffect(Unit) { back() }; return }

  var actions = CheckoutActions({}, {}, {}, {}, {}, {}, {}, ::close)
  val view: CheckoutView? = if (done != null || lines.isEmpty()) last[0] else {
    val promo = PromoLive.rememberOutcome(data, lines)
    val priced = PromoEngine.priced(lines, promo)
    val form = CheckoutSession.form ?: CheckoutModel.start(cart.debtorId.value, data.debtors, HeldSession.extras(cart).discount)
    //  «نیازمند تأیید» هم کادرِ تخفیف را نشان می‌دهد؛ فقط «ممنوع» پنهانش می‌کند (STF-02)
    val canDiscount = remember { ShopRole.mayDiscount(context) }
    val blockExpired = remember { ShopPolicy.blockExpired(context) }
    val seller = remember { runCatching { SyncStore(context).accountName }.getOrDefault("") }
    val payV2 = remember { ShopPolicy.payV2(context) }
    val saleKey = remember(lines) { cart.saleKey() }
    val checkout = CheckoutModel.checkout(form, canDiscount, blockExpired, seller)
    val n = CheckoutModel.numbers(data, priced, form, checkout)
    val decision = ShopRole.decide(context, "sales.discount", DiscountGate.req(n.discount, n.subtotal))
    val gate = DiscountGate.state(decision.decision, saleKey, n.discount, n.subtotal, CheckoutSession.approval)
    val discountError = CheckoutModel.discountError(form, canDiscount)
    val paidError = CheckoutModel.paidError(form, payV2)
    val debtor = form.debtorId?.let { id -> data.debtors.find { it.id == id } }

    fun submit() {
      if (!CheckoutModel.canSubmit(busy, discountError, paidError, gate)) return
      //  POS-12: سبد همین حالا با جابه‌جایی عوض شد ⇒ ردیف‌های قبلی با کلیدِ فاکتورِ دیگر ثبت نشوند
      if (!HeldRules.shownIsCurrent(lines, cart.lines.value)) { error = StrPos.cartChangedRetry; return }
      //  برگهٔ تأییدشده همراهِ فاکتور؛ «نیازمند تأیید» بی برگه یا «ممنوع» این‌جا نمی‌رسد
      val signed = if (gate == DiscountGate.State.APPROVED) CheckoutSession.approval?.let(DiscountGate::json) else null
      val options = if (!payV2) CheckoutModel.legacy(checkout, n, form).copy(approval = signed)
      else when (val r = PayFlow.checkout(CheckoutSession.pay.draft(n.finalTotal, form.debtorId))) {
        is PayFlow.Outcome.Ok -> checkout.copy(settlement = r.value, debtorId = form.debtorId, approval = signed)
        is PayFlow.Outcome.Bad -> { payError = PayFlow.message(r.problem); return }
      }
      payError = null
      error = null
      submitted = true
      val d = data
      val count = lines.size
      scope.launch {
        //  POS-14: یک ثبت در هر لحظه با کلیدِ فاکتور؛ سبد فقط پس از نوشتنِ دفتر پاک می‌شود
        when (val posted = cart.post(store) { key ->
          SalesEngine.record(
            d, priced, options,
            today = todayIso(),
            now = System.currentTimeMillis(),
            newId = Ulid::next,
            //  F0-03: «سری سند»ِ این دستگاه — `{سری}-{شماره}`
            series = DocSeries.current(context),
            saleId = key,
            //  فاکتورِ نسخهٔ ۲: توکنِ QR فقط با پرچمِ `shop.receipt_v2`
            receiptToken = Receipts.newToken(context),
          )
        }) {
          SalePost.Outcome.Busy -> submitted = false
          SalePost.Outcome.NotSaved -> { submitted = false; error = SalePost.NOT_SAVED }
          is SalePost.Outcome.Refused -> { submitted = false; error = posted.message }
          is SalePost.Outcome.Done -> {
            Haptics.success(context)
            val sold = posted.ok.data.sales.find { it.id == posted.ok.saleId }
            //  فروشِ سریع همان لحظه به بقیهٔ اعضای دکان خبر می‌داد — همان می‌ماند
            if (quick && sold != null) ShopNews.sale(context, sold.finalTotal, count)
            CheckoutSession.done = CheckoutDone(posted.ok.data, posted.ok.saleId)
          }
        }
      }
    }

    actions = CheckoutActions(
      form = { CheckoutSession.form = it },
      customer = {
        nav.openForResult(SalesPaths.customer(form.debtorId)) { picked ->
          if (picked != null) CheckoutSession.form = (CheckoutSession.form ?: form).copy(debtorId = picked.ifEmpty { null })
        }
      },
      clearCustomer = { CheckoutSession.form = form.copy(debtorId = null) },
      approve = {
        CheckoutSession.ask = ApprovalAsk(
          title = StrSale.discountApproval,
          summary = StrSale.discountApprovalSummary(PhoneSaleText.amount(n.discount), PhoneSaleText.amount(n.subtotal)),
          need = DiscountGate.need(saleKey, n.discount, n.subtotal),
        )
        nav.open(SalesPaths.APPROVE)
      },
      addCoupon = { PromoLive.addCoupon(it) },
      removeCoupon = { PromoLive.removeCoupon(it) },
      submit = ::submit,
      close = ::close,
    )
    CheckoutView(
      form = form,
      numbers = n,
      rows = CheckoutModel.rows(n, promo, payV2),
      heroLine = CheckoutModel.heroLine(n, promo, PhoneSaleText::amount),
      promo = promo,
      showCodes = PromoLive.snap.value.active && PromoLive.snap.value.hasCodes,
      coupons = PromoLive.coupons.value,
      canDiscount = canDiscount,
      payV2 = payV2,
      gate = gate,
      gateNote = CheckoutModel.gateNote(decision.cap, decision.limit),
      customerName = debtor?.name,
      customerBalance = debtor?.let { CustomerPickerModel.label(ShopStore.debt(data, it.id)) },
      busy = busy,
      error = error,
      discountError = discountError,
      paidError = paidError,
      payError = payError,
      canSubmit = CheckoutModel.canSubmit(busy, discountError, paidError, gate),
      canAskApproval = !busy,
    ).also { last[0] = it }
  }

  //  یک برگه برای فرم و رسید: پس از ثبت همان برگه رسید می‌شود (نه برگهٔ تازه)
  CheckoutSheet(
    view = view,
    pay = CheckoutSession.pay,
    actions = actions,
    receipt = if (done != null && doneSale != null) ({ closer ->
      CheckoutReceipt(
        d = done.data,
        sale = doneSale,
        storeName = remember { storeName(context) },
        onPrint = { nav.open(SalesPaths.print(doneSale.id)) },
        onSend = if (Platform.DESKTOP) null else ({
          scope.launch { PrintJob.shareSale(context, done.data, doneSale)?.let { toasts.show(it, V3Tone.WARNING) } }
        }),
        onNewSale = { closer.close() },
      )
    }) else null,
  )
}

/** `sale/checkout/approve`: برگهٔ تأییدِ تخفیف؛ برگهٔ امضاشده در `CheckoutSession` می‌ماند */
@Composable
fun RouteScope.ApprovalRoute() {
  val context = LocalContext.current
  val ask = CheckoutSession.ask
  if (ask == null) { LaunchedEffect(Unit) { back() }; return }
  ApprovalSheet(
    ask = ask,
    shopId = remember { ShopRole.shopId(context) },
    requesterUserId = remember { runCatching { SyncStore(context).accountId }.getOrDefault("") },
    onApproved = { CheckoutSession.approval = it; back() },
    onClose = { back() },
  )
}

/** نامِ دکان روی رسید — همان که چاپ می‌نویسد */
internal fun storeName(context: Context): String =
  runCatching { context.getSharedPreferences("tohid", Context.MODE_PRIVATE).getString("store_name", "") }.getOrNull().orEmpty()
