package ir.vil3ntec.tohid.ui.components.common

import ir.vil3ntec.tohid.ui.components.common.NavCatalog.ACCOUNT
import ir.vil3ntec.tohid.ui.components.common.NavCatalog.DEBTORS
import ir.vil3ntec.tohid.ui.components.common.NavCatalog.HOME
import ir.vil3ntec.tohid.ui.components.common.NavCatalog.MORE
import ir.vil3ntec.tohid.ui.components.common.NavCatalog.ORDERS
import ir.vil3ntec.tohid.ui.components.common.NavCatalog.PRODUCTS
import ir.vil3ntec.tohid.ui.components.common.NavCatalog.REPORTS
import ir.vil3ntec.tohid.ui.components.common.NavCatalog.SALE
import ir.vil3ntec.tohid.ui.components.common.NavCatalog.SCAN
import ir.vil3ntec.tohid.ui.components.common.NavCatalog.SEARCH
import ir.vil3ntec.tohid.ui.components.common.NavCatalog.SETTINGS
import ir.vil3ntec.tohid.ui.components.common.NavCatalog.TEAM
import ir.vil3ntec.tohid.ui.components.common.NavCatalog.WAREHOUSE
import ir.vil3ntec.tohid.ui.components.common.NavLayouts.Edit
import ir.vil3ntec.tohid.ui.components.common.NavLayouts.Problem
import ir.vil3ntec.tohid.ui.components.common.NavLayouts.Row
import ir.vil3ntec.tohid.ui.components.common.NavLayouts.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** نوارِ پایینِ قابلِ تنظیمِ گوشی (phone v3 §F2): پیش‌فرض، قاعده‌ها، ذخیره، ویرایشگر. */
class NavLayoutTest {

  private val owner = NavCatalog.allowed(canManage = true, ordersLive = true)
  private val clerk = NavCatalog.allowed(canManage = false, ordersLive = false)

  private fun ok(e: Edit): NavLayout = (e as Edit.Ok).layout

  @Test
  fun `پیش‌فرض همان r10 است — جست‌وجو، چهار موردِ قرص با بیشتر، حساب`() {
    val l = NavLayouts.load(null, owner)
    assertEquals(SEARCH, l.start)
    assertEquals(listOf(HOME, SALE, PRODUCTS, MORE), l.pill)
    assertEquals(ACCOUNT, l.end)
  }

  @Test
  fun `ذخیره و خواندن برمی‌گردد و دایرهٔ خاموش خاموش می‌ماند`() {
    val l = NavLayout(null, listOf(DEBTORS, REPORTS, HOME, MORE), SCAN)
    assertEquals(l, NavLayouts.decode(NavLayouts.encode(l)))
    assertEquals(l, NavLayouts.load(NavLayouts.encode(l), owner))
  }

  @Test
  fun `ذخیرهٔ خراب یا نسخهٔ ناشناخته ⇒ پیش‌فرض`() {
    assertNull(NavLayouts.decode("v9|a|b|c"))
    assertNull(NavLayouts.decode("garbage"))
    assertEquals(NavLayouts.DEFAULT, NavLayouts.load("v9|x|y|z", owner))
  }

  @Test
  fun `شناسهٔ ناشناخته پس از به‌روزرسانی بی‌صدا کنار می‌رود و قرص دوباره پر می‌شود`() {
    val l = NavLayouts.load("v1|ghost|home,vanished,more|old", owner)
    assertNull(l.start); assertNull(l.end)
    assertTrue(MORE in l.pill)
    assertTrue(l.pill.size >= NavLayouts.MIN_PILL)
    assertFalse("vanished" in l.pill)
  }

  @Test
  fun `بیشتر همیشه در قرص است — اگر نبود به ته می‌آید و از دایره بیرون می‌رود`() {
    val l = NavLayouts.normalize(NavLayout(MORE, listOf(HOME, SALE, PRODUCTS), ACCOUNT), owner)
    assertNull(l.start)
    assertEquals(MORE, l.pill.last())
    assertEquals(4, l.pill.size)
  }

  @Test
  fun `قرصِ بلند تا پنج کوتاه می‌شود بی آن‌که بیشتر برود`() {
    val l = NavLayouts.normalize(NavLayout(null, listOf(HOME, SALE, PRODUCTS, DEBTORS, REPORTS, WAREHOUSE, MORE), null), owner)
    assertEquals(NavLayouts.MAX_PILL, l.pill.size)
    assertTrue(MORE in l.pill)
  }

  @Test
  fun `قرصِ کوتاه تا سه پر می‌شود و بیشتر ته می‌ماند`() {
    val l = NavLayouts.normalize(NavLayout(SEARCH, listOf(MORE), ACCOUNT), owner)
    assertEquals(NavLayouts.MIN_PILL, l.pill.size)
    assertEquals(MORE, l.pill.last())
    assertFalse(SEARCH in l.pill); assertFalse(ACCOUNT in l.pill)
  }

  @Test
  fun `تکرار نه — قرص بر دایره و شروع بر پایان مقدم است`() {
    val l = NavLayouts.normalize(NavLayout(HOME, listOf(HOME, SALE, SALE, MORE), HOME), owner)
    assertNull(l.start); assertNull(l.end)
    assertEquals(l.ids.distinct(), l.ids)
    val m = NavLayouts.normalize(NavLayout(SCAN, listOf(HOME, SALE, MORE), SCAN), owner)
    assertEquals(SCAN, m.start); assertNull(m.end)
  }

  @Test
  fun `نقش فیلتر می‌کند — شاگرد کارمندان و تنظیمات را ندارد و سفارشِ خاموش پیشنهاد نمی‌شود`() {
    assertFalse(TEAM in clerk); assertFalse(SETTINGS in clerk); assertFalse(ORDERS in clerk)
    val l = NavLayouts.normalize(NavLayout(TEAM, listOf(HOME, ORDERS, SETTINGS, MORE), SALE), clerk)
    assertNull(l.start)
    assertFalse(l.ids.any { it == TEAM || it == SETTINGS || it == ORDERS })
    assertEquals(SALE, l.end)
    val rows = NavLayouts.rows(l, clerk).filterIsInstance<Row.Item>().map { it.id }
    assertFalse(rows.any { it == TEAM || it == SETTINGS || it == ORDERS })
    assertEquals(Edit.Rejected(Problem.NOT_ALLOWED), NavLayouts.place(l, TEAM, Slot.PILL, null, clerk))
  }

  @Test
  fun `کلید برای هر کاربر جداست و بی‌حساب local است`() {
    assertEquals("phone_nav:local", NavLayouts.key(null))
    assertEquals("phone_nav:u_1", NavLayouts.key("u:1"))
    assertFalse(NavLayouts.key("a") == NavLayouts.key("b"))
  }

  @Test
  fun `گذاشتن در دایره موردِ پیشین را بیرون می‌برد`() {
    val l = ok(NavLayouts.place(NavLayouts.DEFAULT, SCAN, Slot.START, null, owner))
    assertEquals(SCAN, l.start)
    assertEquals(Slot.OUT, NavLayouts.slotOf(l, SEARCH))
  }

  @Test
  fun `بیشتر را نمی‌شود بیرون یا در دایره گذاشت`() {
    assertEquals(Edit.Rejected(Problem.MORE_LOCKED), NavLayouts.place(NavLayouts.DEFAULT, MORE, Slot.OUT, null, owner))
    assertEquals(Edit.Rejected(Problem.MORE_LOCKED), NavLayouts.place(NavLayouts.DEFAULT, MORE, Slot.END, null, owner))
  }

  @Test
  fun `قرص بیش از پنج و کمتر از سه نمی‌شود`() {
    var l = ok(NavLayouts.place(NavLayouts.DEFAULT, DEBTORS, Slot.PILL, null, owner))
    assertEquals(5, l.pill.size)
    assertEquals(MORE, l.pill.last())
    assertEquals(Edit.Rejected(Problem.PILL_FULL), NavLayouts.place(l, REPORTS, Slot.PILL, null, owner))
    l = NavLayout(null, listOf(HOME, SALE, MORE), null)
    assertEquals(Edit.Rejected(Problem.PILL_MIN), NavLayouts.place(l, HOME, Slot.OUT, null, owner))
  }

  @Test
  fun `کشیدن در فهرست — جابه‌جایی در قرص و رفتن به دایرهٔ شروع`() {
    val l0 = NavLayouts.DEFAULT
    val rows = NavLayouts.rows(l0, owner)
    //  [شروع] جست‌وجو [قرص] خانه فروش کالاها بیشتر [پایان] حساب [بیرون] …
    assertEquals(Row.Header(Slot.START), rows[0])
    val home = rows.indexOf(Row.Item(HOME))
    val products = rows.indexOf(Row.Item(PRODUCTS))
    val l1 = ok(NavLayouts.move(l0, rows, products, home, owner))
    assertEquals(listOf(PRODUCTS, HOME, SALE, MORE), l1.pill)

    val rows1 = NavLayouts.rows(l1, owner)
    val scan = rows1.indexOf(Row.Item(SCAN))
    val l2 = ok(NavLayouts.move(l1, rows1, scan, 1, owner))
    assertEquals(SCAN, l2.start)
    assertEquals(Slot.OUT, NavLayouts.slotOf(l2, SEARCH))
  }

  @Test
  fun `چیدمانِ سفارشیِ عکس — قرض‌داران و گزارش‌ها در قرص، اسکن در شروع`() {
    var l = NavLayouts.DEFAULT
    l = ok(NavLayouts.place(l, SCAN, Slot.START, null, owner))
    l = ok(NavLayouts.place(l, PRODUCTS, Slot.OUT, null, owner))
    l = ok(NavLayouts.place(l, DEBTORS, Slot.PILL, 1, owner))
    l = ok(NavLayouts.place(l, REPORTS, Slot.PILL, 2, owner))
    assertEquals(NavLayout(SCAN, listOf(HOME, DEBTORS, REPORTS, SALE, MORE), ACCOUNT), l)
    assertEquals(l, NavLayouts.normalize(l, owner))
  }

  @Test
  fun `موردِ فعال از روی صفحهٔ باز`() {
    val l = NavLayouts.DEFAULT
    assertEquals(HOME, NavCatalog.activeFor(l, "home"))
    assertEquals(ACCOUNT, NavCatalog.activeFor(l, "profile"))
    assertEquals(MORE, NavCatalog.activeFor(l, "more"))
    assertNull(NavCatalog.activeFor(l, "stock"))
    //  اسکن و فروش هر دو به «فروش» می‌روند؛ همان که نشسته فعال می‌شود
    val s = NavLayout(SCAN, listOf(HOME, PRODUCTS, MORE), null)
    assertEquals(SCAN, NavCatalog.activeFor(s, "sale"))
  }
}
