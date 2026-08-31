package ir.vil3ntec.tohid.sync

import android.content.Context
import ir.vil3ntec.tohid.core.config.AppConfig
import ir.vil3ntec.tohid.core.net.TokenStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 *  چیزهایی که برای همگام‌سازی باید بین اجراها بماند.
 *
 *  توکن، سایه، شمارهٔ آخرین تغییرِ گرفته‌شده و شناسهٔ همین دستگاه.
 *  شناسهٔ دستگاه یک بار ساخته می‌شود و دیگر عوض نمی‌شود: مجوزِ اشتراک به
 *  همان بسته است، و عوض شدنش یعنی مجوز دیگر مالِ این گوشی نیست.
 */
class SyncStore(context: Context) {

  private val app = context.applicationContext
  private val prefs = app.getSharedPreferences("tohid-sync", Context.MODE_PRIVATE)
  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  val deviceUid: String
    get() = prefs.getString(DEVICE, null) ?: newDeviceUid().also {
      prefs.edit().putString(DEVICE, it).apply()
    }

  /**
   *  نشانیِ سرور.
   *
   *  دیگر اینجا نگه داشته نمی‌شود: `AppConfig` تنها جایی است که نشانی از
   *  آن خوانده می‌شود، و این فقط راهِ رسیدن به آن است تا کدِ موجود
   *  نشکند. اگر نسخه با دامنه ساخته شده باشد، نوشتن روی آن بی‌اثر است.
   */
  var serverUrl: String
    get() = AppConfig.baseUrl(app)
    set(v) { AppConfig.setBaseUrl(app, v) }

  /**
   *  توکن‌ها — رمزشده در `TokenStore`، نه اینجا.
   *
   *  تا دیروز همین‌جا در حافظهٔ ساده می‌نشستند. این دو ویژگی سرِ جایشان
   *  مانده‌اند تا کدی که از آن‌ها استفاده می‌کند نشکند، ولی مقدارِ واقعی
   *  از حافظهٔ رمزشده می‌آید. `TokenStore` هنگامِ اولین ساخت، توکنِ
   *  به‌جامانده از نسخهٔ قبل را خودش می‌آورد و جای قدیمی را پاک می‌کند —
   *  پس کسی که برنامه را به‌روز می‌کند بیرون نمی‌افتد.
   */
  private val tokens: TokenStore by lazy { TokenStore(app) }

  var accessToken: String?
    get() = tokens.accessToken
    set(v) { tokens.accessToken = v }

  var refreshToken: String?
    get() = tokens.refreshToken
    set(v) { tokens.refreshToken = v }

  /**
   *  یک بار پرسیدنِ رمزِ برنامه.
   *
   *  اگر کاربر «فعلاً نه» زد، دیگر سرِ راهش نمی‌ایستیم. هر وقت خواست،
   *  از تنظیمات می‌گذارد.
   */
  var lockDeclined: Boolean
    get() = prefs.getBoolean(LOCK_ASKED, false)
    set(v) = prefs.edit().putBoolean(LOCK_ASKED, v).apply()

  var accountName: String
    get() = prefs.getString(NAME, "") ?: ""
    set(v) = prefs.edit().putString(NAME, v).apply()

  var license: String?
    get() = prefs.getString(LICENSE, null)
    set(v) = prefs.edit().putString(LICENSE, v).apply()

  var publicKey: String?
    get() = prefs.getString(PUBLIC_KEY, null)
    set(v) = prefs.edit().putString(PUBLIC_KEY, v).apply()

  var revision: Long
    get() = prefs.getLong(REV, 0)
    set(v) = prefs.edit().putLong(REV, v).apply()

  /**
   *  بالاترین ساعتی که تا حالا دیده‌ایم.
   *
   *  ساعتِ گوشی دستِ کاربر است و عقب بردنش یک راهِ ساده برای تمام نشدنِ
   *  اشتراک بود. این عدد فقط جلو می‌رود؛ اگر ساعتِ گوشی از آن **عقب‌تر**
   *  بیفتد، معلوم است دست خورده.
   */
  var clockSeen: Long
    get() = prefs.getLong(CLOCK, 0)
    set(v) {
      if (v > prefs.getLong(CLOCK, 0)) prefs.edit().putLong(CLOCK, v).apply()
    }

  var lastSyncAt: Long
    get() = prefs.getLong(LAST_SYNC, 0)
    set(v) = prefs.edit().putLong(LAST_SYNC, v).apply()

  /** آخرین وضعیتی که با سرور یکی بوده */
  var shadow: SyncEngine.Shadow
    get() {
      val raw = prefs.getString(SHADOW, null) ?: return SyncEngine.Shadow()
      return runCatching {
        val tree = json.parseToJsonElement(raw) as JsonObject
        SyncEngine.Shadow(
          tree.mapValues { (_, value) ->
            (value as JsonObject).mapValues { (_, fp) -> (fp as JsonPrimitive).content }
          }
        )
      }.getOrDefault(SyncEngine.Shadow())
    }
    set(value) {
      val tree = buildJsonObject {
        value.entries.forEach { (collection, rows) ->
          put(collection, buildJsonObject { rows.forEach { (id, fp) -> put(id, JsonPrimitive(fp)) } })
        }
      }
      prefs.edit().putString(SHADOW, tree.toString()).apply()
    }

  /** خروج از حساب — داده‌های دکان دست‌نخورده می‌مانند */
  fun signOut() {
    tokens.clear()
    prefs.edit()
      .remove(NAME)
      .remove(LICENSE).remove(SHADOW).remove(REV).remove(LAST_SYNC)
      .apply()
  }

  private fun newDeviceUid(): String {
    val bytes = ByteArray(16)
    java.security.SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
  }

  private companion object {
    const val DEVICE = "device_uid"
    //  `server_url`، `access_token` و `refresh_token` دیگر اینجا نوشته
    //  نمی‌شوند: اولی در `AppConfig` است و دو تای بعدی در `TokenStore`ِ
    //  رمزشده. مقدارِ به‌جامانده‌شان یک بار خوانده و پاک می‌شود.
    const val NAME = "account_name"
    const val LOCK_ASKED = "lock_asked"
    const val LICENSE = "license"
    const val PUBLIC_KEY = "public_key"
    const val SHADOW = "shadow"
    const val REV = "rev"
    const val LAST_SYNC = "last_sync"
    const val CLOCK = "clock_seen"
  }
}
