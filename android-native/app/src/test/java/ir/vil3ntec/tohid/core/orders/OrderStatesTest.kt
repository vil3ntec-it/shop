package ir.vil3ntec.tohid.core.orders

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  آینهٔ کاتلینِ ماشینِ وضعیتِ سفارش در برابرِ بردارِ مشترکِ
 *  `core/test-vectors/order-states.json` — همان فایلی که
 *  `server/scripts/order-vectors.js` از خودِ `order-states.js` می‌سازد و
 *  `server/test/order-vectors.test.js` تازه‌بودنش را می‌سنجد. پس اگر سرور
 *  گذاری را عوض کند، یا آزمونِ سرور قرمز می‌شود یا همین آزمون.
 */
class OrderStatesTest {

  private val vec: JsonObject = run {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/order-states.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/order-states.json پیدا نشد")
  }

  private fun JsonElement.str() = jsonPrimitive.content
  private fun JsonElement.strs() = jsonArray.map { it.str() }
  private fun JsonElement.rows() = jsonArray.map { it.jsonArray }
  private fun JsonElement.mapOfLists() = jsonObject.mapValues { it.value.strs() }

  private val perms = vec.getValue("permissions").mapOfLists()
  private fun canRefund(role: String) = role in perms.getValue("orders.refund")
  private fun canManage(role: String) = role in perms.getValue("orders.manage")

  @Test
  fun `جدول ها همان سرورند`() {
    assertEquals(vec.getValue("statuses").strs(), OrderStates.STATUSES)
    assertEquals(vec.getValue("transitions").mapOfLists(), OrderStates.TRANSITIONS)
    assertEquals(vec.getValue("reasons").mapOfLists(), OrderStates.REASONS)
    assertEquals(vec.getValue("reasonRequired").strs(), OrderStates.REASON_REQUIRED)
    assertEquals(vec.getValue("systemOnly").strs(), OrderStates.SYSTEM_ONLY)
    assertEquals(vec.getValue("moneyTargets").strs(), OrderStates.MONEY_TARGETS)
    assertEquals(vec.getValue("tabs").strs(), OrderStates.TABS)
    assertEquals(vec.getValue("openTabs").strs(), OrderStates.OPEN_TABS)
  }

  @Test
  fun `هر گذار با هر کننده و هر دلیل همان جوابِ سرور را دارد`() {
    val rows = vec.getValue("check").rows()
    assertTrue("بردار خالی است", rows.size > 500)
    for (r in rows) {
      val (from, to, fulfillment, actor, reason, note, out) = r.map { it.str() }
      val a = if (actor == "system") OrderStates.Actor(system = true) else OrderStates.Actor(canRefund = canRefund(actor))
      assertEquals("$r", out, OrderStates.check(from, fulfillment, to, a, reason, note))
    }
  }

  @Test
  fun `دکمه های هر کارت برای هر نقش`() {
    for (r in vec.getValue("nextFor").rows()) {
      val status = r[0].str()
      val fulfillment = r[1].str()
      val role = r[2].str()
      val next = r[3].strs()
      assertEquals("$r", next, OrderStates.nextFor(status, fulfillment, canRefund(role)))
      assertEquals("$r", if (canManage(role)) next else emptyList<String>(),
        OrderStates.actions(status, fulfillment, canManage(role), canRefund(role)))
      assertEquals(emptyList<String>(), OrderStates.actions(status, fulfillment, canManage = false, canRefund = true))
      //  ردِ خودکار هرگز دکمه نیست
      assertTrue("auto_rejected" !in next)
    }
  }

  @Test
  fun `دلیل های پنجره و برگهٔ هر سفارش`() {
    for (r in vec.getValue("reasonChoices").rows()) {
      assertEquals("$r", r[2].strs(), OrderStates.reasonChoices(r[0].str(), r[1].str()))
      assertTrue(OrderStates.needsReason(r[1].str()))
    }
    for (r in vec.getValue("tabOf").rows()) {
      val scheduled = if (r[1] is JsonNull) null else r[1].jsonPrimitive.long
      assertEquals("$r", r[3].str(), OrderStates.tabOf(r[0].str(), scheduled, r[2].jsonPrimitive.long))
    }
  }

  @Test
  fun `هر برگه، وضعیت، دلیل و دکمه نامِ فارسی دارد`() {
    for (t in OrderStates.TABS) assertNotNull(t, OrderText.TAB[t])
    for (s in OrderStates.STATUSES) assertNotNull(s, OrderText.STATUS[s])
    for (list in OrderStates.TRANSITIONS.values) for (to in list) assertNotNull(to, OrderText.ACTION[to])
    for (list in OrderStates.REASONS.values) for (r in list) assertNotNull(r, OrderText.REASON[r])
    for (s in listOf("phone", "telegram", "whatsapp", "manual")) assertNotNull(s, OrderText.SOURCE[s])
    for (s in vec.getValue("codTransitions").jsonObject.keys) assertNotNull(s, OrderText.COD[s])
  }

  @Test
  fun `مبلغ و مقدار بی ممیزِ شناور`() {
    assertEquals("1,250", OrderText.money(125_000))
    assertEquals("33.33", OrderText.money(3_333))
    assertEquals("0.05", OrderText.money(5))
    assertEquals("-1,000,000", OrderText.money(-100_000_000))
    assertEquals("1.5", OrderText.qty(1_500))
    assertEquals("2", OrderText.qty(2_000))
    assertEquals("0.001", OrderText.qty(1))
    assertEquals("همین حالا", OrderText.ago(1_000, 30_000))
    assertEquals("5 دقیقه پیش", OrderText.ago(0, 5 * 60_000))
  }

  @Test
  fun `فرمِ سفارشِ دستی همان قاعدهٔ وب`() {
    assertEquals(1500L, OrderDraft.scaled("۱٫۵", 3))
    assertEquals(1L, OrderDraft.scaled("0.001", 3))
    assertEquals(125000L, OrderDraft.scaled("1,250", 2))
    assertEquals(3333L, OrderDraft.scaled("33.33", 2))
    assertEquals(null, OrderDraft.scaled("1.234", 2))
    assertEquals(null, OrderDraft.scaled("-1", 2))
    assertEquals(null, OrderDraft.scaled("abc", 2))

    val ok = OrderDraft.build(
      OrderDraft.Form(
        source = "phone", fulfillment = "delivery", name = " احمد ", phone = "0791234567",
        addressText = "کارتهٔ سه", deliveryFee = "50", note = "زنگ بزنید",
        lines = listOf(
          OrderDraft.LineInput("برنج", "1.5", "33.33"),
          OrderDraft.LineInput(),
          OrderDraft.LineInput("نوشابه", "2", "12.5"),
        ),
      ),
    )
    ok as OrderDraft.Result.Ok
    assertEquals("احمد", ok.body.customerName)
    assertEquals(5000L, ok.body.deliveryFeeMinor)
    assertEquals(
      listOf(OrderDraft.Line("برنج", 1500, 3333), OrderDraft.Line("نوشابه", 2000, 1250)),
      ok.body.lines,
    )

    fun bad(f: OrderDraft.Form, part: String) {
      val r = OrderDraft.build(f)
      assertTrue("$r", r is OrderDraft.Result.Error && part in r.message)
    }
    val one = listOf(OrderDraft.LineInput("x", "1", "1"))
    bad(OrderDraft.Form(name = "", lines = one), "نامِ مشتری")
    bad(OrderDraft.Form(name = "a", fulfillment = "delivery", lines = one), "شمارهٔ مشتری")
    bad(OrderDraft.Form(name = "a", fulfillment = "delivery", phone = "0791234567", lines = one), "نشانی")
    bad(OrderDraft.Form(name = "a", lines = emptyList()), "دست‌کم یک قلم")
    bad(OrderDraft.Form(name = "a", lines = listOf(OrderDraft.LineInput("x", "0", "1"))), "مقدارِ")
    bad(OrderDraft.Form(name = "a", lines = listOf(OrderDraft.LineInput("x", "1", "x"))), "قیمتِ")

    //  حضوری: نه هزینهٔ ارسال، نه نشانی
    val pickup = OrderDraft.build(OrderDraft.Form(name = "a", deliveryFee = "50", addressText = "x", lines = one))
    pickup as OrderDraft.Result.Ok
    assertEquals(0L, pickup.body.deliveryFeeMinor)
    assertEquals("", pickup.body.addressText)
  }
}

private operator fun <T> List<T>.component6(): T = this[5]
private operator fun <T> List<T>.component7(): T = this[6]
