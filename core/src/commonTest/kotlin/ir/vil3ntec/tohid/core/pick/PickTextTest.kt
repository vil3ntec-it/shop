package ir.vil3ntec.tohid.core.pick

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** PCK: متن و تصمیم‌های کوچکِ برگهٔ جمع‌آوری */
class PickTextTest {

  private data class L(val no: Int, val open: Boolean)

  private val lines = listOf(L(2, false), L(1, true), L(3, true))

  @Test
  fun currentKeepsSelectionWhileOpen() {
    assertEquals(3, PickText.current(lines, 3, { it.no }, { it.open }))
  }

  @Test
  fun currentFallsBackToFirstOpenInSheetOrder() {
    assertEquals(1, PickText.current(lines, 2, { it.no }, { it.open }), "قلمِ بسته‌شده ⇒ نخستین قلمِ باز")
    assertEquals(1, PickText.current(lines, null, { it.no }, { it.open }))
    assertNull(PickText.current(listOf(L(1, false)), null, { it.no }, { it.open }))
  }

  @Test
  fun locationLabel() {
    assertEquals("راهرو 2 · قفسه 1 · طبقه بالا", PickText.location("2", "1", "بالا"))
    assertEquals("قفسه 3", PickText.location("", " 3 ", ""))
    assertEquals("جا ثبت نشده", PickText.location("", " ", ""))
  }

  @Test
  fun weightParsingAndHints() {
    assertEquals(1600, PickText.weightMilli("۱٫۶"))
    assertEquals(1600, PickText.weightMilli("1.6"))
    assertNull(PickText.weightMilli("0"))
    assertNull(PickText.weightMilli("1.6.2"))
    assertNull(PickText.weighHint(1500, 1650, "کیلو"))
    assertTrue(PickText.weighHint(1500, 1651, "کیلو")!!.contains("1.65"))
    assertTrue(PickText.weighHint(1500, 1300, "کیلو")!!.contains("کم"))
  }

  @Test
  fun sheetOnlyTransitionsHiddenOnlyWithFlag() {
    val all = listOf("picking", "cancelled")
    assertEquals(all, PickText.orderActions(all, picking = false), "پرچم خاموش ⇒ همان دکمه‌های پیشین")
    assertEquals(listOf("cancelled"), PickText.orderActions(all, picking = true))
    assertEquals(listOf("ready_for_pickup", "cancelled"), PickText.orderActions(listOf("ready_for_pickup", "cancelled"), true))
    assertEquals(listOf("cancelled"), PickText.orderActions(listOf("packed", "cancelled"), true))
  }

  @Test
  fun pickButtonOnlyForAcceptedOrPickingWithManage() {
    assertTrue(PickText.canPick("accepted", true))
    assertTrue(PickText.canPick("picking", true))
    assertEquals(false, PickText.canPick("picking", false))
    for (s in listOf("new", "packed", "ready_for_pickup", "cancelled")) assertEquals(false, PickText.canPick(s, true), s)
    assertEquals("ادامهٔ جمع‌آوری", PickText.pickAction("picking"))
    assertEquals("جمع‌آوری", PickText.pickAction("accepted"))
  }

  @Test
  fun statusAndRejectTexts() {
    assertEquals("پیدا نشد", PickText.lineStatus("not_found"))
    assertEquals("x", PickText.lineStatus("x"))
    assertTrue(PickText.reject("wrong_item").contains("اشتباه"))
    assertTrue(PickText.reject("odd").contains("odd"))
  }
}
