package ir.vil3ntec.tohid.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** پروندهٔ مشتری: شمارهٔ دوم، برچسب، گروه، لیستِ قیمت، رضایت — CRM-02/08/11/12 */
class CustomerCrmTest {

  private var seq = 0
  private fun newId() = "a${seq++}"
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }

  private val d0 = ShopData(
    debtors = listOf(
      Debtor(id = "c1", name = "احمد", phone = "+93700123456"),
      Debtor(id = "c2", name = "محمود", phone = "+93799000111"),
    ),
  )

  private fun ok(r: DebtorEngine.Result): ShopData = (r as DebtorEngine.Result.Ok).data
  private fun failed(r: DebtorEngine.Result): String = (r as DebtorEngine.Result.Failed).message

  @Test fun `قرض‌دارِ قدیمی بایت‌به‌بایت همان نوشته می‌شود و فیلدهای تازه برمی‌گردند`() {
    val old = Debtor(id = "c1", name = "احمد", phone = "0700", notes = "n", createdAt = 5)
    val text = json.encodeToString(Debtor.serializer(), old)
    assertEquals("""{"id":"c1","name":"احمد","phone":"0700","notes":"n","createdAt":5}""", text)
    val full = old.copy(phone2 = "+93799111222", tags = listOf("عمده"), group = "همکار", priceList = "wholesale", marketingConsent = false, mergedInto = "c9")
    assertEquals(full, json.decodeFromString(Debtor.serializer(), json.encodeToString(Debtor.serializer(), full)))
    //  دادهٔ برنامهٔ قدیمی (بی این کلیدها) همان پیش‌فرض‌های null را می‌گیرد
    assertEquals(old, json.decodeFromString(Debtor.serializer(), text))
  }

  @Test fun `کلیدِ شماره — 07، ‎+93 و 0093 و رقمِ فارسی یکی‌اند؛ شمارهٔ کوتاه کلید ندارد`() {
    val k = CustomerCrm.phoneKey("0700123456")
    assertEquals("+93700123456", k)
    assertEquals(k, CustomerCrm.phoneKey("+93 70 012 3456"))
    assertEquals(k, CustomerCrm.phoneKey("0093700123456"))
    assertEquals(k, CustomerCrm.phoneKey("۰۷۰۰۱۲۳۴۵۶"))
    assertNull(CustomerCrm.phoneKey("123"))
    assertNull(CustomerCrm.phoneKey(""))
    assertNull(CustomerCrm.phoneKey(null))
  }

  @Test fun `شمارهٔ دوم ذخیره و با هر دو شماره پیدا می‌شود؛ تکرارِ شمارهٔ اول پذیرفته نیست`() {
    val d = ok(CustomerCrm.setProfile(d0, "c1", CustomerCrm.Profile(phone2 = "0799 111 222"), "2026-10-19", 1, ::newId))
    val c1 = d.debtors.first { it.id == "c1" }
    assertEquals("+93799111222", c1.phone2)
    assertEquals(listOf("+93700123456", "+93799111222"), CustomerCrm.phones(c1))
    assertEquals(listOf("c1"), CustomerCrm.findByPhone(d, "0799111222").map { it.id })
    assertEquals(listOf("c1"), CustomerCrm.findByPhone(d, "0700123456").map { it.id })
    assertTrue(CustomerCrm.matches(c1, "0799"))
    assertTrue(d.auditLog.isEmpty())

    assertEquals("شمارهٔ دوم همان شمارهٔ اول است", failed(CustomerCrm.setProfile(d0, "c1", CustomerCrm.Profile(phone2 = "0700123456"))))
    assertEquals("مشتری پیدا نشد", failed(CustomerCrm.setProfile(d0, "x", CustomerCrm.Profile())))
  }

  @Test fun `ویرایشِ عادیِ نام و تلفن فیلدهای تازه را پاک نمی‌کند`() {
    var d = ok(CustomerCrm.setProfile(d0, "c1", CustomerCrm.Profile(phone2 = "0799111222", tags = listOf("عمده"), group = "همکار", priceList = "wholesale"), "2026-10-19", 1, ::newId))
    d = ok(DebtorEngine.edit(d, "c1", DebtorEngine.DebtorDraft(name = "احمد جان", phone = "0700123456", notes = "x")))
    val c1 = d.debtors.first { it.id == "c1" }
    assertEquals("احمد جان", c1.name)
    assertEquals("+93799111222", c1.phone2)
    assertEquals(listOf("عمده"), c1.tags)
    assertEquals("همکار", c1.group)
    assertEquals("wholesale", c1.priceList)
  }

  @Test fun `برچسب‌ها — بی «#»، بی تکرار با ي و ی، سقفِ تعداد و طول`() {
    //  نخستین نوشتار می‌ماند (متنِ کاربر عوض نمی‌شود)؛ «همسایه» با ی تکرارِ همان است
    assertEquals(listOf("عمده", "همسايه", "علی آباد"), CustomerCrm.normalizeTags(listOf("#عمده", " عمده ", "همسايه", "همسایه", "", "علی   آباد")))
    assertEquals(listOf("عمده", "بدقول"), CustomerCrm.parseTags("عمده، #بدقول,عمده"))

    val d = ok(CustomerCrm.setProfile(d0, "c1", CustomerCrm.Profile(tags = listOf("#عمده", "همسایه")), "2026-10-19", 1, ::newId))
    val c1 = d.debtors.first { it.id == "c1" }
    assertEquals(listOf("عمده", "همسایه"), c1.tags)
    assertTrue(CustomerCrm.hasTag(c1, "#همسايه"))
    assertTrue(CustomerCrm.matches(c1, "#عمد"))
    assertFalse(CustomerCrm.matches(d.debtors[1], "#عمد"))

    assertEquals("حداکثر 12 برچسب", failed(CustomerCrm.setProfile(d0, "c1", CustomerCrm.Profile(tags = (1..13).map { "t$it" }))))
    assertEquals("هر برچسب حداکثر 30 حرف", failed(CustomerCrm.setProfile(d0, "c1", CustomerCrm.Profile(tags = listOf("ب".repeat(31))))))
    //  برچسبِ خالی ⇒ null، نه فهرستِ خالی (دادهٔ قدیمی همان می‌ماند)
    val cleared = ok(CustomerCrm.setProfile(d, "c1", CustomerCrm.Profile(tags = listOf(" ", "#")), "2026-10-19", 1, ::newId))
    assertNull(cleared.debtors.first { it.id == "c1" }.tags)
  }

  @Test fun `گروه و برچسب‌ها با شمار، پرتکرار اول، مشتریِ ادغام‌شده بیرون`() {
    val d = d0.copy(
      debtors = d0.debtors + listOf(
        Debtor(id = "c3", name = "ج", tags = listOf("عمده", "همسایه"), group = "همکار"),
        Debtor(id = "c4", name = "د", tags = listOf("عمده"), group = "همكار"),
        Debtor(id = "c5", name = "ه", tags = listOf("عمده", "x"), group = "همکار", mergedInto = "c3"),
      ),
    )
    assertEquals(listOf("عمده" to 2, "همسایه" to 1), CustomerCrm.allTags(d))
    assertEquals(listOf("همکار" to 2), CustomerCrm.groups(d))
    assertEquals(listOf("c1", "c2", "c3", "c4"), CustomerCrm.active(d).map { it.id })
  }

  @Test fun `لیستِ قیمت — عمده فقط با قیمتِ عمدهٔ مثبت؛ تغییرش ردی در دفترچه دارد`() {
    val p = Product(id = "p1", salePrice = 100.0, wholesalePrice = 80.0)
    val noWholesale = p.copy(wholesalePrice = 0.0)
    val w = Debtor(id = "w", priceList = "wholesale")
    assertEquals(80.0, CustomerCrm.unitPrice(p, w), 0.0)
    assertEquals(100.0, CustomerCrm.unitPrice(noWholesale, w), 0.0)
    assertEquals(100.0, CustomerCrm.unitPrice(p, Debtor(id = "r")), 0.0)
    assertEquals(100.0, CustomerCrm.unitPrice(p, null), 0.0)

    val d = ok(CustomerCrm.setProfile(d0, "c1", CustomerCrm.Profile(priceList = "wholesale", group = "  عمده  فروش "), "2026-10-19", 7, ::newId))
    val c1 = d.debtors.first { it.id == "c1" }
    assertEquals("wholesale", c1.priceList)
    assertEquals("عمده فروش", c1.group)
    assertEquals(1, d.auditLog.size)
    val a = d.auditLog.single()
    assertEquals("price_list_set", a.type)
    assertEquals("c1", a.refId)
    assertEquals("2026-10-19", a.date)
    assertEquals("لیستِ قیمتِ «احمد»: خرده ⇐ عمده", a.notes)

    //  برگشت به خرده ⇒ null (همان رفتارِ همیشه)، باز با رد
    val back = ok(CustomerCrm.setProfile(d, "c1", CustomerCrm.Profile(group = "عمده فروش"), "2026-10-20", 8, ::newId))
    assertNull(back.debtors.first { it.id == "c1" }.priceList)
    assertEquals(2, back.auditLog.size)
    assertEquals("لیستِ قیمتِ ناشناخته", failed(CustomerCrm.setProfile(d0, "c1", CustomerCrm.Profile(priceList = "vip"))))
    //  بی تغییر ⇒ همان داده، بی رد
    assertSame(back, ok(CustomerCrm.setProfile(back, "c1", CustomerCrm.profileOf(back.debtors.first { it.id == "c1" }), "2026-10-21", 9, ::newId)))
  }

  @Test fun `رضایت برای پیامِ تبلیغاتی — سند در دفترچه، فقط بله‌ی صریح گیرنده است`() {
    var d = ok(CustomerCrm.setMarketingConsent(d0, "c1", true, "حضوری", "2026-10-19", 3, ::newId))
    assertEquals(true, d.debtors.first { it.id == "c1" }.marketingConsent)
    val a = d.auditLog.single()
    assertEquals("marketing_consent", a.type)
    assertEquals("رضایتِ «احمد» برای پیامِ تبلیغاتی: نپرسیده ⇐ بله — حضوری", a.notes)
    //  همان مقدار ⇒ ردِ دوم نه
    assertSame(d, ok(CustomerCrm.setMarketingConsent(d, "c1", true, "تلفنی", "2026-10-19", 3, ::newId)))
    //  «بله» بی منبع سند ندارد ⇒ رد؛ داده دست نمی‌خورد
    for (blank in listOf("", "   ")) {
      assertEquals("برای «بله» منبعِ رضایت را بنویسید (مثلاً «حضوری» یا «تلفنی»)", failed(CustomerCrm.setMarketingConsent(d, "c2", true, blank, "2026-10-19", 4, ::newId)))
    }

    d = ok(CustomerCrm.setMarketingConsent(d, "c2", false, "", "2026-10-19", 4, ::newId))
    d = d.copy(debtors = d.debtors + Debtor(id = "c3", name = "بی‌شماره", marketingConsent = true) + Debtor(id = "c4", name = "ادغامی", phone = "+93788000000", marketingConsent = true, mergedInto = "c1"))
    assertEquals(listOf("c1"), CustomerCrm.promoRecipients(d).map { it.id })
    assertEquals("نه", CustomerCrm.consentText(false))
  }

  @Test fun `شناسهٔ ماندگار زنجیره را دنبال می‌کند و در حلقه گیر نمی‌کند`() {
    val d = ShopData(
      debtors = listOf(
        Debtor(id = "a", mergedInto = "b"), Debtor(id = "b", mergedInto = "c"), Debtor(id = "c"),
        Debtor(id = "x", mergedInto = "y"), Debtor(id = "y", mergedInto = "x"),
        Debtor(id = "z", mergedInto = "gone"),
      ),
    )
    assertEquals("c", CustomerCrm.resolve(d, "a"))
    assertEquals("c", CustomerCrm.resolve(d, "c"))
    //  حلقه ⇒ کوچک‌ترین شناسه، از هر سرش
    assertEquals("x", CustomerCrm.resolve(d, "x"))
    assertEquals("x", CustomerCrm.resolve(d, "y"))
    assertEquals("z", CustomerCrm.resolve(d, "z"))
    assertEquals("nope", CustomerCrm.resolve(d, "nope"))
    assertEquals("این مشتری با مشتریِ دیگری ادغام شده است", failed(CustomerCrm.setProfile(d, "a", CustomerCrm.Profile())))
  }
}
