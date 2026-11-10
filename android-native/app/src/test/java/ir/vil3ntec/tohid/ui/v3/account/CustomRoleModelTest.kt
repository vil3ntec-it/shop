package ir.vil3ntec.tohid.ui.v3.account

import ir.vil3ntec.tohid.core.model.CatalogEntryDto
import ir.vil3ntec.tohid.core.model.CustomRoleDto
import ir.vil3ntec.tohid.core.model.CustomRoleRefDto
import ir.vil3ntec.tohid.core.model.MemberPermsDto
import ir.vil3ntec.tohid.core.model.ShopPermsDto
import ir.vil3ntec.tohid.ui.v3.account.team.CustomRoleModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** STF-01 — فرمِ «نقشِ سفارشی»: کلیدها و پیش‌فرض‌ها از کاتالوگِ سرور، لایهٔ فرستادنی فقط تغییرها */
class CustomRoleModelTest {

  private fun d(vararg modes: String): Map<String, String> =
    listOf("owner", "manager", "staff", "cashier", "storekeeper", "picker", "accountant").zip(modes.toList()).toMap()

  //  همان شکلِ کاتالوگِ `GET /api/shop/permissions` (پرچمِ قرض روشن)
  private val catalog = listOf(
    CatalogEntryDto("audit.view", "restrict", false, d("allow", "allow", "deny", "deny", "deny", "deny", "allow"), false, emptyList()),
    CatalogEntryDto("reports.money", "full", true, d("allow", "allow", "deny", "deny", "deny", "deny", "allow"), false, emptyList()),
    CatalogEntryDto("returns.create", "restrict", false, d("allow", "allow", "allow", "allow", "deny", "deny", "deny"), true, listOf("maxAmount")),
    CatalogEntryDto("sales.credit", "restrict", false, d("allow", "allow", "allow", "allow", "deny", "deny", "deny"), true, listOf("maxAmount")),
    CatalogEntryDto("sales.discount", "restrict", false, d("allow", "allow", "deny", "deny", "deny", "deny", "deny"), true, listOf("maxPct", "maxAmount")),
    CatalogEntryDto("settings.write", "restrict", false, d("allow", "allow", "deny", "deny", "deny", "deny", "deny"), false, emptyList()),
    CatalogEntryDto("drawer.open", "none", false, d("allow", "allow", "deny", "deny", "deny", "deny", "deny"), true, emptyList()),
  )

  private fun json(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

  @Test
  fun `ردیف‌ها فقط کلیدهای معنادارِ نقشِ پایه‌اند و هرگز گزینهٔ گشادتر از نقش ندارند`() {
    val rows = CustomRoleModel.rows(catalog, "staff").associateBy { it.key }
    //  تنظیمات برای شاگرد نه پیش‌فرض دارد نه «با تأیید» ⇒ ردیفی نیست؛ کلیدِ «none» ِ سرور هم نه
    assertNull(rows["settings.write"])
    assertNull(rows["drawer.open"])
    assertNull(rows["audit.view"])
    //  تخفیفِ شاگرد: «با تأیید» یا «ممنوع»، نه «مجاز»
    assertEquals(listOf("approve", "deny"), rows.getValue("sales.discount").options)
    assertEquals("deny", rows.getValue("sales.discount").mode)
    assertEquals(listOf("allow", "approve", "deny"), rows.getValue("sales.credit").options)
    assertEquals("allow", rows.getValue("sales.credit").mode)
    //  کلیدِ «full» (فقط نمایش) باز و بسته می‌شود
    assertEquals(listOf("allow", "deny"), rows.getValue("reports.money").options)
    //  مدیر تنظیمات را دارد
    assertNotNull(CustomRoleModel.rows(catalog, "manager").firstOrNull { it.key == "settings.write" })
  }

  @Test
  fun `لایه فقط تغییرها را دارد و مبلغِ سقف به Minor می‌رود`() {
    val rows = CustomRoleModel.rows(catalog, "staff").map {
      when (it.key) {
        "sales.discount" -> it.copy(mode = "approve")
        "sales.credit" -> it.copy(amount = "۵۰۰۰")
        "returns.create" -> it.copy(mode = "deny", amount = "100")
        else -> it
      }
    }
    assertEquals(
      json("""{"returns.create":{"mode":"deny"},"sales.credit":{"caps":{"maxAmount":500000}},"sales.discount":{"mode":"approve"}}"""),
      CustomRoleModel.layer(rows),
    )
    //  بی تغییر ⇒ لایهٔ خالی (همان رفتارِ نقشِ پایه)
    assertEquals(JsonObject(emptyMap()), CustomRoleModel.layer(CustomRoleModel.rows(catalog, "cashier")))
  }

  @Test
  fun `لایهٔ ذخیره‌شده دوباره همان ردیف‌ها و همان لایه را می‌دهد`() {
    val saved = json("""{"sales.discount":{"caps":{"maxPct":5,"maxAmount":150050}},"audit.view":{"mode":"deny"}}""")
    val rows = CustomRoleModel.rows(catalog, "manager", saved).associateBy { it.key }
    assertEquals("5", rows.getValue("sales.discount").pct)
    assertEquals("1500.5", rows.getValue("sales.discount").amount)
    assertEquals("deny", rows.getValue("audit.view").mode)
    assertEquals(saved, CustomRoleModel.layer(CustomRoleModel.rows(catalog, "manager", saved)))
  }

  @Test
  fun `نام و سقفِ نادرست ذخیره نمی‌شود`() {
    assertNotNull(CustomRoleModel.nameProblem("  "))
    assertNotNull(CustomRoleModel.nameProblem("x".repeat(41)))
    assertNull(CustomRoleModel.nameProblem("صندوق‌دارِ صبح"))
    val r = CustomRoleModel.rows(catalog, "manager").first { it.key == "sales.discount" }
    assertTrue(CustomRoleModel.capProblem(r.copy(pct = "120")))
    assertTrue(CustomRoleModel.capProblem(r.copy(amount = "-1")))
    assertTrue(CustomRoleModel.capProblem(r.copy(amount = "ده")))
    assertFalse(CustomRoleModel.capProblem(r.copy(pct = "", amount = "")))
    //  سقف فقط برای «مجاز» معنا دارد
    assertFalse(CustomRoleModel.capProblem(r.copy(mode = "approve", pct = "120")))
  }

  @Test
  fun `عضوی که نقشِ پایه‌اش یکی از نقش‌های تازه است کارمند است نه صاحب دکان`() {
    for (r in listOf("cashier", "storekeeper", "picker", "accountant")) {
      assertEquals(r, AccountFormat.Role.STAFF, AccountFormat.roleOf(r))
      assertEquals(CustomRoleModel.baseLabel(r), AccountFormat.teamRole(r))
    }
    assertEquals(AccountFormat.Role.OWNER, AccountFormat.roleOf("owner"))
  }

  @Test
  fun `برگهٔ عضو فقط با پرچمِ روشن، و نقشِ کنونی فقط اگر سرور گفته`() {
    val roles = listOf(CustomRoleDto("crl_1", "ارشد", "staff"), CustomRoleDto("crl_2", "حسابِ شب", "accountant"))
    val on = ShopPermsDto(catalog = catalog, customRoles = roles)
    val off = ShopPermsDto(catalog = catalog)
    val staff = MemberPermsDto(id = "m1", role = "staff", customRole = CustomRoleRefDto("crl_1", "ارشد"))
    assertTrue(CustomRoleModel.visibleFor(on, staff))
    assertFalse(CustomRoleModel.visibleFor(off, staff))
    assertFalse(CustomRoleModel.visibleFor(on, staff.copy(role = "owner")))
    assertFalse(CustomRoleModel.visibleFor(on, staff.copy(role = "courier")))
    assertEquals("crl_1", CustomRoleModel.currentOf(on, staff))
    assertNull(CustomRoleModel.currentOf(on, staff.copy(customRole = null)))
    val choices = CustomRoleModel.choices(roles)
    assertEquals(listOf(null, "crl_1", "crl_2"), choices.map { it.id })
    assertTrue(choices[2].label.startsWith("حسابِ شب"))
  }

  //  POS-40: سرور `sales.pay_fix` را با پرچمِ `shop.sync.pay_fix` ویرایش‌پذیر می‌کند (`staff-perms.FLAGGED`) ⇒ نامِ کار، نه کلیدِ خام
  @Test
  fun `اصلاحِ روشِ پرداخت در فرمِ مجوز نامِ خودش را دارد`() {
    assertEquals(ir.vil3ntec.tohid.core.i18n.StrPayFix.menu, CustomRoleModel.permLabel("sales.pay_fix"))
    assertEquals(CustomRoleModel.permLabel("sales.pay_fix"), ir.vil3ntec.tohid.ui.v3.account.team.MemberModel.permLabel("sales.pay_fix"))
  }
}
