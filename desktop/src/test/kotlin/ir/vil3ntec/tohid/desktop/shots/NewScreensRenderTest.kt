package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.crm.CustomerAccess
import ir.vil3ntec.tohid.core.model.MemberDto
import ir.vil3ntec.tohid.core.model.StaffKeyDto
import ir.vil3ntec.tohid.core.model.StaffKeysDto
import ir.vil3ntec.tohid.core.model.StaffRejoinHintDto
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.HttpEngine
import ir.vil3ntec.tohid.core.net.TestHttpServer
import ir.vil3ntec.tohid.core.net.TokenStorage
import ir.vil3ntec.tohid.core.report.ReportBoard
import ir.vil3ntec.tohid.core.sync.ConflictInbox
import ir.vil3ntec.tohid.data.CustomerAccessLog
import ir.vil3ntec.tohid.data.PoBillDto
import ir.vil3ntec.tohid.data.PoBillLineDto
import ir.vil3ntec.tohid.data.PoConfigDto
import ir.vil3ntec.tohid.data.PoLineDto
import ir.vil3ntec.tohid.data.PoOrderDto
import ir.vil3ntec.tohid.data.PoPageDto
import ir.vil3ntec.tohid.data.PoPermsDto
import ir.vil3ntec.tohid.data.PoProgressDto
import ir.vil3ntec.tohid.data.PoReceiptDto
import ir.vil3ntec.tohid.data.PoTotalsDto
import ir.vil3ntec.tohid.data.PurchaseOrderDto
import ir.vil3ntec.tohid.data.PurchaseUnit
import ir.vil3ntec.tohid.data.SalePromo
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.SpecialPrice
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.ui.screens.CustomerAccessPanel
import ir.vil3ntec.tohid.ui.screens.CustomerPricesDialog
import ir.vil3ntec.tohid.ui.screens.DeskFreshReports
import ir.vil3ntec.tohid.ui.screens.DeskPurchasingScreen
import ir.vil3ntec.tohid.ui.screens.ReportFresh
import ir.vil3ntec.tohid.ui.v3.account.team.MemberActions
import ir.vil3ntec.tohid.ui.v3.account.team.MemberContent
import ir.vil3ntec.tohid.ui.v3.account.team.TeamData
import ir.vil3ntec.tohid.ui.v3.shell.ConflictInboxLive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.AfterClass
import org.junit.Assume
import org.junit.BeforeClass
import org.junit.Test

/**
 *  گزارشِ کشیدنِ صفحه‌های تازهٔ دستهٔ ۳۱ (`ScreenRender`) — فقط گزارش، هرگز سد: صندوقِ سندهای ردشده، دستگاه‌های کارمند و
 *  «شاید همان …»، «چه کسی این پرونده را دید»، سفارش‌های خرید (فهرست، فرم، دریافت، تطبیق) با سرورِ ساختگیِ 127.0.0.1،
 *  واحدهای خرید در فرمِ کالا و فاکتورِ خرید، قیمتِ خاصِ مشتری و زبانه‌های «بهای میانگین» و «اثرِ تخفیف».
 *  هر صفحه در گوشیِ ۳۶۰dp و کامپیوترِ ۱۲۸۰dp (صفحه‌های فقط-کامپیوتر فقط ۱۲۸۰dp)، فارسی (راست‌به‌چپ) و انگلیسی.
 *  دادهٔ ثابت (`Demo`، `PoDemo`): بی تصادف، بی شبکهٔ بیرونی؛ زمان‌ها نسبت به اکنون تا «… پیش» در هر اجرا یکی بماند.
 *  یافته‌ها و شکستِ کشیدن در `render-report.json` می‌روند، نه در نتیجهٔ آزمون. کنار رفتن: [RenderReport.skipReason].
 */
class NewScreensRenderTest {

  companion object {
    private const val PHONE = 360
    private const val DESK = 1280

    private lateinit var server: TestHttpServer
    private var ready = false
    private var wiringBefore: Any? = null
    private var toolsBefore = false
    private val json = Json { encodeDefaults = true }
    private val all = ArrayList<ScreenRender.Result>()

    private val wiringField by lazy { Backend::class.java.getDeclaredField("wiring").apply { isAccessible = true } }

    /** نشستِ باز در حافظه — همان قراردادِ `TokenStorage` */
    private class Session : TokenStorage {
      override var accessToken: String? = "render-session"
      override var refreshToken: String? = null
      override var accessExpiresAt: Long = Long.MAX_VALUE / 2
      override var staffDeviceKey: String? = null
      override fun save(access: String?, refresh: String?, expiresAt: Long) { accessToken = access }
      override fun clear() { accessToken = null }
    }

    @BeforeClass @JvmStatic fun up() {
      RenderReport.skipReason()?.let { why ->
        println("RENDER کنار رفت: $why")
        RenderReport.write(emptyList(), why)
        Assume.assumeTrue(why, false)
      }
      server = TestHttpServer()
      PoDemo.serve(server, json)
      //  `Backend.api(context)` ِ صفحه‌های سفارشِ خرید ⇐ همین سرورِ ساختگی (فقط در این آزمون؛ پس از آن برمی‌گردد)
      wiringBefore = wiringField.get(null)
      val cls = Class.forName("ir.vil3ntec.tohid.data.repo.Backend\$Wiring")
      val wiring = cls.getDeclaredConstructor(android.content.Context::class.java).apply { isAccessible = true }.newInstance(DesktopContext)
      cls.getDeclaredField("api").apply { isAccessible = true }
        .set(wiring, ApiClient(HttpEngine(baseUrl = { server.baseUrl }, allowInsecure = true), Session()))
      wiringField.set(null, wiring)
      toolsBefore = ShopPolicy.productTools(DesktopContext)
      ShopPolicy.setProductTools(DesktopContext, true)
      ready = true
    }

    @AfterClass @JvmStatic fun down() {
      if (!ready) return
      wiringField.set(null, wiringBefore)
      ShopPolicy.setProductTools(DesktopContext, toolsBefore)
      ConflictInboxLive.rows.value = emptyList(); ConflictInboxLive.on.value = false; ConflictInboxLive.open.value = 0
      server.close()
      RenderReport.write(synchronized(all) { all.toList() }, null)
      val d = all.flatMap { it.findings }.filter { it.kind in RenderReport.DEFECTS }.size
      println("RENDER ${all.size} حالت، $d یافتهٔ عیب ⇒ render-report.json")
    }

    private fun storeOf(d: ShopData): ShopStore = ShopStore(DesktopContext).also { s ->
      //  دفترِ نمایشی فقط در حافظه — هیچ نوشتنی روی پوشهٔ دادهٔ آزمون
      @Suppress("UNCHECKED_CAST")
      (ShopStore::class.java.getDeclaredField("_data").apply { isAccessible = true }.get(s) as MutableStateFlow<ShopData>).value = d
    }
  }

  /** یک حالت؛ شکستِ کشیدن یافتهٔ `error` است، نه شکستِ آزمون */
  private fun scene(name: String, widthDp: Int, en: Boolean, frames: Int, between: () -> Unit, content: @Composable () -> Unit) {
    val id = "$name-$widthDp-${if (en) "en" else "fa"}"
    val r = runCatching {
      if (widthDp < 600) ScreenRender.render(id, widthDp, 760, en, frames = frames, between = between, content = content)
      else ScreenRender.render(id, widthDp, 800, en, density = 1f, frames = frames, between = between, content = content)
    }.getOrElse { e -> ScreenRender.Result(id, widthDp, en, listOf(ScreenRender.Finding("error", e.toString().take(400), "")), emptyList()) }
    r.findings.forEach { println("RENDER $id: $it") }
    synchronized(all) { all += r }
  }

  /** گوشیِ ۳۶۰dp و کامپیوترِ ۱۲۸۰dp، هر کدام فارسی و انگلیسی */
  private fun both(name: String, frames: Int = 10, between: () -> Unit = {}, content: @Composable () -> Unit) {
    for (w in listOf(PHONE, DESK)) for (en in listOf(false, true)) scene(name, w, en, frames, between, content)
  }

  /** فقط کامپیوترِ ۱۲۸۰dp (صفحه‌های کامپیوتری)، فارسی و انگلیسی */
  private fun desk(name: String, frames: Int = 8, between: () -> Unit = {}, content: @Composable () -> Unit) {
    for (en in listOf(false, true)) scene(name, DESK, en, frames, between, content)
  }

  private fun route(path: String, store: ShopStore) = @Composable { V3RoutePreview(path, store, dark = false) }

  @Test fun `گزارشِ صندوقِ سندهای ردشده`() {
    val store = storeOf(Demo.data())
    both("conflicts", between = {
      ConflictInboxLive.rows.value = Demo.conflicts(); ConflictInboxLive.on.value = true; ConflictInboxLive.open.value = 2
      ConflictInboxLive.failed.value = false
    }, content = route("conflicts", store))
  }

  @Test fun `گزارشِ دستگاه‌های کارمند و دیدنِ پروندهٔ مشتری`() {
    val d = Demo.data()
    both("member-devices") {
      val team = remember { Demo.team() }
      MemberContent(team, team.member("m3"), canEdit = true, busy = false, actions = MemberActions({}, { _, _ -> }, {}, {}, {}))
    }
    both("customer-access") {
      Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) { CustomerAccessPanel(d, MoneyDemo.OWING) }
    }
  }

  @Test fun `گزارشِ سفارش‌های خرید با سرورِ ساختگی`() {
    val store = storeOf(Demo.data())
    both("po-list", frames = 14, content = route("purchasing/orders", store))
    both("po-new", frames = 14, content = route("purchasing/orders/new?supplier=${MoneyDemo.SUPPLIER}", store))
    both("po-receive", frames = 14, content = route("purchasing/orders/po-1/receive", store))
    both("po-match", frames = 14, content = route("purchasing/orders/po-1/match", store))
  }

  @Test fun `گزارشِ واحدهای خرید، قیمتِ خاص و زبانه‌های بهای میانگین و اثرِ تخفیف`() {
    val d = Demo.data()
    val store = storeOf(d)
    both("product-edit-units", content = route("products/p-rice/edit", store))
    desk("desk-purchasing") { DeskPurchasingScreen(store, d, remember { SnackbarHostState() }) }
    desk("customer-prices") {
      CustomerPricesDialog(d, d.debtors.first { it.id == MoneyDemo.OWING }, allowed = true, today = ir.vil3ntec.tohid.todayIso(), onDismiss = {}, onSave = { _, _ -> }, onRemove = {})
    }
    for (tab in listOf("stock-cost", "promo")) {
      desk("reports-$tab") {
        remember { ReportFresh.preset.value = ReportBoard.Preset(tab, "month", "product"); 0 }
        DeskFreshReports(d, remember { SnackbarHostState() })
      }
    }
  }
}

/** دفترِ نمایشی: همان `MoneyDemo` با واحدِ خرید، قیمتِ خاص، دیدنِ پرونده و تخفیفِ خودکار */
internal object Demo {
  private const val HOUR = 3_600_000L
  private val now = System.currentTimeMillis()

  fun data(): ShopData {
    val base = MoneyDemo.full()
    val products = base.products.map { p ->
      if (p.id != "p-rice") p else p.copy(
        purchaseUnits = listOf(
          PurchaseUnit("کارتنِ ۱۲ بسته‌ای", 12_000, supplierId = MoneyDemo.SUPPLIER, barcode = "6261234567890"),
          PurchaseUnit("بوجیِ بزرگ", 40_000),
        ),
      )
    }
    val debtors = base.debtors.map { db ->
      if (db.id != MoneyDemo.OWING) db else db.copy(
        specialPrices = listOf(
          SpecialPrice("p-rice", 99_000, from = ir.vil3ntec.tohid.todayIso(), to = null),
          SpecialPrice("p-oil", 33_500),
        ),
      )
    }
    val items = base.saleItems.mapIndexed { i, si ->
      if (i != 0) si else si.copy(promo = listOf(SalePromo(id = "pr-1", rev = 1, name = "تخفیفِ عیدِ برنجِ پنج کیلویی", discountMinor = 10_000)))
    }
    var d = base.copy(products = products, debtors = debtors, saleItems = items)
    val viewers = listOf(
      CustomerAccessLog.Viewer("u1", "کریم احمدی", "owner", "Desktop"),
      CustomerAccessLog.Viewer("u3", "سمیع الله نورزاده", "staff", "Galaxy A52"),
    )
    var n = 0
    for ((i, v) in viewers.withIndex()) for (k in listOf(CustomerAccess.Kind.DETAIL, CustomerAccess.Kind.PHONE)) {
      CustomerAccessLog.record(d, MoneyDemo.OWING, k, v, ir.vil3ntec.tohid.todayIso(), now - (i * 5 + n) * HOUR, newId = { "acc-${++n}" })?.let { d = it }
    }
    CustomerAccessLog.record(d, CustomerAccess.ALL, CustomerAccess.Kind.EXPORT, viewers[0], ir.vil3ntec.tohid.todayIso(), now - HOUR / 2, newId = { "acc-x" })?.let { d = it }
    return d
  }

  private fun item(id: String, open: Boolean, n: Long, reason: String) = ConflictInbox.Item(
    id = id, kind = ConflictInbox.TX_REJECTED, reason = reason, perm = "", status = if (open) ConflictInbox.OPEN else ConflictInbox.DISMISSED,
    at = now - n * HOUR, docTable = "sales", docRowId = "sale-$n", saleId = "sale-$n", number = 1200 + n, path = "",
    staff = ConflictInbox.Who("u3", "سمیع الله نورزاده", "staff"), deviceId = "dev-7f3a9c21b8e4", deviceName = "Galaxy A52 (پیشخوانِ دوم)",
    amountMinor = 1_284_500_00L / (n + 1), itemCount = 4,
    items = listOf(
      ConflictInbox.Line("برنج سیلا ۵ کیلویی", qtyMilli = 2_000), ConflictInbox.Line("روغن آفتاب‌گردان", qtyMilli = 12_000),
      ConflictInbox.Line("چای سبز", qtyMilli = 500), ConflictInbox.Line("شکرِ سفید", qtyMilli = 3_000),
    ),
    resubmittable = true, fixable = true, busy = false, lastError = if (n == 2L) "stock_negative" else null,
    resolvedAt = if (open) null else now, resolvedBy = if (open) null else ConflictInbox.Who("u1", "کریم احمدی", "owner"),
    note = if (open) "" else "تکراری بود", fixedDoc = "",
  )

  fun conflicts(): List<ConflictInbox.Item> = listOf(item("c1", true, 1, "credit_not_allowed"), item("c2", true, 2, "approval_required"))

  fun team(): TeamData = TeamData().apply {
    loaded = true
    members = listOf(
      MemberDto(id = "m1", userId = "u1", name = "کریم احمدی", phone = "0700123456", role = "owner"),
      MemberDto(id = "m2", userId = "u2", name = "سمیع الله نورزادهٔ قدیم", phone = "0799111222", role = "staff"),
      MemberDto(id = "m3", userId = "u3", name = "سمیع الله نورزاده", phone = "0799111333", role = "staff"),
    )
    maxMembers = 5
    staffKeys = StaffKeysDto(
      devices = listOf(
        StaffKeyDto(id = "k1", userId = "u3", memberId = "m3", label = "Samsung Galaxy A52 (پیشخوانِ دوم)", platform = "android", device = "dev-7f3a9c21b8e4", createdAt = now - 40 * 24 * HOUR, lastUsedAt = now - HOUR),
        StaffKeyDto(id = "k2", userId = "u3", memberId = "m3", label = "Windows Desktop", platform = "desktop", device = "dev-0011", createdAt = now - 3 * 24 * HOUR),
      ),
      hints = listOf(StaffRejoinHintDto(memberId = "m3", userId = "u3", oldMemberId = "m2", oldUserId = "u2", oldName = "سمیع الله نورزادهٔ قدیم", sameName = false, device = "dev-7f3a9c21b8e4")),
      canRevoke = true,
    )
  }
}

/** سرورِ ساختگیِ سفارش‌های خرید: یک سفارشِ نیمه‌رسیده با رسید و فاکتور، یک پیش‌نویس، یک کامل */
internal object PoDemo {
  private val now = System.currentTimeMillis()

  private val lines = listOf(
    PoLineDto("l1", "p-rice", "برنج سیلا ۵ کیلویی", "کارتنِ ۱۲ بسته‌ای", 10_000, 1_140_000, factorMilli = 12_000),
    PoLineDto("l2", "p-oil", "روغن آفتاب‌گردان", "بوتل", 48_000, 31_000),
    PoLineDto("l3", "p-tea", "چای سبز", "کیلو", 25_500, 42_000),
  )

  private val partial = PurchaseOrderDto(
    id = "po-1", no = 1042, supplierId = MoneyDemo.SUPPLIER, supplierName = "شرکتِ تجارتیِ البرز و برادران", state = "sent", status = "partial",
    allowed = listOf("receive", "cancel"), lines = lines,
    progress = listOf(
      PoProgressDto("l1", "p-rice", 10_000, 6_000, 1_000, 5_000, 5_000),
      PoProgressDto("l2", "p-oil", 48_000, 48_000, 0, 48_000, 0),
      PoProgressDto("l3", "p-tea", 25_500, 0, 0, 0, 25_500),
    ),
    totals = PoTotalsDto(1_140_000L * 10 + 31_000L * 48 + 42_000L * 25 + 21_000, 5_700_000 + 1_488_000, 6_762_000),
    expectedDate = "2026-11-20", note = "تحویل پیش از ساعت ۱۰ صبح", version = 3, createdAt = now - 6 * 86_400_000L, sentAt = now - 5 * 86_400_000L,
    receiptCount = 1, firstReceiptAt = now - 2 * 86_400_000L,
    receipts = listOf(PoReceiptDto(id = "r1", poId = "po-1", totalMinor = 7_188_000, freightMinor = 150_000, note = "یک کارتن خیس شده بود", createdBy = "سمیع", createdAt = now - 2 * 86_400_000L)),
    billCount = 1,
    bills = listOf(
      PoBillDto(
        id = "b1", poId = "po-1", billNo = "INV-2026-000873", billDate = "2026-11-06",
        lines = listOf(PoBillLineDto("l1", "p-rice", 6_000, 6_900_000), PoBillLineDto("l2", "p-oil", 48_000, 1_488_000)),
        totalMinor = 8_388_000, createdBy = "کریم", createdAt = now - 86_400_000L,
      ),
    ),
  )

  private val draft = PurchaseOrderDto(
    id = "po-2", no = 1043, supplierId = MoneyDemo.SUPPLIER, supplierName = "شرکتِ تجارتیِ البرز و برادران", state = "draft", status = "draft",
    allowed = listOf("edit", "send", "cancel"), lines = lines.take(2), totals = PoTotalsDto(12_888_000, 0, 12_888_000), version = 1, createdAt = now - 3_600_000L,
  )

  private val done = partial.copy(id = "po-3", no = 1031, status = "complete", allowed = emptyList(), expectedDate = null, totals = PoTotalsDto(4_200_000, 4_200_000, 0))

  fun serve(server: TestHttpServer, json: Json) {
    val root = "/api/v1/shop/purchase-orders"
    server.on("$root/config") { 200 to json.encodeToString(PoConfigDto(enabled = true, permissions = PoPermsDto(manage = true, receive = true))) }
    server.on(root) { 200 to json.encodeToString(PoPageDto(listOf(partial, draft, done))) }
    for (o in listOf(partial, draft, done)) server.on("$root/${o.id}") { 200 to json.encodeToString(PoOrderDto(o)) }
  }
}
