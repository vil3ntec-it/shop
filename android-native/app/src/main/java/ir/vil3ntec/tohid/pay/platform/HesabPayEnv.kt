package ir.vil3ntec.tohid.pay.platform

import android.content.Context
import ir.vil3ntec.tohid.core.gateway.GatewayReply
import ir.vil3ntec.tohid.core.gateway.GatewayTransport
import ir.vil3ntec.tohid.core.gateway.HesabPayAccountClient
import ir.vil3ntec.tohid.core.gateway.HesabPayClient
import ir.vil3ntec.tohid.core.gateway.PendingPayments
import ir.vil3ntec.tohid.core.gateway.PendingStore
import ir.vil3ntec.tohid.core.gateway.TillHesab
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
 *  - «حساب‌پی هست؟»: نت + `GET /shop/payments/config`، یک دقیقه در حافظه. هر خطا ⇐ نه. حساب‌پیِ **خودِ دکان**:
 *    درگاهِ خودکار و/یا شماره و QR ِ حالتِ ساده ([TillHesab])؛ «حساب‌پیِ دکان» ِ صاحب: [account].
 *
 *  منطق در `core/gateway` است؛ این‌جا فقط سیم‌کشی.
 */
class HesabPayEnv private constructor(private val app: Context) {

  val client: HesabPayClient = HesabPayClient(ApiTransport(Backend.api(app)))

  /** «حساب‌پیِ دکان» (فقط صاحب؛ رمز را سرور می‌سنجد) */
  val account: HesabPayAccountClient = HesabPayAccountClient(ApiTransport(Backend.api(app)))

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

  /** نامِ دکان برای صفحهٔ رو به مشتری (همان `store_name` ِ نمایه) */
  fun storeName(): String = runCatching { app.getSharedPreferences("tohid", Context.MODE_PRIVATE).getString("store_name", "").orEmpty() }.getOrDefault("")

  @Volatile private var cached: Pair<Long, TillHesab>? = null

  /** حساب‌پیِ این دکان برای صندوق (درگاه و/یا حالتِ ساده)؛ بی‌نت ⇐ فوراً خاموش (روش پنهان) */
  suspend fun till(): TillHesab {
    if (!online()) return TillHesab.OFF
    val t = now()
    cached?.let { (at, on) -> if (t - at in 0 until TTL_MS) return on }
    val on = client.till()
    cached = t to on
    return on
  }

  /** درگاهِ خودکار روشن است و نت هست؟ */
  suspend fun available(): Boolean = till().gateway

  /** پس از تغییرِ «حساب‌پیِ دکان»: صندوقِ همین دستگاه زود بفهمد */
  fun forget() { cached = null }

  /** عکسِ QR ِ خوانده‌وسنجیده (هش + متن ⇒ بیت‌مپ)؛ فقط یکی، همان عکسِ فعالِ دکان */
  @Volatile private var picture: Triple<String, String, androidx.compose.ui.graphics.ImageBitmap>? = null

  /**
   *  عکسِ QR ِ دکان برای صندوق و نمایشگرِ مشتری (صاحب‌کار ۱۴۰۵/۰۸/۲۶): بایت‌ها از پوشهٔ همین دستگاه (`hesabpay-qr/<hash>`) یا
   *  `GET /shop/payments/qr-image/<hash>` (هشِ بایت‌ها سنجیده)، سپس **دوباره خوانده** و فقط اگر همان متنِ QR ِ حساب بود ⇒ بیت‌مپ.
   *  بی عکس، بی نت، عکسِ دیگر یا خوانده‌نشده ⇒ null (صندوق همان QR ِ کشیده از متن را نشان می‌دهد).
   */
  suspend fun qrPicture(simple: ir.vil3ntec.tohid.core.gateway.SimpleAccount?): androidx.compose.ui.graphics.ImageBitmap? {
    val hash = simple?.qrImage.orEmpty()
    val payload = simple?.qrPayload?.trim().orEmpty()
    if (hash.isEmpty() || payload.isEmpty()) return null
    picture?.let { (h, p, img) -> if (h == hash && p == payload) return img }
    val bytes = qrBytes(hash) ?: return null
    if (ShopQrPicture.read(bytes)?.trim() != payload) return null
    val img = ShopQrPicture.bitmap(bytes) ?: return null
    picture = Triple(hash, payload, img)
    return img
  }

  private suspend fun qrBytes(hash: String): ByteArray? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val dir = java.io.File(app.filesDir, QR_DIR)
    val file = java.io.File(dir, hash)
    val local = runCatching { file.takeIf { it.isFile }?.readBytes() }.getOrNull()
      ?.takeIf { ir.vil3ntec.tohid.core.audit.Sha256.hex(ir.vil3ntec.tohid.core.audit.Sha256.digest(it)) == hash }
    if (local != null) return@withContext local
    if (!online()) return@withContext null
    val got = client.qrImage(hash) ?: return@withContext null
    //  فقط عکسِ فعالِ امروز روی دیسک
    runCatching { dir.mkdirs(); dir.listFiles().orEmpty().filter { it.name != hash }.forEach { it.delete() }; file.writeBytes(got) }
    got
  }

  companion object {
    private const val PREFS = "tohid-hesabpay"
    private const val KEY = "pending"
    private const val TTL_MS = 60_000L
    private const val QR_DIR = "hesabpay-qr"

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

  override suspend fun put(path: String, body: Map<String, Any?>): GatewayReply =
    call { api.put(path, JsonObject(body.mapValues { (_, v) -> json(v) })) }

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
