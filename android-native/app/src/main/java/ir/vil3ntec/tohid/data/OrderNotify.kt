package ir.vil3ntec.tohid.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.orders.AckState
import ir.vil3ntec.tohid.core.orders.OrderNotice
import ir.vil3ntec.tohid.core.orders.OrderNotifyAcks
import ir.vil3ntec.tohid.core.push.PushPolicy
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.OrderNotifyRepository

/**
 *  اعلانِ سفارشِ تازه و رسیدِ «باز شد» — M6ِ قراردادِ دکان ⇐ بازار (v3 §۳۰).
 *
 *  ── چه چیزی نبود ───────────────────────────────────────────────────
 *  سرور اعلانِ هر سفارشِ مشتری را در `order_notify_outbox` می‌نویسد و تا کسی
 *  «باز شد» نزند یادآوری می‌فرستد، ولی این برنامه هیچ‌وقت `/order-notify` را
 *  صدا نمی‌زد ⇒ اعلان فقط با تغییرِ وضعیتِ سفارش (`ack_source = 'order_status'`)
 *  بسته می‌شد. با برنامهٔ باز اعلان را خودِ برنامه از `GET /pending` می‌سازد ([OrdersLive])؛
 *  با برنامهٔ بسته پوشِ Firebase (O-01، `data/push`) — هر دو با همان `tag` ِ سفارش، پس یکی جای دیگری می‌نشیند.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  ⛔ پیش‌فرض خاموش: تا سرور (`/config`، پرچمِ `shop.order_notify`) نگوید `enabled`،
 *  هیچ اعلانی ساخته نمی‌شود.
 *  ⛔ لمسِ اعلان ⇒ رسید همان لحظه در صفِ روی دستگاه ([OrderNotifyAcks]، آفلاین هم) و
 *  سفارش تازه از سرور باز می‌شود؛ صف با هر دورِ [tick] خالی می‌شود.
 *  ⛔ متنِ اعلان فقط شمارهٔ سفارش است — هیچ دادهٔ مشتری (§۳۰.۱ بندِ ۴).
 */
object OrderNotify {

  const val EXTRA_NOTIF = "ir.vil3ntec.tohid.order_notif"
  const val EXTRA_ORDER = "ir.vil3ntec.tohid.order_id"

  private const val PREFS = "order-notify"
  private const val KEY = "acks"
  /** همان کانالِ پوشِ سرور (`order-notify.CHANNEL`)، تا اعلانِ برنامهٔ باز و بسته یک تنظیم داشته باشند */
  const val CHANNEL = "orders"
  /** کانالِ پیش از پوش — یک بار پاک می‌شود تا در تنظیماتِ گوشی دو «سفارشِ تازه» نماند */
  private const val OLD_CHANNEL = "tohid-orders"
  /** نشانِ پیامِ Firebase در extras ِ لمس (اعلانی که خودِ اندروید با برنامهٔ بسته ساخت) */
  private const val PUSH_MESSAGE_ID = "google.message_id"
  private const val NOTE_BASE = 4400
  /** هر چند وقت یک بار «روشن است؟» دوباره پرسیده شود */
  private const val CONFIG_MS = 10 * 60_000L

  private val lock = Any()
  @Volatile private var enabled = false
  @Volatile private var checkedAt = 0L
  @Volatile private var repo: OrderNotifyRepository? = null

  private fun repository(context: Context): OrderNotifyRepository =
    repo ?: synchronized(this) { repo ?: OrderNotifyRepository(Backend.api(context)).also { repo = it } }

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  private fun load(context: Context): AckState = OrderNotifyAcks.decode(prefs(context).getString(KEY, null))

  private fun update(context: Context, fn: (AckState) -> AckState): AckState = synchronized(lock) {
    val cur = load(context)
    val next = fn(cur)
    if (next != cur) prefs(context).edit().putString(KEY, OrderNotifyAcks.encode(next)).apply()
    next
  }

  /** برای صفحه/آزمون: رسیدهای هنوز نرفته */
  fun pendingAcks(context: Context): Int = load(context).pending.size

  /**
   *  اگر [intent] از لمسِ اعلانِ سفارش است: رسید در صف و شناسهٔ سفارش برمی‌گردد
   *  (و نشانه‌ها از intent پاک می‌شوند تا چرخشِ صفحه دوباره نزند)؛ وگرنه null.
   */
  fun fromIntent(context: Context, intent: Intent?, now: Long = System.currentTimeMillis()): String? {
    if (intent == null) return null
    //  پوشی که خودِ اندروید نشان داد (برنامه بسته بود): دادهٔ پیام همان extras است — فقط نوعِ سفارش و شناسهٔ معتبر
    val push = if (intent.getStringExtra(PUSH_MESSAGE_ID) == null) null else PushPolicy.openOf(
      mapOf("type" to intent.getStringExtra("type"), "orderId" to intent.getStringExtra("orderId"), "notifId" to intent.getStringExtra("notifId")),
    )
    val notifId = intent.getStringExtra(EXTRA_NOTIF) ?: push?.notifId
    val orderId = (intent.getStringExtra(EXTRA_ORDER) ?: push?.orderId).orEmpty()
    intent.removeExtra(EXTRA_NOTIF)
    intent.removeExtra(EXTRA_ORDER)
    if (push != null) listOf(PUSH_MESSAGE_ID, "type", "orderId", "notifId").forEach { intent.removeExtra(it) }
    //  هشدارِ دیگرِ سفارش (شروعِ زمان‌بندی، ردِ خودکار…) شناسهٔ اعلان ندارد: فقط باز کردن، بی رسید
    if (notifId != null && OrderNotifyAcks.validId(notifId)) update(context) { OrderNotifyAcks.tap(it, notifId, orderId, now) }
    return orderId.takeIf { OrderNotifyAcks.validId(it) }
  }

  /** پوشِ همین اعلان همین حالا نشان داده شد ⇒ دورِ بعدیِ `/pending` دوباره نشانش نمی‌دهد */
  fun markShown(context: Context, notifId: String) {
    if (!OrderNotifyAcks.validId(notifId)) return
    update(context) { OrderNotifyAcks.fresh(it, listOf(OrderNotice(notifId, notifId, 0))).first }
  }

  /** کانالِ «سفارش‌های آنلاین» (اهمیتِ بالا) — اعلانِ برنامهٔ باز و پوشِ برنامهٔ بسته هر دو همین */
  fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val nm = context.getSystemService(NotificationManager::class.java) ?: return
    nm.createNotificationChannel(
      NotificationChannel(CHANNEL, ir.vil3ntec.tohid.core.i18n.StrPush.channelOrders, NotificationManager.IMPORTANCE_HIGH)
        .apply { description = "سفارشِ تازهٔ مشتری از ویترین" }
    )
    runCatching { nm.deleteNotificationChannel(OLD_CHANNEL) }
  }

  /** همان `tag` ِ پوشِ سرور (`order-<شناسه>`)، تا اعلانِ برنامهٔ باز و بسته برای یک سفارش یکی باشد */
  fun tagOf(orderId: String): String = "order-$orderId"

  /** یک دور: رسیدهای در صف را بفرست؛ اگر روشن است، اعلان‌های تازه را نشان بده. هرگز خطا بیرون نمی‌دهد. */
  suspend fun tick(context: Context) {
    if (!Backend.isReady(context)) return
    flush(context)
    val now = System.currentTimeMillis()
    if (now - checkedAt > CONFIG_MS) {
      repository(context).enabled().onSuccess { enabled = it; checkedAt = now }
    }
    if (enabled) surface(context)
  }

  /** رسیدهای سررسیده — هر کدام جدا؛ ۲xx/۴۰۴ بیرون، بقیه با فاصلهٔ فزاینده دوباره. */
  suspend fun flush(context: Context) {
    val now = System.currentTimeMillis()
    val due = OrderNotifyAcks.due(update(context) { OrderNotifyAcks.expire(it, now) }, now)
    for (a in due) {
      val outcome = when (val r = repository(context).ack(a.notifId)) {
        is ApiResult.Success -> OrderNotifyAcks.Outcome.ACKED
        is ApiResult.Failure -> OrderNotifyAcks.outcomeOf(r.error.status, r.error.code)
      }
      update(context) { OrderNotifyAcks.applyResult(it, a.notifId, outcome, System.currentTimeMillis()) }
    }
  }

  private suspend fun surface(context: Context) {
    val list = repository(context).pending().valueOrNull() ?: return
    var fresh: List<OrderNotice> = emptyList()
    update(context) { s -> OrderNotifyAcks.fresh(s, list).also { fresh = it.second }.first }
    fresh.forEach { show(context, it) }
  }

  /** اجازهٔ اعلانِ همین برنامه (اندروید ۱۳ به بعد) — تنظیماتِ «سفارشِ آنلاین» هم همین را می‌گوید */
  fun allowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
      PackageManager.PERMISSION_GRANTED

  private fun show(context: Context, n: OrderNotice) {
    if (!allowed(context)) return
    ensureChannel(context)
    val noteId = NOTE_BASE + (n.id.hashCode() and 0xFFFF)
    val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
      putExtra(EXTRA_NOTIF, n.id)
      putExtra(EXTRA_ORDER, n.orderId)
    }
    val pending = open?.let {
      PendingIntent.getActivity(context, noteId, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
    val title = if (n.orderNo > 0) "سفارشِ تازه #${n.orderNo}" else "سفارشِ تازه"
    val note = NotificationCompat.Builder(context, CHANNEL)
      .setSmallIcon(android.R.drawable.ic_dialog_info)
      .setContentTitle(title)
      .setContentText("برای دیدن و پذیرفتن لمس کنید")
      .setAutoCancel(true)
      .setOnlyAlertOnce(true)
      .apply { pending?.let { setContentIntent(it) } }
      .build()
    runCatching { NotificationManagerCompat.from(context).notify(tagOf(n.orderId), NOTE_BASE, note) }
  }
}
