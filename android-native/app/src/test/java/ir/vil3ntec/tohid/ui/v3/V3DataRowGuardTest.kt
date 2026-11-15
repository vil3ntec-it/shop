package ir.vil3ntec.tohid.ui.v3

import androidx.compose.ui.graphics.Color
import ir.vil3ntec.tohid.ui.theme.PhoneV3Dark
import ir.vil3ntec.tohid.ui.theme.PhoneV3Light
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  ۶۸c — نگهبانِ متنیِ «ردیفِ فهرستِ داده» (صاحب‌کار: «این شلوغی‌هات رو … درست کن دقیق و منظم» — همان عکسِ «پس»):
 *   • هر فهرستِ داده (خریدها، سفارشِ خرید، تأمین‌کننده‌ها و صفحهٔ هر کدام، مصارف، رسیدها، تاریخچهٔ فروش، کالاها، انبار، سفارش‌های
 *     آنلاین، تخفیف‌ها، کارمندان) ردیفش را با `V3DataRow` می‌کشد — نه قرصِ رنگیِ وضعیت، نه دکمهٔ دوم، نه مبلغِ رنگی در پایان؛
 *   • `V3DataRow` فقط **یک** کار ([V3RowAction]) می‌گیرد و جای دلخواهی برای دکمهٔ دیگر (`trailing`) ندارد؛
 *   • خطِ مبلغ یک‌خطی است و دست‌بالا یک واژهٔ رنگی (فقط خطر) دارد؛ «توجه» (نسیه، کم، نرسیده) دیگر زرد نیست؛
 *   • ردیفِ پیمایشیِ تراشه‌ها لبه‌اش را محو می‌کند (نیمه‌بریده دیده نمی‌شود).
 *  آزمونِ روی گره‌های واقعی: `DataRowsE68cUiTest`.
 */
class V3DataRowGuardTest {

  private val main: File = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
    .map { File(it, "ir/vil3ntec/tohid") }.first { it.isDirectory }

  private fun text(path: String) = File(main, path).readText().replace("\r\n", "\n")

  /** فایلِ ردیفِ هر فهرستِ داده */
  private val lists = listOf(
    "ui/v3/money/PurchasesList.kt", "ui/v3/money/PurchaseOrderRow.kt", "ui/v3/money/SuppliersScreen.kt",
    "ui/v3/money/SupplierLedgerScreen.kt", "ui/v3/money/ExpensesScreen.kt", "ui/v3/money/ReceiptsScreen.kt",
    "ui/v3/sales/history/HistoryParts.kt", "ui/v3/products/ProductsListParts.kt", "ui/v3/stock/StockListParts.kt",
    "ui/v3/online/orders/OrderCard.kt", "ui/screens/Promos.kt", "ui/v3/account/team/TeamScreen.kt",
  )

  @Test fun `هر فهرستِ داده ردیفش را با V3DataRow می‌کشد و قرص یا مبلغِ رنگیِ کهنه ندارد`() {
    val bad = mutableListOf<String>()
    for (path in lists) {
      val src = text(path)
      if (!src.contains("V3DataRow(")) bad += "$path: بی V3DataRow"
      //  قرصِ رنگیِ وضعیت و برچسبِ رنگی در ردیف
      for (old in listOf("OrderStatusPill(", "StatusPill(", "DraftTag(StrOrdersHub.onlineBadge", "DebtLabel(", "StockQty(", "V3AmountStatus(")) {
        if (src.contains(old)) bad += "$path: $old"
      }
    }
    //  ردیفِ کارتِ سفارش و تخفیف دیگر دو دکمه ندارند
    assertFalse("دکمهٔ «ویرایش» و «حذف» ِ کارتِ تخفیف", text("ui/screens/Promos.kt").contains("TextButton(onClick = onEdit)"))
    assertFalse("دکمهٔ بزرگِ گامِ بعدیِ کارتِ صف", text("ui/v3/online/orders/OrderCard.kt").contains("V3Button("))
    assertTrue(bad.joinToString("\n"), bad.isEmpty())
  }

  @Test fun `V3DataRow یک کار می‌گیرد و جای دکمهٔ دیگری ندارد`() {
    val src = text("ui/v3/V3DataRow.kt")
    val sig = src.substring(src.indexOf("fun V3DataRow("), src.indexOf(") {", src.indexOf("fun V3DataRow(")))
    assertTrue(sig.contains("action: V3RowAction? = null"))
    assertFalse("جای دلخواهِ پایانِ ردیف", sig.contains("trailing"))
    assertFalse("فهرستِ کارها", Regex("""actions: List<V3RowAction>""").containsMatchIn(sig))
    //  قرصِ ردیفِ داده خنثی است و پیکان فقط وقتی کاری نیست
    assertTrue(src.contains("neutral = true"))
    assertTrue(src.contains("chevron = action == null"))
    //  خطِ مبلغ یک خط
    assertTrue(src.contains("maxLines = 1"))
  }

  @Test fun `خطِ مبلغ — مبلغ به رنگِ نوشته، وضعیت کم‌رنگ، دست‌بالا یک واژهٔ خطر و هیچ زرد`() {
    for (c in listOf(PhoneV3Light, PhoneV3Dark)) {
      val warning = PhoneV3Roles.status(c, V3Status.WARNING).fg
      val danger = v3RowToneColor(c, V3RowTone.DANGER)
      fun colors(meta: V3RowMeta): List<Color> = v3MetaText(c, meta).spanStyles.map { it.item.color }
      //  «۸٬۳۲۰ ؋ • ۳٬۳۲۰ ؋ مانده • نرسیده»: هیچ رنگی جز نوشته و کم‌رنگ
      val calm = colors(V3RowMeta("8,320 ؋", V3RowStatus("3,320 ؋ مانده", V3RowTone.ATTENTION), V3RowStatus("نرسیده", V3RowTone.ATTENTION)))
      assertEquals(setOf(c.ink, c.inkMuted), calm.toSet())
      assertFalse("زرد/کهربایی در خطِ مبلغ", warning in calm)
      //  دو خطر ⇒ فقط نخستین رنگی
      val two = colors(V3RowMeta("5,400 ؋", V3RowStatus("سررسید گذشته", V3RowTone.DANGER), V3RowStatus("تمام‌شده", V3RowTone.DANGER)))
      assertEquals(1, two.count { it == danger })
      assertEquals("مبلغ به رنگِ نوشته", c.ink, two.first())
      //  «توجه» دیگر زرد نیست
      assertEquals(c.inkMuted, v3RowToneColor(c, V3RowTone.ATTENTION))
      //  سندِ لغوشده: مبلغِ کم‌رنگ
      assertEquals(c.inkMuted, colors(V3RowMeta("300 ؋", struck = true)).first())
    }
  }

  @Test fun `اندازه‌های ردیفِ داده همان ردیفِ «بیشتر»‌اند و خطِ مویی از زیرِ متن`() {
    assertEquals(V3NavRowSpec.TILE, V3DataRowSpec.TILE)
    assertEquals(16, V3DataRowSpec.TITLE_SP)
    assertEquals(13, V3DataRowSpec.SECONDARY_SP)
    assertTrue(V3DataRowSpec.META_SP in 13..14)
    assertEquals(V3DataRowSpec.PAD_START + V3DataRowSpec.TILE + V3DataRowSpec.GAP, V3DataRowSpec.DIVIDER_START)
  }

  @Test fun `ردیفِ پیمایشیِ تراشه‌ها لبه‌اش را محو می‌کند`() {
    val chips = text("ui/v3/V3Chips.kt")
    val row = chips.substring(chips.indexOf("fun V3ChipRow("), chips.indexOf("fun Modifier.v3EdgeFade("))
    assertTrue(row.contains(".v3EdgeFade(scroll).horizontalScroll(scroll)"))
    assertTrue(chips.contains("BlendMode.DstIn"))
  }
}
