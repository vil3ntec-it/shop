package ir.vil3ntec.tohid.platform

import android.content.Context
import ir.vil3ntec.tohid.core.config.ApiConfig
import ir.vil3ntec.tohid.core.config.AppConfig
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.server.ServerProfile
import ir.vil3ntec.tohid.core.server.ServerProfiles
import java.net.HttpURLConnection

/*
 *  جایگزینِ iOS ِ `platform/ServerSwitchAndroid.kt` (همان نام و API، چون `HttpEngine` و دیگران همین را
 *  صدا می‌زنند؛ مثلِ «replaced» ِ کامپیوتر).
 *
 *  «سرورِ خودِ دکان» (پروفایل‌های سرور با گواهیِ سنجاق‌شده، پرچمِ `app.multi_server`) روی آیفون هنوز
 *  خاموش است: سنجاقِ گواهی در iOS از راهِ نمایندهٔ NSURLSession است، نه `HttpsURLConnection` (گامِ ۳).
 *  پس رفتار **دقیقاً همان اندرویدِ پرچم‌خاموش** است: فقط سرورِ زمانِ ساخت، همان `ApiConfig.reject`،
 *  دامنهٔ دادهٔ خالی و کلیدهای امروز.
 */
object ServerSwitchAndroid {
  fun enabled(context: Context): Boolean = false

  fun profiles(context: Context): ServerProfiles = ServerProfiles.decode(null, AppConfig.buildTimeBaseUrl)

  fun activeProfile(context: Context): ServerProfile? = null

  fun activeUrl(context: Context): String? = null

  fun scope(context: Context): String = ""

  fun scopedKey(context: Context, raw: String): String = raw

  fun blocked(context: Context, id: String): Boolean = false

  /** همان شاخهٔ «بی پروفایل» ِ اندروید */
  fun reject(base: String, allowInsecure: Boolean): ApiFailure? =
    ApiConfig.reject(base, allowInsecure)?.let { reason ->
      if (reason == ApiConfig.Rejection.MISSING) ApiFailure.NotConfigured() else ApiFailure.BadConfiguration(reason.message)
    }

  /** بی پروفایل گواهیِ سنجاق‌شده‌ای نیست */
  fun prepare(connection: HttpURLConnection, base: String) {}

  fun reportFailure(base: String) {}

  fun applyPending(context: Context) {}

  fun restartNow(): Nothing = kotlin.system.exitProcess(0)

  data class LogEntry(val at: Long, val action: String, val name: String, val fingerprint: String, val note: String)

  fun logEntries(context: Context): List<LogEntry> = emptyList()
}
