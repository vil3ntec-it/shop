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

/**
 *  همان `ServersController` ِ اندروید برای صفحهٔ «سرورها»؛ در آیفون فقط می‌خواند و همیشه می‌گوید «این‌جا نه»:
 *  `canManage` نه، `test` ⇐ «روی آیفون هنوز نیست»، افزودن/حذف/جابه‌جایی ⇐ همان پیام. (گامِ ۳: سنجاقِ گواهی.)
 */
class ServersController(context: Context) {
  private val app = context.applicationContext

  val canManage: Boolean get() = false
  val pinRequired: Boolean get() = false
  fun pinOk(pin: String): Boolean = false

  fun enabled(): Boolean = ServerSwitchAndroid.enabled(app)
  fun profiles(): ServerProfiles = ServerSwitchAndroid.profiles(app)
  fun blocked(id: String): Boolean = false
  fun log(): List<ServerSwitchAndroid.LogEntry> = emptyList()
  fun fingerprintOf(p: ServerProfile): String = p.serverId?.let { ir.vil3ntec.tohid.core.server.ServerIdentity.fingerprint(it) }.orEmpty()
  fun setEnabled(on: Boolean): Boolean = false

  data class Candidate(
    val urls: List<String>,
    val name: String,
    val info: ir.vil3ntec.tohid.core.server.ServerInfo,
    val lan: Boolean,
    val certPins: Map<String, String>,
    val alternateOf: ServerProfile?,
    val newCertFingerprint: String?,
  ) {
    val fingerprint: String get() = info.fingerprint
  }

  sealed class Test {
    data class Ready(val candidate: Candidate) : Test()
    data class Failed(val message: String) : Test()
    data class NeedsCertTrust(val url: String, val fingerprint: String) : Test()
  }

  suspend fun test(input: String, lan: Boolean, trustCert: String? = null): Test = Test.Failed(NOT_YET)
  fun add(c: Candidate): String? = NOT_YET
  fun remove(id: String): String? = NOT_YET
  fun switchTo(id: String): String? = NOT_YET
  fun restartNow(): Nothing = ServerSwitchAndroid.restartNow()

  private companion object {
    const val NOT_YET = "سرورِ خودِ دکان روی آیفون هنوز نیست"
  }
}
