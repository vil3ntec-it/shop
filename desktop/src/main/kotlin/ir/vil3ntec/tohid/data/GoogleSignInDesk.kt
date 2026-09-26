package ir.vil3ntec.tohid.data

import android.content.Context

/**
 *  ورود با گوگل روی کامپیوتر ساخته نشده: Credential Managerِ اندروید
 *  این‌جا نیست و راهِ مرورگری شناسهٔ کلاینتِ جدا (Desktop) می‌خواهد که
 *  سرور امروز نمی‌پذیرد. خودِ دکمه هم در برنامه خاموش است
 *  (`GOOGLE_LOGIN`)؛ اگر روزی روشن شد، این پیامِ روشن را می‌دهد.
 */
object GoogleSignIn {
  data class Account(val idToken: String, val email: String)

  suspend fun pick(context: Context, clientId: String): Account? =
    throw IllegalStateException("ورود با گوگل روی برنامهٔ کامپیوتر نیست — با ایمیل وارد شوید")
}
