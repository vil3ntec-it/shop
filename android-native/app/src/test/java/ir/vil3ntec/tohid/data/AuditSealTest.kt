package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.audit.AuditChain
import ir.vil3ntec.tohid.core.audit.HashChain
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 *  STF-09 — `AuditSeal`: هر ردیفِ تازهٔ سابقه کاربر، نقش و دستگاهِ همان لحظه را می‌گیرد و (با پرچمِ
 *  `shop.doc_chain`) حلقهٔ بعدیِ زنجیرهٔ همین دستگاه؛ مُهر و حلقهٔ ردیفِ موجود عوض نمی‌شود؛ تلاشِ دوباره،
 *  بستن و باز کردن و افزودنِ هم‌زمان زنجیره را نمی‌شکند؛ بازیابی و ردیفِ سرور مُهر نمی‌خورند.
 */
class AuditSealTest {
  private val dev = "0123456789abcdef0123456789abcdef"

  private class Mem : AuditSeal.AnchorStore {
    @Volatile var text: String? = null
    var writes = 0
    override fun read() = AuditChain.Anchors.decode(text)
    override fun write(anchors: AuditChain.Anchors) { text = anchors.encode(); writes++ }
  }

  private fun session(chain: Boolean, user: String = "usr_1", role: String = "cashier") =
    { AuditSeal.Session(AuditChain.Stamp(user, role, dev), chain) }

  private fun a(id: String, notes: String = "", amount: Double = 0.0) =
    AuditEntry(id, type = "sale", date = "2026-11-08", notes = notes, createdAt = 1762600000000, amount = amount)

  private fun add(d: ShopData, vararg rows: AuditEntry) = d.copy(auditLog = d.auditLog + rows)

  /** همان نوشتنِ `ShopStore`: نقشه روی دفترِ همان لحظه، نوشتن، لنگر پس از نوشتن */
  private fun write(disk: ShopData, next: ShopData, store: Mem, chain: Boolean = true, base: ShopData = disk): ShopData {
    val plan = AuditSeal.plan(disk, base, next, session(chain), store)
    plan.commit()
    return plan.data
  }

  @Test fun freshRowGetsUserRoleAndDeviceEvenWithChainOff() {
    val store = Mem()
    val out = write(ShopData(), add(ShopData(), a("a1")), store, chain = false)
    val r = out.auditLog.single()
    assertEquals("usr_1", r.userId)
    assertEquals("cashier", r.userRole)
    assertEquals(dev, r.deviceId)
    assertNull(r.hash)
    assertNull(r.chain)
    assertEquals(0, store.writes)
  }

  @Test fun chainOnLinksAndVerifies() {
    val store = Mem()
    var d = write(ShopData(), add(ShopData(), a("a1"), a("a2", amount = 12.5)), store)
    d = write(d, add(d, a("a3", notes = "یادداشت")), store)
    assertEquals(listOf(1L, 2L, 3L), d.auditLog.map { it.chainSeq })
    assertEquals(setOf("$dev/1"), d.auditLog.mapTo(HashSet()) { it.chain })
    assertEquals(HashChain.GENESIS, d.auditLog[0].prev)
    assertEquals(d.auditLog[1].hash, d.auditLog[2].prev)
    val anchors = store.read()!!
    assertEquals(AuditChain.Head("$dev/1", 3, d.auditLog[2].hash!!), anchors.heads["$dev/1"])
    assertTrue(AuditSeal.verify(d.auditLog, anchors).ok)
    //  دست‌کاریِ دفترِ دیسک: ویرایش، حذف، بریدنِ دم
    val edited = d.auditLog.toMutableList().also { it[1] = it[1].copy(notes = "x") }
    assertEquals(2L to "hash_mismatch", AuditSeal.verify(edited, anchors).broken!!.let { it.seq to it.reason })
    assertEquals(2L to "seq_gap", AuditSeal.verify(d.auditLog.filter { it.id != "a2" }, anchors).broken!!.let { it.seq to it.reason })
    assertEquals(3L to "truncated", AuditSeal.verify(d.auditLog.dropLast(1), anchors).broken!!.let { it.seq to it.reason })
  }

  @Test fun sealIsImmutableAndOldRowsAreNeverStamped() {
    val store = Mem()
    val legacy = a("old")   //  ردیفِ نسخهٔ پیشین/وب/سرور: بی مُهر
    val disk = write(ShopData(auditLog = listOf(legacy)), add(ShopData(auditLog = listOf(legacy)), a("a1")), store)
    //  صفحه می‌کوشد مُهرِ ردیفِ موجود را عوض کند و به ردیفِ کهنه مُهر بزند
    val forged = disk.copy(auditLog = disk.auditLog.map {
      if (it.id == "a1") it.copy(userId = "usr_boss", userRole = "owner", deviceId = "other", hash = "f".repeat(64))
      else it.copy(userId = "usr_boss", deviceId = dev)
    })
    val out = write(disk, forged, store)
    assertEquals(disk.auditLog, out.auditLog)
    assertNull(out.auditLog.first { it.id == "old" }.userId)
    assertEquals("usr_1", out.auditLog.first { it.id == "a1" }.userId)
  }

  @Test fun retriedSaveDoesNotRelinkTheSameRow() {
    val store = Mem()
    val base = ShopData()
    val next = add(base, a("a1"))
    val first = write(base, next, store)
    //  پاسخ گم شد و صفحه همان [next] را از همان پایه دوباره می‌فرستد؛ دیسک [first] را دارد
    val again = write(first, next, store, base = base)
    assertEquals(first.auditLog, again.auditLog)
    assertEquals(1L, again.auditLog.single().chainSeq)
    //  نوشتنی که سابقه را دست نمی‌زند همان شیء را برمی‌گرداند (بی هزینه)
    val noAudit = again.copy(expenseCategories = again.expenseCategories + "x")
    assertSame(noAudit, AuditSeal.plan(again, again, noAudit, session(true), store).data)
  }

  @Test fun restartContinuesTheSameChain() {
    val store = Mem()
    val d1 = write(ShopData(), add(ShopData(), a("a1"), a("a2")), store)
    //  برنامه بسته و باز شد: لنگر از پرونده، دفتر از دیسک
    val reopened = Mem().also { it.text = store.text }
    val d2 = write(d1, add(d1, a("a3")), reopened)
    assertEquals(3L, d2.auditLog.last().chainSeq)
    assertEquals("$dev/1", d2.auditLog.last().chain)
    assertTrue(AuditSeal.verify(d2.auditLog, reopened.read()).ok)
  }

  @Test fun rolledBackLedgerStartsANewEpochAndKeepsEvidence() {
    val store = Mem()
    val d1 = write(ShopData(), add(ShopData(), a("a1"), a("a2"), a("a3")), store)
    //  پشتیبانِ کهنه برگشت (فقط ردیفِ نخست) — دمِ زنجیره رفت
    val restored = d1.copy(auditLog = d1.auditLog.take(1))
    val d2 = write(restored, add(restored, a("b1")), store)
    val b1 = d2.auditLog.last()
    assertEquals("$dev/2", b1.chain)
    assertEquals(1L, b1.chainSeq)
    val r = AuditSeal.verify(d2.auditLog, store.read())
    assertFalse(r.ok)
    assertEquals(AuditChain.Break("$dev/1", 2, "truncated"), r.broken)
  }

  @Test fun serverRowsAndMergedRowsAreNotStamped() {
    val store = Mem()
    val base = ShopData()
    //  میانِ ساختنِ صفحه و نوشتن، ردیفی از سرور (دستگاهِ دیگر، بی مُهر) روی دیسک نشست
    val disk = ShopData(auditLog = listOf(a("srv1")))
    val merged = add(disk, a("a1"))   //  نتیجهٔ ادغام: ردیفِ سرور + ردیفِ تازهٔ صفحه
    val out = AuditSeal.plan(disk, base, merged, session(true), store).data
    assertNull(out.auditLog.first { it.id == "srv1" }.deviceId)
    assertEquals(dev, out.auditLog.first { it.id == "a1" }.deviceId)
  }

  @Test fun noSessionMeansNoStampButStillImmutable() {
    val out = AuditSeal.plan(ShopData(), ShopData(), add(ShopData(), a("a1")), { null }, Mem()).data
    assertNull(out.auditLog.single().deviceId)
  }

  @Test fun concurrentAppendsOnOneDeviceAreSerialized() {
    val lock = Any()
    val store = Mem()
    var disk = ShopData()
    val w = LedgerWriter(lock, { disk }, { next -> disk = next; true }) { prior, next -> AuditSeal.plan(prior, prior, next, session(true), store) }
    val pool = Executors.newFixedThreadPool(8)
    val start = CountDownLatch(1)
    val n = 8 * 25
    repeat(n) { i ->
      pool.execute {
        start.await()
        w.edit(LedgerWriter.appendAudit { a("c$i") })
      }
    }
    start.countDown()
    pool.shutdown()
    assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
    assertEquals(n, disk.auditLog.size)
    assertEquals((1L..n).toList(), disk.auditLog.map { it.chainSeq })
    val r = AuditSeal.verify(disk.auditLog, store.read())
    assertTrue(r.ok)
    assertEquals(n, r.chained)
    assertEquals(n.toLong(), store.read()!!.heads.getValue("$dev/1").seq)
  }

  @Test fun replaceAllIsNeverStamped() {
    val store = Mem()
    var disk = ShopData()
    val w = LedgerWriter(Any(), { disk }, { next -> disk = next; true }) { prior, next -> AuditSeal.plan(prior, prior, next, session(true), store) }
    w.save(ShopData(auditLog = listOf(a("restored"))))   //  بازیابی/«پاک کردنِ همه»
    assertNull(disk.auditLog.single().deviceId)
    w.save(add(disk, a("a1")), base = disk)               //  ذخیرهٔ صفحه روی پایهٔ صریح
    assertEquals(dev, disk.auditLog.last().deviceId)
    assertNull(disk.auditLog.first().deviceId)
  }

  /**
   *  ⛔ سطحِ مدل (`AuditSeal.LEVEL`): فیلدِ تازه در `AuditEntry` یا سندهای درونش را نسخهٔ پیشینِ برنامه هنگامِ خواندن
   *  می‌اندازد و محتوای ردیف را دیگر نمی‌تواند بسنجد. فیلدِ تازه ⇐ این فهرست + `LEVEL` + ۱ (و `chainV` روی ردیف‌های تازه).
   */
  @Test fun schemaLevel() {
    fun names(d: SerialDescriptor, prefix: String = ""): List<String> = (0 until d.elementsCount).flatMap { i ->
      val n = prefix + d.getElementName(i)
      val c = d.getElementDescriptor(i).let { if (it.kind == StructureKind.LIST) it.getElementDescriptor(0) else it }
      if (c.kind == StructureKind.CLASS && !c.serialName.startsWith("kotlinx.serialization.json")) listOf(n) + names(c, "$n.") else listOf(n)
    }
    assertEquals(1L, AuditSeal.LEVEL)
    assertEquals(SCHEMA_V1, names(AuditEntry.serializer().descriptor).sorted().joinToString(","))
  }

  private companion object {
    /** `AuditEntry` و سندهای درونش در سطحِ ۱ (نامِ فیلدها، مرتب) */
    const val SCHEMA_V1 =
      "amount,approval,approvedBy,by,chain,chainSeq,chainV,createdAt,date,deviceId,drawer,fromMethod," +
      "fromStatus,hash,id,layaway,layaway.debtorId,layaway.depositMinor,layaway.kind,layaway.lines," +
      "layaway.lines.label,layaway.lines.name,layaway.lines.productId,layaway.lines.qtyMilli," +
      "layaway.lines.unitMinor,layaway.saleId,layaway.schedule,layaway.schedule.amountMinor," +
      "layaway.schedule.due,layaway.schedule.no,layaway.to,layaway.totalMinor,note,notes,payMethod,payRef," +
      "payRow,points,prev,reason,refId,refIds,specialOrder,specialOrder.debtorId,specialOrder.depositMinor," +
      "specialOrder.due,specialOrder.item,specialOrder.productId,specialOrder.qtyMilli,specialOrder.saleId," +
      "specialOrder.to,specialOrder.totalMinor,toMethod,toStatus,type,userId,userRole,xfer,z"
  }

  @Test fun sealedRowRoundTripsThroughTheLedgerJson() {
    val store = Mem()
    val d = write(ShopData(), add(ShopData(), a("a1", amount = 1234.345)), store)
    val r = d.auditLog.single()
    assertNotNull(r.hash)
    assertEquals(r.hash, AuditChain.hashOf(AuditSeal.tree(r)))
  }
}
