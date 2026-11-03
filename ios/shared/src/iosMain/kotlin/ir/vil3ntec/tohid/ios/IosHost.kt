package ir.vil3ntec.tohid.ios

import platform.Foundation.NSBundle
import platform.Foundation.NSURL

/**
 *  فرآیندِ میزبان — آیا درونِ بستهٔ برنامهٔ آیفون (`iosApp.app`) اجرا می‌شویم، یا در فرآیندِ بی بسته (آزمونِ
 *  شبیه‌ساز، `test.kexe` ِ `iosSimulatorArm64Test`).
 *
 *  چند سرویسِ iOS فقط برای برنامهٔ ثبت‌شده هستند: `UNUserNotificationCenter` در فرآیندِ بی بسته با
 *  «bundleProxyForCurrentProcess is nil» می‌ایستد (استثنای ObjC، نه خطای Kotlin) و بازخوردِ لمسی بی UIApplication
 *  معنا ندارد. آن‌جا همان رفتارِ «اجازه نیست / بی‌صدا» ِ گوشیِ بی اجازه را می‌گیرند (گامِ ۸، §۰.۱۳).
 */
internal object IosHost {
  val inApp: Boolean by lazy { NSBundle.mainBundle.bundlePath.endsWith(".app") }
}

/**
 *  نشانیِ شبکه در آزمونِ سرتاسریِ شبیه‌ساز (گامِ ۸، docs/IOS-PLAN-fa.md §۰.۱۳) — فقط آزمون می‌گذارد؛ در برنامه
 *  همیشه null و هر درخواست همان نشانیِ خودش است.
 *
 *  برنامه همان نشانیِ زمانِ ساخت (`BuildConfig.API_BASE`، https) را می‌بیند و همهٔ قاعده‌هایش (https ِ اجباری،
 *  سرورِ پیش‌فرض، سنجاقِ گواهی) دست نمی‌خورد؛ فقط لایهٔ حمل — NSURLSession ِ `java.net` (`Http.kt`) و سوکتِ زندهٔ
 *  `okhttp3` (`WebSocket.kt`) — درخواستِ همان میزبان را به سرورِ آزمونِ روی همین مک (http، فقط loopback) می‌برد؛
 *  مثلِ سطری در فایلِ hosts. هیچ راهی از برنامه یا کاربر به این‌جا نیست.
 */
internal object IosNetRoute {
  @kotlin.concurrent.Volatile
  private var rule: NetRule? = null

  /**
   *  `base` (مثلاً `https://api.example.com`) ⇐ `local` (`http://127.0.0.1:4000`، فقط loopback). تا پایانِ فرآیند می‌ماند:
   *  کارهای پس‌زمینهٔ برنامه (همگام‌سازی، سوکت) پس از آزمون هم هرگز به سرورِ واقعی نمی‌روند.
   */
  fun toLocal(base: String, local: String) {
    rule = NetRule.of(base, local)
  }

  fun of(spec: String): String = rule?.of(spec) ?: spec

  fun url(ns: NSURL): NSURL {
    val r = rule ?: return ns
    val spec = ns.absoluteString ?: return ns
    val routed = r.of(spec)
    return if (routed == spec) ns else NSURL.URLWithString(routed) ?: ns
  }
}

/** قاعدهٔ `IosNetRoute`: نشانیِ همان میزبان (https/wss، درگاهِ پیش‌فرض) ⇐ همان مسیر و پرسش روی سرورِ آزمون (http/ws) */
internal class NetRule private constructor(private val host: String, private val local: String) {
  fun of(spec: String): String {
    val scheme = spec.substringBefore("://", "").lowercase()
    if (scheme.isEmpty()) return spec
    val rest = spec.substringAfter("://")
    val authorityRaw = rest.substringBefore('/').substringBefore('?')
    val authority = authorityRaw.lowercase()
    if (authority != host && authority != "$host:443") return spec
    val plain = when (scheme) { "https" -> "http"; "wss" -> "ws"; else -> scheme }
    return "$plain://$local" + rest.substring(authorityRaw.length)
  }

  companion object {
    fun of(base: String, local: String): NetRule {
      require(local.startsWith("http://127.0.0.1:") || local.startsWith("http://localhost:")) { "فقط سرورِ آزمونِ همین مک: $local" }
      val host = base.substringAfter("://").substringBefore('/').lowercase()
      require(host.isNotEmpty()) { "نشانیِ برنامه خالی است" }
      return NetRule(host, local.substringAfter("://").trimEnd('/'))
    }
  }
}
