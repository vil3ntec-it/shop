package ir.vil3ntec.tohid.desktop

import android.content.Context
import ir.vil3ntec.tohid.data.BackupBundle
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopSettingsBundle
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.sync.v1.HeartbeatCache
import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import ir.vil3ntec.tohid.sync.v1.SoftLock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 *  DATA-SAFETY (`docs/DATA-SAFETY-fa.md`) — نگهبانِ «چه چیزی به سرور/پشتیبان می‌رسد» و «پایانِ اشتراک
 *  چیزی پاک نمی‌کند».
 */
class DataSafetyGuardTest {

  private val ctx = DesktopContext
  private val ledger get() = File(ctx.filesDir, "shop-data.json")
  private val policy get() = ctx.getSharedPreferences("tohid-policy", Context.MODE_PRIVATE)

  private fun clean() {
    HeartbeatCache(ctx).forget()
    SoftLock.refresh(ctx)
    ctx.filesDir.listFiles().orEmpty().filter { it.name.startsWith("shop-data.json") }.forEach { it.deleteRecursively() }
    policy.edit().remove("dsg.rounding").remove("dsg.kept").commit()
    ctx.getSharedPreferences("tohid-session", Context.MODE_PRIVATE).edit().remove("dsg.evil").commit()
    ShopSettingsBundle.dropStaged(ctx)
  }

  @Before fun before() = clean()
  @After fun after() = clean()

  /* ---------------- ۶) دفتر: هر مجموعه همگام می‌شود ---------------- */

  /**
   *  هر مجموعهٔ دفتر (`ShopData`) یا همگام می‌شود (`LedgerDiff.COLLECTIONS`/`LIST_COLLECTIONS`) یا عمداً
   *  مالِ همین دستگاه است و دلیلش این‌جا و در جدولِ DATA-SAFETY آمده. مجموعهٔ تازه بی تصمیم ⇒ سرخ.
   */
  @Test fun `هر مجموعهٔ دفتر به سرور می‌رسد یا عمداً مالِ دستگاه است`() {
    val d = ShopData.serializer().descriptor
    val names = (0 until d.elementsCount).map { d.getElementName(it) }
    val synced = LedgerDiff.COLLECTIONS + LedgerDiff.LIST_COLLECTIONS
    //  شمارهٔ فاکتورِ بعدیِ هر دستگاه مالِ خودش است (کفش پس از بازسازی از ردیف‌ها ساخته می‌شود: `SyncRebuild`)
    //  و سندِ ردشده‌ای که صاحب کنار گذاشت (`ShopData.setAside`): سابقهٔ همان دستگاهِ سازنده، عمداً هرگز به سرور نمی‌رود
    val deviceOnly = setOf("nextInvoiceNo", "setAside")
    assertEquals("مجموعهٔ دفتر بی همگام‌سازی و بی دلیل", emptyList<String>(), names.filter { it !in synced && it !in deviceOnly })
    assertTrue("نامِ همگام‌شده‌ای که دیگر در دفتر نیست", synced.all { it in names })
  }

  /* ---------------- ۴) به‌روزرسانی با دفترِ نسخهٔ پایدارِ قدیم ---------------- */

  /**
   *  `StableUpgradeTest` (#959) فقط `Json` را می‌سنجد؛ این همان دفترِ ساختِ پایدارِ ۳.۲.۳۳۷ را از راهِ **خودِ**
   *  `ShopStore.load` و `save` (همان راهِ گوشی پس از به‌روزرسانی) می‌برد: نوشتن باز، همهٔ ردیف‌ها، و پس از
   *  نخستین ذخیره و باز کردنِ دوباره هیچ ردیفی کم یا عوض نمی‌شود.
   */
  @Test fun `دفترِ پایدارِ قدیم با خودِ ShopStore بار و ذخیره می‌شود و هیچ ردیفی نمی‌افتد`() {
    val fixture = File("../android-native/app/src/test/resources/upgrade/stable-3.2.337/shop-data.json")
    assertTrue(fixture.isFile)
    fixture.copyTo(ledger, overwrite = true)
    val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }
    val old = json.decodeFromString(ShopData.serializer(), fixture.readText())
    assertTrue(old.sales.isNotEmpty() && old.products.isNotEmpty() && old.transactions.isNotEmpty())

    val store = ShopStore(ctx).also { runBlocking { it.load() } }
    assertEquals("دفترِ قدیم خراب خوانده نمی‌شود", null, store.unreadable.value)
    assertEquals(old, store.data.value)
    val added = Product(id = "dsg-up-1", name = "کالای تازه")
    assertTrue(runBlocking { store.save(store.data.value.copy(products = store.data.value.products + added)) })

    val after = ShopStore(ctx).also { runBlocking { it.load() } }.data.value
    assertEquals(old.copy(products = old.products + added), after)
  }

  /* ---------------- ۶) تنظیماتِ دکان در پشتیبان ---------------- */

  @Test fun `تنظیماتِ دکان با پشتیبانِ کامل برمی‌گردد و تنظیمِ موجود عوض نمی‌شود`() {
    policy.edit().putLong("dsg.rounding", 500L).putString("dsg.kept", "backup").commit()
    val zip = ByteArrayOutputStream().also { BackupBundle.write(ctx, it, "{\"products\":[{\"id\":\"dsg-p1\",\"name\":\"x\"}]}").getOrThrow() }.toByteArray()

    //  گوشیِ تازه: کلید نیست؛ ولی یکی را کاربر این‌جا خودش گذاشته
    policy.edit().remove("dsg.rounding").putString("dsg.kept", "here").commit()
    val opened = BackupBundle.read(ctx, ByteArrayInputStream(zip)) { ShopStore(ctx).parseBackup(it) }.getOrThrow()
    assertEquals(1, opened.data.products.size)
    //  تا تأییدِ بازیابی هیچ تنظیمی نمی‌نشیند
    assertFalse(policy.contains("dsg.rounding"))
    BackupBundle.commitPhotos(ctx)

    assertEquals(500L, policy.getLong("dsg.rounding", 0L))
    assertEquals("تنظیمِ موجودِ این دستگاه بی‌صدا عوض نمی‌شود", "here", policy.getString("dsg.kept", null))
  }

  @Test fun `پشتیبانِ دست‌ساز نشست یا همگام‌سازی را نمی‌نویسد`() {
    val crafted = buildJsonObject {
      put("tohid-session", buildJsonObject { put("dsg.evil", buildJsonObject { put("t", JsonPrimitive("s")); put("v", JsonPrimitive("x")) }) })
    }.toString()
    assertEquals(0, ShopSettingsBundle.apply(ctx, crafted))
    assertFalse(ctx.getSharedPreferences("tohid-session", Context.MODE_PRIVATE).contains("dsg.evil"))
  }

  /* ---------------- ۸) پایانِ اشتراک هیچ چیزی پاک نمی‌کند ---------------- */

  @Test fun `اشتراکِ تمام‌شده فقط فروشِ تازه را می‌بندد — دفتر، خواندن و پشتیبان سرِ جایشان`() {
    val base = ShopData(
      products = listOf(Product(id = "dsg-p1", name = "برنج", salePrice = 120.0)),
      sales = listOf(Sale(id = "dsg-s1", invoiceNumber = 1, finalTotal = 120.0, date = "2026-11-01")),
    )
    val store = ShopStore(ctx).also { runBlocking { it.load() } }
    assertTrue(runBlocking { store.save(base) })
    val bytes = ledger.readBytes()

    HeartbeatCache(ctx).write(buildJsonObject {
      put("subscription", buildJsonObject { put("active", JsonPrimitive(false)); put("status", JsonPrimitive("expired")) })
    })
    assertTrue(SoftLock.refresh(ctx))

    val sale = base.copy(sales = base.sales + Sale(id = "dsg-s2", invoiceNumber = 2, finalTotal = 90.0, date = "2026-11-02"))
    assertFalse("فروشِ تازه بسته است", runBlocking { store.save(sale) })
    assertArrayEquals("دفتر دست‌نخورده", bytes, ledger.readBytes())
    val reopened = ShopStore(ctx).also { runBlocking { it.load() } }
    assertEquals("همه‌چیز خوانده می‌شود", base, reopened.data.value)
    assertTrue("پشتیبان گرفته می‌شود", runBlocking { reopened.exportBackup() }.contains("dsg-s1"))
    //  کالا و انبار «همیشه باز» — از دفترِ همان نمونه، همان کاری که هر صفحه می‌کند. ⛔ با `shop.ledger_merge` ِ روشن (پیش‌فرض از
    //  ۱۴۰۵/۰۸/۱۸) دفترِ دست‌سازِ بی هیچ ردیفِ مشترک با این نمونه پایهٔ روشنی ندارد (فقط فهرست‌های خالیِ `emptyList()`) و دفترِ
    //  خالیِ پیش از `load` پایه می‌شود — امن برای داده (هیچ ردیفی حذف نمی‌شود) ولی قفلِ نرم فروش را «تغییرِ کاربر» می‌بیند
    val now = reopened.data.value
    assertTrue(runBlocking { reopened.save(now.copy(products = now.products + Product(id = "dsg-p2", name = "شکر"))) })
  }

  /* ---------------- ۶) هر انبارِ محلی دسته دارد ---------------- */

  /**
   *  هر `SharedPreferences` ِ برنامه در یکی از دسته‌های جدولِ DATA-SAFETY §۶ است. انبارِ تازه بی دسته ⇒
   *  سرخ: باید تصمیم گرفته شود که به سرور/پشتیبان می‌رسد یا عمداً مالِ دستگاه است.
   */
  @Test fun `هر انبارِ تنظیمِ برنامه دسته‌بندی شده است`() {
    val src = File("../android-native/app/src/main/java")
    assertTrue(src.isDirectory)
    val found = HashSet<String>()
    src.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
      val text = f.readText()
      Regex("""getSharedPreferences\(\s*("([^"]+)"|[A-Za-z_][A-Za-z0-9_.]*)""").findAll(text).forEach { m ->
        val lit = m.groupValues[2]
        if (lit.isNotEmpty()) { found += lit; return@forEach }
        val id = m.groupValues[1].substringAfterLast('.')
        Regex("""val\s+$id\s*(?::\s*String)?\s*=\s*"([^"]+)"""").find(text)?.let { found += it.groupValues[1] }
      }
    }
    val unknown = found - CLASSIFIED.keys
    assertEquals("انبارِ تنظیمِ بی دسته — در DATA-SAFETY §۶ و همین فهرست بیاید", emptySet<String>(), unknown)
    assertTrue(ShopSettingsBundle.SHOP_PREFS.all { CLASSIFIED[it] == Kind.BACKUP })
  }

  private enum class Kind { BACKUP, SERVER, DEVICE, GAP }

  /** همان جدولِ DATA-SAFETY §۶ */
  private val CLASSIFIED: Map<String, Kind> = buildMap {
    ShopSettingsBundle.SHOP_PREFS.forEach { put(it, Kind.BACKUP) }
    //  نسخهٔ محلیِ چیزی که منبعش سرور است (یا صفِ فرستادنی به سرور)
    listOf(
      "tohid-heartbeat", "tohid-news", "tohid-promos", "tohid-storefront", "tohid-shop-chat", "order-notify",
      "tohid-doc-chain", "tohid-doc-series", "tohid-hesabpay", "tohid-courier", "tohid-staff-time", "tohid-imgbank",
      "tohid-photos", "tohid-photobot", "device-push",
    ).forEach { put(it, Kind.SERVER) }
    //  عمداً مالِ همین دستگاه: نشست و همگام‌سازی، سرور، قفل، زبان و ظاهر، چاپگر/دستگاه‌ها، پیش‌نویس‌ها
    listOf(
      "tohid", "tohid-api", "tohid-config", "tohid-servers", "tohid-session", "tohid-sync", "tohid-sync-v1",
      "tohid-sync-choice", "tohid-ledger", "tohid-ledger-merge", "tohid-lock", "tohid-locale", "tohid-theme",
      "tohid-ui", "tohid-shell", "tohid-devices", "tohid-location", "tohid-location-ping", "tohid-visit-ping",
      "tohid-otp", "tohid-watch", "tohid_preview", "tohid-debtor-detail", "tohid-stock", "tohid-stock-transfer",
      //  `CloudSettingsRestore`: نشانِ «یک بار برای این حساب@دکان روی همین دستگاه» (claude/no-data-loss)
      "tohid-settings-restore",
      //  سایه و cursor ِ همگام‌سازیِ تنظیماتِ دکان (`ShopSettingsSync`) — خودِ تنظیم‌ها در `SHOP_PREFS`
      "tohid-settings-sync",
      //  صف، نسخه‌ها و cursor ِ پشتیبانِ فاکتورهای نگه‌داشته (`HeldSync`، پرچمِ `shop.held_sync`) — خودِ نگه‌داشته‌ها روی سرورند
      "tohid-held-sync",
      //  «اصلاح و ثبت» ِ صندوقِ سندهای ردشده: کلیدِ سبدِ همین صندوق ⇒ ردیفِ صندوقِ سرور (`ConflictFix`)؛ خودِ ردیف روی سرور است
      "tohid-conflict-fix",
    ).forEach { put(it, Kind.DEVICE) }
    //  مانده (DATA-SAFETY §۶): رمزِ کارمندان عمداً از پشتیبان بیرون است — دوباره گذاشته می‌شود
    put("tohid-staff-pins", Kind.GAP)
  }
}
