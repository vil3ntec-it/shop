package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrFx
import ir.vil3ntec.tohid.core.pay.ExchangeRecord
import ir.vil3ntec.tohid.core.pay.FxRate
import ir.vil3ntec.tohid.core.pay.FxRateInput
import ir.vil3ntec.tohid.core.pay.FxTill
import ir.vil3ntec.tohid.core.pay.PayResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 *  PAY-10 — ردیفِ ارزِ نقدِ یک فاکتور (`SalePayment.fx`، فقط روی ردیفِ `fx`): همان رکوردِ تبادلِ هسته (`ExchangeRecord`)
 *  که در لحظهٔ فروش ثابت شد. `amountMinor` ِ خودِ ردیف سهمِ افغانیِ همین فاکتور است؛ [afnMinor] ارزشِ همهٔ ارزِ گرفته‌شده
 *  و تفاوتشان پولِ پسِ افغانی ([FxTillFlow.changeOut]). نرخِ تازه هیچ‌کدام را عوض نمی‌کند.
 */
@Serializable
data class SaleFx(
  val currency: String,
  /** مبلغِ دریافتی به واحدِ کوچکِ همان ارز (سنت، پیسه، ریال) */
  val amount: Long,
  /** پولِ افغانی برای [per] واحدِ ارز */
  val rate: Long,
  val per: Long = 1,
  /** ارزشِ افغانیِ همهٔ [amount] به پول */
  val afnMinor: Long,
  val source: String,
  /** زمانِ گذاشتنِ نرخ */
  val at: Long,
  /** گذارنده/تأییدکنندهٔ نرخ */
  val by: String,
)

/**
 *  سیم‌کشیِ PAY-10 در برنامه — خالص (نه Context، نه ساعت): تنظیمِ نرخِ دکان (`tohid-policy/fx_rates`، JSON)،
 *  پیش‌نویسِ صندوق ⇒ رکوردِ هسته (`FxTill.tender`)، ردیفِ فاکتور، رسید، پولِ پسِ افغانی و جمعِ ارزِ X/Z.
 *  حساب فقط در `core/pay/FxTill` و `Pay.settle`؛ این‌جا فقط نگاشت.
 */
object FxTillFlow {

  /** ارزهای فرمِ نرخ و صندوق، به همین ترتیب */
  val CURRENCIES: List<String> = listOf("USD", "PKR", "IRR")

  /** نامِ ارز به زبانِ برنامه («دالر (USD)»)؛ کدِ ناشناخته همان کد */
  fun name(code: String): String = when (code) {
    "USD" -> StrFx.usd
    "PKR" -> StrFx.pkr
    "IRR" -> StrFx.irr
    else -> code
  }

  /** کادرِ ارزِ صندوق: جدولِ نرخِ دکان، ارزِ برگزیده، متنِ مبلغ و «اکنون» ِ صفحه */
  data class Tender(val rates: List<FxRate>, val currency: String, val amount: String, val now: Long)

  // ── تنظیمِ دکان ─────────────────────────────────────────
  private fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
  private fun JsonObject.l(k: String): Long? = (this[k] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

  /** متنِ تنظیم ⇒ جدولِ سنجیده؛ خالی، خراب یا هر ردیفِ نادرست ⇒ هیچ نرخ (صندوق ارز نمی‌پذیرد، نه حدس) */
  fun decode(text: String?): List<FxRate> {
    if (text.isNullOrBlank()) return emptyList()
    val arr = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonArray ?: return emptyList()
    val inputs = arr.map { e ->
      val o = e as? JsonObject ?: return emptyList()
      FxRateInput(o.s("currency"), o.l("rate"), o.l("per"), o.s("source"), o.l("at"), o.s("by"))
    }
    return when (val r = FxTill.rates(inputs)) {
      is PayResult.Ok -> r.value
      is PayResult.Err -> emptyList()
    }
  }

  fun encode(list: List<FxRate>): String = buildJsonArray {
    list.forEach { r ->
      add(
        buildJsonObject {
          put("currency", r.currency); put("rate", r.rate); put("per", r.per)
          put("source", r.source); put("at", r.at); put("by", r.by)
        }
      )
    }
  }.toString()

  /**
   *  «ذخیرهٔ نرخ»: نرخِ [currency] = [rateText] افغانی برای واحدِ پیش‌فرضِ همان ارز (`FxTill.DEFAULT_PER`)، با [source]،
   *  زمانِ [now] و گذارندهٔ [by]؛ ردیفِ پیشینِ همان ارز جایگزین می‌شود. خطا: `BAD_RATE` با `field` (همان `FxTill.rates`).
   */
  fun withRate(list: List<FxRate>, currency: String, rateText: String, source: String, now: Long, by: String): PayResult<List<FxRate>> {
    val rate = FxTill.parseAmount(rateText, FxTill.BASE)?.takeIf { it > 0 } ?: return PayResult.Err("BAD_RATE", field = "rate")
    val rows = list.filter { it.currency != currency }.map { FxRateInput(it.currency, it.rate, it.per, it.source, it.at, it.by) } +
      FxRateInput(currency, rate, FxTill.DEFAULT_PER[currency] ?: 1L, source, now, by)
    return when (val r = FxTill.rates(rows)) {
      is PayResult.Ok -> PayResult.Ok(r.value.sortedBy { CURRENCIES.indexOf(it.currency) })
      is PayResult.Err -> r
    }
  }

  fun remove(list: List<FxRate>, currency: String): List<FxRate> = list.filter { it.currency != currency }

  /** نرخ هنوز در صندوق پذیرفته است؟ (همان مرزِ `FxTill.tender`) */
  fun fresh(r: FxRate, now: Long): Boolean = (now - r.at).let { it <= FxTill.MAX_AGE_MS && it >= -FxTill.SKEW_MS }

  // ── صندوق ─────────────────────────────────────────────────
  /** کادرِ ارز ⇒ رکوردِ تبادلِ همین لحظه؛ بی کادر یا مبلغِ خالی ⇒ `null` (بی ارز، همان تسویهٔ امروز) */
  fun record(t: Tender?): PayResult<ExchangeRecord>? {
    if (t == null || t.amount.isBlank()) return null
    val minor = FxTill.parseAmount(t.amount, t.currency) ?: return PayResult.Err("INVALID_AMOUNT")
    return FxTill.tender(t.rates, t.currency, minor, t.now)
  }

  fun saleFx(r: ExchangeRecord): SaleFx = SaleFx(r.fromCurrency, r.fromAmount, r.rate, r.per, r.toAmount, r.source, r.at, r.approvedBy)

  /** «10.00 USD · 1 USD = 70.25 AFN» — مبلغِ اصلیِ ارز و نرخِ همان لحظه (فاکتور، رسید، جزئیات) */
  fun detail(fx: SaleFx): String = "${FxTill.format(fx.amount, fx.currency)} ${fx.currency} · ${FxTill.rateLine(fx.rate, fx.per, fx.currency)}"

  /** پولِ پسِ افغانی که برای ارزِ این فاکتور از دخل رفت (پول): ارزشِ ارز منهای سهمِ فاکتور؛ بی ارز ⇒ ۰ */
  fun changeOut(s: Sale): Long =
    s.payments.orEmpty().sumOf { p -> p.fx?.let { (it.afnMinor - p.amountMinor).coerceAtLeast(0L) } ?: 0L }

  // ── X/Z ───────────────────────────────────────────────────
  data class Total(val currency: String, val amount: Long, val afnMinor: Long, val count: Int)

  /**
   *  ارزِ نقدِ دریافتیِ بازه به تفکیکِ ارز — همان اسکناسی که باید در کشو باشد (برگشت به افغانی پس داده می‌شود و این را
   *  کم نمی‌کند). فروشِ لغوشده و اینترنتی بیرون.
   */
  fun totals(d: ShopData, from: Long, to: Long): List<Total> {
    val by = LinkedHashMap<String, Total>()
    d.sales.filter { it.status != "cancelled" && !CashShift.isOnline(it) && it.createdAt in from..to }.forEach { s ->
      s.payments.orEmpty().forEach { p ->
        val fx = p.fx ?: return@forEach
        val t = by[fx.currency] ?: Total(fx.currency, 0, 0, 0)
        by[fx.currency] = Total(fx.currency, t.amount + fx.amount, t.afnMinor + fx.afnMinor, t.count + 1)
      }
    }
    return by.values.sortedBy { CURRENCIES.indexOf(it.currency) }
  }
}
