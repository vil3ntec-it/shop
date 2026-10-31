package ir.vil3ntec.tohid.pay.platform

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import ir.vil3ntec.tohid.core.nfc.NdefType4Tag
import ir.vil3ntec.tohid.pay.HceSlot

/**
 *  برچسبِ NFC ِ نوعِ ۴ با نشانیِ پرداختِ حساب‌پی (HCE) — گوشیِ مشتری با لمسِ دستگاهِ دکان همان
 *  صفحهٔ پرداخت را باز می‌کند. هیچ کارتِ بانکی‌ای این‌جا خوانده یا شبیه‌سازی نمی‌شود.
 *
 *  همهٔ منطقِ APDU در `core/nfc/NdefTag.kt` (آزمون‌شده)؛ این‌جا فقط سیم. نشانی از [HceSlot] و فقط
 *  تا برگهٔ QR باز است؛ بی آن هر دستوری 6A 82 می‌گیرد. AID و دسته در `res/xml/apduservice.xml`.
 *
 *  ⚠️ فقط اندروید: در `desktop/build.gradle.kts` (`replaced`) کنار گذاشته می‌شود.
 */
class HesabPayHceService : HostApduService() {

  //  اندروید همهٔ فراخوان‌ها را روی نخِ اصلی می‌دهد
  private val tag = NdefType4Tag()

  override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
    val apdu = commandApdu ?: return NdefType4Tag.SW_WRONG_LENGTH.copyOf()
    return tag.process(apdu, HceSlot.url)
  }

  override fun onDeactivated(reason: Int) {
    tag.reset()
  }
}
