package ir.vil3ntec.tohid.pay.platform

import android.content.Context
import ir.vil3ntec.tohid.core.gateway.GatewayReply
import ir.vil3ntec.tohid.core.gateway.GatewayTransport
import ir.vil3ntec.tohid.core.gateway.HesabPayClient
import ir.vil3ntec.tohid.core.gateway.PendingPayments
import ir.vil3ntec.tohid.core.gateway.PendingStore
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.data.PermPolicy
import ir.vil3ntec.tohid.data.repo.Backend
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 *  مرزِ پلتفرمِ حساب‌پی (PAY-21) — اندروید و کامپیوتر هر دو همین را دارند.
 *
 *  - HTTP: همان `ApiClient` ِ برنامه (توکنِ کاربرِ دکان، تازه‌سازیِ نشست، پیشوندِ `/api/v1`).
 *  - دفترِ تلاش‌ها: `SharedPreferences` ِ جدای `tohid-hesabpay` (همگام نمی‌شود؛ فقط همین دستگاه).
 *  - «حساب‌پی هست؟»: نت + `GET /shop/payments/config`، یک دقیقه در حافظه. هر خطا ⇐ نه.
 *
 *  منطق در `core/gateway` است؛ این‌جا فقط سیم‌کشی.
 */
class HesabPayEnv private constructor(private val app: Context) {

  val client: HesabPayClient = HesabPayClient(ApiTransport(Backend.api(app)))

  val pending: PendingPayments = PendingPayments(
    object : PendingStore {
      private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      override fun read(): String? = prefs.getString(KEY, null)
      override fun write(value: String) { prefs.edit().putString(KEY, value).apply() }
    },
  ) { now() }

  fun now(): Long = System.currentTimeMillis()

  fun online(): Boolean = Backend.isOnline(app)

  /** وضعیتِ NFC ِ همین دستگاه (کامپیوتر: همیشه «ندارد») */
  fun nfc(): PayDevice.Nfc = PayDevice.nfc(app)

  fun openNfcSettings() = PayDevice.openNfcSettings(app)

  @Volatile private var cached: Pair<Long, Boolean>? = null

  /** حساب‌پی برای این دکان روشن است و نت هست؟ بی‌نت ⇐ فوراً نه (روش پنهان) */
  suspend fun available(): Boolean {
    if (!online()) return false
    val t = now()
    cached?.let { (at, on) -> if (t - at in 0 until TTL_MS) return on }
    val on = client.enabled()
    cached = t to on
    return on
  }

  companion object {
    private const val PREFS = "tohid-hesabpay"
    private const val KEY = "pending"
    private const val TTL_MS = 60_000L

    @Volatile private var one: HesabPayEnv? = null

    fun of(context: Context): HesabPayEnv =
      one ?: HesabPayEnv(context.applicationContext ?: context).also { one = it }
  }
}

/** `GatewayTransport` روی `ApiClient` — هر خطای API به پاسخِ بی‌پرتاب؛ لغوِ کوروتین دست‌نخورده */
private class ApiTransport(private val api: ApiClient) : GatewayTransport {

  override suspend fun get(path: String): GatewayReply = call { api.get(path) }

  override suspend fun post(path: String, body: Map<String, Any?>, idempotencyKey: String?): GatewayReply {
    val json = JsonObject(body.mapValues { (_, v) -> json(v) })
    return call { if (idempotencyKey != null) api.postKeyed(path, json, idempotencyKey) else api.post(path, json) }
  }

  private inline fun call(block: () -> JsonObject): GatewayReply = try {
    @Suppress("UNCHECKED_CAST")
    GatewayReply(200, PermPolicy.tree(block()) as? Map<String, Any?>)
  } catch (e: CancellationException) {
    throw e
  } catch (e: ApiFailure) {
    GatewayReply(e.status, null, e.code, e.userMessage)
  } catch (e: Exception) {
    GatewayReply(0, null, "network", e.message.orEmpty())
  }

  private fun json(v: Any?): JsonElement = when (v) {
    null -> JsonNull
    is String -> JsonPrimitive(v)
    is Long -> JsonPrimitive(v)
    is Int -> JsonPrimitive(v)
    is Boolean -> JsonPrimitive(v)
    is Double -> JsonPrimitive(v)
    else -> JsonPrimitive(v.toString())
  }
}
