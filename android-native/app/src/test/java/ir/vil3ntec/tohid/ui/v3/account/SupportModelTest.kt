package ir.vil3ntec.tohid.ui.v3.account

import ir.vil3ntec.tohid.core.model.SupportMessageDto
import ir.vil3ntec.tohid.ui.v3.account.support.SupportModel
import ir.vil3ntec.tohid.ui.v3.account.support.SupportModel.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportModelTest {
  private val day = 86_400_000L
  private val now = java.util.Calendar.getInstance().apply { set(2026, 9, 20, 15, 0, 0) }.timeInMillis

  @Test
  fun `جداکنندهٔ روز، سه گونه پیام و حبابِ در حالِ فرستادن`() {
    val items = SupportModel.items(
      listOf(
        SupportMessageDto(id = "1", sender = "user", body = "سلام", createdAt = now - day - 3600_000),
        SupportMessageDto(id = "2", sender = "admin", senderName = "", body = "بفرمایید", createdAt = now - day),
        SupportMessageDto(id = "3", sender = "system", body = "سه روز مانده", createdAt = now - 60_000),
      ),
      now,
      pending = "کمک",
    )
    val days = items.filterIsInstance<Item.Day>().map { it.label }
    assertEquals(listOf("دیروز", "امروز"), days)
    val bubbles = items.filterIsInstance<Item.Bubble>()
    assertEquals(listOf(SupportModel.Kind.MINE, SupportModel.Kind.THEIRS, SupportModel.Kind.SYSTEM, SupportModel.Kind.MINE), bubbles.map { it.kind })
    assertEquals(listOf(null, "پشتیبانی", "خبر از فروشگاه من", null), bubbles.map { it.sender })
    assertTrue(bubbles.last().sending)
    assertEquals(items.size, items.map { it.key }.toSet().size)
  }

  @Test
  fun `فرستادن فقط با نوشته و نه وسطِ فرستادن`() {
    assertTrue(SupportModel.canSend("سلام", sending = false))
    assertFalse(SupportModel.canSend("  ", sending = false))
    assertFalse(SupportModel.canSend("سلام", sending = true))
  }
}
