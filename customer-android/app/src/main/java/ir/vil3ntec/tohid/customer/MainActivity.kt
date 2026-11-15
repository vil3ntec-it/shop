package ir.vil3ntec.tohid.customer

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import ir.vil3ntec.tohid.core.shopper.ChatPhoto
import ir.vil3ntec.tohid.core.shopper.GeoPin
import ir.vil3ntec.tohid.core.shopper.ShopperApi
import ir.vil3ntec.tohid.core.shopper.ShopperRoutedHttp
import ir.vil3ntec.tohid.core.shopper.ShopperServers
import ir.vil3ntec.tohid.core.shopper.ShopperText
import ir.vil3ntec.tohid.customer.ui.CustomerApp
import ir.vil3ntec.tohid.customer.ui.CustomerModel
import ir.vil3ntec.tohid.customer.ui.Locator
import ir.vil3ntec.tohid.customer.ui.Platform
import ir.vil3ntec.tohid.customer.ui.openLink
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 *  نقطهٔ ورودِ اندروید: فقط سیم‌کشیِ مرزهای سکو؛ همهٔ صفحه‌ها در `ui/` (بی android.*).
 *  مرحلهٔ ۲: اجازهٔ موقعیت فقط با زدنِ «موقعیتِ فعلیِ من»، اجازهٔ میکروفون فقط با زدنِ میکروفونِ گفت‌وگو (`VoiceAndroid`)،
 *  گزینشگرِ عکسِ سیستم (بی اجازهٔ حافظه)، «نمایش روی نقشه»
 *  با `geo:`، تکه‌های «انتخاب روی نقشه» (`MapTilesAndroid`، CHK-08)، و پیوندِ عمیق (`tohidshop://store/…`، `https://vill3n.top/store/…`، `<slug>.vill3n.top`) در
 *  `onCreate`/`onNewIntent`.
 */
class MainActivity : ComponentActivity() {
  private lateinit var model: CustomerModel
  private var locationDone: ((GeoPin?) -> Unit)? = null
  private var photoDone: ((ChatPhoto?) -> Unit)? = null
  //  پیامِ صوتی (CHAT-07): پیش از onCreate ساخته می‌شود (پرسشِ اجازهٔ میکروفون با registerForActivityResult)
  private val voice = VoiceAndroid(this)
  //  چند نشانیِ یک سرور (پرچمِ خاموشِ `tohid.multiServer`، `docs/SERVERS-fa.md` §۳) — خاموش ⇒ همیشه null
  private var routed: ShopperRoutedHttp? = null

  private val askLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
    val done = locationDone ?: return@registerForActivityResult
    locationDone = null
    if (granted.values.any { it }) LocationAndroid.current(this, done) else done(null)
  }

  private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    val done = photoDone ?: return@registerForActivityResult
    photoDone = null
    if (uri == null) {
      done(null)
      return@registerForActivityResult
    }
    lifecycleScope.launch { done(withContext(Dispatchers.Default) { PhotoAndroid.prepare(contentResolver, uri) }) }
  }

  private fun locate(done: (GeoPin?) -> Unit) {
    val perms = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    if (perms.any { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
      LocationAndroid.current(this, done)
    } else {
      locationDone = done
      askLocation.launch(perms)
    }
  }

  private fun openMap(geo: String) {
    try {
      startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(geo)))
    } catch (_: ActivityNotFoundException) {
      //  بی برنامهٔ نقشه ⇒ همان مختصات در مرورگر (OpenStreetMap)
      val p = geo.substringAfter("geo:").substringBefore('?').split(',')
      if (p.size == 2) runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/?mlat=${p[0]}&mlon=${p[1]}#map=18/${p[0]}/${p[1]}"))) }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val kv = StoreAndroid(this)
    val server = { kv.get(SERVER_KEY)?.takeIf { it.isNotBlank() } ?: BuildConfig.API_BASE }
    val http = HttpAndroid(server)
    //  پرچمِ خاموش ⇒ `routed` تهی، `base` همان `server` و فرستنده همان `http` ِ امروز؛ نشانیِ دستیِ «تنظیمات» هم همان، بی جابه‌جایی
    val routed = if (BuildConfig.MULTI_SERVER) {
      ShopperServers.routed(BuildConfig.API_BASE, kv, { kv.get(SERVER_KEY) }, http, System::currentTimeMillis) { e -> HttpAndroid.notSent(e) }
    } else null
    this.routed = routed
    val base: () -> String = if (routed != null) ({ routed.base() }) else server
    val api = ShopperApi(routed ?: http) { ShopperText.lang.code }
    val random = SecureRandom()
    model = CustomerModel(
      api, kv, lifecycleScope, Dispatchers.IO, System::currentTimeMillis,
      locator = Locator { done -> locate(done) },
      secureRandom = { n -> ByteArray(n).also { random.nextBytes(it) } },
      secrets = SecretsAndroid(this),
      voice = voice,
    )
    val platform = Platform(
      photo = { path, modifier -> ImagesAndroid.Photo(base().trimEnd('/') + path, modifier) },
      call = { phone -> startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) },
      server = server,
      setServer = { kv.put(SERVER_KEY, it.trim().takeIf { s -> s.isNotEmpty() }) },
      version = BuildConfig.VERSION_NAME,
      serverField = BuildConfig.SERVER_FIELD,
      openMap = { openMap(it) },
      canPickPhoto = true,
      pickPhoto = { done ->
        photoDone = done
        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
      },
      picture = { bytes, modifier -> PhotoAndroid.Picture(bytes, modifier) },
      tile = { url, modifier, loaded -> MapTilesAndroid.Tile(base(), url, "TohidShop/${BuildConfig.VERSION_NAME} (Android)", modifier, loaded) },
      rate = RateAndroid.hook(this),   //  RATE-01: فقط ساختِ `-Pstore=play`؛ پیش‌فرض null ⇒ هرگز
    )
    if (savedInstanceState == null) intent?.dataString?.let { model.openLink(it) }
    setContent {
      BackHandler(enabled = model.canGoBack) { model.back() }
      CustomerApp(model, platform)
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    intent.dataString?.let { if (::model.isInitialized) model.openLink(it) }
  }

  override fun onResume() {
    super.onResume()
    if (::model.isInitialized) model.resumed()
    //  سنجشِ پس‌زمینهٔ نشانی‌های همان سرور — فقط با پرچمِ روشن و وقتی وقتش رسیده
    routed?.let { r -> lifecycleScope.launch(Dispatchers.IO) { runCatching { r.refreshIfDue() } } }
  }

  //  بیرون از RESUMED (پس‌زمینه، پنجرهٔ دیگر جلو) پیامِ گفت‌وگو «دیده» حساب نمی‌شود — فقط «رسید» (CHAT-04)
  override fun onPause() {
    if (::model.isInitialized) model.paused()
    super.onPause()
  }

  override fun onDestroy() {
    voice.release()
    super.onDestroy()
  }

  private companion object {
    const val SERVER_KEY = "shopper.server"
  }
}
