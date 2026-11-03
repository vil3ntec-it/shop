package ir.vil3ntec.tohid.core.nfc

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  برچسبِ پرداختِ صندوقِ گوشیِ نسخهٔ ۳ (HCE، `PayTag`): پیامِ NDEF با نشانیِ پرداخت + متنِ مبلغ، کدِ فاکتور و گیرنده — همان
 *  گفت‌وگوی گوشیِ مشتری با برچسبِ نوعِ ۴ (`NdefType4Tag.processFile`).
 */
class PayTagTest {

  private fun hex(s: String): ByteArray = s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
  private val info = PayTag.Info(amountMinor = 145_025, ref = "HP-7K2M-Q9XA", payeeName = "دکانِ رحیم", payeeLast4 = "4321")
  private val link = "https://pay.hesab.example/s/sess_77"

  /** رکوردهای پیام: (TNF و نوع، بار) */
  private fun records(msg: ByteArray): List<Pair<String, ByteArray>> {
    val out = ArrayList<Pair<String, ByteArray>>()
    var i = 0
    while (i < msg.size) {
      val h = msg[i].toInt() and 0xFF
      val typeLen = msg[i + 1].toInt() and 0xFF
      val short = h and 0x10 != 0
      val (n, at) = if (short) (msg[i + 2].toInt() and 0xFF) to i + 3
      else (((msg[i + 2].toInt() and 0xFF) shl 24) or ((msg[i + 3].toInt() and 0xFF) shl 16) or ((msg[i + 4].toInt() and 0xFF) shl 8) or (msg[i + 5].toInt() and 0xFF)) to i + 6
      val type = msg.copyOfRange(at, at + typeLen).decodeToString()
      out += "${h and 0x07}:$type" to msg.copyOfRange(at + typeLen, at + typeLen + n)
      //  MB فقط روی نخستین، ME فقط روی آخرین
      assertEquals(out.size == 1, h and 0x80 != 0)
      i = at + typeLen + n
      assertEquals(i == msg.size, h and 0x40 != 0)
    }
    return out
  }

  private fun textOf(payload: ByteArray): Pair<String, String> {
    val langLen = payload[0].toInt() and 0x3F
    assertEquals(0, payload[0].toInt() and 0x80, "UTF-8")
    return payload.copyOfRange(1, 1 + langLen).decodeToString() to payload.copyOfRange(1 + langLen, payload.size).decodeToString()
  }

  @Test
  fun autoModeCarriesThePaymentLinkThenAmountReferenceAndPayee() {
    val r = records(PayTag.message(link, info)!!)
    assertEquals(listOf("1:U", "1:T"), r.map { it.first })
    //  URI: پیشوندِ کوتاهِ https:// (۰x۰۴) + بقیهٔ نشانی
    assertEquals(4, r[0].second[0].toInt())
    assertEquals("pay.hesab.example/s/sess_77", r[0].second.copyOfRange(1, r[0].second.size).decodeToString())
    val (lang, text) = textOf(r[1].second)
    assertEquals("fa", lang)
    assertEquals("HesabPay · 1,450.25 AFN · HP-7K2M-Q9XA · → دکانِ رحیم ••4321", text)
  }

  @Test
  fun simpleModeWithoutALinkStillShowsAmountReferenceAndPayee() {
    //  QR ِ دکان شمارهٔ خالی است (نه نشانی) ⇒ فقط متن
    val r = records(PayTag.message("0700123456", info)!!)
    assertEquals(listOf("1:T"), r.map { it.first })
    assertTrue(textOf(r[0].second).second.contains("HP-7K2M-Q9XA"))
    assertEquals(listOf("1:T"), records(PayTag.message(null, info)!!).map { it.first })
    assertEquals(listOf("1:T"), records(PayTag.message("javascript:alert(1)", info)!!).map { it.first })
  }

  @Test
  fun wholeAmountsHaveNoCentsAndThousandsAreGrouped() {
    assertEquals("1,450", PayTag.amount(145_000))
    assertEquals("12,345,678.05", PayTag.amount(1_234_567_805))
    assertEquals("0.50", PayTag.amount(50))
  }

  @Test
  fun badAmountOrMissingReferenceTurnsTheTagOff() {
    assertNull(PayTag.message(link, info.copy(amountMinor = 0)))
    assertNull(PayTag.file(link, info.copy(ref = " ")))
  }

  @Test
  fun fileIsLengthPrefixedAndReadableThroughTheType4Session() {
    val file = assertNotNull(PayTag.file(link, info))
    val msg = PayTag.message(link, info)!!
    assertEquals(msg.size, ((file[0].toInt() and 0xFF) shl 8) or (file[1].toInt() and 0xFF))
    val tag = NdefType4Tag()
    assertContentEquals(NdefType4Tag.SW_OK, tag.processFile(hex("00 A4 04 00 07 D2760000850101 00"), file))
    assertContentEquals(NdefType4Tag.SW_OK, tag.processFile(hex("00 A4 00 0C 02 E104"), file))
    val got = ArrayList<Byte>()
    var at = 0
    while (at < file.size) {
      val r = tag.processFile(byteArrayOf(0x00, 0xB0.toByte(), (at shr 8).toByte(), at.toByte(), NdefType4Tag.MLE.toByte()), file)
      assertContentEquals(NdefType4Tag.SW_OK, r.copyOfRange(r.size - 2, r.size))
      got += r.copyOfRange(0, r.size - 2).toList()
      at += r.size - 2
    }
    assertContentEquals(file, got.toByteArray())
    //  بی پرداختِ باز ⇒ خاموش
    assertContentEquals(NdefType4Tag.SW_FILE_NOT_FOUND, NdefType4Tag().processFile(hex("00 A4 04 00 07 D2760000850101 00"), null))
  }

  @Test
  fun singleUriMessageIsUnchanged() {
    //  همان بایت‌های پیش از پیامِ چندرکوردی (`HesabPaySheet` ِ کامپیوتر و برچسبِ نشانیِ تنها)
    assertEquals("D1010C5504706179", Ndef.uriMessage("https://pay.example").copyOfRange(0, 8).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase() })
    assertContentEquals(Ndef.fileOf(Ndef.uriMessage("https://pay.example")), Ndef.file("https://pay.example"))
  }
}
