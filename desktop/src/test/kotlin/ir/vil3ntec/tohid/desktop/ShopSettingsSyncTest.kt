package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.ShopDayChoice
import ir.vil3ntec.tohid.data.LedgerOwner
import ir.vil3ntec.tohid.data.ShopSettingsKeys
import ir.vil3ntec.tohid.data.ShopSettingsKeys.Scope
import ir.vil3ntec.tohid.data.ShopSettingsKeys.Value
import ir.vil3ntec.tohid.data.ShopSettingsPlan
import ir.vil3ntec.tohid.data.ShopSettingsSync
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 *  همگام‌سازیِ زندهٔ تنظیماتِ دکان (`ShopSettingsSync`، `ShopSettingsPlan`، `ShopSettingsKeys`) — DATA-SAFETY ۱۴۰۵/۰۸/۱۸.
 *
 *   • هر کلیدِ سه انبار دسته دارد و نوعِ دسته همان نوعِ خواننده است (کلیدِ تازهٔ بی دسته ⇒ سرخ).
 *   • نقشه: آخرین نویسنده (سرور) هر کلید جدا؛ نخستین بار مقدارِ سرور را می‌گیرد و فقط کلیدِ نبوده را می‌کارد؛
 *     کارمند نمی‌فرستد و به مقدارِ سرور برمی‌گردد؛ کلیدِ ناشناخته، دستگاهی یا نوعِ دیگر هرگز نمی‌نشیند.
 *   • با سرورِ ساختگی (همان قاعدهٔ `lib/shop-settings-sync.js`): پرچمِ خاموش هیچ؛ گوشیِ تازه تنظیمِ دکان را می‌گیرد؛
 *     کشو و آموزش هرگز نمی‌روند و نمی‌آیند.
 */
class ShopSettingsSyncTest {

  private val ctx = DesktopContext
  private val policy get() = ctx.getSharedPreferences(ShopSettingsKeys.POLICY, 0)
  private val state get() = ctx.getSharedPreferences("tohid-settings-sync", 0)
  private val owner get() = ctx.getSharedPreferences("tohid-ledger", 0)
  private var savedUser = ""
  private var savedShop = ""
  /** کلیدهای «مالِ دکان» و دو کلیدِ دستگاهیِ همین آزمون — پیش از آزمون نگه داشته و پس از آن برگردانده می‌شوند */
  private val ours = ShopSettingsKeys.SYNCED.map { it.name }.toSet() + setOf("multi_drawer", "training_mode")
  private var saved: Map<String, Any?> = emptyMap()

  @Before fun before() {
    savedUser = owner.getString("owner_user_id", "").orEmpty()
    savedShop = owner.getString("owner_shop_id", "").orEmpty()
    saved = policy.all.filterKeys { it in ours }
    LedgerOwner.remember(ctx, "u-sss", "shop-sss")
    wipe()
  }

  @After fun after() {
    wipe()
    val e = policy.edit()
    for ((k, v) in saved) when (v) {
      is Boolean -> e.putBoolean(k, v)
      is String -> e.putString(k, v)
      is Int -> e.putInt(k, v)
      is Long -> e.putLong(k, v)
      is Float -> e.putFloat(k, v)
      is Set<*> -> e.putStringSet(k, v.filterIsInstance<String>().toSet())
    }
    e.commit()
    LedgerOwner.remember(ctx, savedUser, savedShop)
  }

  private fun wipe() {
    val e = policy.edit()
    ours.forEach { e.remove(it) }
    e.commit()
    state.edit().clear().commit()
  }

  /* ------------------------------- دسته‌بندی ------------------------------- */

  /** کلیدهایی که کد با `get…`/`put…`/`remove`/`contains` روی سه انبار می‌خواند و می‌نویسد، با نوعِ هر کدام */
  private fun keysInSource(): Map<String, Set<String>> {
    val src = File("../android-native/app/src/main/java")
    assertTrue(src.isDirectory)
    val types = mapOf("Boolean" to "b", "String" to "s", "Int" to "i", "Long" to "l", "Float" to "f", "StringSet" to "set")
    //  پرونده‌هایی که انبار را کلی می‌خوانند (همین همگام‌سازی و پشتیبان)، و انبارِ دیگرِ همان پرونده
    val generic = setOf("ShopSettingsBundleAndroid.kt", "ShopSettingsSyncAndroid.kt", "ShopSettingsKeys.kt")
    //  `ShopDayChoiceAndroid.kt` هم `tohid-policy` (آغازِ هفته، پایانِ روز) را دارد و هم نسخهٔ محلیِ جدولِ تعطیلاتِ سرور را در
    //  انبارِ جدای `tohid-holidays` (LOC-04؛ نه تنظیمِ دکان، DATA-SAFETY §۶) — آن دو کلید مالِ انبارِ دیگرند
    val otherStore = mapOf("WhatsNewPrefAndroid.kt" to setOf("seen", "keys"), "ShopDayChoiceAndroid.kt" to setOf("table", "fetched_at"))
    val out = HashMap<String, MutableSet<String>>()
    src.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name !in generic }.forEach { f ->
      val text = f.readText()
      val store = ShopSettingsKeys.STORES.firstOrNull { "\"$it\"" in text } ?: return@forEach
      val consts = Regex("""val\s+([A-Z_][A-Z0-9_]*)\s*=\s*"([^"]*)"""").findAll(text).associate { it.groupValues[1] to it.groupValues[2] }
      Regex("""\.(get|put)(Boolean|StringSet|String|Int|Long|Float)\(\s*("([^"$]*)"|[A-Z_][A-Z0-9_]*)\s*,""").findAll(text).forEach { m ->
        val key = m.groupValues[4].ifEmpty { consts[m.groupValues[3]] ?: return@forEach }
        if (key in otherStore[f.name].orEmpty()) return@forEach
        out.getOrPut("$store/$key") { HashSet() } += types.getValue(m.groupValues[2])
      }
    }
    return out
  }

  @Test fun `هر کلیدِ سه انبار دسته دارد و نوعِ دسته همان نوعِ خواننده است`() {
    val found = keysInSource()
    assertTrue("اسکن چیزی پیدا نکرد", found.size > 40)
    val unknown = found.keys.filter { id -> ShopSettingsKeys.scopeOf(id.substringBefore('/'), id.substringAfter('/')) == null }
    assertEquals("کلیدِ بی دسته — در ShopSettingsKeys و DATA-SAFETY §۶ بیاید", emptyList<String>(), unknown.sorted())
    val wrongType = found.filter { (id, t) ->
      val k = ShopSettingsKeys.KEYS.firstOrNull { it.id == id } ?: return@filter false
      t != setOf(k.type)
    }
    assertEquals("نوعِ دسته باید همان نوعِ خواننده باشد", emptyMap<String, Set<String>>(), wrongType)
    //  هر کلیدِ فهرست هنوز در کد هست (نامش در پروندهٔ همان انبار؛ بعضی با کمکیِ `get(context, KEY)` خوانده می‌شوند)
    val texts = File("../android-native/app/src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }
      .map { it.readText() }.toList()
    val stale = ShopSettingsKeys.KEYS.filter { k -> texts.none { "\"${k.store}\"" in it && "\"${k.name}\"" in it } }.map { it.id }
    assertEquals("کلیدی که دیگر در کد نیست", emptyList<String>(), stale)
  }

  @Test fun `دسته‌ها — پول و قاعده مالِ دکان؛ کشو، چاپگر، بارکدخوان، صفحه و آموزش مالِ دستگاه؛ نسخهٔ سرور جدا`() {
    val p = ShopSettingsKeys.POLICY
    for (k in listOf("cash_rounding", "auto_fees", "return_window_days", "tax_quarter_rate_bp", "day_end_hour", "credit_gate", "loyalty")) {
      assertEquals(k, Scope.SHOP, ShopSettingsKeys.scopeOf(p, k))
    }
    for (k in listOf("multi_drawer", "multi_drawer_shift", "label_tools", "customer_card", "training_mode", "whats_new", "chat_media_guard", "report_keys")) {
      assertEquals(k, Scope.DEVICE, ShopSettingsKeys.scopeOf(p, k))
    }
    assertEquals(Scope.DEVICE, ShopSettingsKeys.scopeOf(ShopSettingsKeys.MODULES, "on"))
    assertEquals(Scope.SERVER, ShopSettingsKeys.scopeOf(p, "loyalty_shop:abc"))
    assertEquals(Scope.SERVER, ShopSettingsKeys.scopeOf(ShopSettingsKeys.RECEIPT, "profile:abc"))
    assertEquals(Scope.SERVER, ShopSettingsKeys.scopeOf(ShopSettingsKeys.RECEIPT, "v2:abc:at"))
    assertNull("ناشناخته ⇒ هرگز همگام نمی‌شود", ShopSettingsKeys.scopeOf(p, "something_new"))
    assertTrue(ShopSettingsKeys.SYNCED.all { it.store == p && it.name.matches(Regex("^[a-z0-9_]{1,64}$")) })
    assertTrue("سقفِ سرور ۵۰۰ کلید", ShopSettingsKeys.SYNCED.size < 500)
  }

  /* --------------------------------- نقشه --------------------------------- */

  private fun l(n: Long) = Value("l", JsonPrimitive(n))
  private fun i(n: Int) = Value("i", JsonPrimitive(n))
  private fun b(x: Boolean) = Value("b", JsonPrimitive(x))
  private fun r(key: String, t: String, v: JsonElement?, rev: Long) = ShopSettingsPlan.Remote(ShopSettingsKeys.POLICY, key, t, v, rev)
  private val cr = "tohid-policy/cash_rounding"
  private val rw = "tohid-policy/return_window_days"
  private val lo = "tohid-policy/loyalty"

  @Test fun `نقشه — آخرین نویسنده هر کلید جدا، تغییرِ این دستگاه می‌رود و بقیه مقدارِ سرور`() {
    val shadow = mapOf(cr to l(500), rw to i(7))
    val local = mapOf(cr to l(1000), rw to i(7))                    // این دستگاه گردکردن را عوض کرد
    val remote = listOf(r("cash_rounding", "l", JsonPrimitive(250), 5), r("return_window_days", "i", JsonPrimitive(14), 6))
    val plan = ShopSettingsPlan.plan(local, shadow, remote, first = false, canWrite = true)
    assertEquals(mapOf(cr to l(1000)), plan.push)
    assertEquals(mapOf(rw to i(14)), plan.apply)
    assertEquals(i(14), plan.shadow[rw])
  }

  @Test fun `نقشه — نخستین بار کلیدِ سرور گرفته می‌شود و فقط کلیدِ نبوده کاشته`() {
    val local = mapOf(cr to l(1000), lo to b(true))
    val remote = listOf(r("cash_rounding", "l", JsonPrimitive(500), 1))
    val plan = ShopSettingsPlan.plan(local, emptyMap(), remote, first = true, canWrite = true)
    assertEquals("پیش‌فرضِ این دستگاه روی همه نوشته نمی‌شود", mapOf(cr to l(500)), plan.apply)
    assertEquals(mapOf(lo to b(true)), plan.push)
  }

  @Test fun `نقشه — کارمند نمی‌فرستد و تغییرِ محلی‌اش به مقدارِ سرور برمی‌گردد`() {
    val plan = ShopSettingsPlan.plan(mapOf(cr to l(1)), mapOf(cr to l(500)), listOf(r("cash_rounding", "l", JsonPrimitive(500), 3)),
      first = false, canWrite = false)
    assertEquals(emptyMap<String, Value?>(), plan.push)
    assertEquals(mapOf(cr to l(500)), plan.apply)
  }

  @Test fun `نقشه — کلیدِ دستگاهی، ناشناخته یا نوعِ دیگر از سرور هرگز نمی‌نشیند؛ del برمی‌دارد`() {
    val remote = listOf(
      r("multi_drawer", "b", JsonPrimitive(true), 1),
      r("training_mode", "b", JsonPrimitive(true), 2),
      r("future_key", "s", JsonPrimitive("x"), 3),
      r("cash_rounding", "i", JsonPrimitive(5), 4),                  // نوعِ دیگر: getLong روی Int پرتاب می‌کرد
      r("return_window_days", "s", JsonPrimitive("7"), 5),
      r("loyalty", "del", JsonNull, 6),
    )
    val plan = ShopSettingsPlan.plan(mapOf(lo to b(true)), mapOf(lo to b(true)), remote, first = false, canWrite = true)
    assertEquals(mapOf(lo to null), plan.apply)
    assertEquals(emptyMap<String, Value?>(), plan.push)
  }

  @Test fun `نقشه — Float ِ برابر با عددِ صحیحِ سرور تغییر نیست`() {
    val k = ShopSettingsKeys.KEYS.first { it.name == "return_limit" }
    val mine = ShopSettingsKeys.encode(k, 2.0f)!!
    val theirs = ShopSettingsKeys.decode(k, "f", JsonPrimitive(2))!!
    assertTrue(mine.same(theirs))
    val plan = ShopSettingsPlan.plan(mapOf(k.id to mine), mapOf(k.id to mine), listOf(r("return_limit", "f", JsonPrimitive(2), 1)),
      first = false, canWrite = true)
    assertEquals(emptyMap<String, Value?>(), plan.apply)
    assertEquals(emptyMap<String, Value?>(), plan.push)
  }

  /* ---------------------- با سرورِ ساختگی (همان قاعدهٔ سرور) ---------------------- */

  /** همان `lib/shop-settings-sync.js`: rev ِ پیاپی، آخرین نویسنده برنده، همان مقدار هیچ */
  private class FakeServer(var enabled: Boolean = true, var write: Boolean = true) : ShopSettingsSync.Port {
    data class Row(val store: String, val key: String, val t: String, val v: JsonElement, val rev: Long)
    val rows = LinkedHashMap<String, Row>()
    var rev = 0L
    var pushes = 0
    fun set(key: String, t: String, v: JsonElement) { rows["tohid-policy/$key"] = Row("tohid-policy", key, t, v, ++rev) }
    override suspend fun pull(since: Long): JsonObject = if (!enabled) buildJsonObject { put("enabled", JsonPrimitive(false)) } else buildJsonObject {
      put("enabled", JsonPrimitive(true))
      put("rev", JsonPrimitive(rev))
      put("rows", buildJsonArray {
        rows.values.filter { it.rev > since }.sortedBy { it.rev }.forEach { x ->
          add(buildJsonObject {
            put("store", JsonPrimitive(x.store)); put("key", JsonPrimitive(x.key)); put("t", JsonPrimitive(x.t))
            put("v", x.v); put("rev", JsonPrimitive(x.rev))
          })
        }
      })
      put("permissions", buildJsonObject { put("write", JsonPrimitive(write)) })
    }
    override suspend fun push(body: JsonObject): JsonObject? {
      if (!enabled || !write) return null
      pushes++
      for (e in body["entries"] as JsonArray) {
        val o = e as JsonObject
        val id = "${o["store"]!!.jsonPrimitive.content}/${o["key"]!!.jsonPrimitive.content}"
        val t = o["t"]!!.jsonPrimitive.content
        val old = rows[id]
        if (old != null && old.t == t && old.v == o["v"]) continue
        rows[id] = Row(o["store"]!!.jsonPrimitive.content, o["key"]!!.jsonPrimitive.content, t, o["v"] ?: JsonNull, ++rev)
      }
      return buildJsonObject { put("enabled", JsonPrimitive(true)); put("rev", JsonPrimitive(rev)) }
    }
  }

  private fun cycle(server: FakeServer) = runBlocking { ShopSettingsSync.cycle(ctx, server) }

  @Test fun `پرچمِ خاموش (پیش‌فرضِ سرور) — هیچ تنظیمی نوشته یا فرستاده نمی‌شود`() {
    policy.edit().putLong("cash_rounding", 1000L).commit()
    val server = FakeServer(enabled = false).apply { enabled = true; set("return_window_days", "i", JsonPrimitive(30)); enabled = false }
    val rep = cycle(server)
    assertFalse(rep.enabled)
    assertEquals(0, server.pushes)
    assertFalse(policy.contains("return_window_days"))
    assertEquals(1000L, policy.getLong("cash_rounding", 0L))
    assertFalse(state.contains("shadow:shop-sss"))
  }

  @Test fun `صاحب — تغییرِ گردکردن می‌رود؛ تغییرِ دستگاهِ دیگر می‌آید؛ کشو و آموزش نه می‌روند نه می‌آیند`() {
    val server = FakeServer()
    policy.edit().putLong("cash_rounding", 1000L).putBoolean("multi_drawer", true).putBoolean("training_mode", true).commit()
    cycle(server)
    assertEquals(setOf("tohid-policy/cash_rounding"), server.rows.keys)
    assertEquals(JsonPrimitive(1000L), server.rows.getValue("tohid-policy/cash_rounding").v)

    //  دستگاهِ دیگر (مدیر) قاعدهٔ برگشت و گردکردن را عوض کرد؛ کسی هم کشو را روی سرور نوشت
    server.set("return_window_days", "i", JsonPrimitive(14))
    server.set("cash_rounding", "l", JsonPrimitive(250))
    server.set("multi_drawer", "b", JsonPrimitive(false))
    val rep = cycle(server)
    assertTrue(rep.enabled)
    assertEquals(14, policy.getInt("return_window_days", 0))
    assertEquals(250L, policy.getLong("cash_rounding", 0L))
    assertTrue("کشوی همین دستگاه دست نخورد", policy.getBoolean("multi_drawer", false))
    assertTrue(policy.getBoolean("training_mode", false))
    //  پایدار: دورِ بعد هیچ نمی‌فرستد
    val pushes = server.pushes
    cycle(server)
    assertEquals(pushes, server.pushes)
  }

  @Test fun `گوشیِ تازهٔ دکان — تنظیمِ دکان را می‌گیرد و پیش‌فرضِ خودش را روی همه نمی‌نویسد`() {
    val server = FakeServer()
    server.set("cash_rounding", "l", JsonPrimitive(500))
    server.set("loyalty", "b", JsonPrimitive(true))
    //  گوشیِ تازه یک مقدارِ محلیِ دیگر هم دارد (مثلاً از پشتیبانِ قدیم)
    policy.edit().putLong("cash_rounding", 1000L).putInt("day_end_hour", 4).commit()
    cycle(server)
    assertEquals(500L, policy.getLong("cash_rounding", 0L))
    assertTrue(policy.getBoolean("loyalty", false))
    assertEquals("کلیدی که سرور نداشت کاشته می‌شود", JsonPrimitive(4), server.rows.getValue("tohid-policy/day_end_hour").v)
    assertEquals(JsonPrimitive(500), server.rows.getValue("tohid-policy/cash_rounding").v)
  }

  @Test fun `کارمند — نمی‌فرستد و تغییرِ محلی‌اش به قاعدهٔ دکان برمی‌گردد`() {
    val server = FakeServer(write = false)
    server.set("cash_rounding", "l", JsonPrimitive(500))
    cycle(server)
    assertEquals(500L, policy.getLong("cash_rounding", 0L))
    policy.edit().putLong("cash_rounding", 1L).commit()
    cycle(server)
    assertEquals(0, server.pushes)
    assertEquals(500L, policy.getLong("cash_rounding", 0L))
  }

  @Test fun `برداشتنِ کلید روی دستگاهِ دیگر اینجا هم برمی‌دارد؛ نوعِ دیگر از سرور نادیده`() {
    val server = FakeServer()
    server.set("loyalty", "b", JsonPrimitive(true))
    cycle(server)
    assertTrue(policy.getBoolean("loyalty", false))
    server.rows["tohid-policy/loyalty"] = FakeServer.Row("tohid-policy", "loyalty", "del", JsonNull, ++server.rev)
    server.set("return_window_days", "s", JsonPrimitive("هفت"))
    cycle(server)
    assertFalse(policy.contains("loyalty"))
    assertFalse("رشته جای Int نمی‌نشیند", policy.contains("return_window_days"))
  }

  @Test fun `آغازِ هفته و تعطیلِ رسیده از دستگاهِ دیگر بی بازکردنِ دوبارهٔ برنامه در صفحه‌ها می‌نشیند (LOC-03)`() {
    try {
      ShopDayChoice.load(ctx)
      assertEquals(6, ShopDayChoice.weekStart)
      val server = FakeServer()
      server.set("week_start", "i", JsonPrimitive(7))
      server.set("closed_days", "s", JsonPrimitive("5,6"))
      cycle(server)
      assertEquals(7, policy.getInt("week_start", 0))
      //  بی `ShopDayChoice.load` ِ دوباره (همان بازکردنِ برنامه)
      assertEquals("آغازِ هفتهٔ رسیده در صفحه ننشست", 7, ShopDayChoice.weekStart)
      assertEquals(setOf(5, 6), ShopDayChoice.closedDays)
      //  کلیدِ دیگری از دکان ⇒ تنظیمِ هفته دست نمی‌خورد
      server.set("return_window_days", "i", JsonPrimitive(7))
      cycle(server)
      assertEquals(7, ShopDayChoice.weekStart)
      //  برداشتن روی دستگاهِ دیگر ⇒ دوباره شنبه
      server.rows["tohid-policy/week_start"] = FakeServer.Row("tohid-policy", "week_start", "del", JsonNull, ++server.rev)
      cycle(server)
      assertEquals(6, ShopDayChoice.weekStart)
    } finally {
      wipe()
      ShopDayChoice.load(ctx)
    }
  }
}
