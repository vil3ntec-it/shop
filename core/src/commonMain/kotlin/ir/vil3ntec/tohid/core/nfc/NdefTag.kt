package ir.vil3ntec.tohid.core.nfc

/**
 *  برچسبِ NFC ِ نوعِ ۴ (NFC Forum Type 4 Tag، نسخهٔ نگاشتِ ۲٫۰) با یک رکوردِ NDEF ِ URI —
 *  گوشیِ مشتری که به دستگاهِ دکان نزدیک شود همان نشانیِ پرداخت (`checkoutUrl`) را باز می‌کند.
 *
 *  کاتلینِ خالص: پاسخِ هر APDU از روی نشانیِ همین لحظه. سرویسِ HCE ِ اندروید
 *  (`HostApduService`) فقط بایت‌ها را به [NdefType4Tag.process] می‌دهد و پاسخ را برمی‌گرداند.
 *
 *  جریانِ خواننده (گوشیِ مشتری):
 *    SELECT AID  00 A4 04 00 07 D2760000850101 00   ⇐ 90 00 (بی پرداختِ فعال ⇐ 6A 82)
 *    SELECT CC   00 A4 00 0C 02 E103                ⇐ 90 00
 *    READ        00 B0 00 00 0F                     ⇐ CC (۱۵ بایت) + 90 00
 *    SELECT NDEF 00 A4 00 0C 02 E104                ⇐ 90 00
 *    READ        00 B0 00 00 02                     ⇐ NLEN + 90 00
 *    READ        00 B0 00 02 <n>                    ⇐ پیام + 90 00
 *
 *  ⛔ فقط خواندنی (دسترسیِ نوشتن FF)؛ UPDATE BINARY و هر دستورِ دیگر رد می‌شود.
 *  ⛔ بی پرداختِ فعال (نشانیِ `null`) حتی برنامهٔ NDEF هم انتخاب نمی‌شود: 6A 82 (file not found).
 */
class NdefType4Tag {

  private var appSelected = false
  private var file = NONE

  /** قطعِ ارتباط با خواننده (`onDeactivated`) ⇐ از نو */
  fun reset() {
    appSelected = false
    file = NONE
  }

  /** پاسخِ یک دستور. `uri`: نشانیِ پرداختِ فعال، یا `null` */
  fun process(apdu: ByteArray, uri: String?): ByteArray {
    val ndef = uri?.let { Ndef.file(it) }
    if (ndef == null) { reset(); return SW_FILE_NOT_FOUND.copyOf() }
    if (apdu.size < 4) return SW_WRONG_LENGTH.copyOf()
    if (apdu[0].toInt() != 0x00) return SW_CLA_NOT_SUPPORTED.copyOf()
    val ins = apdu[1].toInt() and 0xFF
    val p1 = apdu[2].toInt() and 0xFF
    val p2 = apdu[3].toInt() and 0xFF
    return when (ins) {
      0xA4 -> select(apdu, p1, p2)
      0xB0 -> read(apdu, p1, p2, ndef)
      else -> SW_INS_NOT_SUPPORTED.copyOf()
    }
  }

  private fun select(apdu: ByteArray, p1: Int, p2: Int): ByteArray {
    val data = body(apdu) ?: return SW_WRONG_LENGTH.copyOf()
    return when (p1) {
      //  با نامِ برنامه (AID)
      0x04 -> if (data.contentEquals(AID)) {
        appSelected = true
        file = NONE
        SW_OK.copyOf()
      } else {
        appSelected = false
        file = NONE
        SW_FILE_NOT_FOUND.copyOf()
      }
      //  با شناسهٔ پرونده — فقط درونِ برنامهٔ NDEF
      0x00 -> {
        if (!appSelected) return SW_FILE_NOT_FOUND.copyOf()
        if (p2 != 0x0C && p2 != 0x00) return SW_WRONG_P1P2.copyOf()
        if (data.size != 2) return SW_WRONG_LENGTH.copyOf()
        val id = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        when (id) {
          CC_FILE, NDEF_FILE -> { file = id; SW_OK.copyOf() }
          else -> { file = NONE; SW_FILE_NOT_FOUND.copyOf() }
        }
      }
      else -> SW_WRONG_P1P2.copyOf()
    }
  }

  private fun read(apdu: ByteArray, p1: Int, p2: Int, ndef: ByteArray): ByteArray {
    if (!appSelected || file == NONE) return SW_NO_CURRENT_EF.copyOf()
    //  بیتِ بالای P1 = خواندن با شناسهٔ کوتاهِ پرونده — پشتیبانی نمی‌شود
    if (p1 and 0x80 != 0) return SW_WRONG_P1P2.copyOf()
    val offset = (p1 shl 8) or p2
    val content = if (file == CC_FILE) CC else ndef
    if (offset > content.size) return SW_WRONG_P1P2.copyOf()
    //  Le: یک بایتِ پایانی (۰ = ۲۵۶)؛ بی Le ⇐ حداکثرِ اعلام‌شده
    val le = when (apdu.size) {
      4 -> MLE
      5 -> (apdu[4].toInt() and 0xFF).let { if (it == 0) 256 else it }
      else -> return SW_WRONG_LENGTH.copyOf()
    }
    val end = minOf(content.size, offset + minOf(le, MLE))
    return content.copyOfRange(offset, end) + SW_OK
  }

  /** دادهٔ دستور (Lc و همان‌قدر بایت)؛ Le ِ پایانی پذیرفته؛ طولِ ناجور ⇐ `null` */
  private fun body(apdu: ByteArray): ByteArray? {
    if (apdu.size < 5) return null
    val lc = apdu[4].toInt() and 0xFF
    if (lc == 0 || apdu.size < 5 + lc || apdu.size > 6 + lc) return null
    return apdu.copyOfRange(5, 5 + lc)
  }

  companion object {
    /** برنامهٔ NDEF ِ NFC Forum (Type 4، نسخهٔ ۲) */
    val AID: ByteArray = byteArrayOf(0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01)
    const val AID_HEX = "D2760000850101"

    const val CC_FILE = 0xE103
    const val NDEF_FILE = 0xE104
    private const val NONE = -1

    /** بیشترین دادهٔ یک پاسخ و یک فرمان (MLe = 59، MLc = 52 — مقدارهای سازگار با همهٔ خواننده‌ها) */
    const val MLE = 0x3B
    const val MLC = 0x34

    /** بیشینهٔ پروندهٔ NDEF (NLEN + پیام) — نشانیِ درازتر رد می‌شود */
    const val MAX_NDEF_FILE = 0x0400

    /** پروندهٔ قابلیت (CC): نسخهٔ نگاشتِ ۲٫۰، MLe، MLc و TLV ِ پروندهٔ NDEF (فقط خواندنی) */
    val CC: ByteArray = byteArrayOf(
      0x00, 0x0F, // CCLEN
      0x20, // نسخهٔ نگاشت ۲٫۰
      0x00, MLE.toByte(),
      0x00, MLC.toByte(),
      0x04, 0x06, // TLV ِ کنترلِ پروندهٔ NDEF
      0xE1.toByte(), 0x04,
      (MAX_NDEF_FILE shr 8).toByte(), (MAX_NDEF_FILE and 0xFF).toByte(),
      0x00, // خواندن: آزاد
      0xFF.toByte(), // نوشتن: هرگز
    )

    val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
    val SW_FILE_NOT_FOUND = byteArrayOf(0x6A, 0x82.toByte())
    val SW_WRONG_LENGTH = byteArrayOf(0x67, 0x00)
    val SW_WRONG_P1P2 = byteArrayOf(0x6B, 0x00)
    val SW_NO_CURRENT_EF = byteArrayOf(0x69, 0x86.toByte())
    val SW_INS_NOT_SUPPORTED = byteArrayOf(0x6D, 0x00)
    val SW_CLA_NOT_SUPPORTED = byteArrayOf(0x6E, 0x00)
  }
}

/** پیامِ NDEF با یک رکوردِ URI (NFC Forum URI RTD) */
object Ndef {

  /** پیشوندهای کوتاه‌شدهٔ URI RTD — درازتر اول، تا `https://www.` پیش از `https://` بنشیند */
  private val PREFIXES = listOf(
    "https://www." to 0x02,
    "http://www." to 0x01,
    "https://" to 0x04,
    "http://" to 0x03,
  )

  /** رکوردِ تکِ URI به‌عنوانِ پیامِ کامل (MB و ME روشن) */
  fun uriMessage(uri: String): ByteArray {
    val (code, rest) = PREFIXES.firstOrNull { uri.startsWith(it.first) }?.let { it.second to uri.substring(it.first.length) }
      ?: (0x00 to uri)
    val payload = byteArrayOf(code.toByte()) + rest.encodeToByteArray()
    val type = byteArrayOf(0x55) // 'U'
    return if (payload.size <= 255) {
      byteArrayOf(0xD1.toByte(), type.size.toByte(), payload.size.toByte()) + type + payload
    } else {
      val n = payload.size
      byteArrayOf(
        0xC1.toByte(), type.size.toByte(),
        (n ushr 24).toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte(),
      ) + type + payload
    }
  }

  /**
   *  پروندهٔ NDEF ِ برچسب: NLEN (دو بایت) + پیام. فقط `https://` و `http://` پذیرفته است
   *  و نشانیِ بیش از [NdefType4Tag.MAX_NDEF_FILE] یا خالی ⇐ `null` (برچسب خاموش می‌ماند).
   */
  fun file(uri: String): ByteArray? {
    val u = uri.trim()
    if (u.isEmpty() || !(u.startsWith("https://") || u.startsWith("http://"))) return null
    if (u.any { it.code < 0x21 || it.code == 0x7F }) return null
    val msg = uriMessage(u)
    if (msg.size + 2 > NdefType4Tag.MAX_NDEF_FILE) return null
    return byteArrayOf((msg.size shr 8).toByte(), (msg.size and 0xFF).toByte()) + msg
  }
}
