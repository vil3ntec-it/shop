package ir.vil3ntec.tohid.ios

import android.content.IosContext
import android.net.Uri
import androidx.security.crypto.KeychainPreferences
import ir.vil3ntec.tohid.data.FileName
import ir.vil3ntec.tohid.data.SignInProvider
import ir.vil3ntec.tohid.sync.Integrity
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *  گامِ ۶ ِ آیفون روی شبیه‌ساز (کارِ `ios-app`؛ روی لینوکس فقط کامپایل می‌شود): ورود با اپل (nonce ِ هم‌شکلِ سرور) و
 *  **خودِ** فایل‌های اندروید که جایگزینِ iOS شان حذف شد — `FileName`، `Integrity` و پروندهٔ «رمزدار» ِ `TokenStore`.
 */
class Phase6IosTest {
  @Test
  fun appleNonceIsHashedLikeTheServerAndEmailComesFromTheToken() {
    //  همان `nonceHash` ِ `server/src/lib/apple.js` (SHA-256 ِ هگزِ کوچک)
    assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", AppleSignInIos.sha256Hex("abc"))
    assertEquals("apple_link_required", SignInProvider.APPLE.linkRequiredCode)
    assertEquals("google_link_required", SignInProvider.GOOGLE.linkRequiredCode)

    //  base64url ِ بی «=» ِ {"iss":"https://appleid.apple.com","email":"x7@privaterelay.appleid.com"}
    val payload = "eyJpc3MiOiJodHRwczovL2FwcGxlaWQuYXBwbGUuY29tIiwiZW1haWwiOiJ4N0Bwcml2YXRlcmVsYXkuYXBwbGVpZC5jb20ifQ"
    assertEquals("x7@privaterelay.appleid.com", AppleSignInIos.emailOf("e30.$payload.sig"))
    assertEquals("", AppleSignInIos.emailOf("not-a-token"))
  }

  @Test
  fun androidFileNameReadsTheDisplayNameOfAFileUri() {
    val f = File(IosContext.cacheDir, "۱۲۳۴ عکسِ کالا.jpg")
    assertEquals("۱۲۳۴ عکسِ کالا.jpg", FileName.of(IosContext, Uri.fromFile(f)))
    //  نشانیِ غیرِ پرونده ⇐ همان «تکهٔ آخرِ نشانی» ِ اندروید
    assertEquals("z.jpg", FileName.of(IosContext, Uri.parse("content://media/y/z.jpg")))
  }

  @Test
  fun androidIntegrityHasNoApkSignatureAndStaysGenuine() {
    assertEquals("", Integrity.fingerprint(IosContext))
    assertTrue(Integrity.isGenuine(IosContext))
  }

  @Test
  fun encryptedPreferencesCodecKeepsEveryType() {
    val map = linkedMapOf<String, Any>(
      "access_token" to "tok.\"quoted\"",
      "access_expires_at" to 1_799_999_999_123L,
      "ratio" to 1.5f,
      "migrated" to true,
      "set" to setOf("a", "ب"),
      "empty" to "",
    )
    val back = KeychainPreferences.decode(KeychainPreferences.encode(map))
    assertEquals(map, back)
    assertEquals(1_799_999_999_123L, back["access_expires_at"])
  }
}
