package ir.vil3ntec.tohid.desktop

import android.app.DesktopNotify
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuScope
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import ir.vil3ntec.tohid.core.i18n.StrLedger
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.i18n.StrOrderOps
import ir.vil3ntec.tohid.core.i18n.StrOrdersHub
import ir.vil3ntec.tohid.core.i18n.StrShift
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.orders.OrderNotice
import ir.vil3ntec.tohid.core.orders.OrderNotifyAcks
import ir.vil3ntec.tohid.core.orders.OrderRing
import ir.vil3ntec.tohid.data.OrderNotify
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.OrderNotifyRepository
import ir.vil3ntec.tohid.scan.ScanFeedback
import ir.vil3ntec.tohid.ui.AppNav
import ir.vil3ntec.tohid.ui.OrdersLive
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 *  زنگِ سفارشِ تازه در سینی — NOTIF-06 (§۳۰.۱ بندِ ۳: «Windows: اعلانِ داخلِ برنامه و اعلانِ سیستم‌عامل هنگامی که
 *  برنامه/عاملِ tray فعال است»؛ §۳۰.۵: «اعلان/صدا ایجاد و با تأییدِ کاربر متوقف شود»)، ORD-02 و S36 (≤ ۱۰ ثانیه، حتی در سینی).
 *
 *  ── چه چیزی نبود ───────────────────────────────────────────────────
 *  پرسشِ سفارش (`OrdersLive.Host`) به چرخهٔ پنجره بسته است: پنجرهٔ کوچک‌شده (`ON_STOP`) هیچ نمی‌پرسید، پرسش هر ۲۰
 *  ثانیه بود، اعلانِ سیستم یک بالنِ بی‌صدا بود که کلیکش فقط پنجره را جلو می‌آورد (سفارش باز نمی‌شد و رسیدِ «باز شد»
 *  نمی‌رفت، چون `PendingIntent` روی کامپیوتر هیچ است) و هیچ صدای تکراری تا «دیدم» نبود.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  این حلقه جدا از پنجره (پیدا، کوچک‌شده یا در سینی) هر [OrderRing.POLL_MS] همان `GET /shop/order-notify/pending` را
 *  می‌پرسد؛ قاعده‌ها همه در `core/orders/OrderRing` (آزمون: `OrderRingTest`) و این پرونده فقط راهِ برنامه است ([Port]).
 *  ⛔ پشتِ همان دو پرچمِ خاموش: بخشِ «سفارش‌ها» (`shop.orders`، `OrdersLive.enabled` — نقشِ بی `orders.view` ۴۰۳ ⇒ خاموش)
 *  **و** `shop.order_notify`؛ هر کدام خاموش یا بی حساب ⇒ هیچ پرسش، صدا و نشانی.
 *  ⛔ متنِ اعلان فقط شمارهٔ سفارش (§۳۰.۱ بندِ ۴)؛ کلیک سفارش را تازه از سرور باز می‌کند (`OrdersLive.openFromNotice`، بندِ ۵).
 *  ⛔ بالنِ سیستم یک بار برای هر اعلان روی این دستگاه، با همان حافظهٔ «نشان‌داده‌شده» ِ `OrderNotify` (بندِ ۶).
 *  ⛔ «دیدم»/کلیک ⇒ رسیدِ «باز شد» فقط برای نقشِ رسیدگی‌کننده (`orders.manage`، همان A16 ِ سرور)؛ بقیه فقط همین‌جا خاموش.
 *  ⛔ بی اینترنت: هیچ خطایی دیده نمی‌شود؛ سفارشِ شناخته‌شده تا «دیدم» زنگ می‌زند و چیزی تازه ساخته نمی‌شود.
 */
class OrderTrayAlarm(private val port: Port) {

  /** راهِ برنامه — آزمون (`OrderTrayAlarmTest`) جعلی‌اش را می‌دهد */
  interface Port {
    fun now(): Long
    /** هر دو پرچم روشن و حساب وارد ⇒ true؛ خاموش ⇒ false؛ ناپیدا (بی اینترنت) ⇒ null یعنی «همان پیشین» */
    suspend fun on(): Boolean?
    /** `/pending`؛ null ⇒ پرسش نشد */
    suspend fun pending(): List<OrderNotice>?
    /** از [arrived] آن‌هایی که روی این دستگاه هنوز بالن نشده‌اند (و حالا «نشان‌داده‌شده» ثبت می‌شوند) */
    fun firstShown(arrived: List<OrderNotice>): List<OrderNotice>
    fun announce(arrived: List<OrderNotice>, ringing: Int)
    fun chime()
    /** رسیدِ «باز شد» در صفِ روی دستگاه */
    fun ack(notices: List<OrderNotice>)
    suspend fun flush()
    /** null ⇒ فهرستِ سفارش‌ها */
    fun open(orderId: String?)
  }

  val state = mutableStateOf(OrderRing.State())
  private val lock = Any()
  @Volatile private var on = false

  /** یک دور؛ هرگز خطا بیرون نمی‌دهد */
  suspend fun round() {
    runCatching { port.on() }.getOrNull()?.let { on = it }
    if (!on) {
      synchronized(lock) { state.value = OrderRing.State() }
      return
    }
    runCatching { port.flush() }
    val list = runCatching { port.pending() }.getOrNull()
    val step = synchronized(lock) {
      val now = port.now()
      val s = state.value
      (if (list == null) OrderRing.onOffline(s, now) else OrderRing.onPending(s, list, now)).also { state.value = it.state }
    }
    if (step.arrived.isNotEmpty()) {
      val first = runCatching { port.firstShown(step.arrived) }.getOrDefault(step.arrived)
      if (first.isNotEmpty()) runCatching { port.announce(first, step.state.ringing.size) }
    }
    if (step.ring) runCatching { port.chime() }
  }

  /** «دیدم» یا کلیک روی اعلان/سینی: زنگ خاموش، رسید در صف و اگر [open] سفارش باز می‌شود. بی زنگ ⇒ false. */
  fun seen(open: Boolean): Boolean {
    val before = synchronized(lock) { state.value.also { state.value = OrderRing.seen(it, null) } }
    if (!before.active) return false
    runCatching { port.ack(before.ringing) }
    if (open) port.open(OrderRing.target(before))
    return true
  }

  companion object {
    /** حلقهٔ برنامه — یک بار در `main`، جدا از چرخهٔ پنجره */
    fun start(context: Context, scope: CoroutineScope): OrderTrayAlarm {
      val alarm = OrderTrayAlarm(AppPort(context))
      scope.launch {
        //  پس از نخستین ترکیبِ پنجره، تا راهِ بالنِ سینی (`DesktopNotify.sink`) گذاشته شده باشد
        delay(5_000)
        while (isActive) {
          alarm.round()
          delay(OrderRing.POLL_MS)
        }
      }
      return alarm
    }
  }
}

/** سرخطِ اعلان/سینی/نوار: یک سفارش ⇒ «سفارشِ تازه #۱۲»؛ چند تا ⇒ «سفارش‌های آنلاین، ۳ سفارشِ تازه» */
internal fun orderAlarmHeadline(ringing: List<OrderNotice>): String {
  val one = ringing.singleOrNull()
  return when {
    one == null -> StrOrdersHub.tileFresh(ringing.size)
    one.orderNo > 0 -> StrLedger.newOrderNo(one.orderNo)
    else -> StrLedger.newOrder
  }
}

/** راهنمای سینی وقتی زنگ می‌زند؛ وگرنه null (همان نامِ برنامه) */
fun OrderTrayAlarm.tooltip(): String? = state.value.takeIf { it.active }?.let { orderAlarmHeadline(it.ringing) }

/** نشانِ سینی تا «دیدم» چشمک می‌زند (ORD-02) — نیم‌ثانیه نشان با نقطهٔ سرخ، نیم‌ثانیه بی آن */
@Composable
fun rememberOrderAlarmIcon(base: Painter, alarm: OrderTrayAlarm): Painter {
  val active = alarm.state.value.active
  var lit by remember { mutableStateOf(false) }
  LaunchedEffect(active) {
    lit = active
    while (active) {
      delay(600)
      lit = !lit
    }
  }
  val dotted = remember(base) { DotPainter(base) }
  return if (active && lit) dotted else base
}

private class DotPainter(private val base: Painter) : Painter() {
  override val intrinsicSize: Size get() = base.intrinsicSize
  override fun DrawScope.onDraw() {
    with(base) { draw(size) }
    val r = size.minDimension * 0.24f
    drawCircle(Color(0xFFE53935), radius = r, center = Offset(size.width - r, r))
  }
}

/** ردیف‌های منوی سینی وقتی زنگ می‌زند: «سفارشِ تازه #۱۲» (باز کردن) و «دیدم» */
@Composable
fun MenuScope.OrderAlarmItems(alarm: OrderTrayAlarm, show: () -> Unit) {
  val s = alarm.state.value
  if (!s.active) return
  Item(orderAlarmHeadline(s.ringing), onClick = { if (alarm.seen(open = true)) show() })
  Item(StrOrderOps.startSeen, onClick = { alarm.seen(open = false) })
  Separator()
}

/** اعلانِ داخلِ پنجره (§۳۰.۱ بندِ ۳) بالای هر صفحه تا «دیدم» — روی کارِ صندوق می‌نشیند و فوکوس را نمی‌گیرد */
@Composable
fun OrderAlarmBanner(alarm: OrderTrayAlarm) {
  val s = alarm.state.value
  if (!s.active) return
  Popup(alignment = Alignment.TopCenter, properties = PopupProperties(focusable = false)) {
    Surface(
      color = Shop.colors.surfaceSolid,
      shape = RoundedCornerShape(12.dp),
      shadowElevation = 8.dp,
      modifier = Modifier.padding(12.dp),
    ) {
      ir.vil3ntec.tohid.ui.ProvideLang {
        Row(
          Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = Shop.colors.warning)
          Text(orderAlarmHeadline(s.ringing), color = Shop.colors.text, fontWeight = FontWeight.Bold)
          TextButton(onClick = { alarm.seen(open = false) }) { Text(StrOrderOps.startSeen) }
          Button(onClick = { alarm.seen(open = true) }) { Text(StrShift.openRegister) }
        }
      }
    }
  }
}

/** سه نتِ بالارونده — جدا از بوقِ اسکن تا بی نگاه کردن شناخته شود */
private fun chimeNow() {
  Thread {
    ScanFeedback.play(880.0, 130)
    ScanFeedback.play(1175.0, 130)
    ScanFeedback.play(1568.0, 220)
  }.apply { isDaemon = true; name = "order-chime" }.start()
}

/** راهِ واقعیِ برنامه */
private class AppPort(private val context: Context) : OrderTrayAlarm.Port {
  private val repo by lazy { OrderNotifyRepository(Backend.api(context)) }
  @Volatile private var configAt = 0L
  @Volatile private var notifyOn: Boolean? = null
  @Volatile private var notifyAt = 0L

  override fun now() = System.currentTimeMillis()

  override suspend fun on(): Boolean? {
    if (!Backend.isReady(context)) return false
    val t = now()
    //  پنجرهٔ کوچک‌شده `OrdersLive.Host` را نگه می‌دارد ⇒ «روشن است؟» را همین‌جا هم تازه نگه می‌داریم
    if (t - configAt >= CONFIG_MS) {
      configAt = t
      OrdersLive.refreshConfig(context)
    }
    if (!OrdersLive.enabled) return false
    if (notifyOn == null || t - notifyAt >= CONFIG_MS) {
      when (val r = repo.enabled()) {
        is ApiResult.Success -> { notifyOn = r.value; notifyAt = t }
        is ApiResult.Failure -> if (!r.error.retryable) { notifyOn = false; notifyAt = t }
      }
    }
    return notifyOn
  }

  override suspend fun pending(): List<OrderNotice>? = when (val r = repo.pending()) {
    is ApiResult.Success -> r.value
    //  پرچم همین حالا خاموش شد یا نقش اجازه ندارد ⇒ خاموش تا پرسشِ بعدیِ «روشن است؟»
    is ApiResult.Failure -> if (r.error.status == 403) { notifyOn = false; notifyAt = now(); emptyList() } else null
  }

  override fun firstShown(arrived: List<OrderNotice>): List<OrderNotice> {
    var out = emptyList<OrderNotice>()
    OrderNotify.update(context) { s -> OrderNotifyAcks.fresh(s, arrived).also { out = it.second }.first }
    return out
  }

  override fun announce(arrived: List<OrderNotice>, ringing: Int) {
    val title = if (arrived.size == 1) orderAlarmHeadline(arrived) else StrOrdersHub.tileFresh(ringing)
    DesktopNotify.show(title, StrOnline.newOrderArrived)
  }

  override fun chime() = chimeNow()

  override fun ack(notices: List<OrderNotice>) {
    if (OrdersLive.config.value?.permissions?.manage != true) return
    val t = now()
    OrderNotify.update(context) { s -> notices.fold(s) { acc, n -> OrderNotifyAcks.tap(acc, n.id, n.orderId, t) } }
  }

  override suspend fun flush() = OrderNotify.flush(context)

  override fun open(orderId: String?) {
    if (orderId != null) OrdersLive.openFromNotice(orderId) else AppNav.go("orders")
  }

  private companion object {
    /** همان «هر چند وقت یک بار روشن است؟» ِ `OrdersLive`/`OrderNotify` */
    const val CONFIG_MS = 10 * 60_000L
  }
}
