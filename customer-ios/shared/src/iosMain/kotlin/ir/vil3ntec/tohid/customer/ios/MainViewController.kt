package ir.vil3ntec.tohid.customer.ios

import androidx.compose.ui.window.ComposeUIViewController
import ir.vil3ntec.tohid.core.shopper.ShopperApi
import ir.vil3ntec.tohid.core.shopper.ShopperText
import ir.vil3ntec.tohid.customer.ui.CustomerApp
import ir.vil3ntec.tohid.customer.ui.CustomerModel
import ir.vil3ntec.tohid.customer.ui.Locator
import ir.vil3ntec.tohid.customer.ui.Platform
import ir.vil3ntec.tohid.customer.ui.openLink
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import platform.Foundation.NSDate
import platform.Foundation.NSURL
import platform.Foundation.timeIntervalSince1970
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

/*
 *  دروازهٔ SwiftUI ِ برنامهٔ خریدِ مشتری (`customer-ios/iosApp`): همان `CustomerApp` و `CustomerModel` ِ اندروید، با مرزهای
 *  آیفون (`*Ios.kt`). پیوندِ عمیق از `.onOpenURL` ِ SwiftUI به [openUrl] می‌رسد (طرحِ `tohidshop://` ِ Info.plist؛
 *  پیوندِ جهانیِ https پروندهٔ apple-app-site-association و Team ID می‌خواهد — کارِ صاحب‌کار، `docs/IOS-PLAN-fa.md` §۰.۶).
 */

private const val SERVER_KEY = "shopper.server"

private val store = StoreIos()
//  همان دامنهٔ اندروید (`customer-android/gradle.properties` ⇐ `CustomerIosConfig`، ساخته‌شده)؛ آزمون: «تنظیمات ⇐ نشانیِ سرور»
private val server = { store.get(SERVER_KEY)?.takeIf { it.isNotBlank() } ?: CustomerIosConfig.API_BASE }
private val http = HttpIos(server)
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

private val model: CustomerModel by lazy {
  CustomerModel(
    ShopperApi(http) { ShopperText.lang.code }, store, scope, Dispatchers.IO, { (NSDate().timeIntervalSince1970 * 1000).toLong() },
    locator = Locator { done -> LocationIos.current(done) },
    secureRandom = { n -> ByteArray(n).also { b -> b.usePinned { SecRandomCopyBytes(kSecRandomDefault, n.toULong(), it.addressOf(0)) } } },
  )
}

private val platform: Platform by lazy {
  Platform(
    photo = { path, modifier -> ImagesIos.Photo(http, path, modifier) },
    call = { phone -> NSURL.URLWithString("tel:$phone")?.let { UIApplication.sharedApplication.openURL(it, options = emptyMap<Any?, Any>(), completionHandler = null) } },
    server = server,
    setServer = { store.put(SERVER_KEY, it.trim().takeIf { s -> s.isNotEmpty() }) },
    version = CustomerIosConfig.VERSION_NAME,
    openMap = { geo ->
      //  «نمایش روی نقشه» ⇐ Apple Maps با همان مختصات (iOS طرحِ `geo:` ندارد)
      val p = geo.substringAfter("geo:").substringBefore('?')
      NSURL.URLWithString("https://maps.apple.com/?ll=$p&q=$p")?.let { UIApplication.sharedApplication.openURL(it, options = emptyMap<Any?, Any>(), completionHandler = null) }
    },
    canPickPhoto = true,
    pickPhoto = { done -> PhotoIos.pick(done) },
    picture = { bytes, modifier -> ImagesIos.Picture(bytes, modifier) },
    tile = { url, modifier, loaded -> ImagesIos.Tile(http, url, modifier, loaded) },
  )
}

/** `MainViewControllerKt.MainViewController()` ِ SwiftUI */
fun MainViewController(): UIViewController = ComposeUIViewController { CustomerApp(model, platform) }

/** `.onOpenURL` ِ SwiftUI: `tohidshop://store/…` یا نشانیِ ویترین */
fun openUrl(url: String) {
  model.openLink(url)
}

/** برنامه دوباره پیش آمد (`scenePhase == .active`): پیگیری، سبد و صفِ گفت‌وگو از نو */
fun resumed() = model.resumed()

/** برنامه دیگر جلوی چشم نیست (`scenePhase` ِ inactive/background): گفت‌وگو فقط «رسید»، نه «خواندم» (CHAT-04) */
fun paused() = model.paused()
