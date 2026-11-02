package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.orders.OrderFeedback
import ir.vil3ntec.tohid.data.repo.OrderFeedbackSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  NTF-07/09 — «گزارش‌ها و نظرها» ِ کارمند: خواندنِ پاسخِ `/api/shop/order-feedback`، حالِ فهرست، «رسیدگی شد» و
 *  «تأیید»/«رد»، و پنهان ماندن تا سرور فهرست نداده (پرچم‌های خاموش ⇒ ۴۰۳).
 */
class OrderFeedbackTest {

  private val i1 = "01JB0000000000000000000001"
  private val i2 = "01JB0000000000000000000002"
  private val p1 = "01JB00000000000000000000P1"
  private val r1 = "01JB00000000000000000000R1"
  private val r2 = "01JB00000000000000000000R2"

  private fun json(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

  private fun issueJson(id: String, status: String = "open", reply: String? = null) =
    """{"id":"$id","orderId":"ord-$id","orderNo":42,"kind":"damaged","text":"<b>کیسه</b> پاره بود","status":"$status",
       "reply":${reply?.let { "\"$it\"" } ?: "null"},"photos":[{"id":"$p1","mime":"image/jpeg","size":900,"width":320,"height":240,"createdAt":5}],
       "createdAt":1000,"resolvedAt":null,"resolvedBy":null}"""

  private fun reviewJson(id: String, status: String = "pending", stars: Int = 4) =
    """{"id":"$id","orderId":"ord-$id","orderNo":7,"stars":$stars,"comment":"خوب بود","status":"$status","createdAt":2000,"moderatedBy":null,"moderatedAt":null}"""

  private fun issue(id: String, status: String = "open") = OrderFeedback.issue(json(issueJson(id, status)))!!
  private fun review(id: String, status: String = "pending") = OrderFeedback.review(json(reviewJson(id, status)))!!

  /** سرورِ ساختگی: پاسخِ هر در از پیش؛ هر صدا شمرده می‌شود */
  private class Fake : OrderFeedbackSource {
    var onIssues: (String) -> ApiResult<List<OrderFeedback.Issue>> = { ApiResult.Success(emptyList()) }
    var onReviews: (String) -> ApiResult<List<OrderFeedback.Review>> = { ApiResult.Success(emptyList()) }
    var onResolve: (String, String) -> ApiResult<OrderFeedback.Issue> = { _, _ -> ApiResult.Failure(ApiFailure.InvalidResponse()) }
    var onModerate: (String, String) -> ApiResult<OrderFeedback.Review> = { _, _ -> ApiResult.Failure(ApiFailure.InvalidResponse()) }
    val calls = mutableListOf<String>()
    override suspend fun issues(filter: String) = onIssues.invoke(filter).also { calls += "issues?$filter" }
    override suspend fun reviews(filter: String) = onReviews.invoke(filter).also { calls += "reviews?$filter" }
    override suspend fun resolve(issueId: String, reply: String) = onResolve.invoke(issueId, reply).also { calls += "resolve $issueId $reply" }
    override suspend fun moderate(reviewId: String, decision: String) = onModerate.invoke(reviewId, decision).also { calls += "moderate $reviewId $decision" }
    override suspend fun photo(issueId: String, photoId: String): ApiResult<ByteArray> = ApiResult.Success(ByteArray(0)).also { calls += "photo $issueId $photoId" }
  }

  private val issuesOff = ApiResult.Failure(ApiFailure.Forbidden("خاموش", OrderFeedback.ISSUES_OFF))
  private val reviewsOff = ApiResult.Failure(ApiFailure.Forbidden("خاموش", OrderFeedback.REVIEWS_OFF))

  @Test
  fun `پاسخ فهرست گزارش ها خوانده می شود و ردیف نادرست کنار می رود`() {
    val list = OrderFeedback.issues(json("""{"issues":[${issueJson(i1)},${issueJson("bad-id")},
      {"id":"$i2","orderId":"o2","status":"weird"},${issueJson(i2, "resolved", "پس گرفتیم")}]}"""))
    assertEquals(listOf(i1, i2), list.map { it.id })
    val a = list[0]
    assertEquals("<b>کیسه</b> پاره بود", a.text)
    assertEquals(42L, a.orderNo)
    assertEquals("damaged", a.kind)
    assertNull(a.reply)
    assertEquals(listOf(OrderFeedback.Photo(p1, "image/jpeg", 320, 240)), a.photos)
    assertTrue(a.open)
    assertEquals("پس گرفتیم", list[1].reply)
    assertFalse(list[1].open)
    //  نوعِ ناشناخته ⇒ «دیگر»؛ پاسخِ بی کلید ⇒ فهرستِ خالی
    assertEquals("other", OrderFeedback.issue(json(issueJson(i1).replace("\"damaged\"", "\"zzz\"")))!!.kind)
    assertEquals(emptyList<OrderFeedback.Issue>(), OrderFeedback.issues(json("{}")))
  }

  @Test
  fun `پاسخ فهرست نظرها خوانده می شود و ستاره بیرون از یک تا پنج کنار می رود`() {
    val list = OrderFeedback.reviews(json("""{"reviews":[${reviewJson(r1)},${reviewJson(r2, stars = 6)},${reviewJson(r2, "all")},${reviewJson(r2, "approved", 5)}]}"""))
    assertEquals(listOf(r1 to "pending", r2 to "approved"), list.map { it.id to it.status })
    assertEquals(4, list[0].stars)
    assertEquals("خوب بود", list[0].comment)
    assertEquals("★★★★☆", OrderFeedback.starLine(4))
    assertEquals("★★★★★", OrderFeedback.starLine(9))
  }

  @Test
  fun `نشانی ها فقط با شناسه درست و فیلتر ناشناخته همان پیش فرض`() {
    assertEquals("/shop/order-feedback/issues?status=open", OrderFeedback.issuesPath("zzz"))
    assertEquals("/shop/order-feedback/issues?status=resolved", OrderFeedback.issuesPath("resolved"))
    assertEquals("/shop/order-feedback/reviews?status=pending", OrderFeedback.reviewsPath("&x=1"))
    assertEquals("/shop/order-feedback/issues/$i1/photos/$p1", OrderFeedback.photoPath(i1, p1))
    assertNull(OrderFeedback.photoPath(i1, "../../admin"))
    assertNull(OrderFeedback.resolvePath("x/../y"))
    assertEquals("/shop/order-feedback/reviews/$r1/moderate", OrderFeedback.moderatePath(r1))
    assertNull(OrderFeedback.moderatePath(""))
  }

  @Test
  fun `پاسخ رسیدگی دو تا هزار نویسه پس از برداشتن فاصله دو سر`() {
    assertFalse(OrderFeedback.replyOk("  a  "))
    assertTrue(OrderFeedback.replyOk(" بله "))
    assertTrue(OrderFeedback.replyOk("x".repeat(1000)))
    assertFalse(OrderFeedback.replyOk("x".repeat(1001)))
    //  نویسه، نه واحدِ UTF-16 — همان شمارشِ سرور
    assertTrue(OrderFeedback.replyOk("😀".repeat(1000)))
    assertFalse(OrderFeedback.replyOk("😀"))
  }

  @Test
  fun `پرچم های خاموش یعنی هیچ چیز دیده نمی شود`() = runBlocking {
    val fake = Fake().apply { onIssues = { issuesOff }; onReviews = { reviewsOff } }
    val c = OrderFeedbackController(fake, this)
    assertFalse("پیش از پاسخ پنهان", c.board.visible)
    c.refresh()
    assertFalse(c.board.visible)
    assertEquals(0, c.board.waiting)
    assertEquals(listOf("issues?open", "reviews?pending"), fake.calls)
    //  سرورِ کهنه (۴۰۴) هم پنهان؛ خطای گذرا حالِ پیشین را نگه می‌دارد
    fake.onIssues = { ApiResult.Failure(ApiFailure.NotFound("نیست", "not_found")) }
    c.refresh()
    assertFalse(c.board.visible)
    fake.onIssues = { ApiResult.Success(listOf(issue(i1))) }
    c.refresh()
    assertTrue(c.board.visible)
    assertEquals(true, c.board.issuesOn)
    assertEquals(false, c.board.reviewsOn)
    assertEquals(1, c.board.waiting)
    fake.onIssues = { ApiResult.Failure(ApiFailure.Offline()) }
    c.refresh()
    assertTrue("گذرا ⇒ همان", c.board.visible)
    assertEquals(listOf(i1), c.board.issues.map { it.id })
    fake.onIssues = { issuesOff }
    c.refresh()
    assertFalse("پرچم خاموش شد ⇒ پنهان", c.board.visible)
    assertEquals(emptyList<OrderFeedback.Issue>(), c.board.issues)
  }

  @Test
  fun `رسیدگی گزارش را از برگه باز برمی دارد و شمار را کم می کند`() = runBlocking {
    val fake = Fake().apply {
      onIssues = { f -> ApiResult.Success(if (f == "open") listOf(issue(i1), issue(i2)) else listOf(issue(i1, "resolved"))) }
      onReviews = { reviewsOff }
      onResolve = { id, reply -> ApiResult.Success(OrderFeedback.issue(json(issueJson(id, "resolved", reply)))!!) }
    }
    val c = OrderFeedbackController(fake, this)
    c.refresh()
    assertEquals(2, c.board.openIssues)
    assertNull("پاسخِ کوتاه فرستاده نمی‌شود", c.resolve(c.board.issues[0], " a "))
    assertNull("گزارشِ بسته دوباره نه", c.resolve(issue(i1, "resolved"), "پس گرفتیم"))
    c.resolve(c.board.issues[0], "  پس گرفتیم  ")!!.join()
    assertTrue(fake.calls.contains("resolve $i1   پس گرفتیم  "))
    assertEquals(listOf(i2), c.board.issues.map { it.id })
    assertEquals(1, c.board.openIssues)
    assertEquals(true, c.note?.first)
    assertNull(c.busy)
    //  برگهٔ «رسیدگی‌شده» همان پاسخ را نشان می‌دهد
    c.showIssues("resolved")!!.join()
    assertEquals("resolved", c.board.issueFilter)
    assertEquals(listOf(i1 to "resolved"), c.board.issues.map { it.id to it.status })
    assertEquals(1, c.board.openIssues)
  }

  @Test
  fun `رسیدگی رد شده پیام می دهد و ۴۰۹ فهرست را از نو می خواند و خاموش پنهان می کند`() = runBlocking {
    val fake = Fake().apply {
      onIssues = { ApiResult.Success(listOf(issue(i1))) }
      onReviews = { reviewsOff }
      onResolve = { _, _ -> ApiResult.Failure(ApiFailure.Conflict("پیش‌تر با پاسخِ دیگری بسته شده", "already_resolved")) }
    }
    val c = OrderFeedbackController(fake, this)
    c.refresh()
    fake.calls.clear()
    c.resolve(c.board.issues[0], "پاسخ")!!.join()
    assertEquals(listOf("resolve $i1 پاسخ", "issues?open"), fake.calls)
    assertEquals(false to "پیش‌تر با پاسخِ دیگری بسته شده", c.note)
    assertNull(c.busy)
    fake.onResolve = { _, _ -> issuesOff }
    c.resolve(c.board.issues[0], "پاسخ")!!.join()
    assertFalse(c.board.visible)
  }

  @Test
  fun `تایید و رد نظر را جابه جا می کند و همان تصمیم دوباره فرستاده نمی شود`() = runBlocking {
    val fake = Fake().apply {
      onIssues = { issuesOff }
      onReviews = { f -> ApiResult.Success(if (f == "pending") listOf(review(r1), review(r2)) else listOf(review(r1, "approved"))) }
      onModerate = { id, d -> ApiResult.Success(review(id, if (d == OrderFeedback.APPROVE) "approved" else "rejected")) }
    }
    val c = OrderFeedbackController(fake, this)
    c.refresh()
    assertTrue(c.board.visible)
    assertEquals(2, c.board.waiting)
    assertNull(c.moderate(c.board.reviews[0], "publish"))
    c.moderate(c.board.reviews[0], OrderFeedback.APPROVE)!!.join()
    assertEquals(listOf(r2), c.board.reviews.map { it.id })
    assertEquals(1, c.board.pendingReviews)
    assertEquals(true, c.note?.first)
    c.showReviews("approved")!!.join()
    val approved = c.board.reviews.single()
    assertNull("همان تصمیم ⇒ هیچ", c.moderate(approved, OrderFeedback.APPROVE))
    //  «رد» ِ نظرِ منتشرشده آن را از برگهٔ «منتشرشده» برمی‌دارد؛ شمارِ «در انتظار» دست نمی‌خورد
    c.moderate(approved, OrderFeedback.REJECT)!!.join()
    assertTrue(c.board.reviews.isEmpty())
    assertEquals(1, c.board.pendingReviews)
    assertTrue(fake.calls.containsAll(listOf("moderate $r1 approve", "moderate $r1 reject")))
    fake.onModerate = { _, _ -> reviewsOff }
    c.showReviews("pending")!!.join()
    c.moderate(c.board.reviews[0], OrderFeedback.APPROVE)!!.join()
    assertFalse("پرچم میانِ کار خاموش ⇒ پنهان", c.board.visible)
  }

  @Test
  fun `پاسخ فیلتر کهنه فهرست برگه تازه را عوض نمی کند`() {
    var b = OrderFeedback.issuesLoaded(OrderFeedback.Board(), "open", listOf(issue(i1)))
    b = OrderFeedback.issueFilter(b, "resolved")
    assertFalse(b.issuesLoaded)
    b = OrderFeedback.issuesLoaded(b, "open", listOf(issue(i1), issue(i2)))
    assertTrue("فهرستِ برگهٔ «رسیدگی‌شده» هنوز نیامده", b.issues.isEmpty())
    assertFalse(b.issuesLoaded)
    assertEquals(2, b.openIssues)
    assertEquals(b, OrderFeedback.issueFilter(b, "nope"))
  }

  @Test
  fun `دکمه فقط در صفحه سفارش ها و فقط وقتی سرور فهرست داده`() {
    fun src(rel: String): String =
      listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
        .map { File(it, "ir/vil3ntec/tohid/$rel") }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد")
    assertTrue(src("ui/screens/Orders.kt").contains("OrderFeedbackEntry(ir.vil3ntec.tohid.data.repo.Backend.api(context), cfg.permissions.manage, rev)"))
    val panel = src("ui/screens/OrderFeedbackPanel.kt")
    val entry = panel.substringAfter("internal fun OrderFeedbackEntry(").substringBefore("@Composable")
    assertTrue(entry.indexOf("if (!b.visible) return") in 0 until entry.indexOf("TohidSecondaryButton("))
    //  عکس فقط از مرزِ پلتفرم و با همان رمزگشاییِ رسانهٔ چت
    assertTrue(src("ui/screens/OrderFeedbackPhotoAndroid.kt").contains("BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()"))
    assertFalse(panel.contains("android."))
  }
}
