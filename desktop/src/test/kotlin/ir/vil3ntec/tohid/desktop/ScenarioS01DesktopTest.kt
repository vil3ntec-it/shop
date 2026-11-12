package ir.vil3ntec.tohid.desktop

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.pay.CashKeys
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.desktop.perf.PosSpeed
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.print.PrintSpooler
import ir.vil3ntec.tohid.print.PrinterRegistry
import ir.vil3ntec.tohid.print.ThermalPrinter
import ir.vil3ntec.tohid.sync.v1.HeartbeatCache
import ir.vil3ntec.tohid.sync.v1.SoftLock
import ir.vil3ntec.tohid.ui.components.pos.PosCatalog
import ir.vil3ntec.tohid.ui.screens.DeskSaleScreen
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Locale
import kotlin.concurrent.thread

/**
 *  S01 ⌨ (§۷.۱) «فروش نقدی سریع» سرتاسر در **پنجرهٔ واقعیِ صندوقِ کامپیوتر** (`DeskSaleScreen` با `ShopStore` و `CartStore` ِ
 *  واقعی روی دیسکِ آزمون، پشتِ `ShopPolicy.payV2` ِ روشن) و چاپگرِ ۸۰ میلی‌متریِ ساختگی روی شبکه (`net:127.0.0.1:…`، همان
 *  راهِ ESC/POS ِ خامِ ۹۱۰۰): اسکنِ A؛ «3*» و اسکنِ B؛ «نوشا» و Enter روی پیشنهادِ اول؛ F9؛ ۱۰۰۰ و Enter.
 *  نتیجه: سه ردیف (B × ۳)، پولِ پس در پنل و پیامِ ثبت، فاکتور در دفترِ روی دیسک، بایت‌های رسید به چاپگر، فوکوس در کادرِ اسکن.
 *  کاتالوگ ۲۰ هزار کالا (`PosSpeed.shop`)؛ زمانِ «اسکن تا ردیف» در همین پنجره با سقفِ CI ِ POS-42 (پنج برابرِ بودجه).
 *  ⛔ سنجهٔ واقعیِ p95 ≤ ۱۰۰ms روی «کامپیوترِ مرجع» با ۳۰۰ هزار ردیفِ فروش، اسکنرِ USB و چاپگرِ واقعی فقط روی دستگاه.
 */
@OptIn(ExperimentalTestApi::class)
class ScenarioS01DesktopTest {

  @get:Rule val rule = createComposeRule()

  private val ctx = DesktopContext
  private val draft get() = File(ctx.filesDir, CartStore.FILE)
  private val roles get() = ctx.getSharedPreferences("tohid-ledger", 0)
  private var roleWas: String? = null
  private var autoWas = false
  private var printer: PrinterRegistry.Saved? = null
  private lateinit var socket: ServerSocket
  private val received = ByteArrayOutputStream()
  @Volatile private var connections = 0

  private fun clean() {
    CartStore.awaitWrites()
    draft.delete()
    HeartbeatCache(ctx).forget()
    SoftLock.refresh(ctx)
    ctx.filesDir.listFiles().orEmpty().filter { it.name.startsWith("shop-data.json") }.forEach { it.deleteRecursively() }
  }

  @Before fun before() {
    clean()
    I18n.source = { Lang.FA }
    roleWas = roles.getString("shop_role", null)
    ShopRole.remember(ctx, ShopRole.OWNER)
    ShopPolicy.setPayV2(ctx, true)
    autoWas = PrintSpooler.autoPrint(ctx)
    PrintSpooler.setAutoPrint(ctx, true)
    //  چاپگرِ ساختگی: هر اتصال تا پایان خوانده می‌شود
    socket = ServerSocket(0, 4, InetAddress.getLoopbackAddress())
    thread(isDaemon = true, name = "s01-fake-printer") {
      while (!socket.isClosed) {
        val c = runCatching { socket.accept() }.getOrNull() ?: break
        c.use { val bytes = it.getInputStream().readBytes(); synchronized(received) { received.write(bytes) }; connections++ }
      }
    }
    printer = PrinterRegistry.add(ctx, "S01", "net:127.0.0.1:${socket.localPort}", ThermalPrinter.WIDTH_80MM)
    PrinterRegistry.makeDefault(ctx, printer!!)
  }

  @After fun after() {
    printer?.let { PrinterRegistry.remove(ctx, it.id) }
    runCatching { socket.close() }
    PrintSpooler.setAutoPrint(ctx, autoWas)
    ShopPolicy.setPayV2(ctx, false)
    roles.edit().apply { if (roleWas == null) remove("shop_role") else putString("shop_role", roleWas) }.commit()
    ShopRole.policyVersion.value++
    clean()
  }

  private val a = Product(id = "s01-a", name = "شیر پاکتی آزمون", unit = "عدد", salePrice = 45.0, barcodes = listOf("6260000100017"))
  private val b = Product(id = "s01-b", name = "بیسکویت آزمون", unit = "عدد", salePrice = 30.0, barcodes = listOf("6260000100024"))
  private val soda = Product(id = "s01-c", name = "نوشابهٔ آزمون ۳۰۰", unit = "عدد", salePrice = 60.0, barcodes = listOf("6260000100031"))

  /** ۲۰ هزار کالای `PosSpeed` (نامِ «نوشابه» ِ آن‌ها کنار رفته تا پیشنهادِ اولِ «نوشا» یکتا باشد) + سه کالای سناریو با موجودی */
  private fun shop(): Pair<ShopData, List<String>> {
    val s = PosSpeed.shop(products = 20_000, sales = 2_000)
    val ours = listOf(a, b, soda)
    val d = s.d.copy(
      products = s.d.products.map { if (it.name.contains("نوشا")) it.copy(name = it.name.replace("نوشابه", "آب‌میوه")) else it } + ours,
      warehouseEntries = s.d.warehouseEntries + ours.map { WarehouseEntry(id = "w-${it.id}", productId = it.id, units = 100.0, unit = it.unit, date = "2026-11-01") },
    )
    //  بارکدهای لاتینِ کالاهای فعال (۲٪ ِ `PosSpeed` غیرفعال است و صندوق آن را نمی‌فروشد)
    val item = s.itemCodes.toHashSet()
    return d to s.d.products.filter { it.active }.mapNotNull { p -> p.barcodes.firstOrNull { it in item } }
  }

  private class Till(val store: ShopStore, val cart: CartStore, val snackbar: SnackbarHostState)

  private fun open(d: ShopData): Till {
    val store = ShopStore(ctx).also { runBlocking { it.load() } }
    assertTrue(runBlocking { store.save(d) })
    val t = Till(store, CartStore(ctx), SnackbarHostState())
    rule.setContent {
      CompositionLocalProvider(
        LocalContext provides DesktopContext,
        LocalView provides remember { android.view.View(DesktopContext) },
        LocalConfiguration provides Configuration(1280, 800),
      ) {
        TohidTheme(ThemeChoice.LIGHT) {
          val live by t.store.data.collectAsState()
          DeskSaleScreen(store = t.store, cartStore = t.cart, d = live, snackbar = t.snackbar) {}
        }
      }
    }
    rule.waitForIdle()
    return t
  }

  private var scanId = -1

  /**
   *  کادرِ اسکن (نوشتهٔ راهنمای `StrSale.deskSearchHint`)؛ همان گره تا پایان. صندوق‌دار یک بار آن را می‌زند: پنجرهٔ آزمون تا
   *  نخستین زدن فوکوسِ آغازینِ صندوق را نمی‌گیرد (همان `PriceCheckEscUiTest`)؛ بازگشتِ فوکوس پس از فروش همان `refocus()` ِ برنامه است.
   */
  private fun scanBox(): SemanticsNodeInteraction {
    val box = hasSetTextAction() and (hasText(StrSale.deskSearchHint) or hasAnyDescendant(hasText(StrSale.deskSearchHint)))
    rule.waitUntil(10_000) { rule.onAllNodes(box, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    scanId = rule.onAllNodes(box, useUnmergedTree = true).fetchSemanticsNodes().first().id
    val scan = rule.onNode(SemanticsMatcher("کادرِ اسکن") { it.id == scanId })
    scan.performClick()
    rule.waitForIdle()
    scan.assertIsFocused()
    return scan
  }

  private fun shown(text: String) = rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

  private fun SemanticsNodeInteraction.enter() = performKeyInput { pressKey(Key.Enter) }

  @Test fun `S01 — اسکن، 3*، نوشا، F9، ۱۰۰۰ و Enter ⇒ سه ردیف، پولِ پس، رسیدِ چاپ‌شده و فوکوس در کادرِ اسکن`() {
    val (d, _) = shop()
    val t = open(d)
    val scan = scanBox()
    val salesBefore = t.store.data.value.sales.size

    scan.performTextInput(a.barcodes.single()); scan.enter()
    scan.performTextInput("3*" + b.barcodes.single()); scan.enter()
    //  پیشنهادِ اولِ «نوشا» همان جست‌وجوی صندوق است
    assertEquals(soda.id, PosCatalog.matches(t.store.data.value.products, "نوشا", false).first().id)
    scan.performTextInput("نوشا"); scan.enter()
    rule.waitForIdle()
    assertEquals(listOf(a.id to 1.0, b.id to 3.0, soda.id to 1.0), t.cart.lines.value.map { it.productId to it.quantity })
    val total = 45.0 + 3 * 30.0 + 60.0

    scan.performKeyInput { pressKey(Key.F9) }
    rule.waitForIdle()
    //  کادرِ «نقدِ دریافتی» ِ پنل (پنجرهٔ آزمون فوکوسِ آغازینِ پنجرهٔ تازه را تا نخستین زدن نمی‌دهد ⇒ یک زدن، مثلِ کادرِ اسکن)
    val cashBox = hasSetTextAction() and (hasText(StrPos.cashReceived) or hasAnyDescendant(hasText(StrPos.cashReceived)))
    rule.waitUntil(5_000) { rule.onAllNodes(cashBox, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    val cashId = rule.onAllNodes(cashBox, useUnmergedTree = true).fetchSemanticsNodes().single().id
    val cash = rule.onNode(SemanticsMatcher("نقدِ دریافتی") { it.id == cashId })
    cash.performClick()
    cash.performTextInput("1000")
    rule.waitForIdle()
    //  پولِ پس پیش از ثبت در خودِ پنل (ردیفِ «پولِ پس»: «805 ؋»)
    assertTrue(shown("${money(1000 - total)} ؋"))
    Thread.sleep(CashKeys.ENTER_GUARD_MS + 50) // PAY-04: Enter ِ پنل پس از سدِ باز شدن
    cash.enter()

    rule.waitUntil(20_000) { t.store.data.value.sales.size == salesBefore + 1 }
    rule.waitForIdle()
    val sale = t.store.data.value.sales.last()
    assertEquals(total, sale.finalTotal, 0.001)
    val items = t.store.data.value.saleItems.filter { it.saleId == sale.id }.associate { it.productId to it.quantity }
    assertEquals(mapOf(a.id to 1.0, b.id to 3.0, soda.id to 1.0), items)
    //  سبد پس از نوشتنِ دفتر خالی می‌شود (`CartStore.post`)
    val wrote = System.nanoTime()
    rule.waitUntil(60_000) { t.cart.lines.value.isEmpty() }
    println("S01 ledger write (20k items) → cart wiped: %.0f ms".format(Locale.ROOT, (System.nanoTime() - wrote) / 1e6))
    //  پیامِ ثبت با پولِ پس
    rule.waitUntil(30_000) { t.snackbar.currentSnackbarData?.visuals?.message?.contains(StrPos.saleRecorded(money(total))) == true }
    val msg = t.snackbar.currentSnackbarData!!.visuals.message
    assertTrue(msg, msg.contains(StrPos.saleRecorded(money(total))) && msg.contains(StrPos.changeDue(money(1000 - total))))
    //  روی دیسک
    assertTrue(ShopStore(ctx).also { runBlocking { it.load() } }.data.value.sales.any { it.id == sale.id })
    //  رسید به چاپگرِ ۸۰ میلی‌متری رسید (ESC @ … و برشِ GS V)، بی خطا و بی کارِ مانده
    rule.waitUntil(30_000) { connections > 0 && PrintSpooler.jobs.value.none { it.saleId == sale.id } }
    val bytes = synchronized(received) { received.toByteArray() }
    assertTrue("بایت‌های رسید: ${bytes.size}", bytes.size > 1_000)
    assertTrue(bytes.toList().windowed(2).any { it == listOf<Byte>(0x1B, 0x40) })
    assertTrue(bytes.toList().windowed(2).any { it == listOf<Byte>(0x1D, 0x56) })
    //  فوکوس به کادرِ اسکن برگشت
    scan.assertIsFocused()
  }

  @Test fun `S01 — زمانِ اسکن تا ردیف در پنجرهٔ صندوق با ۲۰ هزار کالا`() {
    val (d, codes) = shop()
    val t = open(d)
    val scan = scanBox()
    val ms = ArrayList<Double>()
    for ((i, code) in codes.filterIndexed { i, _ -> i % 97 == 0 }.take(30).withIndex()) {
      scan.performTextInput(code)
      val t0 = System.nanoTime()
      scan.enter()
      rule.waitForIdle()
      ms += (System.nanoTime() - t0) / 1e6
      assertEquals("اسکنِ ${i + 1}", i + 1, t.cart.lines.value.size)
    }
    val sorted = ms.drop(3).sorted() // سه اسکنِ نخست گرم شدنِ JVM است
    val p95 = sorted[((sorted.size - 1) * 0.95).toInt()]
    println("S01 scan→row (window, 20k items): n=${sorted.size} p50=%.1f p95=%.1f max=%.1f ms".format(Locale.ROOT, sorted[sorted.size / 2], p95, sorted.last()))
    assertTrue("p95 = $p95 ms", p95 <= PosSpeed.SCAN_BUDGET_MS * 5)
    scan.assertIsFocused()
  }
}
