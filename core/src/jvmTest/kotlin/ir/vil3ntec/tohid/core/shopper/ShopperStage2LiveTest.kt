package ir.vil3ntec.tohid.core.shopper

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import org.junit.Assume
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 *  مرحلهٔ ۲ ِ برنامهٔ خرید روی **سرورِ واقعی** (PGlite) — فاز ۲ ِ `server/test/shopper-live.mjs` پس از `ShopperLiveTest`:
 *  همان دکان با پرچم‌های گفت‌وگو، نظر، امتیازِ پیک و تخفیفِ ویترین، یک کدِ `VIP10` و یک سفارشِ تحویل‌شده
 *  (`SHOPPER_LIVE_DELIVERED`). بی متغیرها رد می‌شود (skip)، پس `gradle -p core jvmTest` ِ معمولی سرور نمی‌خواهد.
 */
class ShopperStage2LiveTest {
  private val base: String? = System.getenv("SHOPPER_LIVE_BASE")
  private val slug: String = System.getenv("SHOPPER_LIVE_SLUG").orEmpty()
  private val delivered: String = System.getenv("SHOPPER_LIVE_DELIVERED").orEmpty()

  private fun <T> ShopperResult<T>.ok(what: String): T = when (this) {
    is ShopperResult.Ok -> value
    is ShopperResult.Fail -> throw AssertionError("$what: ${error.status} ${error.code} ${error.message}")
  }

  /** «فقط ایمیل»: بلیت از کدِ ایمیل (شماره هرگز تأیید نمی‌شود) */
  private fun ticket(api: ShopperApi, email: String): CustomerTicket {
    val code = assertNotNull(api.verifyStart(slug, email).ok("کد").devCode)
    return api.verifyCheck(slug, email, code).ok("تأیید")
  }

  /** نشستِ حسابِ مشتری فقط برای آزمونِ درهای نشانی (ورودِ مشتری در برنامه پس از D-09 است) */
  private fun session(http: JdkShopperHttp, t: CustomerTicket): String {
    val r = http.send(ShopperRequest("POST", "/api/store/$slug/account", mapOf("Content-Type" to "application/json", "X-Store-Ticket" to t.ticket), "{}"))
    assertEquals(201, r.status, r.body)
    return Json.parseToJsonElement(r.body).jsonObject["session"]!!.jsonPrimitive.content
  }

  @Test fun addressesChatReviewsAndPromosAgainstARealServer() {
    Assume.assumeTrue("فاز ۲ ِ server/test/shopper-live.mjs این را می‌زند", base != null && slug.isNotEmpty() && delivered.isNotEmpty())
    val http = JdkShopperHttp(base!!)
    val api = ShopperApi(http) { "fa" }
    val now = { System.currentTimeMillis() }
    val page = api.store(slug).ok("ویترین")
    assertTrue(page.features.chat && page.features.reviews, "پرچم‌های گفت‌وگو و نظر")

    // ── تخفیف: پیش‌نمایشِ سرور ⇒ جمعِ دیده‌شده ⇒ همان مبلغِ ثبت ──
    val promos = api.promos(slug).ok("تخفیف‌ها")
    assertTrue(promos.codes, "کدی پذیرفته می‌شود (بی فهرست کردنِ خودِ کد)")
    val rice = api.product(slug, "rice").ok("برنج").product
    var cart = CartRules.add(CartRules.add(Cart(slug), rice, now()), rice, now())
    val quote = api.promoQuote(slug, PromoRules.lines(cart), PromoRules.cleanCode(" vip10 "), null, null).ok("پیش‌نمایش")
    assertEquals(PromoRules.APPLIED, quote.code?.status)
    assertTrue(quote.discountMinor in 1..2410, "۱۰٪ ِ ۲۴۱ افغانی با گردِ صندوق: ${quote.discountMinor}")
    assertEquals("unknown", api.promoQuote(slug, PromoRules.lines(cart), "NOPE99", null, null).ok("کدِ نادرست").code?.status)
    val t = ticket(api, "live.second@test.local")
    val form = CheckoutForm(fulfillment = "pickup", name = "مشتریِ دوم", email = t.email, phone = "0700222333")
    val req = CheckoutRules.request(page, cart, form, CheckoutRules.phoneOf(form)!!, "fa", null, PromoRules.applied(quote, "VIP10", null))
    assertEquals(24100 - quote.discountMinor, req.expectedTotalMinor)
    val placed = api.checkout(slug, req, CheckoutKeys.keyFor(null, req).key, t.ticket).ok("ثبت با کد")
    assertEquals(quote.discountMinor, placed.order.discountMinor)
    assertEquals(24100 - quote.discountMinor, placed.order.totalMinor, "تخفیف از سرور، در همان تراکنش = پیش‌نمایش")
    //  کدِ اعمال‌نشده در ثبت ⇒ ۴۰۹ با وضعیت، هرگز سفارشِ بی‌صدا
    val bad = req.copy(promoCode = "NOPE99", expectedTotalMinor = 24100)
    val rejected = api.checkout(slug, bad, CheckoutKeys.keyFor(null, bad).key, t.ticket).errorOrNull()
    assertEquals(CheckoutFlow.After.PromoRejected("unknown"), CheckoutFlow.after(cart, assertNotNull(rejected), now()))

    // ── پیگیری با کلیدهای پرچم‌دار ──
    val fresh = api.trackPage(slug, placed.order.token).ok("پیگیری")
    assertTrue(fresh.extras.chat)
    assertEquals(false, fresh.extras.review?.canReview, "نظر پس از تحویل")
    val done = api.trackPage(slug, delivered).ok("سفارشِ تحویل‌شده")
    assertEquals(true, done.extras.review?.canReview)
    val sent = api.review(slug, delivered, 5, "  برنج خوب بود  ").ok("نظر")
    assertEquals(5, sent.review.stars)
    assertEquals("برنج خوب بود", sent.review.comment)
    assertTrue(api.review(slug, delivered, 5, "برنج خوب بود").ok("تکرارِ نظر").replayed, "همان نظر دوباره ⇒ همان پاسخ")
    assertEquals(false, api.trackPage(slug, delivered).ok("پس از نظر").extras.review?.canReview)
    assertEquals(0, api.shopReviews(slug).ok("نظرهای عمومی").count, "تا تأییدِ دکان عمومی نیست")
    val rating = api.rating(slug, delivered).ok("امتیازِ پیک")
    assertFalse(rating.canRate, "دریافت از دکان رساننده ندارد")
    assertTrue("on_time" in rating.tags)
    val again = api.reorder(slug, delivered).ok("خریدِ دوباره")
    val (refill, gone) = RepeatPlans.cartFrom(Cart(slug), again, now())
    assertEquals(0, gone)
    assertEquals(12050, refill.lines.single().priceMinor, "قیمتِ امروزِ سرور")

    // ── گفت‌وگو: هویت با بلیت، clientId ِ تکراری ⇒ همان پیام، عکس بی EXIF ──
    val conv = api.chatOpen(slug, t.ticket, placed.order.orderNo, "مشتریِ دوم").ok("گفت‌وگوی سفارش")
    assertEquals(placed.order.orderNo, conv.orderNo)
    assertTrue(conv.canSend)
    val (q, item) = ChatOutbox.add(emptyList(), slug, conv.id, "text", "برنج تازه است؟", now())
    val first = api.chatSend(slug, t.ticket, item).ok("پیام")
    assertFalse(first.replayed)
    val replay = api.chatSend(slug, t.ticket, q.single()).ok("همان پیام دوباره (جوابِ گم‌شده)")
    assertTrue(replay.replayed)
    assertEquals(first.message.id, replay.message.id)
    val reused = api.chatSend(slug, t.ticket, item.copy(body = "متنِ دیگر")).errorOrNull()
    assertEquals(ChatFailure.FAIL, ChatOutbox.failureOf(assertNotNull(reused)), "همان شناسه با متنِ دیگر ⇒ قطعی")
    val img = BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB).also { b -> for (x in 0 until 64) b.setRGB(x, 0, 0xFF0000) }
    val jpeg = ByteArrayOutputStream().also { ImageIO.write(img, "jpg", it) }.toByteArray()
    val up = api.chatUpload(slug, t.ticket, conv.id, jpeg, thumb = false).ok("بارگذاریِ عکس")
    assertEquals("image", up.kind)
    val (_, pic) = ChatOutbox.add(emptyList(), slug, conv.id, "image", "", now(), width = 64, height = 48)
    val picSent = api.chatSend(slug, t.ticket, pic.copy(mediaId = up.mediaId)).ok("پیامِ عکس")
    assertEquals("image", picSent.message.kind)
    val back = api.chatMedia(slug, t.ticket, conv.id, up.mediaId).ok("خواندنِ عکس")
    assertEquals("image/jpeg", ShopperPhoto.mimeOf(back))
    val thread = ChatThread(conv).merge(api.chatChanges(slug, t.ticket, conv.id, 0).ok("تغییرها"))
    assertTrue(thread.arrived(item.clientId), "پیامِ صف در رشتهٔ سرور ⇒ از صف بیرون")
    assertEquals(2, thread.messages.count { it.mine })
    api.chatReceipts(slug, t.ticket, conv.id, 0, 0).ok("رسید")
    // ── CHAT-14/07 روی سرورِ واقعی: سقف‌های گشودن، پاسخ، ویرایش با POST (JDK ِ HttpURLConnection PATCH نمی‌فرستد)، سنجاق، جست‌وجو ──
    val opened = api.chatStart(slug, t.ticket, placed.order.orderNo, "مشتریِ دوم").ok("گشودن با سقف‌ها")
    assertEquals(conv.id, opened.conversation.id)
    assertFalse(opened.limits.voice, "پرچمِ خاموشِ shop.chat.voice ⇒ بی دکمهٔ صدا")
    assertTrue(opened.limits.audioMax > 0 && opened.limits.imageMax > 0)
    val (_, reply) = ChatOutbox.add(emptyList(), slug, conv.id, "text", "پاسخ به پیامِ خودم", now(), replyTo = first.message.id)
    val replied = api.chatSend(slug, t.ticket, reply).ok("پاسخ").message
    assertEquals(first.message.id, replied.replyTo)
    assertTrue((replied.editUntil ?: 0) > now(), "editUntil ِ ساعتِ سرور روی پیامِ خودم")
    val edited = api.chatEdit(slug, t.ticket, conv.id, replied.id, "پاسخِ ویرایش‌شده").ok("ویرایش با POST …/edit")
    assertEquals("پاسخِ ویرایش‌شده", edited.body)
    assertNotNull(edited.editedAt)
    assertEquals(listOf(replied.id), api.chatPin(slug, t.ticket, conv.id, replied.id, true).ok("سنجاق").pinned.map { it.messageId })
    assertTrue(api.chatPin(slug, t.ticket, conv.id, replied.id, false).ok("برداشتنِ سنجاق").pinned.isEmpty())
    assertEquals(listOf(replied.id), api.chatSearch(slug, t.ticket, "ویرایش‌شده").ok("جست‌وجو").map { it.id })
    assertEquals("query_too_short", api.chatSearch(slug, t.ticket, "پ").errorOrNull()?.code)
    val noTicket = api.chatOpen(slug, "v2.bad", null, "").errorOrNull()
    assertEquals(ChatFailure.VERIFY, ChatOutbox.failureOf(assertNotNull(noTicket)), "بی بلیت ⇒ تأییدِ دوباره، پیام در صف می‌ماند")

    // ── نشانی‌های حسابِ مشتری (CHK-09): همان قاعده و سقفِ ۱۰، و به شناسهٔ همان مشتری بسته ──
    val mine = session(http, ticket(api, "live.mine@test.local"))
    val other = session(http, ticket(api, "live.other@test.local"))
    val home = api.addAddress(slug, mine, AddressRules.clean("خانه", "", "پشتِ مسجد", GeoPin(34.528123456, 69.17))).ok("نشانیِ تازه")
    assertEquals(GeoPin(34.528123, 69.17), home.pin, "گردِ شش رقمیِ سرور = گردِ برنامه")
    assertEquals("bad_address", api.addAddress(slug, mine, AddressInput("", "x")).errorOrNull()?.code, "بی نام ⇒ همان ردِ برنامه")
    repeat(AddressRules.MAX - 1) { i -> api.addAddress(slug, mine, AddressRules.clean("نشانی $i", "کوچهٔ $i", "", null)).ok("نشانی $i") }
    assertEquals("too_many_addresses", api.addAddress(slug, mine, AddressInput("یازدهم", "x")).errorOrNull()?.code)
    assertEquals(AddressRules.MAX, api.addresses(slug, mine).ok("فهرست").size)
    assertEquals("address_not_found", api.updateAddress(slug, other, home.id, AddressInput("دزدی", "x")).errorOrNull()?.code, "نشانیِ دیگری ⇒ پیدا نشد")
    assertEquals("address_not_found", api.removeAddress(slug, other, home.id).errorOrNull()?.code)
    assertTrue(api.addresses(slug, other).ok("فهرستِ دیگری").isEmpty())
    assertEquals("دفتر", api.updateAddress(slug, mine, home.id, AddressInput("دفتر", "سرکِ اول")).ok("ویرایش").label)
    assertTrue(api.removeAddress(slug, mine, home.id).ok("پاک کردن"))
    assertEquals("session_required", api.addresses(slug, "sc1.bad").errorOrNull()?.code)
    assertIs<ShopperResult.Ok<*>>(api.store(slug))
  }
}
