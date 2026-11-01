package ir.vil3ntec.tohid.core.audit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * SEC-02 / STF-09 / SEC-03 — همان بردارهای `license/test-vectors/hashchain-v1.json` که سرور و وب
 * هم می‌خوانند (`server/test/hash-chain-vectors.test.js`). یک بایت فرق یعنی زنجیرهٔ ساخته‌شده روی
 * گوشی، روی سرور «شکسته» دیده می‌شود. (تا ۱۴۰۵/۰۸/۰۸ در `android-native/.../core/audit` بود و
 * روی `JsonElement` می‌دوید؛ حالا روی درختِ سادهٔ commonMain.)
 */
class HashChainVectorsTest {

  @Suppress("UNCHECKED_CAST")
  private val v: Map<String, Any?> by lazy { TestJson.read("license/test-vectors/hashchain-v1.json") as Map<String, Any?> }

  @Suppress("UNCHECKED_CAST")
  private fun Any?.obj() = this as Map<String, Any?>
  private fun Any?.list() = this as List<*>
  private fun Any?.str(key: String) = obj()[key] as String
  private fun Any?.arr(key: String) = obj()[key].list()

  private fun codeOf(block: () -> Unit): String? =
    try { block(); null } catch (e: HashChainException) { e.code }

  @Test
  fun formatAndGenesis() {
    assertEquals(HashChain.FORMAT, v["format"])
    assertEquals(HashChain.GENESIS, v["rules"].str("genesis"))
  }

  @Test
  fun canonicalJson() {
    for (c in v.arr("canonical")) {
      assertEquals(c.str("output"), HashChain.canonicalJson(c.obj()["input"]), c.str("name"))
    }
    for (c in v.arr("invalid")) {
      assertEquals(c.str("error"), codeOf { HashChain.canonicalJson(c.obj()["input"]) }, c.str("name"))
    }
  }

  @Test
  fun numbersThatJsonParseTreatsAsIntegers() {
    assertEquals("[1,100,0,0]", HashChain.canonicalJson(TestJson.parse("[1.0,1e2,-0,-0.0]")))
    assertEquals("not_integer", codeOf { HashChain.canonicalJson(TestJson.parse("[9007199254740993]")) })
    assertEquals("bad_value", codeOf { HashChain.canonicalJson(mapOf(1 to 2)) })
    assertEquals("bad_value", codeOf { HashChain.canonicalJson(listOf(Any())) })
  }

  @Test
  fun entryHashAndChain() {
    for (c in v.arr("hash")) {
      val doc = c.obj()["doc"]
      assertEquals(c.str("canonical"), HashChain.canonicalJson(doc), c.str("name"))
      assertEquals(c.str("hash"), HashChain.entryHash(c.str("prev"), doc), c.str("name"))
    }
    assertEquals("bad_prev", codeOf { HashChain.entryHash("ABC", emptyMap<String, Any?>()) })

    val chain = v["chain"]
    val built = mutableListOf<ChainEntry>()
    for (d in chain.arr("docs")) built += HashChain.append(built, d)
    val expected = chain.arr("entries")
    assertEquals(expected.size, built.size)
    expected.forEachIndexed { i, e ->
      assertEquals(e.obj()["seq"], built[i].seq)
      assertEquals(e.str("prevHash"), built[i].prevHash)
      assertEquals(e.str("hash"), built[i].hash)
    }
    assertEquals(chain.str("head"), built.last().hash)
  }

  private fun entryOf(e: Any?): ChainEntry {
    val o = e.obj()
    return ChainEntry(seq = o["seq"] as Long?, prevHash = o["prevHash"] as String, hash = o["hash"] as String, doc = o["doc"])
  }

  @Test
  fun verifyFindsFirstBrokenLink() {
    for (c in v.arr("verify")) {
      val name = c.str("name")
      val r = HashChain.verify(c.arr("entries").map(::entryOf))
      val ok = c.obj()["ok"] as Boolean
      assertEquals(ok, r.ok, name)
      if (ok) {
        assertEquals(c.str("head"), r.head, name)
        assertEquals((c.obj()["length"] as Long).toInt(), r.length, name)
      } else {
        assertEquals((c.obj()["index"] as Long).toInt(), r.index, name)
        assertEquals(c.str("reason"), r.reason, name)
      }
    }
  }

  @Test
  fun postedDocumentIsNeverEdited() {
    for (c in v.arr("guard")) {
      val o = c.obj()
      @Suppress("UNCHECKED_CAST")
      val r = PostedDocument.guardEdit(o["before"] as Map<String, Any?>?, o["after"].obj())
      val ok = o["ok"] as Boolean
      assertEquals(ok, r.ok, c.str("name"))
      if (!ok) assertEquals(c.str("code"), r.code, c.str("name"))
    }
  }

  @Test
  fun correctionOnlyByReversingDocument() {
    for (c in v.arr("reversal")) {
      val o = c.obj()
      val name = c.str("name")
      val opts = o["opts"].obj()
      val s = { k: String -> opts[k] as? String ?: "" }
      val req = ReversalRequest(
        id = s("id"), no = opts["no"] as? Long ?: 0L,
        postedAt = opts["postedAt"] as? Long ?: 0L, reason = s("reason"), by = s("by"),
      )
      val existing = (o["existing"] as? List<*>)?.map { it.obj() } ?: emptyList()
      val doc = o["doc"].obj()
      val error = o["error"] as? String
      if (error != null) {
        assertEquals(error, codeOf { PostedDocument.reverse(doc, req, existing) }, name)
        continue
      }
      val before = HashChain.canonicalJson(doc)
      val rev = PostedDocument.reverse(doc, req, existing)
      assertEquals(HashChain.canonicalJson(o["expected"]), HashChain.canonicalJson(rev), name)
      assertEquals(before, HashChain.canonicalJson(doc), "سندِ اصلی دست نخورد")
      assertTrue(PostedDocument.isPosted(rev))
      assertEquals(rev, PostedDocument.findReversal(listOf(doc, rev), doc["id"] as String))
      assertFalse(PostedDocument.guardEdit(rev, doc).ok)
    }
  }
}
