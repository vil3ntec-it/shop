package ir.vil3ntec.tohid.print

import android.content.Context
import android.graphics.Bitmap
import ir.vil3ntec.tohid.LocaleChoice
import ir.vil3ntec.tohid.LocaleText
import ir.vil3ntec.tohid.core.config.AppConfig
import ir.vil3ntec.tohid.core.hw.MonoImage
import ir.vil3ntec.tohid.core.hw.PrinterProfile
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrReceipt
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.receipt.DiscountLine
import ir.vil3ntec.tohid.core.receipt.ExchangeView
import ir.vil3ntec.tohid.core.receipt.LineView
import ir.vil3ntec.tohid.core.receipt.PaymentLine
import ir.vil3ntec.tohid.core.receipt.ReceiptDoc
import ir.vil3ntec.tohid.core.receipt.ReceiptFormat
import ir.vil3ntec.tohid.core.receipt.ReceiptInput
import ir.vil3ntec.tohid.core.receipt.ReceiptLabels
import ir.vil3ntec.tohid.core.receipt.ReceiptLayout
import ir.vil3ntec.tohid.core.receipt.ReceiptPdf
import ir.vil3ntec.tohid.core.receipt.ReceiptRaster
import ir.vil3ntec.tohid.core.receipt.ReceiptTokens
import ir.vil3ntec.tohid.core.receipt.ReturnLineView
import ir.vil3ntec.tohid.core.receipt.ReturnReceiptInput
import ir.vil3ntec.tohid.core.receipt.ReturnReceiptLabels
import ir.vil3ntec.tohid.core.receipt.ReturnReceiptLayout
import ir.vil3ntec.tohid.core.receipt.ShopHeader
import ir.vil3ntec.tohid.core.receipt.StatementLabels
import ir.vil3ntec.tohid.core.text.DariText
import ir.vil3ntec.tohid.data.LedgerOwner
import ir.vil3ntec.tohid.data.ReceiptFlag
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.ReceiptProfileStore
import ir.vil3ntec.tohid.data.ReturnFlow
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleReceipt
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.invoiceLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 *  فاکتورِ نسخهٔ ۲ — رابطِ یگانه برای چاپ، پیش‌نمایش، PNG/PDF و صفحهٔ فاکتورِ V3 (G5).
 *
 *    [doc]       بلوک‌ها برای صفحهٔ فاکتور (Compose همان‌ها را با توکن‌های V3 می‌کشد)
 *    [mono]      تصویرِ یک‌بیتیِ کاغذ — `EscPos.document` همین را می‌برد
 *    [bitmap]    پیش‌نمایش و PNG ِ «فرستادن»
 *    [pdf]       PDF ِ ۸۰ میلی‌متری برای «فرستادن»
 *    [publicUrl] نشانیِ QR (کپیِ پیوند)؛ null ⇒ فروشِ پیش از نسخهٔ ۲ یا QR ِ خاموش
 *
 *  هیچ مبلغی این‌جا حساب نمی‌شود: هر عدد همان فیلدِ ذخیره‌شدهٔ فروش است. سربرگ و لوگو
 *  فقط از `ReceiptProfileStore` (نسخهٔ نگه‌داشته)، پس چاپ بی اینترنت هم کامل است.
 */
object Receipts {

  /** پهنای PNG و PDF ِ «فرستادن» — همان کاغذِ ۸۰ میلی‌متری */
  const val SHARE_DOTS = PrinterProfile.WIDTH_80MM

  /** توکنِ QR برای فروشِ تازهٔ صندوق — فقط با پرچمِ `shop.receipt_v2`؛ خاموش ⇒ null (همان فروشِ پیشین) */
  fun newToken(context: Context): String? =
    if (ReceiptFlag.enabled(context) && !ir.vil3ntec.tohid.data.TrainingMode.on) ReceiptTokens.new() else null   //  OPS-07: فاکتورِ آموزشی نشانیِ عمومی ندارد

  fun publicUrl(context: Context, sale: Sale): String? {
    val token = sale.receiptToken ?: return null
    if (ir.vil3ntec.tohid.data.TrainingMode.on) return null
    if (!ReceiptFlag.enabled(context) || !ReceiptProfileStore.current(context).qr) return null
    return ReceiptTokens.url(AppConfig.baseUrl(context), LedgerOwner.currentShop(context), token)
  }

  /** نامِ دکان وقتی سربرگ نامی ندارد — همان «نام فروشگاه» ِ روی همین دستگاه (پیش از نسخهٔ ۲) */
  fun localName(context: Context): String =
    context.getSharedPreferences("tohid", Context.MODE_PRIVATE).getString("store_name", "").orEmpty()

  fun input(
    context: Context,
    d: ShopData,
    sale: Sale,
    widthDots: Int,
    copyMark: String? = null,
    fallbackName: String = localName(context),
    legacyQr: String? = null,
    labels: ReceiptLabels = labels(),
  ): ReceiptInput {
    val publicUrl = publicUrl(context, sale)
    val profile = ReceiptProfileStore.current(context)
    val saleItems = d.saleItems.filter { it.saleId == sale.id }
    val stamped = ir.vil3ntec.tohid.ui.PromoLive.stampOn(context)
    val items = saleItems.map { item ->
      LineView(
        name = SalesEngine.itemName(d, item),
        qty = item.quantity,
        unit = d.products.find { it.id == item.productId }?.unit.orEmpty(),
        unitPrice = item.unitPrice,
        total = item.totalPrice,
        returnedQty = item.returnedQty,
        //  پیگیریِ #307 (S3، پرچمِ `shop.promo_stamp`): قیمتِ فهرست فقط برای قلمی که تخفیفِ خودکار گرفته
        listPrice = SaleReceipt.line(item, stamped).takeIf { it.promo > 0 }?.unit,
        note = ir.vil3ntec.tohid.data.CustomerPriceCart.noteWith(item), // POS-18: «قیمتِ خاص»؛ بی منبع ⇒ همان یادداشت
      )
    }
    val time = if (sale.createdAt > 0) SimpleDateFormat("HH:mm", Locale.US).format(Date(sale.createdAt)) else ""
    return ReceiptInput(
      shop = ShopHeader(profile.name.ifBlank { fallbackName }, profile.address, profile.phone, profile.footer),
      invoiceNo = sale.invoiceLabel().orEmpty(),
      dateIso = sale.date,
      time = time,
      cashier = sale.seller,
      //  PAY (v3 A4): برچسب و ردیف‌های پرداخت همان `PayFlow` ِ تاریخچه و جزئیات
      kind = kindOf(sale, labels),
      cancelled = sale.status == "cancelled",
      items = items,
      subtotal = sale.total,
      discounts = listOf(DiscountLine(labels.discount, sale.discount)),
      //  «جمع اقلام» = جمعِ ستونِ قلم‌ها؛ تخفیفِ خودکار فقط آگاهی است و دوباره کم نمی‌شود
      included = listOfNotNull(
        SaleReceipt.included(sale, saleItems, stamped, ir.vil3ntec.tohid.ui.PromoLive::ruleName)?.let { DiscountLine(it.label, it.amount) },
      ) + ir.vil3ntec.tohid.data.LoyaltyPoints.receiptLines(d, sale.id).mapNotNull { (label, amount) -> amount?.let { DiscountLine(label, it) } }, // PRM-13
      total = sale.finalTotal,
      rounding = PayFlow.rounding(sale) ?: 0.0, // PAY-09
      //  ردیف‌های روش، «دریافتی» و «پولِ پس» — فقط فاکتوری که `payments` دارد؛ وگرنه «پرداختی»
      payments = paymentLines(sale),
      paid = sale.paidAmount,
      remaining = sale.remaining,
      debtorName = sale.debtorId?.let { id -> d.debtors.find { it.id == id }?.name },
      //  فروشِ نسخهٔ ۲: نشانیِ عمومی؛ فروشِ کهنه با «برگشتِ تازه»: کدِ `TR1:` ِ اسکنرِ برگشت (بی متنِ «اسکن کنید»)
      qrUrl = publicUrl ?: legacyQr,
      qrCaption = publicUrl != null,
      //  MKT-10: QR ِ ویترین زیرِ فاکتور — تنظیمِ همین دستگاه، پیش‌فرض خاموش
      storeQrUrl = ReceiptStoreQr.url(context),
      logo = ReceiptProfileStore.logoMono(context, widthDots),
      copyMark = ir.vil3ntec.tohid.data.TrainingMode.watermark(copyMark),   //  OPS-07: «آموزشی» روی هر فاکتور و رسیدِ برگشتِ آموزشی
      note = sale.note,
    )
  }

  fun doc(
    context: Context, d: ShopData, sale: Sale, widthDots: Int,
    copyMark: String? = null, fallbackName: String = localName(context), legacyQr: String? = null,
  ): ReceiptDoc = ReceiptLayout.build(input(context, d, sale, widthDots, copyMark, fallbackName, legacyQr), widthDots, format(ir.vil3ntec.tohid.ui.PromoLive.stampOn(context)), labels())

  fun mono(
    context: Context, d: ShopData, sale: Sale, widthDots: Int,
    copyMark: String? = null, fallbackName: String = localName(context), legacyQr: String? = null,
  ): MonoImage = ReceiptRaster.compose(doc(context, d, sale, widthDots, copyMark, fallbackName, legacyQr), ReceiptPainter(context))

  fun bitmap(
    context: Context, d: ShopData, sale: Sale, widthDots: Int = SHARE_DOTS,
    copyMark: String? = null, fallbackName: String = localName(context), legacyQr: String? = null,
  ): Bitmap = ThermalPrinter.toBitmap(mono(context, d, sale, widthDots, copyMark, fallbackName, legacyQr))

  fun pdf(context: Context, d: ShopData, sale: Sale, legacyQr: String? = null): ByteArray =
    ReceiptPdf.single(mono(context, d, sale, SHARE_DOTS, legacyQr = legacyQr))

  /**
   *  POS-34 — رسیدِ هدیهٔ بی قیمت با چیدمانِ هسته (`GiftReceiptLayout`): همان ورودیِ فاکتور، ولی هیچ
   *  مبلغ، پرداخت، قرض‌دار یا QR به کاغذ نمی‌رسد. فقط از دکمهٔ «رسیدِ هدیه» (`GiftReceiptPrefs`، خاموش).
   */
  fun giftBitmap(context: Context, d: ShopData, sale: Sale, widthDots: Int, fallbackName: String = localName(context)): Bitmap {
    val doc = ir.vil3ntec.tohid.core.receipt.GiftReceiptLayout.build(input(context, d, sale, widthDots, fallbackName = fallbackName), widthDots, format(), giftLabels())
    return ThermalPrinter.toBitmap(ReceiptRaster.compose(doc, ReceiptPainter(context)))
  }

  /* ------------------------- برچسب‌های کاغذ به زبانِ برنامه (#348) ------------------------- */

  /**
   *  برچسب‌های فاکتور، رسیدِ هدیه و صورت‌حساب به زبانِ برنامه (`StrReceipt`) — کاغذ، PNG، PDF و صفحهٔ فاکتور همه از همین‌اند:
   *  فارسی بایت‌به‌بایت همان `ReceiptLabels.FA` ِ هسته، دری همان فارسی از لایهٔ واژه (دورِ ۷، «فروشگاه» ⇐ «دکان»)، انگلیسی و
   *  پشتو ترجمه. دادهٔ فاکتور (نامِ کالا، دکان، مشتری، یادداشت) هرگز از این‌جا نمی‌گذرد. جهتِ کاغذ (راست‌به‌چپ، انگلیسی
   *  چپ‌به‌راست) با `ReceiptPainter`؛ رقم، پول و تاریخ با [format].
   */
  private val dari: Boolean get() = I18n.lang == Lang.PRS

  fun labels(): ReceiptLabels {
    val s = StrReceipt
    return ReceiptLabels(
      fallbackShop = s.fallbackShop, invoiceNo = s.invoiceNo, date = s.date, lunarDate = s.lunarDate, cashier = s.cashier, kind = s.kind,
      cancelled = s.cancelled, returned = s.returned, listPrice = s.listPrice, count = s.count, rows = s.rows, rowOne = s.rowOne,
      pieces = s.pieces, pieceOne = s.pieceOne,
      subtotal = s.subtotal, discount = s.discount, total = s.total, paid = s.paid, tendered = s.tendered, change = s.change,
      remaining = s.remaining, debtor = s.debtor, phone = s.phone, scan = s.scan, storeScan = s.storeScan, footer = s.footer,
      cash = s.cash, credit = s.credit, online = s.online, note = s.note, rounding = s.rounding,
    )
  }

  fun giftLabels(): ir.vil3ntec.tohid.core.receipt.GiftReceiptLayout.Labels =
    ir.vil3ntec.tohid.core.receipt.GiftReceiptLayout.Labels(title = StrReceipt.giftTitle, note = StrReceipt.giftNote, base = labels())

  fun statementLabels(): StatementLabels {
    val s = StrReceipt
    return StatementLabels(
      fallbackShop = s.fallbackShop, title = s.statementTitle, customer = s.customer, phone = s.phone, date = s.date, give = s.give,
      receive = s.receive, balance = s.balance, totalGive = s.totalGive, totalReceive = s.totalReceive, owes = s.owes, credit = s.inCredit,
      settled = s.settled, empty = s.empty, rows = s.rows, rowOne = s.rowOne, footer = s.statementFooter,
    )
  }

  /** «نوع» ِ فاکتور (نقدی، نسیه، ترکیبی…) به زبانِ برنامه — همان `PayFlow.payLabel` ِ تاریخچه؛ دری همان لایهٔ واژهٔ دورِ ۷ */
  fun kindOf(sale: Sale, labels: ReceiptLabels = labels()): String =
    PayFlow.payLabel(sale, cash = labels.cash).let { if (dari) DariText.of(it) else payName(it) }

  /** ردیف‌های پرداختِ ترکیبی، «دریافتی» و «پولِ پس» (`PayFlow.receiptLines`) با نامِ روش به زبانِ برنامه ([payName]) */
  fun paymentLines(sale: Sale): List<PaymentLine> =
    PayFlow.receiptLines(sale, name = ::payName).map { (label, amount) -> PaymentLine(label, amount) }

  /**
   *  نامِ روشِ پرداخت روی کاغذ («نوع» و ردیف‌های پرداخت): فقط واژهٔ ثابتِ برنامه (`PayFlow.KIND_LABELS`، `Pay.DEFAULT_METHODS`،
   *  «به حسابِ مشتری») به زبانِ برنامه؛ هر نامِ دیگر (شناسهٔ ناشناخته، نامِ تجاریِ لاتین) همان‌طور که هست. فارسی ⇒ همان.
   *  خودِ آن جدول‌ها داده می‌مانند (هم‌شکلِ `report-core.js` و ستونِ خروجی).
   */
  fun payName(name: String): String = if (I18n.lang == Lang.FA) name else PAY_NAMES[name]?.invoke() ?: name

  /** واژهٔ فارسیِ ثابت ⇒ نوشتهٔ همان معنا در `StrReceipt`؛ کلید از خودِ جدول‌ها، نه رونوشتِ متن */
  private val PAY_NAMES: Map<String, () -> String> by lazy {
    val byKind: List<Pair<String, () -> String>> = listOf(
      PayFlow.CASH to { StrReceipt.payCash }, "credit" to { StrReceipt.payCredit }, PayFlow.ACCOUNT to { StrReceipt.payCredit },
      "card" to { StrReceipt.payCard }, "transfer" to { StrReceipt.payTransfer }, "wallet" to { StrReceipt.payWallet },
      "store_credit" to { StrReceipt.payStoreCredit }, "gift_card" to { StrReceipt.payGiftCard }, "other" to { StrReceipt.payOther },
      PayFlow.MIXED to { StrReceipt.payMixed }, PayFlow.ONLINE to { StrReceipt.payOnline }, PayFlow.COD to { StrReceipt.payCod },
      ir.vil3ntec.tohid.core.pay.Pay.FX to { StrReceipt.payFx }, // PAY-10
    )
    buildMap {
      byKind.forEach { (key, text) -> PayFlow.KIND_LABELS[key]?.let { put(it, text) } }
      PayFlow.method(PayFlow.ACCOUNT)?.name?.let { put(it, { StrReceipt.payAccount }) }
      ir.vil3ntec.tohid.core.pay.Pay.DEFAULT_METHODS.firstOrNull { it.id == AFPAY_CARD }?.name?.let { put(it, { StrReceipt.payAfPayCard }) }
    }
  }

  /** شناسهٔ روشِ «کارت AfPay» در `Pay.DEFAULT_METHODS` (تنها نامِ پیش‌فرضِ فارسی جز نام‌های `KIND_LABELS`) */
  private const val AFPAY_CARD = "afpay_card"

  /* ---------------------------- رسیدِ برگشت (RET-12) ---------------------------- */

  /** برچسب‌های رسیدِ برگشت به زبانِ برنامه — همان نوشته‌های رسیدِ پیشین (`StrPos`) */
  fun returnLabels(): ReturnReceiptLabels = ReturnReceiptLabels(
    fallbackShop = StrSale.storeFallback,
    receipt = StrPos.returnReceipt("{0}"),
    fromInvoice = StrPos.fromInvoice("{0}"),
    noReceipt = StrPos.returnNoReceipt,
    restock = ReturnFlow.dispositionName(ReturnFlow.RESTOCK),
    damaged = ReturnFlow.dispositionName(ReturnFlow.DAMAGED),
    amount = StrPos.returnAmount,
    newPurchase = StrPos.newPurchaseInvoice("{0}"),
    newPurchaseTotal = StrPos.newPurchaseTotal,
    collect = StrPos.collectDifference,
    pay = StrPos.payDifference,
    reason = StrPos.reasonLine("{0}"),
    approvedBy = StrPos.approvedBy("{0}"),
    recordedBy = StrPos.recordedBy("{0}"),
    separate = StrPos.returnSeparate,
  )

  /**
   *  ورودیِ رسیدِ برگشت از سندِ ثبت‌شده (`ReturnFlow.doc`) — هیچ مبلغی این‌جا حساب نمی‌شود: ردیف‌ها،
   *  جمع، سهمِ هر مقصد و تفاوتِ تعویض همان سند است. `null` ⇒ سند پیدا نشد.
   */
  fun returnInput(
    context: Context, d: ShopData, docId: String, widthDots: Int,
    copyMark: String? = null, fallbackName: String = localName(context),
  ): ReturnReceiptInput? {
    val profile = ReceiptProfileStore.current(context)
    return returnInputOf(
      d, docId, ShopHeader(profile.name.ifBlank { fallbackName }, profile.address, profile.phone, profile.footer),
      logo = ReceiptProfileStore.logoMono(context, widthDots),
      copyMark = ir.vil3ntec.tohid.data.TrainingMode.watermark(copyMark),   //  OPS-07: «آموزشی» روی هر فاکتور و رسیدِ برگشتِ آموزشی
    )
  }

  /**
   *  همان [returnInput] بی `Context`: سندِ ثبت‌شده ⇒ ورودیِ رسید، با سربرگ و لوگوی داده‌شده (S15 — سناریوی تعویض
   *  همین را از سندِ واقعی می‌سازد). `null` ⇒ سند پیدا نشد.
   */
  fun returnInputOf(
    d: ShopData, docId: String, shop: ShopHeader, logo: MonoImage? = null, copyMark: String? = null,
  ): ReturnReceiptInput? {
    val doc = ReturnFlow.doc(d, docId) ?: return null
    fun unitOf(productId: String) = d.products.find { it.id == productId }?.unit.orEmpty()
    val refunds = buildList {
      add(PaymentLine(StrPos.offDebt, doc.split.account))
      add(PaymentLine(StrPos.storeCredit, doc.split.credit))
      doc.split.methods.forEach { (m, a) -> add(PaymentLine(StrPos.refundTo(ReturnFlow.methodName(m)), a)) }
      add(PaymentLine(StrPos.cashRefunded, doc.split.cash))
    }
    val exchange = doc.exchange?.let { ex ->
      ExchangeView(
        invoiceNo = ex.invoiceLabel().orEmpty(),
        items = d.saleItems.filter { it.saleId == ex.id }.map { si ->
          LineView(SalesEngine.itemName(d, si), si.quantity, unitOf(si.productId), si.unitPrice, si.totalPrice)
        },
        total = ex.finalTotal,
        due = doc.due ?: 0.0,
      )
    }
    return ReturnReceiptInput(
      shop = shop,
      label = doc.label,
      invoiceNo = doc.sale?.let { it.invoiceLabel().orEmpty() },
      dateIso = doc.date,
      time = if (doc.createdAt > 0) SimpleDateFormat("HH:mm", Locale.US).format(Date(doc.createdAt)) else "",
      lines = doc.rows.map { r ->
        ReturnLineView(ReturnFlow.rowName(d, r), r.quantity, unitOf(r.productId), r.amount, r.disposition == ReturnFlow.DAMAGED)
      },
      total = doc.total,
      refunds = refunds,
      reason = doc.reason,
      approvedBy = doc.approvedBy,
      by = doc.by,
      exchange = exchange,
      logo = logo,
      copyMark = copyMark,
    )
  }

  /** رسیدِ برگشت روی کاغذ/PNG با همان چیدمانِ هسته (`ReturnReceiptLayout`) — فقط با پرچمِ `shop.receipt_v2` صدا زده می‌شود */
  fun returnBitmap(
    context: Context, d: ShopData, docId: String, widthDots: Int = SHARE_DOTS,
    copyMark: String? = null, fallbackName: String = localName(context),
  ): Bitmap? {
    val input = returnInput(context, d, docId, widthDots, copyMark, fallbackName) ?: return null
    val doc = ReturnReceiptLayout.build(input, widthDots, format(), returnLabels())
    return ThermalPrinter.toBitmap(ReceiptRaster.compose(doc, ReceiptPainter(context)))
  }

  /**
   *  رقم و نمادِ همین دستگاه (LOC-05، LOC-07) به زبانِ برنامه (#348) — همان قاعدهٔ صفحه: رقم = انتخابِ دکان، جز انگلیسی که
   *  همیشه لاتین است (`I18n.screenDigits`)؛ واژهٔ پول «افغانی» / «AFN» / «افغانۍ» (`I18n.money`، «؋» در هر زبان یکی)؛ تاریخ
   *  خورشیدیِ عددی، و برای انگلیسی و پشتو همان تقویمِ صفحه (`I18n.gregorian`: انگلیسیِ «خودکار» میلادی). فارسی و دری
   *  بایت‌به‌بایت همان پیشین.
   *  @param exactUnit پرچمِ `shop.promo_stamp`: قیمتِ واحدِ اعشاری با دو رقم؛ خاموش ⇒ همان پیشین
   */
  fun format(exactUnit: Boolean = false): ReceiptFormat {
    val lang = I18n.lang
    val digits = I18n.screenDigits(lang, LocaleChoice.digits)
    val symbol = LocaleChoice.symbol
    val calendar = LocaleChoice.calendar
    val gregorian = !lang.persian && I18n.gregorian(lang, calendar)
    val lunar = LocaleChoice.lunar
    val lunarAdjust = LocaleChoice.lunarAdjust
    return object : ReceiptFormat {
      override fun money(afn: Double) = I18n.money(afn, lang, digits, symbol)
      //  قیمتِ واحدِ اعشاری (قلمِ تخفیفِ خودکار) با دو رقم؛ عددِ کامل همان `LocaleText.amount`
      override fun amount(afn: Double) = if (exactUnit) LocaleText.unitAmount(afn, digits) else LocaleText.amount(afn, digits)
      override fun qty(q: Double) = LocaleText.digitsIn(ir.vil3ntec.tohid.qty(q), digits)
      override fun digits(text: String) = LocaleText.digitsIn(text, digits)
      //  شماره جدا و چپ‌به‌راست (LRE…PDF) تا در سطرِ راست‌به‌چپ رقم‌ها جابه‌جا نشوند
      override fun phone(text: String) = "\u202A" + LocaleText.digitsIn(text, digits) + "\u202C"
      override fun date(iso: String) =
        if (gregorian) ir.vil3ntec.tohid.core.text.LocaleFormat.displayDigits(I18n.dateNumeric(iso, lang, calendar), digits)
        else LocaleText.date(iso, digits)
      //  LOC-01: فقط با تنظیمِ «تاریخِ قمری» (پیش‌فرض خاموش)
      override fun lunarDate(iso: String) = if (lunar) LocaleText.lunar(iso, digits, lunarAdjust) else null
    }
  }
}
