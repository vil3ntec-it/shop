package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PAY-19 — «کشو بی فروش» ِ `PrintSpooler.openDrawer`: دلیل اجباری، مجوزِ `drawer.open` و ردیفِ `no_sale` با
 *  کاربر، دلیل، تأییدکننده، دستگاه، چاپگر و صندوقِ بازِ همان لحظه.
 */
class DrawerNoSaleTest {

  @Test
  fun `دلیلِ خالی پیش از مجوز رد می‌شود`() {
    assertEquals(DrawerNoSale.NEED_REASON, DrawerNoSale.refusal("", allowed = true))
    assertEquals(DrawerNoSale.NEED_REASON, DrawerNoSale.refusal("   ", allowed = false))
  }

  @Test
  fun `بی مجوزِ drawer-open رد با APPROVAL_REQUIRED، با مجوز و دلیل آزاد`() {
    val no = DrawerNoSale.refusal("خرد کردنِ پول", allowed = false)
    assertEquals(DrawerNoSale.NEED_APPROVAL, no)
    assertTrue(no!!.contains("APPROVAL_REQUIRED"))
    assertNull(DrawerNoSale.refusal("خرد کردنِ پول", allowed = true))
  }

  @Test
  fun `ردیفِ no_sale — کاربر، دلیلِ پیراسته، صندوقِ باز و دستگاه و چاپگر`() {
    val e = DrawerNoSale.entry(
      id = "a1", reason = "  خرد کردنِ پول ", by = "احمد", device = "Pixel", printer = "XP-58",
      printerError = null, shiftOpenId = "shift-7", today = "2026-11-02", now = 1_700_000_000_000,
    )
    assertEquals("no_sale", e.type)
    assertEquals("a1", e.id)
    assertEquals("2026-11-02", e.date)
    assertEquals(1_700_000_000_000, e.createdAt)
    assertEquals("shift-7", e.refId)
    assertEquals("احمد", e.by)
    assertEquals("احمد", e.approvedBy)
    assertEquals("خرد کردنِ پول", e.reason)
    assertEquals(0.0, e.amount, 0.0)
    assertEquals("کشو بی فروش — خرد کردنِ پول — دستگاه: Pixel · چاپگر: XP-58 — تأیید: احمد", e.notes)
  }

  @Test
  fun `بی نامِ کاربر تأییدکننده policy است و خطای چاپگر در یادداشت می‌ماند`() {
    val e = DrawerNoSale.entry(
      id = "a2", reason = "پول خرد", by = null, device = "این دستگاه", printer = "XP-80",
      printerError = "کاغذ تمام شد", shiftOpenId = null, today = "2026-11-02", now = 5,
    )
    assertNull(e.by)
    assertEquals(DrawerNoSale.POLICY_APPROVER, e.approvedBy)
    assertNull(e.refId)
    assertTrue(e.notes.contains("— خطای چاپگر: کاغذ تمام شد —"))
    assertTrue(e.notes.endsWith("تأیید: policy"))
    assertFalse(e.notes.contains("صاحب"))
  }

  /** PAY-19 — Shift+F8 ِ صندوقِ کامپیوتر: بی چاپگرِ کشودار پیام، بی `drawer.open` همان APPROVAL_REQUIRED، وگرنه پنجرهٔ دلیل */
  @Test
  fun `کلیدِ کشو — بی چاپگرِ کشودار پیام، بی مجوز رد، با هر دو پنجرهٔ دلیل`() {
    assertEquals(DrawerNoSale.NO_DRAWER_PRINTER, DrawerNoSale.keyGate(hasDrawerPrinter = false, allowed = true))
    //  نبودِ چاپگر پیش از مجوز: شاگرد هم می‌فهمد چرا کشو باز نمی‌شود، نه «تأیید می‌خواهد»
    assertEquals(DrawerNoSale.NO_DRAWER_PRINTER, DrawerNoSale.keyGate(hasDrawerPrinter = false, allowed = false))
    assertEquals(DrawerNoSale.NEED_APPROVAL, DrawerNoSale.keyGate(hasDrawerPrinter = true, allowed = false))
    assertNull(DrawerNoSale.keyGate(hasDrawerPrinter = true, allowed = true))
  }

  /**
   *  PAY-19 — «کشو بی فروش» ِ صندوقِ گوشیِ نسخهٔ ۳ (`sales/drawer/move?mode=no_sale`): پیش‌تر فقط ردیف می‌نوشت (حتی با چاپگرِ
   *  کشودار کشو باز نمی‌شد) و سدش «شاگرد نیست» بود نه مجوزِ `drawer.open` ِ کامپیوتر. حالا همان سد؛ با چاپگرِ پیش‌فرضِ کشودار
   *  همان ضربهٔ `PrintSpooler.openDrawer` (که ردیف را فقط وقتی ضربه رسید می‌نویسد)، بی آن همان ثبتِ دلیل و تأیید.
   */
  @Test
  fun `کشو بی فروش ِ گوشی — بی مجوز بسته حتی با چاپگر، با مجوز ضربه یا فقط ثبت`() {
    assertEquals(DrawerNoSale.PhoneAction.DENIED, DrawerNoSale.phoneAction(hasDrawerPrinter = true, allowed = false))
    assertEquals(DrawerNoSale.PhoneAction.DENIED, DrawerNoSale.phoneAction(hasDrawerPrinter = false, allowed = false))
    assertEquals(DrawerNoSale.PhoneAction.KICK, DrawerNoSale.phoneAction(hasDrawerPrinter = true, allowed = true))
    assertEquals(DrawerNoSale.PhoneAction.RECORD, DrawerNoSale.phoneAction(hasDrawerPrinter = false, allowed = true))
  }

  @Test
  fun `برگهٔ کشو بی فروش ِ گوشی همان مجوز و همان ضربهٔ کامپیوتر را به کار می‌برد`() {
    val route = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { java.io.File(it, "ir/vil3ntec/tohid/ui/v3/sales/drawer/DrawerRoutes.kt") }.first { it.isFile }.readText()
    assertTrue(route.contains("ShopRole.canOpenDrawer(context)"))
    assertTrue(route.contains("DrawerNoSale.phoneAction(drawer != null, allowed)"))
    assertTrue(route.contains("PrintSpooler.openDrawer(context, kick, why)"))
    assertTrue(route.contains("DrawerNoSale.refusal(why, allowed)"))
  }
}
