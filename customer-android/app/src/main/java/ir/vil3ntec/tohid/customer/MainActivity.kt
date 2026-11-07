package ir.vil3ntec.tohid.customer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import ir.vil3ntec.tohid.core.shopper.ShopperApi
import ir.vil3ntec.tohid.core.shopper.ShopperText
import ir.vil3ntec.tohid.customer.ui.CustomerApp
import ir.vil3ntec.tohid.customer.ui.CustomerModel
import ir.vil3ntec.tohid.customer.ui.Platform
import kotlinx.coroutines.Dispatchers

/** نقطهٔ ورودِ اندروید: فقط سیم‌کشیِ مرزهای سکو؛ همهٔ صفحه‌ها در `ui/` (بی android.*). */
class MainActivity : ComponentActivity() {
  private lateinit var model: CustomerModel

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val kv = StoreAndroid(this)
    val server = { kv.get(SERVER_KEY)?.takeIf { it.isNotBlank() } ?: BuildConfig.API_BASE }
    val api = ShopperApi(HttpAndroid(server)) { ShopperText.lang.code }
    model = CustomerModel(api, kv, lifecycleScope, Dispatchers.IO, System::currentTimeMillis)
    val platform = Platform(
      photo = { path, modifier -> ImagesAndroid.Photo(server().trimEnd('/') + path, modifier) },
      call = { phone -> startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) },
      server = server,
      setServer = { kv.put(SERVER_KEY, it.trim().takeIf { s -> s.isNotEmpty() }) },
      version = BuildConfig.VERSION_NAME,
    )
    setContent {
      BackHandler(enabled = model.canGoBack) { model.back() }
      CustomerApp(model, platform)
    }
  }

  override fun onResume() {
    super.onResume()
    if (::model.isInitialized) model.resumed()
  }

  private companion object {
    const val SERVER_KEY = "shopper.server"
  }
}
