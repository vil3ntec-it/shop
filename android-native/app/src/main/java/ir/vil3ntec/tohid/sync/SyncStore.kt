package ir.vil3ntec.tohid.sync

import android.content.Context
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

  /*
   *  ایمیل، شماره و روزِ ساختنِ حساب.
   *
   *  ── چرا روی گوشی نوشته می‌شوند ────────────────────────────────────
   *  صفحهٔ پروفایل بدونِ اینترنت هم باید همان چیزی را نشان بدهد که با
   *  اینترنت: برنامه در دکانی کار می‌کند که ممکن است روزها آنتن نداشته
   *  باشد. اینها با هر ورود و هر بار باز شدنِ پروفایل از سرور تازه
   *  می‌شوند و همین‌جا می‌مانند.
   *  ──────────────────────────────────────────────────────────────────
   *
   *  با خروج پاک می‌شوند، مثل نام: ایمیلِ نفرِ قبلی نباید در پروفایلِ
   *  نفرِ بعدی دیده شود.
   */
  var accountEmail: String
    get() = prefs.getString(EMAIL, "") ?: ""
    set(v) = prefs.edit().putString(EMAIL, v).apply()

  var accountPhone: String
    get() = prefs.getString(PHONE, "") ?: ""
    set(v) = prefs.edit().putString(PHONE, v).apply()

  /** ساعتِ ساختنِ حساب — «مدت عضویت» از همین حساب می‌شود */
  var accountCreatedAt: Long
    get() = prefs.getLong(CREATED, 0)
    set(v) = prefs.edit().putLong(CREATED, v).apply()

  /**
   *  هرچه از حسابِ تازه‌وارد باید روی گوشی بماند — یک جا.
   *
   *  سه مسیرِ ورود (رمز، کد، کدِ شاگرد) هر کدام تا دیروز فقط نام را
   *  می‌نوشتند و بس. با اضافه شدنِ صفحهٔ پروفایل، ایمیل و شماره و روزِ
   *  ساختنِ حساب هم لازم شد؛ اگر هر مسیر خودش می‌نوشت، یکی‌شان
   *  فراموش می‌شد و کاربرِ آن راه، پروفایلِ نصفه می‌دید.
   *
   *  @param name نامی که نشان داده می‌شود — معمولاً همان نامِ سرور، ولی
   *    در ورودِ تازه با شماره، نامی است که همان لحظه تایپ شده.
   */
  fun rememberAccount(
    user: ir.vil3ntec.tohid.core.model.UserDto,
    name: String = user.name,
  ) {
    prefs.edit()
      .putString(NAME, name)
      .putString(EMAIL, user.email.orEmpty())
      .putString(PHONE, user.phone.orEmpty())
      .putLong(CREATED, user.createdAt)
      .apply()
  }

  /**
   *  شناسهٔ حسابی که الان وارد است.
   *
   *  مجوزِ اشتراک با همین سنجیده می‌شود: مجوز `sub` را از اول داشت ولی
   *  فقط دستگاه بررسی می‌شد، یعنی روی یک گوشیِ مشترک، اشتراکِ یک نفر
   *  برای نفرِ بعدی هم باز بود.
   */
  var accountId: String
    get() = prefs.getString(ACCOUNT_ID, "") ?: ""
    set(v) = prefs.edit().putString(ACCOUNT_ID, v).apply()

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

  /**
   *  خروج از حساب.
   *
   *  دفترِ دکان روی گوشی می‌ماند — کسی که خارج می‌شود باید بتواند آفلاین
   *  کارش را ببیند. جدا نگه داشتنِ حساب‌ها کارِ `LedgerOwner` است: دفتر
   *  به نامِ همین حساب سند خورده و اگر حسابِ دیگری وارد شود، همان‌جا
   *  بایگانی و جایگزین می‌شود.
   */
  fun signOut() {
    tokens.clear()
    prefs.edit()
      .remove(NAME).remove(EMAIL).remove(PHONE).remove(CREATED)
      .remove(ACCOUNT_ID)
      .remove(LICENSE).remove(SHADOW).remove(REV).remove(LAST_SYNC)
      .apply()
  }

  /**
   *  پاک کردنِ حافظهٔ همگام‌سازی، بدونِ دست زدن به توکن.
   *
   *  وقتی دفترِ روی میز عوض می‌شود، سایه و شمارهٔ آخرین تغییر دیگر به
   *  هیچ دردی نمی‌خورند: سایه عکسِ دفترِ حسابِ قبلی است و شمارهٔ تغییر
   *  مالِ دکانِ دیگری. نگه داشتنشان یعنی همان قاطی‌شدنی که قرار بود
   *  بسته شود.
   */
  fun forgetSyncState() {
    prefs.edit().remove(SHADOW).remove(REV).remove(LAST_SYNC).apply()
  }

  /**
   *  همه‌ی آنچه به حسابِ قبلی بسته بود.
   *
   *  کنارِ سایه و شماره‌ی تغییر، **مجوز** هم می‌رود: به دستگاه بسته است،
   *  نه به شخص، پس اگر بماند اشتراکِ نفرِ قبلی برای نفرِ تازه باز
   *  می‌ماند. دفعه‌ی بعدِ همگام‌سازی، مجوزِ خودِ این حساب می‌آید.
   */
  fun forgetAccountState() {
    prefs.edit()
      .remove(SHADOW).remove(REV).remove(LAST_SYNC)
      .remove(LICENSE).remove(NAME).remove(EMAIL).remove(PHONE).remove(CREATED)
      .apply()
  }

  private fun newDeviceUid(): String {
    val bytes = ByteArray(16)
    java.security.SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
  }

  private companion object {
    const val DEVICE = "device_uid"
    //  `server_url`، `access_token` و `refresh_token` دیگر اینجا نیستند:
    //  نشانیِ سرور در زمانِ ساخت داخلِ برنامه می‌نشیند و دو تای بعدی در
    //  `TokenStore`ِ رمزشده‌اند. مقدارِ به‌جامانده‌شان یک بار پاک می‌شود.
    const val NAME = "account_name"
    const val EMAIL = "account_email"
    const val PHONE = "account_phone"
    const val CREATED = "account_created"
    const val ACCOUNT_ID = "account_id"
    const val LOCK_ASKED = "lock_asked"
    const val LICENSE = "license"
    const val PUBLIC_KEY = "public_key"
    const val SHADOW = "shadow"
    const val REV = "rev"
    const val LAST_SYNC = "last_sync"
    const val CLOCK = "clock_seen"
  }
}
