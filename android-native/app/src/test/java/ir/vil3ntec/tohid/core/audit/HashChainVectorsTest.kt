package ir.vil3ntec.tohid.core.audit

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * SEC-02 / STF-09 / SEC-03 — همان بردارهای `license/test-vectors/hashchain-v1.json`
 * که سرور و وب هم می‌خوانند (`server/test/hash-chain-vectors.test.js`).
 * یک بایت فرق یعنی زنجیرهٔ ساخته‌شده روی گوشی، روی سرور «شکسته» دیده می‌شود.
 */
class HashChainVectorsTest {

  private val v: JsonObject by lazy {
    var dir: File? = File("").absoluteFile
    while (dir != null) {
      val f = File(dir, "license/test-vectors/hashchain-v1.json")
      if (f.isFile) return@lazy Json.parseToJsonElement(f.readText(Charsets.UTF_8)).jsonObject
      dir = dir.parentFile
    }
    fail("بردارهای hashchain-v1.json پیدا نشد (از ${File("").absolutePath})")
    throw IllegalStateException()
  }

  private fun JsonElement.str(key: String) = jsonObject.getValue(key).jsonPrimitive.content
  private fun JsonElement.arr(key: String) = jsonObject.getValue(key).jsonArray

  private fun codeOf(block: () -> Unit): String? =
    try { block(); null } catch (e: HashChainException) { e.code }

  @Test fun formatAndGenesis() {
    assertEquals(HashChain.FORMAT, v.str("format"))
    assertEquals(HashChain.GENESIS, v.getValue("rules").str("genesis"))
  }

  @Test fun canonicalJson() {
    for (c in v.arr("canonical")) {
      assertEquals(c.str("name"), c.str("output"), HashChain.canonicalJson(c.jsonObject.getValue("input")))
    }
    for (c in v.arr("invalid")) {
      assertEquals(c.str("name"), c.str("error"), codeOf { HashChain.canonicalJson(c.jsonObject.getValue("input")) })
    }
  }

  @Test fun numbersThatJsonParseTreatsAsIntegers() {
    assertEquals("[1,100,0,0]", HashChain.canonicalJson(Json.parseToJsonElement("[1.0,1e2,-0,-0.0]")))
    assertEquals("not_integer", codeOf { HashChain.canonicalJson(Json.parseToJsonElement("[9007199254740993]")) })
  }

  @Test fun entryHashAndChain() {
    for (c in v.arr("hash")) {
      val doc = c.jsonObject.getValue("doc")
      assertEquals(c.str("name"), c.str("canonical"), HashChain.canonicalJson(doc))
      assertEquals(c.str("name"), c.str("hash"), HashChain.entryHash(c.str("prev"), doc))
    }
    assertEquals("bad_prev", codeOf { HashChain.entryHash("ABC", JsonObject(emptyMap())) })

    val chain = v.getValue("chain")
    val built = mutableListOf<ChainEntry>()
    for (d in chain.arr("docs")) built += HashChain.append(built, d)
    val expected = chain.arr("entries")
    assertEquals(expected.size, built.size)
    expected.forEachIndexed { i, e ->
      assertEquals(e.jsonObject.getValue("seq").jsonPrimitive.long, built[i].seq)
      assertEquals(e.str("prevHash"), built[i].prevHash)
      assertEquals(e.str("hash"), built[i].hash)
    }
    assertEquals(chain.str("head"), built.last().hash)
  }

  private fun entryOf(e: JsonElement): ChainEntry {
    val o = e.jsonObject
    return ChainEntry(
      seq = o["seq"]?.jsonPrimitive?.long,
      prevHash = o.getValue("prevHash").jsonPrimitive.content,
      hash = o.getValue("hash").jsonPrimitive.content,
      doc = o.getValue("doc"),
    )
  }

  @Test fun verifyFindsFirstBrokenLink() {
    for (c in v.arr("verify")) {
      val name = c.str("name")
      val r = HashChain.verify(c.arr("entries").map(::entryOf))
      val ok = c.jsonObject.getValue("ok").jsonPrimitive.boolean
      assertEquals(name, ok, r.ok)
      if (ok) {
        assertEquals(name, c.str("head"), r.head)
        assertEquals(name, c.jsonObject.getValue("length").jsonPrimitive.int, r.length)
      } else {
        assertEquals(name, c.jsonObject.getValue("index").jsonPrimitive.int, r.index)
        assertEquals(name, c.str("reason"), r.reason)
      }
    }
  }

  @Test fun postedDocumentIsNeverEdited() {
    for (c in v.arr("guard")) {
      val o = c.jsonObject
      val before = o["before"]?.takeIf { it !is JsonNull }?.jsonObject
      val r = PostedDocument.guardEdit(before, o.getValue("after").jsonObject)
      val ok = o.getValue("ok").jsonPrimitive.boolean
      assertEquals(c.str("name"), ok, r.ok)
      if (!ok) assertEquals(c.str("name"), c.str("code"), r.code)
    }
  }

  @Test fun correctionOnlyByReversingDocument() {
    for (c in v.arr("reversal")) {
      val o = c.jsonObject
      val name = c.str("name")
      val opts = o.getValue("opts").jsonObject
      val s = { k: String -> (opts[k] as? JsonPrimitive)?.content ?: "" }
      val req = ReversalRequest(
        id = s("id"), no = opts["no"]?.jsonPrimitive?.long ?: 0L,
        postedAt = opts["postedAt"]?.jsonPrimitive?.long ?: 0L, reason = s("reason"), by = s("by"),
      )
      val existing = (o["existing"] as? JsonArray)?.map { it.jsonObject } ?: emptyList()
      val doc = o.getValue("doc").jsonObject
      val error = (o["error"] as? JsonPrimitive)?.content
      if (error != null) {
        assertEquals(name, error, codeOf { PostedDocument.reverse(doc, req, existing) })
        continue
      }
      val before = HashChain.canonicalJson(doc)
      val rev = PostedDocument.reverse(doc, req, existing)
      assertEquals(name, HashChain.canonicalJson(o.getValue("expected")), HashChain.canonicalJson(rev))
      assertEquals("سندِ اصلی دست نخورد", before, HashChain.canonicalJson(doc))
      assertTrue(PostedDocument.isPosted(rev))
      assertEquals(rev, PostedDocument.findReversal(listOf(doc, rev), doc.str("id")))
      assertFalse(PostedDocument.guardEdit(rev, doc).ok)
    }
  }
}
