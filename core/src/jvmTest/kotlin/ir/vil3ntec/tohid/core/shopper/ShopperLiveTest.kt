package ir.vil3ntec.tohid.core.shopper

import org.junit.Assume
import java.net.HttpURLConnection
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** فرستندهٔ JDK برای آزمون — همان روشِ `HttpAndroid.kt` ِ برنامه (HttpURLConnection، بی دنبال کردنِ ریدایرکت). */
internal class JdkShopperHttp(private val base: String) : ShopperHttp {
  override fun send(request: ShopperRequest): ShopperResponse {
    val c = URI(base.trimEnd('/') + request.path).toURL().openConnection() as HttpURLConnection
    try {
      c.instanceFollowRedirects = false
      c.connectTimeout = 10_000
      c.readTimeout = 30_000
      c.requestMethod = request.method
      for ((k, v) in request.headers) c.setRequestProperty(k, v)
      //  بدنهٔ JSON، یا بایت‌های خام (عکسِ گفت‌وگو با `Content-Type` ِ خودش)
      (request.bytes ?: request.body?.toByteArray(Charsets.UTF_8))?.let { b ->
        c.doOutput = true
        c.outputStream.use { it.write(b) }
      }
      val status = c.responseCode
      val raw = (if (status >= 400) c.errorStream else c.inputStream)?.use { it.readBytes() } ?: ByteArray(0)
      val headers = c.headerFields.filterKeys { it != null }.mapValues { it.value.joinToString(",") }
      return ShopperResponse(status, if (request.binary && status == 200) "" else raw.toString(Charsets.UTF_8), headers, raw.takeIf { request.binary })
    } finally {
      c.disconnect()
    }
  }
}

/**
 *  یک دورِ کاملِ خرید با کارخواهِ برنامه روی **سرورِ واقعی** (PGlite) — همان‌که `server/test/shopper-live.mjs`
 *  بالا می‌آورد و می‌کارد (دکان با ناحیه، دو کالای منتشرشده، بازارچهٔ روشن) و با `SHOPPER_LIVE_BASE`/`SHOPPER_LIVE_SLUG`
 *  همین آزمون را می‌زند. بی آن دو متغیر رد می‌شود (skip)، پس `gradle -p core jvmTest` ِ معمولی سرور نمی‌خواهد.
 */
class ShopperLiveTest {
  private val base: String? = System.getenv("SHOPPER_LIVE_BASE")
  private val slug: String = System.getenv("SHOPPER_LIVE_SLUG").orEmpty()

  private fun <T> ShopperResult<T>.ok(what: String): T = when (this) {
    is ShopperResult.Ok -> value
    is ShopperResult.Fail -> throw AssertionError("$what: ${error.status} ${error.code} ${error.message}")
  }

  @Test fun fullShoppingFlowAgainstARealServer() {
    Assume.assumeTrue("SHOPPER_LIVE_BASE نیست — server/test/shopper-live.mjs سرور را بالا می‌آورد و همین را می‌زند", base != null && slug.isNotEmpty())
    val api = ShopperApi(JdkShopperHttp(base!!)) { "fa" }
    val now = { System.currentTimeMillis() }

    // ── بازارچه و دکان ──
    assertTrue(api.market().ok("بازارچه").shopCount >= 1)
    assertTrue(api.shops(q = "آزمایشی").ok("فهرستِ دکان").shops.any { it.slug == slug }, "دکانِ فهرست‌شده در جست‌وجوی بازارچه")
    val page = api.store(slug).ok("ویترین")
    assertEquals("ویترینِ آزمایشی", page.shop.title)
    assertTrue(page.verificationAvailable)
    val zone = CheckoutRules.zones(page)?.single()
    assertNotNull(zone, "ناحیهٔ کاشته‌شده")
    assertEquals(3000, zone.feeMinor)

    // ── کاتالوگ، جست‌وجو، برگهٔ کالا ──
    val all = api.products(slug).ok("کاتالوگ").items
    assertEquals(setOf("rice", "oil"), all.map { it.id }.toSet(), "فقط کالاهای منتشرشده")
    assertEquals(listOf("oil"), api.products(slug, q = " روغن ").ok("جست‌وجو").items.map { it.id })
    val rice = api.product(slug, "rice").ok("برگهٔ کالا").product
    assertEquals(12050L, rice.priceMinor)
    assertEquals("product_not_found", api.product(slug, "nope").errorOrNull()?.code)

    // ── سبد: ۲ برنج + ۱ روغن = ۵۴۱ ⇒ زیرِ حداقلِ ۶۰۰ ِ ناحیه با هزینهٔ سبدِ کوچک ──
    var cart = Cart(slug)
    cart = CartRules.add(cart, rice, now())
    cart = CartRules.add(cart, rice, now())
    cart = CartRules.add(cart, all.first { it.id == "oil" }, now())
    assertEquals(54100, CartRules.subtotal(cart))
    val gone = CartRules.refresh(cart.copy(lines = cart.lines + CartLine("ghost", "شبح", priceMinor = 100, qtyMilli = 1000)),
      api.productsByIds(slug, cart.lines.map { it.productId } + "ghost").ok("تازه‌سازی").items, now())
    assertEquals(1, gone.gone, "کالای نبوده خط‌خورده")

    // ── «فقط ایمیل»: تأییدِ ایمیل با کدِ آزمایشیِ سرور؛ شماره فقط تماسِ پیک ──
    val email = "live.buyer@test.local"
    assertEquals("otp_not_found", api.verifyCheck(slug, email, "000000").errorOrNull()?.code)
    val sent = api.verifyStart(slug, email).ok("فرستادنِ کد")
    val code = assertNotNull(sent.devCode, "سرورِ آزمون کد را برمی‌گرداند")
    val ticket = api.verifyCheck(slug, email, code).ok("زدنِ کد")
    assertEquals(email, ticket.email)

    // ── ثبتِ ارسال با ناحیه: مبلغِ برنامه همان مبلغِ سرور ──
    val form = CheckoutForm(name = "مشتریِ آزمایشی", email = email, phone = "۰۷۰۰ ۱۲۳ ۴۵۶", addressText = "کارته‌سه، سرکِ اول", zoneId = zone.id)
    val phone = assertNotNull(CheckoutRules.phoneOf(form), "شمارهٔ تماسِ پیک به قالبِ سرور")
    assertTrue(CheckoutRules.problems(page, cart, form, ticket, null, now()).isEmpty())
    val sum = CheckoutRules.summary(page, cart, form, null)
    assertEquals(54100 + 3000 + 1500, sum.totalMinor)
    val req = CheckoutRules.request(page, cart, form, phone, "fa", null)
    val memo = CheckoutKeys.keyFor(null, req)
    val placed = api.checkout(slug, req, memo.key, ticket.ticket).ok("ثبت")
    assertFalse(placed.replayed)
    assertEquals(sum.totalMinor, placed.order.totalMinor, "جمعِ سرور = برآوردِ برنامه")
    assertEquals(4500, placed.order.deliveryFeeMinor)
    assertEquals(1500, placed.order.smallBasketFeeMinor)
    assertEquals(zone.name, placed.order.zoneName)

    // ── CHK-19: زدنِ دوباره با همان کلید = همان سفارش ──
    val again = api.checkout(slug, req, CheckoutKeys.keyFor(memo, req).key, ticket.ticket).ok("تکرار")
    assertTrue(again.replayed)
    assertEquals(placed.order.orderNo, again.order.orderNo)

    // ── پیگیری و لغو ──
    val tracked = api.track(slug, placed.order.token).ok("پیگیری")
    assertEquals("new", tracked.status)
    assertTrue(tracked.canCancel)
    assertEquals(TrackRules.StepState.NOW, TrackRules.steps(tracked).first().state)
    assertEquals("cancelled_by_customer", api.cancel(slug, placed.order.token).ok("لغو").status)
    assertEquals(TrackRules.StepState.STOP, TrackRules.steps(api.track(slug, placed.order.token).ok("پس از لغو")).last().state)
    assertNotEquals("", ShopperText.error(api.track(slug, "v2.not-a-token").errorOrNull()!!))

    // ── قیمتِ کهنه در سبد ⇒ ۴۰۹ با برگهٔ امروز؛ پذیرفتن ⇒ ثبت ──
    val stale = cart.copy(lines = cart.lines.map { if (it.productId == "rice") it.copy(priceMinor = 11000) else it })
    val staleReq = CheckoutRules.request(page, stale, form.copy(fulfillment = "pickup"), phone, "fa", null)
    val err = api.checkout(slug, staleReq, CheckoutKeys.keyFor(memo, staleReq).key, ticket.ticket).errorOrNull()
    assertEquals(ShopperError.PRICE_CHANGED, err?.code)
    val quote = assertNotNull(err!!.quote())
    val (fixed, changed) = CartRules.applyQuote(stale, quote, now())
    assertEquals(listOf(Triple("برنج", 11000L, 12050L)), changed)
    val pickupReq = CheckoutRules.request(page, fixed, form.copy(fulfillment = "pickup"), phone, "fa", null)
    val pickup = api.checkout(slug, pickupReq, CheckoutKeys.keyFor(null, pickupReq).key, ticket.ticket).ok("ثبتِ دریافت از دکان")
    assertEquals(54100, pickup.order.totalMinor, "دریافت از دکان بی هزینهٔ ارسال")
    assertEquals("pickup", pickup.order.fulfillment)

    // ── بی بلیت ⇒ ۴۰۱ و پیامِ روشن ──
    val noTicket = api.checkout(slug, pickupReq, "app-no-ticket-1", null).errorOrNull()
    assertEquals("verification_required", noTicket?.code)
  }
}
