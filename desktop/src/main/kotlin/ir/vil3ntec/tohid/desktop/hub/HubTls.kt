package ir.vil3ntec.tohid.desktop.hub

import java.io.ByteArrayInputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

/**
 *  SYN-09 — گواهیِ خودامضای هاب (EC P-256)، همان سنجاقِ `app.multi_server`: دستگاه SHA-256 ِ DER ِ همین
 *  گواهی را سنجاق می‌کند (`PinnedTls`) و هیچ مرجعِ عمومی‌ای در کار نیست.
 *
 *  ⛔ کلیدِ خصوصی هرگز باز روی دیسک نیست: با `seal` (در برنامه همان `SecretBox` ِ توکن‌ها، AES-GCM)
 *     نوشته می‌شود. گواهی عمومی است و باز می‌ماند. کلید و گواهیِ ناهم‌خوان یا خراب ⇒ جفتِ تازه (سنجاقِ
 *     دستگاه‌ها می‌شکند و باید دوباره جفت شوند؛ بی صدا به گواهیِ دیگر اعتماد نمی‌شود).
 *  ⚠️ بی کتابخانه: گواهی با DER ِ دست‌ساز ساخته و با `CertificateFactory` ِ خودِ JDK سنجیده می‌شود.
 */
internal class HubTls private constructor(val context: SSLContext, val certificate: X509Certificate) {

  /** همان قالبِ `ServerProfile.normalizeCertPin`: ۶۴ نویسهٔ هگزِ کوچک */
  val pin: String = sha256Hex(certificate.encoded)

  companion object {
    const val CERT_FILE = "hub-cert.der"
    const val KEY_FILE = "hub-key.sealed"
    private const val TEN_YEARS_MS = 10L * 365 * 24 * 3_600_000

    fun sha256Hex(der: ByteArray): String =
      MessageDigest.getInstance("SHA-256").digest(der).joinToString("") { b -> "%02x".format(b) }

    fun loadOrCreate(
      dir: File, seal: (String) -> String, open: (String) -> String?, now: Long = System.currentTimeMillis(),
    ): HubTls {
      dir.mkdirs()
      val certFile = File(dir, CERT_FILE)
      val keyFile = File(dir, KEY_FILE)
      val pair = runCatching { load(certFile, keyFile, open) }.getOrNull()
        ?: create(now).also { (cert, key) ->
          HubFiles.writeAtomic(keyFile, seal(Base64.getEncoder().encodeToString(key.encoded)).toByteArray(Charsets.UTF_8))
          HubFiles.writeAtomic(certFile, cert.encoded)
        }
      return HubTls(contextOf(pair.first, pair.second), pair.first)
    }

    private fun load(certFile: File, keyFile: File, open: (String) -> String?): Pair<X509Certificate, PrivateKey>? {
      if (!certFile.isFile || !keyFile.isFile) return null
      val cert = CertificateFactory.getInstance("X.509")
        .generateCertificate(ByteArrayInputStream(certFile.readBytes())) as X509Certificate
      val raw = open(keyFile.readText(Charsets.UTF_8)) ?: return null
      val key = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(raw)))
      //  کلید و گواهی از یک جفت‌اند؟
      val probe = "tohid-hub".toByteArray()
      val sig = Signature.getInstance("SHA256withECDSA").apply { initSign(key); update(probe) }.sign()
      val ok = Signature.getInstance("SHA256withECDSA").apply { initVerify(cert.publicKey); update(probe) }.verify(sig)
      return if (ok) cert to key else null
    }

    private fun create(now: Long): Pair<X509Certificate, PrivateKey> {
      val kp = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1"), SecureRandom()) }.generateKeyPair()
      val alg = Der.seq(Der.oid(ECDSA_SHA256))
      val name = Der.seq(Der.set(Der.seq(Der.oid(COMMON_NAME), Der.utf8("Tohid local hub"))))
      val serial = BigInteger(1, ByteArray(16).also { SecureRandom().nextBytes(it) }).setBit(120)
      val tbs = Der.seq(
        Der.explicit(0, Der.int(BigInteger.valueOf(2))),
        Der.int(serial),
        alg,
        name,
        Der.seq(Der.time(now - 24 * 3_600_000L), Der.time(now + TEN_YEARS_MS)),
        name,
        kp.public.encoded,
      )
      val sig = Signature.getInstance("SHA256withECDSA").apply { initSign(kp.private); update(tbs) }.sign()
      val der = Der.seq(tbs, alg, Der.bits(sig))
      val cert = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(der)) as X509Certificate
      cert.verify(kp.public)
      return cert to kp.private
    }

    private fun contextOf(cert: X509Certificate, key: PrivateKey): SSLContext {
      val pass = CharArray(24).also { a -> val r = SecureRandom(); for (i in a.indices) a[i] = 'a' + r.nextInt(26) }
      val ks = KeyStore.getInstance("PKCS12").apply { load(null, null); setKeyEntry("hub", key, pass, arrayOf(cert)) }
      val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(ks, pass) }
      return SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, null, SecureRandom()) }
    }

    /** 1.2.840.10045.4.3.2 */
    private val ECDSA_SHA256 = byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0xCE.toByte(), 0x3D, 0x04, 0x03, 0x02)
    /** 2.5.4.3 */
    private val COMMON_NAME = byteArrayOf(0x55, 0x04, 0x03)
  }

  /** کمینهٔ DER برای یک گواهیِ X.509 v3 ِ بی افزونه */
  private object Der {
    fun tlv(tag: Int, body: ByteArray): ByteArray {
      val n = body.size
      val len = when {
        n < 0x80 -> byteArrayOf(n.toByte())
        n < 0x100 -> byteArrayOf(0x81.toByte(), n.toByte())
        n < 0x10000 -> byteArrayOf(0x82.toByte(), (n shr 8).toByte(), n.toByte())
        else -> error("DER too long")
      }
      return byteArrayOf(tag.toByte()) + len + body
    }

    private fun cat(parts: Array<out ByteArray>) = parts.fold(ByteArray(0)) { a, b -> a + b }
    fun seq(vararg parts: ByteArray) = tlv(0x30, cat(parts))
    fun set(vararg parts: ByteArray) = tlv(0x31, cat(parts))
    fun int(v: BigInteger) = tlv(0x02, v.toByteArray())
    fun oid(body: ByteArray) = tlv(0x06, body)
    fun utf8(s: String) = tlv(0x0C, s.toByteArray(Charsets.UTF_8))
    fun bits(b: ByteArray) = tlv(0x03, byteArrayOf(0) + b)
    fun explicit(n: Int, body: ByteArray) = tlv(0xA0 + n, body)

    /** UTCTime تا ۲۰۴۹، پس از آن GeneralizedTime (RFC 5280 §4.1.2.5) */
    fun time(ms: Long): ByteArray {
      val t = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC)
      return if (t.year < 2050) {
        tlv(0x17, DateTimeFormatter.ofPattern("yyMMddHHmmss'Z'").format(t).toByteArray(Charsets.US_ASCII))
      } else {
        tlv(0x18, DateTimeFormatter.ofPattern("yyyyMMddHHmmss'Z'").format(t).toByteArray(Charsets.US_ASCII))
      }
    }
  }
}
