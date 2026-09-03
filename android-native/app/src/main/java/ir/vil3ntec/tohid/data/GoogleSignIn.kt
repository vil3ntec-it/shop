package ir.vil3ntec.tohid.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

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
    if (clientId.isBlank()) throw IllegalStateException("ورود با گوگل روی این سرور روشن نیست")

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
        "روی این گوشی حساب گوگلی نیست، یا گوگل حسابی برای این برنامه پیدا نکرد — " +
          "از تنظیماتِ گوشی یک حساب گوگل اضافه کنید و دوباره بزنید"
      )
    } catch (e: Exception) {
      /*
       *  پیامِ خامِ گوگل نگه داشته می‌شود، نه اینکه با یک جملهٔ کلی
       *  پاک شود.
       *
       *  گزارش شد «ورود با گوگل کار نمی‌کند» و همین جمله‌ی کلی روی صفحه
       *  بود؛ از رویش معلوم نمی‌شد اشکال از گوشی است یا از تنظیماتِ
       *  سرور. تقریباً همیشه یکی از این دو است:
       *   • شناسهٔ کلاینت با امضای این نسخهٔ برنامه (SHA-1) جور نیست
       *   • شناسه‌ای که سرور می‌دهد از نوعِ «Web» نیست
       *  هر دو را از متنِ خودِ گوگل می‌شود فهمید.
       */
      val raw = e.message.orEmpty().take(160)
      throw IllegalStateException(
        buildString {
          append("ورود با گوگل انجام نشد")
          if (raw.isNotBlank()) append(" — ").append(raw)
          append("\nاگر تکرار شد: شناسهٔ کلاینتِ سرور باید از نوعِ Web باشد و ")
          append("امضای SHA-1 این برنامه در Google Cloud ثبت شده باشد.")
        }
      )
    }

    val credential = response.credential
    if (credential !is CustomCredential ||
      credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
      throw IllegalStateException("پاسخِ گوگل شناخته نشد")
    }

    val google = runCatching { GoogleIdTokenCredential.createFrom(credential.data) }
      .getOrElse { throw IllegalStateException("توکنِ گوگل خوانده نشد") }

    return Account(idToken = google.idToken, email = google.id)
  }
}
