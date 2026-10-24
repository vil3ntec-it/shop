package ir.vil3ntec.tohid.data

import android.content.Context
import android.graphics.BitmapFactory
import ir.vil3ntec.tohid.core.hw.LogoDither
import ir.vil3ntec.tohid.core.hw.MonoImage
import ir.vil3ntec.tohid.data.repo.Backend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File

/**
 *  سربرگ و لوگوی فاکتورِ دکان (فاکتورِ نسخهٔ ۲) — یک منبع روی سرور
 *  (`/api/shop/receipt-profile`)، نگه‌داشته روی دستگاه برای چاپِ بی اینترنت.
 *
 *  ── رابط برای صفحه‌ها (V3 G9 «فاکتور و چاپ») ──────────────────────────────
 *    [profile]             حالتِ فعلی (از حافظهٔ دستگاه، بی شبکه)
 *    [refresh]/[kick]      گرفتن از سرور — اتصالِ سوکتِ زنده (`hello`) و پس از هر ویرایش
 *    [save]                نام، نشانی، تلفن، متنِ پایین، qr، حالتِ لوگو، رقم و نماد
 *    [setLogo]/[removeLogo] لوگو (PNG/JPG/WebP، ≤ ۲۰۰ KB، ≤ ۱۰۲۴ px — آماده‌سازی با صفحه)
 *    [logoMono]            لوگوی یک‌بیتیِ این کاغذ برای چاپ و پیش‌نمایش (`LogoDither`)
 *  نوشتن فقط با `settings.write` (صاحب و مدیر)؛ سرور خودش می‌سنجد.
 *
 *  ⛔ چاپ هرگز منتظرِ شبکه نمی‌ماند: فقط از همین نسخهٔ نگه‌داشته می‌خواند.
 *  داده به حسابِ دکان بسته است (`LedgerOwner.currentShop`)؛ دکانِ دیگر سربرگِ خودش را دارد.
 */
object ReceiptProfileStore {

  data class Profile(
    val name: String = "",
    val address: String = "",
    val phone: String = "",
    val footer: String = "",
    val qr: Boolean = true,
    val logoMode: LogoDither.Mode = LogoDither.Mode.AUTO,
    val digits: String = "latin",
    val symbol: String = "word",
    val logoHash: String? = null,
    /** برچسبِ چرخشِ EXIF ِ لوگو (۱..۸)؛ رمزگشای سکو خودش اعمالش نمی‌کند */
    val logoOrientation: Int = 1,
    val rev: Long = 0,
  )

  /** بدنهٔ ویرایش — فقط فیلدهای غیرِ null فرستاده می‌شوند */
  data class Patch(
    val name: String? = null,
    val address: String? = null,
    val phone: String? = null,
    val footer: String? = null,
    val qr: Boolean? = null,
    val logoMode: LogoDither.Mode? = null,
  )

  private const val PREFS = "tohid-receipt"
  private const val ROOT = "/shop/receipt-profile"
  private const val LOGO_DIR = "receipt-logo"

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val lock = Mutex()
  private val state = MutableStateFlow(Profile())
  private var loadedFor: String? = null
  private val monoCache = HashMap<String, MonoImage>()

  /** حالتِ فعلیِ دکانِ واردشده — برای صفحه‌ها */
  fun profile(context: Context): StateFlow<Profile> {
    ensureLoaded(context)
    return state.asStateFlow()
  }

  fun current(context: Context): Profile {
    ensureLoaded(context)
    return state.value
  }

  /** گرفتن از سرور و نگه داشتن؛ خطا ⇒ همان نسخهٔ قبلی می‌ماند. برمی‌گرداند: پیامِ خطا یا null */
  suspend fun refresh(context: Context): String? = withContext(Dispatchers.IO) {
    lock.withLock {
      runCatching { adopt(context, Backend.api(context).get(ROOT)) }.exceptionOrNull()?.let { message(it) }
    }
  }

  /** همان [refresh]، بی انتظار — اتصالِ سوکتِ زنده (`hello`) */
  fun kick(context: Context) {
    val app = context.applicationContext
    scope.launch { refresh(app) }
  }

  suspend fun save(context: Context, patch: Patch): String? = withContext(Dispatchers.IO) {
    val body = buildJsonObject {
      patch.name?.let { put("name", it) }
      patch.address?.let { put("address", it) }
      patch.phone?.let { put("phone", it) }
      patch.footer?.let { put("footer", it) }
      patch.qr?.let { put("qr", it) }
      patch.logoMode?.let { put("logoMode", it.key) }
    }
    lock.withLock { runCatching { adopt(context, Backend.api(context).put(ROOT, body)) }.exceptionOrNull()?.let { message(it) } }
  }

  suspend fun setLogo(context: Context, bytes: ByteArray, contentType: String): String? = withContext(Dispatchers.IO) {
    lock.withLock {
      runCatching { adopt(context, Backend.api(context).putBytes("$ROOT/logo", bytes, contentType)) }
        .exceptionOrNull()?.let { message(it) }
    }
  }

  suspend fun removeLogo(context: Context): String? = withContext(Dispatchers.IO) {
    lock.withLock { runCatching { adopt(context, Backend.api(context).delete("$ROOT/logo")) }.exceptionOrNull()?.let { message(it) } }
  }

  /**
   *  لوگوی یک‌بیتیِ کاغذِ [paperDots] — فقط از فایلِ روی دستگاه. null ⇒ لوگو ندارد یا هنوز
   *  نرسیده. نتیجه در حافظه می‌ماند تا هر چاپ دوباره رمزگشایی نکند.
   */
  fun logoMono(context: Context, paperDots: Int): MonoImage? {
    val p = current(context)
    val hash = p.logoHash ?: return null
    val key = "$hash:$paperDots:${p.logoMode.key}"
    synchronized(monoCache) { monoCache[key]?.let { return it } }
    val file = logoFile(context, hash)
    if (!file.isFile) return null
    val bytes = file.readBytes()
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val px = IntArray(bmp.width * bmp.height)
    bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    val o = LogoDither.orient(px, bmp.width, bmp.height, p.logoOrientation)
    val mono = LogoDither.toMono(o.argb, o.width, o.height, paperDots, p.logoMode)
    bmp.recycle()
    synchronized(monoCache) { monoCache[key] = mono }
    return mono
  }

  // ── درون ──────────────────────────────────────────────────────────────

  private fun shopKey(context: Context) = LedgerOwner.currentShop(context).ifBlank { "-" }

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  private fun ensureLoaded(context: Context) {
    val shop = shopKey(context)
    if (loadedFor == shop) return
    val raw = prefs(context).getString("profile:$shop", null)
    state.value = raw?.let { runCatching { parse(Json.parseToJsonElement(it).jsonObject) }.getOrNull() } ?: Profile()
    loadedFor = shop
  }

  /** پاسخِ سرور ⇒ نگه داشتن، و گرفتنِ لوگوی تازه اگر hash عوض شده */
  private suspend fun adopt(context: Context, body: JsonObject) {
    val next = parse(body)
    val shop = shopKey(context)
    next.logoHash?.let { hash ->
      val file = logoFile(context, hash)
      if (!file.isFile) {
        val bytes = Backend.api(context).getBytes("$ROOT/logo")
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(file)) { tmp.delete(); error("لوگو ذخیره نشد") }
      }
    }
    logoDir(context).listFiles()?.forEach { if (it.name != next.logoHash) it.delete() }
    prefs(context).edit().putString("profile:$shop", serialize(body)).apply()
    loadedFor = shop
    state.value = next
  }

  private fun parse(body: JsonObject): Profile {
    val p = (body["profile"] as? JsonObject) ?: JsonObject(emptyMap())
    fun s(k: String) = (p[k] as? JsonElement)?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }.orEmpty()
    val logo = p["logo"] as? JsonObject
    return Profile(
      name = s("name"), address = s("address"), phone = s("phone"), footer = s("footer"),
      qr = runCatching { p["qr"]?.jsonPrimitive?.booleanOrNull }.getOrNull() ?: true,
      logoMode = LogoDither.Mode.of(s("logoMode")),
      digits = s("digits").ifBlank { "latin" },
      symbol = s("symbol").ifBlank { "word" },
      logoHash = logo?.get("hash")?.jsonPrimitive?.contentOrNull?.takeIf { HASH.matches(it) },
      logoOrientation = logo?.get("orientation")?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.takeIf { it in 1..8 } ?: 1,
      rev = runCatching { body["rev"]?.jsonPrimitive?.longOrNull }.getOrNull() ?: 0,
    )
  }

  private fun serialize(body: JsonObject): String = Json.encodeToString(JsonObject.serializer(), body)

  private fun logoDir(context: Context) = File(context.filesDir, "$LOGO_DIR/${shopKey(context)}")

  private fun logoFile(context: Context, hash: String) = File(logoDir(context), hash)

  private fun message(t: Throwable): String = t.message ?: "سربرگِ فاکتور به‌روز نشد"

  private val HASH = Regex("^[0-9a-f]{64}$")
}
