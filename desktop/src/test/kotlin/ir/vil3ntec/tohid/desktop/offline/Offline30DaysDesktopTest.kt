package ir.vil3ntec.tohid.desktop.offline

import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.core.perm.ApprovalSlip
import ir.vil3ntec.tohid.core.sync.OpBacklog
import ir.vil3ntec.tohid.data.BackupClock
import ir.vil3ntec.tohid.data.CashShift
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.data.HeldBook
import ir.vil3ntec.tohid.data.HeldDisk
import ir.vil3ntec.tohid.data.HeldExtras
import ir.vil3ntec.tohid.data.HeldInvoiceCodec
import ir.vil3ntec.tohid.data.HeldInvoiceStore
import ir.vil3ntec.tohid.data.HeldRules
import ir.vil3ntec.tohid.data.LocalSnapshots
import ir.vil3ntec.tohid.data.SaleDraft
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.sync.License
import ir.vil3ntec.tohid.sync.OfflineCode
import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import ir.vil3ntec.tohid.sync.v1.OpQueue
import ir.vil3ntec.tohid.sync.v1.Ulid
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 *  SYN-01 (P0) — «همه کارهای صندوق، برگشت، انبار، قرض و شیفت تا ۳۰ روز بدون اینترنت کار کنند»: دکانِ شلوغ با دو
 *  دستگاه (کامپیوترِ صاحب و گوشیِ شاگرد)، ۳۰ روزِ بی اینترنت با ساعتِ دست‌گردان، و بازگشت به سرورِ **واقعی**.
 *
 *  ── چه چیزی واقعی است ─────────────────────────────────────────────────────────────────────────────────────
 *   • کارِ دکان: `SalesEngine.record` (نقد، قرض، تخفیف)، `recordReturn`، `WarehouseEngine` (کالا، ورودِ انبار،
 *     انبارگردانی، ویرایشِ قیمت)، `DebtorEngine` (قرض‌دار، دریافتِ قرض)، `CashShift` (باز و بستنِ صندوق)، `HeldRules`
 *     و `HeldInvoiceStore` (فاکتورِ نگه‌داشته) — همه با `now`/`today` ِ تزریق‌پذیرِ خودشان ([ShopClock]).
 *   • همگام‌سازی: `SyncCommit` + `OpQueue` + `SyncStateV1` روی دیسکِ جدای هر دستگاه، `SyncV1Client` ⇐ `ApiClient` ⇐
 *     `HttpEngine` (شرحِ [ShopDevice])؛ سرور همان سرورِ مخزن (`server/test/e2e/lib/offline30-backend.mjs`) روی PGlite
 *     یا PostgreSQL (`OFFLINE30_DATABASE_URL`)، با ساعتِ سرورِ دست‌گردان و پراکسیِ «شبکهٔ دکان».
 *   • op ها ساعتِ دیواریِ دستگاه را دارند (`LedgerDiff.diff` ِ `SyncCommit.record`)؛ سند و هر قاعدهٔ «منقضی‌شدنی» ساعتِ دکان را.
 *
 *  ── اندازه و اجرا ───────────────────────────────────────────────────────────────────────────────────────
 *   پیش‌فرض: ۳۰ روز، کامپیوتر ۷۰ و گوشی ۳۰ کارِ دکان در روز (≈ ۵۴۰ op در روز؛ صفِ کامپیوتر روزِ ۲۸ از ۱۰٬۰۰۰ می‌گذرد).
 *   سنجهٔ عمیق (متغیرِ محیط؛ Gradle همان را به JVM ِ آزمون می‌دهد، با `cleanTest` تا «UP-TO-DATE» نشود): `OFFLINE30_DAYS`،
 *   `OFFLINE30_OWNER`، `OFFLINE30_STAFF`، `OFFLINE30_SOLO`؛
 *   PostgreSQL ِ محلی: `OFFLINE30_DATABASE_URL=postgres://…/<پایگاهِ جدا>` (schema ِ public از نو ساخته می‌شود).
 *   بی node یا `server/node_modules` ⇒ آزمونِ اصلی رد با پیام (همان `assume` ِ `SyncCommitDesktopTest`)؛ آزمونِ بی‌سرور همیشه.
 */
class Offline30DaysDesktopTest {

  private val days = env("OFFLINE30_DAYS")?.toIntOrNull() ?: 30
  private val ownerPerDay = env("OFFLINE30_OWNER")?.toIntOrNull() ?: 70
  private val staffPerDay = env("OFFLINE30_STAFF")?.toIntOrNull() ?: 30
  private val work = File(System.getProperty("java.io.tmpdir"), "offline30-${System.nanoTime().toString(36)}")
  private val DAY = ShopClock.DAY

  @After fun clean() { work.deleteRecursively() }

  /* ════════════════════════ کارِ یک روزِ دکان ════════════════════════ */

  private class Tally {
    val kinds = LinkedHashMap<String, Int>()
    var failed = 0
    val failures = LinkedHashMap<String, Int>()
    fun ok(k: String) { kinds[k] = (kinds[k] ?: 0) + 1 }
    fun no(k: String, why: String) { failed++; failures["$k: ${why.take(60)}"] = (failures["$k: ${why.take(60)}"] ?: 0) + 1 }
  }

  private fun newId(): String = Ulid.next()

  private fun ShopDevice.sell(rnd: Random, clock: ShopClock, t: Tally, owner: Boolean, debtors: List<String>) {
    val d = ledger
    val picks = d.products.shuffled(rnd).take(1 + rnd.nextInt(3))
    val cart = picks.map { SalesEngine.CartLine(it.id, (1 + rnd.nextInt(3)).toDouble()) }
    val credit = rnd.nextInt(100) < 22
    val discount = owner && rnd.nextInt(100) < 35   //  ⛔ شاگرد بی برگهٔ تأیید تخفیف نمی‌دهد (`sales.discount` ِ نقشِ staff = deny)
    val checkout = SalesEngine.Checkout(
      seller = seller,
      discountType = if (rnd.nextBoolean()) SalesEngine.DiscountType.PERCENT else SalesEngine.DiscountType.AMOUNT,
      discountValue = if (!discount) 0.0 else if (rnd.nextBoolean()) (3 + rnd.nextInt(8)).toDouble() else 10.0,
      payment = if (credit) SalesEngine.Payment.CREDIT else SalesEngine.Payment.CASH,
      paidAmount = if (credit) (rnd.nextInt(3) * 20).toDouble() else 0.0,
      debtorId = if (credit) debtors[rnd.nextInt(debtors.size)] else null,
    )
    when (val r = SalesEngine.record(d, cart, checkout, clock.today, clock.now, ::newId, series = series)) {
      is SalesEngine.Result.Ok -> { save(r.data); t.ok(if (credit) "sale.credit" else if (discount) "sale.discount" else "sale.cash") }
      is SalesEngine.Result.Failed -> t.no("sale", r.message)
    }
  }

  private fun ShopDevice.giveBack(rnd: Random, clock: ShopClock, t: Tally) {
    val mine = ledger.sales.filter { it.seller == seller && it.status != "cancelled" }.takeLast(40)
    val sale = mine.randomOrNull(rnd) ?: return t.no("return", "no sale")
    val item = ledger.saleItems.firstOrNull { it.saleId == sale.id && SalesEngine.returnable(it) >= 1.0 } ?: return t.no("return", "nothing left")
    when (val r = SalesEngine.recordReturn(ledger, sale.id, mapOf(item.id to 1.0), "مشتری پس آورد", clock.today, clock.now, ::newId, by = seller)) {
      is SalesEngine.Result.Ok -> { save(r.data); t.ok("return") }
      is SalesEngine.Result.Failed -> t.no("return", r.message)
    }
  }

  private fun ShopDevice.payDebt(rnd: Random, clock: ShopClock, t: Tally, debtors: List<String>) {
    val owing = debtors.filter { ShopStore.debt(ledger, it) > 10 }
    val who = owing.randomOrNull(rnd) ?: return t.no("debt.pay", "nobody owes")
    val amount = minOf(ShopStore.debt(ledger, who), (10 + rnd.nextInt(10) * 10).toDouble())
    when (val r = DebtorEngine.addTransaction(ledger, who, DebtorEngine.Kind.RECEIVE, amount, clock.today, "پرداختِ قرض", clock.today, clock.now, ::newId)) {
      is DebtorEngine.Result.Ok -> { save(r.data); t.ok("debt.pay") }
      is DebtorEngine.Result.Failed -> t.no("debt.pay", r.message)
    }
  }

  private fun ShopDevice.receive(rnd: Random, clock: ShopClock, t: Tally) {
    val p = ledger.products.random(rnd)
    val draft = WarehouseEngine.EntryDraft(productId = p.id, units = (20 + rnd.nextInt(60)).toDouble(), unit = p.unit, price = p.purchasePrice)
    when (val r = WarehouseEngine.addEntry(ledger, draft, clock.today, clock.now, ::newId)) {
      is WarehouseEngine.Result.Ok -> { save(r.data); t.ok("stock.receipt") }
      is WarehouseEngine.Result.Failed -> t.no("stock.receipt", r.message)
    }
  }

  private fun ShopDevice.count(rnd: Random, clock: ShopClock, t: Tally) {
    val p = ledger.products.random(rnd)
    val have = ShopStore.stock(ledger, p.id)
    val counted = maxOf(0.0, have + (rnd.nextInt(5) - 2))
    when (val r = WarehouseEngine.stockCount(ledger, mapOf(p.id to counted), "انبارگردانیِ شب", clock.today, clock.now, ::newId, kind = WarehouseEngine.AdjustKind.COUNT)) {
      is WarehouseEngine.Result.Ok -> { save(r.data); t.ok("stock.count") }
      is WarehouseEngine.Result.Failed -> t.no("stock.count", r.message)
    }
  }

  /** هر روز هر دو دستگاه قیمتِ همان کالا را عوض می‌کنند (بی خبر از هم) — تعارضِ همان ردیف */
  private fun ShopDevice.reprice(productId: String, price: Double, clock: ShopClock, t: Tally) {
    val p = ledger.products.first { it.id == productId }
    val draft = WarehouseEngine.ProductDraft(name = p.name, category = p.category, unit = p.unit, purchasePrice = p.purchasePrice, salePrice = price, minStock = p.minStock)
    when (val r = WarehouseEngine.editProduct(ledger, p.id, draft, clock.today, clock.now, ::newId)) {
      is WarehouseEngine.Result.Ok -> { save(r.data); t.ok("product.edit") }
      is WarehouseEngine.Result.Failed -> t.no("product.edit", r.message)
    }
  }

  private fun ShopDevice.shift(open: Boolean, clock: ShopClock, t: Tally) {
    val r = if (open) CashShift.open(ledger, 1000.0, seller, clock.today, clock.now, ::newId)
      else CashShift.close(ledger, CashShift.totalsOf(ledger, CashShift.current(ledger)!!, clock.now).expected, "بستنِ شب", seller, clock.today, clock.now, ::newId)
    when (r) {
      is CashShift.Result.Ok -> { save(r.data); t.ok(if (open) "shift.open" else "shift.close") }
      is CashShift.Result.Failed -> t.no("shift", r.message)
    }
  }

  /** فاکتورِ نگه‌داشته: امروز نگه داشته، فردا (یا روزِ آخر) از سر گرفته و فروخته */
  private class Held(val disk: File) : HeldDisk {
    override fun read(): String? = disk.takeIf { it.isFile }?.readText()
    override fun write(text: String) { disk.parentFile.mkdirs(); disk.writeText(text) }
  }

  private fun ShopDevice.hold(rnd: Random, clock: ShopClock, disk: Held, t: Tally): String? {
    val book = HeldInvoiceCodec.decode(disk.read()) ?: HeldBook()
    val p = ledger.products.random(rnd)
    val draft = SaleDraft(key = newId(), lines = listOf(SaleDraft.Line(p.id, 2.0)), savedAt = clock.now)
    return when (val r = HeldRules.hold(book, draft, HeldExtras(), "میزِ ${rnd.nextInt(9) + 1}", null, clock.now, deviceId)) {
      is HeldRules.Result.Ok -> { disk.write(HeldInvoiceCodec.encode(r.book)); t.ok("held.hold"); draft.key }
      is HeldRules.Result.Refused -> { t.no("held.hold", r.message); null }
    }
  }

  private fun ShopDevice.resume(id: String, clock: ShopClock, disk: Held, t: Tally) {
    val book = HeldInvoiceCodec.decode(disk.read()) ?: HeldBook()
    when (val r = HeldRules.resume(book, id, SaleDraft(), HeldExtras(), clock.now, deviceId, ledger)) {
      is HeldRules.Result.Ok -> {
        disk.write(HeldInvoiceCodec.encode(r.book.copy(current = null)))
        val cart = r.cart!!.cart()
        when (val s = SalesEngine.record(ledger, cart, SalesEngine.Checkout(seller = seller), clock.today, clock.now, ::newId, series = series, saleId = id)) {
          is SalesEngine.Result.Ok -> { save(s.data); t.ok("held.resume.sale") }
          is SalesEngine.Result.Failed -> t.no("held.resume", s.message)
        }
      }
      is HeldRules.Result.Refused -> t.no("held.resume", r.message)
    }
  }

  private fun ShopDevice.day(rnd: Random, clock: ShopClock, n: Int, owner: Boolean, debtors: List<String>, shared: String, d: Int, disk: Held, t: Tally, held: MutableList<String>) {
    clock.at(d, if (owner) 7 * 60 else 8 * 60)
    shift(true, clock, t)
    //  فاکتورِ نگه‌داشتهٔ دیروز از سر گرفته می‌شود (جز نخستینِ روزِ یک که تا روزِ آخر می‌ماند)
    if (held.size > 1) resume(held.removeAt(held.size - 1), clock, disk, t)
    val step = (13 * 3600_000L) / (n + 2)
    for (i in 0 until n) {
      clock.tick(step)
      val roll = rnd.nextInt(100)
      when {
        roll < 58 -> sell(rnd, clock, t, owner, debtors)
        roll < 68 -> payDebt(rnd, clock, t, debtors)
        roll < 76 -> giveBack(rnd, clock, t)
        roll < 84 -> receive(rnd, clock, t)
        roll < 88 && owner -> count(rnd, clock, t)
        else -> sell(rnd, clock, t, owner, debtors)
      }
      if (i == n / 2) reprice(shared, (100 + d * 2 + if (owner) 1 else 0).toDouble(), clock, t)
      if (i == n / 3) hold(rnd, clock, disk, t)?.let { held += it }
    }
    clock.tick(step)
    shift(false, clock, t)
  }

  /** روزِ صفر: ۴۰ کالا با ۴۰۰ موجودی و ۱۴ قرض‌دار — همان درهای «کالای تازه»، «ورودِ انبار» و «قرض‌دارِ تازه» */
  private fun seed(dev: ShopDevice, clock: ShopClock) {
    val names = listOf("برنج", "شکر", "روغن", "چای", "آرد", "نمک", "صابون", "رب", "عدس", "لوبیا")
    repeat(40) { i ->
      clock.tick(30_000)
      val draft = WarehouseEngine.ProductDraft(name = "${names[i % names.size]} ${i + 1}", category = "خوراکی", unit = "عدد", purchasePrice = 70.0, salePrice = 100.0 + i)
      val r = WarehouseEngine.addProduct(dev.ledger, draft, clock.now, ::newId) as WarehouseEngine.Result.Ok
      dev.save(r.data)
      val e = WarehouseEngine.addEntry(dev.ledger, WarehouseEngine.EntryDraft(productId = r.id, units = 400.0, unit = "عدد", price = 70.0), clock.today, clock.now, ::newId) as WarehouseEngine.Result.Ok
      dev.save(e.data)
    }
    repeat(14) { i ->
      val r = DebtorEngine.add(dev.ledger, DebtorEngine.DebtorDraft(name = "مشتریِ ${i + 1}", phone = "07990000${10 + i}"), clock.now, ::newId) as DebtorEngine.Result.Ok
      dev.save(r.data)
    }
  }

  /** نسخهٔ خودکارِ شبانهٔ دفتر (`AutoBackup` ⇐ `LocalSnapshots`) با همان قاعدهٔ نگه‌داریِ «۶ آخر + ۷ روزانه + ۴ هفتگی» */
  private fun nightly(dev: ShopDevice, clock: ShopClock, snapDir: File, ledgerFile: File) {
    ledgerFile.writeText(LedgerDiff.toJson(dev.ledger).toString())
    LocalSnapshots.takeIfChanged(ledgerFile, snapDir, "auto-${clock.today}.json")?.setLastModified(clock.now)
    LocalSnapshots.prune(snapDir, { 0 })
  }

  /** برگهٔ تأییدِ تخفیف روی یکی از فاکتورهای روزِ ۳ ِ قطعی — زمانِ برگه همان زمانِ فاکتور */
  private fun slipOf(dev: ShopDevice, shopId: String, clock: ShopClock): Pair<ApprovalSlip.Slip, ApprovalSlip.Need> {
    val sale = dev.ledger.sales.first { it.createdAt > clock.start + 3 * DAY }
    val slip = ApprovalSlip.Slip(shop = shopId, doc = "sales/${sale.id}", perm = "sales.discount", pctBp = 2000, amount = null,
      by = dev.userId, key = "k-off30", iat = sale.createdAt - 60_000, exp = sale.createdAt + 10 * 60_000, nonce = "off30nonce0000001", reason = "مشتریِ همیشگی")
    return slip to ApprovalSlip.Need(perm = "sales.discount", doc = slip.doc, pctBp = 1500, at = sale.createdAt)
  }

  /**
   *  قاعده‌های منقضی‌شدنیِ خودِ دستگاه در ساعتِ ۹ ِ روزِ [d] (بی سرور): برگهٔ تأیید، فاکتورِ نگه‌داشته، پشتیبان و نسخه‌های
   *  خودکار. هیچ‌کدام کاری را نمی‌بندد یا چیزی از دفتر و صف برنمی‌دارد.
   */
  private suspend fun localRules(dev: ShopDevice, clock: ShopClock, d: Int, shopId: String, firstHeld: String, disk: Held, snapDir: File, ledgerFile: File): String {
    val now = clock.start + d * DAY + 9 * 3600_000L
    clock.now = now
    val queued = dev.queue.size()
    //  برگهٔ تأیید با زمانِ op سنجیده می‌شود، نه ساعتِ امروز
    val (slip, need) = slipOf(dev, shopId, clock)
    assertNull("روزِ $d برگهٔ تأیید", ApprovalSlip.covers(slip, need))
    assertEquals("اگر با ساعتِ امروز سنجیده می‌شد…", "approval_expired", ApprovalSlip.covers(slip, need.copy(at = now)))
    //  فاکتورِ نگه‌داشتهٔ روزِ یک: «کهنه» دیده می‌شود ولی پاک‌سازیِ خودکار (پیش‌فرض خاموش) دست نمی‌زند
    val held = HeldInvoiceStore(disk, { dev.deviceId }, { clock.now })
    held.load()
    assertTrue("روزِ $d نگه‌داشتهٔ روزِ یک کهنه دیده می‌شود", HeldRules.stale(held.book.value, now).any { it.id == firstHeld })
    assertEquals(0, held.autoDropStale(on = false))
    assertTrue("روزِ $d نگه‌داشته سرِ جایش", HeldInvoiceCodec.decode(disk.read())!!.items.any { it.id == firstHeld })
    //  پشتیبان: «۷ روز پشتیبان نگرفته‌اید» فقط هشدار است؛ نسخه‌های خودکار با قاعدهٔ نگه‌داری، دفتر دست‌نخورده
    val since = BackupClock.daysSince(dev.ctx, now) ?: -1
    assertTrue("روزِ $d از آخرین پشتیبان: $since", since >= BackupClock.STALE_DAYS)
    val snaps = LocalSnapshots.list(snapDir)
    assertTrue("روزِ $d نسخه‌های خودکار: ${snaps.size}", snaps.isNotEmpty() && snaps.size <= 6 + 7 + 4)
    assertEquals(LedgerDiff.toJson(dev.ledger).toString(), ledgerFile.readText())
    assertEquals("روزِ $d صف دست‌نخورده", queued, dev.queue.size())
    return "slip=ok held=kept(stale) backups=${snaps.size} sinceBackup=${since}d"
  }

  /* ════════════════════════ آزمونِ اصلی ════════════════════════ */

  @Test fun `دکانِ شلوغ با دو دستگاه ۳۰ روز بی اینترنت ⇒ همه یک بار به سرور و یکسان`() {
    val root = OfflineBackend.repoRoot()
    val missing = OfflineBackend.available(root)
    assumeTrue("سرورِ واقعی در دسترس نیست: $missing", missing == null)
    work.mkdirs()
    val t0 = System.currentTimeMillis()
    OfflineBackend.start(root!!, File(work, "backend.log")).use { be -> runBlocking { scenario(be, t0) } }
  }

  private suspend fun scenario(be: OfflineBackend, t0: Long) {
    val rnd = Random(30)
    val setup = be.call("POST", "/setup", obj("subscriptionDays" to 120))
    val shopId = setup.str("shopId")
    val publicKey = setup.str("publicKey")
    val o = setup.obj("owner")
    val s = setup.obj("staff")
    val clock = ShopClock(System.currentTimeMillis())
    val A = ShopDevice("owner-pc", o.str("deviceId"), o.str("userId"), "صاحب", setup.str("api"), MemTokens(o.str("access"), o.str("refresh")), File(work, "A"), "1.0.9999")
    val B = ShopDevice("staff-phone", s.str("deviceId"), s.str("userId"), "علی", setup.str("api"), MemTokens(s.str("access"), s.str("refresh")), File(work, "B"), "3.2.9999")
    val heldA = Held(File(work, "A/held.json"))
    val heldB = Held(File(work, "B/held.json"))
    val tallyA = Tally()
    val tallyB = Tally()

    /* ── روزِ صفر، آنلاین: کالا، قرض‌دار، سریِ فاکتور، مجوز ── */
    be.serverDay(0.0)
    clock.at(0, 6 * 60)
    A.firstSync(); B.firstSync()
    for (dev in listOf(A, B)) dev.series = ApiJson.text(dev.api.post("/shop/series", buildJsonObject { put("deviceId", dev.deviceId) }), "series")
    assertTrue("سریِ جدای هر دستگاه: ${A.series} / ${B.series}", A.series.isNotBlank() && B.series.isNotBlank() && A.series != B.series)
    seed(A, clock)
    assertTrue(A.sync().isSuccess); assertTrue(B.sync().isSuccess)
    assertEquals("روزِ صفر هر دو دستگاه همان دفتر", 0, LedgerDiff.diff(LedgerDiff.shadowOf(A.ledger), LedgerDiff.toJson(B.ledger), 0).size)
    val debtors = A.ledger.debtors.map { it.id }
    val shared = A.ledger.products.first().id
    val day0Ops = mapOf(A to A.made.size, B to B.made.size)

    //  مجوزِ اشتراک: یکی با پرچمِ خاموش (رفتارِ امروز)، یکی با `shop.license_offline_30d`
    suspend fun license(dev: ShopDevice) = ApiJson.text(dev.api.post("/license/sync", buildJsonObject {
      put("device", buildJsonObject { put("uid", dev.deviceId); put("name", dev.label) })
    }), "license")
    val lic10 = license(A)
    be.call("POST", "/flag", obj("key" to "shop.license_offline_30d", "value" to true))
    val licA = license(A)
    val licB = license(B)
    //  کدِ اشتراکِ آفلاینِ ۳۰ روزهٔ همین کامپیوتر (پنلِ مدیر)
    val fingerprint = "off30-desk-fingerprint-${A.deviceId}"
    val offline = be.call("POST", "/offline-code", obj("computer" to OfflineCode.computerId(fingerprint), "days" to 30)).str("code")
    BackupClock.mark(A.ctx)
    val firstHeld = A.hold(rnd, clock, heldA, tallyA)!!

    /* ── قطعِ اینترنت ── */
    be.net(false)
    A.online = false; B.online = false
    val pushed0 = A.pushes to B.pushes
    val heldA2 = mutableListOf(firstHeld)
    val heldB2 = mutableListOf<String>()
    val backlog = ArrayList<String>()
    var crossed = -1
    val snapDir = File(work, "A/files/snapshots").apply { mkdirs() }
    val ledgerFile = File(work, "A/files/shop-data.json")
    val queueDaily = ArrayList<Pair<Int, Int>>()

    for (d in 1..days) {
      //  هر صبح برنامه از نو باز می‌شود — صف، سایه و ژورنال از دیسک
      A.boot(); B.boot()
      A.day(rnd, clock, ownerPerDay, true, debtors, shared, d, heldA, tallyA, heldA2)
      B.day(rnd, clock, staffPerDay, false, debtors, shared, d, heldB, tallyB, heldB2)
      //  هیچ op ای در قطعی بیرون نرفت (و نباید رفت)
      assertEquals(pushed0, A.pushes to B.pushes)
      nightly(A, clock, snapDir, ledgerFile)
      //  «X تغییر هنوز به سرور نرسیده» — همان آستانهٔ `OpBacklog.WARN_AT`
      for (dev in listOf(A, B)) {
        val q = dev.queue.size()
        val w = OpBacklog.warning(q)
        assertEquals("روزِ $d ${dev.label}: پیام از $q", q >= OpBacklog.WARN_AT, w.isNotEmpty())
        if (dev === A && w.isNotEmpty() && crossed < 0) { crossed = d; backlog += "روزِ $d: $w" }
      }
      queueDaily += d to (A.queue.size() + B.queue.size())
    }
    assertTrue("فاکتورِ نگه‌داشتهٔ روزِ یک هنوز هست", HeldInvoiceCodec.decode(heldA.read())!!.items.any { it.id == firstHeld })

    /* ── قاعده‌های منقضی‌شدنی در روزهای ۲۹، ۳۰ و ۳۱ — پیش از هر اتصال ── */
    val expiry = ArrayList<String>()
    val (slip, need) = slipOf(A, shopId, clock)
    for (d in listOf(29, 30, 31)) {
      val now = clock.start + d * DAY + 9 * 3600_000L
      be.serverDay(d + 9 / 24.0)
      val c = be.call("POST", "/check", obj("access" to o.str("access"), "refresh" to A.tokens.refreshToken, "staffUserId" to B.userId, "staffDeviceId" to B.deviceId))
      //  ۱) توکنِ دسترسی (۶۰ دقیقه) مرده، تازه‌سازی (۹۰ روز) زنده ⇒ بازگشت بی ورودِ دوباره
      assertEquals("روزِ $d توکنِ دسترسی", false, (c["access"] as JsonPrimitive).content.toBoolean())
      assertEquals("روزِ $d توکنِ تازه‌سازی", true, (c["refresh"] as JsonPrimitive).content.toBoolean())
      //  ۲) اشتراک (۱۲۰ روز) با ساعتِ سرور، شاگرد می‌نویسد (`multi_device`)
      val ent = c.obj("entitlement")
      assertEquals("روزِ $d اشتراک", "subscription", ent.str("source"))
      assertEquals("روزِ $d شاگرد می‌نویسد", "true", ent.str("multiDevice"))
      //  ۳) رازِ دستگاهِ شاگرد (`auth.staff_session_guard`) عمرِ زمانی ندارد
      assertEquals("روزِ $d رازِ دستگاهِ شاگرد", "true", c.str("staffKeyLive"))
      //  ۴) مجوزِ اشتراکِ دستگاه: پرچمِ خاموش ⇒ روزِ ۱۱ به بعد EXPIRED (VipGate «فروش» و «قرض‌داران» را می‌بندد)؛ با پرچم ⇒ ACTIVE
      assertEquals("روزِ $d مجوزِ ده‌روزه", License.State.EXPIRED, License.status(lic10, publicKey, A.deviceId, now, A.userId).state)
      assertEquals("روزِ $d مجوزِ کامپیوتر", License.State.ACTIVE, License.status(licA, publicKey, A.deviceId, now, A.userId).state)
      assertEquals("روزِ $d مجوزِ گوشی", License.State.ACTIVE, License.status(licB, publicKey, B.deviceId, now, B.userId).state)
      //  ۵) کدِ آفلاینِ ۳۰ روزه: درست تا پایانِ همان ۳۰ روزِ خریده‌شده
      val oc = OfflineCode.verify(offline, publicKey, fingerprint, A.userId, now)
      val ends = OfflineCode.parse(offline)!!.endsAt
      assertEquals("روزِ $d کدِ آفلاین", now < ends, oc is OfflineCode.Verdict.Ok)
      //  ۶) برگهٔ تأیید روی سرور هم با زمانِ op (`staff-approval` ⇐ `covers(at = op.ts)`)
      val srv = be.call("POST", "/approval-covers", obj("slip" to slipJson(slip), "need" to buildJsonObject { put("perm", need.perm); put("doc", need.doc); put("pct_bp", need.pctBp); put("at", need.at) }))
      assertEquals("روزِ $d برگه روی سرور", "null", srv["verdict"].toString())
      //  ۷) برگه، فاکتورِ نگه‌داشته، پشتیبان و نسخه‌های خودکارِ خودِ دستگاه
      val local = localRules(A, clock, d, shopId, firstHeld, heldA, snapDir, ledgerFile)
      expiry += "روزِ $d: access=false refresh=true sub=${ent.str("source")}/${ent.str("phase")} staffKey=true lic10=EXPIRED lic35=ACTIVE " +
        "offlineCode=${if (oc is OfflineCode.Verdict.Ok) "ok" else (oc as OfflineCode.Verdict.Bad).reason} $local"
    }

    /* ── روزِ ۳۱: هنوز می‌فروشد (مجوز ACTIVE)، بعد اینترنت برمی‌گردد ── */
    clock.at(days + 1, 8 * 60)
    A.boot(); B.boot()
    A.sell(rnd, clock, tallyA, true, debtors)
    A.resume(firstHeld, clock, heldA, tallyA)
    B.sell(rnd, clock, tallyB, false, debtors)
    val queuedA = A.queue.size()
    val queuedB = B.queue.size()
    val offlineOpsA = A.made.size - day0Ops.getValue(A)
    val offlineOpsB = B.made.size - day0Ops.getValue(B)
    assertEquals("صفِ کامپیوتر همهٔ کارِ قطعی", offlineOpsA, queuedA)
    assertEquals("صفِ گوشی همهٔ کارِ قطعی", offlineOpsB, queuedB)

    val offlineMs = System.currentTimeMillis() - t0
    println("SYN-01 قطعی: $days روز، کامپیوتر ${tallyA.kinds} ناموفق ${tallyA.failed} ${tallyA.failures.entries.take(4)}؛ گوشی ${tallyB.kinds} " +
      "ناموفق ${tallyB.failed}؛ صف ${queuedA}+${queuedB}؛ ذخیرهٔ کامپیوتر ${A.saves} بار، میانگین ${A.saveMs / maxOf(1, A.saves)}ms، کندترین ${A.slowestSaveMs}ms؛ " +
      "گوشی ${B.saves} بار، میانگین ${B.saveMs / maxOf(1, B.saves)}ms؛ ${offlineMs / 1000}s")

    be.serverDay(days + 1.0 + 9 / 24.0)
    //  کامپیوتر: دسترسی مرده ⇒ ۴۰۱ ⇒ تازه‌سازی ⇒ همان push؛ پاسخِ دستهٔ [cutA] ام در راه گم می‌شود (سرور گرفت، دستگاه نفهمید)
    //  و شبکه همان دم دوباره می‌رود. شمارِ پراکسی: push ِ ۴۰۱ هم یکی است ⇒ دسته‌های ۱..cutA−۱ می‌رسند، پاسخِ دستهٔ cutA گم می‌شود
    val cutA = if (queuedA > OpQueue.MAX_OPS * 4) 3 else 1
    be.net(true, cutPushAfter = cutA, cutKind = "response", thenDown = true)
    A.online = true
    val savesBefore = A.tokens.saves
    val first = A.sync()
    assertTrue("بریدنِ پاسخ در میانه باید دیده شود", first.isFailure)
    assertEquals("پراکسی پاسخ را برید", "1", be.call("GET", "/stats").obj("stats").str("cutResponse"))
    assertTrue("نشست با تازه‌سازی ماند", A.tokens.saves > savesBefore && A.tokens.clears == 0)
    val left = A.queue.size()
    assertTrue("صف نیمه‌رفته: $left از $queuedA", left in 1..queuedA && (cutA == 1 || left < queuedA))
    be.net(true)
    assertTrue("دوباره، از همان جا", A.sync().isSuccess)
    assertEquals(0, A.queue.size())
    assertTrue("دستهٔ گم‌شده «تکراری» برگشت، نه دوباره اعمال", A.duplicates > 0)

    //  گوشیِ شاگرد: تازه‌سازی هم منقضی ⇒ نشست می‌رود، صف **همه‌اش** می‌ماند ⇒ ورودِ دوباره با کد + رازِ دستگاه ⇒ همه می‌رود
    be.call("POST", "/expire-refresh", obj("userId" to B.userId))
    be.net(true)
    B.online = true
    val lost = B.sync()
    assertTrue(lost.isFailure)
    assertTrue("نشستِ شاگرد بسته شد: ${lost.exceptionOrNull()}", lost.exceptionOrNull() is ApiFailure.SessionExpired && B.tokens.accessToken == null)
    assertEquals("هیچ op ای با از دست رفتنِ نشست گم نشد", queuedB, B.queue.size())
    val again = B.api.postPublic("/auth/staff", buildJsonObject {
      put("code", setup.str("staffCode")); put("name", "علی"); put("app", "shop")
      put("device", buildJsonObject { put("uid", B.deviceId); put("name", B.label); put("platform", "android"); put("keyVersion", 1); put("key", s.str("deviceKey")) })
    })
    B.tokens.save(ApiJson.text(again, "access_token").ifBlank { ApiJson.text(again, "accessToken") }, ApiJson.text(again, "refresh_token").ifBlank { ApiJson.text(again, "refreshToken") }, 0)
    assertEquals("همان حساب، نه عضوِ تازه", B.userId, ApiJson.text(again["user"] as JsonObject, "id"))
    assertEquals(queuedB, B.queue.size())
    //  اشتراک در همان روزها تمام شد ⇒ سرور نوشتنِ شاگرد را نمی‌پذیرد (D-05: صاحب می‌نویسد، شاگرد نه) — صف **همه‌اش** می‌ماند
    be.call("POST", "/subscription", obj("endsInDays" to -1, "graceDays" to 0))
    val ended = B.sync()
    assertTrue("اشتراکِ تمام‌شده: ${ended.exceptionOrNull()}", ended.isFailure && (ended.exceptionOrNull() as? ApiFailure)?.code == "subscription_required")
    assertEquals("هیچ op ای با پایانِ اشتراک گم نشد", queuedB, B.queue.size())
    assertEquals(0, B.refused)
    //  تمدید ⇒ همان صف می‌رود؛ نخستین دسته پیش از رسیدن به سرور بریده می‌شود (شبکه در میانه قطع)
    be.call("POST", "/subscription", obj("endsInDays" to 60, "graceDays" to 3))
    be.net(true, cutPushAfter = 0, cutKind = "request", thenDown = true)
    assertTrue("بریدنِ درخواست در میانه", B.sync().isFailure)
    assertEquals("پراکسی درخواست را برید", "1", be.call("GET", "/stats").obj("stats").str("cutRequest"))
    assertEquals(queuedB, B.queue.size())
    be.net(true)
    assertTrue(B.sync().isSuccess)
    assertEquals(0, B.queue.size())
    //  هر دو یک دورِ دیگر تا هر کدام کارِ دیگری را بگیرد
    assertTrue(A.sync().isSuccess); assertTrue(B.sync().isSuccess); assertTrue(A.sync().isSuccess)

    /* ── سنجش ── */
    val log = be.call("GET", "/oplog")
    val ids = (log["ids"] as kotlinx.serialization.json.JsonArray).map { (it as JsonPrimitive).content }.toHashSet()
    val byDevice = log.obj("byDevice")
    assertEquals("هیچ op_id ِ تکراری", "[]", log["duplicates"].toString())
    assertEquals(log.str("total"), log.str("distinct"))
    assertEquals("هر op ِ کامپیوتر یک بار", A.made.size, byDevice.str(A.deviceId).toInt())
    assertEquals("هر op ِ گوشی یک بار", B.made.size, byDevice.str(B.deviceId).toInt())
    assertTrue("هیچ op ای جا نماند", ids.containsAll(A.made) && ids.containsAll(B.made))
    assertEquals("هیچ op ای رد نشد: ${A.queue.dropped().take(3)} ${B.queue.dropped().take(3)}", 0, A.refused + B.refused)
    assertTrue(A.queue.dropped().isEmpty() && B.queue.dropped().isEmpty())

    //  دو دستگاه همان دفتر (تفاضلِ واقعیِ `LedgerDiff` = هیچ)، همان Snapshot ِ سرور
    assertEquals("کامپیوتر و گوشی یکسان", emptyList<String>(), LedgerDiff.diff(LedgerDiff.shadowOf(A.ledger), LedgerDiff.toJson(B.ledger), 0).map { "${it.table}/${it.rowId}" }.take(10))
    val snap = A.api.get("/sync/v1/snapshot")
    val server = LedgerDiff.fromSnapshot(snap, LedgerDiff.toJson(A.ledger))
    assertEquals("سرور همان دفتر", emptyList<String>(), LedgerDiff.diff(LedgerDiff.shadowOf(server), LedgerDiff.toJson(A.ledger), 0).map { "${it.table}/${it.rowId}" }.take(10))
    for (p in A.ledger.products) assertEquals("موجودیِ ${p.name}", ShopStore.stock(A.ledger, p.id), ShopStore.stock(B.ledger, p.id), 1e-9)
    for (id in debtors) assertEquals("قرضِ $id", ShopStore.debt(A.ledger, id), ShopStore.debt(B.ledger, id), 1e-9)
    val price = A.ledger.products.first { it.id == shared }.salePrice
    assertEquals("قیمتِ کالای مشترک یکی", price, B.ledger.products.first { it.id == shared }.salePrice, 0.0)
    val shifts = CashShift.list(A.ledger)
    assertEquals("هر روز دو صندوق باز و بسته", days * 2, shifts.size)
    assertTrue(shifts.none { it.isOpen })
    val made = (tallyA.kinds.filterKeys { it.startsWith("sale") || it == "held.resume.sale" }.values.sum() +
      tallyB.kinds.filterKeys { it.startsWith("sale") || it == "held.resume.sale" }.values.sum())
    assertEquals("همهٔ فاکتورهای دو دستگاه", made, A.ledger.sales.size)
    val numbers = A.ledger.sales.map { "${it.series}#${it.invoiceNumber}" }
    assertEquals("شمارهٔ فاکتور تکراری نیست", numbers.size, numbers.toSet().size)
    assertTrue("دسته‌ها ≤ ${OpQueue.MAX_OPS} op و ≤ ${OpQueue.MAX_BYTES} بایت", A.maxBatch <= OpQueue.MAX_OPS && B.maxBatch <= OpQueue.MAX_OPS && A.maxBatchBytes <= OpQueue.MAX_BYTES)
    if (days >= 30 && ownerPerDay >= 70) assertTrue("پیامِ صفِ بزرگ دیده شد", crossed > 0)

    val total = System.currentTimeMillis() - t0
    val stats = be.call("GET", "/stats")
    println(
      """
      |── SYN-01: $days روز بی اینترنت (${be.db}) ──
      |کامپیوترِ صاحب: ${tallyA.kinds} — ناموفقِ موتور ${tallyA.failed} ${tallyA.failures.entries.take(4)}
      |گوشیِ شاگرد:    ${tallyB.kinds} — ناموفقِ موتور ${tallyB.failed} ${tallyB.failures.entries.take(4)}
      |op: کامپیوتر ${A.made.size} (قطعی $offlineOpsA)، گوشی ${B.made.size} (قطعی $offlineOpsB)؛ سرور ${log.str("total")} یکتا ${log.str("distinct")}، تکراری ۰، ردشده ${A.refused + B.refused}
      |push: کامپیوتر ${A.pushes} (بیشترین دسته ${A.maxBatch} op، ${A.maxBatchBytes} بایت، تکراریِ پس از بریدن ${A.duplicates})، گوشی ${B.pushes} (تکراری ${B.duplicates})؛ پراکسی $stats
      |صف در روزها: ${queueDaily.filter { it.first % 5 == 0 || it.first == days }}؛ پیامِ صف: ${backlog.ifEmpty { listOf("زیرِ آستانه") }}
      |ذخیره: کامپیوتر ${A.saves} بار، میانگین ${A.saveMs / maxOf(1, A.saves)}ms، کندترین ${A.slowestSaveMs}ms؛ گوشی ${B.saves} بار، میانگین ${B.saveMs / maxOf(1, B.saves)}ms
      |دفتر: ${A.ledger.sales.size} فاکتور، ${A.ledger.saleReturns.size} برگشت، ${A.ledger.stockMovements.size} حرکتِ انبار، ${A.ledger.transactions.size} ردیفِ قرض، ${shifts.size} صندوق
      |${expiry.joinToString("\n|")}
      |زمان: قطعی ${offlineMs / 1000}s، کل ${total / 1000}s
      """.trimMargin()
    )
  }

  /* ════════════════════════ بی سرور (همیشه، CI ِ کامپیوتر) ════════════════════════ */

  @Test fun `بی سرور ۳۰ روز بی اینترنت ⇒ صف همه را نگه می‌دارد و هیچ مهلتی کار را نمی‌بندد`() = runBlocking {
    work.mkdirs()
    val rnd = Random(31)
    val clock = ShopClock(System.currentTimeMillis())
    val n = (env("OFFLINE30_SOLO")?.toIntOrNull() ?: 25)
    val A = ShopDevice("solo-pc", "solo-pc-${clock.start.toString(36)}", "usr_solo", "صاحب", "http://127.0.0.1:9", MemTokens("", ""), File(work, "S"), "1.0.9999")
    A.online = false
    A.seatEmpty()
    clock.at(0, 6 * 60)
    seed(A, clock)
    BackupClock.mark(A.ctx)
    val disk = Held(File(work, "S/held.json"))
    val t = Tally()
    val firstHeld = A.hold(rnd, clock, disk, t)!!
    val held = mutableListOf(firstHeld)
    val snapDir = File(work, "S/files/snapshots").apply { mkdirs() }
    val ledgerFile = File(work, "S/files/shop-data.json")
    val debtors = A.ledger.debtors.map { it.id }
    val shared = A.ledger.products.first().id
    for (d in 1..days) {
      A.boot()
      assertEquals("روزِ $d: صف پس از باز شدنِ دوباره همهٔ op ها را دارد", A.made.size, A.queue.size())
      A.day(rnd, clock, n, true, debtors, shared, d, disk, t, held)
      nightly(A, clock, snapDir, ledgerFile)
      assertEquals(A.queue.size() >= OpBacklog.WARN_AT, OpBacklog.warning(A.queue.size()).isNotEmpty())
    }
    val rules = listOf(29, 30, 31).map { localRules(A, clock, it, "shp_solo", firstHeld, disk, snapDir, ledgerFile) }
    //  روزِ ۳۱ هنوز می‌فروشد و نگه‌داشتهٔ روزِ یک فروخته می‌شود
    clock.at(days + 1, 8 * 60)
    A.boot()
    val sales = A.ledger.sales.size
    A.sell(rnd, clock, t, true, debtors)
    A.resume(firstHeld, clock, disk, t)
    assertEquals(sales + 2, A.ledger.sales.size)
    assertTrue(A.ledger.sales.any { it.id == firstHeld })
    A.boot()
    assertEquals("هیچ op ای گم نشد", A.made.size, A.queue.size())
    assertEquals(A.made, A.queue.all().mapTo(LinkedHashSet()) { it.opId })
    assertEquals(0, A.pushes)
    assertTrue(A.queue.dropped().isEmpty())
    assertEquals("هر روز یک صندوق باز و بسته", days, CashShift.list(A.ledger).count { !it.isOpen })
    println("بی سرور: $days روز، ${A.made.size} op در صف، ${A.ledger.sales.size} فاکتور، ${t.kinds}، ناموفق ${t.failed} ${t.failures.entries.take(3)}؛ ${rules.joinToString(" | ")}")
  }

  private fun slipJson(s: ApprovalSlip.Slip): JsonObject = buildJsonObject {
    put("v", s.v); put("shop", s.shop); put("doc", s.doc); put("perm", s.perm); put("pct_bp", s.pctBp); put("amount", s.amount)
    put("by", s.by); put("key", s.key); put("iat", s.iat); put("exp", s.exp); put("nonce", s.nonce); put("reason", s.reason); put("sig", "x")
  }
}
