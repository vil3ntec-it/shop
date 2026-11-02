package ir.vil3ntec.tohid.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.pay.platform.HesabPayEnv
import ir.vil3ntec.tohid.pay.platform.HesabPayNotice
import ir.vil3ntec.tohid.scan.CameraScanner
import ir.vil3ntec.tohid.ui.theme.Shop

/*
 *  مرزِ پلتفرمِ «حساب‌پیِ دکان» (`HesabPayAccountSettings.kt` بی Context می‌ماند): محیطِ حساب‌پی، نقشِ صاحب، تنظیمِ
 *  پیشنهادِ اعلان (فقط اندروید؛ کامپیوتر `HesabPayNoticeDesk.kt`) و دوربینِ QR با اجازه‌اش.
 */

/** آنچه صفحهٔ «حساب‌پیِ دکان» از دستگاه می‌خواهد */
internal class HesabPayDeps(val env: HesabPayEnv, private val app: Context) {
  val noticeSupported: Boolean get() = HesabPayNotice.SUPPORTED

  /** واردشده، سرورِ آماده و نقشِ همین دستگاه صاحبِ دکان */
  fun ownerReady(signedIn: Boolean): Boolean = signedIn && ShopRole.current(app) == ShopRole.OWNER && Backend.isReady(app)

  fun hintOn(): Boolean = HesabPayNotice.hintOn(app)

  fun setHintOn(on: Boolean) = HesabPayNotice.setHintOn(app, on)

  fun noticeGranted(): Boolean = HesabPayNotice.granted(app)

  fun openNoticeAccess() = HesabPayNotice.openAccess(app)
}

@Composable
internal fun rememberHesabPayDeps(): HesabPayDeps {
  val context = LocalContext.current
  return remember { HesabPayDeps(HesabPayEnv.of(context), context.applicationContext ?: context) }
}

/**
 *  تاریخچهٔ حساب‌های حساب‌پیِ دکان (نام، ۴ رقم، اثرِ انگشت) از پیکربندیِ صندوق ⇐ X/Z به تفکیکِ حساب (`DrawerModel.hesabPayLines`)؛
 *  بی نت یا خطا ⇒ خالی (حسابِ بیگانه همچنان جدا و با نشان).
 */
@Composable
internal fun rememberHesabPayees(): List<ir.vil3ntec.tohid.core.gateway.Payee> {
  val context = LocalContext.current
  return androidx.compose.runtime.produceState(emptyList<ir.vil3ntec.tohid.core.gateway.Payee>()) {
    value = runCatching { HesabPayEnv.of(context).till().payees }.getOrDefault(emptyList())
  }.value
}

/** دوربین فقط برای QR ِ خودِ دکان (اندروید)؛ خوانشِ اول پذیرفته می‌شود */
@Composable
internal fun HpQrScanDialog(onDismiss: () -> Unit, onText: (String) -> Unit) {
  val context = LocalContext.current
  var granted by remember {
    mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
  }
  var taken by remember { mutableStateOf(false) }
  val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> granted = ok }
  LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
  Dialog(onDismissRequest = onDismiss) {
    Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
        if (granted) {
          CameraScanner(
            onCode = { text -> if (!taken) { taken = true; onText(text) } },
            onStatus = { _, _ -> },
            modifier = Modifier.fillMaxWidth().height(320.dp),
            qrOnly = true,
          )
        } else {
          Text("برای اسکن، اجازهٔ دوربین لازم است.", color = Shop.colors.muted)
        }
      }
      OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("بستن") }
    }
  }
}
