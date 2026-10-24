package ir.vil3ntec.tohid.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import ir.vil3ntec.tohid.core.orders.OrderText
import ir.vil3ntec.tohid.ui.components.common.OrderStatusTone
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.pillColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  سیستمِ رنگِ VILL3N — دستورِ صاحب محصول (۱۴۰۵/۰۷/۲۸، `COLOR-SYSTEM.md`).
 *
 *  ۱. عددها عینِ فایل‌اند؛ هر انحراف در `deviations` نام و دلیل دارد و جای دیگری
 *     بی‌صدا عوض نمی‌شود.
 *  ۲. جدولِ کنتراست (WCAG AA): متنِ کوچک ≥ ۴٫۵:۱، آیکن و نشانگر و خطِ کادر ≥ ۳:۱.
 *  ۳. هفت وضعیتِ سفارش یک معنا دارند: هر وضعیتِ `OrderText.STATUS` نشانِ ثبت‌شده
 *     دارد و پیک همان معنا را با نام‌های خودش می‌گیرد.
 */
class Vill3nPaletteTest {

  private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
  }

  private fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)

  /** نام ⇐ مقدارِ فایلِ صاحب محصول (§۳–۵) */
  private val ownerLight = mapOf(
    "background" to "#F3F6FA", "backgroundSubtle" to "#EAF0F6", "surface" to "#FFFFFF",
    "surfaceElevated" to "#F8FAFC", "surfaceSelected" to "#E0F2EF",
    "textPrimary" to "#17212B", "textSecondary" to "#5B687A", "textMuted" to "#7B8798",
    "icon" to "#64748B", "iconActive" to "#108B81",
    "borderCard" to "#D5DEE9", "borderInput" to "#C5D0DE", "divider" to "#E2E8F0",
    "brandPrimary" to "#108B81", "brandDeep" to "#08665F", "link" to "#2563EB",
  )
  private val ownerDark = mapOf(
    "background" to "#0B1220", "surface" to "#162235", "surfaceElevated" to "#203149", "surfaceSelected" to "#123C3A",
    "textPrimary" to "#F1F5F9", "textSecondary" to "#B6C2D2", "textMuted" to "#94A3B8",
    "icon" to "#A3B2C5", "iconActive" to "#6EE7D1",
    "borderCard" to "#34445A", "borderInput" to "#40516A", "divider" to "#29374A",
  )

  private fun tokens(c: ShopColors) = mapOf(
    "background" to c.background, "backgroundSubtle" to c.backgroundSubtle, "surface" to c.surface,
    "surfaceElevated" to c.surfaceElevated, "surfaceSelected" to c.surfaceSelected,
    "textPrimary" to c.textPrimary, "textSecondary" to c.textSecondary, "textMuted" to c.textMuted,
    "icon" to c.icon, "iconActive" to c.iconActive,
    "borderCard" to c.borderCard, "borderInput" to c.borderInput, "divider" to c.divider,
    "brandPrimary" to c.brandPrimary, "brandDeep" to c.brandDeep, "link" to c.link,
  )

  /**
   *  انحراف‌های ثبت‌شده از فایل — فقط کوتاه شود. هر کدام دلیلِ کنتراست دارد
   *  (جدولِ کامل در توضیحِ PR).
   */
  private val deviations = mapOf(
    //  سفید روی #108B81 = ۴٫۱۷:۱ ⇒ برچسبِ دکمهٔ پُر زیرِ AA؛ دکمهٔ پُر و `primary`ِ متریال #08665F
    "light:button.primary.bg" to ("#108B81" to "#08665F"),
  )

  @Test
  fun `عددهای سطح، متن، خط و برند عینِ فایلِ صاحب محصول‌اند`() {
    val bad = mutableListOf<String>()
    for ((theme, owner, c) in listOf(Triple("light", ownerLight, LightColors), Triple("dark", ownerDark, DarkColors))) {
      val t = tokens(c)
      for ((name, want) in owner) {
        val got = hex(t.getValue(name))
        if (got != want) bad += "$theme:$name = $got ≠ $want"
      }
      for ((name, v) in t) if (v.alpha != 1f) bad += "$theme:$name شفاف است"
    }
    //  برند و رنگ‌های پشتیبان (§۳) و سری‌های نمودار به ترتیب (§۱۰)
    assertEquals(listOf("#108B81", "#2563EB", "#8B5CF6", "#F59E0B", "#EF4444", "#14B8A6", "#94A3B8"), LightColors.chart.series.map(::hex))
    assertEquals(LightColors.chart.series, DarkColors.chart.series)
    assertEquals("#E2E8F0", hex(LightColors.chart.grid))
    assertEquals("#29374A", hex(DarkColors.chart.grid))
    assertEquals("#DDF5EF", hex(Brand.successSurface))
    assertEquals("#DBEAFE", hex(Brand.blueSurface))
    assertEquals(emptyList<String>(), bad)
  }

  @Test
  fun `دکمه‌ها و هفت وضعیت عینِ فایل‌اند، جز انحراف‌های ثبت‌شده`() {
    fun b(t: ButtonTone) = listOf(hex(t.bg), hex(t.text), hex(t.border))
    val l = LightColors.button
    val d = DarkColors.button
    //  §۷ — دکمهٔ اصلیِ روز در `deviations`
    assertEquals(deviations.getValue("light:button.primary.bg").second, hex(l.primary.bg))
    assertEquals("#FFFFFF", hex(l.primary.text))
    assertEquals(listOf("#FFFFFF", "#08665F", "#B8DCD5"), b(l.secondary))
    assertEquals(listOf("#E8EDF3", "#344256"), b(l.neutral).take(2))
    assertEquals(listOf("#DC2626", "#FFFFFF"), b(l.danger).take(2))
    assertEquals(listOf("#E2E8F0", "#94A3B8"), b(l.disabled).take(2))
    assertEquals(listOf("#19A985", "#041B15"), b(d.primary).take(2))
    assertEquals(listOf("#162235", "#6EE7D1", "#40516A"), b(d.secondary))
    assertEquals(listOf("#203149", "#F1F5F9"), b(d.neutral).take(2))
    assertEquals(listOf("#DC2626", "#FFFFFF"), b(d.danger).take(2))
    //  §۸ — هفت وضعیتِ روز (متن، زمینه) و «تحویل‌شده»ِ شب
    val s = LightColors.status
    assertEquals(
      listOf(
        "#15803D/#DCFCE7", "#1D4ED8/#DBEAFE", "#92400E/#FEF3C7", "#B91C1C/#FEE2E2",
        "#6D28D9/#EDE9FE", "#0E7490/#CFFAFE", "#475569/#E2E8F0",
      ),
      s.all.map { (_, t) -> "${hex(t.fg)}/${hex(t.bg)}" },
    )
    assertEquals("#86EFAC/#123C2B", DarkColors.status.delivered.let { "${hex(it.fg)}/${hex(it.bg)}" })
    //  سطحِ وارونه (نوارِ سبد): روز #0B1220 و شب #050A14، برآمده #162235
    assertEquals(listOf("#0B1220", "#162235"), listOf(hex(LightColors.inverse), hex(LightColors.inverseRaised)))
    assertEquals(listOf("#050A14", "#162235"), listOf(hex(DarkColors.inverse), hex(DarkColors.inverseRaised)))
  }

  @Test
  fun `جدولِ کنتراست — متن ≥ ۴٫۵، آیکن و نشانگر ≥ ۳ در روز و شب`() {
    val bad = mutableListOf<String>()
    fun need(theme: String, what: String, fg: Color, bg: Color, min: Float) {
      val r = contrast(fg, bg)
      if (r < min) bad += "$theme: $what = ${"%.2f".format(r)} < $min"
    }
    for ((theme, c) in listOf("روز" to LightColors, "شب" to DarkColors)) {
      val grounds = listOf(
        "background" to c.background, "backgroundSubtle" to c.backgroundSubtle, "surface" to c.surface,
        "surfaceElevated" to c.surfaceElevated, "surfaceSelected" to c.surfaceSelected,
      )
      for ((g, ground) in grounds) {
        need(theme, "textPrimary/$g", c.textPrimary, ground, 4.5f)
        need(theme, "textSecondary/$g", c.textSecondary, ground, 4.5f)
        need(theme, "brandText/$g", c.brandText, ground, 4.5f)
        need(theme, "icon/$g", c.icon, ground, 3f)
        need(theme, "iconActive/$g", c.iconActive, ground, 3f)
        need(theme, "brandPrimary/$g (پُر و نشانگر)", c.brandPrimary, ground, 3f)
      }
      need(theme, "link/surface", c.link, c.surface, 4.5f)
      need(theme, "link/background", c.link, c.background, 4.5f)
      need(theme, "focus/surface", c.focus, c.surface, 3f)
      need(theme, "borderInput/surface (کادرِ ورودی)", c.borderInput, c.surface, 1.5f)
      need(theme, "onInverse/inverse", c.onInverse, c.inverse, 4.5f)
      need(theme, "onInverseMuted/inverseRaised", c.onInverseMuted, c.inverseRaised, 4.5f)
      //  دکمه‌ها (غیرفعال از قاعده بیرون است — WCAG 1.4.3)
      for ((name, t) in listOf("primary" to c.button.primary, "secondary" to c.button.secondary, "neutral" to c.button.neutral, "danger" to c.button.danger)) {
        need(theme, "button.$name", t.text, t.bg, 4.5f)
      }
      //  هفت وضعیت: متن روی زمینهٔ خودش
      for ((name, t) in c.status.all) need(theme, "status.$name", t.fg, t.bg, 4.5f)
      //  پیام‌ها: متن ≥ ۴٫۵ و آیکن ≥ ۳ روی زمینهٔ خودشان
      for ((name, t) in c.message.all) {
        need(theme, "message.$name.text", t.text, t.bg, 4.5f)
        need(theme, "message.$name.icon", t.icon, t.bg, 3f)
      }
    }
    //  textMuted در شب متنِ عادی است؛ در روز فقط راهنما/غیرفعال/درشت (۳٫۶:۱ روی سفید) —
    //  برای همین نامِ قدیمیِ `muted2` (متنِ کوچک در صفحه‌ها) به `textSecondary` می‌رود
    for ((g, ground) in listOf("surface" to DarkColors.surface, "surfaceElevated" to DarkColors.surfaceElevated)) {
      need("شب", "textMuted/$g", DarkColors.textMuted, ground, 4.5f)
    }
    need("روز", "textMuted/surface (درشت/راهنما)", LightColors.textMuted, LightColors.surface, 3f)
    assertEquals(LightColors.textSecondary, LightColors.muted2)
    assertTrue("زیرِ آستانه — $bad", bad.isEmpty())
  }

  @Test
  fun `سریِ نمودارِ زیرِ ۳ به ۱ روی کارتِ روز همیشه برچسب دارد — فهرست فقط کوتاه شود`() {
    //  §۱۰: «همیشه نامِ سری/راهنما/برچسب». نارنجی، فیروزه‌ای و خاکستریِ فایل روی سفید
    //  زیرِ ۳:۱اند؛ رنگ می‌ماند ولی تنها حاملِ معنا نیست (DeskDashboard برچسبِ عدد دارد).
    val low = LightColors.chart.series.filter { contrast(it, LightColors.surface) < 3f }.map(::hex).toSet()
    assertEquals(setOf("#F59E0B", "#14B8A6", "#94A3B8"), low)
    val lowDark = DarkColors.chart.series.filter { contrast(it, DarkColors.surface) < 3f }
    assertEquals(emptyList<Color>(), lowDark)
  }

  @Test
  fun `طرحِ متریال از توکن‌ها می‌آید`() {
    for (c in listOf(LightColors, DarkColors)) {
      val m = materialScheme(c)
      assertEquals(c.button.primary.bg, m.primary)
      assertEquals(c.button.primary.text, m.onPrimary)
      assertEquals(c.background, m.background)
      assertEquals(c.surface, m.surface)
      assertEquals(c.textPrimary, m.onSurface)
      assertEquals(c.textSecondary, m.onSurfaceVariant)
      assertEquals(c.surfaceSelected, m.primaryContainer)
      assertEquals(c.borderInput, m.outline)
      assertEquals(c.divider, m.outlineVariant)
      //  سطح‌های متریال رنگِ برند نمی‌گیرند (بی تهِ سبزآبی روی پنجره و منو)
      assertEquals(c.surface, m.surfaceTint)
      assertEquals(c.surface, m.surfaceContainerHigh)
    }
    assertEquals(LightColors.button.danger.bg, materialScheme(LightColors).error)
    //  رنگ‌های قدیمیِ صفحه‌ها نامِ دیگرِ همان توکن‌اند
    with(LightColors) {
      assertEquals(background, bg); assertEquals(textPrimary, text); assertEquals(textSecondary, muted)
      assertEquals(borderCard, border); assertEquals(surfaceElevated, surface2); assertEquals(brandText, primary)
      assertEquals(surfaceSelected, primaryTint); assertEquals(status.delivered.fg, success); assertEquals(status.cancelled.fg, danger)
      assertEquals(backgroundSubtle, zoneNav); assertEquals(borderInput, fieldBorder)
    }
  }

  @Test
  fun `بنفشِ پیشین هیچ‌جای پالت نیست`() {
    //  «سبزآبیِ #108B81 همه‌جا جای بنفش را می‌گیرد» — بنفش فقط «نیاز به بررسی» و سریِ هزینه است
    val purple = setOf("#5B45E0", "#4532C2", "#A594FF", "#C3B8FF", "#7C5CFF", "#6F50F0")
    for (c in listOf(LightColors, DarkColors)) {
      val all = tokens(c).values + listOf(c.primary, c.primaryDark, c.accent, c.focus, c.glow.copy(alpha = 1f), c.auroraOne.copy(alpha = 1f))
      val found = all.map(::hex).filter { it in purple }
      assertEquals(emptyList<String>(), found)
    }
  }

  /** همهٔ وضعیت‌های سفارش ⇐ نشان؛ تازه شدنِ وضعیت در هسته این‌جا تصمیمِ رنگ می‌خواهد */
  private val orderTable = mapOf(
    "new" to PillTone.WARNING, "return_requested" to PillTone.WARNING,
    "accepted" to PillTone.PREPARING, "picking" to PillTone.PREPARING, "packed" to PillTone.PREPARING,
    "ready_for_pickup" to PillTone.PREPARING,
    "out_for_delivery" to PillTone.INFO, "retry_scheduled" to PillTone.INFO,
    "delivered" to PillTone.SUCCESS, "picked_up" to PillTone.SUCCESS,
    "rejected" to PillTone.DANGER, "auto_rejected" to PillTone.DANGER, "cancelled" to PillTone.DANGER,
    "cancelled_by_customer" to PillTone.DANGER,
    "delivery_failed" to PillTone.REVIEW, "returned_to_store" to PillTone.REVIEW,
    "restocked" to PillTone.NEUTRAL, "refunded_partial" to PillTone.NEUTRAL, "refunded_full" to PillTone.NEUTRAL,
  )

  @Test
  fun `وضعیتِ سفارش و پیک همه‌جا یک معنا و یک رنگ دارند`() {
    assertEquals("هر وضعیتِ هسته باید در جدولِ رنگ باشد", OrderText.STATUS.keys, orderTable.keys)
    for ((s, tone) in orderTable) assertEquals("سفارش: $s", tone, OrderStatusTone.order(s))
    //  همان معنا با نام‌های پیک
    assertEquals(OrderStatusTone.order("out_for_delivery"), OrderStatusTone.courier("moving"))
    assertEquals(OrderStatusTone.order("delivered"), OrderStatusTone.courier("delivered"))
    assertEquals(OrderStatusTone.order("delivery_failed"), OrderStatusTone.courier("failed"))
    assertEquals(OrderStatusTone.order("rejected"), OrderStatusTone.courier("rejected"))
    assertEquals(OrderStatusTone.order("new"), OrderStatusTone.courier("requested"))
    //  هر نشان یکی از هفت توکنِ وضعیت است و هفت نشان هفت رنگِ جدا دارند
    for (c in listOf(LightColors, DarkColors)) {
      val pairs = PillTone.entries.map { pillColors(c, it) }
      assertEquals(7, pairs.toSet().size)
      assertEquals(c.status.all.map { (_, t) -> t.fg to t.bg }.toSet(), pairs.toSet())
    }
    //  قرمز فقط برای لغو و رد
    val red = orderTable.filterValues { it == PillTone.DANGER }.keys
    assertEquals(setOf("rejected", "auto_rejected", "cancelled", "cancelled_by_customer"), red)
  }
}
