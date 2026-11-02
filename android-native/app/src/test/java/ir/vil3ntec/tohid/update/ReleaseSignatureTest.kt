package ir.vil3ntec.tohid.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 *  S64 (بخشِ برنامه) — فهرستِ جمع‌های کنترلیِ انتشار (`SHA256SUMS.txt`) فقط با امضای کلیدِ سنجاق‌شده
 *  پذیرفته می‌شود (`ReleaseSignature.verify`). امضا همان قالبِ کارِ «انتشار» است: ECDSA P-256 با
 *  SHA-256، امضای DER به base64 و کلیدِ عمومیِ X.509 به base64.
 *
 *  فهرستِ دست‌خورده، امضای کلیدِ دیگر، امضا یا کلیدِ خراب و خالی همه «نه» اند و هیچ‌کدام استثنا
 *  بیرون نمی‌دهند؛ نبودِ کلیدِ سنجاق‌شده یعنی رفتارِ پیشین (فقط جمعِ کنترلی).
 */
class ReleaseSignatureTest {

  private val sums = "3f2a…  tohid-2.14.5.apk\n9b1c…  tohid-2.14.5-setup.exe\n"

  private fun keys(): KeyPair =
    KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

  private fun sign(text: String, kp: KeyPair): String =
    Base64.getEncoder().encodeToString(
      Signature.getInstance("SHA256withECDSA").run {
        initSign(kp.private)
        update(text.toByteArray(Charsets.UTF_8))
        sign()
      },
    )

  private fun spki(kp: KeyPair): String = Base64.getEncoder().encodeToString(kp.public.encoded)

  @Test fun `فهرستِ امضاشده با کلیدِ سنجاق‌شده پذیرفته می‌شود`() {
    val kp = keys()
    assertTrue(ReleaseSignature.verify(sums, sign(sums, kp), spki(kp)))
    //  خطِ تازهٔ آخرِ فایلِ امضا و کلید بی‌اثر است
    assertTrue(ReleaseSignature.verify(sums, sign(sums, kp) + "\n", " " + spki(kp) + "\n"))
  }

  @Test fun `فهرستِ دست‌خورده رد می‌شود`() {
    val kp = keys()
    val sig = sign(sums, kp)
    assertFalse(ReleaseSignature.verify(sums.replace("3f2a", "3f2b"), sig, spki(kp)))
    assertFalse(ReleaseSignature.verify(sums + "0000…  evil.apk\n", sig, spki(kp)))
    assertFalse(ReleaseSignature.verify(sums.trimEnd(), sig, spki(kp)))
  }

  @Test fun `امضای کلیدِ دیگر رد می‌شود`() {
    val pinned = keys()
    val other = keys()
    assertFalse(ReleaseSignature.verify(sums, sign(sums, other), spki(pinned)))
  }

  @Test fun `امضا یا کلیدِ خراب و خالی بی استثنا رد می‌شود`() {
    val kp = keys()
    val sig = sign(sums, kp)
    assertFalse(ReleaseSignature.verify(sums, "", spki(kp)))
    assertFalse(ReleaseSignature.verify(sums, "not-base64!!", spki(kp)))
    assertFalse(ReleaseSignature.verify(sums, Base64.getEncoder().encodeToString(ByteArray(70)), spki(kp)))
    assertFalse(ReleaseSignature.verify(sums, sig, ""))
    assertFalse(ReleaseSignature.verify(sums, sig, "AAAA"))
  }

  @Test fun `بی کلیدِ سنجاق‌شده رفتارِ پیشین می‌ماند`() {
    assertFalse(ReleaseSignature.pinned(""))
    assertFalse(ReleaseSignature.pinned("  "))
    assertTrue(ReleaseSignature.pinned(spki(keys())))
  }
}
