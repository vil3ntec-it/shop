package ir.vil3ntec.tohid.pay

import ir.vil3ntec.tohid.core.nfc.Ndef

/**
 *  آنچه برچسبِ NFC ِ دستگاه همین حالا نشان می‌دهد (HCE، PAY-21).
 *
 *  - [url]: نشانیِ تنها — برگهٔ QR ِ حساب‌پیِ کامپیوتر و صندوقِ کهنه (`HesabPaySheet`).
 *  - [ndef]: پروندهٔ NDEF ِ آماده — صندوقِ گوشیِ نسخهٔ ۳ (`HesabTillRoute`، `PayTag.file`): نشانیِ پرداخت + متنِ مبلغ،
 *    کدِ فاکتور و گیرنده. اگر هست بر [url] مقدم است.
 *  فقط تا برگهٔ پرداخت باز است پر است؛ بسته شدنِ برگه ⇐ `null` و سرویسِ HCE به هر خواننده‌ای «پرونده نیست» (6A 82)
 *  می‌گوید. کامپیوتر NFC ندارد و آیفون HCE به برنامه نمی‌دهد؛ آن‌جا بی‌اثر است (فقط QR).
 */
object HceSlot {
  @Volatile var url: String? = null
  @Volatile var ndef: ByteArray? = null

  /** پروندهٔ NDEF ِ همین لحظه برای `NdefType4Tag.processFile`؛ هیچ ⇒ `null` */
  fun file(): ByteArray? = ndef ?: url?.let { Ndef.file(it) }
}
