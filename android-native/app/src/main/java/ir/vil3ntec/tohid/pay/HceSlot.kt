package ir.vil3ntec.tohid.pay

/**
 *  نشانیِ پرداختی که برچسبِ NFC ِ دستگاه همین حالا نشان می‌دهد (HCE، PAY-21).
 *
 *  فقط وقتی برگهٔ QR ِ حساب‌پی باز است پر است (`HesabPaySheet`)؛ بسته شدنِ برگه ⇐ `null` و
 *  سرویسِ HCE به هر خواننده‌ای «پرونده نیست» (6A 82) می‌گوید. کامپیوتر NFC ندارد و این‌جا بی‌اثر است.
 */
object HceSlot {
  @Volatile var url: String? = null
}
