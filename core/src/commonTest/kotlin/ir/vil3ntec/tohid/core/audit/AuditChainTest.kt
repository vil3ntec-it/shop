package ir.vil3ntec.tohid.core.audit

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  STF-09 — `AuditChain`: افزودن، سنجش، دست‌کاری، ردیفِ کهنهٔ بی مُهر، لنگر، ادامه پس از بستن و باز کردن،
 *  و «دورهٔ تازه» پس از دفترِ عقب‌رفته. بردارهای مشترک با سرور در `AuditChainVectorsTest` (jvmTest).
 */
class AuditChainTest {
  private val dev = "0123456789abcdef0123456789abcdef"
  private val stamp = AuditChain.Stamp("usr_1", "owner", dev)

  private fun row(id: String, notes: String = "", amount: Double = 0.0): JsonObject = buildJsonObject {
    put("id", id); put("type", "sale"); put("date", "2026-11-08"); put("notes", notes); put("createdAt", 1762600000000L); put("amount", amount)
  }

  /** همان کارِ برنامه: پسِ سرِ داده‌شده، ردیف‌ها به ترتیب */
  private fun append(head: AuditChain.Head, vararg rows: JsonObject): Pair<List<JsonObject>, AuditChain.Head> {
    var h = head
    val out = rows.map { r -> AuditChain.linked(AuditChain.stamped(r, stamp), h).also { h = it.second }.first }
    return out to h
  }

  private fun links(rows: List<JsonObject>) = rows.mapNotNull { AuditChain.linkOf(it) }

  @Test fun appendThenVerify() {
    val head = AuditChain.resume(dev, emptyList(), null)
    assertEquals("$dev/1", head.chain)
    assertEquals(0L, head.seq)
    val (rows, top) = append(head, row("a1"), row("a2", amount = 12.5), row("a3", notes = "یادداشت"))
    assertEquals(listOf(1L, 2L, 3L), links(rows).map { it.seq })
    assertEquals(HashChain.GENESIS, links(rows).first().prev)
    assertEquals(links(rows)[1].hash, links(rows)[2].prev)
    val r = AuditChain.verify(rows, AuditChain.Anchors().with(top))
    assertTrue(r.ok)
    assertEquals(3, r.chained)
    assertEquals(top.hash, r.chains.single().head)
    assertEquals(dev, (rows[0][AuditChain.DEVICE] as JsonPrimitive).content)
    assertEquals("owner", (rows[0][AuditChain.ROLE] as JsonPrimitive).content)
  }

  @Test fun tamperIsDetected() {
    val (rows, top) = append(AuditChain.resume(dev, emptyList(), null), row("a1"), row("a2"), row("a3"), row("a4"))
    val anchors = AuditChain.Anchors().with(top)
    fun broken(rs: List<JsonObject>) = AuditChain.verify(rs, anchors).broken?.let { it.seq to it.reason }
    //  ویرایش
    val edited = rows.toMutableList().also { it[1] = JsonObject(it[1] + ("notes" to JsonPrimitive("x"))) }
    assertEquals(2L to "hash_mismatch", broken(edited))
    //  حذف
    assertEquals(2L to "seq_gap", broken(rows.filterIndexed { i, _ -> i != 1 }))
    //  جابه‌جاییِ ترتیب (شماره‌ها)
    val swapped = rows.toMutableList().also {
      it[1] = JsonObject(it[1] + (AuditChain.SEQ to JsonPrimitive(3L)))
      it[2] = JsonObject(it[2] + (AuditChain.SEQ to JsonPrimitive(2L)))
    }
    assertEquals(2L to "prev_mismatch", broken(swapped))
    //  جای ردیف در فهرست (ترتیبِ رسیدن از همگام‌سازی) چیزی را نمی‌شکند
    assertNull(broken(rows.reversed()))
    //  بریدنِ دم: بی لنگر دیده نمی‌شود، با لنگر دیده می‌شود
    assertTrue(AuditChain.verify(rows.dropLast(1)).ok)
    assertEquals(4L to "truncated", broken(rows.dropLast(1)))
  }

  @Test fun oldRowsWithoutStampStayReadableAndOutsideTheChain() {
    val old = row("old1", notes = "پیش از مُهر")
    val (rows, _) = append(AuditChain.resume(dev, emptyList(), null), row("a1"))
    val r = AuditChain.verify(listOf(old) + rows)
    assertTrue(r.ok)
    assertEquals(1, r.legacy)
    assertEquals(1, r.chained)
    assertNull(AuditChain.linkOf(old))
    assertNull(old[AuditChain.USER])
  }

  @Test fun anchorsSurviveRestartAndResumeIsIdempotent() {
    val (rows, top) = append(AuditChain.resume(dev, emptyList(), null), row("a1"), row("a2"))
    val text = AuditChain.Anchors().with(top).encode()
    //  «بستن و باز کردن»: لنگر از متن، دفتر از دیسک
    val again = AuditChain.Anchors.decode(text)!!
    assertEquals(AuditChain.Anchors().with(top), again)
    val h1 = AuditChain.resume(dev, links(rows), again)
    val h2 = AuditChain.resume(dev, links(rows), again)
    assertEquals(top, h1)
    assertEquals(h1, h2)
    val (more, _) = append(h1, row("a3"))
    assertTrue(AuditChain.verify(rows + more, again.with(AuditChain.linkOf(more.single())!!.let { AuditChain.Head(it.chain, it.seq, it.hash) })).ok)
    //  لنگرِ خراب ⇐ همان «لنگر نیست»
    assertNull(AuditChain.Anchors.decode("v=1\nhead=x\t1\tnothex"))
    assertNull(AuditChain.Anchors.decode(""))
  }

  @Test fun anchorBehindTheLedgerIsCaughtUp() {
    //  نوشتنِ لنگر پس از دفتر نرسید (قطعِ برق): دفتر جلوتر است و همان زنجیره ادامه می‌یابد
    val (rows, top) = append(AuditChain.resume(dev, emptyList(), null), row("a1"), row("a2"))
    val lagging = AuditChain.Anchors().with(AuditChain.linkOf(rows[0])!!.let { AuditChain.Head(it.chain, it.seq, it.hash) })
    assertEquals(top, AuditChain.resume(dev, links(rows), lagging))
  }

  @Test fun brokenLedgerStartsANewEpochAndKeepsTheOldAsEvidence() {
    val (rows, top) = append(AuditChain.resume(dev, emptyList(), null), row("a1"), row("a2"), row("a3"))
    val anchors = AuditChain.Anchors().with(top)
    //  دفتر عقب رفت (بازیابیِ پشتیبانِ کهنه / پاک شدنِ دم)
    val restored = rows.take(1)
    val h = AuditChain.resume(dev, links(restored), anchors)
    assertEquals("$dev/2", h.chain)
    assertEquals(0L, h.seq)
    assertEquals(HashChain.GENESIS, h.hash)
    val (fresh, top2) = append(h, row("b1"))
    val next = anchors.with(top2)
    assertEquals("$dev/2", next.current)
    assertEquals(top, next.heads["$dev/1"])
    //  دورهٔ کهنه بریده دیده می‌شود، تازه سالم
    val r = AuditChain.verify(restored + fresh, next)
    assertFalse(r.ok)
    assertEquals(AuditChain.Break("$dev/1", 2, "truncated"), r.broken)
    assertNull(r.chains.single { it.chain == "$dev/2" }.broken)
    //  سرِ دست‌کاری‌شده (همان شماره، هشِ دیگر) هم دورهٔ تازه می‌گیرد
    val forged = AuditChain.Anchors().with(top.copy(hash = "f".repeat(64)))
    assertEquals("$dev/2", AuditChain.resume(dev, links(rows), forged).chain)
    //  زنجیرهٔ دستگاهِ دیگر هرگز ادامه داده نمی‌شود
    assertEquals("ffff/1", AuditChain.resume("ffff", links(rows), anchors).chain)
  }

  @Test fun numbersAndEmptiesAreCanonical() {
    //  ‎0.0‎ و نبودن یکی است؛ ‎100.0‎ همان ‎100‎؛ اعشاری با بیت‌های IEEE
    val a = row("x", amount = 0.0)
    val b = JsonObject(a - "amount" - "notes")
    assertEquals(AuditChain.doc(a), AuditChain.doc(b))
    assertEquals(100L, AuditChain.norm(JsonPrimitive(100.0)))
    assertEquals(mapOf("\$f" to "4029000000000000"), AuditChain.norm(JsonPrimitive(12.5)))
    assertEquals(1250L, AuditChain.amountMinor(row("x", amount = 12.5)))
    assertNotEquals(AuditChain.detailHash(row("x", notes = "a")), AuditChain.detailHash(row("x", notes = "b")))
  }
}
