package ir.vil3ntec.tohid.pay.platform

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 *  سخت‌افزارِ پرداختِ همین دستگاه — نسخهٔ اندروید (کامپیوتر: `desktop/…/PayDeviceDesk.kt` با همین API).
 *
 *  ⛔ هیچ‌چیز این‌جا به سرویس‌های گوگل تکیه ندارد: NFC و HCE بخشِ خودِ اندروید است، پس روی
 *  کارت‌خوان‌های هوشمندِ اندرویدی (بی Play) هم کار می‌کند. نبودنِ NFC هیچ بخشی را نمی‌بندد.
 */
object PayDevice {

  enum class Nfc {
    /** دستگاه NFC یا شبیه‌سازیِ برچسب (HCE) ندارد ⇐ هیچ نشانه‌ای از NFC دیده نمی‌شود */
    NONE,

    /** دارد ولی خاموش است ⇐ میان‌برِ تنظیمات */
    OFF,

    /** آماده ⇐ «گوشی را نزدیک کنید» */
    READY,
  }

  fun nfc(context: Context): Nfc {
    val hce = runCatching { context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION) }.getOrDefault(false)
    if (!hce) return Nfc.NONE
    val adapter = runCatching { NfcAdapter.getDefaultAdapter(context) }.getOrNull() ?: return Nfc.NONE
    return if (runCatching { adapter.isEnabled }.getOrDefault(false)) Nfc.READY else Nfc.OFF
  }

  /** صفحهٔ تنظیماتِ NFC؛ دستگاهی که ندارد ⇐ تنظیماتِ بی‌سیم، بعد تنظیماتِ کلی */
  fun openNfcSettings(context: Context) {
    for (action in listOf(Settings.ACTION_NFC_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS, Settings.ACTION_SETTINGS)) {
      val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
  }

  /**
   *  تا برگهٔ QR باز است، سرویسِ HCE ِ ما برنامهٔ پیش‌رو باشد — اگر برنامهٔ دیگری هم همان AID ِ NDEF
   *  را گرفته، اندروید از مشتری نمی‌پرسد کدام. بی NFC، بی‌اثر.
   */
  @Composable
  fun HceForeground(active: Boolean) {
    val context = LocalContext.current
    DisposableEffect(active) {
      val activity = if (active) context.activity() else null
      val emulation = activity?.let { a ->
        runCatching { NfcAdapter.getDefaultAdapter(a)?.let { CardEmulation.getInstance(it) } }.getOrNull()
      }
      if (activity != null && emulation != null) {
        runCatching { emulation.setPreferredService(activity, ComponentName(activity, HesabPayHceService::class.java)) }
      }
      onDispose {
        if (activity != null && emulation != null) runCatching { emulation.unsetPreferredService(activity) }
      }
    }
  }

  private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
  }
}
