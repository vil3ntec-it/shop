package ir.vil3ntec.tohid.data.push

import android.content.Context
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.push.PushPolicy
import ir.vil3ntec.tohid.data.LedgerOwner
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 *  سکوی پوشِ سرور — همان درزِ آیفون (APNs) برای بعد. اندروید: [FcmPushAndroid] (Firebase)؛ کامپیوتر: هیچ.
 *  آیفون امروز توکنِ APNs را خودش از `ios/IosPush.kt` به `/me/push` می‌دهد؛ روزی که حسابِ اپل آمد (O-02)، یک
 *  `PushPlatform` با `provider = "apns"` همین‌جا می‌نشیند و بقیه (پرچم، ثبت، حالِ تنظیمات) همین است.
 */
interface PushPlatform {
  /** `fcm` | `apns` — همان `provider` ِ `POST /api/shop/push/devices` */
  val provider: String
  /** `android` | `ios` */
  val platform: String
  /** این ساخت کلیدِ سرویس را دارد؟ (اندروید: `google-services.json` هنگامِ ساخت) */
  fun bundled(context: Context): Boolean
  /** روشن کردن و گرفتنِ توکن؛ نشد ⇒ null (هرگز خطا) */
  suspend fun token(context: Context): String?
  /** خاموش: توکنِ این دستگاه باطل و ساختِ خودکار بسته */
  suspend fun stop(context: Context)
}

/**
 *  پوشِ گوشی (تصمیمِ O-01، `docs/FIREBASE-SETUP-fa.md`) — «برنامه بسته هم باشد، سفارشِ تازه زنگ بزند».
 *
 *  ⛔ پیش‌فرض خاموش: پیش از هر کاری `GET /api/shop/push/config` (پرچمِ `shop.device_push`)؛ خاموش ⇒ Firebase روی
 *  گوشی راه نمی‌افتد و هیچ توکنی ساخته نمی‌شود (اگر پیش‌تر ساخته بود، باطل می‌شود).
 *  ⛔ توکن فقط برای همین کس، همین دکان و همین دستگاه (`POST /api/shop/push/devices`؛ سرور هر سه را از نشست می‌گیرد).
 *  خروج ⇒ سرور خودش توکنِ این دستگاه را کنار می‌گذارد (`/auth/logout`) و این‌جا توکنِ Firebase هم باطل می‌شود.
 *  ⛔ هرگز خطا بیرون نمی‌دهد و کارِ دیگری را نگه نمی‌دارد: هر دور در پس‌زمینه و حداکثر هر [PushPolicy.CONFIG_MS] یک بار.
 */
object DevicePush {

  /** اندروید در `MainActivity` و سرویسِ پیام می‌نشاند؛ کامپیوتر هرگز */
  @Volatile var platform: PushPlatform? = null

  private const val PREFS = "device-push"
  private const val KEY = "sent"
  private const val AT = "sentAt"
  private const val ROOT = "/shop/push"

  data class Status(val state: PushPolicy.State, val orderAlerts: Boolean = false)

  private val _status = MutableStateFlow(Status(PushPolicy.State.UNKNOWN))
  /** برای «تنظیمات» ← «سفارشِ آنلاین» */
  val status: StateFlow<Status> = _status

  private val mutex = Mutex()
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  @Volatile private var checkedAt = 0L

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  private fun set(s: Status): Status { _status.value = s; return s }

  /** بی انتظار — از ورود، سرویسِ پیام و چرخهٔ عمرِ صفحه */
  fun syncSoon(context: Context, force: Boolean = false) {
    val app = context.applicationContext
    scope.launch { runCatching { sync(app, force) } }
  }

  /** توکنِ تازهٔ Firebase (`onNewToken`) ⇒ همان دم به سرور */
  fun onNewToken(context: Context) = syncSoon(context, force = true)

  /** پس از خروج: توکنِ این دستگاه باطل؛ ورودِ بعدی (هر کس) توکنِ تازه می‌گیرد */
  fun signedOut(context: Context) {
    val app = context.applicationContext
    prefs(app).edit().remove(KEY).remove(AT).apply()
    checkedAt = 0L
    set(Status(PushPolicy.State.SIGNED_OUT))
    val p = platform ?: return
    scope.launch { runCatching { p.stop(app) } }
  }

  /** یک دور. `force` ⇒ بی درنگِ [PushPolicy.CONFIG_MS] (ورود، توکنِ تازه، باز کردنِ تنظیمات) */
  suspend fun sync(context: Context, force: Boolean = false): Status = mutex.withLock { syncLocked(context, force) }

  private suspend fun syncLocked(context: Context, force: Boolean): Status {
    val p = platform ?: return set(Status(PushPolicy.State.UNSUPPORTED))
    if (!p.bundled(context)) return set(Status(PushPolicy.State.NOT_BUNDLED))
    if (!Backend.isReady(context)) return set(Status(PushPolicy.State.SIGNED_OUT))
    val now = System.currentTimeMillis()
    if (!force && _status.value.state != PushPolicy.State.UNKNOWN && now - checkedAt in 0 until PushPolicy.CONFIG_MS) {
      return _status.value
    }
    val api = Backend.api(context)
    val cfg = when (val r = result { configOf(api.get("$ROOT/config")) }) {
      is ApiResult.Success -> r.value
      is ApiResult.Failure -> return set(_status.value.copy(state = PushPolicy.State.UNKNOWN))
    }
    checkedAt = now
    val prefs = prefs(context)
    if (!cfg.enabled) {
      //  پرچم خاموش شد ⇒ توکنِ پیشین هم باطل (سرور هم با `DELETE` کنارش می‌گذارد)
      if (prefs.getString(KEY, null) != null) {
        result { api.delete("$ROOT/devices") }
        runCatching { p.stop(context) }
        prefs.edit().remove(KEY).remove(AT).apply()
      }
      return set(Status(PushPolicy.State.FLAG_OFF, cfg.orderAlerts))
    }
    val token = runCatching { p.token(context) }.getOrNull()?.takeIf { it.isNotBlank() }
      ?: return set(Status(PushPolicy.State.NO_TOKEN, cfg.orderAlerts))
    val key = PushPolicy.sentKey(token, LedgerOwner.currentUser(context), ShopRole.shopId(context))
    if (PushPolicy.needsRegister(cfg.registered, prefs.getString(KEY, null), key, prefs.getLong(AT, 0L), now)) {
      val body = buildJsonObject {
        put("token", token)
        put("provider", p.provider)
        put("platform", p.platform)
      }
      when (result { api.post("$ROOT/devices", body) }) {
        is ApiResult.Success -> prefs.edit().putString(KEY, key).putLong(AT, now).apply()
        is ApiResult.Failure -> return set(Status(PushPolicy.State.UNKNOWN, cfg.orderAlerts))
      }
    }
    return set(Status(PushPolicy.stateOf(cfg, hasToken = true), cfg.orderAlerts))
  }

  private fun bool(o: JsonObject?, key: String): Boolean = (o?.get(key) as? JsonPrimitive)?.booleanOrNull == true

  private fun configOf(o: JsonObject) = PushPolicy.ServerConfig(
    enabled = bool(o, "enabled"),
    serverReady = bool(o["server"] as? JsonObject, "ready"),
    registered = bool(o["device"] as? JsonObject, "registered"),
    orderAlerts = bool(o, "orderAlerts"),
  )
}
