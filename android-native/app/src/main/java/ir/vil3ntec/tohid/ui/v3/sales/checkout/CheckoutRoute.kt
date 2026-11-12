package ir.vil3ntec.tohid.ui.v3.sales.checkout

import ir.vil3ntec.tohid.ui.components.pos.rememberLoyaltyTill
import ir.vil3ntec.tohid.ui.components.pos.LoyaltyBar
import ir.vil3ntec.tohid.ui.components.pos.LoyaltyAtTill
import ir.vil3ntec.tohid.data.TillSale
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
import ir.vil3ntec.tohid.data.GiftCardPref
import ir.vil3ntec.tohid.data.HeldRules
import ir.vil3ntec.tohid.ui.v3.sales.cart.HeldSession
import ir.vil3ntec.tohid.data.PromoEngine
import ir.vil3ntec.tohid.data.SalePost
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.SaleCapGate
import ir.vil3ntec.tohid.core.perm.SaleCaps
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.print.PrintJob
import ir.vil3ntec.tohid.print.Receipts
import ir.vil3ntec.tohid.sync.DocSeries
import ir.vil3ntec.tohid.sync.ShopNews
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
import ir.vil3ntec.tohid.ui.v3.sales.approval.CreditApprovalSheet
import ir.vil3ntec.tohid.core.gateway.TillHesab
import ir.vil3ntec.tohid.core.i18n.StrPay
import ir.vil3ntec.tohid.core.i18n.StrTillPay
import ir.vil3ntec.tohid.core.pay.Settlement
import ir.vil3ntec.tohid.pay.HesabPaySettle
import ir.vil3ntec.tohid.pay.platform.HesabPayEnv
import androidx.compose.runtime.produceState
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
    val form = CheckoutSession.form ?: CheckoutModel.start(cart.debtorId.value, data.debtors, HeldSession.extras(cart))
    //  PRM-11: تخفیفِ «فقط این گروه‌ها» با مشتریِ همین برگه (عوض کردنِ مشتری ⇒ دوباره سنجیده می‌شود)
    val promo = PromoLive.rememberOutcome(data, lines, form.debtorId)
    val priced = PromoEngine.priced(lines, promo)
    //  PRM-13: امتیازِ وفاداریِ مشتریِ همین فاکتور — همان `rememberLoyaltyTill` ِ صندوقِ کامپیوتر؛ جمع، سقفِ اعتبار و ثبت
    //  با سبدِ پس از امتیاز (`TillSale.cart`)؛ قاعدهٔ خاموش یا بی مشتری ⇒ `null` و همان سبد
    val loyalty = rememberLoyaltyTill(data, priced, form.debtorId)
    val loyal = loyalty?.cart ?: priced
    //  «نیازمند تأیید» هم کادرِ تخفیف را نشان می‌دهد؛ فقط «ممنوع» پنهانش می‌کند (STF-02)
    val canDiscount = remember { ShopRole.mayDiscount(context) }
    //  سدهای فروشِ همین دستگاه (`TillGuards`، همان `DeskSale` و فروشِ تعویض): «فروخته نشود»؛ S22 «منقضی فقط با مجوزِ مدیر»
    //  (`ExpiryPref.gate`، با «فروخته نشود» بی اثر)؛ POS-24 قاعده‌های پیش از پایانِ فاکتور (`CheckoutRulesPref`) — همه پیش‌فرض خاموش
    val tillGuards = remember { ir.vil3ntec.tohid.data.TillGuardsPref.get(context) }
    val blockExpired = tillGuards.blockExpired
    val expiryGateOn = tillGuards.expiryGate
    val checkoutRules = tillGuards.rules
    val seller = remember { ir.vil3ntec.tohid.data.TillUser.me(context) } // S46: کاربرِ صندوق؛ خاموش ⇒ همان نامِ حساب
    val payV2 = remember { ShopPolicy.payV2(context) }
    //  PAY-24: کارتِ هدیه و اعتبار در روش‌های پنل فقط با `GiftCardPref` ِ همین دستگاه (پیش‌فرض خاموش ⇒ همان روش‌ها)
    val giftOn = remember { GiftCardPref.on(context) }
    if (CheckoutSession.pay.stored != giftOn) CheckoutSession.pay.stored = giftOn
    //  PAY-06: روش‌هایی که دکان از پنل برداشته (`PayMethodsPref`، خالی ⇒ همان روش‌ها)
    val offMethods = remember { ir.vil3ntec.tohid.data.PayMethodsPref.off(context) }
    if (CheckoutSession.pay.off != offMethods) CheckoutSession.pay.off = offMethods
    //  PAY-10: ارزِ نقد فقط با تنظیمِ روشنِ دکان (`FxTillPref`، پیش‌فرض خاموش ⇒ جدولِ خالی، بی کادرِ ارز و همان پنل)
    val fxRates = remember { ir.vil3ntec.tohid.data.FxTillPref.tillRates(context) }
    if (CheckoutSession.pay.fxRates != fxRates) CheckoutSession.pay.fxRates = fxRates
    CheckoutSession.pay.clock = ir.vil3ntec.tohid.data.ReportBridge::nowMs
    //  PAY-09: پلهٔ گردکردنِ نقدیِ همین دستگاه (`CashRoundPref` از `TillGuards`، پیش‌فرض خاموش) — پنلِ ترکیبی یا نقدِ کاملِ ساده
    val cashStep = tillGuards.cashRounding
    if (CheckoutSession.pay.cashRounding != cashStep) CheckoutSession.pay.cashRounding = cashStep
    //  CRM-03 / S07 — سقفِ اعتبار در صندوق (`ShopPolicy.creditGate`، پیش‌فرض خاموش)
    val creditGateOn = remember { ShopPolicy.creditGate(context) }
    //  STF-03 — سقف‌هایی که سرور برای همین دکان اجرا می‌کند (قرض، تغییرِ قیمت، تخفیفِ پنهان)؛ پرچم‌های خاموش ⇒ `active` نادرست
    val capRules = remember { ShopRole.saleCaps(context) }
    val saleKey = remember(lines) { cart.saleKey() }
    //  POS-23: فروشندهٔ هر ردیف فقط با تنظیمِ روشنِ دکان روی قلم می‌نشیند (`LineSellerPref`)؛ خاموش ⇒ همان امروز
    val lineSellers = remember { ir.vil3ntec.tohid.data.LineSellerPref.on(context) }
    //  POS-37: مالیاتِ فاکتور فقط با پرچمِ سرورِ `shop.sale_tax` و تنظیمِ دکان (`SaleTaxPref` از `TillGuards`)؛ خاموش ⇒ `null`
    val taxConfig = tillGuards.tax
    //  «اصلاحِ فاکتور» (`SaleReopen.extras`): فروشندهٔ همان فاکتور؛ وگرنه کاربرِ صندوق. تأییدها همیشه به نامِ کاربرِ صندوق‌اند
    val invoiceSeller = HeldSession.extras(cart).seller.ifBlank { seller }
    //  همین یک `Checkout` جمع، پیش‌سنجیِ حساب‌پی و ثبت را می‌سازد ⇒ قاعده‌ها و تأییدِ منقضی همه‌جا همان‌اند
    val checkout = CheckoutModel.checkout(
      form, canDiscount, blockExpired, invoiceSeller, if (payV2) 0L else cashStep, rules = checkoutRules, expiryGate = expiryGateOn,
    ).copy(lineSellers = lineSellers, tax = taxConfig).copy(expiryApproval = CheckoutSession.expiryApproval)
    val n = CheckoutModel.numbers(data, loyal, form, checkout)
    val decision = ShopRole.decide(context, "sales.discount", DiscountGate.req(n.discount, n.subtotal))
    val gate = DiscountGate.state(decision.decision, saleKey, n.discount, n.subtotal, CheckoutSession.approval)
    val discountError = CheckoutModel.discountError(form, canDiscount)
    val paidError = CheckoutModel.paidError(form, payV2)
    val debtor = form.debtorId?.let { id -> data.debtors.find { it.id == id } }
    //  ⛔ نسیه بی مشتری (`CreditDebtor`): هر سهمِ نپرداخته مشتریِ موجود و فعال می‌خواهد ⇒ «ثبت» خاموش با «انتخابِ مشتری»
    val split = if (payV2) ir.vil3ntec.tohid.data.PayFlow.preview(CheckoutSession.pay.draft(n.finalTotal, form.debtorId)).valueOrNull() else null
    val creditBlock = CheckoutModel.creditBlock(data, form, n, payV2, split)
    //  PAY-21: «پرداخت با حساب‌پی (NFC/QR)» — فقط با `pay.hesabpay`، گیرندهٔ معلوم و نت (`TillHesab.tillNfc`)؛ یک دقیقه در حافظه
    val hesabEnv = remember { HesabPayEnv.of(context) }
    val till by produceState(TillHesab.OFF, saleKey) { value = hesabEnv.till() }

    /**
     *  S22: برگهٔ تأییدِ فروشِ منقضی (همان PIN ِ سقفِ اعتبار، `ExpiryApprovalRoute`) برای نیازِ [need] ِ همین سبد.
     *  [resubmit] ⇒ با رسیدنِ تأیید همان «ثبت»؛ از «حساب‌پی» نه (پس از تأیید کاربر دوباره «حساب‌پی» می‌زند)
     */
    fun askExpiry(need: Map<String, Long>, resubmit: Boolean) {
      CheckoutSession.expiry = CheckoutExpiry.ask(data, need, seller, ShopRole.current(context), ir.vil3ntec.tohid.data.TillUser.meId(context))
      CheckoutSession.expiryPending = resubmit
      nav.open(SalesPaths.EXPIRY)
    }

    /** @param paid تسویهٔ تأییدشدهٔ حساب‌پی (`HesabTillRoute`): کلِ فاکتور یا باقیِ پنل را پرداخته ⇒ نه نسیه، نه «پرداختی» */
    fun submit(paid: Settlement? = null) {
      if (!CheckoutModel.canSubmit(busy, discountError, paidError.takeIf { paid == null }, gate, creditBlock.takeIf { paid == null })) return
      //  POS-12: سبد همین حالا با جابه‌جایی عوض شد ⇒ ردیف‌های قبلی با کلیدِ فاکتورِ دیگر ثبت نشوند
      if (!HeldRules.shownIsCurrent(lines, cart.lines.value)) { error = StrPos.cartChangedRetry; return }
      //  برگهٔ تأییدشده همراهِ فاکتور؛ «نیازمند تأیید» بی برگه یا «ممنوع» این‌جا نمی‌رسد. STF-03: با پرچمِ قیمتِ سرور برگهٔ
      //  «تخفیفِ پنهان» هم (تخفیفِ نوشته شاید صفر یا زیرِ سقف باشد)؛ سرور برگهٔ نالازم را مصرف نمی‌کند
      val signed = if (gate == DiscountGate.State.APPROVED || capRules.price) CheckoutSession.approval?.let(DiscountGate::json) else null
      val capSlips = buildMap { CheckoutSession.approval?.let { put(SaleCaps.DISCOUNT, it) }; putAll(CheckoutSession.capSlips) }
      val options = if (paid != null) checkout.copy(settlement = paid, debtorId = form.debtorId, approval = signed)
      else if (!payV2) CheckoutModel.legacy(checkout, n, form).copy(approval = signed)
      else when (val r = CheckoutStoredPay.settle(data, CheckoutSession.pay.draft(n.finalTotal, form.debtorId))) {
        is CheckoutStoredPay.Out.Ok -> checkout.copy(settlement = r.settlement, debtorId = form.debtorId, approval = signed)
        is CheckoutStoredPay.Out.Bad -> { payError = r.message; return }
      }
      payError = null
      error = null
      //  CRM-03 / S07 (پشتِ `ShopPolicy.creditGate`): نسیهٔ بیش از سقف ⇒ «درخواستِ تأیید»؛ بی تأیید هیچ چیز
      //  ثبت نمی‌شود (`SalesEngine.record` هم دوباره می‌سنجد). تأییدِ گرفته‌شده فقط همین مشتری و تا همین برد
      //  را می‌پوشاند؛ سبدِ بزرگ‌تر یا مشتریِ دیگر دوباره می‌پرسد. خاموش ⇒ همان ثبتِ امروز.
      val gated = options.copy(creditGate = creditGateOn)
      val over = SalesEngine.creditOver(data, loyal, gated)
      //  STF-03: برگه‌های قرض و قیمتِ همین فاکتور (`SaleCapGate`) همراهِ همان ثبت
      val sale = CheckoutModel.credited(gated, over, CheckoutSession.creditApproval)
        ?.copy(creditCapSlip = SaleCapGate.json(capSlips, SaleCaps.CREDIT), priceSlip = SaleCapGate.json(capSlips, SaleCaps.PRICE),
          freeLineSlip = SaleCapGate.json(capSlips, SaleCaps.FREE_LINE))
      if (sale == null) {
        val o = over ?: return
        CheckoutSession.credit = CreditAsk(
          debtorId = gated.debtorId.orEmpty(),
          addMinor = o.addMinor,
          summary = (debtor?.name?.let { "«$it» — " } ?: "") + SalesEngine.creditOverText(o),
          seller = seller,
        )
        nav.open(SalesPaths.CREDIT)
        return
      }
      //  S22 (پشتِ `ExpiryPref.gate`): فروشی که از ماندهٔ منقضی برمی‌دارد بی تأییدِ مدیر/صاحبِ همان کالاها و تا همان مقدار ثبت
      //  نمی‌شود — ترتیبِ `DeskSale.finish` (پس از سقفِ اعتبار، پیش از سقف‌های سرور). سبدی که منقضیِ بیشتری برمی‌دارد دوباره
      //  می‌پرسد (`ExpiryGate.covers`) و `SalesEngine.record` با همان `expiryApproval` دوباره می‌سنجد.
      CheckoutExpiry.uncovered(data, loyal, sale, todayIso())?.let { need -> askExpiry(need, resubmit = true); return }
      //  STF-03 — پیش‌سنجی روی **همان** فاکتوری که ثبت و فرستاده می‌شود (پس از تخفیفِ خودکار و امتیاز)، با همان قاعدهٔ سرور
      //  (`SaleCaps`): «ممنوع» ⇒ پیام و هیچ ثبت؛ «نیازمندِ تأیید» ⇒ برگهٔ تأیید («پرسیدن از مدیر» یا PIN) و با رسیدنش همان ثبت
      if (capRules.active) {
        val trial = TillSale.record(
          data, priced, sale, cart.note.value, promo, LoyaltyAtTill.points(context, loyalty),
          today = todayIso(), now = System.currentTimeMillis(), newId = Ulid::next, saleId = saleKey,
        ) as? SalesEngine.Result.Ok
        val b = trial?.let { SaleCapGate.check(capRules, it.data, it.saleId, capSlips).blocked }
        if (b != null) {
          if (b.state == SaleCaps.State.DENIED) { error = SaleCapGate.message(b); return }
          CheckoutSession.ask = ApprovalAsk(title = SaleCapGate.title(b.perm), summary = SaleCapGate.summary(b), need = b.need)
          CheckoutSession.capPending = true
          nav.open(SalesPaths.APPROVE)
          return
        }
      }
      submitted = true
      val d = data
      val count = lines.size
      //  یادداشتِ فاکتور (POS-11) و امتیاز (PRM-13) همان لحظهٔ «ثبت»
      val note = cart.note.value
      val points = LoyaltyAtTill.points(context, loyalty)
      scope.launch {
        //  POS-14: یک ثبت در هر لحظه با کلیدِ فاکتور؛ سبد فقط پس از نوشتنِ دفتر پاک می‌شود
        //  S2 (#375): با تخفیفِ خودکار، نشانِ قیمتِ فهرست و سهمِ هر قاعده روی فاکتور — پرچمِ `shop.promo_stamp` خاموش ⇒ همان
        //  همان زنجیرهٔ صندوقِ کامپیوتر (`TillSale`): سبدِ پس از امتیاز و یادداشتِ فاکتور ⇒ ثبت ⇒ نشانِ تخفیفِ خودکار ⇒
        //  ردیف‌های مصرف و کسبِ امتیاز در همان دفتر
        //  PRM-02: پیش از ساختنِ فاکتور، زیرِ همان قفل و با کلیدِ همین فاکتور، کدِ سقف‌دار از سرور — همان دروازهٔ صندوقِ
        //  کامپیوتر (`PromoLive.redeemGate` ⇐ `TillSale.redeem`)؛ رد یا بی اینترنت ⇒ همان پیام بالای برگه و هیچ ثبتی
        when (val posted = cart.post(store, d, gate = { key -> PromoLive.redeemGate(context, key, promo) }) { key ->
          //  STF-03: آخرین سد — فاکتوری که سرور بی خبر رد می‌کرد نوشته نمی‌شود (صفحه پیش‌تر پرسیده است)
          SaleCapGate.guard(capRules, TillSale.record(
            d, priced, sale, note, promo, points,
            today = todayIso(),
            now = System.currentTimeMillis(),
            newId = Ulid::next,
            //  F0-03: «سری سند»ِ این دستگاه — `{سری}-{شماره}`
            series = DocSeries.current(context),
            saleId = key,
            //  فاکتورِ نسخهٔ ۲: توکنِ QR فقط با پرچمِ `shop.receipt_v2`
            receiptToken = Receipts.newToken(context),
          ), capSlips)
        }) {
          SalePost.Outcome.Busy -> submitted = false
          SalePost.Outcome.NotSaved -> { submitted = false; error = SalePost.NOT_SAVED }
          is SalePost.Outcome.Refused -> { submitted = false; error = posted.message }
          is SalePost.Outcome.Done -> {
            Haptics.success(context)
            val sold = posted.ok.data.sales.find { it.id == posted.ok.saleId }
            //  فروشِ سریع همان لحظه به بقیهٔ اعضای دکان خبر می‌داد — همان می‌ماند
            if (quick && sold != null) ShopNews.sale(context, sold.finalTotal, count)
            CheckoutSession.hesabPaid = null
            CheckoutSession.done = CheckoutDone(posted.ok.data, posted.ok.saleId)
          }
        }
      }
    }

    //  تأییدِ سقفِ اعتبار تازه رسید ⇒ همان فاکتور همان دم ثبت می‌شود (مثلِ رابطِ امروز)؛ اگر در این میان سبد یا
    //  مشتری عوض شده و تأیید دیگر نمی‌پوشاند، `submit` دوباره می‌پرسد
    val freshCredit = CheckoutSession.creditApproval
    LaunchedEffect(freshCredit) { if (freshCredit != null && !submitted) submit() }
    //  STF-03: تأییدِ سقفی که خودِ «ثبت» پرسید رسید ⇒ همان ثبت (برگهٔ بسته‌شده بی تأیید ⇒ هیچ)
    val capTick = CheckoutSession.capSlips to CheckoutSession.approval
    LaunchedEffect(capTick) {
      if (CheckoutSession.capPending && !submitted) { CheckoutSession.capPending = false; submit() }
    }
    //  حساب‌پی تأیید شد ⇒ همان دم ثبت با همان تسویه؛ ثبتِ ردشده (سبد عوض شد، موجودی) ⇒ همان ردیف «ثبتِ فروش» می‌شود
    val hesabPaid = CheckoutSession.hesabPaid
    LaunchedEffect(hesabPaid) { if (hesabPaid != null && !submitted) submit(hesabPaid) }
    //  S22: تأییدِ منقضی که خودِ «ثبت» پرسید رسید ⇒ همان ثبت (با همان تسویهٔ حساب‌پی اگر پول گرفته شده بود)؛ انصراف ⇒ هیچ
    val freshExpiry = CheckoutSession.expiryApproval
    LaunchedEffect(freshExpiry) {
      if (freshExpiry != null && CheckoutSession.expiryPending && !submitted) { CheckoutSession.expiryPending = false; submit(CheckoutSession.hesabPaid) }
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
      submit = { submit() },
      close = ::close,
      hesabPay = {
        //  S22: منقضیِ نپوشیده ⇒ تأییدِ مدیر پیش از گرفتنِ پول (پیش‌سنجیِ زیر هم با همان `checkout` رد می‌کرد)
        val expired = if (hesabPaid == null) CheckoutExpiry.uncovered(data, loyal, checkout, todayIso()) else null
        if (hesabPaid != null) submit(hesabPaid)
        else if (expired != null) askExpiry(expired, resubmit = false)
        else {
          val draft = if (payV2) CheckoutSession.pay.draft(n.finalTotal, form.debtorId) else null
          //  پیش از گرفتنِ پول: همین فاکتور همین حالا ثبت‌شدنی است؟ (موجودی، تکرار، قاعده‌های POS-24) — همان سنجشِ صندوقِ کامپیوتر
          CheckoutSession.hesab = HesabTillModel.Ask(saleKey, n.finalTotal, draft, till) {
            HesabTillModel.precheck(data, loyal, checkout.copy(debtorId = form.debtorId), saleKey, todayIso())
          }
          CheckoutSession.hesabEnv = hesabEnv
          nav.open(SalesPaths.HESAB)
        }
      },
    )
    CheckoutView(
      form = form,
      numbers = n,
      rows = CheckoutModel.rows(n, promo, payV2, loyalty?.points ?: 0, loyalty?.off ?: 0.0),
      heroLine = CheckoutModel.heroLine(n, promo, PhoneSaleText::amount, loyalty?.off ?: 0.0),
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
      canSubmit = CheckoutModel.canSubmit(busy, discountError, paidError, gate, creditBlock),
      creditBlock = creditBlock,
      hesab = when {
        hesabPaid != null -> HesabEntry(StrPay.recordSale, StrTillPay.confirmed, enabled = !busy)
        till.tillNfc && HesabPaySettle.due(n.finalTotal, if (payV2) CheckoutSession.pay.draft(n.finalTotal, form.debtorId) else null) is HesabPaySettle.Due.Amount ->
          HesabEntry(StrTillPay.entry, if (till.gateway) StrTillPay.entryAuto else StrTillPay.entrySimple, enabled = !busy && discountError == null && gate != DiscountGate.State.NEEDS_APPROVAL && gate != DiscountGate.State.DENIED)
        else -> null
      },
      canAskApproval = !busy,
      giftLedger = data.takeIf { giftOn },
      points = if (loyalty != null) ({ LoyaltyBar(loyalty) }) else null,
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
    requesterUserId = remember { ir.vil3ntec.tohid.data.TillUser.meId(context) },
    //  STF-03: برگهٔ قرض و قیمت جدا از برگهٔ تخفیف (`Sale.creditApproval` / `priceApproval`)
    onApproved = { s ->
      if (s.perm == SaleCaps.DISCOUNT) CheckoutSession.approval = s else CheckoutSession.capSlips = CheckoutSession.capSlips + (s.perm to s)
      back()
    },
    onClose = { CheckoutSession.capPending = false; back() },
  )
}

/** `sale/checkout/credit`: تأییدِ فروشِ نسیهٔ بیش از سقف (CRM-03)؛ تأیید در `CheckoutSession` می‌ماند و «ثبت» با آن می‌رود */
@Composable
fun RouteScope.CreditApprovalRoute() {
  val context = LocalContext.current
  val ask = CheckoutSession.credit
  if (ask == null) { LaunchedEffect(Unit) { back() }; return }
  CreditApprovalSheet(
    summary = ask.summary,
    debtorId = ask.debtorId,
    addMinor = ask.addMinor,
    selfRole = remember { ShopRole.current(context) },
    selfName = ask.seller,
    requesterUserId = remember { ir.vil3ntec.tohid.data.TillUser.meId(context) },
    onApproved = { CheckoutSession.creditApproval = it; CheckoutSession.credit = null; back() },
    onClose = { CheckoutSession.credit = null; back() },
  )
}

/** نامِ دکان روی رسید — همان که چاپ می‌نویسد */
internal fun storeName(context: Context): String =
  runCatching { context.getSharedPreferences("tohid", Context.MODE_PRIVATE).getString("store_name", "") }.getOrNull().orEmpty()
