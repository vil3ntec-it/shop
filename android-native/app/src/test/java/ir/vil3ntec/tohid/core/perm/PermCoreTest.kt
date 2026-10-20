package ir.vil3ntec.tohid.core.perm

import ir.vil3ntec.tohid.data.PermPolicy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 *  بردارهای مشترکِ `core/test-vectors/perm-v1.json` روی `PermCore` — همان فایلی که
 *  `server/test/perm-core.test.js` روی `license/perm-core.js` و نسخهٔ سرور می‌دواند.
 *  و آزمونِ مرجع: بی لایه، تصمیمِ هسته همان `!isStaff`ِ امروزِ `ShopRole` است.
 */
class PermCoreTest {

  private val v: JsonObject = run {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/perm-v1.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/perm-v1.json پیدا نشد")
  }

  private fun list(name: String) = v.getValue(name).jsonArray.map { it.jsonObject }
  private fun JsonObject.s(k: String) = getValue(k).jsonPrimitive.content
  private fun JsonObject.layers() = PermPolicy.tree(getValue("layers")) as List<*>

  @Suppress("UNCHECKED_CAST")
  private fun capsOf(o: Any?): Map<String, Double> = (o as Map<String, Any?>).mapValues { (it.value as Number).toDouble() }

  private fun resolvedOf(o: Any?): PermCore.Resolved {
    val m = o as Map<*, *>
    return PermCore.Resolved(m["mode"] as String, capsOf(m["caps"]).toSortedMap())
  }

  @Test fun versionAndKeys() {
    assertEquals(v.getValue("version").jsonPrimitive.content.toInt(), PermCore.VERSION)
    assertEquals(v.getValue("keys").jsonArray.map { it.jsonPrimitive.content }, PermCore.keys())
  }

  @Test fun defaults() {
    for (c in list("defaults")) {
      val out = c.getValue("out").let { if (it is JsonNull) null else it.jsonPrimitive.content }
      assertEquals(c.toString(), out, PermCore.defaultMode(c.s("role"), c.s("key")))
    }
  }

  @Test fun resolve() {
    for (c in list("resolve")) {
      assertEquals(c.toString(), resolvedOf(PermPolicy.tree(c.getValue("out"))), PermCore.resolve(c.s("role"), c.layers(), c.s("key")))
    }
  }

  @Test fun evaluate() {
    for (c in list("evaluate")) {
      val req = (PermPolicy.tree(c.getValue("req")) as Map<*, *>?)
        ?.entries?.associate { (k, x) -> k as String to (x as Number).toDouble() }
      val o = PermPolicy.tree(c.getValue("out")) as Map<*, *>
      val want = PermCore.Decision(
        o["decision"] as String, o["reason"] as String, o["cap"] as String?, (o["limit"] as Number?)?.toDouble(),
      )
      assertEquals(c.toString(), want, PermCore.evaluate(c.s("role"), c.layers(), c.s("key"), req))
    }
  }

  @Test fun effective() {
    for (c in list("effective")) {
      val want = (PermPolicy.tree(c.getValue("out")) as Map<*, *>).entries
        .associate { (k, x) -> k as String to resolvedOf(x) }
      assertEquals(c.toString(), want, PermCore.effective(c.s("role"), c.layers()))
      assertEquals(want.keys.toList(), PermCore.effective(c.s("role"), c.layers()).keys.toList())
    }
  }

  @Test fun validate() {
    for (c in list("validate")) {
      val out = c.getValue("out").let { if (it is JsonNull) null else it.jsonPrimitive.content }
      assertEquals(c.toString(), out, PermCore.validate(c.s("role"), c.s("key"), PermPolicy.tree(c.getValue("override"))))
    }
  }

  /** درصدِ تخفیف برای سقف (صدمِ درصد، با بخششِ نیم افغانی) — همان `discountBp`ِ JS */
  @Test fun discountBp() {
    for (c in list("discount_bp")) {
      val d = c.getValue("discount").jsonPrimitive.content.toLong()
      val t = c.getValue("total").jsonPrimitive.content.toLong()
      assertEquals(c.toString(), c.getValue("out").jsonPrimitive.content.toLong(), PermCore.discountBp(d, t))
    }
  }

  /** جدول‌های نوشتنیِ هر نقش (A16) — همان `canWrite`/`writable`ِ JS */
  @Test fun writes() {
    for (c in list("writes")) {
      assertEquals(c.toString(), c.getValue("out").jsonPrimitive.content.toBoolean(), PermCore.canWrite(c.s("role"), c.s("table")))
    }
    for (c in list("writable")) {
      val want: Any? = when (val o = c.getValue("out")) {
        is JsonNull -> null
        is kotlinx.serialization.json.JsonArray -> o.map { it.jsonPrimitive.content }
        else -> o.jsonPrimitive.content
      }
      assertEquals(c.toString(), want, PermCore.writable(c.s("role")))
    }
  }

  /** نقش‌های تازه هیچ‌کدام از سه نقشِ قدیم را عوض نکرده‌اند (آزمونِ مرجع) */
  @Test fun legacyRolesUnchanged() {
    assertEquals(listOf("owner", "manager", "staff"), PermCore.ROLES.take(3))
    for (role in listOf("owner", "manager", "staff")) {
      assertEquals(role, PermCore.ALL_TABLES, PermCore.writable(role))
      assertEquals(role, true, PermCore.canWrite(role, "anything"))
    }
    assertEquals(false, PermCore.canWrite("courier", "sales"))
    assertEquals(emptyList<String>(), PermCore.writable("accountant"))
  }

  /** «نیازمند تأیید» برای شاگرد پذیرفته است ولی «مجاز» نه (بخشِ ۱۰.۱) */
  @Test fun staffApproveButNeverAllow() {
    val approve = listOf(mapOf("sales.discount" to mapOf("mode" to "approve")))
    assertEquals("approve", PermCore.resolve("staff", approve, "sales.discount").mode)
    assertEquals(false, PermCore.allows("staff", approve, "sales.discount"))
    val allow = listOf(mapOf("sales.discount" to mapOf("mode" to "allow")))
    assertEquals("deny", PermCore.resolve("staff", allow, "sales.discount").mode)
    assertEquals("not_approvable", PermCore.validate("staff", "settings.write", mapOf("mode" to "approve")))
  }

  /** آزمونِ مرجع: canDiscount / canCancelSale / canSeeMoney / canOpenSettings ِ امروز = !isStaff */
  @Test fun shopRoleCharacterisation() {
    val keys = listOf("sales.discount", "sales.cancel", "reports.money", "settings.write")
    for (role in listOf("owner", "manager", "staff")) {
      for (k in keys) {
        assertEquals("$role / $k", role != "staff", PermPolicy.allows(role, null, k))
        assertEquals("$role / $k", role != "staff", PermPolicy.allows(role, """{"v":1,"layers":[{},{}]}""", k))
        assertEquals("$role / $k (خراب)", role != "staff", PermPolicy.allows(role, "{not json", k))
      }
    }
  }

  @Test fun policyFromServerOnlyNarrows() {
    val p = """{"v":1,"layers":[{"settings.write":{"mode":"deny"}},{"reports.money":{"mode":"allow"}}]}"""
    assertEquals(false, PermPolicy.allows("manager", p, "settings.write"))
    assertEquals(true, PermPolicy.allows("owner", p, "settings.write"))
    assertEquals(false, PermPolicy.allows("staff", """{"layers":[{"sales.discount":{"mode":"allow"}}]}""", "sales.discount"))
    assertEquals(true, PermPolicy.allows("staff", p, "reports.money"))
  }
}
