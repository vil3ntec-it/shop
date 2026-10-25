package ir.vil3ntec.tohid.core.delivery

import ir.vil3ntec.tohid.core.courier.CourierFlow
import ir.vil3ntec.tohid.core.courier.CourierInputError
import ir.vil3ntec.tohid.core.courier.CourierQueue
import ir.vil3ntec.tohid.core.courier.QueueState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  دو آینهٔ کاتلینِ ماشینِ مأموریتِ تحویل در برابرِ بردارِ مشترکِ
 *  `core/test-vectors/delivery-states-v1.json` — همان فایلی که
 *  `server/scripts/delivery-vectors.js` از خودِ `delivery-states.js` می‌سازد و
 *  `server/test/delivery-vectors.test.js` تازه‌بودنش را می‌سنجد:
 *    • `CourierFlow` (برنامهٔ پیک): جدول‌ها، `canSend` و دکمه‌ها؛
 *    • `DeskActions` (میزِ تحویلِ دکان): دکمه‌های هر روش/وضعیت/دسترسی.
 *  هر فرق با سرور باید **دقیقاً** در `knownDivergences`ِ همان فایل باشد؛
 *  فرقِ تازه یا ناهم‌خوانیِ رفع‌شده هر دو این آزمون را قرمز می‌کنند.
 */
class DeliveryStatesVectorsTest {

  private val vec: JsonObject = run {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/delivery-states-v1.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/delivery-states-v1.json پیدا نشد")
  }

  private fun JsonElement.str() = jsonPrimitive.content
  private fun JsonElement.strs() = jsonArray.map { it.str() }

  private val statuses = vec.getValue("statuses").strs()
  private val modes = vec.getValue("modes").strs()
  private val live = vec.getValue("live").strs().toSet()
  private val departed = vec.getValue("departed").strs().toSet()
  private val terminal = vec.getValue("terminal").strs().toSet()
  private val reasons = vec.getValue("reasons").jsonObject.mapValues { it.value.strs() }
  private val reasonRequired = vec.getValue("reasonRequired").strs().toSet()
  private val noteRequired = vec.getValue("noteRequired").jsonArray.map { it.strs() }.toSet()

  /** [روش، از، به، کننده] ⇒ نتیجهٔ `check()`ِ سرور */
  private val verdicts: Map<List<String>, String> = vec.getValue("allowed").jsonArray.associate { row ->
    val r = row.strs()
    r.subList(0, 4) to r[4]
  }

  private fun serverAllows(mode: String, from: String, to: String, actor: String) =
    verdicts[listOf(mode, from, to, actor)] == "ok"

  private fun divergences(impl: String): Set<List<String>> =
    vec.getValue("knownDivergences").jsonArray.map { it.jsonObject }
      .filter { it.getValue("impl").str() == impl }
      .flatMap { d -> d.getValue("pairs").jsonArray.map { it.strs() } }
      .toSet()

  // ── برنامهٔ پیک ────────────────────────────────────────────────────

  @Test
  fun `جدول های پیک همان سرورند`() {
    assertEquals(statuses, CourierFlow.STATUSES)
    assertEquals(live, CourierFlow.LIVE)
    assertEquals(vec.getValue("courierTargets").strs().toSet(), CourierFlow.COURIER_TARGETS)
    assertEquals(reasons.getValue("rejected"), CourierFlow.REJECT_REASONS)
    assertEquals(reasons.getValue("failed"), CourierFlow.FAIL_REASONS)
    assertEquals(reasons.getValue("cash"), CourierFlow.CASH_REASONS)
    for (s in statuses) {
      assertEquals(s, s in terminal, CourierFlow.isTerminal(s))
      assertEquals(s, s in live, CourierFlow.isLive(s))
    }
  }

  @Test
  fun `canSend همان حکمِ سرور برای پیکِ همین مأموریت است جز ناهم خوانی های ثبت شده`() {
    val differs = HashSet<List<String>>()
    for (from in statuses) for (to in statuses) {
      if (CourierFlow.canSend(from, to) != serverAllows("shop_courier", from, to, "courier")) differs += listOf("shop_courier", from, to)
    }
    assertEquals(divergences("CourierFlow.canSend"), differs)
  }

  @Test
  fun `دکمه های کارتِ پیک — هیچ دکمه بی اثر و هر پنهان ثبت شده`() {
    val hidden = HashSet<List<String>>()
    for (status in statuses) {
      val offered = CourierFlow.actions(status).toSet()
      val accepted = statuses.filter { serverAllows("shop_courier", status, it, "courier") }.toSet()
      assertEquals("دکمهٔ بی‌اثر در $status", emptySet<String>(), offered - accepted)
      (accepted - offered).forEach { hidden += listOf("shop_courier", status, it) }
    }
    assertEquals(divergences("CourierFlow.actions"), hidden)
  }

  @Test
  fun `صفِ پیک دلیل را همان جا می خواهد که سرور`() {
    for (type in CourierFlow.COURIER_TARGETS) {
      val code = try {
        CourierQueue.enqueue(QueueState(), "evt-vector-$type", "A", type, 0)
        "ok"
      } catch (e: CourierInputError) {
        e.code
      }
      assertEquals(type, if (type in reasonRequired) "reason_required" else "ok", code)
    }
  }

  // ── میزِ تحویلِ دکان ────────────────────────────────────────────────

  /** کننده‌های کارمندِ بردار ⇒ دسترسی‌های میز */
  private val staffPerms = mapOf(
    "staff" to DeskPerms(view = true, assign = true, handover = true),
    "staff_assign" to DeskPerms(view = true, assign = true),
    "staff_handover" to DeskPerms(view = true, handover = true),
    "staff_none" to DeskPerms(view = true),
  )

  private fun mission(mode: String, status: String) =
    Mission(id = "A", mode = mode, status = status, live = status in live, hasCode = true)

  @Test
  fun `دکمه های میز — هیچ دکمه بی اثر و هر پنهان ثبت شده`() {
    val hidden = HashSet<List<String>>()
    for (mode in modes) for (status in statuses) for ((actor, perms) in staffPerms) {
      val m = mission(mode, status)
      val acts = DeskActions.of(m, perms)
      val offered = acts.mapNotNull { DeskActions.target(it) }.toSet()
      val accepted = statuses.filter { serverAllows(mode, status, it, actor) }.toSet()
      assertEquals("دکمهٔ بی‌اثر: $mode/$status/$actor", emptySet<String>(), offered - accepted)
      (accepted - offered).forEach { hidden += listOf(mode, status, it) }

      for (act in acts) {
        val to = DeskActions.target(act) ?: continue
        assertEquals("توضیح: $mode/$status→$to", listOf(mode, status, to) in noteRequired, DeskActions.needsNote(m, act))
      }
      //  فراخوانی فقط برای بستهٔ رفته و با «سپردن» (`recall`ِ delivery.js)
      if (DeskActions.Act.RECALL in acts) assertTrue("$mode/$status/$actor", status in departed && perms.assign)
      if (status in terminal) assertTrue("$mode/$status", acts.isEmpty())
    }
    //  پنهان در هر دسترسی‌ای که سرور می‌پذیرد شمرده شده؛ پس فهرست همان روش/از/به است
    assertEquals(divergences("DeskActions.of"), hidden)
  }
}
