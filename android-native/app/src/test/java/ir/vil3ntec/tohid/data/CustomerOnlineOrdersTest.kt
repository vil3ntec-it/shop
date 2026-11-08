package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrderPermsDto
import ir.vil3ntec.tohid.core.model.OrdersConfigDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  CRM-07 — سفارش‌های اینترنتیِ هنوز تحویل‌نشده یا لغوشده در پروندهٔ مشتری (گوشیِ نسخهٔ ۳ و کامپیوتر):
 *  دروازه (بخشِ روشن + «تماس»)، شماره‌ها، ردیف‌ها و سیم‌کشیِ هر دو پرونده و درِ سرور.
 */
class CustomerOnlineOrdersTest {

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/$rel") }.firstOrNull { it.isFile }?.readText()
      ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  private fun o(id: String, status: String, at: Long) = OrderDto(id = id, status = status, createdAt = at, orderNo = at, totalMinor = 12_050)

  @Test
  fun `فقط با بخشِ سفارشِ روشن و اجازهٔ تماس پرسیده می‌شود`() {
    assertFalse(CustomerOnlineOrders.canAsk(null))
    assertFalse(CustomerOnlineOrders.canAsk(OrdersConfigDto(enabled = false, permissions = OrderPermsDto(contact = true))))
    assertFalse("شاگرد (بی «تماس») هیچ", CustomerOnlineOrders.canAsk(OrdersConfigDto(enabled = true, permissions = OrderPermsDto(manage = true))))
    assertTrue(CustomerOnlineOrders.canAsk(OrdersConfigDto(enabled = true, permissions = OrderPermsDto(contact = true))))
  }

  @Test
  fun `شماره‌ها اول و دوم، بی خالی و تکراری؛ ادغام‌شده و بی شماره هیچ`() {
    assertEquals(listOf("0700111222", "0788555444"), CustomerOnlineOrders.phones(Debtor("d1", "احمد", phone = " 0700111222 ", phone2 = "0788555444")))
    assertEquals(listOf("0700111222"), CustomerOnlineOrders.phones(Debtor("d1", "احمد", phone = "0700111222", phone2 = "0700111222")))
    assertEquals(listOf("0788555444"), CustomerOnlineOrders.phones(Debtor("d1", "احمد", phone = "", phone2 = "0788555444")))
    assertEquals(emptyList<String>(), CustomerOnlineOrders.phones(Debtor("d1", "احمد")))
    assertEquals(emptyList<String>(), CustomerOnlineOrders.phones(Debtor("d1", "احمد", phone = "0700111222", mergedInto = "d2")))
    assertEquals(emptyList<String>(), CustomerOnlineOrders.phones(null))
  }

  @Test
  fun `ردیف‌ها فقط باز و لغوشده، تازه‌ترین بالا، هر سفارش یک بار`() {
    val rows = CustomerOnlineOrders.rows(listOf(
      o("a", "new", 10), o("b", "picked_up", 50), o("c", "cancelled", 30), o("d", "delivered", 40),
      o("e", "out_for_delivery", 20), o("c", "cancelled", 30), o("f", "rejected", 60), o("", "new", 70),
      o("g", "refunded_full", 80), o("h", "returned_to_store", 25),
    ))
    assertEquals(listOf("f", "c", "h", "e", "a"), rows.map { it.id })
    assertEquals(120.5, CustomerOnlineOrders.amount(rows.first()), 0.0)
    //  همان دو فهرستِ سرور (`OPEN_STATUSES` + `CUSTOMER_OTHER`)
    val server = File(listOf("../../server/src/lib/orders.js", "../server/src/lib/orders.js", "server/src/lib/orders.js").first { File(it).isFile }).readText()
    for (s in CustomerOnlineOrders.OPEN + CustomerOnlineOrders.CANCELLED) assertTrue(s, server.contains("'$s'"))
  }

  @Test
  fun `هر دو پرونده و درِ سرور`() {
    assertTrue(src("ui/v3/money/DebtorFileExtras.kt").contains("CustomerOnlineOrdersPanel(d, debtorId, Modifier.padding(top = 12.dp), onOpen = onOpenOrder)"))
    assertTrue(src("ui/screens/DeskCustomers.kt").contains("CustomerOnlineOrdersPanel(d, id, Modifier.padding(top = 8.dp), onOpen = onOpenOrder)"))
    val panel = src("ui/screens/CustomerOnlineOrdersAndroid.kt")
    //  دروازه پیش از هر پرسش؛ خطای شبکه یا فهرستِ خالی ⇒ هیچ
    //  CRM-15: همان پرسش، با شناسهٔ پرونده برای «چه کسی پرونده را دید» ِ سرور
    assertTrue(panel.indexOf("if (!CustomerOnlineOrders.canAsk(OrdersLive.config.value)) return") in 0 until panel.indexOf("forCustomer(phones, customerId = debtorId)"))
    assertTrue(panel.contains("val shown = page ?: return"))
    assertTrue(panel.contains("if (rows.isEmpty()) return"))
    val repo = src("data/repo/OrdersRepository.kt")
    assertTrue(repo.contains("val q = phones.take(2).joinToString(\"&\") { \"phone=\" + enc(it) }"))
    assertTrue(repo.contains("api.get(\"\$ROOT/customer?\$q\")"))
  }

  /** CRM-07 — هر ردیفِ سفارش در پرونده همان سفارش را باز می‌کند (`orders/{id}`، همان دروازهٔ بخشِ سفارش) */
  @Test
  fun `ردیفِ سفارش در پروندهٔ گوشی و کامپیوتر همان سفارش را باز می‌کند`() {
    val panel = src("ui/screens/CustomerOnlineOrdersAndroid.kt")
    assertTrue(panel.contains("onOpen: ((String) -> Unit)? = null"))
    assertTrue(panel.contains("Modifier.clickable(onClickLabel = StrOnline.customerOrderOpen) { open(o.id) }"))
    //  بی صدازننده ردیف کلیک‌پذیر نیست (همان امروز)
    assertTrue(panel.contains("val open = onOpen"))
    val money = src("ui/v3/money/MoneyRoutes.kt")
    assertTrue(money.contains("onOpenOrder = { nav.open(OnlinePaths.order(it)) }"))
    assertTrue(money.contains("DebtorFileExtras(data, store, id, onOpenOrder = { nav.open(OnlinePaths.order(it)) })"))
    assertTrue(money.contains("DeskCustomersScreen(store, data, snackbar, onOpenOrder = { nav.open(OnlinePaths.order(it)) })"))
    val desk = src("ui/screens/DeskCustomers.kt")
    assertTrue(desk.contains("CustomerFile(store, d, snackbar, open.id, onOpenOrder) { openId = null }"))
    //  همان مسیرِ صفحهٔ سفارش، پشتِ همان «سفارش‌های زنده»
    assertTrue(src("ui/v3/online/OnlineRoutes.kt").contains("V3Route(\"orders/{id}\", RouteKind.PAGE, { StrOnline.ordersTitle }, parent = OnlinePaths.ORDERS, needs = setOf(Need.ORDERS_LIVE)"))
  }
}
