package ir.vil3ntec.tohid.core.push

/**
 *  پوشِ گوشی (FCM ِ اندروید؛ APNs ِ آیفون بعداً) — منطقِ خالص، بی اندروید و بی شبکه (تصمیمِ O-01).
 *
 *  سرور: `server/src/routes/shop-push.js` (`/api/shop/push/config|devices`) و `server/src/lib/order-notify.js` (دادهٔ پیام).
 *  برنامه: `data/push/DevicePush.kt` (همه‌جا) + `data/push/FcmPushAndroid.kt` (فقط اندروید، Firebase).
 *
 *  ⛔ پیش‌فرض خاموش: تا سرور `enabled` نگوید (پرچمِ `shop.device_push`) هیچ توکنی ساخته یا فرستاده نمی‌شود.
 *  ⛔ دادهٔ پیام منبعِ داده نیست: فقط «کدام سفارش را باز کن» (شناسهٔ مات)؛ جزئیات همیشه از سرور با دسترسیِ همان لحظه.
 */
object PushPolicy {

  /** حالِ این دستگاه، برای «تنظیمات» ← «سفارشِ آنلاین». ترتیب همان ترتیبِ زنجیره است (نخستین مانع گفته می‌شود). */
  enum class State {
    /** این سکو پوشِ سرور ندارد (کامپیوتر؛ آیفون تا حسابِ اپل) */
    UNSUPPORTED,
    /** این ساخت بی `google-services.json` است ⇒ Firebase اصلاً راه نمی‌افتد */
    NOT_BUNDLED,
    SIGNED_OUT,
    /** پرچمِ `shop.device_push` ِ این دکان خاموش است */
    FLAG_OFF,
    /** گوشی توکن نداد (بی Google Play Services، یا شبکه) */
    NO_TOKEN,
    /** ثبت شد ولی سرور کلیدِ Firebase یا کلیدِ «پوش روشن» ندارد */
    SERVER_OFF,
    ACTIVE,
    /** هنوز معلوم نیست (بی نت، خطای گذرا) */
    UNKNOWN,
  }

  /** پاسخِ `GET /api/shop/push/config` */
  data class ServerConfig(
    val enabled: Boolean,
    val serverReady: Boolean,
    val registered: Boolean,
    val orderAlerts: Boolean,
  )

  /** هر چند وقت یک بار حتی بی تغییر دوباره ثبت شود (سرور توکنِ کهنه را کنار می‌گذارد؛ این یعنی «هنوز زنده‌ام») */
  const val RESEND_MS: Long = 24L * 60 * 60 * 1000
  /** «روشن است؟» حداکثر هر چند وقت پرسیده شود، جز وقتی لازم است (ورود، توکنِ تازه، باز کردنِ تنظیمات) */
  const val CONFIG_MS: Long = 10L * 60 * 1000

  /** نشانِ «این توکن برای این کس و این دکان فرستاده شد» — کسِ دیگر یا دکانِ دیگر ⇒ ثبتِ دوباره */
  fun sentKey(token: String, user: String, shop: String): String = "$token|$user|$shop"

  /**
   *  توکن باید (دوباره) به سرور برود؟ سرور نمی‌شناسدش (خروج، برداشتنِ عضو، پاسخِ UNREGISTERED…)، توکن یا کس یا
   *  دکان عوض شده، یا یک روز گذشته. ساعتِ گوشی که عقب رفته ⇒ همان «گذشته» (دوباره فرستادن بی‌خطر است).
   */
  fun needsRegister(serverRegistered: Boolean, lastKey: String?, key: String, lastAt: Long, now: Long): Boolean =
    !serverRegistered || lastKey != key || now - lastAt !in 0 until RESEND_MS

  /** حالِ پایانی پس از یک دورِ موفق */
  fun stateOf(cfg: ServerConfig, hasToken: Boolean): State = when {
    !cfg.enabled -> State.FLAG_OFF
    !hasToken -> State.NO_TOKEN
    !cfg.serverReady -> State.SERVER_OFF
    else -> State.ACTIVE
  }

  /** پیامِ پوشی که لمسش سفارشی را باز می‌کند */
  data class Open(val orderId: String, val notifId: String?)

  private val ID = Regex("^[A-Za-z0-9_-]{1,64}$")

  /**
   *  دادهٔ پیام (`data` ِ FCM؛ در اندروید همان extras ِ intent ِ لمس) ⇐ کدام سفارش. فقط نوع‌های سفارش
   *  (`order_*`) و فقط شناسهٔ معتبر؛ `notifId` فقط برای `order_notify` (رسیدِ «باز شد» ِ `/shop/order-notify/:id/ack`).
   */
  fun openOf(data: Map<String, String?>): Open? {
    val type = data["type"].orEmpty()
    if (!type.startsWith("order_")) return null
    val orderId = data["orderId"]?.takeIf { ID.matches(it) } ?: return null
    val notifId = if (type == "order_notify") data["notifId"]?.takeIf { ID.matches(it) } else null
    return Open(orderId, notifId)
  }

  /** کانالِ اعلانِ اندروید برای هر کانالِ سرور (`message.channel`)؛ ناشناخته ⇒ «پشتیبانی» (همان پیش‌فرضِ سرور) */
  fun channelOf(serverChannel: String?): String = when (serverChannel) {
    "orders", "events", "notice", "alerts", "support" -> serverChannel
    else -> "support"
  }
}
