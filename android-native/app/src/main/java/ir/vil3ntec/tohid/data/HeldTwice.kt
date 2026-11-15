package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.money.Currency
import ir.vil3ntec.tohid.core.money.Money
import ir.vil3ntec.tohid.core.money.Qty
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 *  S09 — «یک فاکتور در دو دستگاه»: لغوِ خودکارِ **فروشِ دوم** (خالص؛ آزمون: `HeldTwiceTest`). پشتِ پرچمِ خاموشِ
 *  `shop.held_twice_cancel` (`HeldSyncState.autoCancel`)؛ خاموش ⇒ همان «دوتایی» ِ پیشین که کاربر خودش یکی را برمی‌گرداند.
 *
 *  کی «دوم» است: دستگاهی که بازگرداندنِ بی شبکه‌اش پس از وصل `held_conflict` گرفت (سرور گفت دستگاهِ دیگری **پیش‌تر**
 *  همان را برگرداند/فروخت) — همین دستگاه، و فقط فروشِ **خودش** ([HeldConflict.sale]، کلیدِ تازهٔ همان بازگرداندن).
 *  کی لغو می‌شود ([decide]) — فقط وقتی دو فروش بی‌گمان یکی‌اند:
 *   - فروشِ دوم در دفتر هست، لغو نشده، سفارشِ اینترنتی نیست و هیچ برگشتی ندارد؛
 *   - در دفتر **دقیقاً یک** فروشِ دیگر از سریِ دیگر (دستگاهِ دیگر)، لغونشده، پس از نسخه‌ای که این دستگاه دیده بود
 *     ([HeldConflict.base]، با [SKEW_MS] برای ساعتِ دستگاه‌ها) هست که قلم‌به‌قلم (کالا، مقدار، قیمت)، مبلغِ نهایی و
 *     قرض‌دار با آن یکی است. صفر ⇒ هنوز نرسیده (صبر تا [WAIT_MS])؛ بیش از یک یا ناهمسان ⇒ دست نمی‌خورد (کاربر داوری می‌کند).
 *  چگونه: از راهِ لغوِ عادیِ برنامه (`SalesVoid.run` ⇐ `store.save` ⇐ سندِ معکوسِ زنجیره)، با دلیلِ ثابتِ [REASON]؛ اگر کاربرِ
 *  دستگاه اجازهٔ «لغوِ فاکتور» ندارد لغو نمی‌شود. در هر دو حال گزارش ([body]) به صندوقِ تعارضِ صاحب می‌رود — پول بی‌صدا نمی‌رود.
 */
object HeldTwice {

  /** ساعتِ دو دستگاه تا این اندازه جدا ⇒ هنوز «پس از» نسخهٔ دیده‌شده */
  const val SKEW_MS = 6 * 60 * 60_000L

  /** فروشِ نخست تا این مدت پس از «دوتایی» در دفتر نیامد ⇒ داوری رها می‌شود (همان «دوتایی» ِ پیشین) */
  const val WAIT_MS = 7 * 24 * 60 * 60_000L

  sealed interface Decision {
    /** فروشِ نخست (یا خودِ فروشِ دوم) هنوز در دفتر نیست */
    data object Wait : Decision
    /** چیزی برای کردن نیست یا بی‌گمان نیست ⇒ کاربر داوری می‌کند */
    data object Leave : Decision
    /** فروشِ دوم لغو شود؛ [kept] فروشِ نخست */
    data class Cancel(val kept: Sale) : Decision
    /** یکی‌اند ولی این کاربر اجازهٔ لغو ندارد ⇒ فقط گزارش به صاحب */
    data class Report(val kept: Sale) : Decision
  }

  /** دلیلِ لغو روی فاکتور (دادهٔ دفتر، همگام می‌شود — مثلِ `InvoiceVoid.CORRECTION_REASON`) */
  const val REASON = "یک فاکتور در دو دستگاه — لغوِ خودکار" // داده: دلیلِ ذخیره‌شدهٔ لغو

  private val AFN = Currency.AFN
  private fun minor(v: Double): Long = Money.fromLegacy(v, AFN).minor

  /** امضای فروش: مبلغِ نهایی، قرض‌دار و قلم‌ها (کالا، مقدار به هزارم، قیمتِ واحد به پول)، مرتب */
  fun signature(d: ShopData, s: Sale): String? = runCatching {
    val items = d.saleItems.filter { it.saleId == s.id }
      .map { "${it.productId}|${Qty.fromLegacy(it.quantity).milli}|${minor(it.unitPrice)}" }.sorted()
    if (items.isEmpty()) return@runCatching null
    "${minor(s.finalTotal)}#${s.debtorId.orEmpty()}#" + items.joinToString(";")
  }.getOrNull()

  fun decide(d: ShopData, c: HeldConflict, now: Long, canCancel: Boolean): Decision {
    if (c.sale.isBlank()) return Decision.Leave
    val waited = c.at > 0 && now - c.at > WAIT_MS
    val mine = d.sales.firstOrNull { it.id == c.sale } ?: return if (waited) Decision.Leave else Decision.Wait
    if (mine.status == "cancelled" || mine.isOnlineOrder() || d.saleReturns.any { it.saleId == mine.id }) return Decision.Leave
    val sig = signature(d, mine) ?: return Decision.Leave
    val from = c.base - SKEW_MS
    val same = d.sales.filter {
      it.id != mine.id && it.status != "cancelled" && !it.isOnlineOrder() && it.series.orEmpty() != mine.series.orEmpty() &&
        it.createdAt >= from && signature(d, it) == sig
    }
    return when {
      same.isEmpty() -> if (waited) Decision.Leave else Decision.Wait
      same.size > 1 -> Decision.Leave
      canCancel -> Decision.Cancel(same.single())
      else -> Decision.Report(same.single())
    }
  }

  /**
   *  یک دور پس از همگام‌سازیِ نگه‌داشته‌ها (`HeldTwiceAndroid.sweep`): هر «دوتایی» ِ دارای فروشِ همین دستگاه ⇐ [decide] ⇐
   *  لغو با [cancel] (راهِ عادیِ برنامه؛ دفترِ پس از لغو یا null اگر نشست) یا فقط گزارش ⇐ [HeldSyncEngine.report]. [ledger]
   *  دفترِ همین لحظه. پرچمِ خاموش ⇒ هیچ.
   */
  suspend fun sweep(
    engine: HeldSyncEngine,
    ledger: () -> ShopData,
    canCancel: Boolean,
    now: Long,
    device: String,
    cancel: suspend (Sale) -> ShopData?,
  ) {
    if (!engine.autoCancel) return
    for ((id, c) in engine.twiceOpen()) {
      val d = ledger()
      if (c.kept.isNotBlank()) {
        //  داوری شده ولی گزارش نرسیده ⇒ فقط گزارش
        val sale = d.sales.firstOrNull { it.id == c.sale }
        val kept = d.sales.firstOrNull { it.id == c.kept }
        if (sale == null || kept == null) engine.giveUp(id) else engine.report(id, body(d, sale, kept, c.cancelled, device))
        continue
      }
      when (val decision = decide(d, c, now, canCancel)) {
        Decision.Wait -> Unit
        Decision.Leave -> engine.giveUp(id)
        is Decision.Report -> {
          engine.settle(id, decision.kept.id, cancelled = false)
          engine.report(id, body(d, d.sales.first { it.id == c.sale }, decision.kept, false, device))
        }
        is Decision.Cancel -> {
          val sale = d.sales.first { it.id == c.sale }
          //  موتور یا دیسک نپذیرفت ⇒ دست نخورد؛ همان «دوتایی» تا کاربر داوری کند
          val after = cancel(sale) ?: run { engine.giveUp(id); null } ?: continue
          engine.settle(id, decision.kept.id, cancelled = true)
          engine.report(id, body(after, sale, decision.kept, true, device))
        }
      }
    }
  }

  /** بدنهٔ `POST /api/shop/held-invoices/:id/twice` (`server/src/lib/held-twice.js`) */
  fun body(d: ShopData, sale: Sale, kept: Sale, cancelled: Boolean, device: String): JsonObject = buildJsonObject {
    put("sale_id", JsonPrimitive(sale.id))
    put("kept_id", JsonPrimitive(kept.id))
    kept.series?.takeIf { it.isNotBlank() }?.let { put("kept_series", JsonPrimitive(it)) }
    put("cancelled", JsonPrimitive(cancelled))
    sale.invoiceNumber?.takeIf { it > 0 }?.let { put("number", JsonPrimitive(it)) }
    put("total_minor", JsonPrimitive(maxOf(0L, minor(sale.finalTotal))))
    put("created_at", JsonPrimitive(sale.createdAt))
    val names = d.products.associate { it.id to it.name }
    put("items", JsonArray(d.saleItems.filter { it.saleId == sale.id }.take(200).map { x ->
      buildJsonObject {
        put("productId", JsonPrimitive(x.productId))
        put("name", JsonPrimitive((names[x.productId] ?: x.name).take(120)))
        put("qtyMilli", JsonPrimitive(Qty.fromLegacy(x.quantity).milli))
        put("unitPriceMinor", JsonPrimitive(minor(x.unitPrice)))
        put("totalMinor", JsonPrimitive(minor(x.totalPrice)))
      }
    }))
    if (device.isNotBlank()) put("device_id", JsonPrimitive(device.take(64)))
  }
}
