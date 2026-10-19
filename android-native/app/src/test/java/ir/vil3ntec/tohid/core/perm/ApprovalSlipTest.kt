package ir.vil3ntec.tohid.core.perm

import ir.vil3ntec.tohid.data.PermPolicy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 *  «برگهٔ امضاشده» (STF-02) و PINِ هر کاربر (STF-04) — بردارهای مشترکِ
 *  `core/test-vectors/approval-v1.json`، همان فایلی که `server/test/staff-approval.test.js`
 *  روی `license/approval-core.js` و نسخهٔ سرور می‌دواند.
 */
class ApprovalSlipTest {

  private val v: JsonObject = run {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/approval-v1.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/approval-v1.json پیدا نشد")
  }

  private fun list(name: String) = v.getValue(name).jsonArray.map { it.jsonObject }
  private fun slipOf(o: Any?): ApprovalSlip.Slip? = ApprovalSlip.fromMap(PermPolicy.tree(o as kotlinx.serialization.json.JsonElement) as? Map<*, *>)
  private fun outOf(c: JsonObject): String? = c.getValue("out").let { if (it is JsonNull) null else it.jsonPrimitive.content }
  private val testKey = v.getValue("test_key").jsonObject
  private fun b64(s: String) = Base64.getDecoder().decode(s)

  private fun verify(spkiB64: String, text: String, sigB64: String): Boolean {
    val pub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(b64(spkiB64)))
    val s = Signature.getInstance("SHA256withECDSA")
    s.initVerify(pub)
    s.update(text.toByteArray(Charsets.UTF_8))
    return runCatching { s.verify(b64(sigB64)) }.getOrDefault(false)
  }

  @Test fun constants() {
    assertEquals(v.getValue("version").jsonPrimitive.content.toInt(), ApprovalSlip.VERSION)
    assertEquals(v.getValue("ttl_max_ms").jsonPrimitive.content.toLong(), ApprovalSlip.TTL_MAX_MS)
    assertEquals(v.getValue("skew_ms").jsonPrimitive.content.toLong(), ApprovalSlip.SKEW_MS)
  }

  @Test fun payload() {
    for (c in list("payload")) {
      val s = slipOf(c.getValue("slip"))
      assertNotNull(c.toString(), s)
      assertEquals(c.toString(), outOf(c), ApprovalSlip.payload(s!!))
    }
  }

  /** شکل: بردارهایی که برگه‌شان اصلاً به `Slip` درنمی‌آید، در JS هم «bad_slip» یا «bad_value»اند */
  @Test fun check() {
    for (c in list("check")) {
      val raw = c.getValue("slip")
      val s = if (raw is JsonNull || raw !is JsonObject) null else slipOf(raw)
      val want = outOf(c)
      if (s == null) {
        assertTrue(c.toString(), want == "bad_slip" || want == "bad_value")
        continue
      }
      assertEquals(c.toString(), want, ApprovalSlip.check(s))
    }
  }

  @Test fun covers() {
    for (c in list("covers")) {
      val s = slipOf(c.getValue("slip"))!!
      val n = PermPolicy.tree(c.getValue("need")) as Map<*, *>
      val need = ApprovalSlip.Need(
        perm = n["perm"] as String, doc = n["doc"] as String,
        pctBp = (n["pct_bp"] as Number?)?.toLong(), amount = (n["amount"] as Number?)?.toLong(),
        at = (n["at"] as Number?)?.toLong(),
      )
      assertEquals(c.toString(), outOf(c), ApprovalSlip.covers(s, need))
    }
  }

  /** امضای DERِ Node روی همان متن در JVM هم می‌خواند، و متنِ دست‌خورده نه */
  @Test fun serverSignaturesVerify() {
    val spki = testKey.getValue("spki_b64").jsonPrimitive.content
    assertEquals(testKey.getValue("key_id").jsonPrimitive.content, PinCrypto.keyId(b64(spki)))
    val payloads = list("payload")
    for (sig in list("signatures")) {
      val text = outOf(payloads[sig.getValue("payload").jsonPrimitive.content.toInt()])!!
      val der = sig.getValue("der_b64").jsonPrimitive.content
      assertTrue(verify(spki, text, der))
      assertFalse(verify(spki, text.replace("\nreason=", "\nreason=x"), der))
    }
  }

  @Test fun jvmSignatureUsesSameFormat() {
    val text = outOf(list("payload")[0])!!
    val sig = PinCrypto.sign(b64(testKey.getValue("pkcs8_b64").jsonPrimitive.content), text)
    assertEquals(0x30, b64(sig)[0].toInt() and 0xff)       //  DER، همان که سرور می‌خواند
    assertTrue(verify(testKey.getValue("spki_b64").jsonPrimitive.content, text, sig))
  }

  @Test fun discountNeedMatchesServer() {
    val n = ApprovalSlip.discountNeed("s-1", 2000, 10000, 5L)
    assertEquals(ApprovalSlip.Need("sales.discount", "sales/s-1", 1950, 2000, 5L), n)
  }

  @Test fun mapRoundTrip() {
    val s = slipOf(list("payload")[0].getValue("slip"))!!
    assertEquals(s, ApprovalSlip.fromMap(ApprovalSlip.toMap(s)))
    assertNull(ApprovalSlip.fromMap(mapOf("shop" to "x")))
    assertNull(ApprovalSlip.fromMap(ApprovalSlip.toMap(s) + ("iat" to 1.5)))
  }

  /* ------------------------------------------------------------- PIN */

  /** PBKDF2-HMAC-SHA256: بردارهای RFC 7914 (بخشِ ۱۱)، ۳۲ بایتِ اول */
  @Test fun pbkdf2Rfc7914() {
    assertEquals(
      "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc",
      PinCrypto.pinHash("passwd", "salt".toByteArray(), 1),
    )
    assertEquals(
      "4ddcd8f60b98be21830cee5ef22701f9641a4418d04c0414aeff08876b34ab56",
      PinCrypto.pinHash("Password", "NaCl".toByteArray(), 80000),
    )
  }

  @Test fun pinGuardShapeAndLock() {
    assertEquals("123456", PinGuard.normalize("۱۲۳۴۵۶"))
    assertEquals("123456", PinGuard.normalize("١٢٣٤٥٦"))
    assertTrue(PinGuard.isWellFormed("۴۸۲۹۱۷"))
    assertFalse(PinGuard.isWellFormed("12a4"))
    assertEquals("weak", PinGuard.newPinProblem("111111"))
    assertEquals("weak", PinGuard.newPinProblem("123456"))
    assertEquals("weak", PinGuard.newPinProblem("654321"))
    assertEquals("length", PinGuard.newPinProblem("4829"))
    assertNull(PinGuard.newPinProblem("482917"))
    var s = PinGuard.State()
    repeat(4) { s = PinGuard.after(s, false) }
    assertEquals(1, PinGuard.remaining(s))
    assertEquals(PinGuard.State(), PinGuard.after(s, true))
    s = PinGuard.after(s, false)
    assertTrue(s.locked)
    assertEquals(s, PinGuard.after(s, true))               //  قفل با PINِ درست هم باز نمی‌شود
  }

  @Test fun vaultApproveSignsAndCountsFailures() {
    val r0 = ApproverVault.create("usr_owner", "صاحب دکان", "owner", "482917", iterations = 1000)
    assertTrue(r0.hasKey)
    assertFalse(r0.check.contains("482917"))
    assertFalse(ApproverVault.encode(r0).contains("482917"))
    val need = ApprovalSlip.discountNeed("s-9", 2000, 10000)
    val draft = ApproverVault.draft(r0, "shp_x", need, "مشتریِ\nعمده", 1_760_000_000_000)
    assertEquals("مشتریِ عمده", draft.reason)

    val (r1, bad) = ApproverVault.approve(r0, "000000", draft)
    assertNull(bad)
    assertEquals(1, r1.failures)
    val (r2, slip) = ApproverVault.approve(r1, "۴۸۲۹۱۷", draft)
    assertNotNull(slip)
    assertEquals(0, r2.failures)
    assertNull(ApprovalSlip.check(slip!!))
    assertNull(ApprovalSlip.covers(slip, need.copy(at = draft.iat)))
    assertTrue(verify(r0.spki, ApprovalSlip.payload(slip), slip.sig))

    var r = r2
    repeat(5) { r = ApproverVault.approve(r, "111112", draft).first }
    assertTrue(r.locked)
    assertNull(ApproverVault.approve(r, "482917", draft).second)

    //  شاگرد کلید ندارد؛ فقط PIN برای تعویضِ کاربر
    val st = ApproverVault.create("usr_staff", "شاگرد", "staff", "730518", iterations = 1000)
    assertFalse(st.hasKey)
    assertTrue(ApproverVault.verify(st, "730518").ok)
    assertNull(ApproverVault.approve(st, "730518", draft).second)
  }

  @Test fun vaultEncodeRoundTrip() {
    val r = ApproverVault.create("usr_m", "مدیر = «علی»", "manager", "590382", iterations = 1000)
      .copy(registered = true, failures = 2)
    assertEquals(r, ApproverVault.decode(ApproverVault.encode(r)))
    assertNull(ApproverVault.decode(""))
    assertNull(ApproverVault.decode("userId=\niterations=1"))
  }
}
