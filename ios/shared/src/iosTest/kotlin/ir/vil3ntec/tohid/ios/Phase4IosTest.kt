package ir.vil3ntec.tohid.ios

import android.content.ComponentName
import android.graphics.Paint
import android.print.PrintAttributes
import android.print.pdf.PrintedPdfDocument
import android.content.IosContext
import ir.vil3ntec.tohid.platform.PinnedTls
import java.io.ByteArrayOutputStream
import javax.net.ssl.IosTls
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFRelease
import platform.Security.SecCertificateCreateWithData
import platform.Security.SecPolicyCreateBasicX509
import platform.Security.SecTrustCreateWithCertificates
import platform.Security.SecTrustRefVar

/**
 *  گامِ ۴ ِ آیفون روی شبیه‌ساز (کارِ `ios-app`؛ روی لینوکس فقط کامپایل می‌شود):
 *  سنجاقِ گواهیِ «سرورِ خودِ دکان» با **همان** `PinnedTls` ِ اندروید روی زنجیرهٔ `SecTrust` ِ iOS، PDF ِ چاپ
 *  (`PrintedPdfDocument` ِ Skia + zlib)، `ComponentName` و هگزِ توکنِ APNs.
 */
@OptIn(ExperimentalEncodingApi::class)
class Phase4IosTest {
  /** گواهیِ خودامضای P-256 (CN=shop.local)، DER؛ SHA-256 همان [PIN] */
  private val der = Base64.decode("MIIBfzCCASWgAwIBAgIUV/8Crf8O7+MCeqgRvRg69zcDtlcwCgYIKoZIzj0EAwIwFTETMBEGA1UEAwwKc2hvcC5sb2NhbDAeFw0yNjExMDIxNDUyMTZaFw0zNjEwMzAxNDUyMTZaMBUxEzARBgNVBAMMCnNob3AubG9jYWwwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAARuuHSF3BIwAZODir23gNgX6rVbVJuzzNMbQnXY+T/R5YTH+x+7NMkf9EMA1jQw1KVAHwblBmG+V/quZeHMSYhto1MwUTAdBgNVHQ4EFgQUhPkpNSh5r+fgjYRxVDTaQ+IeAD0wHwYDVR0jBBgwFoAUhPkpNSh5r+fgjYRxVDTaQ+IeAD0wDwYDVR0TAQH/BAUwAwEB/zAKBggqhkjOPQQDAgNIADBFAiEAlSSEqCapNgaGeeQoR/mLD54vvrE76ecH250WM/gI1d0CIGRQWtP+4vb6bMivwzLiM3jaWRt9VSwoUGl/wHbDgUQl")

  private fun trustOf(bytes: ByteArray) = memScoped {
    val data = bytes.usePinned { CFDataCreate(null, it.addressOf(0).reinterpret(), bytes.size.toLong()) }
    val cert = SecCertificateCreateWithData(null, data)!!
    val out = alloc<SecTrustRefVar>()
    SecTrustCreateWithCertificates(cert, SecPolicyCreateBasicX509(), out.ptr)
    CFRelease(data)
    out.value!!
  }

  @Test
  fun chainOfSecTrustIsTheSameDer() {
    val chain = IosTls.chainOf(trustOf(der))
    assertEquals(1, chain.size)
    assertTrue(chain[0].contentEquals(der))
    assertEquals(PIN, PinnedTls.sha256Hex(chain[0]))
  }

  @Test
  fun androidPinAcceptsOnlyTheSameCertificate() {
    val chain = IosTls.chainOf(trustOf(der))
    val ok = PinnedTls.of(PIN)
    assertTrue(IosTls.accepts(chain, "192.168.1.10", listOf(ok.trust), ok.verifier))
    val other = PinnedTls.of("00".repeat(32))
    assertFalse(IosTls.accepts(chain, "192.168.1.10", listOf(other.trust), other.verifier))
    //  بی verifier یا بی مدیرِ اعتماد ⇐ رد (بسته)
    assertFalse(IosTls.accepts(chain, "192.168.1.10", listOf(ok.trust), null))
    assertFalse(IosTls.accepts(chain, "192.168.1.10", emptyList(), ok.verifier))
    assertFalse(IosTls.accepts(emptyList(), "192.168.1.10", listOf(ok.trust), ok.verifier))
  }

  @Test
  fun printedPdfIsA4AndReadable() {
    val attrs = PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4)
      .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME).build()
    val doc = PrintedPdfDocument(IosContext, attrs)
    assertEquals(595, doc.pageWidth)
    assertEquals(841, doc.pageHeight)
    repeat(2) { n ->
      val page = doc.startPage(n)
      assertEquals(595, page.canvas.width)
      assertEquals(841, page.canvas.height)
      page.canvas.drawRect(28f, 28f, 300f, 60f, Paint().apply { color = android.graphics.Color.BLACK })
      doc.finishPage(page)
    }
    val out = ByteArrayOutputStream()
    doc.writeTo(out)
    doc.close()
    val text = out.toByteArray().decodeToString()
    assertTrue(text.startsWith("%PDF-1.4"))
    assertTrue("/Count 2" in text)
    assertTrue("/MediaBox [0 0 595.00 841.00]" in text)
    assertTrue("/ColorSpace /DeviceGray" in text)
  }

  @Test
  fun componentNameRoundTrip() {
    val c = ComponentName("ir.vil3ntec.tohid", "ir.vil3ntec.tohid.pay.platform.HesabPayNoticeService")
    assertEquals(c, ComponentName.unflattenFromString(c.flattenToString()))
    assertEquals(ComponentName("a.b", "a.b.C"), ComponentName.unflattenFromString("a.b/.C"))
    assertEquals(null, ComponentName.unflattenFromString("nothing"))
  }

  @Test
  fun apnsTokenIsLowerHex() {
    assertEquals("00ff10ab", IosPush.hex(byteArrayOf(0, -1, 0x10, 0xAB.toByte())))
  }

  private companion object {
    const val PIN = "808bc9dbd0f6db1a8040ad5a9540bfd9f7536f6f7ebf1a5a77a46d3407728379"
  }
}
