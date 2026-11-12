package ir.vil3ntec.tohid.core.terminal

import ir.vil3ntec.tohid.core.pay.PayLevel
import ir.vil3ntec.tohid.core.pay.PayStatus
import ir.vil3ntec.tohid.core.pay.PaymentRow
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 *  PAY-08 — «تأییدِ دستگاه» با دستگاهِ پرداختِ ساختگی: «موفق» فقط با پاسخی که کلید، فاکتور، مبلغ و ارز را بازمی‌گرداند
 *  و شمارهٔ تأیید دارد؛ هر ناجوری یا پاسخِ دیر ⇒ «نیازمندِ بررسی»، هرگز گم و هرگز موفق؛ تکرار و ازسرگیری ⇒ یک پول و یک ردیف.
 */
class DevicePayTest {

  private fun <T> runSync(block: suspend () -> T): T {
    var out: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { out = it })
    return out!!.getOrThrow()
  }

  /**
   *  دستگاهِ ساختگی با همان قرارداد: هر کلید یک بار پول می‌گیرد و تکرارِ همان کلید پاسخِ همان را می‌دهد.
   *  [answer] پاسخِ نخستین درخواستِ هر کلید است (`null` ⇒ در دسترس نیست).
   */
  private class FakeDevice(var answer: (DeviceAttempt) -> DeviceReceipt?) : VerifyingTerminal {
    override val id = "fake_device"
    val byKey = LinkedHashMap<String, DeviceReceipt>()
    var charges = 0
    var asked = 0
    override suspend fun pay(attempt: DeviceAttempt): DeviceReceipt? {
      asked++
      byKey[attempt.key]?.let { return it }
      val r = answer(attempt) ?: return null
      if (r.status == DeviceReceipt.Status.APPROVED) charges++
      byKey[attempt.key] = r
      return r
    }
    override suspend fun lookup(key: String): DeviceReceipt? = byKey[key]
  }

  private val rnd = Random(7)
  private fun attempt(sale: String = "s-1001", amount: Long = 145_000, now: Long = 1_000) =
    DevicePay.attempt(null, sale, amount, now, ttlMs = 120_000, random = rnd)

  private fun approved(a: DeviceAttempt, approval: String = "A1B2C3", card: String? = "•••• 4321") =
    DeviceReceipt(a.key, a.saleKey, a.amountMinor, "AFN", DeviceReceipt.Status.APPROVED, approval, card)

  @Test
  fun matchingApprovalIsTheOnlySuccessAndIsTheDeviceLevel() {
    val a = attempt()
    val v = DevicePay.verdict(a, approved(a))
    assertIs<DevicePay.Verdict.Confirmed>(v)
    assertEquals(PaymentRow("afpay_card", "card", 145_000, "A1B2C3 (•••• 4321)", PayStatus.CONFIRMED, "device"), v.row)
    assertEquals(PayLevel.DEVICE, PayLevel.of(v.row.verification))
    assertTrue(PayLevel.success(v.row.kind, v.row.status, v.row.verification))
    //  شمارهٔ تأییدِ فارسی/با فاصله همان قاعدهٔ کارت‌خوان
    assertEquals("A1B2C3", (DevicePay.verdict(a, approved(a, " a1b2 c3 ", null)) as DevicePay.Verdict.Confirmed).row.ref)
  }

  @Test
  fun anyMismatchOrLateApprovalIsReviewNeverSuccess() {
    val a = attempt()
    val ok = approved(a)
    val cases = mapOf(
      DevicePay.Hold.OTHER_KEY to ok.copy(key = "dev-other"),
      DevicePay.Hold.OTHER_SALE to ok.copy(saleRef = "s-9999"),
      DevicePay.Hold.AMOUNT to ok.copy(amountMinor = 14_500),
      DevicePay.Hold.NO_APPROVAL to ok.copy(approval = "!!"),
    )
    for ((hold, r) in cases) {
      val v = DevicePay.verdict(a, r)
      assertIs<DevicePay.Verdict.Review>(v, "$hold")
      assertEquals(hold, v.hold)
      assertEquals(PayStatus.NEEDS_REVIEW, v.row.status)
      assertEquals(r.amountMinor, v.row.amount, "مبلغی که دستگاه گفت گرفت، برای بررسی")
      assertFalse(PayLevel.success(v.row.kind, v.row.status, v.row.verification), "$hold هرگز موفق نیست")
    }
    assertEquals(DevicePay.Hold.AMOUNT, (DevicePay.verdict(a, ok.copy(currency = "USD")) as DevicePay.Verdict.Review).hold)
    //  تأییدی که پس از لغو یا پایانِ مهلت رسید: پول شاید گرفته شده ⇒ بررسی، نه گم
    val late = DevicePay.verdict(a, ok, closed = true)
    assertIs<DevicePay.Verdict.Review>(late)
    assertEquals(DevicePay.Hold.LATE, late.hold)
  }

  @Test
  fun declinedCancelledOrPendingWriteNothing() {
    val a = attempt()
    for (st in listOf(DeviceReceipt.Status.DECLINED, DeviceReceipt.Status.CANCELLED)) {
      val v = DevicePay.verdict(a, approved(a).copy(status = st))
      assertEquals(DevicePay.Verdict.None(st), v)
      assertEquals(DevicePay.Recorded.Added(emptyList()), DevicePay.record(emptyList(), v))
    }
    assertEquals(DevicePay.Verdict.Waiting, DevicePay.verdict(a, approved(a).copy(status = DeviceReceipt.Status.PENDING)))
    assertEquals(DevicePay.Verdict.None(DeviceReceipt.Status.CANCELLED), DevicePay.verdict(a, approved(a).copy(status = DeviceReceipt.Status.PENDING), closed = true))
  }

  @Test
  fun sameAttemptIsResumedNotRecharged() {
    val a = attempt()
    //  همان فاکتور و مبلغ و پیش از مهلت ⇒ همان کلید؛ مبلغِ دیگر، فاکتورِ دیگر یا پس از مهلت ⇒ کلیدِ تازه
    assertEquals(a, DevicePay.attempt(a, "s-1001", 145_000, now = 60_000, ttlMs = 120_000, random = rnd))
    assertNotEquals(a.key, DevicePay.attempt(a, "s-1001", 150_000, now = 60_000, ttlMs = 120_000, random = rnd).key)
    assertNotEquals(a.key, DevicePay.attempt(a, "s-1002", 145_000, now = 60_000, ttlMs = 120_000, random = rnd).key)
    assertNotEquals(a.key, DevicePay.attempt(a, "s-1001", 145_000, now = a.until, ttlMs = 120_000, random = rnd).key)
    assertTrue(a.key.startsWith("dev-s1001-") && a.key.length == "dev-s1001-".length + 12)

    val dev = FakeDevice { approved(it) }
    val first = runSync { DevicePay.run(dev, a, now = 2_000) }
    //  قطعِ شبکه پس از پرداخت / زدنِ دوباره / بسته شدنِ برنامه و ازسرگیری: همان پاسخ، پولِ دوم نه
    val again = runSync { DevicePay.run(dev, a, now = 3_000) }
    assertEquals(first, again)
    assertEquals(1, dev.charges)
    assertEquals(1, dev.asked, "ازسرگیری اول می‌پرسد (lookup)، درخواستِ دوم نمی‌فرستد")

    //  یک ردیف، حتی با پاسخِ تکراری
    val rows = (DevicePay.record(emptyList(), first) as DevicePay.Recorded.Added).rows
    assertEquals(1, rows.size)
    assertEquals(DevicePay.Recorded.Already, DevicePay.record(rows, again))
    //  همان شمارهٔ تأیید با کلیدِ دیگر (پاسخِ دوبارهٔ دستگاه) هم ردیفِ دوم نیست
    val b = attempt(amount = 145_000, now = 5_000)
    assertEquals(DevicePay.Recorded.Already, DevicePay.record(rows, DevicePay.verdict(b, approved(b))))
  }

  @Test
  fun paidWhileTheAppWasClosedIsFoundOnResumeAndLateIsReview() {
    val a = attempt()
    val dev = FakeDevice { approved(it) }
    runSync { dev.pay(a) }
    //  برنامه پیش از پاسخ بسته شد؛ بازکردنِ همان فاکتور همان تلاش را می‌پاید ⇒ تأییدِ همان، بی پولِ دوم
    assertIs<DevicePay.Verdict.Confirmed>(runSync { DevicePay.run(dev, a, now = 50_000) })
    assertEquals(1, dev.charges)
    //  پاسخ پس از مهلت پیدا شد ⇒ بررسی (پول گرفته شده، گم نمی‌شود)
    assertEquals(DevicePay.Hold.LATE, (runSync { DevicePay.run(dev, a, now = a.until + 1) } as DevicePay.Verdict.Review).hold)
    //  پس از مهلت و بی هیچ پاسخی ⇒ درخواستِ تازه فرستاده نمی‌شود
    val b = attempt(sale = "s-2002")
    val quiet = FakeDevice { error("پس از مهلت نباید پرسیده شود") }
    assertEquals(DevicePay.Verdict.None(DeviceReceipt.Status.CANCELLED), runSync { DevicePay.run(quiet, b, now = b.until) })
    assertEquals(0, quiet.asked)
  }

  @Test
  fun unavailableOrStillPendingDeviceWaitsWithTheSameKey() {
    val a = attempt()
    var up = false
    val dev = FakeDevice { if (up) approved(it) else null }
    assertEquals(DevicePay.Verdict.Waiting, runSync { DevicePay.run(dev, a, now = 2_000) })
    up = true
    assertIs<DevicePay.Verdict.Confirmed>(runSync { DevicePay.run(dev, a, now = 3_000) })
    assertEquals(1, dev.charges)
    val slow = FakeDevice { approved(it).copy(status = DeviceReceipt.Status.PENDING) }
    assertEquals(DevicePay.Verdict.Waiting, runSync { DevicePay.run(slow, a, now = 2_000) })
    assertEquals(DevicePay.Verdict.Waiting, runSync { DevicePay.run(slow, a, now = 3_000) })
  }

  @Test
  fun threeLevelsAreDistinctAndManualTerminalIsNeverDevice() {
    assertEquals(PayLevel.MANUAL, PayLevel.of("manual"))
    assertEquals(PayLevel.MANUAL, PayLevel.of("cashier"))
    assertEquals(PayLevel.DEVICE, PayLevel.of("device"))
    assertEquals(PayLevel.PROVIDER, PayLevel.of("gateway"))
    assertEquals(PayLevel.PROVIDER, PayLevel.of("provider"))
    assertEquals(null, PayLevel.of(null))
    assertEquals(null, PayLevel.of("customer_said"))
    //  ثبتِ دستیِ «در انتظار» همان «تأییدشده» ِ صاحب‌کار؛ غیرنقدِ بی سطح یا در انتظارِ درگاه هرگز «موفق» نیست
    assertTrue(PayLevel.success("card", PayStatus.PENDING, "manual"))
    assertFalse(PayLevel.success("card", PayStatus.CONFIRMED, null))
    assertFalse(PayLevel.success("wallet", PayStatus.PENDING, "gateway"))
    assertTrue(PayLevel.success("cash", PayStatus.CONFIRMED, null))
    //  کارت‌خوانِ دستی فقط شمارهٔ مرجع می‌دهد، نه کلید و مبلغِ بازگشتی ⇒ هرگز `device` (همان ثبتِ دستیِ PAY-06)
    val manual: CardTerminal = ManualCardTerminal { ManualCardTerminal.Answer.Approved("1234") }
    val vendor: CardTerminal = VendorAppTerminal
    assertFalse(manual is VerifyingTerminal)
    assertFalse(vendor is VerifyingTerminal)
  }
}
