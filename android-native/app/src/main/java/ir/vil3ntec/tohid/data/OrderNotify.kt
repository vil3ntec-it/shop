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
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.OrderNotifyRepository

/**
 *  اعلانِ سفارشِ تازه و رسیدِ «باز شد» — M6ِ قراردادِ دکان ⇐ بازار (v3 §۳۰).
 *
 *  ── چه چیزی نبود ───────────────────────────────────────────────────
 *  سرور اعلانِ هر سفارشِ مشتری را در `order_notify_outbox` می‌نویسد و تا کسی
 *  «باز شد» نزند یادآوری می‌فرستد، ولی این برنامه هیچ‌وقت `/order-notify` را
 *  صدا نمی‌زد ⇒ اعلان فقط با تغییرِ وضعیتِ سفارش (`ack_source = 'order_status'`)
 *  بسته می‌شد. FCM هنوز در برنامه نیست؛ پس اعلان را خودِ برنامه از
 *  `GET /pending` می‌سازد — فقط وقتی جلوی چشم است ([OrdersLive] همین را صدا می‌زند).
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
  private const val CHANNEL = "tohid-orders"
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
    val notifId = intent?.getStringExtra(EXTRA_NOTIF) ?: return null
    val orderId = intent.getStringExtra(EXTRA_ORDER).orEmpty()
    intent.removeExtra(EXTRA_NOTIF)
    intent.removeExtra(EXTRA_ORDER)
    if (!OrderNotifyAcks.validId(notifId)) return null
    update(context) { OrderNotifyAcks.tap(it, notifId, orderId, now) }
    return orderId.takeIf { OrderNotifyAcks.validId(it) }
  }

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

  private fun allowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
      PackageManager.PERMISSION_GRANTED

  private fun show(context: Context, n: OrderNotice) {
    if (!allowed(context)) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
        NotificationChannel(CHANNEL, "سفارشِ تازه", NotificationManager.IMPORTANCE_HIGH)
          .apply { description = "سفارشِ تازهٔ مشتری از ویترین" }
      )
    }
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
      .apply { pending?.let { setContentIntent(it) } }
      .build()
    runCatching { NotificationManagerCompat.from(context).notify(noteId, note) }
  }
}
