package ir.vil3ntec.tohid.core.audit

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull

/**
 * STF-09 — گزارشِ سرِ زنجیرهٔ سابقهٔ عملیاتِ همین دستگاه به سرور (لنگرِ سرور)، و حکمِ سرور برای صفحهٔ «سابقهٔ عملیات».
 *
 *   POST /api/sync/v1/audit-anchor  { device_id, format, heads: [{chain, seq, hash}] } ⇒ { enabled, acks, problems }
 *   GET  /api/sync/v1/audit-anchor  ⇒ { enabled, valid, broken, pending }
 * (`server/src/lib/device-audit-anchor.js`؛ جدولِ `device_audit_anchors`، مهاجرتِ ۴۱۰).
 *
 *  ── چه چیزی، کِی ─────────────────────────────────────────────────────
 *  سرِ هر زنجیرهٔ همین دستگاه از **لنگرِ محلی** ([AuditChain.Anchors]، پس از هر نوشتنِ دفتر، بیرون از پشتیبان) —
 *  نه از دفتر: دفترِ بریده سرِ واقعی را نمی‌گوید، لنگر می‌گوید؛ ردیفی هم که هنوز همگام نشده در آن هست، پس
 *  بریدنش روی سرور دیده می‌شود. پس از هر دورِ همگام‌سازی ([due]) فقط سرهایی که با «آخرین فرستاده» ([Sent]) فرق
 *  دارند، دورهٔ کهنه پیش از تازه (سرور ادامهٔ دورهٔ کهنه پس از تازه را «عقب‌گرد» می‌داند)، حداکثر [MAX_HEADS].
 *
 *  ── بی‌اینترنت و تکرار ─────────────────────────────────────────────────
 *  پاسخِ نرسیده یا پرچمِ خاموش ⇒ [Sent] همان می‌ماند و دورِ بعد همان سر (یا سرِ تازه‌تر) دوباره می‌رود ([after]).
 *  سرور همان (زنجیره، شماره، هش) را دوباره نمی‌نویسد. سری که سرور پذیرفت یا یک بار دید و مشکلش را گفت
 *  (`fork`، `rollback`، `bad_epoch`) «فرستاده» است و هر دور دوباره نمی‌رود. [Sent] ِ خراب ⇒ همه دوباره (بی‌ضرر).
 *
 *  ⛔ پشتِ همان پرچمِ خاموشِ `shop.doc_chain` (مرزِ سکو: `AuditAnchorSync`). Kotlin خالص (commonMain).
 */
object AuditAnchor {
  const val PATH = "/sync/v1/audit-anchor"
  /** همان سقفِ سرور */
  const val MAX_HEADS = 4

  /** آخرین سری که برای هر زنجیره فرستاده و پاسخ گرفته شد */
  data class Sent(val heads: Map<String, AuditChain.Head> = emptyMap()) {
    fun encode(): String = buildString {
      append("v=1\n")
      heads.values.sortedBy { it.chain }.forEach { append("sent=").append(it.chain).append('\t').append(it.seq).append('\t').append(it.hash).append('\n') }
    }

    companion object {
      /** متنِ [encode] ⇐ [Sent]؛ نبود یا خراب ⇐ تهی (همه دوباره می‌روند؛ سرور تکرار را نمی‌نویسد) */
      fun decode(text: String?): Sent {
        if (text.isNullOrBlank()) return Sent()
        val lines = text.split('\n')
        if (lines.firstOrNull() != "v=1") return Sent()
        val out = LinkedHashMap<String, AuditChain.Head>()
        for (l in lines.drop(1)) {
          if (l.isEmpty()) continue
          if (!l.startsWith("sent=")) return Sent()
          val p = l.removePrefix("sent=").split('\t')
          val seq = p.getOrNull(1)?.toLongOrNull()
          if (p.size != 3 || p[0].isEmpty() || seq == null || seq < 1 || !HashChain.isHash(p[2])) return Sent()
          out[p[0]] = AuditChain.Head(p[0], seq, p[2])
        }
        return Sent(out)
      }
    }
  }

  data class Problem(val chain: String, val seq: Long, val problem: String)

  /** پاسخِ `POST`؛ [enabled] = false ⇐ پرچمِ سرور خاموش (هیچ چیزی ننشست) */
  data class Reply(val enabled: Boolean, val acks: List<AuditChain.Head> = emptyList(), val problems: List<Problem> = emptyList())

  /** حکمِ سرور روی رونوشت و لنگرها (`GET`): [broken] نخستین شکست، [pending] ردیف‌های زیرِ لنگر که هنوز نرسیده‌اند */
  data class Verdict(val valid: Boolean, val broken: AuditChain.Break? = null, val pending: Long = 0)

  private fun deviceOf(chain: String) = chain.substringBeforeLast('/', "")
  private fun epochOf(chain: String) = chain.substringAfterLast('/', "").toLongOrNull() ?: 0L

  /** سرهای [deviceId] در [anchors] که با [sent] فرق دارند — دورهٔ کهنه اول، حداکثر [MAX_HEADS] ِ تازه‌تر */
  fun due(deviceId: String, anchors: AuditChain.Anchors?, sent: Sent): List<AuditChain.Head> {
    if (anchors == null || deviceId.isBlank()) return emptyList()
    return anchors.heads.values
      .filter { deviceOf(it.chain) == deviceId && epochOf(it.chain) >= 1 && it.seq >= 1 && HashChain.isHash(it.hash) }
      .filter { sent.heads[it.chain] != it }
      .sortedWith(compareBy({ epochOf(it.chain) }, { it.chain }))
      .takeLast(MAX_HEADS)
  }

  fun body(deviceId: String, heads: List<AuditChain.Head>): JsonObject = buildJsonObject {
    put("device_id", JsonPrimitive(deviceId))
    put("format", JsonPrimitive(AuditChain.FORMAT))
    put("heads", JsonArray(heads.map { h ->
      buildJsonObject {
        put("chain", JsonPrimitive(h.chain))
        put("seq", JsonPrimitive(h.seq))
        put("hash", JsonPrimitive(h.hash))
      }
    }))
  }

  private fun str(o: JsonObject, k: String): String = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
  private fun num(o: JsonObject, k: String): Long = (o[k] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull ?: 0L
  private fun objects(o: JsonObject, k: String): List<JsonObject> = (o[k] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

  fun reply(o: JsonObject): Reply = Reply(
    enabled = (o["enabled"] as? JsonPrimitive)?.booleanOrNull == true,
    acks = objects(o, "acks").map { AuditChain.Head(str(it, "chain"), num(it, "seq"), str(it, "hash")) },
    problems = objects(o, "problems").map { Problem(str(it, "chain"), num(it, "seq"), str(it, "problem")) },
  )

  /** پاسخِ `GET` ⇐ حکم؛ پرچمِ خاموش یا پاسخِ ناخوانا ⇐ null (فقط سنجشِ همین دستگاه) */
  fun verdict(o: JsonObject): Verdict? {
    if ((o["enabled"] as? JsonPrimitive)?.booleanOrNull != true) return null
    val valid = (o["valid"] as? JsonPrimitive)?.booleanOrNull ?: return null
    val b = o["broken"] as? JsonObject
    val broken = b?.let { AuditChain.Break(str(it, "chain"), num(it, "seq"), str(it, "reason"), str(it, "id").ifEmpty { null }) }
    return Verdict(valid && broken == null, broken, num(o, "pending"))
  }

  /**
   *  [Sent] پس از گزارشِ [heads]: [reply] ِ null (بی‌اینترنت، خطا) یا پرچمِ خاموش ⇐ همان [sent]؛ وگرنه هر سری که سرور
   *  پذیرفت (`acks`) یا مشکلش را گفت (`problems`) فرستاده است. سرِ بی پاسخ دورِ بعد دوباره می‌رود.
   */
  fun after(sent: Sent, heads: List<AuditChain.Head>, reply: Reply?): Sent {
    if (reply == null || !reply.enabled) return sent
    val out = LinkedHashMap(sent.heads)
    for (h in heads) {
      val answered = reply.acks.any { it == h } || reply.problems.any { it.chain == h.chain && it.seq == h.seq }
      if (answered) out[h.chain] = h
    }
    return if (out == sent.heads) sent else Sent(out)
  }
}
