package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrderReviewRefDto
import ir.vil3ntec.tohid.core.model.StockHoldDto
import ir.vil3ntec.tohid.core.orders.OrderText
import ir.vil3ntec.tohid.core.orders.StockHold
import ir.vil3ntec.tohid.ui.theme.Shop

/*
 *  M5 — «سفارش در بررسی» در بخشِ «سفارش‌ها» (رزروِ نرمِ موجودی، `server/src/lib/stock-reserve.js`).
 *  نگاشت، متن و کارِ پیشنهادی در `core` (`StockHold`)؛ این‌جا فقط چیدن. هشدار با همان کادرِ
 *  `FulfillNotice` ِ `Orders.kt` (رنگ + نقطه + متن، هرگز رنگِ تنها) که از بیرون داده می‌شود.
 */

/** (عنوان، متن، دکمه، کار) ⇒ همان کادرِ هشدارِ `Orders.kt` */
internal typealias OrderNotice = @Composable (String, String, String?, () -> Unit) -> Unit

/** بالای فهرست: سفارش‌های «در بررسی» از `summary` (هر برگه‌ای که باز است) */
@Composable
internal fun StockReviewBanner(refs: List<OrderReviewRefDto>, onOpen: (String) -> Unit, notice: OrderNotice) {
  if (refs.isEmpty()) return
  Spacer(Modifier.height(8.dp))
  notice(
    "${refs.size} سفارش در بررسی — موجودیِ نگه‌داشته را فروشِ حضوری برد",
    refs.take(5).joinToString("، ") { "#${it.orderNo}" } + if (refs.size > 5) " …" else "",
    "بررسی",
  ) { onOpen(refs.first().id) }
}

/**
 *  جزئیاتِ سفارش: «در بررسی» ⇒ هشدار با قلم‌های کم و کارِ پیشنهادی (رد/لغو با دلیلِ «ناموجود» — همان گذارِ
 *  عادیِ سرور، فقط اگر همین کاربر دکمه‌اش را دارد، با پنجرهٔ تأیید)؛ بقیهٔ حالت‌ها یک خطِ آگاهی.
 *  وضعیتِ ناشناس (سرورِ تازه‌تر) ⇒ هیچ.
 */
@Composable
internal fun StockHoldNotice(
  o: OrderDto,
  hold: StockHoldDto,
  actions: List<String>,
  stamp: (Long) -> String,
  tag: @Composable (String, Boolean) -> Unit,
  notice: OrderNotice,
  onAct: (to: String, reason: String) -> Unit,
) {
  val phase = StockHold.phase(hold.status, hold.review) ?: return
  Spacer(Modifier.height(8.dp))
  if (phase == StockHold.Phase.REVIEW) {
    val lines = hold.lines.map { StockHold.Line(it.lineNo, it.status, it.short) }
    val names = StockHold.shortNames(lines, o.lines.associate { it.lineNo to it.name })
    val tip = StockHold.suggestion(o.status, actions)
    notice(
      StockHold.label(phase) + " — موجودیِ نگه‌داشته را فروشِ حضوری برد",
      StockHold.reviewBody(names),
      tip?.let { "${OrderText.action(it.to)} (${OrderText.reason(it.reason)})" },
    ) { tip?.let { onAct(it.to, it.reason) } }
    return
  }
  val until = hold.expiresAt?.takeIf { phase == StockHold.Phase.HELD }?.let { " تا ${stamp(it)}" } ?: ""
  val text = StockHold.label(phase) + until
  if (StockHold.isWarning(phase)) tag(text, true)
  else Text(text, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
}
