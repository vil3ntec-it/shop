package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.crm.CustomerErasure
import ir.vil3ntec.tohid.core.i18n.StrErasure
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.orders.LateApology
import ir.vil3ntec.tohid.core.orders.OrderEvidence
import ir.vil3ntec.tohid.data.repo.CustomerErasureSource
import ir.vil3ntec.tohid.data.repo.LateApologySource
import ir.vil3ntec.tohid.data.repo.OrderEvidenceSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 *  صفحه‌های برنامه برای سه درِ سرور: پروندهٔ مدرکِ اختلاف (RSK-08)، پوزشِ خودکارِ تأخیر (NTF-10) و حذفِ دادهٔ
 *  مشتری (SEC-10) — خواندنِ پاسخ، گام‌ها، پنهان ماندن با پرچمِ خاموش، نقش، و ترتیبِ پیش‌نمایش ⇐ تأیید ⇐ هویت ⇐ اجرا.
 */
class ServerFeatureScreensTest {

  private fun json(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

  @Before fun reset() {
    OrderEvidenceLive.gate.reset()
    CustomerErasureLive.gate.reset()
  }

  // ═════════════════════════════ RSK-08 ═════════════════════════════

  private val d1 = "aud_0123456789abcdef01234567"
  private val d2 = "aud_0123456789abcdef0123456f"

  private fun disputeJson(id: String, status: String = "open") =
    """{"id":"$id","kind":"not_delivered","reason":"مشتری می‌گوید نرسید","status":"$status",
       "openedBy":{"userId":"u1","role":"owner"},"openedAt":1000,
       "notes":[{"id":"aud_x","text":"با پیک تماس گرفتیم","by":{"userId":"u2","role":"manager"},"at":2000}],
       "resolution":${if (status == "resolved") """{"id":"aud_y","outcome":"shop_favor","text":"عکسِ تحویل هست","by":{"userId":"u1","role":"owner"},"at":3000}""" else "null"}}"""

  private fun fileJson(disputes: String = "[]") = """{
    "version":1,"generatedAt":5000,
    "order":{"id":"ord_1","orderNo":42,"status":"delivered","statusReason":"","source":"storefront","isTest":false,
      "fulfillment":"delivery","paymentMethod":"cod","paymentStatus":"unpaid","codStatus":"collected","currency":"AFN",
      "totalMinor":125000,"itemsCount":3,"createdAt":100,"updatedAt":200,
      "customer":{"name":"احمد","phoneMasked":"07•••••123"},"address":{"landmark":"کنارِ مسجد","area":"کارته ۳","text":"","hasPin":true},
      "lines":[{"lineNo":1},{"lineNo":2}]},
    "timeline":[{"seq":1,"kind":"created","fromStatus":"","toStatus":"new","actorType":"customer","actorId":"","reason":"","note":"","at":100},
                {"seq":2,"kind":"status","fromStatus":"new","toStatus":"accepted","actorType":"user","actorId":"u1","reason":"","note":"","at":150}],
    "delivery":{"photoConsent":true,"photosVisible":true,"assignments":[
      {"id":"as1","attemptNo":1,"mode":"own","courierId":"c1","externalName":"","status":"delivered","expectedCollectMinor":125000,
       "cashCollectedMinor":120000,"confirmation":{"hasCode":true,"codeUsed":true,"codeLocked":false,"codeVerified":true,"manualConfirm":false,"signature":null},
       "photos":[{"id":"p1"},{"id":"p2"}],"events":[{"seq":1},{"seq":2},{"seq":3}],"deliveredAt":4000}]},
    "location":{"rawRetained":false,"atDelivery":[{"assignmentId":"as1","seq":3,"toStatus":"delivered","distanceM":35,"serverTime":4000}],"note":"فقط فاصلهٔ گردشده"},
    "clock":{"warnMs":300000,"samples":3,"maxAbsSkewMs":420000,"warnings":1,"note":"زمانِ سرور مرجع است"},
    "scans":{"pick":{"status":"packed","pickerId":"u2","batchId":null,"bags":2,"shelfSlot":"A1","startedAt":1,"packedAt":2,"readyScannedAt":null},
      "pickEvents":[{"kind":"line","outcome":"picked"},{"kind":"line","outcome":"not_found"}],"shelfScans":[{"bag":1}],
      "bagsAtPickup":[{"assignmentId":"as1","seq":2,"bagsScanned":2,"serverTime":3500}]},
    "pickPhotos":[{"id":"pp1"}],
    "issues":[{"id":"i1","kind":"damaged","status":"open","photos":[{"id":"ip1"}],"createdAt":4500}],
    "contacts":{"courierCalls":[{"assignmentId":"as1"}],"conversations":[{"id":"cv1","fromCustomer":4,"fromStaff":3}],"none":false,"note":"فقط شمار"},
    "disputes":$disputes,"current":null,
    "people":{"users":{"u1":"صاحب","u2":"علی"},"couriers":{"c1":"پیکِ یک"}},
    "truncated":[]}"""

  private class EvFake : OrderEvidenceSource {
    var onProbe: () -> ApiResult<Unit> = { ApiResult.Failure(ApiFailure.NotFound("سفارش پیدا نشد", OrderEvidence.NOT_FOUND)) }
    var onFile: () -> ApiResult<OrderEvidence.File> = { ApiResult.Failure(ApiFailure.Offline()) }
    var onWrite: (OrderEvidence.Pending) -> ApiResult<OrderEvidence.Dispute> = { ApiResult.Failure(ApiFailure.Offline()) }
    val calls = mutableListOf<String>()
    val keys = mutableListOf<String>()
    override suspend fun probe() = onProbe().also { calls += "probe" }
    override suspend fun file(orderId: String) = onFile().also { calls += "file $orderId" }
    override suspend fun write(orderId: String, p: OrderEvidence.Pending) = onWrite(p).also { calls += "write ${p.draft.act}"; keys += p.key }
  }

  private fun file(disputes: String = "[]") = OrderEvidence.file(json(fileJson(disputes)))!!

  @Test
  fun `پرونده همه بخش ها را از پاسخ سرور می خواند`() {
    val f = file("[${disputeJson(d1, "resolved")},${disputeJson(d2)}]")
    assertEquals(42L, f.order.orderNo)
    assertEquals(125000L, f.order.totalMinor)
    assertTrue(f.order.hasPin)
    assertEquals(2, f.order.lines)
    assertEquals(listOf("created", "status"), f.timeline.map { it.kind })
    val a = f.assignments.single()
    assertEquals(2, a.photos)
    assertEquals(3, a.events)
    assertTrue(a.codeVerified)
    assertEquals("پیکِ یک", f.courier(a))
    assertEquals(35L, f.distances.single().meters)
    assertEquals(7L, OrderEvidence.skewMinutes(f.clock))
    assertEquals(1, f.scans.notFound)
    assertEquals(listOf(2), f.scans.bagsAtPickup)
    assertEquals(1, f.pickPhotos)
    assertEquals(4, f.contacts.fromCustomer)
    assertEquals(d2, f.current?.id)
    assertEquals(listOf(d1), f.past.map { it.id })
    assertEquals("shop_favor", f.past.single().resolution?.outcome)
    assertEquals("صاحب", f.who("u1"))
    //  بی دسترسیِ `delivery.photo` ⇒ سرور `photos: null` ⇒ «اجازه نیست»، نه صفر
    val hidden = OrderEvidence.file(json(fileJson().replace("\"photos\":[{\"id\":\"p1\"},{\"id\":\"p2\"}]", "\"photos\":null")))!!
    assertNull(hidden.assignments.single().photos)
    //  پاسخِ بی سفارش یا شناسهٔ اختلافِ نادرست ⇒ هیچ
    assertNull(OrderEvidence.file(json("""{"order":{}}""")))
    assertNull(OrderEvidence.dispute(json(disputeJson("aud_bad"))))
    //  بخش‌ها و جدولِ چاپ از همان پرونده
    val sections = evidenceSections(f, { "t$it" }, { "m$it" })
    assertTrue(sections.size >= 7)
    val rows = evidencePrintRows(f, { "t$it" }, { "m$it" })
    assertTrue(rows.all { it.size == 2 })
    assertTrue(rows.any { it[1].contains("m125000") })
  }

  @Test
  fun `دکمه پرونده فقط با پاسخ روشن سرور پیداست`() = runBlocking {
    val fake = EvFake()
    //  روشن و مجاز ⇒ ۴۰۴ ِ «این سفارش نیست» برای شناسهٔ بی‌اثر
    OrderEvidenceLive.ensure(fake, now = 1000)
    assertTrue(OrderEvidenceLive.gate.visible)
    //  تا مهلت دوباره پرسیده نمی‌شود
    OrderEvidenceLive.ensure(fake, now = 2000)
    assertEquals(listOf("probe"), fake.calls)
    //  پرچمِ خاموش ⇒ پنهان
    fake.onProbe = { ApiResult.Failure(ApiFailure.Forbidden("خاموش", OrderEvidence.OFF)) }
    OrderEvidenceLive.ensure(fake, now = 1000 + OrderEvidenceLive.TTL_MS)
    assertFalse(OrderEvidenceLive.gate.visible)
    //  بی اجازه (`orders.manage`) ⇒ پنهان؛ خطای گذرا ⇒ همان
    OrderEvidenceLive.gate.reset()
    fake.onProbe = { ApiResult.Failure(ApiFailure.Forbidden("اجازه ندارید", "forbidden")) }
    OrderEvidenceLive.ensure(fake, now = 5)
    assertFalse(OrderEvidenceLive.gate.visible)
    assertNull(OrderEvidence.probeSays("offline", refused = false))
    assertEquals(false, OrderEvidence.probeSays("not_found", refused = true))
  }

  @Test
  fun `اختلاف باز و یادداشت و بسته می شود و ارسال دوباره همان کلید را می برد`() = runBlocking {
    var n = 0
    val fake = EvFake().apply { onFile = { ApiResult.Success(file()) } }
    val c = OrderEvidenceController(fake, "ord_1", this, newKey = { "key-${++n}-abcdef" })
    c.load()
    assertNotNull(c.file)
    assertNull("بی فرم چیزی فرستاده نمی‌شود", c.submit())
    c.begin(OrderEvidence.Act.NOTE)
    assertNull("بی اختلافِ باز یادداشت نیست", c.draft)
    c.begin(OrderEvidence.Act.OPEN)
    c.edit(OrderEvidence.Draft(OrderEvidence.Act.OPEN, "cash", "ab"))
    assertNull("متنِ کوتاه", c.submit())
    c.edit(OrderEvidence.Draft(OrderEvidence.Act.OPEN, "cash", "  پول کم بود  "))
    //  قطعِ نت ⇒ فرم می‌ماند و «ثبت» ِ دوباره همان کلید
    c.submit()!!.join()
    assertEquals(false, c.note?.first)
    fake.onWrite = { p -> ApiResult.Success(OrderEvidence.dispute(json(disputeJson(d1)))!!.copy(kind = p.draft.choice)) }
    c.submit()!!.join()
    assertEquals(listOf("key-1-abcdef", "key-1-abcdef"), fake.keys)
    assertNull(c.draft)
    assertEquals(d1, c.file?.current?.id)
    assertEquals(true, c.note?.first)
    //  بدنهٔ نوشتن: کلید، شناسهٔ اختلافِ باز، متنِ پیراسته
    val p = OrderEvidence.pending(null, OrderEvidence.Draft(OrderEvidence.Act.RESOLVE, "settled", " توافق "), d1) { "key-resolve-1" }
    val body = OrderEvidence.body(p.draft, p.key, p.disputeId)!!
    assertEquals("settled", body["outcome"]!!.jsonPrimitive.content)
    assertEquals("توافق", body["text"]!!.jsonPrimitive.content)
    assertEquals(d1, body["disputeId"]!!.jsonPrimitive.content)
    assertNull("کلیدِ کوتاه", OrderEvidence.body(p.draft, "k", d1))
    //  اختلافِ باز عوض شده (۴۰۹) ⇒ پرونده از نو؛ پرچمِ خاموش ⇒ پنهان
    c.begin(OrderEvidence.Act.NOTE)
    c.edit(OrderEvidence.Draft(OrderEvidence.Act.NOTE, "", "یادداشتِ تازه"))
    fake.onWrite = { ApiResult.Failure(ApiFailure.Conflict("عوض شده", "dispute_changed")) }
    fake.calls.clear()
    c.submit()!!.join()
    assertEquals(listOf("write NOTE", "file ord_1"), fake.calls)
    OrderEvidenceLive.gate.record(true, 1)
    c.begin(OrderEvidence.Act.OPEN)
    c.edit(OrderEvidence.Draft(OrderEvidence.Act.OPEN, "late", "دیر رسید"))
    fake.onWrite = { ApiResult.Failure(ApiFailure.Forbidden("خاموش", OrderEvidence.OFF)) }
    c.submit()!!.join()
    assertTrue(c.refused)
    assertFalse(OrderEvidenceLive.gate.visible)
  }

  // ═════════════════════════════ NTF-10 ═════════════════════════════

  private val viewOn = """{"enabled":true,
    "policy":{"enabled":true,"lateMinutes":45,"coupon":{"kind":"percent","amountMinor":0,"bp":1250,"days":7},"version":3,"updatedAt":9},
    "recent":[{"orderId":"o1","orderNo":11,"outcome":"apology","lateMinutes":50,"dueAt":1,"at":2,"queued":true,
      "coupon":{"last4":"AB12","kind":"percent","amountMinor":0,"bp":1250,"expiresAt":99,"redeemedAt":null}},
      {"orderId":"o2","orderNo":12,"outcome":"skipped_customer","lateMinutes":60,"dueAt":1,"at":3,"queued":false,"coupon":null}],
    "permissions":{"settings":true}}"""

  private class LaFake(var view: String) : LateApologySource {
    var onSave: (JsonObject) -> ApiResult<LateApology.Policy> = { ApiResult.Failure(ApiFailure.Offline()) }
    val calls = mutableListOf<String>()
    override suspend fun load() = ApiResult.Success(LateApology.view(Json.parseToJsonElement(view).jsonObject)).also { calls += "load" }
    override suspend fun save(body: JsonObject) = onSave(body).also { calls += "save" }
  }

  @Test
  fun `سیاست پوزش با پرچم خاموش پنهان است و خوانده می شود`() = runBlocking {
    val off = LateApology.view(json("""{"enabled":false}"""))
    assertFalse(off.enabled)
    val fake = LaFake("""{"enabled":false}""")
    val s = LateApologyForm(fake)
    s.load()
    assertFalse(s.visible)
    assertFalse(LateApologyLive.visible)
    fake.view = viewOn
    s.load()
    assertTrue(s.visible)
    assertTrue(LateApologyLive.visible)
    val v = s.view!!
    assertEquals(45, v.policy.lateMinutes)
    assertEquals(LateApology.Coupon("percent", 0, 1250, 7), v.policy.coupon)
    assertEquals(listOf("AB12", null), v.recent.map { it.couponLast4 })
    assertEquals(LateApology.Form(true, "45", "percent", "", "12.5", "7"), s.form)
    assertFalse(s.changed)
    val lines = s.recentLines { "t$it" }
    assertEquals(2, lines.size)
  }

  @Test
  fun `فرم پوزش با بازه های سرور سنجیده و فرستاده می شود`() {
    fun check(f: LateApology.Form) = LateApology.check(f, 3)
    assertEquals(setOf(LateApology.Bad.MINUTES), check(LateApology.Form(minutes = "4")).errors)
    assertEquals(setOf(LateApology.Bad.MINUTES), check(LateApology.Form(minutes = "1441")).errors)
    assertTrue(check(LateApology.Form(minutes = "۳۰")).errors.isEmpty())
    assertEquals(setOf(LateApology.Bad.PERCENT), check(LateApology.Form(kind = "percent", percent = "50.01")).errors)
    assertEquals(setOf(LateApology.Bad.AMOUNT, LateApology.Bad.DAYS), check(LateApology.Form(kind = "amount", amount = "0", days = "91")).errors)
    val pct = check(LateApology.Form(enabled = true, minutes = "20", kind = "percent", percent = "12.5", days = "7")).body!!
    assertEquals(20, pct["lateMinutes"]!!.jsonPrimitive.int)
    val coupon = pct["coupon"]!!.jsonObject
    assertEquals(1250, coupon["bp"]!!.jsonPrimitive.int)
    assertNull(coupon["amountMinor"])
    assertEquals(3L, pct["expectedVersion"]!!.jsonPrimitive.long)
    val amt = check(LateApology.Form(kind = "amount", amount = "۵۰", days = "14")).body!!
    assertEquals(5000L, amt["coupon"]!!.jsonObject["amountMinor"]!!.jsonPrimitive.long)
    assertEquals(JsonNull, check(LateApology.Form(kind = "none")).body!!["coupon"])
    assertEquals(1250L, LateApology.hundredths("12.5"))
    assertNull(LateApology.hundredths("1.234"))
  }

  @Test
  fun `فقط صاحب و مدیر سیاست پوزش را عوض می کنند`() = runBlocking {
    val fake = LaFake(viewOn.replace("\"settings\":true", "\"settings\":false"))
    val s = LateApologyForm(fake)
    s.load()
    assertFalse(s.editable)
    s.edit(s.form.copy(minutes = "90"))
    assertEquals("45", s.form.minutes)
    assertNull(s.save { fake.save(it) })
    assertEquals(listOf("load"), fake.calls)
    //  مدیر: ذخیره؛ نسخهٔ کهنه ⇒ خواندنِ دوباره
    fake.view = viewOn
    s.load()
    assertTrue(s.editable)
    s.edit(s.form.copy(minutes = "90"))
    assertTrue(s.changed)
    fake.onSave = { ApiResult.Failure(ApiFailure.Conflict("کهنه", "version_conflict")) }
    s.save { fake.save(it) }
    assertEquals(listOf("load", "load", "save", "load"), fake.calls)
    assertEquals("45", s.form.minutes)
    s.edit(s.form.copy(minutes = "90"))
    fake.onSave = { b -> ApiResult.Success(LateApology.Policy(true, b["lateMinutes"]!!.jsonPrimitive.int, null, 4)) }
    s.save { fake.save(it) }
    assertEquals(90, s.view!!.policy.lateMinutes)
    assertFalse(s.changed)
  }

  // ═════════════════════════════ SEC-10 ═════════════════════════════

  private val previewJson = """{"dryRun":true,"mode":"phone","openOrders":0,
    "counts":{"orders":2,"orderLines":5,"orderEvents":0,"messages":3},"total":10,"kept":["order_ledger_docs","device_sync_ledger"]}"""

  private class ErFake(var preview: String) : CustomerErasureSource {
    var onProbe: () -> ApiResult<Unit> = { ApiResult.Failure(ApiFailure.Invalid("هدف لازم است", CustomerErasure.BAD_TARGET)) }
    var onErase: () -> ApiResult<CustomerErasure.Result> = { ApiResult.Failure(ApiFailure.Offline()) }
    val calls = mutableListOf<String>()
    override suspend fun probe() = onProbe().also { calls += "probe" }
    override suspend fun erase(phone: String, dryRun: Boolean): ApiResult<CustomerErasure.Result> {
      calls += if (dryRun) "dry $phone" else "erase $phone"
      return if (dryRun) ApiResult.Success(CustomerErasure.result(Json.parseToJsonElement(preview).jsonObject)!!) else onErase()
    }
  }

  @Test
  fun `دکمه حذف فقط برای صاحب و با پرچم روشن پیداست`() = runBlocking {
    val fake = ErFake(previewJson)
    CustomerErasureLive.ensure(fake, now = 10)
    assertTrue("روشن و صاحب ⇒ ۴۰۰ ِ «هدف لازم است»", CustomerErasureLive.gate.visible)
    CustomerErasureLive.gate.reset()
    fake.onProbe = { ApiResult.Failure(ApiFailure.NotFound("پیدا نشد", "not_found")) }
    CustomerErasureLive.ensure(fake, now = 10)
    assertFalse("پرچمِ خاموش ⇒ همان ۴۰۴ ِ درِ ناشناخته", CustomerErasureLive.gate.visible)
    fake.onProbe = { ApiResult.Failure(ApiFailure.Forbidden("فقط صاحب", CustomerErasure.OWNER_ONLY)) }
    CustomerErasureLive.ensure(fake, now = 10 + CustomerErasureLive.TTL_MS)
    assertFalse("شاگرد", CustomerErasureLive.gate.visible)
    assertNull(CustomerErasure.probeSays("offline", refused = false, invalid = false))
    //  رابط هم فقط برای صاحبِ واردشده می‌پرسد
    assertTrue(src("ui/screens/ServerScreensAndroid.kt").contains("ShopRole.current(context) == ShopRole.OWNER && Backend.isReady(context)"))
    for (f in listOf("ui/screens/CustomerErasurePanel.kt", "ui/v3/money/CustomerErasureV3.kt")) {
      val t = src(f)
      assertTrue(f, t.indexOf("rememberErasureApi() ?: return") in 0 until t.indexOf("CustomerErasureLive.ensure"))
      assertFalse(f, t.contains("android."))
    }
  }

  @Test
  fun `حذف پیش نمایش و تایید سخت و تایید هویت و سپس اجرا`() = runBlocking {
    val fake = ErFake(previewJson)
    val asks = mutableListOf<String>()
    var allow = false
    val c = CustomerErasureController(fake, "0700 123 456", { asks += "reauth"; fake.calls += "reauth"; allow }, this)
    assertNull("بی پیش‌نمایش نه تأیید و نه اجرا", c.erase())
    c.preview()!!.join()
    val pv = c.step as CustomerErasure.Step.Preview
    assertEquals(listOf("orders" to 2, "orderLines" to 5, "messages" to 3), pv.result.counts)
    assertEquals(10, pv.result.total)
    assertEquals(listOf("dry 0700 123 456"), fake.calls)
    c.proceed()
    assertTrue(c.step is CustomerErasure.Step.Confirm)
    c.type("1234")
    assertFalse(c.confirmOk)
    assertNull("رقم‌های نادرست", c.erase())
    c.type("۳۴۵۶")
    assertTrue(c.confirmOk)
    //  انصرافِ برگهٔ هویت ⇒ هیچ درخواستی
    c.erase()!!.join()
    assertEquals(listOf("dry 0700 123 456", "reauth"), fake.calls)
    val back = c.step as CustomerErasure.Step.Confirm
    assertNotNull(back.note)
    //  تأیید ⇒ همان دم اجرا
    allow = true
    c.type("3456")
    fake.onErase = { ApiResult.Success(CustomerErasure.result(json(previewJson.replace("\"dryRun\":true", "\"dryRun\":false")))!!) }
    c.erase()!!.join()
    assertEquals(listOf("dry 0700 123 456", "reauth", "reauth", "erase 0700 123 456"), fake.calls)
    val done = c.step as CustomerErasure.Step.Done
    assertEquals(10, done.result.total)
    assertEquals(3, erasureCountLines(done.result).size)
    assertEquals(3, erasureKeptLines.size)
  }

  @Test
  fun `سفارش باز یا هیچ داده اجازه حذف نمی دهد`() = runBlocking {
    val fake = ErFake(previewJson.replace("\"openOrders\":0", "\"openOrders\":2"))
    val c = CustomerErasureController(fake, "0700123456", { true }, this)
    c.preview()!!.join()
    c.proceed()
    assertTrue(c.step is CustomerErasure.Step.Preview)
    val empty = CustomerErasure.result(json("""{"dryRun":true,"mode":"phone","openOrders":0,"counts":{},"total":0,"kept":[]}"""))!!
    assertFalse(CustomerErasure.canProceed(empty))
    assertNull(CustomerErasure.toConfirm(CustomerErasure.Step.Preview(empty)))
    //  اجرای ردشده (سفارشِ تازهٔ باز) ⇒ پیش‌نمایش با پیام
    val s = CustomerErasure.eraseFailed(CustomerErasure.Step.Erasing(empty), "سفارشِ باز")
    assertEquals("سفارشِ باز", (s as CustomerErasure.Step.Preview).note)
    assertFalse(CustomerErasure.usable("12"))
    assertTrue(CustomerErasure.usable("0700123456"))
    assertFalse(CustomerErasure.confirmOk("", "0700123456"))
  }

  @Test
  fun `کلیدهای شمارش همان کلیدهای سرورند به همان ترتیب و هر کدام نام دارند`() {
    val server = File(listOf("../../server/src/lib/customer-erasure.js", "../server/src/lib/customer-erasure.js", "server/src/lib/customer-erasure.js")
      .first { File(it).isFile }).readText()
    val block = Regex("""const COUNT_KEYS = Object\.freeze\(\[([^\]]*)\]\)""").find(server)!!.groupValues[1]
    val keys = Regex("""'(\w+)'""").findAll(block).map { it.groupValues[1] }.toList()
    assertTrue("کلیدهای سرور خوانده شدند: $keys", keys.size >= 20)
    assertEquals("همان کلیدهای سرور، به همان ترتیب", keys, CustomerErasure.COUNT_KEYS)
    for (k in CustomerErasure.COUNT_KEYS) assertNotEquals("$k نامِ خودش را دارد", k, StrErasure.label(k))
    //  کلیدهای «فهرستِ خرید»، پوشِ سفارش و پوشِ «خبرم کن» در خلاصهٔ پاسخ می‌آیند (پیش‌تر دور ریخته می‌شدند)
    val r = CustomerErasure.result(json("""{"dryRun":true,"mode":"phone","openOrders":0,
      "counts":{"lists":1,"pushSubs":2,"alertPushSubs":3,"unknownKey":9},"total":6,"kept":[]}"""))!!
    assertEquals(listOf("lists" to 1, "pushSubs" to 2, "alertPushSubs" to 3), r.counts)
    assertEquals(3, erasureCountLines(r).size)
  }

  // ═════════════════════════════ سیم‌کشی ═════════════════════════════

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/$rel") }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد")

  @Test
  fun `هر سه صفحه وصل اند و پشت پرچم سرورند`() {
    //  پروندهٔ مدرک: فقط با `orders.manage` و پس از پاسخِ روشن
    //  (صفحهٔ قدیمیِ `ui/screens/Orders.kt` بی‌راه بود و در LOC-06 برداشته شد؛ فقط نسخهٔ ۳)
    assertTrue(src("ui/v3/online/orders/OrdersRouteContent.kt").contains("OrderEvidenceEntryV3(o.id, cfg?.permissions?.manage == true,"))
    //  (`OrderEvidenceEntry` ِ رابطِ امروز در `ui/screens/OrderEvidencePanel.kt` بی‌راه بود و برداشته شد، `claude/e-rows-39m`)
    val ev = "ui/v3/online/orders/OrderEvidenceV3.kt"
    val t = src(ev)
    assertTrue(ev, t.indexOf("if (!OrderEvidenceLive.gate.visible) return") in 0 until t.indexOf("open = true"))
    assertTrue(ev, t.contains("if (!canManage"))
    //  پوزش: صفحهٔ نسخهٔ ۳ (گوشی و کامپیوتر) با همان نوشتن، ردیف فقط با پاسخِ روشنِ سرور
    assertTrue(src("ui/v3/settings/sections/LateApologyScreen.kt").contains("LateApologyLive.set("))
    assertTrue(src("ui/v3/settings/SettingsRoutes.kt").contains("shown = { lateApologyRow(lateApologyApi()) }"))
    //  حذف: پروندهٔ مشتریِ کامپیوتر و نسخهٔ ۳ (گوشی)
    assertTrue(src("ui/screens/DeskCustomers.kt").contains("CustomerErasureButton("))
    assertTrue(src("ui/v3/money/DebtorLedgerScreen.kt").contains("CustomerErasureV3(account.debtor.phone)"))
    //  وابستگیِ پلتفرم فقط در `*Android.kt`
    for (f in listOf("ui/screens/OrderEvidencePanel.kt", "ui/screens/OrderEvidenceState.kt", "ui/screens/LateApologySettings.kt", "ui/screens/CustomerErasureState.kt")) {
      assertFalse(f, src(f).contains("android."))
    }
    assertTrue(src("ui/screens/OrderEvidencePrintAndroid.kt").contains("ReportPrint.print("))
  }
}
