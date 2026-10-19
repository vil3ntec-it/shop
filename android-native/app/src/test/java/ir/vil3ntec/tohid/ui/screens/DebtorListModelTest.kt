package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.screens.DebtorListModel.Sort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  فهرستِ قرض‌دارانِ گوشی (r9): ترتیب‌ها، «چند روز»، سه عددِ بالا، متنِ زیرِ
 *  مبلغ و جست‌وجو — همه بی Compose.
 */
class DebtorListModelTest {

  private val day = 86_400_000L
  private val now = 1_790_000_000_000L

  private var seq = 0
  private fun tx(debtorId: String, type: String, amount: Double, daysAgo: Long) =
    DebtTransaction(id = "t${seq++}", debtorId = debtorId, type = type, amount = amount, createdAt = now - daysAgo * day)

  /** همان سه نفرِ r9: دو بدهکار (۱٬۰۰۰ و ۶۰۰، شش روز) و یکی صاف با رسیدِ ۱۰٬۰۰۰ */
  private val r9 = ShopData(
    debtors = listOf(
      Debtor(id = "h0", name = "haroon"),
      Debtor(id = "h6", name = "haroon", phone = "0792236008"),
      Debtor(id = "j", name = "جلیل"),
    ),
    transactions = listOf(
      tx("j", "give", 1000.0, 6),
      tx("h6", "give", 600.0, 6),
      tx("h0", "give", 10000.0, 9),
      tx("h0", "receive", 10000.0, 2),
    ),
  )

  private fun names(rows: List<DebtorListModel.Row>) = rows.map { it.debtor.id }

  @Test
  fun `r9 — بیشترین قرض اول، متنِ زیر و قرصِ روز`() {
    val rows = DebtorListModel.rows(r9, "", Sort.MOST, now)
    assertEquals(listOf("j", "h6", "h0"), names(rows))
    assertEquals(listOf("1,000 افغانی بدهکار", "0792236008", "حساب صاف است"), rows.map(DebtorListModel::subtitle))
    assertEquals(listOf("6 روز", "6 روز", null), rows.map(DebtorListModel::daysLabel))
    assertEquals(listOf(true, true, false), rows.map { it.owes })
  }

  @Test
  fun `r9 — سه عددِ بالا از همهٔ دفتر`() {
    assertEquals(DebtorListModel.Summary(people = 3, owed = 1600.0, received = 10000.0), DebtorListModel.summary(r9))
    //  موجودیِ یک نفر از قرضِ دیگری کم نمی‌شود
    val credit = r9.copy(
      debtors = r9.debtors + Debtor(id = "c", name = "پیش‌پرداخت"),
      transactions = r9.transactions + tx("c", "receive", 300.0, 1),
    )
    assertEquals(1600.0, DebtorListModel.summary(credit).owed, 0.0)
    assertEquals(10300.0, DebtorListModel.summary(credit).received, 0.0)
  }

  @Test
  fun `کمترین قرض — موجودی‌دار اول، بعد صاف، بعد بدهکارِ کم`() {
    val d = r9.copy(
      debtors = r9.debtors + Debtor(id = "c", name = "پیش‌پرداخت"),
      transactions = r9.transactions + tx("c", "receive", 300.0, 1),
    )
    assertEquals(listOf("c", "h0", "h6", "j"), names(DebtorListModel.rows(d, "", Sort.LEAST, now)))
  }

  @Test
  fun `قدیمی‌ترین قرض — مدت، بعد مبلغ؛ بی‌قرض ته`() {
    val d = ShopData(
      debtors = listOf(
        Debtor(id = "new", name = "الف"),
        Debtor(id = "old", name = "ب"),
        Debtor(id = "flat", name = "پ"),
        Debtor(id = "big", name = "ت"),
      ),
      transactions = listOf(
        tx("new", "give", 900.0, 1),
        tx("old", "give", 50.0, 40),
        tx("big", "give", 2000.0, 1),
        tx("flat", "give", 100.0, 90),
        tx("flat", "receive", 100.0, 80),
      ),
    )
    assertEquals(listOf("old", "big", "new", "flat"), names(DebtorListModel.rows(d, "", Sort.OLDEST, now)))
  }

  @Test
  fun `بر اساس نام — الفبای فارسی، نه ترتیبِ یونی‌کد؛ لاتین بعد، بی‌نام آخر`() {
    val list = listOf("گل", "کریم", "پری", "بهار", "Ali", "", "احمد", "ژیلا", "زهره", "چمن", "جواد", "آرش")
    val d = ShopData(debtors = list.mapIndexed { i, n -> Debtor(id = "d$i", name = n) })
    val sorted = DebtorListModel.rows(d, "", Sort.NAME, now).map { it.debtor.name }
    //  «آ» همان «ا» است (احمد پیش از آرش: ح پیش از ر)
    assertEquals(listOf("احمد", "آرش", "بهار", "پری", "جواد", "چمن", "زهره", "ژیلا", "کریم", "گل", "Ali", ""), sorted)
  }

  @Test
  fun `نامِ عربی‌نویس و فارسی یک جا می‌نشیند`() {
    assertEquals(0, DebtorListModel.byName.compare("علي", "علی"))
    assertEquals(0, DebtorListModel.byName.compare("كريم", "کریم"))
    assertTrue(DebtorListModel.byName.compare("علی", "عمر") < 0)
  }

  @Test
  fun `هم‌مبلغ‌ها همیشه یک ترتیب دارند — نام، بعد شناسه`() {
    val d = ShopData(
      debtors = listOf(Debtor(id = "z", name = "ب"), Debtor(id = "y", name = "الف"), Debtor(id = "x", name = "الف")),
      transactions = listOf(tx("z", "give", 500.0, 3), tx("y", "give", 500.0, 3), tx("x", "give", 500.0, 3)),
    )
    for (sort in Sort.entries) {
      val order = names(DebtorListModel.rows(d, "", sort, now))
      assertEquals("ترتیبِ $sort", listOf("x", "y", "z"), order)
      assertEquals("پایداریِ $sort", order, names(DebtorListModel.rows(d.copy(debtors = d.debtors.reversed()), "", sort, now)))
    }
  }

  @Test
  fun `چند روز — قدیمی‌ترین قرضِ پس‌نداده، اول‌آمده اول‌رفته`() {
    val d = ShopData(
      debtors = listOf(Debtor(id = "a", name = "الف")),
      transactions = listOf(
        tx("a", "give", 500.0, 10),
        tx("a", "give", 500.0, 3),
        tx("a", "receive", 500.0, 1),
      ),
    )
    //  رسید قرضِ ده‌روزه را بست؛ آنچه مانده از سه روز پیش است
    assertEquals(3L, DebtorListModel.days(d, "a", now))
    assertEquals("3 روز", DebtorListModel.daysLabel(DebtorListModel.rows(d, "", Sort.MOST, now).single()))
    //  امروز ⇐ «امروز»
    val today = d.copy(transactions = listOf(tx("a", "give", 10.0, 0)))
    assertEquals("امروز", DebtorListModel.daysLabel(DebtorListModel.rows(today, "", Sort.MOST, now).single()))
    //  بی‌قرض ⇐ قرص نیست
    val settled = d.copy(transactions = d.transactions + tx("a", "receive", 500.0, 0))
    assertNull(DebtorListModel.days(settled, "a", now))
    assertNull(DebtorListModel.daysLabel(DebtorListModel.rows(settled, "", Sort.MOST, now).single()))
  }

  @Test
  fun `یک ماه به بالا پررنگ`() {
    val d = ShopData(debtors = listOf(Debtor(id = "a")), transactions = listOf(tx("a", "give", 5.0, 30)))
    assertTrue(DebtorListModel.stale(DebtorListModel.rows(d, "", Sort.MOST, now).single()))
    val fresh = d.copy(transactions = listOf(tx("a", "give", 5.0, 29)))
    assertFalse(DebtorListModel.stale(DebtorListModel.rows(fresh, "", Sort.MOST, now).single()))
  }

  @Test
  fun `کسرِ ریزِ اعشار صاف است، نه «۰ افغانی بدهکار»`() {
    val d = ShopData(
      debtors = listOf(Debtor(id = "a", name = "الف")),
      transactions = listOf(tx("a", "give", 0.3, 5), tx("a", "receive", 0.1, 4), tx("a", "receive", 0.2, 3)),
    )
    val row = DebtorListModel.rows(d, "", Sort.MOST, now).single()
    assertFalse(row.owes)
    assertEquals("حساب صاف است", DebtorListModel.subtitle(row))
    assertNull(DebtorListModel.daysLabel(row))
  }

  @Test
  fun `جست‌وجو — نام با ي و ی، شماره با ‎+93 و رقمِ فارسی`() {
    val d = ShopData(
      debtors = listOf(
        Debtor(id = "a", name = "علي رضا", phone = "+93792236008"),
        Debtor(id = "b", name = "Haroon", phone = "0700111222"),
      ),
    )
    fun found(q: String) = DebtorListModel.rows(d, q, Sort.NAME, now).map { it.debtor.id }
    assertEquals(listOf("a", "b"), found(""))
    assertEquals(listOf("a", "b"), found("   "))
    assertEquals(listOf("a"), found("علی"))
    assertEquals(listOf("a"), found("علیرضا"))
    assertEquals(listOf("b"), found("haroon"))
    assertEquals(listOf("a"), found("0792"))
    assertEquals(listOf("b"), found("۰۷۰۰"))
    assertEquals(emptyList<String>(), found("کریم"))
  }

  @Test
  fun `ترتیبِ ذخیره‌شده — نامِ ناشناس همان پیش‌فرض`() {
    assertEquals(Sort.OLDEST, Sort.parse("OLDEST"))
    assertEquals(Sort.MOST, Sort.parse("SOMETHING_NEW"))
    assertEquals(Sort.MOST, Sort.parse(null))
    assertEquals(listOf("بیشترین قرض", "کمترین قرض", "قدیمی‌ترین قرض", "بر اساس نام"), Sort.entries.map { it.label })
  }
}
