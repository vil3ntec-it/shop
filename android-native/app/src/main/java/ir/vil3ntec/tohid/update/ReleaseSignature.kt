package ir.vil3ntec.tohid.update

import ir.vil3ntec.tohid.sync.License
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 *  امضای جدای فهرستِ جمع‌های کنترلی (`SHA256SUMS.txt.sig`).
 *
 *  ── چرا ───────────────────────────────────────────────────────────
 *  نصبی، `version.txt` و `SHA256SUMS.txt` همه از یک انتشار می‌آیند؛ کسی
 *  که انتشار را عوض کند هر سه را با هم عوض می‌کند و جمعِ کنترلی چیزی
 *  نمی‌گیرد. امضای اندروید هم تا وقتی کلیدش در مخزن بوده، نگهبان نیست.
 *  این امضا با کلیدی زده می‌شود که فقط در secretِ CI است
 *  (`TOHID_UPDATE_SIGNING_KEY`) و کلیدِ عمومی‌اش داخلِ خودِ برنامه سنجاق
 *  شده (`BuildConfig.UPDATE_PUBLIC_KEY`). پس فهرستی که با این کلید امضا
 *  نشده باشد، نصب نمی‌شود — هر کس که انتشار را دست زده باشد.
 *
 *  ECDSA P-256 با SHA-256 (همان `openssl dgst -sha256 -sign`، امضای DER)؛
 *  روی اندروید ۷ و JVMِ کامپیوتر بی کتابخانهٔ اضافه کار می‌کند.
 *  ──────────────────────────────────────────────────────────────────
 */
object ReleaseSignature {

  /** کلیدِ سنجاق‌شده دارد؟ اگر نه، رفتارِ قبلی (فقط جمعِ کنترلی) می‌ماند. */
  fun pinned(key: String): Boolean = key.isNotBlank()

  /**
   *  @param text      متنِ دقیقِ `SHA256SUMS.txt`، همان بایت‌هایی که امضا شده
   *  @param signature امضای DER به base64 (محتوای `SHA256SUMS.txt.sig`)
   *  @param spki      کلیدِ عمومیِ P-256 به base64 (X.509 SubjectPublicKeyInfo)
   */
  fun verify(text: String, signature: String, spki: String): Boolean = runCatching {
    val key = KeyFactory.getInstance("EC")
      .generatePublic(X509EncodedKeySpec(License.decodeBase64(spki.trim())))
    Signature.getInstance("SHA256withECDSA").run {
      initVerify(key)
      update(text.toByteArray(Charsets.UTF_8))
      verify(License.decodeBase64(signature.trim()))
    }
  }.getOrDefault(false)
}
