package ir.vil3ntec.tohid.ui.v3.analytics

import ir.vil3ntec.tohid.core.audit.AuditChain
import ir.vil3ntec.tohid.core.i18n.StrReports
import ir.vil3ntec.tohid.data.AuditEntry
import ir.vil3ntec.tohid.data.AuditSeal
import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Purchase
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.Supplier
import ir.vil3ntec.tohid.data.WarehouseEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** سابقهٔ عملیات: تراشه‌های سریع، فیلتر، گروهِ روزانه و مقصدِ هر ردیف */
class AuditModelTest {
  private fun e(id: String, type: String, date: String = "2026-10-24", ref: String? = null, at: Long = 0) =
    AuditEntry(id, type = type, date = date, refId = ref, createdAt = at)

  @Test
  fun `پنج نوعِ پرتکرار به ترتیبِ شمار و سپس ترتیبِ جدول`() {
    val log = listOf(e("1", "sale"), e("2", "sale"), e("3", "return"), e("4", "purchase"), e("5", "cancel_sale"),
      e("6", "price_change"), e("7", "data_export"), e("8", "data_export"))
    assertEquals(listOf("sale", "data_export", "cancel_sale", "return", "purchase"), AuditModel.quickKinds(log))
    val sheet = AuditModel.sheetKinds(log + e("9", "mystery"))
    assertEquals(AuditModel.KINDS.size + 1, sheet.size)
    assertEquals("mystery" to 1, sheet.last())
    assertEquals("sale" to 2, sheet.first())
  }

  @Test
  fun `فیلتر تازه‌ترین را اول و گروه را روز به روز می‌دهد`() {
    val log = listOf(e("a", "sale", "2026-10-23", at = 5), e("b", "sale", "2026-10-24", at = 1), e("c", "return", "2026-10-24", at = 9))
    assertEquals(listOf("c", "b", "a"), AuditModel.filter(log, null).map { it.id })
    assertEquals(listOf("b", "a"), AuditModel.filter(log, "sale").map { it.id })
    val days = AuditModel.byDay(AuditModel.filter(log, null))
    assertEquals(listOf("2026-10-24", "2026-10-23"), days.map { it.first })
    assertEquals(2, days.first().second.size)
  }

  @Test
  fun `پاک‌کردن‌ها قرصِ حذف دارند`() {
    assertTrue(AuditModel.isDeletion("delete_product"))
    assertTrue(AuditModel.isDeletion("expense_delete"))
    assertFalse(AuditModel.isDeletion("sale"))
  }

  @Test
  fun `مقصد فقط وقتی مورد هنوز در دفتر است`() {
    val d = ShopData(
      sales = listOf(Sale("s1")),
      products = listOf(Product("p1")),
      debtors = listOf(Debtor("c1")),
      transactions = listOf(DebtTransaction("t1", debtorId = "c1")),
      suppliers = listOf(Supplier("sp1")),
      purchases = listOf(Purchase("pu1", supplierId = "sp1")),
      warehouseEntries = listOf(WarehouseEntry("w1", productId = "p1")),
    )
    assertEquals("sales/s1", AuditModel.target(e("1", "cancel_sale", ref = "s1"), d))
    assertNull(AuditModel.target(e("2", "sale", ref = "gone"), d))
    assertEquals("products/p1", AuditModel.target(e("3", "price_change", ref = "p1"), d))
    assertEquals("debtors/c1", AuditModel.target(e("4", "customer_payment", ref = "c1"), d))
    assertEquals("debtors/c1", AuditModel.target(e("5", "debt_due_set", ref = "t1"), d))
    assertEquals("purchasing/suppliers/sp1", AuditModel.target(e("6", "purchase", ref = "pu1"), d))
    assertEquals("stock/p1", AuditModel.target(e("7", "stock_adjustment", ref = "w1"), d))
    assertEquals("sales/drawer", AuditModel.target(e("8", "shift_open"), d))
    assertNull(AuditModel.target(e("9", "delete_product", ref = "p9"), d))
    assertNull(AuditModel.target(e("10", "data_export"), d))
  }

  /* ───── STF-09: چه کسی، کجا، و سلامتِ زنجیره ───── */

  private val me = "0123456789abcdef0123456789abcdef"

  private var savedAnchors: AuditChain.Anchors? = null
  private val store = object : AuditSeal.AnchorStore {
    override fun read() = savedAnchors
    override fun write(anchors: AuditChain.Anchors) { savedAnchors = anchors }
  }

  private fun sealed(vararg rows: AuditEntry): List<AuditEntry> {
    val plan = AuditSeal.plan(ShopData(), ShopData(), ShopData(auditLog = rows.toList()), { AuditSeal.Session(AuditChain.Stamp("usr_7", "cashier", me), true) }, store)
    plan.commit()
    return plan.data.auditLog
  }

  @Test
  fun `ردیفِ کهنه بی مُهر نامعلوم است و ردیفِ تازه کاربر و نقش و دستگاه دارد`() {
    val old = e("1", "sale")
    val w0 = AuditModel.who(old, emptyMap(), me)
    assertEquals(StrReports.unknown, w0.user)
    assertNull(w0.role)
    assertEquals(StrReports.unknown, w0.device)
    val fresh = sealed(e("2", "sale")).single()
    val w1 = AuditModel.who(fresh, mapOf("usr_7" to "علی"), me)
    assertEquals("علی", w1.user)
    assertEquals(AuditModel.roleName("cashier"), w1.role)
    assertEquals(StrReports.thisDevice, w1.device)
    //  دستگاهِ دیگر با شش نویسهٔ نخستِ شناسه؛ کاربرِ ناشناخته با شناسه‌اش
    val other = AuditModel.who(fresh.copy(deviceId = "ffeedd00112233"), emptyMap(), me)
    assertEquals(StrReports.deviceShort("ffeedd"), other.device)
    assertEquals("usr_7", other.user)
    assertTrue(AuditModel.whoLine(w1).contains("علی"))
  }

  @Test
  fun `وضعیتِ زنجیره سالم، شکسته از ردیفِ N یا هنوز شروع‌نشده`() {
    assertEquals(null, AuditModel.chainLine(AuditSeal.verify(listOf(e("1", "sale")), null), me).ok)
    val rows = sealed(e("a", "sale"), e("b", "sale"), e("c", "sale"))
    val ok = AuditModel.chainLine(AuditSeal.verify(rows, savedAnchors), me)
    assertEquals(true, ok.ok)
    assertEquals(StrReports.chainOk, ok.title)
    //  ویرایش ⇐ «زنجیره از ردیفِ ۲ شکسته است» و همان ردیف نشان می‌خورد
    val edited = rows.toMutableList().also { it[1] = it[1].copy(notes = "دست‌خورده") }
    val report = AuditSeal.verify(edited, savedAnchors)
    val bad = AuditModel.chainLine(report, me)
    assertEquals(false, bad.ok)
    assertEquals(StrReports.chainBroken(2), bad.title)
    assertTrue(bad.body!!.contains(StrReports.chainEdited))
    assertTrue(bad.body!!.contains(StrReports.thisDevice))
    assertEquals("b", AuditModel.brokenId(report))
    //  حذف ⇐ «پاک شده»
    assertTrue(AuditModel.chainLine(AuditSeal.verify(rows.filter { it.id != "b" }, savedAnchors), me).body!!.contains(StrReports.chainDeleted))
  }
}
