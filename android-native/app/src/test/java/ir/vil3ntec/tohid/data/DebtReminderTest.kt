package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** یادآوریِ بدهی با قالب و زمان‌بندی — CRM-06 */
class DebtReminderTest {

  private var seq = 0
  private fun newId() = "r${seq++}"
  private val today = "2026-10-19"
  private fun ms(iso: String) = ir.vil3ntec.tohid.core.report.ReportDates.epochDay(iso)!! * 86_400_000L
  private val now = ms(today) + 12 * 3_600_000L

  private fun give(id: String, who: String, amount: Double, date: String, due: String? = null) =
    DebtTransaction(id = id, debtorId = who, type = "give", amount = amount, date = date, createdAt = ms(date), dueDate = due)

  @Test fun `قالبِ پیش‌فرض همان جملهٔ امروزِ دکمه‌های پیامک و واتس‌اپ است`() {
    //  عیناً `ReminderRow` در Debtors.kt
    val legacy = "سلام احمد عزیز، یادآوری می‌کنم که ${money(1500.0)} افغانی از حساب شما باقی مانده. ممنون."
    assertEquals(legacy, DebtReminder.message(DebtReminder.Vars("احمد", 1500.0)))
    assertEquals("سلام احمد عزیز،", DebtReminder.message(DebtReminder.Vars("احمد", 0.0)))
    assertEquals("سلام احمد عزیز،", DebtReminder.message(DebtReminder.Vars("احمد", -20.0)))
  }

  @Test fun `همهٔ جای‌نگهدارها؛ ناشناخته دست نمی‌خورد؛ اعتبارسنجی`() {
    val t = "{نام}، {مبلغ} افغانی، سررسید {سررسید}، {روز} روز — {دکان} {ناشناس}"
    val v = DebtReminder.Vars("محمود", 2500.0, "دکانِ توحید", "2026-10-01", 45)
    assertEquals("محمود، ${money(2500.0)} افغانی، سررسید ${formatDate("2026-10-01")}، 45 روز — دکانِ توحید {ناشناس}", DebtReminder.render(t, v))
    assertEquals("محمود، ${money(2500.0)} افغانی، سررسید ، 45 روز — دکانِ توحید {ناشناس}", DebtReminder.render(t, v.copy(dueDate = "")))
    assertNull(DebtReminder.validate(DebtReminder.DEFAULT_TEMPLATE))
    assertEquals("متنِ یادآوری خالی است", DebtReminder.validate("  "))
    assertEquals("جای‌نگهدارِ ناشناخته: {ناشناس}", DebtReminder.validate(t))
    assertEquals("متنِ یادآوری حداکثر 600 حرف", DebtReminder.validate("ا".repeat(601)))
    //  قالبِ خالی ⇒ پیش‌فرض
    assertEquals(DebtReminder.message(DebtReminder.Vars("احمد", 10.0)), DebtReminder.message(DebtReminder.Vars("احمد", 10.0), ""))
  }

  private val d0 = ShopData(
    debtors = listOf(
      Debtor(id = "old", name = "کهنه", phone = "+93700000001"),       // بدهیِ ۴۵ روزه
      Debtor(id = "fresh", name = "تازه", phone = "+93700000002"),     // بدهیِ ۵ روزه، بی سررسید
      Debtor(id = "late", name = "سررسیدگذشته", phone = "0700000003"), // ۱۰ روزه ولی سررسیدش گذشته
      Debtor(id = "nophone", name = "بی‌شماره"),                        // کهنه ولی شماره ندارد
      Debtor(id = "paid", name = "تسویه", phone = "+93700000005"),      // کهنه ولی صفر
      Debtor(id = "gone", name = "ادغامی", phone = "+93700000006", mergedInto = "old"),
    ),
    transactions = listOf(
      give("t1", "old", 1000.0, "2026-09-04"),
      give("t2", "fresh", 5000.0, "2026-10-14"),
      give("t3", "late", 300.0, "2026-10-09", due = "2026-10-15"),
      give("t4", "nophone", 800.0, "2026-08-01"),
      give("t5", "paid", 400.0, "2026-08-01"),
      DebtTransaction(id = "t6", debtorId = "paid", type = "receive", amount = 400.0, date = "2026-09-01", createdAt = ms("2026-09-01")),
      give("t7", "gone", 70.0, "2026-08-01"),
    ),
  )

  @Test fun `فهرستِ امروز — سررسیدگذشته اول، بدهیِ کهنه، بی‌شماره و تسویه و ادغامی بیرون`() {
    val due = DebtReminder.due(d0, today, now = now)
    assertEquals(listOf("late", "old"), due.map { it.debtor.id })
    assertEquals("2026-10-15", due[0].overdueSince)
    assertEquals(10L, due[0].debtDays)
    assertEquals(45L, due[1].debtDays)
    assertEquals("", due[1].overdueSince)
    assertEquals(DebtReminder.message(DebtReminder.Vars("کهنه", 1000.0)), due[1].message)

    //  مرزِ روز و مبلغ قابلِ تنظیم است
    assertEquals(listOf("late", "fresh", "old"), DebtReminder.due(d0, today, DebtReminder.Rule(minDebtDays = 5), now = now).map { it.debtor.id })
    assertEquals(emptyList<String>(), DebtReminder.due(d0, today, DebtReminder.Rule(minBalance = 1001.0), now = now).map { it.debtor.id })
    assertEquals(listOf("fresh"), DebtReminder.due(d0, today, DebtReminder.Rule(minBalance = 1001.0, minDebtDays = 5), now = now).map { it.debtor.id })
  }

  @Test fun `یادآوریِ ثبت‌شده تا «هر چند روز» دوباره در فهرست نمی‌آید`() {
    val r = DebtReminder.record(d0, "old", "whatsapp", "2026-10-16", 5, ::newId) as DebtorEngine.Result.Ok
    val a = r.data.auditLog.single()
    assertEquals(DebtReminder.TYPE, a.type)
    assertEquals("old", a.refId)
    assertEquals("یادآوریِ بدهی به «کهنه» با واتس‌اپ — مانده ${money(1000.0)} افغانی", a.notes)
    assertEquals("2026-10-16", DebtReminder.lastReminded(r.data, "old"))

    //  سه روز پیش ⇒ نه؛ با «هر ۳ روز» ⇒ بله
    assertEquals(listOf("late"), DebtReminder.due(r.data, today, now = now).map { it.debtor.id })
    val again = DebtReminder.due(r.data, today, DebtReminder.Rule(everyDays = 3), now = now)
    assertEquals(listOf("late", "old"), again.map { it.debtor.id })
    assertEquals("2026-10-16", again[1].lastReminded)

    assertEquals("راهِ یادآوریِ ناشناخته", (DebtReminder.record(d0, "old", "fax") as DebtorEngine.Result.Failed).message)
    assertEquals("مشتری پیدا نشد", (DebtReminder.record(d0, "x", "sms") as DebtorEngine.Result.Failed).message)
  }

  @Test fun `برگهٔ یادآوری`() {
    val s = DebtReminder.dueSheet(d0, today)
    assertEquals("یادآوری-بدهی-2026-10-19", s.name)
    assertEquals(listOf("نام", "شماره تماس", "مانده", "روزهای بدهی", "سررسیدِ گذشته", "آخرین یادآوری", "متنِ پیام"), s.rows[0])
    //  این برگه با ساعتِ دستگاه حساب می‌شود؛ فقط «سررسیدگذشته» مستقل از ساعت است
    assertEquals("سررسیدگذشته", s.rows[1][0])
    assertEquals("2026-10-15", s.rows[1][4])
  }
}
