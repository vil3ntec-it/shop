package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.perm.ApprovalSlip
import ir.vil3ntec.tohid.core.perm.ApproverVault
import ir.vil3ntec.tohid.core.perm.PinGuard
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.StaffPins
import ir.vil3ntec.tohid.data.TillUser
import ir.vil3ntec.tohid.sync.SyncStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 *  سناریوی S46 (§۷.۵، STF-04) روی دیسکِ واقعیِ کامپیوتر — «Ctrl+L صندوق را قفل می‌کند. کاربرِ دوم با PIN
 *  وارد می‌شود. فاکتورِ نیمه‌کارهٔ کاربرِ اول نگه داشته می‌شود و سابقه‌ها به نامِ کاربرِ درست ثبت می‌شوند.»
 *
 *  حسابِ دستگاه صاحب است؛ «علی» (شاگرد) PIN ِ شخصی روی همین دستگاه دارد. PIN با همان `ApproverVault`
 *  (PBKDF2) سنجیده می‌شود؛ سبدِ هر کس جداست؛ نقشِ کاربرِ صندوق هرگز بالاتر از حسابِ دستگاه نیست؛ هر بازکردن
 *  یک ردیفِ سابقه با شناسه و نامِ همان کس. تنظیمِ خاموش ⇒ هیچ‌کدام (همان قفلِ امروز).
 */
class ScenarioS46DesktopTest {
  private val ctx = DesktopContext
  private val draft get() = File(ctx.filesDir, CartStore.FILE)
  private val stash get() = File(ctx.filesDir, "till-carts")
  private val pins get() = StaffPins(ctx)
  private val sync get() = SyncStore(ctx)

  private fun clean() {
    CartStore.awaitWrites()
    draft.delete()
    stash.deleteRecursively()
    listOf("u-owner-s46", "u-ali-s46").forEach { pins.remove(it) }
    TillUser.active.value = null
    TillUser.locked.value = false
    ctx.getSharedPreferences("tohid-ui", 0).edit().remove("till_active").apply()
    TillUser.set(ctx, false)
  }

  //  حالِ پیشینِ دستگاه برای آزمون‌های دیگرِ همین JVM
  private val roles get() = ctx.getSharedPreferences("tohid-ledger", 0)
  private var was: Triple<String, String, String?> = Triple("", "", null)

  @Before fun before() {
    was = Triple(sync.accountId, sync.accountName, roles.getString("shop_role", null))
    clean()
    sync.accountId = "u-owner-s46"
    sync.accountName = "صاحب"
    ShopRole.remember(ctx, ShopRole.OWNER)
    //  شمارِ کمِ PBKDF2 فقط برای سرعتِ آزمون؛ قاعدهٔ PIN همان
    pins.put(ApproverVault.create("u-owner-s46", "صاحب", "owner", "482915", iterations = 1000))
    pins.put(ApproverVault.create("u-ali-s46", "علی", "staff", "739164", iterations = 1000))
  }

  @After fun after() {
    clean()
    sync.accountId = was.first
    sync.accountName = was.second
    roles.edit().apply { if (was.third == null) remove("shop_role") else putString("shop_role", was.third) }.commit()
    ShopRole.policyVersion.value++
  }

  private fun open(): ShopStore = ShopStore(ctx).also { runBlocking { it.load() } }

  @Test
  fun `تنظیمِ خاموش ـ Ctrl+L همان قفلِ امروز و نامِ فروشنده همان حساب`() {
    assertFalse(TillUser.lock(ctx, deviceCode = false))
    assertFalse(TillUser.locked.value)
    assertEquals("صاحب", TillUser.me(ctx))
    assertEquals(ShopRole.OWNER, ShopRole.current(ctx))
  }

  @Test
  fun `S46 قفل، PIN ِ کاربرِ دوم، سبدِ جدا و سابقه به نامِ درست`() = runBlocking<Unit> {
    val store = open()
    val cart = CartStore(ctx)
    TillUser.set(ctx, true)
    val first = listOf(SalesEngine.CartLine("p-rice", 2.0))
    assertTrue(cart.set(first))

    //  Ctrl+L
    assertTrue(TillUser.lock(ctx, deviceCode = false))
    assertTrue(TillUser.locked.value)

    //  PIN ِ غلط هیچ نمی‌کند و شمرده می‌شود؛ PIN ِ درست ⇒ «علی» پشتِ صندوق
    val ali = pins.get("u-ali-s46")!!
    val wrong = ApproverVault.verify(ali, "111222")
    assertFalse(wrong.ok)
    assertEquals(1, wrong.record.failures)
    val ok = ApproverVault.verify(ali, "739164")
    assertTrue(ok.ok)
    TillUser.unlock(ctx, cart, store, TillUser.user(ok.record))
    assertFalse(TillUser.locked.value)
    assertEquals("علی", TillUser.me(ctx))
    assertEquals("u-ali-s46", TillUser.meId(ctx))
    //  شاگرد پشتِ صندوقِ صاحب اختیارِ صاحب را نمی‌گیرد
    assertEquals("staff", ShopRole.current(ctx))
    //  فاکتورِ نیمه‌کارهٔ صاحب جلوی علی نیست، ولی گم هم نشده
    assertTrue(cart.lines.value.isEmpty())
    val mine = listOf(SalesEngine.CartLine("p-tea", 1.0))
    assertTrue(cart.set(mine))

    //  دوباره Ctrl+L و صاحب با PIN ِ خودش: سبدِ خودش برمی‌گردد
    assertTrue(TillUser.lock(ctx, deviceCode = false))
    TillUser.unlock(ctx, cart, store, TillUser.user(pins.get("u-owner-s46")!!))
    assertEquals(first, cart.lines.value)
    assertNull(TillUser.current(ctx))
    assertEquals(ShopRole.OWNER, ShopRole.current(ctx))
    assertEquals("صاحب", TillUser.me(ctx))

    //  و سبدِ علی برای علی می‌ماند
    TillUser.unlock(ctx, cart, store, TillUser.user(pins.get("u-ali-s46")!!))
    assertEquals(mine, cart.lines.value)

    //  سابقه: هر بازکردن با شناسه و نامِ همان کس
    val rows = store.data.value.auditLog.filter { it.type == TillUser.AUDIT_TYPE }.takeLast(3)
    assertEquals(listOf("u-ali-s46", "u-owner-s46", "u-ali-s46"), rows.map { it.refId })
    assertEquals(listOf("علی", "صاحب", "علی"), rows.map { it.by })

    //  فروشِ علی به نامِ علی (همان `seller` ِ صندوق: `TillUser.me`)
    assertEquals("علی", SalesEngine.Checkout(seller = TillUser.me(ctx)).seller)

    //  خاموش کردنِ تنظیم ⇒ همان حسابِ دستگاه
    TillUser.set(ctx, false)
    assertEquals(ShopRole.OWNER, ShopRole.current(ctx))
    assertEquals("صاحب", TillUser.me(ctx))
  }

  @Test
  fun `بی راهِ برگشتِ حسابِ دستگاه قفلِ PIN نمی‌افتد`() {
    TillUser.set(ctx, true)
    pins.remove("u-owner-s46")
    assertFalse("صاحب PIN ندارد و رمزِ دستگاه هم نیست", TillUser.lock(ctx, deviceCode = false))
    assertTrue("رمزِ دستگاه راهِ برگشت است", TillUser.lock(ctx, deviceCode = true))
  }

  /** همهٔ پرونده‌های زیرِ ریشهٔ دادهٔ برنامه (همان که پیش‌تر گشته می‌شد) */
  private fun disk(): String = ctx.filesDir.parentFile!!.walkTopDown().filter { it.isFile && it.length() < 2_000_000 }
    .mapNotNull { runCatching { it.readText() }.getOrNull() }.joinToString("\n")

  private fun fa(pin: String) = pin.map { '۰' + (it - '0') }.joinToString("")

  /**
   *  ⛔ ریشهٔ سرخیِ ناپایدار (main ِ #958، `:148`): پوشهٔ دادهٔ آزمون را ~۳۲۰۰ آزمونِ همین JVM پر می‌کنند (oplog،
   *  سایهٔ همگام‌سازی، زمان‌های میلی‌ثانیه‌ای، شناسه‌ها)؛ در یک اجرای کامل تا ۲۲ هزار پنجرهٔ شش‌رقمیِ گوناگون در آن
   *  هست، پس هر PIN ِ ثابت تا ۲٪ به تصادف «پیدا» می‌شد («482915» در run 42407192906). PIN ِ تازه این را می‌بندد:
   *   • در هیچ پرونده‌ای پیش از کار نیست (نه با رقمِ لاتین، نه فارسی) ⇒ پیدا شدنش فقط از نوشته‌های پس از آن است؛
   *   • پنجره‌ای از ساعتِ ده دقیقهٔ پیشِ رو هم نیست (`onClock`) ⇒ زمان‌هایی که خودِ این راه می‌نویسد (سابقه، oplog،
   *     HLC) هم به تصادف همان PIN نمی‌شوند. همین دو، جز رقم‌های تصادفیِ درونِ شناسه‌ها، همهٔ رقم‌های نوشته‌شده‌اند.
   */
  private fun freshPin(seen: String, t0: Long, vararg taken: String): String =
    generateSequence { (1..6).joinToString("") { kotlin.random.Random.nextInt(10).toString() } }.first { p ->
      PinGuard.newPinProblem(p) == null && p !in seen && fa(p) !in seen && !onClock(p, t0, t0 + 600_000) &&
        taken.none { it == p }
    }

  /**
   *  [p] پنجرهٔ ششْ‌رقمیِ زمانی در [from, to] است؟ میلی‌ثانیهٔ ۱۳ رقمی، ثانیه و HLC ِ ۱۵ رقمیِ صفرپیشوند همه
   *  `⌊T/10^m⌋ mod 10⁶` با m = ۰…۹اند و ⌊T/10^m⌋ روی بازه پیوسته است.
   */
  private fun onClock(p: String, from: Long, to: Long): Boolean {
    val v = p.toLong()
    var d = 1L
    repeat(10) {
      val lo = from / d
      val hi = to / d
      if (hi - lo >= 1_000_000 || Math.floorMod(v - lo, 1_000_000L) <= hi - lo) return true
      d *= 10
    }
    return false
  }

  @Test
  fun `PIN خام هیچ‌جا نوشته نمی‌شود`() = runBlocking<Unit> {
    val t0 = System.currentTimeMillis()
    val before = disk()
    val ownerPin = freshPin(before, t0)
    val aliPin = freshPin(before, t0, ownerPin)
    val wrongPin = freshPin(before, t0, ownerPin, aliPin)

    //  گشتن واقعاً می‌بیند: رشتهٔ تازه در پوشهٔ داده ⇒ پیدا
    val canary = File(ctx.cacheDir, "s46-canary.txt")
    val decoy = freshPin(before, t0, ownerPin, aliPin, wrongPin)
    canary.writeText("x$decoy\n")
    assertTrue(disk().contains(decoy))
    canary.delete()

    //  همهٔ راهِ PIN، هر ردیفِ برگشتی همان‌طور که برنامه ذخیره می‌کند
    pins.put(ApproverVault.create("u-owner-s46", "صاحب", "owner", ownerPin, iterations = 1000))
    pins.put(ApproverVault.create("u-ali-s46", "علی", "staff", aliPin, iterations = 1000))
    val wrong = ApproverVault.verify(pins.get("u-ali-s46")!!, wrongPin)
    assertFalse(wrong.ok)
    pins.put(wrong.record)
    //  با رقمِ فارسی (صفحه‌کلیدِ فارسی)
    val ok = ApproverVault.verify(pins.get("u-ali-s46")!!, fa(aliPin))
    assertTrue(ok.ok)
    pins.put(ok.record)
    val owner = pins.get("u-owner-s46")!!
    val (signedBy, slip) = ApproverVault.approve(
      owner, ownerPin, ApproverVault.draft(owner, "shp_s46", ApprovalSlip.discountNeed("s-46", 2000, 10000), "S46", System.currentTimeMillis()),
    )
    assertNotNull(slip)
    pins.put(signedBy)

    val store = open()
    val cart = CartStore(ctx)
    TillUser.set(ctx, true)
    assertTrue(cart.set(listOf(SalesEngine.CartLine("p-rice", 2.0))))
    assertTrue(TillUser.lock(ctx, deviceCode = false))
    TillUser.unlock(ctx, cart, store, TillUser.user(ok.record))
    assertTrue(TillUser.lock(ctx, deviceCode = false))
    TillUser.unlock(ctx, cart, store, TillUser.user(pins.get("u-owner-s46")!!))

    //  `apply()` ِ prefs در صفِ یک رشته می‌نویسد؛ `commit()` پشتِ همهٔ آن‌ها منتظر می‌ماند ⇒ گشتن پس از آخرین نوشتن
    assertTrue(ctx.getSharedPreferences("tohid-staff-pins", 0).edit().commit())

    val after = disk()
    for (p in listOf(ownerPin, aliPin, wrongPin)) {
      assertFalse("PIN ِ خام روی دیسک", after.contains(p))
      assertFalse("PIN ِ خام (رقمِ فارسی) روی دیسک", after.contains(fa(p)))
    }
  }
}
