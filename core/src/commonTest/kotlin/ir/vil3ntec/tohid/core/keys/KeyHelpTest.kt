package ir.vil3ntec.tohid.core.keys

import ir.vil3ntec.tohid.core.keys.KeyHelp.Item
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** S50 — فهرستِ F1 ِ هر صفحه و برچسب‌های «نگه‌داشتنِ Ctrl» */
class KeyHelpTest {

  private val items = listOf(
    Item("global.help", "global", "راهنما", listOf("F1"), listed = true),
    Item("global.find", "global", "جست‌وجو", listOf("Ctrl+F"), listed = true, mod = true),
    Item("global.orders", "global", "سفارش‌ها", listOf("Ctrl+O"), listed = false, mod = true),
    Item("sale.find", "sale", "جست‌وجوی کالا", listOf("Ctrl+F"), listed = true, overrides = listOf("global.find"), mod = true),
    Item("sale.pay", "sale", "پرداخت", listOf("F9"), listed = true),
    Item("orders.busy", "orders", "شلوغم", listOf("Ctrl+B"), listed = true, mod = true),
    Item("orders.open", "orders", "باز کردن", listOf("Enter"), listed = false),
  )
  private val globals = setOf("global")

  @Test
  fun screenListsOwnKeysThenUncoveredGlobals() {
    val s = KeyHelp.forScreen(items, globals, "sale")
    assertEquals(listOf("sale", "global"), s.map { it.scope })
    assertEquals(listOf("sale.find", "sale.pay"), s[0].items.map { it.id })
    //  Ctrl+F ِ صندوق Ctrl+F ِ سراسری را پوشانده؛ planned (سفارش‌ها) نمی‌آید
    assertEquals(listOf("global.help"), s[1].items.map { it.id })
  }

  @Test
  fun screensDifferAndUnknownScreenIsGlobalOnly() {
    val orders = KeyHelp.forScreen(items, globals, "orders")
    assertEquals(listOf("orders.busy"), orders[0].items.map { it.id })
    assertTrue(orders[1].items.any { it.id == "global.find" })
    val none = KeyHelp.forScreen(items, globals, null)
    assertEquals(listOf("global"), none.map { it.scope })
  }

  @Test
  fun routesMapToKeymapScopes() {
    assertEquals("sale", KeyHelp.scopeOf("sale"))
    assertEquals("sale", KeyHelp.scopeOf("sale/quick"))
    assertEquals("customers", KeyHelp.scopeOf("debtors"))
    assertEquals("purchase", KeyHelp.scopeOf("purchasing"))
    assertEquals("orders", KeyHelp.scopeOf("orders"))
    assertNull(KeyHelp.scopeOf("dashboard"))
    assertNull(KeyHelp.scopeOf(null))
  }

  @Test
  fun ctrlLabelsAreTheModKeysOfThatScreen() {
    val labels = KeyHelp.ctrlLabels(KeyHelp.forScreen(items, globals, "orders"))
    assertEquals(listOf("orders.busy", "global.find"), labels.map { it.id })
  }

  @Test
  fun holdingCtrlHalfASecondShowsLabelsAndAnyOtherKeyCancels() {
    val h = KeyHelp.Hold()
    h.down(ctrlAlone = true, at = 1_000)
    assertFalse(h.shown(1_499))
    assertEquals(1L, h.waitMs(1_499))
    //  تکرارِ خودکارِ کلید زمان را از نو نمی‌گیرد
    h.down(ctrlAlone = true, at = 1_300)
    assertTrue(h.shown(1_500))
    h.up()
    assertFalse(h.shown(9_999))
    assertNull(h.waitMs(9_999))
    //  Ctrl+B: کلیدِ دیگر ⇒ برچسب نه
    h.down(ctrlAlone = true, at = 2_000)
    h.down(ctrlAlone = false, at = 2_100)
    assertFalse(h.shown(3_000))
  }
}
