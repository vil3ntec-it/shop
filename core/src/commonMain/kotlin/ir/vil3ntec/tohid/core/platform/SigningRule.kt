package ir.vil3ntec.tohid.core.platform

/**
 *  F0-09 گامِ ۳ (`docs/spec/OWNER-ACTIONS-fa.md` §۲) — پیش از چرخشِ کلیدِ امضای اندروید (APK Signature v3، `apksigner rotate`).
 *
 *  `BuildConfig.SIGNING_SHA256` از این نسخه **فهرست** است: اثرِ انگشتِ SHA-256 ِ کلیدِ ساخت و هر کلیدِ دیگرِ پذیرفته
 *  (`TOHID_SIGNING_SHA256_EXTRA` ِ ساخت، جدا با «,»). برنامه **امضای فعلیِ** خودش را می‌سنجد:
 *    • اندروید ۹ به بالا: آخرین گواهیِ `signingCertificateHistory` (تاریخچه از قدیم به تازه است) یا `apkContentsSigners` ِ چندامضایی؛
 *    • اندروید ۷ و ۸: همان امضای v2 (کلیدِ قدیم، چون چرخش از API 28 است).
 *  پس پس از چرخش، با فهرستِ «قدیم، تازه» هم گوشیِ قدیمی و هم تازه «همان برنامه» می‌مانند و اشتراک بسته نمی‌شود؛ بی چرخش رفتار همان امروز.
 *
 *  ⛔ فهرستِ خالی ⇒ بررسی خاموش (نسخهٔ آزمایشی، همان امروز). هر مقدارِ بدشکل در فهرست نادیده — نه «همه پذیرفته».
 */
object SigningRule {
  private val HEX64 = Regex("^[0-9a-f]{64}$")

  /** اثرِ انگشت‌های پذیرفته از متنِ ساخت (فاصله، «:» ِ خروجیِ keytool و بزرگی/کوچکیِ حروف بی‌اثر) */
  fun allowed(raw: String): Set<String> =
    raw.split(',', ';', '\n').map { normalize(it) }.filter { HEX64.matches(it) }.toSet()

  fun normalize(fp: String): String = fp.trim().replace(":", "").replace(" ", "").lowercase()

  /** بررسیِ خاموش (ساختِ بی کلید یا آزمایشی) */
  fun off(raw: String): Boolean = raw.isBlank()

  /**
   *  آیا امضای فعلی پذیرفته است. `raw` ِ ناتهی که هیچ اثرِ انگشتِ درستی ندارد ⇒ نه (ساختِ خراب نباید بررسی را خاموش کند).
   *  `current` خالی (خواندنِ امضا نشد) ⇒ نه.
   */
  fun genuine(raw: String, current: String): Boolean {
    if (off(raw)) return true
    val c = normalize(current)
    return HEX64.matches(c) && c in allowed(raw)
  }
}
