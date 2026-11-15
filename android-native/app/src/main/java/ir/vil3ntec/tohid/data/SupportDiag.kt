package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.ops.CrashReport
import ir.vil3ntec.tohid.core.ops.CrashReports

/**
 *  جزئیاتِ فنیِ گرهٔ همگام‌سازی برای پشتیبانی — **فرستاده می‌شود، هرگز نشان داده نمی‌شود** (قاعدهٔ «هیچ جزئیاتِ
 *  فنی/زیرساخت در صفحه»، `docs/RULES-fa.md`).
 *
 *  همان راهِ گزارشِ خطای OPS-03 (`POST /api/errors`، صفِ `crash-reports.json`، `CrashReporter`) به کار می‌رود، بی درِ تازه
 *  روی سرور: گزارش با `shown = true` ساخته می‌شود تا برگهٔ «این کد را به پشتیبانی بگویید» ِ خطای برنامه برایش باز نشود.
 *
 *  پیش از نوشتن روی دیسک: همان پوشاندنِ OPS-03 (ایمیل، شماره، توکن، عددِ بلند) و **پوشاندنِ میزبان**
 *  (`api.vill3n.top` ⇒ `api.•••`، IP ⇒ `•••`) — پوشهٔ دادهٔ کامپیوتر دیدنی است و نشانیِ سرور نباید از آن بیرون بیاید.
 *  گزارش به همان سروری می‌رود که برنامه با آن کار می‌کند، پس پشتیبانی میزبان را خودش دارد.
 *
 *  منطقِ خالص؛ دیسک، ساعت و فرستادن در `platform/SupportDiagAndroid.kt`.
 */
object SupportDiag {

  /** همان گره در این بازه دوباره فرستاده نمی‌شود (هر بار باز کردنِ صفحه گزارشِ تازه نمی‌سازد) */
  const val REPEAT_MS: Long = 6L * 60 * 60 * 1000

  const val MASK = "•••"

  private val URL_HOST = Regex("""(?i)\b((?:https?|wss?)://)([^\s/:"'<>()\[\]]+)""")
  private val HOST_WORD = Regex("""(?i)\b(host\s*["'«]?)([a-z0-9-]+(?:\.[a-z0-9-]+)+)""")
  private val OUR_DOMAIN = Regex("""(?i)\b(?:[a-z0-9-]+\.)*vill3n\.top\b""")
  private val IPV4 = Regex("""(?<![\d.])(?:\d{1,3}\.){3}\d{1,3}(?![\d.])""")

  /** نامِ میزبان ⇒ فقط برچسبِ نخست («api.•••»)؛ IP ⇒ «•••» */
  private fun maskHost(host: String): String =
    if (IPV4.matches(host)) MASK else host.substringBefore('.') + "." + MASK

  /** هر میزبانِ نشانی، «host x»، دامنهٔ خودِ ما و هر IPv4 پوشانده می‌شود؛ بقیهٔ متن همان */
  fun maskHosts(text: String): String {
    var s = URL_HOST.replace(text) { m -> m.groupValues[1] + maskHost(m.groupValues[2]) }
    s = HOST_WORD.replace(s) { m -> m.groupValues[1] + maskHost(m.groupValues[2]) }
    s = OUR_DOMAIN.replace(s) { m -> if (m.value.endsWith(MASK)) m.value else maskHost(m.value) }
    s = IPV4.replace(s, MASK)
    return s
  }

  /** پوشاندنِ کامل: میزبان، سپس همان `CrashReports.scrub` */
  fun clean(text: String, limit: Int): String = CrashReports.scrub(maskHosts(text), limit)

  /** اثرِ گره: نوع و متنِ خام بی عدد (زمان و شمارنده دو گرهِ یکسان را دو تا نمی‌کنند) */
  fun signature(kind: String, detail: String): String {
    val stable = clean(detail, 160).replace(Regex("[0-9۰-۹٠-٩]+"), "#")
    return CrashReports.signatureOf("diag:$kind:$stable", "")
  }

  /** گزارشِ پشتیبانی: پیام = نوع و متنِ خام، Stack = جزئیاتِ فنیِ کنارش (`کلید=مقدار`)؛ همه پوشانده */
  fun report(kind: String, detail: String, facts: Map<String, String>, appVersion: String, nowMs: Long, id: String = CrashReports.newId(nowMs)): CrashReport {
    val lines = facts.entries.filter { it.value.isNotBlank() }.joinToString("\n") { "${it.key}=${it.value}" }
    return CrashReport(
      id = id, atMs = nowMs, appVersion = CrashReports.scrub(appVersion, 32),
      message = clean("diag:$kind — $detail", CrashReports.MAX_MESSAGE),
      stack = clean(lines, CrashReports.MAX_STACK),
      signature = signature(kind, detail),
      shown = true,
    )
  }

  /** همین گره باید فرستاده شود؟ اثرِ دیگر، یا همان اثر پس از [REPEAT_MS] (ساعتِ عقب‌رفته ⇒ دوباره) */
  fun due(lastSignature: String?, lastAtMs: Long, signature: String, nowMs: Long): Boolean =
    lastSignature != signature || nowMs - lastAtMs !in 0 until REPEAT_MS
}
