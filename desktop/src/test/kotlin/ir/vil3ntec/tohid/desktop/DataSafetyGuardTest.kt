package ir.vil3ntec.tohid.desktop

import android.content.Context
import ir.vil3ntec.tohid.data.BackupBundle
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.DeviceDocsBundle
import ir.vil3ntec.tohid.data.HeldBackup
import ir.vil3ntec.tohid.data.HeldBackupRules
import ir.vil3ntec.tohid.data.HeldBook
import ir.vil3ntec.tohid.data.HeldInvoice
import ir.vil3ntec.tohid.data.HeldInvoiceCodec
import ir.vil3ntec.tohid.data.HeldInvoiceStore
import ir.vil3ntec.tohid.data.HeldInvoices
import ir.vil3ntec.tohid.data.LedgerOwner
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.QuoteBook
import ir.vil3ntec.tohid.data.QuoteStore
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleDraft
import ir.vil3ntec.tohid.data.SaleDraftCodec
import ir.vil3ntec.tohid.data.SavedQuote
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
import java.util.zip.ZipInputStream

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
    DeviceDocsBundle.dropStaged(ctx)
    File(ctx.filesDir, QuoteStore.FILE).delete()
    File(ctx.filesDir, HeldInvoiceStore.FILE).delete()
    File(ctx.filesDir, CartStore.FILE).delete()
    HeldInvoices.reset()
    ir.vil3ntec.tohid.data.HeldSync.cart = null
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

  /**
   *  DATA-SAFETY §۸: پیش‌فاکتور (`quotes.json`) نه در دفتر است نه در همگام‌سازی؛ پیش از این در پشتیبان هم نبود و با نصبِ
   *  دوباره گم می‌شد. حالا در هر پشتیبانِ کامل (پس با پشتیبانِ ابری روی سرور) و پس از تأییدِ بازیابی — فقط روی دستگاهی
   *  که هیچ پیش‌فاکتوری ندارد — برمی‌گردد.
   */
  @Test fun `پیش‌فاکتورها با پشتیبانِ کامل به دستگاهِ تازه برمی‌گردند و پیش‌فاکتورِ موجود عوض نمی‌شود`() {
    val q = SavedQuote(id = "dsg-q1", number = 3, createdAt = 1762500000000, createdDay = "2026-11-01",
      validUntil = "2026-11-08", totalMinor = 24000, by = "صاحب", note = "برای آقای کریمی")
    assertTrue(QuoteStore.update(ctx) { QuoteBook(items = listOf(q)) } != null)
    val zip = ByteArrayOutputStream().also { BackupBundle.write(ctx, it, "{\"products\":[{\"id\":\"dsg-p1\",\"name\":\"x\"}]}").getOrThrow() }.toByteArray()

    //  نصبِ دوباره: پرونده نیست
    File(ctx.filesDir, QuoteStore.FILE).delete()
    BackupBundle.read(ctx, ByteArrayInputStream(zip)) { ShopStore(ctx).parseBackup(it) }.getOrThrow()
    assertTrue("تا تأییدِ بازیابی چیزی نمی‌نشیند", QuoteStore.read(ctx).items.isEmpty())
    BackupBundle.commitPhotos(ctx, onlyMissing = true)
    assertEquals(listOf(q), QuoteStore.read(ctx).items)

    //  دستگاهی که پیش‌فاکتورِ خودش را دارد: هیچ چیز رونویسی یا قاطی نمی‌شود
    val mine = q.copy(id = "dsg-q2", number = 1, totalMinor = 500)
    assertTrue(QuoteStore.update(ctx) { QuoteBook(items = listOf(mine)) } != null)
    BackupBundle.read(ctx, ByteArrayInputStream(zip)) { ShopStore(ctx).parseBackup(it) }.getOrThrow()
    BackupBundle.commitPhotos(ctx)
    assertEquals(listOf(mine), QuoteStore.read(ctx).items)
  }

  /**
   *  DATA-SAFETY §۸: فاکتورهای نگه‌داشته و سبدِ جاری (`held-invoices.json`، `sale-draft.json`) با پرچمِ خاموشِ `shop.held_sync`
   *  (پیش‌فرض) در هیچ پشتیبانی نبودند: نصبِ دوباره یا بازیابیِ پشتیبانِ کامل همه را می‌برد. حالا قلمِ `device/held-invoices.json`
   *  ِ پشتیبانِ کامل (پس با پشتیبانِ ابری روی سرور)؛ بی نگه‌داشته و سبد ⇒ هیچ قلمی، پشتیبان همان پیشین.
   */
  @Test fun `نگه‌داشته‌ها و سبدِ جاری در پشتیبانِ کامل‌اند و بی آن‌ها هیچ قلمی نیست`() {
    fun entries(): List<String> {
      val zip = ByteArrayOutputStream().also { BackupBundle.write(ctx, it, "{\"products\":[{\"id\":\"dsg-p1\",\"name\":\"x\"}]}").getOrThrow() }.toByteArray()
      return ZipInputStream(ByteArrayInputStream(zip)).use { z -> generateSequence { z.nextEntry }.map { it.name }.toList() }
    }
    assertTrue("بی نگه‌داشته و سبد هیچ قلمی", entries().none { it.startsWith("device/held") })
    val h = HeldInvoice(id = "dsg-h1", number = 1, label = "آقای کریمی", lines = listOf(SaleDraft.Line("dsg-p1", 2.0, 95.0)), heldAt = 1_762_600_000_000)
    File(ctx.filesDir, HeldInvoiceStore.FILE).writeText(HeldInvoiceCodec.encode(HeldBook(items = listOf(h))))
    HeldInvoices.reset()
    assertTrue("نگه‌داشته", "device/held-invoices.json" in entries())
    File(ctx.filesDir, HeldInvoiceStore.FILE).delete()
    File(ctx.filesDir, CartStore.FILE).writeText(SaleDraftCodec.encode(SaleDraft(key = "dsg-c1", lines = h.lines)))
    HeldInvoices.reset()
    assertTrue("سبدِ جاری", "device/held-invoices.json" in entries())
  }

  /**
   *  DATA-SAFETY §۸: بازیابیِ پس از ورود (`CloudSettingsRestore`: `commitPhotos(onlyMissing)` و سپس `commitHeld` با دفترِ سرور) —
   *  نگه‌داشته‌ها فقط پس از دفتر، فقط روی دستگاهِ بی فاکتورِ باز، فقط در همان دکان، و نه آن‌که در دفتر فروش رفته؛ و چون این
   *  راه خودکار و بی پرسش است، فقط پشتیبانی که **همین دستگاه** نوشت (`sameMachineOnly`): پشتیبانِ ابریِ گوشیِ دیگرِ همان حساب
   *  روی کامپیوترِ تازه، نگه‌داشته‌های هنوز-بازِ آن گوشی را دوتایی نمی‌کند (هم‌کلید ⇒ Sync v1 دو فروش را در یک ردیف می‌آمیخت).
   */
  @Test fun `نگه‌داشته‌های پشتیبانِ ابری پس از ورود فقط روی دستگاهِ بی فاکتورِ باز و در همان دکان برمی‌گردند`() {
    val heldFile = File(ctx.filesDir, HeldInvoiceStore.FILE)
    fun now(): HeldBook = HeldInvoices.of(ctx).let { runBlocking { it.load() }; it.book.value }
    val h = HeldInvoice(id = "dsg-h2", number = 2, label = "آقای کریمی", lines = listOf(SaleDraft.Line("dsg-p1", 2.0, 95.0)), heldAt = 1_762_600_000_000)
    val sold = h.copy(id = "dsg-s1", number = 3)
    heldFile.writeText(HeldInvoiceCodec.encode(HeldBook(items = listOf(h, sold))))
    HeldInvoices.reset()
    val zip = ByteArrayOutputStream().also { BackupBundle.write(ctx, it, "{\"products\":[{\"id\":\"dsg-p1\",\"name\":\"x\"}]}").getOrThrow() }.toByteArray()
    val ledger = ShopData(products = listOf(Product(id = "dsg-p1", name = "x")), sales = listOf(Sale(id = "dsg-s1", invoiceNumber = 1)))
    fun stage() = BackupBundle.read(ctx, ByteArrayInputStream(zip)) { ShopStore(ctx).parseBackup(it) }.getOrThrow()

    //  نصبِ دوباره
    heldFile.delete(); HeldInvoices.reset()
    stage()
    BackupBundle.commitPhotos(ctx, onlyMissing = true)
    assertTrue("عکس و تنظیم نگه‌داشته نمی‌نشانند — فقط پس از دفتر", now().items.isEmpty())
    assertEquals(1, runBlocking { DeviceDocsBundle.commitHeld(ctx, ledger, sameMachineOnly = true) })
    assertEquals("فروخته در دفترِ سرور هرگز", listOf(h), now().items)
    assertEquals("یک بار", 0, runBlocking { DeviceDocsBundle.commitHeld(ctx, ledger, sameMachineOnly = true) })

    //  دستگاهی که نگه‌داشتهٔ خودش را دارد: هیچ چیز رونویسی یا قاطی نمی‌شود
    val mine = HeldBook(items = listOf(h.copy(id = "dsg-mine", number = 1, label = "مالِ همین دستگاه")))
    heldFile.writeText(HeldInvoiceCodec.encode(mine)); HeldInvoices.reset()
    stage()
    assertEquals(0, runBlocking { DeviceDocsBundle.commitHeld(ctx, ledger, sameMachineOnly = true) })
    assertEquals(mine, now())

    //  دکانِ دیگر روی همین دستگاه: هیچ
    heldFile.delete(); HeldInvoices.reset()
    LedgerOwner.remember(ctx, "u-x", "s-x")
    try {
      stage()
      assertEquals(0, runBlocking { DeviceDocsBundle.commitHeld(ctx, ledger, sameMachineOnly = true) })
      assertTrue(now().items.isEmpty())
    } finally {
      LedgerOwner.remember(ctx, "", "")
    }
    //  پشتیبانِ دستگاهِ دیگرِ همین حساب: راهِ خودکارِ پس از ورود هیچ؛ بازیابیِ آگاهانهٔ فایل (`BackupRestore.confirm`) همه
    heldFile.delete(); HeldInvoices.reset()
    val other = HeldBackupRules.encode(HeldBackup(from = "other-machine", items = listOf(h)))
    File(ctx.cacheDir, "restore-held.json").writeText(other)
    assertEquals(0, runBlocking { DeviceDocsBundle.commitHeld(ctx, ledger, sameMachineOnly = true) })
    assertTrue(now().items.isEmpty())
    File(ctx.cacheDir, "restore-held.json").writeText(other)
    assertEquals(1, runBlocking { DeviceDocsBundle.commitHeld(ctx, ledger) })
    assertEquals(listOf(h), now().items)
    heldFile.delete(); HeldInvoices.reset()
    //  پروندهٔ خراب: هیچ اثری
    File(ctx.cacheDir, "restore-held.json").writeText("{not json")
    assertEquals(0, runBlocking { DeviceDocsBundle.commitHeld(ctx, ledger, sameMachineOnly = true) })
    assertFalse(File(ctx.cacheDir, "restore-held.json").exists())
  }

  /**
   *  DATA-SAFETY §۸ — سیم‌کشیِ «فقط پس از نشستنِ دفتر»: بازیابیِ فایل (`BackupRestore.confirm`) نگه‌داشته‌ها را پس از
   *  `restoreBackup` و ردِ آن (همان حصارِ «اول وارد شوید / اول همگام کنید / صفِ نرفته») می‌نشاند؛ `commitPhotos` (که پیش از
   *  دفتر هم صدا زده می‌شود) هرگز؛ راهِ خودکارِ پس از ورود فقط پشتیبانِ همین دستگاه. (کدِ اندرویدی، پس متن سنجیده می‌شود.)
   */
  @Test fun `نگه‌داشته‌ها فقط پس از حصارِ بازیابی و از راهِ ابری فقط پشتیبانِ همین دستگاه`() {
    fun src(name: String) = File("../android-native/app/src/main/java/ir/vil3ntec/tohid/data/$name").readText()
    val confirm = src("BackupRestoreAndroid.kt").let { it.substring(it.indexOf("suspend fun confirm(")) }
    val restored = confirm.indexOf("store.restoreBackup(")
    val refused = confirm.indexOf("if (!r.ok)")
    val held = confirm.indexOf("DeviceDocsBundle.commitHeld(context, store.data.value)")
    assertTrue("ترتیب: دفتر ⇐ رد ⇐ نگه‌داشته ($restored، $refused، $held)", restored in 0 until refused && refused < held)
    val bundle = src("BackupBundle.kt")
    assertFalse("commitPhotos نگه‌داشته نمی‌نشاند", bundle.substring(bundle.indexOf("fun commitPhotos(")).substringBefore("fun dropStaging(").contains("commitHeld"))
    val cloud = src("CloudSettingsRestore.kt")
    assertTrue(cloud.contains("DeviceDocsBundle.commitHeld(app, store.data.value, sameMachineOnly = true)"))
    assertTrue("پس از خواندنِ سالم", cloud.indexOf("isFailure") in 0 until cloud.indexOf("DeviceDocsBundle.commitHeld(app,"))
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
      //  LOC-04: نسخهٔ محلیِ جدولِ تعطیلاتِ رسمیِ سرور (`ShopDayChoice`، روزی یک بار)؛ تنظیمِ دکان نیست
      "tohid-holidays",
      //  POS-04: نسخهٔ محلیِ فهرستِ مرکزیِ مترادفِ سرور (`TillSynonyms`، روزی یک بار)؛ تنظیمِ دکان نیست
      "tohid-synonyms",
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
      //  OPS-02: پرچمِ «پشتیبانِ دستی و پوشه هم رمزدار» و شکستِ پشتیبانِ پوشه (`BackupDevice`)؛ کلیدِ عبارت مالِ همین دستگاه است
      "tohid-backup-device",
    ).forEach { put(it, Kind.DEVICE) }
    //  مانده (DATA-SAFETY §۶): رمزِ کارمندان عمداً از پشتیبان بیرون است — دوباره گذاشته می‌شود
    put("tohid-staff-pins", Kind.GAP)
  }
}
