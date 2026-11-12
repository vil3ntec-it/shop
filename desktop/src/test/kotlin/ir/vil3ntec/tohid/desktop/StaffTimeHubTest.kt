package ir.vil3ntec.tohid.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.performClick
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrStaffTime
import ir.vil3ntec.tohid.core.net.TestHttpServer
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.repo.StaffTimeRepository
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.screens.StaffTimeHub
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  STF-07 — سیم‌کشیِ «ورود و خروج» (`StaffTimeHub`: کارت ⇐ `StaffTimeRepository` ⇐ `Backend.api` و صفِ آفلاینِ
 *  `tohid-staff-time`) در برابرِ سرورِ ساختگی‌ای که همان قاعدهٔ `server/src/lib/staff-time.js` را دارد (شناسهٔ تکراری ⇒ همان
 *  زدن، ۲۰۰؛ پیش از آخرین زدن ⇒ ۴۰۹ `punch_before_last`؛ دو هم‌نوع ⇒ ۴۰۹ `punch_same_kind`):
 *   • آنلاین: ورود و خروج هر کدام یک بار به سرور می‌رسند؛
 *   • بی شبکه: زدن در صف می‌ماند (هیچ درخواستی) و کارت «سرِ کار» و شمارِ صف را می‌گوید؛
 *   • برگشتِ شبکه: بازشدنِ دوبارهٔ صفحه صف را یک بار می‌فرستد و بارِ بعد چیزی تکرار نمی‌شود؛
 *   • برگشتِ شبکه و زدنِ تازه پیش از بازشدنِ دوباره: زدنِ صف‌شده **پیش** از زدنِ تازه می‌رود (ترتیبِ زمان)، نه پس از آن
 *     (وگرنه سرور ورودِ صف‌شده را «پیش از آخرین زدن» رد می‌کرد و نوبت گم می‌شد).
 */
@OptIn(ExperimentalTestApi::class)
class StaffTimeHubTest {

  @get:Rule val rule = createComposeRule()

  private lateinit var backend: TestBackend
  private lateinit var fake: FakeStaffTime
  private var mount by mutableStateOf(0)

  private fun clearQueue() = DesktopContext.getSharedPreferences("tohid-staff-time", 0).edit().clear().commit()

  @Before fun up() {
    I18n.source = { Lang.FA }
    clearQueue()
    backend = TestBackend()
    fake = FakeStaffTime(backend.server)
  }

  @After fun down() {
    backend.close()
    clearQueue()
  }

  private fun show() {
    val today = todayIso()
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) {
          //  `mount` ِ تازه = بستن و بازکردنِ دوبارهٔ صفحه (همان `LaunchedEffect` ِ آغاز)
          key(mount) { StaffTimeHub(ShopData(), today.take(7) + "-01", today, StaffTimeRepository.Config(true, "m1", "staff")) }
        }
      }
    }
    rule.waitUntil(10_000) { has(StrStaffTime.clockIn) }
  }

  private fun has(text: String) = rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

  private fun until(what: () -> Boolean) = rule.waitUntil(20_000) { what() }

  /** دکمه پس از پایانِ کارِ پیشین (بازخوانیِ بی‌شبکه با تلاشِ دوباره) باز می‌شود؛ لمسِ دکمهٔ بسته هیچ است */
  private fun press(text: String) {
    until { rule.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    rule.onNode(hasText(text) and isEnabled()).performClick()
  }

  @Test fun onlineClockInAndOutEachReachTheServerOnce() {
    show()
    press(StrStaffTime.clockIn)
    until { fake.rows().size == 1 && has(StrStaffTime.clockOut) }
    press(StrStaffTime.clockOut)
    until { fake.rows().size == 2 && has(StrStaffTime.clockIn) }
    assertEquals(listOf("in", "out"), fake.rows().map { it.kind })
    assertEquals(2, fake.posts().size)
    assertEquals(2, fake.posts().map { it.id }.toSet().size)
    assertTrue(fake.rows().all { it.memberId == "m1" })
    assertTrue(!has(StrStaffTime.queued(1)))
  }

  @Test fun offlinePunchIsQueuedThenSentExactlyOnceWhenTheNetworkReturns() {
    show()
    backend.online = false
    val before = System.currentTimeMillis()
    press(StrStaffTime.clockIn)
    until { has(StrStaffTime.queued(1)) && has(StrStaffTime.clockOut) }
    val after = System.currentTimeMillis()
    assertEquals(0, fake.posts().size)
    //  هنوز بی شبکه: بازشدنِ دوباره هم چیزی نمی‌فرستد و صف می‌ماند
    mount++
    until { has(StrStaffTime.queued(1)) }
    assertEquals(0, fake.posts().size)

    backend.online = true
    mount++
    until { fake.rows().size == 1 && !has(StrStaffTime.queued(1)) }
    val sent = fake.rows().single()
    assertEquals("in", sent.kind)
    //  همان زمانِ زدنِ بی‌شبکه، نه زمانِ رسیدن
    assertTrue(sent.at in before..after)
    //  بازشدنِ دوباره پس از رسیدن ⇒ هیچ فرستادنِ تکراری
    val reads = backend.server.hits(FakeStaffTime.PUNCHES)
    mount++
    until { backend.server.hits(FakeStaffTime.PUNCHES) > reads }
    rule.waitForIdle()
    assertEquals(1, fake.posts().size)
    assertTrue(has(StrStaffTime.clockOut))
  }

  @Test fun queuedPunchGoesBeforeANewPunchMadeAfterTheNetworkReturns() {
    show()
    backend.online = false
    press(StrStaffTime.clockIn)
    until { has(StrStaffTime.queued(1)) && has(StrStaffTime.clockOut) }
    //  بازخوانیِ پس از زدن هنوز بی شبکه تمام شد (دکمه باز) ⇒ زدن در صف مانده است
    until { rule.onAllNodes(hasText(StrStaffTime.clockOut) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    assertEquals(0, fake.posts().size)

    //  شبکه برگشت ولی صفحه هنوز باز است (هیچ بازخوانی‌ای)؛ شاگرد «ثبتِ خروج» می‌زند
    backend.online = true
    press(StrStaffTime.clockOut)
    until { fake.rows().size == 2 && has(StrStaffTime.clockIn) }
    assertEquals(listOf("in", "out"), fake.rows().map { it.kind })
    assertEquals(listOf("in", "out"), fake.posts().map { it.kind })
    assertTrue(fake.rejected().isEmpty())
    assertTrue(!has(StrStaffTime.beforeLast))
    assertTrue(!has(StrStaffTime.queued(1)))
  }
}

/** سرورِ ساختگیِ `/api/shop/staff-time` با همان قاعدهٔ زدنِ سرور (`server/src/lib/staff-time.js` ‏`punch`) */
internal class FakeStaffTime(server: TestHttpServer, role: String = "staff") {
  companion object {
    const val ROOT = "/api/v1/shop/staff-time"
    const val PUNCHES = "$ROOT/punches"
  }

  data class Row(val id: String, val memberId: String, val kind: String, val at: Long)

  private val lock = Any()
  private val rows = ArrayList<Row>()
  private val posts = ArrayList<Row>()
  private val rejected = ArrayList<String>()

  fun rows(): List<Row> = synchronized(lock) { rows.toList() }
  fun posts(): List<Row> = synchronized(lock) { posts.toList() }
  fun rejected(): List<String> = synchronized(lock) { rejected.toList() }

  init {
    server.on("$ROOT/config") { 200 to """{"enabled":true,"memberId":"m1","role":"$role"}""" }
    server.on(PUNCHES) { r -> if (r.method == "POST") post(r.body) else 200 to list() }
  }

  private fun conflict(code: String) = 409 to """{"error":{"code":"$code","message":"$code"}}"""

  private fun post(body: String): Pair<Int, String> = synchronized(lock) {
    val o: JsonObject = Json.parseToJsonElement(body).jsonObject
    val p = Row(
      o["id"]!!.jsonPrimitive.content, o["memberId"]?.jsonPrimitive?.content ?: "m1",
      o["kind"]!!.jsonPrimitive.content, o["at"]!!.jsonPrimitive.long,
    )
    posts += p
    rows.firstOrNull { it.id == p.id }?.let { same ->
      return@synchronized if (same == p) 200 to """{"duplicate":true}""" else conflict("punch_id_conflict").also { rejected += it.second }
    }
    val last = rows.filter { it.memberId == p.memberId }.maxWithOrNull(compareBy({ it.at }, { it.id }))
    if (last != null && p.at < last.at) return@synchronized conflict("punch_before_last").also { rejected += "punch_before_last" }
    if (last != null && last.kind == p.kind) return@synchronized conflict("punch_same_kind").also { rejected += "punch_same_kind" }
    rows += p
    201 to """{"duplicate":false}"""
  }

  private fun list(): String = synchronized(lock) {
    rows.joinToString(",", """{"punches":[""", "]}") { r ->
      """{"id":"${r.id}","memberId":"${r.memberId}","kind":"${r.kind}","at":${r.at},"deviceUid":"d","name":"شاگرد","late":false}"""
    }
  }
}
