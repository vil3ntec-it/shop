package ir.vil3ntec.tohid.core.gateway

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** کلیدِ یکتایی یک بار برای هر تلاش؛ ازسرگیری پس از بسته شدنِ برنامه؛ ذخیرهٔ امن */
class HesabPayAttemptsTest {

  private val sale = "01JAB3C4D5E6F7G8H9J0K1M2N3"

  @Test
  fun keyMatchesServerAlphabetAndIsNewEachAttempt() {
    val r = Random(7)
    val a = HesabPayAttempts.newKey(sale, r)
    val b = HesabPayAttempts.newKey(sale, r)
    assertTrue(Wire.isToken(a, min = 8), a)
    assertTrue(a.startsWith("pos-$sale-"))
    assertNotEquals(a, b)
    //  کلیدِ فاکتورِ عجیب هم کلیدِ معتبر می‌سازد
    assertTrue(Wire.isToken(HesabPayAttempts.newKey("ف/ا|ک", r), min = 8))
  }

  @Test
  fun sameSaleSameAmountResumesTheSameIntent() {
    val stored = PendingPayment(sale, 145000, "pos-$sale-aaaaaaaaaaaa", "pi_1", "pending", createdAt = 1)
    val plan = HesabPayAttempts.plan(stored, sale, 145000, now = 5)
    assertEquals(stored, (plan as HesabPayAttempts.Plan.Resume).pending)
    //  پیش از پاسخِ سرور (بی شناسهٔ قصد) هم همان کلید ⇐ همان قصد
    val early = stored.copy(intentId = null, status = "created")
    assertEquals(early, (HesabPayAttempts.plan(early, sale, 145000, now = 5) as HesabPayAttempts.Plan.Resume).pending)
  }

  @Test
  fun paidIsAlwaysResumedNeverReplaced() {
    val paid = PendingPayment(sale, 145000, "pos-$sale-aaaaaaaaaaaa", "pi_1", "paid", createdAt = 1)
    assertIs<HesabPayAttempts.Plan.Resume>(HesabPayAttempts.plan(paid, sale, 999, now = 5))
  }

  @Test
  fun deadAttemptGetsAFreshKey() {
    for (s in listOf("failed", "expired", "cancelled")) {
      val dead = PendingPayment(sale, 145000, "pos-$sale-aaaaaaaaaaaa", "pi_1", s, createdAt = 1)
      val fresh = (HesabPayAttempts.plan(dead, sale, 145000, now = 9, random = Random(1)) as HesabPayAttempts.Plan.Fresh).pending
      assertNotEquals(dead.idempotencyKey, fresh.idempotencyKey)
      assertNull(fresh.intentId)
      assertEquals(9L, fresh.createdAt)
    }
  }

  @Test
  fun changedAmountCancelsTheLiveIntentFirst() {
    val live = PendingPayment(sale, 145000, "pos-$sale-aaaaaaaaaaaa", "pi_1", "pending", createdAt = 1)
    val plan = HesabPayAttempts.plan(live, sale, 150000, now = 9) as HesabPayAttempts.Plan.Replace
    assertEquals(live, plan.old)
    assertEquals(150000L, plan.fresh.amountMinor)
    //  بی شناسهٔ قصد چیزی برای لغو نیست
    assertIs<HesabPayAttempts.Plan.Fresh>(HesabPayAttempts.plan(live.copy(intentId = null), sale, 150000, now = 9))
    //  فاکتورِ دیگر ⇐ تازه
    assertIs<HesabPayAttempts.Plan.Fresh>(HesabPayAttempts.plan(live, "OTHER", 145000, now = 9))
  }

  @Test
  fun withIntentRecordsIdStatusExpiryAndSkewOnce() {
    val p = PendingPayment(sale, 145000, "pos-$sale-aaaaaaaaaaaa", createdAt = 1)
    val i = PaymentIntent.fromMap(intentJson())!!
    val a = HesabPayAttempts.withIntent(p, i, localNow = 103_000)
    assertEquals("pi_1", a.intentId)
    assertEquals("pending", a.status)
    assertEquals(1_000_000L, a.expiresAt)
    assertEquals(3_000L, a.skewMs)
    //  خواندنِ بعدی اختلافِ ساعت را از createdAt ِ کهنه دوباره حساب نمی‌کند
    assertEquals(3_000L, HesabPayAttempts.withIntent(a, i, localNow = 900_000).skewMs)
  }

  @Test
  fun encodeDecodeRoundTripAndSkipsBrokenLines() {
    val a = PendingPayment(sale, 145000, "pos-$sale-aaaaaaaaaaaa", "pi_1", "pending", 10, 1_000_000, -250)
    val b = PendingPayment("S2", 5, "pos-S2-bbbbbbbbbbbb", null, "created", 11, null, 0)
    val raw = HesabPayAttempts.encode(listOf(a, b))
    assertEquals(listOf(a, b), HesabPayAttempts.decode(raw))
    val broken = raw + "\nv1|x\nv2|a|1|k|i|s|1|2|3\nv1|bad id|5|pos-x-bbbbbbbbbbbb||created|1||0"
    assertEquals(listOf(a, b), HesabPayAttempts.decode(broken))
    assertTrue(HesabPayAttempts.decode(null).isEmpty())
    assertTrue(HesabPayAttempts.decode("garbage").isEmpty())
  }

  @Test
  fun storeReplacesPrunesAndForgets() {
    var raw: String? = null
    var now = 1_000L
    val store = PendingPayments(object : PendingStore {
      override fun read() = raw
      override fun write(value: String) { raw = value }
    }) { now }
    val a = PendingPayment(sale, 100, "pos-$sale-aaaaaaaaaaaa", createdAt = now)
    store.save(a)
    store.save(a.copy(intentId = "pi_1", status = "pending"))
    assertEquals(1, store.all().size)
    assertEquals("pi_1", store.of(sale)?.intentId)
    store.save(PendingPayment("S2", 5, "pos-S2-bbbbbbbbbbbb", createdAt = now))
    store.prune { it == sale }
    assertNull(store.of(sale))
    assertEquals("S2", store.all().single().saleKey)
    now += HesabPayAttempts.MAX_AGE_MS + 1
    store.prune { false }
    assertTrue(store.all().isEmpty())
    store.save(a.copy(createdAt = now))
    store.forget(sale)
    assertTrue(store.all().isEmpty())
  }

  @Test
  fun storeKeepsAtMostTheCap() {
    var list = emptyList<PendingPayment>()
    repeat(HesabPayAttempts.MAX_RECORDS + 5) { n ->
      list = HesabPayAttempts.upsert(list, PendingPayment("S$n", 1, "pos-S$n-cccccccccccc", createdAt = 100), now = 100)
    }
    assertEquals(HesabPayAttempts.MAX_RECORDS, list.size)
    assertEquals("S${HesabPayAttempts.MAX_RECORDS + 4}", list.last().saleKey)
  }
}
