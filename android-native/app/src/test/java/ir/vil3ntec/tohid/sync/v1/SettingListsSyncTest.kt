package ir.vil3ntec.tohid.sync.v1

import ir.vil3ntec.tohid.data.DEFAULT_UNITS
import ir.vil3ntec.tohid.data.ShopData
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  F0-04 — دسته‌های کالا، واحدها و دسته‌های مصارف هم همگام می‌شوند (پلهٔ ۳).
 *
 *  تا پلهٔ ۲ `LedgerDiff` این سه را عمداً کنار می‌گذاشت و سرور هم برایشان
 *  جدول نداشت؛ دسته‌ای که روی گوشی ساخته می‌شد روی کامپیوتر و وب نبود.
 *
 *  ⚠️ قرینهٔ این‌ها روی وب و با سرورِ واقعی (دو دستگاه) در
 *  `server/test/sync-setting-lists.test.js` است. شناسهٔ ردیف در دو زبان
 *  باید یکی باشد — همان‌جا با `crypto`ِ Node ساخته شده و این‌جا ثابت آمده.
 */
class SettingListsSyncTest {

  private fun tree(d: ShopData) = LedgerDiff.toJson(d)

  private fun names(o: JsonObject, key: String): List<String> =
    (o[key] as JsonArray).map { (it as JsonPrimitive).content }

  @Test
  fun `شناسهٔ ردیف همان sha256ِ نام است — یکی با وب و سرور`() {
    assertTrue("فهرست‌ها از پلهٔ ۳ می‌روند", LedgerDiff.SCHEMA_VERSION >= 3)
    assertEquals(listOf("productCategories", "productUnits", "expenseCategories"), LedgerDiff.LIST_COLLECTIONS)
    //  از Node: crypto.createHash('sha256').update('نوشیدنی').digest('hex').slice(0, 32)
    assertEquals("4139e35db2389aeade6e2fc223d80108", LedgerDiff.listRowId("نوشیدنی"))
    assertEquals("eac5a12c89920d0b050218df8ef1e040", LedgerDiff.listRowId("بسته"))
  }

  @Test
  fun `ساختنِ دسته یک insert می‌سازد و حذفش یک delete`() {
    val one = ShopData(productCategories = listOf("لبنیات"))
    val two = one.copy(productCategories = listOf("لبنیات", " نوشیدنی "))
    val add = LedgerDiff.diff(LedgerDiff.shadowOf(tree(one)), tree(two))
    assertEquals(1, add.size)
    assertEquals("productCategories", add[0].table)
    assertEquals(SyncOp.INSERT, add[0].type)
    assertEquals("4139e35db2389aeade6e2fc223d80108", add[0].rowId)
    assertEquals(buildJsonObject { put("name", JsonPrimitive("نوشیدنی")) }, add[0].fields)

    val del = LedgerDiff.diff(LedgerDiff.shadowOf(tree(one)), tree(one.copy(productCategories = emptyList())))
    assertEquals(listOf("productCategories" to SyncOp.DELETE), del.map { it.table to it.type })
    assertNull(del[0].fields)
  }

  @Test
  fun `سایهٔ پیش از پلهٔ ۳ بی این کلیدها ⇒ همهٔ نام‌ها یک بار درج، نه حذف`() {
    val data = ShopData(productUnits = listOf("عدد", "بسته"), expenseCategories = emptyList())
    val oldShadow = buildJsonObject {
      for (k in LedgerDiff.COLLECTIONS) put(k, JsonArray(emptyList()))
    }
    val ops = LedgerDiff.diff(oldShadow, tree(data))
    assertEquals(listOf("productUnits" to SyncOp.INSERT, "productUnits" to SyncOp.INSERT), ops.map { it.table to it.type })
    //  دفتری که کلید را اصلاً ندارد چیزی را پاک نمی‌کند
    val noKey = JsonObject(tree(data).filterKeys { it != "productUnits" })
    assertTrue(LedgerDiff.diff(LedgerDiff.shadowOf(tree(data)), noKey).none { it.table == "productUnits" })
  }

  @Test
  fun `opِ رسیده روی فهرستِ نام می‌نشیند، تکراری نمی‌سازد و پژواک ندارد`() {
    val mine = tree(ShopData(productCategories = listOf("لبنیات"), expenseCategories = listOf("برق", "کرایه")))
    val insert = SyncOp("o1", 1, "productCategories", LedgerDiff.listRowId("نوشیدنی"), SyncOp.INSERT,
      buildJsonObject { put("name", JsonPrimitive("نوشیدنی")) })
    val delete = SyncOp("o2", 2, "expenseCategories", LedgerDiff.listRowId("برق"), SyncOp.DELETE, null)
    val after = LedgerDiff.applyRemote(mine, listOf(insert, insert, delete))
    val data = LedgerDiff.fromJson(after)
    assertEquals(listOf("لبنیات", "نوشیدنی"), data.productCategories)
    assertEquals(listOf("کرایه"), data.expenseCategories)
    assertEquals(0, LedgerDiff.diff(LedgerDiff.shadowOf(after), LedgerDiff.toJson(data)).size)
  }

  @Test
  fun `سایهٔ بی کلید پیش‌فرض‌های مدل را نمی‌گیرد — نامِ محلی هنوز می‌رود`() {
    //  سایهٔ پلهٔ ۲: فقط COLLECTIONS
    val oldShadow = buildJsonObject { for (k in LedgerDiff.COLLECTIONS) put(k, JsonArray(emptyList())) }
    val remote = SyncOp("o1", 1, "productUnits", LedgerDiff.listRowId("جعبه"), SyncOp.INSERT,
      buildJsonObject { put("name", JsonPrimitive("جعبه")) })

    //  opِ بی‌ربط ⇒ کلیدِ فهرست در سایه پیدا نمی‌شود (نه با پیش‌فرض‌های مدل)
    val unrelated = SyncOp("o0", 1, "products", "p-1", SyncOp.INSERT, buildJsonObject { put("name", JsonPrimitive("چای")) })
    val s1 = LedgerDiff.shadowOf(LedgerDiff.applyRemote(oldShadow, listOf(unrelated)))
    assertNull(s1["productUnits"])
    assertNull(s1["expenseCategories"])

    //  opِ فهرست ⇒ فقط همان نامِ رسیده، نه هفت واحدِ پیش‌فرض
    val s2 = LedgerDiff.shadowOf(LedgerDiff.applyRemote(oldShadow, listOf(remote)))
    assertEquals(listOf("جعبه"), names(s2, "productUnits"))

    //  و دفترِ این دستگاه (پیش‌فرض‌ها + جعبه) نام‌های خودش را می‌فرستد
    val local = tree(ShopData(productUnits = DEFAULT_UNITS + "جعبه"))
    val ops = LedgerDiff.diff(s2, local).filter { it.table == "productUnits" }
    assertEquals(DEFAULT_UNITS.size, ops.size)
    assertTrue(ops.all { it.type == SyncOp.INSERT })
  }

  @Test
  fun `Snapshot فهرستِ سرور را جای پیش‌فرضِ گوشیِ نو می‌نشاند`() {
    val snap = buildJsonObject {
      put("cursor", JsonPrimitive(9))
      put("tables", buildJsonObject {
        put("productUnits", JsonArray(listOf("بسته", "عدد").map { n ->
          buildJsonObject {
            put("id", JsonPrimitive(LedgerDiff.listRowId(n)))
            put("data", buildJsonObject { put("name", JsonPrimitive(n)) })
          }
        }))
      })
    }
    val out = LedgerDiff.fromJson(LedgerDiff.fromSnapshot(snap, tree(ShopData())))
    assertEquals("ترتیبِ محلی برای نام‌های مشترک", listOf("عدد", "بسته"), out.productUnits)
    assertEquals("فهرستی که سرور ندارد دست نمی‌خورد", ShopData().expenseCategories, out.expenseCategories)

    val union = LedgerDiff.unionRows(tree(ShopData(productCategories = listOf("لبنیات"))), LedgerDiff.fromSnapshot(snap, JsonObject(emptyMap())))
    assertEquals(listOf("عدد", "کیلوگرم", "گرم", "لیتر", "متر", "کارتن", "بسته"), LedgerDiff.fromJson(union).productUnits)
    assertEquals(listOf("لبنیات"), LedgerDiff.fromJson(union).productCategories)
  }
}
