package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.market.StockBadge
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.FakeHttp
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.line
import ir.vil3ntec.tohid.core.shopper.ShopperSamples.product
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  CHK-03 — سبدِ حساب در برنامهٔ خرید ([CartSync] و گام‌های سبدِ [AccountRules]) روی سرورِ جعلیِ هم‌قرارداد با
 *  `lib/store-cart.js` ([CartServer]؛ بردارِ مشترکِ `account-cart-v1.json` در `ShopperCartVectorsTest`):
 *    ۱) پرچمِ خاموش یا بی نشست ⇒ همان سبدِ امروزِ دستگاه؛ درِ بسته هرگز از حساب بیرون نمی‌برد.
 *    ۲) ورود ⇒ یک ادغامِ سبدِ مهمان (پاسخش همان یک خواندن) و دیگر هرگز؛ تغییرِ میانهٔ ادغام گم نمی‌شود.
 *    ۳) تغییرهای پشتِ‌هم ⇒ یک PUT با آخرین نسخه پس از سکوت؛ ۴۰۹ ⇒ همان تغییر روی سبدِ تازه، یک بار؛ بی حلقه.
 *    ۴) بی اینترنت، ۵xx، ۴۲۹ ⇒ سبدِ دستگاه می‌ماند و درنگِ فزاینده (`Retry-After` پذیرفته).
 *    ۵) خروج یا نشستِ باطل ⇒ هیچ سبدِ حسابی روی گوشی نمی‌ماند.
 *    ۶) قیمت، «هست؟»، موجودی و سقف همیشه از پاسخِ سرور، با شمارِ پیام.
 */
class ShopperCartSyncTest {

  private val slug = "golestan"
  private val first = AccountSession(CartServer.SESSION_A, expiresAt = 10_000)
  private val second = AccountSession(CartServer.SESSION_B, expiresAt = 20_000)
  private val on = AccountFeatures(cart = true)

  private fun server() = CartServer(slug).apply {
    product("rice", "برنج", "کیلو", 12050)
    product("oil", "روغن", "بوتل", 30000)
    product("salt", "نمک", "بسته", 2000)
  }

  private fun api(srv: CartServer): ShopperApi = ShopperApi(FakeHttp { srv.handle(it) })

  private class Run(val state: ShopperState, val signedOut: Boolean, val outcomes: List<AccountOutcome>)

  /** همان حلقهٔ `ModelAccount.kt`: یک کار در هر بار، حالِ اکنون پس از هر پاسخ؛ درِ بسته ⇒ سبد خاموش و ادامه */
  private fun run(
    api: ShopperApi,
    start: ShopperState,
    at: Long = 50,
    pulls: AccountPulls = AccountPulls(),
    f: AccountFeatures = on,
    session: AccountSession = first,
    during: ((ShopperState) -> ShopperState)? = null,
  ): Run {
    var s = AccountRules.begin(start, slug, session.expiresAt)
    var p = pulls
    var feats = f
    var hook = during
    val outs = mutableListOf<AccountOutcome>()
    repeat(60) {
      val step = AccountRules.next(s, slug, feats, p) ?: return Run(s, false, outs)
      val reply = AccountRules.send(api, session.session, slug, step)
      //  کارِ کاربر در میانهٔ درخواست
      hook?.let {
        s = it(s)
        hook = null
      }
      val out = AccountRules.apply(s, slug, step, reply, at)
      outs += out
      s = out.state
      p = AccountRules.after(p, step, reply)
      if (out.signedOut) return Run(s, true, outs)
      if (out.cartOff) feats = feats.copy(cart = false)
      if (!out.go && !out.cartOff) return Run(s, false, outs)
    }
    error("حلقه تمام نشد")
  }

  private fun guest(vararg l: CartLine) = ShopperState(carts = listOf(Cart(slug, l.toList(), 1)))

  private fun cart(s: ShopperState): List<Pair<String, Long>> = ShopperMemory.cartOf(s, slug).lines.map { it.productId to it.qtyMilli }

  private fun edit(s: ShopperState, f: (Cart) -> Cart): ShopperState = ShopperMemory.withCart(s, f(ShopperMemory.cartOf(s, slug)))

  private fun st(r: Run): AccountSync = r.state.sync.getValue(slug)

  @Test fun flagOffOrNoSessionKeepsTodaysLocalCartAndAClosedDoorNeverSignsOut() {
    val srv = server()
    val api = api(srv)
    val s0 = guest(line("rice", 12050, 2000))
    //  بی نشست (بی حالِ هم‌گامی): هیچ کار، هیچ درنگ؛ خروج سبدِ مهمان را دست نمی‌زند
    assertNull(AccountRules.next(s0, slug, on, AccountPulls.ALL))
    assertEquals(0L, CartSync.waitMs(s0, slug, 5_000))
    assertEquals(s0.carts, AccountRules.signedOut(s0).carts)
    //  پرچمِ خاموش در `features` ⇒ هیچ درخواستِ سبد؛ سبدِ دستگاه همان
    val off = run(api, s0, pulls = AccountPulls.ALL, f = AccountFeatures())
    assertTrue(srv.seen.isEmpty())
    assertEquals(s0.carts, off.state.carts)
    //  پرچم در سرور خاموش شد ولی برنامه هنوز روشن می‌دید ⇒ ۴۰۱ ِ درِ ناشناخته: فقط سبدِ دستگاه، بی خروج و بی درنگ
    srv.flagOn = false
    val closed = run(api, s0, pulls = AccountPulls.ALL)
    assertFalse(closed.signedOut)
    assertTrue(closed.outcomes.single().cartOff)
    assertEquals(s0.carts, closed.state.carts)
    assertNotNull(closed.state.sync[slug], "نشست می‌ماند")
    assertEquals(0L, CartSync.waitMs(closed.state, slug, 50))
    //  سبدِ مهمانِ هرگز ادغام‌نشده با خروج هم می‌ماند
    assertEquals(s0.carts, AccountRules.signedOut(closed.state).carts)
    assertTrue(CartSync.doorClosed(ShopperError(401, "unauthorized")))
    assertTrue(CartSync.doorClosed(ShopperError(404, "store_not_found")))
    assertFalse(CartSync.doorClosed(ShopperError(401, CartSync.SESSION_REQUIRED)))
    assertFalse(CartSync.doorClosed(ShopperError(404, ShopperError.BAD_RESPONSE)), "پاسخِ خرابِ میانه پرچم را خاموش نمی‌کند")
  }

  @Test fun signInMergesTheGuestCartOnceReadsOnceAndNeverMergesItAgain() {
    val srv = server().apply { items = listOf(CartServer.Item("oil", 1000, "تازه باشد")); version = 3 }
    val api = api(srv)
    //  سبدِ مهمان: برنج با قیمتِ دیروز، روغنِ کمتر از حساب
    val r1 = run(api, guest(line("rice", 11000, 2000), line("oil", 30000, 500)), pulls = AccountPulls.ALL)
    assertEquals(listOf("POST /merge"), srv.seen, "پاسخِ ادغام همان یک خواندن است")
    assertEquals(listOf("oil" to 1000L, "rice" to 2000L), srv.brief(), "بیشینه، نه جمع")
    assertEquals(4L, srv.version)
    assertEquals(listOf("oil" to 1000L, "rice" to 2000L), cart(r1.state), "سبدِ دستگاه همان سبدِ حساب")
    assertEquals(12050L, ShopperMemory.cartOf(r1.state, slug).lines.first { it.productId == "rice" }.priceMinor, "قیمتِ امروز")
    assertEquals(1, r1.outcomes.single().repriced)
    assertTrue(st(r1).cartM)
    assertEquals(mapOf("oil" to "تازه باشد"), st(r1).cartNotes, "یادداشتِ وب با نوشتنِ برنامه پاک نمی‌شود")

    //  برگشتِ برنامه با همان نشست: فقط یک خواندن، هرگز ادغامِ دوباره
    srv.seen.clear()
    val r2 = run(api, r1.state, pulls = AccountPulls.ALL)
    assertEquals(listOf("GET /cart"), srv.seen)
    assertEquals(cart(r1.state), cart(r2.state))

    //  خروج ⇒ سبدِ حساب از دستگاه پاک؛ سبدِ مهمانِ تازه در نشستِ بعد فقط خودش ادغام می‌شود
    val out = AccountRules.signedOut(r2.state)
    assertTrue(cart(out).isEmpty())
    srv.seen.clear()
    srv.bodies.clear()
    val r3 = run(api, ShopperMemory.withCart(out, Cart(slug, listOf(line("salt", 2000, 1000)), 7)), pulls = AccountPulls.ALL, session = second)
    assertEquals(listOf("POST /merge"), srv.seen)
    assertEquals("""[{"productId":"salt","qtyMilli":1000}]""", srv.bodies.single()["items"].toString(), "بی قیمت، بی نام، و نه آینهٔ پیشین")
    assertEquals(listOf("oil" to 1000L, "rice" to 2000L, "salt" to 1000L), cart(r3.state))

    //  نشستِ تازه بی خروجِ ثبت‌شده (برنامه در میانه بسته شد): آینهٔ نشستِ پیشین ادغام نمی‌شود، کنار می‌رود و از سرور می‌آید
    srv.seen.clear()
    val stale = run(api, r3.state, pulls = AccountPulls.ALL, session = first.copy(expiresAt = 30_000))
    assertEquals(listOf("GET /cart"), srv.seen)
    assertEquals(cart(r3.state), cart(stale.state))
  }

  @Test fun aChangeDuringTheMergeIsKeptAndNothingOnTheAccountIsLost() {
    val srv = server().apply { items = listOf(CartServer.Item("oil", 1000)); version = 1 }
    val salt = product("salt", 2000)
    val r = run(api(srv), guest(line("rice", 12050, 1000)), during = { s -> edit(s) { CartRules.add(it, salt, 60) } })
    assertEquals(listOf("POST /merge", "PUT /cart"), srv.seen)
    assertEquals(listOf("oil" to 1000L, "rice" to 1000L, "salt" to 1000L), srv.brief(), "روغنِ حساب و نمکِ میانه هر دو ماندند")
    assertEquals(srv.brief(), cart(r.state))
  }

  @Test fun aBurstOfChangesIsOnePutWithTheLastVersionAndAConflictReappliesTheChangeOnce() {
    val srv = server().apply { items = listOf(CartServer.Item("rice", 1000)); version = 1 }
    val api = api(srv)
    var s = run(api, ShopperState(), pulls = AccountPulls.ALL).state
    assertEquals(listOf("GET /cart"), srv.seen, "سبدِ مهمانِ خالی ⇒ بی ادغام، یک خواندن")
    assertEquals(listOf("rice" to 1000L), cart(s))

    //  سه تغییرِ پشتِ‌هم: تا ۸۰۰ میلی‌ثانیه پس از آخرین صبر؛ سپس یک PUT با آخرین نسخه و آخرین مقدارها
    val oil = product("oil", 30000)
    s = edit(s) { CartRules.step(it, "rice", up = true, at = 1_000) }
    s = edit(s) { CartRules.step(it, "rice", up = true, at = 1_200) }
    s = edit(s) { CartRules.add(it, oil, 1_400) }
    assertEquals(700L, CartSync.waitMs(s, slug, 1_500))
    assertEquals(0L, CartSync.waitMs(s, slug, 2_200))
    srv.seen.clear()
    s = run(api, s, at = 2_200).state
    assertEquals(listOf("PUT /cart"), srv.seen)
    assertEquals(1L, srv.bodies.last()["version"]!!.jsonPrimitive.long)
    assertEquals(listOf("oil" to 1000L, "rice" to 3000L), srv.brief())
    assertEquals(2L, srv.version)

    //  دستگاهِ دیگر: روغن را برداشت، برنج ۱، نمک ۲ (نسخهٔ ۳). این دستگاه نسخهٔ ۲ را دیده و یکی از برنج کم می‌کند ⇒ ۴۰۹
    //  ⇒ همان «کم کردن» روی سبدِ تازه: برنج ۲ (کارِ این دستگاه)، نمک ۲ و بی روغن (کارِ آن دستگاه)
    srv.otherDevice("rice" to 1000L, "salt" to 2000L)
    s = edit(s) { CartRules.step(it, "rice", up = false, at = 3_000) }
    srv.seen.clear()
    val c = run(api, s, at = 4_000)
    assertEquals(listOf("PUT /cart", "PUT /cart"), srv.seen)
    assertEquals(listOf("rice" to 2000L, "salt" to 2000L), srv.brief())
    assertEquals(4L, srv.version)
    assertEquals(listOf("rice" to 2000L, "salt" to 2000L), cart(c.state))
    assertEquals(0, st(c).cartC)

    //  دستگاهِ دیگر پیش از هر نوشتن می‌نویسد ⇒ فقط یک بار دوباره (دو PUT)، سپس دورِ بعد با درنگ؛ تغییرِ آخر گم نمی‌شود
    srv.raceEveryPut = true
    val s2 = edit(c.state) { CartRules.step(it, "salt", up = true, at = 5_000) }
    srv.seen.clear()
    val raced = run(api, s2, at = 6_000)
    assertEquals(listOf("PUT /cart", "PUT /cart"), srv.seen, "بی حلقه")
    assertEquals(listOf("rice" to 2000L, "salt" to 3000L), cart(raced.state), "تغییرِ آخر روی دستگاه")
    assertFalse(raced.outcomes.last().go)
    assertEquals(6_000L + CartSync.backoffMs(1), st(raced).retryAt)
    assertTrue(CartSync.waitMs(raced.state, slug, 6_000) > 0)
    //  آرام شد ⇒ دورِ بعد (پس از درنگ) یک PUT و همان تغییر رسید
    srv.raceEveryPut = false
    srv.seen.clear()
    val calm = run(api, raced.state, at = 9_000)
    assertEquals(listOf("PUT /cart"), srv.seen)
    assertEquals(listOf("rice" to 2000L, "salt" to 3000L), srv.brief())
    assertEquals(0L, st(calm).retryAt)
    assertEquals(0, st(calm).fails)
  }

  @Test fun offlineServerErrorsAndRateLimitsKeepTheLocalCartAndBackOff() {
    val srv = server()
    val api = api(srv)
    var s = run(api, guest(line("rice", 12050, 1000))).state
    s = edit(s) { CartRules.step(it, "rice", up = true, at = 100) }
    val pending = cart(s)
    //  بی اینترنت
    srv.down = true
    var r = run(api, s, at = 1_000)
    assertFalse(r.signedOut)
    assertEquals(pending, cart(r.state))
    assertEquals(1, st(r).fails)
    assertEquals(3_000L, st(r).retryAt)
    assertEquals(2_000L, CartSync.waitMs(r.state, slug, 1_000))
    //  ۵۰۳
    srv.down = false
    srv.failTimes = 1
    r = run(api, r.state, at = 3_000)
    assertEquals(pending, cart(r.state))
    assertEquals(2, st(r).fails)
    assertEquals(7_000L, st(r).retryAt)
    //  ۴۲۹ با `Retry-After` ِ سرور
    srv.failWith = 429
    srv.failTimes = 1
    srv.retryAfter = 60
    r = run(api, r.state, at = 7_000)
    assertEquals(3, st(r).fails)
    assertEquals(67_000L, st(r).retryAt, "Retry-After ِ سرور")
    assertEquals(listOf("POST /merge"), srv.seen, "هیچ نوشتنی به سرور نرسید")
    //  برگشت ⇒ یک PUT با همان تغییر؛ شمارِ شکست صفر
    r = run(api, r.state, at = 70_000)
    assertEquals(listOf("POST /merge", "PUT /cart"), srv.seen)
    assertEquals(pending, srv.brief())
    assertEquals(0, st(r).fails)
    assertEquals(0L, st(r).retryAt)
    //  درنگ: ۲، ۴، ۸ … تا ۱۲۰ ثانیه؛ Retry-After تا ربع ساعت
    assertEquals(listOf(2_000L, 4_000L, 8_000L, 64_000L, 120_000L, 120_000L), listOf(1, 2, 3, 6, 7, 40).map { CartSync.backoffMs(it) })
    assertEquals(CartSync.RETRY_AFTER_MAX_MS, CartSync.backoffMs(1, 99_999))
    //  گذرا ⇒ دوباره؛ بقیه ⇒ نه (۴۰۰ با دوباره درست نمی‌شود)
    val transient = listOf(ShopperError(0, ShopperError.NETWORK), ShopperError(503, "x"), ShopperError(429, "rate_limited"), ShopperError(408, "x"))
    assertTrue(transient.all { CartSync.retryable(it) })
    assertFalse(listOf(ShopperError(400, "bad_lines"), ShopperError(403, "x"), ShopperError(409, CartSync.CART_FULL)).any { CartSync.retryable(it) })
  }

  @Test fun signOutAndARevokedSessionLeaveNoAccountCartOnThePhone() {
    val srv = server().apply { items = listOf(CartServer.Item("oil", 2000)); version = 1 }
    val api = api(srv)
    val mine = run(api, ShopperState(), pulls = AccountPulls.ALL).state
    assertEquals(listOf("oil" to 2000L), cart(mine))
    //  دکانِ دیگر: سبدِ مهمانِ بی هم‌گامی
    val withOther = ShopperMemory.withCart(mine, Cart("other", listOf(line("tea", 5000, 1000)), 3))
    val out = AccountRules.signedOut(withOther)
    assertTrue(cart(out).isEmpty(), "سبدِ حساب روی گوشی نمی‌ماند")
    assertEquals(listOf("tea"), ShopperMemory.cartOf(out, "other").lines.map { it.productId })
    assertTrue(out.sync.isEmpty())
    assertFalse("روغن" in ShopperJson.encodeToString(ShopperState.serializer(), out))
    //  نشست در دستگاهِ دیگر بسته شد (۴۰۱ `session_required`) در میانهٔ نوشتن ⇒ بیرون و سبدِ حساب پاک
    srv.sessions.remove(CartServer.SESSION_A)
    val changed = edit(mine) { CartRules.step(it, "oil", up = true, at = 9) }
    val revoked = run(api, changed)
    assertTrue(revoked.signedOut)
    assertTrue(cart(revoked.state).isEmpty())
    assertTrue(revoked.state.sync.isEmpty())
    assertEquals(listOf("oil" to 2000L), srv.brief(), "سرور دست نخورد")
  }

  @Test fun theServerWinsOnPriceAvailabilityStockAndLimits() {
    val srv = server().apply {
      items = listOf(CartServer.Item("rice", 2000), CartServer.Item("oil", 1000), CartServer.Item("salt", 3000))
      version = 1
    }
    val api = api(srv)
    val s = run(api, ShopperState(), pulls = AccountPulls.ALL).state
    assertEquals(listOf(12050L, 30000L, 2000L), ShopperMemory.cartOf(s, slug).lines.map { it.priceMinor })
    //  دکان: قیمتِ برنج بالا، روغن «ناموجود»، نمک بیرون از ویترین
    srv.products.getValue("rice").priceMinor = 13000
    srv.products.getValue("oil").stock = StockBadge.OUT_OF_STOCK
    srv.products.getValue("salt").published = false
    srv.seen.clear()
    val r = run(api, s, pulls = AccountPulls(cart = true))
    assertEquals(listOf("GET /cart"), srv.seen, "مقدارها همان ⇒ هیچ نوشتن")
    val lines = ShopperMemory.cartOf(r.state, slug).lines.associateBy { it.productId }
    assertEquals(13000L, lines.getValue("rice").priceMinor)
    assertFalse(lines.getValue("rice").unavailable)
    assertTrue(lines.getValue("oil").unavailable, "ناموجود ⇒ تا برداشته نشود ثبت بسته")
    assertTrue(lines.getValue("salt").unavailable)
    assertEquals(2000L, lines.getValue("salt").priceMinor, "بی قیمتِ سرور ⇒ قیمتِ دیده‌شده، خط‌خورده")
    assertEquals("نمک", lines.getValue("salt").name)
    val o = r.outcomes.single()
    assertEquals(1, o.repriced)
    assertEquals(2, o.gone)
    assertEquals(ShopperText.repricedCount(1) + " " + ShopperText.goneCount(2), ShopperText.cartFresh(1, 2))
    //  سقفِ تازهٔ هر سفارش ⇒ مقدارِ دستگاه همان سقف و یک PUT
    srv.products.getValue("rice").maxQtyMilli = 1000
    srv.seen.clear()
    val capped = run(api, r.state, pulls = AccountPulls(cart = true))
    assertEquals(listOf("GET /cart", "PUT /cart"), srv.seen)
    assertEquals(1000L, srv.items.first { it.productId == "rice" }.qtyMilli)
    assertEquals(1000L, ShopperMemory.cartOf(capped.state, slug).lines.first { it.productId == "rice" }.qtyMilli)
  }

  @Test fun rebaseKeepsBothDevicesChangesAndOldSavedStateStillLoads() {
    fun i(id: String, q: Long, note: String? = null) = AccountItem(id, q, note)
    val base = listOf(i("a", 1000), i("b", 1000), i("c", 1000))
    //  این دستگاه: a بیشتر، b برداشته، d افزوده؛ آن دستگاه: یادداشتِ a، b و c عوض، e افزوده
    val local = listOf(i("a", 3000), i("c", 1000), i("d", 1000))
    val fresh = listOf(i("a", 1000, "یادداشتِ وب"), i("b", 2000), i("c", 5000), i("e", 1000))
    assertEquals(listOf(i("a", 3000, "یادداشتِ وب"), i("c", 5000), i("e", 1000), i("d", 1000)), CartSync.rebase(base, local, fresh))
    //  بیش از ۱۰۰ قلم ⇒ قلمِ این دستگاه بیرون نمی‌افتد
    val many = (1..100).map { i("f$it", 1000) }
    val capped = CartSync.rebase(emptyList(), listOf(i("mine", 1000)), many)
    assertEquals(CartRules.MAX_LINES, capped.size)
    assertTrue(capped.any { it.productId == "mine" })
    //  حالِ ذخیره‌شدهٔ پیش از CHK-03 بی فیلدهای تازه بار می‌شود
    val old = ShopperJson.decodeFromString(AccountSync.serializer(), """{"t":5,"cartV":3,"cartK":"rice:1000:","cartM":true}""")
    assertEquals(emptyList(), old.cartB)
    assertEquals(0, old.cartC)
    assertEquals(0, old.fails)
    assertEquals(0L, old.retryAt)
    assertEquals(old, ShopperJson.decodeFromString(AccountSync.serializer(), ShopperJson.encodeToString(AccountSync.serializer(), old)))
  }
}
