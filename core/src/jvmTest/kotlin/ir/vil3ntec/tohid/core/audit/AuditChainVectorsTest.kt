package ir.vil3ntec.tohid.core.audit

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *  STF-09 — `core/test-vectors/audit-chain-v1.json` (ساختهٔ `server/scripts/audit-chain-vectors.js` با
 *  `lib/device-audit-chain.js`): Kotlin همان جزئیات، بدنه و هش را بایت‌به‌بایت می‌سازد و همان نتیجهٔ سنجش را
 *  می‌دهد (سالم، ویرایش، حذف، جابه‌جایی، انشعاب، بریدنِ دم با لنگر، پیشوندِ بایگانی، سطحِ تازه‌ترِ مدل).
 *  یک بایت فرق یعنی سرور سابقهٔ سالمِ گوشی را «دست‌خورده» می‌دید.
 */
class AuditChainVectorsTest {

  private val v: JsonObject by lazy {
    Json.parseToJsonElement(TestJson.repoFile("core/test-vectors/audit-chain-v1.json").readText(Charsets.UTF_8)).jsonObject
  }

  private fun rows(e: Any?): List<JsonObject> = (e as JsonArray).map { it.jsonObject }
  private fun str(o: JsonObject, k: String) = o.getValue(k).jsonPrimitive.content

  private fun anchors(o: JsonObject?): AuditChain.Anchors? = o?.let { a ->
    AuditChain.Anchors(
      str(a, "current"),
      a.getValue("heads").jsonObject.mapValues { (_, h) ->
        h.jsonObject.let { AuditChain.Head(str(it, "chain"), it.getValue("seq").jsonPrimitive.long, str(it, "hash")) }
      },
    )
  }

  private fun assertSummary(name: String, want: JsonObject, got: AuditChain.Report) {
    assertEquals(want.getValue("ok").jsonPrimitive.boolean, got.ok, "$name: ok")
    assertEquals(want.getValue("legacy").jsonPrimitive.long, got.legacy.toLong(), "$name: legacy")
    assertEquals(want.getValue("chained").jsonPrimitive.long, got.chained.toLong(), "$name: chained")
    assertEquals(want.getValue("unverified").jsonPrimitive.long, got.unverified.toLong(), "$name: unverified")
    val b = want["broken"] as? JsonObject
    assertEquals(
      b?.let { Triple(str(it, "chain"), it.getValue("seq").jsonPrimitive.long, str(it, "reason")) },
      got.broken?.let { Triple(it.chain, it.seq, it.reason) },
      "$name: broken",
    )
    assertEquals(
      want.getValue("chains").jsonArray.map { c -> c.jsonObject.let { listOf(str(it, "chain"), it.getValue("from").jsonPrimitive.long, it.getValue("seq").jsonPrimitive.long, str(it, "head"), it.getValue("rows").jsonPrimitive.long) } },
      got.chains.map { listOf(it.chain, it.from, it.seq, it.head, it.rows.toLong()) },
      "$name: chains",
    )
  }

  @Test
  fun detailDocAndHashAreByteForByteTheServers() {
    assertEquals(AuditChain.FORMAT, str(v, "format"))
    assertEquals(HashChain.FORMAT, str(v, "hashFormat"))
    for (c in v.getValue("cases").jsonArray.map { it.jsonObject }) {
      val row = c.getValue("row").jsonObject
      val id = str(row, "id")
      assertEquals(str(c, "detail"), HashChain.canonicalJson(AuditChain.detail(row)), "$id: detail")
      assertEquals(str(c, "detailHash"), AuditChain.detailHash(row), "$id: detailHash")
      assertEquals(str(c, "doc"), HashChain.canonicalJson(AuditChain.doc(row)), "$id: doc")
      assertEquals(str(c, "hash"), AuditChain.hashOf(row), "$id: hash")
    }
  }

  @Test
  fun stampAndAppendBuildTheSameRows() {
    val heads = HashMap<String, AuditChain.Head>()
    val built = v.getValue("plan").jsonArray.map { it.jsonObject }.map { p ->
      val dev = str(p, "device")
      val head = heads[dev] ?: AuditChain.resume(dev, emptyList(), null)
      val stamped = AuditChain.stamped(p.getValue("row").jsonObject, AuditChain.Stamp(str(p, "userId"), str(p, "userRole"), dev))
      val (row, next) = AuditChain.linked(stamped, head, (p["level"] as? JsonPrimitive)?.long ?: 1)
      heads[dev] = next
      row
    }
    val sealed = v.getValue("cases").jsonArray.map { it.jsonObject.getValue("row").jsonObject }
    assertEquals(sealed, built)
    assertEquals(v.getValue("chains").jsonArray.map { it.jsonPrimitive.content }, heads.values.map { it.chain }.sorted())
  }

  @Test
  fun verifyMatchesTheServerOnEveryCase() {
    assertSummary("rows", v.getValue("expect").jsonObject, AuditChain.verify(rows(v.getValue("rows"))))
    val all = v.getValue("tamper").jsonArray + v.getValue("levels").jsonArray
    assertTrue(all.size >= 20)
    for (t in all.map { it.jsonObject }) {
      val name = str(t, "name")
      val got = AuditChain.verify(
        rows(t.getValue("rows")),
        anchors = anchors(t["anchors"] as? JsonObject),
        strict = (t["strict"] as? JsonPrimitive)?.boolean ?: false,
        known = (t["known"] as? JsonPrimitive)?.long ?: 1,
      )
      assertSummary(name, t.getValue("expect").jsonObject, got)
    }
  }
}
