package ir.vil3ntec.tohid.desktop.offline

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.net.HttpEngine
import ir.vil3ntec.tohid.core.orders.OrderDraft
import ir.vil3ntec.tohid.data.repo.OrdersRepository
import ir.vil3ntec.tohid.ui.v3.online.orders.OrderRefundModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 *  FIN-01 سرتاسری — برنامهٔ کامپیوتر با **سرورِ واقعیِ محلی** (`server/test/e2e/lib/offline30-backend.mjs`، PGlite ِ در حافظه؛
 *  با `OFFLINE30_DATABASE_URL` همان روی PostgreSQL): همان کدِ برنامه — فرمِ سفارشِ دستی (`OrderDraft` ⇐ `OrdersRepository.create`)،
 *  گذارها، قاعده‌های برگهٔ «ثبتِ بازپرداخت» (`OrderRefundModel`) و `OrdersRepository.refund` ⇐ `ApiClient` ⇐ `HttpEngine` — تا
 *  دفترِ بازپرداختِ سرور (`shop.orders.refunds`) و گزارشِ تطبیق (`GET /shop/orders/reconciliation`).
 *
 *   1. دفترِ روشن ⇒ گذارِ سادهٔ «بازپرداخت‌شده» ۴۰۹ `refund_via_ledger` (همان دلیلِ ساختنِ برگه)؛
 *   2. «بازپرداختِ بخشی» از برگه ⇒ یک ردیف با همان مبلغ و `final` ⇒ «بازپرداختِ بخشی»؛ «تأیید» ِ دوباره با همان کلید (پاسخِ
 *      گم‌شده) ردیفِ دوم نمی‌سازد؛ دو «تأیید» ِ هم‌زمان هم یک ردیف؛
 *   3. «بازپرداختِ کامل» با مبلغِ ازپیش‌پُرِ برگه (= مانده) ⇒ «بازپرداختِ کامل»؛ بیش از مانده را برگه نمی‌فرستد و سرور هم رد می‌کند؛
 *   4. جمعِ دفترِ هر سفارش = جمعِ «بازپرداخت» ِ گزارشِ تطبیق؛ خالص = دریافتی − بازپرداخت؛ هیچ اختلافی (`issues` خالی).
 */
class OrderRefundServerDesktopTest {

  private val work = File(System.getProperty("java.io.tmpdir"), "fin01-${System.nanoTime()}")

  private fun <T> ApiResult<T>.ok(what: String): T = when (this) {
    is ApiResult.Success -> value
    is ApiResult.Failure -> throw AssertionError("$what: ${error.code} ${error.message}")
  }

  private val body = OrderDraft.Body(
    source = "phone", fulfillment = "pickup", customerName = "احمد", customerPhone = "0791234567", addressText = "", landmark = "",
    deliveryFeeMinor = 0,
    //  ۱٫۵ × ۳۳٫۳۳ ⇒ ۵۰ افغانی، ۲ × ۱۲٫۵ ⇒ ۲۵ افغانی ⇒ ۷۵۰۰ واحدِ خرد (همان سفارشِ `order-refunds.test.js`)
    lines = listOf(OrderDraft.Line("برنج", 1500, 3333), OrderDraft.Line("نوشابه", 2000, 1250)),
    note = "", test = false,
  )

  /** سفارشِ دستیِ برنامه تا «درخواستِ برگشت» — پول گرفته شده و حالا برمی‌گردد */
  private suspend fun returned(repo: OrdersRepository): OrderDto {
    var o = repo.create(OrdersRepository.newKey(), body).ok("سفارش")
    for (to in listOf("accepted", "picking", "packed", "ready_for_pickup", "picked_up", "return_requested")) {
      o = repo.transition(o.id, to, o.status).ok(to)
    }
    return o
  }

  private fun JsonObject.n(path: String): Long =
    path.split('.').fold<String, kotlinx.serialization.json.JsonElement>(this) { e, k -> e.jsonObject[k]!! }.jsonPrimitive.long

  @Test fun `بازپرداختِ بخشی و کامل از برگهٔ برنامه ⇒ دفترِ سفارش و گزارشِ تطبیقِ سرور با هم می‌خوانند`() {
    I18n.source = { Lang.FA }
    val root = OfflineBackend.repoRoot()
    val missing = OfflineBackend.available(root)
    assumeTrue("سرورِ واقعی در دسترس نیست: $missing", missing == null)
    work.mkdirs()
    OfflineBackend.start(root!!, File(work, "backend.log")).use { be -> runBlocking { scenario(be) } }
    work.deleteRecursively()
  }

  private suspend fun scenario(be: OfflineBackend) {
    val setup = be.call("POST", "/setup")
    val o = setup.obj("owner")
    be.call("POST", "/flag", obj("key" to "shop.orders", "value" to true))
    be.call("POST", "/flag", obj("key" to "shop.orders.refunds", "value" to true))
    val api = ApiClient(
      HttpEngine(baseUrl = { setup.str("api") }, allowInsecure = true,
        identity = { mapOf("X-App" to "shop", "X-App-Id" to "shop", "X-Device" to o.str("deviceId"), "X-App-Version" to "1.0.9999") }),
      MemTokens(o.str("access"), o.str("refresh")),
    )
    val repo = OrdersRepository(api)
    val from = System.currentTimeMillis() - 60_000
    val cfg = repo.config().ok("پیکربندی")
    assertTrue("دفترِ بازپرداختِ سرور روشن است", OrderRefundModel.ledger(cfg, "refunded_partial"))

    //  ── ۱) گذارِ ساده دیگر راه نیست ──
    val a = returned(repo)
    val plain = repo.transition(a.id, "refunded_partial", a.status)
    assertEquals("refund_via_ledger", (plain as ApiResult.Failure).error.code)

    //  ── ۲) «بازپرداختِ بخشی» از برگه ──
    val partial = "refunded_partial"
    assertEquals("", OrderRefundModel.startAmount(a, partial))
    //  همان مرزهای برگه: بیش از مانده و برابرِ مانده برای «بخشی» نه؛ دلیل لازم
    assertEquals(OrderRefundModel.Problem.NOT_BELOW_REMAINING, OrderRefundModel.check(a, partial, "75", "x"))
    assertEquals(OrderRefundModel.Problem.REASON, OrderRefundModel.check(a, partial, "20", " "))
    assertNull(OrderRefundModel.check(a, partial, "۲۰", "یک کیلو برنج خراب بود"))
    val minor = OrderRefundModel.amountMinor("۲۰")!!
    val key = OrdersRepository.newKey()
    val method = OrderRefundModel.methodFor("cash", OrderRefundModel.methods(cfg))
    val r1 = repo.refund(a.id, key, minor, "یک کیلو برنج خراب بود", OrderRefundModel.final(partial), method).ok("بازپرداختِ بخشی")
    assertEquals("refunded_partial", r1.status)
    assertEquals(2000L, r1.refund!!.refundedMinor)
    assertEquals(5500L, r1.refund!!.remainingMinor)
    //  پاسخِ گم‌شده: «تأیید» ِ دوباره با همان کلیدِ برگه ⇒ همان ردیف
    val again = repo.refund(a.id, key, minor, "یک کیلو برنج خراب بود", true, method).ok("تکرار")
    assertEquals(2000L, again.refund!!.refundedMinor)
    //  بسته شد ⇒ ردیفِ تازه با کلیدِ تازه هم نه
    val closed = repo.refund(a.id, OrdersRepository.newKey(), 100, "باز هم", true, method)
    assertEquals("refund_closed", (closed as ApiResult.Failure).error.code)

    //  ── ۳) «بازپرداختِ کامل» با مبلغِ ازپیش‌پُر؛ دو «تأیید» ِ هم‌زمان با یک کلید ⇒ یک ردیف ──
    val b = returned(repo)
    val full = "refunded_full"
    val start = OrderRefundModel.startAmount(b, full)
    assertEquals("75", start)
    assertEquals(OrderRefundModel.Problem.NOT_REMAINING, OrderRefundModel.check(b, full, "80", "x"))
    assertNull(OrderRefundModel.check(b, full, start, "مشتری همه را پس آورد"))
    val fk = OrdersRepository.newKey()
    val both = kotlinx.coroutines.coroutineScope {
      List(2) { async { repo.refund(b.id, fk, OrderRefundModel.amountMinor(start)!!, "مشتری همه را پس آورد", OrderRefundModel.final(full), method) } }.awaitAll()
    }
    assertTrue("هر دو «تأیید» همان پاسخ", both.all { it is ApiResult.Success })
    val bAfter = repo.get(b.id).ok("سفارشِ دوم")
    assertEquals("refunded_full", bAfter.status)
    assertEquals(7500L, bAfter.refund!!.refundedMinor)
    assertEquals(0L, bAfter.refund!!.remainingMinor)
    //  بیش از مانده: سرور هم رد می‌کند و ردیفی نمی‌سازد
    val c = returned(repo)
    val over = repo.refund(c.id, OrdersRepository.newKey(), 7600, "بیش از کل", false, method)
    assertEquals("refund_exceeds_remaining", (over as ApiResult.Failure).error.code)

    //  ── ۴) گزارشِ تطبیق = دفترِ هر سفارش ──
    val rec = api.get("/shop/orders/reconciliation?from=$from&to=${System.currentTimeMillis() + 60_000}")
    val perOrder = listOf(a.id, b.id, c.id).sumOf { repo.get(it).ok("سفارش").refund?.refundedMinor ?: 0L }
    assertEquals(9500L, perOrder)
    assertEquals(perOrder, rec.n("refunds.totalMinor"))
    assertEquals(2L, rec.n("refunds.count"))
    assertEquals(3L, rec.n("collected.count"))
    assertEquals(3 * 7500L, rec.n("collected.totalMinor"))
    assertEquals(3 * 7500L - 9500L, rec.n("netMinor"))
    assertEquals("هیچ اختلافی میانِ دفتر و وضعیت نیست", 0, (rec["issues"] as JsonArray).size)
  }
}
