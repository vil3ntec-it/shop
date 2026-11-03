package ir.vil3ntec.tohid.ios

import android.content.Context
import android.content.IosContext
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runSkikoComposeUiTest
import ir.vil3ntec.tohid.data.Migration
import ir.vil3ntec.tohid.pay.platform.PayDevice
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 *  گامِ ۹ ِ آیفون روی شبیه‌ساز (کارِ `ios-app`؛ روی لینوکس فقط کامپایل می‌شود): **خودِ** `data/Migration.kt` و
 *  `pay/platform/PayDevice.kt` ِ اندروید روی shimهای بی‌رفتارِ WebView، NFC و نمایشگرِ دوم، با همان نتیجهٔ همتاهای
 *  پاک‌شده (`MigrationIos`: «دفترِ قدیمی نیست»؛ `PayDeviceIos`: «NFC نیست» و بی صفحهٔ مشتری).
 */
class Phase9IosTest {
  @Test
  fun migrationDarIphoneDaftareGhadimiNadarad() {
    assertFalse(Migration.hasLegacyWebData(IosContext))
    assertNull(runBlocking { Migration.readLegacyData(IosContext) })
    assertNull(runBlocking { Migration.readLegacyData(IosActivity) })
    //  اگر روزی WebView ای ساخته شود ⇐ خطای روشن (`AppRoot` آن را با runCatching به «دفترِ قدیمی نیست» می‌برد)
    assertFailsWith<UnsupportedOperationException> { android.webkit.WebView(IosContext) }
  }

  @Test
  fun payDeviceDarIphoneNfcNadarad() {
    assertEquals(PayDevice.Nfc.NONE, PayDevice.nfc(IosContext))
    assertEquals(PayDevice.Nfc.NONE, PayDevice.nfc(IosActivity))
    assertFalse(IosContext.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION))
    assertNull(NfcAdapter.getDefaultAdapter(IosContext))
    assertNull(IosActivity.getSystemService(Context.DISPLAY_SERVICE))
  }

  /** همان ریشهٔ آیفون (`LocalContext` = `IosActivity`)؛ برگهٔ حساب‌پی هر دو را با `active = true` می‌سازد */
  @OptIn(ExperimentalTestApi::class)
  @Test
  fun payDeviceComposableHaBiAsar() = runSkikoComposeUiTest {
    setContent {
      CompositionLocalProvider(LocalContext provides IosActivity) {
        PayDevice.HceForeground(active = true)
        PayDevice.CustomerScreen(active = true) { Text("صفحهٔ مشتری") }
        Text("برگهٔ حساب‌پی")
      }
    }
    waitForIdle()
    onNodeWithText("برگهٔ حساب‌پی").assertExists()
    //  بی نمایشگرِ دوم هیچ پنجرهٔ ارائه‌ای ساخته نمی‌شود
    assertEquals(0, onAllNodesWithText("صفحهٔ مشتری").fetchSemanticsNodes().size)
  }
}
