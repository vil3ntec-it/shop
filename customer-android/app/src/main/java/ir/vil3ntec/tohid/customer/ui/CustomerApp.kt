package ir.vil3ntec.tohid.customer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import ir.vil3ntec.tohid.core.shopper.ChatPhoto

/**
 *  مرزِ سکو برای صفحه‌ها (همهٔ آن‌چه اندروید/آیفون فرق دارد): عکس، تماس، نشانیِ سرور و نسخه.
 *  [photo] مسیرِ نسبی (`/api/store/…/thumb?v=…`) می‌گیرد؛ پیاده‌سازیِ اندروید `ImagesAndroid.Photo` است.
 *  مرحلهٔ ۲: [openMap] پیوندِ `geo:` ِ پین را به برنامهٔ نقشهٔ گوشی می‌دهد؛ [pickPhoto] عکسی از گالری می‌گیرد و
 *  **آماده** برمی‌گرداند (چرخیده با EXIF، شفافیت روی سفید، ≤ ۱۶۰۰px، JPEG ِ تازه بی EXIF — `ShopperPhoto`)؛
 *  [picture] بایت‌های عکسِ گفت‌وگو را می‌کشد. CHK-08: [tile] یک تکهٔ ۲۵۶ ِ نقشه (مسیرِ همین سرور یا https ِ سرورِ تکه‌های
 *  ویترین) را بی کوکی می‌کشد و [loaded] را یک بار با «آمد؟» صدا می‌زند؛ null ⇒ «انتخاب روی نقشه» نیست.
 */
class Platform(
  val photo: @Composable (path: String, modifier: Modifier) -> Unit,
  val call: (phone: String) -> Unit,
  val server: () -> String,
  val setServer: (String) -> Unit,
  val version: String,
  val openMap: (geoUri: String) -> Unit = {},
  val canPickPhoto: Boolean = false,
  val pickPhoto: (done: (ChatPhoto?) -> Unit) -> Unit = { it(null) },
  val picture: @Composable (bytes: ByteArray, modifier: Modifier) -> Unit = { _, m -> Box(m) },
  val tile: (@Composable (url: String, modifier: Modifier, loaded: (Boolean) -> Unit) -> Unit)? = null,
)

val LocalPlatform = staticCompositionLocalOf<Platform> { error("Platform داده نشده") }

/** رنگِ ویترین (#0F766E، همان accent ِ پیش‌فرضِ دکان‌ها)؛ کنتراستِ متن روی هر دو تم ≥ ۴٫۵ */
private val Light: ColorScheme = lightColorScheme(
  primary = Color(0xFF0F766E), onPrimary = Color.White,
  primaryContainer = Color(0xFFCCEFEA), onPrimaryContainer = Color(0xFF00201D),
  secondary = Color(0xFF4A635F), error = Color(0xFFB3261E),
)
private val Dark: ColorScheme = darkColorScheme(
  primary = Color(0xFF6FD6C8), onPrimary = Color(0xFF003732),
  primaryContainer = Color(0xFF005049), onPrimaryContainer = Color(0xFFCCEFEA),
  secondary = Color(0xFFB1CCC7), error = Color(0xFFF2B8B5),
)

/** ریشهٔ برنامه: تم (مثلِ گوشی/روشن/تیره)، جهتِ نوشته از زبان، و صفحهٔ بالای پشته. */
@Composable
fun CustomerApp(model: CustomerModel, platform: Platform) {
  val dark = when (model.saved.theme) {
    "dark" -> true
    "light" -> false
    else -> isSystemInDarkTheme()
  }
  val dir = if (model.lang.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
  MaterialTheme(colorScheme = if (dark) Dark else Light) {
    CompositionLocalProvider(LocalLayoutDirection provides dir, LocalPlatform provides platform) {
      Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        //  زبانِ تازه ⇒ همهٔ نوشته‌ها از نو (ShopperText حالتِ Compose نیست)
        key(model.lang) {
          Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            when (val r = model.route) {
              Route.Home -> HomeScreen(model)
              is Route.Shop -> ShopScreen(model, r.slug)
              is Route.Product -> ProductScreen(model, r.slug, r.id)
              is Route.CartPage -> CartScreen(model, r.slug)
              is Route.Checkout -> CheckoutScreen(model, r.slug)
              is Route.Placed -> PlacedScreen(model, r.slug, r.token)
              is Route.Track -> TrackScreen(model, r.slug, r.token)
              Route.Settings -> SettingsScreen(model)
              Route.Addresses -> AddressesScreen(model)
              is Route.AddressEdit -> AddressEditScreen(model, r.id)
              is Route.Chat -> ChatScreen(model, r.slug, r.orderNo)
            }
          }
        }
      }
    }
  }
}
