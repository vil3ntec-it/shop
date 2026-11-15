package ir.vil3ntec.tohid.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

import ir.vil3ntec.tohid.core.i18n.StrAppData
import ir.vil3ntec.tohid.core.i18n.StrAuth
/**
 *  ورود با حسابِ گوگلِ خودِ گوشی.
 *
 *  هیچ رمزی رد و بدل نمی‌شود: گوشی یک توکنِ امضاشده می‌دهد و همان توکن
 *  می‌رود روی سرورِ خودِ کاربر تا آنجا سنجیده شود. کلیدِ محرمانه‌ای داخلِ
 *  برنامه نیست که با باز کردنِ فایلِ برنامه لو برود.
 *
 *  شناسهٔ کلاینت هم داخلِ برنامه نوشته نشده — سرور خودش آن را می‌گوید.
 *  پس هر کسی سرورِ خودش را دارد، با حسابِ گوگلِ خودش کار می‌کند.
 */
object GoogleSignIn {

  data class Account(val idToken: String, val email: String)

  /**
   *  حساب را از کاربر می‌پرسد.
   *
   *  `null` یعنی کاربر خودش پنجره را بست — این خطا نیست و نباید پیامِ
   *  قرمز بگیرد. هر چیزِ دیگری با پیامِ فارسی پرتاب می‌شود.
   */
  suspend fun pick(context: Context, clientId: String): Account? {
    if (clientId.isBlank()) throw IllegalStateException(StrAppData.googleOff)

    val option = GetGoogleIdOption.Builder()
      .setServerClientId(clientId)
      // حساب‌هایی که تا حالا وارد نشده‌اند هم نشان داده شوند، وگرنه بارِ
      // اول فهرست خالی است و کاربر فکر می‌کند خراب است
      .setFilterByAuthorizedAccounts(false)
      .setAutoSelectEnabled(false)
      .build()

    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

    val response = try {
      CredentialManager.create(context).getCredential(context, request)
    } catch (e: GetCredentialCancellationException) {
      return null
    } catch (e: NoCredentialException) {
      throw IllegalStateException(
        StrAppData.googleNoAccount
      )
    } catch (e: Exception) {
      /*
       *  ⛔ پیامِ خامِ گوگل (کدِ خطا، شناسهٔ کلاینت، SHA-1) دیگر روی صفحه نمی‌آید — جزئیاتِ ورود فنی است و به هر کاربری
       *  نشان داده می‌شد (`docs/RULES-fa.md`: «هیچ جزئیاتِ فنی/زیرساخت در صفحه»). تقریباً همیشه یکی از این دو است و
       *  کارِ صاحبِ سرور است، نه کاربر: شناسهٔ کلاینت با امضای این نسخه (SHA-1) جور نیست، یا شناسهٔ سرور از نوعِ «Web» نیست.
       */
      throw IllegalStateException(StrAuth.googleFailed + "\n" + StrAppData.googleHint, e)
    }

    val credential = response.credential
    if (credential !is CustomCredential ||
      credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
      throw IllegalStateException(StrAppData.googleBadAnswer)
    }

    val google = runCatching { GoogleIdTokenCredential.createFrom(credential.data) }
      .getOrElse { throw IllegalStateException(StrAppData.googleBadToken) }

    return Account(idToken = google.idToken, email = google.id)
  }
}
