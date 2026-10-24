package ir.vil3ntec.tohid.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import ir.vil3ntec.tohid.core.orders.OrderText
import ir.vil3ntec.tohid.ui.components.common.OrderStatusTone
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.pillColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  قاعده‌های پالت — از THEME-V2 (۱۴۰۵/۰۸/۰۲، `THEME-V2.md`) برای **هر شش پالت** (سه تم × روز و شب).
 *  نامِ آزمون از سیستمِ VILL3N مانده؛ عددهای سبزِ آن با دستورِ تازه کنار رفت.
 *
 *  ۱. عددهای «آسمان» (پیش‌فرض = `LightColors`/`DarkColors`) عینِ فایل‌اند؛ هر انحراف در `deviations`
 *     نام و دلیل دارد. عددهای دو تمِ دیگر در `ThemeV2PaletteTest`.
 *  ۲. جدولِ کنتراست (WCAG AA) در هر شش پالت: متنِ کوچک ≥ ۴٫۵:۱، آیکن و نشانگر و خطِ کادر ≥ ۳:۱.
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

  /** هر شش پالت با نامشان */
  private val palettes: List<Pair<String, ShopColors>> =
    ShopTheme.entries.flatMap { t -> listOf("${t.id}-روز" to shopColors(t, false), "${t.id}-شب" to shopColors(t, true)) }

  /** نام ⇐ مقدارِ فایلِ صاحب‌کار برای «آسمان» (و مشتق‌های ثبت‌شدهٔ `gen_themes.py`) */
  private val ownerLight = mapOf(
    "background" to "#F5F8FF", "backgroundSubtle" to "#ECF0F8", "surface" to "#FFFFFF",
    "surfaceElevated" to "#EEF3FF", "surfaceSelected" to "#E5ECFD",
    "textPrimary" to "#0F172A", "textSecondary" to "#5B6A80", "textMuted" to "#7C8BA1",
    "icon" to "#64748B", "iconActive" to "#2563EB",
    "borderCard" to "#D5DEE9", "borderInput" to "#C8D2E0", "divider" to "#EDF1F7",
    "brandPrimary" to "#2563EB", "brandDeep" to "#1D4ED8", "link" to "#1D4ED8",
  )
  private val ownerDark = mapOf(
    "background" to "#0B1020", "surface" to "#121A2E", "surfaceElevated" to "#1A2440",
    "textPrimary" to "#E6ECFF", "textSecondary" to "#94A3B8", "textMuted" to "#8897AC",
    "iconActive" to "#60A5FA", "brandPrimary" to "#60A5FA",
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
   *  انحراف‌های ثبت‌شده از فایل (عددِ فایل ⇐ عددِ ما) — فقط کوتاه شود. دلیلِ هر کدام کنتراست است
   *  و در سرِ `ShopThemes.kt` هم نوشته شده.
   */
  private val deviations = mapOf(
    //  #64748B روی زمینه‌های رنگیِ تازه (#EEF3FF، زمینهٔ گزینهٔ فعال) زیرِ ۴٫۵:۱
    "light:textSecondary" to ("#64748B" to "#5B6A80"),
    //  #CBD5E1 روی سفید ۱٫۴۸:۱ — کفِ خطِ کادرِ ورودی ۱٫۵
    "light:borderInput" to ("#CBD5E1" to "#C8D2E0"),
    //  در شب `textMuted` متنِ عادی است و روی سطحِ ۲ باید ≥ ۴٫۵:۱ بماند
    "dark:textMuted" to ("#7C8BA1" to "#8897AC"),
  )

  @Test
  fun `عددهای «آسمان» عینِ فایلِ صاحب‌کارند، جز انحراف‌های ثبت‌شده`() {
    val bad = mutableListOf<String>()
    for ((theme, owner, c) in listOf(Triple("light", ownerLight, LightColors), Triple("dark", ownerDark, DarkColors))) {
      val t = tokens(c)
      for ((name, want) in owner) {
        val got = hex(t.getValue(name))
        if (got != want) bad += "$theme:$name = $got ≠ $want"
      }
    }
    for ((key, d) in deviations) {
      val (theme, name) = key.split(':')
      val got = hex(tokens(if (theme == "dark") DarkColors else LightColors).getValue(name))
      if (got != d.second || d.first == d.second) bad += "$key: انحرافِ ثبت‌شده با پالت نمی‌خواند ($got)"
    }
    //  پیش‌فرض همان «آسمان» است
    assertSame(shopColors(ShopTheme.SKY, false), LightColors)
    assertSame(shopColors(ShopTheme.SKY, true), DarkColors)
    for ((name, c) in palettes) for ((k, v) in tokens(c)) if (v.alpha != 1f) bad += "$name:$k شفاف است"
    //  سری‌های نمودار به ترتیبِ رنگ‌های همراه، در هر شش پالت یکی
    val accents = listOf("#0EA5E9", "#8B5CF6", "#F97362", "#F59E0B", "#14B8A6", "#EC4899", "#84CC16")
    for ((name, c) in palettes) {
      if (c.chart.series.map(::hex) != accents) bad += "$name: سری‌های نمودار ≠ رنگ‌های همراه"
      if (c.accents.map(::hex) != accents) bad += "$name: رنگ‌های همراه"
    }
    assertEquals("#E2E8F0", hex(LightColors.chart.grid))
    assertEquals(emptyList<String>(), bad)
  }

  @Test
  fun `دکمه‌ها و هفت وضعیتِ «آسمان» عینِ عددهای ثبت‌شده‌اند`() {
    fun b(t: ButtonTone) = listOf(hex(t.bg), hex(t.text), hex(t.border))
    val l = LightColors.button
    val d = DarkColors.button
    assertEquals(listOf("#1D4ED8", "#FFFFFF", "#1D4ED8"), b(l.primary))
    assertEquals(listOf("#FFFFFF", "#1D4ED8"), b(l.secondary).take(2))
    assertEquals(listOf("#DC2626", "#FFFFFF"), b(l.danger).take(2))
    assertEquals(listOf("#E2E8F0", "#94A3B8"), b(l.disabled).take(2))
    assertEquals(listOf("#60A5FA", "#0B1020"), b(d.primary).take(2))
    assertEquals(listOf("#121A2E", "#60A5FA"), b(d.secondary).take(2))
    assertEquals(listOf("#DC2626", "#FFFFFF"), b(d.danger).take(2))
    //  هفت وضعیت ثابت‌اند و به تم بستگی ندارند (THEME-V2 «رنگ‌های معنایی»)
    assertEquals(
      listOf(
        "#15803D/#DCFCE7", "#1D4ED8/#DBEAFE", "#92400E/#FEF3C7", "#B91C1C/#FEE2E2",
        "#6D28D9/#EDE9FE", "#0E7490/#CFFAFE", "#475569/#E2E8F0",
      ),
      LightColors.status.all.map { (_, t) -> "${hex(t.fg)}/${hex(t.bg)}" },
    )
    assertEquals("#86EFAC/#123C2B", DarkColors.status.delivered.let { "${hex(it.fg)}/${hex(it.bg)}" })
    for (t in ShopTheme.entries) {
      assertEquals(LightColors.status, shopColors(t, false).status)
      assertEquals(DarkColors.status, shopColors(t, true).status)
      assertEquals(LightColors.message, shopColors(t, false).message)
    }
  }

  @Test
  fun `جدولِ کنتراست — متن ≥ ۴٫۵، آیکن و نشانگر ≥ ۳ در هر شش پالت`() {
    val bad = mutableListOf<String>()
    fun need(theme: String, what: String, fg: Color, bg: Color, min: Float) {
      val r = contrast(fg, bg)
      if (r < min) bad += "$theme: $what = ${"%.2f".format(r)} < $min"
    }
    for ((theme, c) in palettes) {
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
      //  textMuted در شب متنِ عادی است؛ در روز فقط راهنما/غیرفعال/درشت —
      //  برای همین نامِ قدیمیِ `muted2` (متنِ کوچک در صفحه‌ها) به `textSecondary` می‌رود
      if (c.isDark) {
        for ((g, ground) in listOf("surface" to c.surface, "surfaceElevated" to c.surfaceElevated)) {
          need(theme, "textMuted/$g", c.textMuted, ground, 4.5f)
        }
      } else {
        need(theme, "textMuted/surface (درشت/راهنما)", c.textMuted, c.surface, 3f)
      }
      assertEquals(c.textSecondary, c.muted2)
    }
    assertTrue("زیرِ آستانه — $bad", bad.isEmpty())
  }

  @Test
  fun `سریِ نمودارِ زیرِ ۳ به ۱ روی کارتِ روز همیشه برچسب دارد — فهرست فقط کوتاه شود`() {
    //  «همیشه نامِ سری/راهنما/برچسب». پنج رنگِ همراه روی سفید زیرِ ۳:۱اند؛ رنگ می‌ماند
    //  ولی تنها حاملِ معنا نیست (برچسبِ عدد کنارِ ستون).
    val low = LightColors.chart.series.filter { contrast(it, LightColors.surface) < 3f }.map(::hex).toSet()
    assertEquals(setOf("#0EA5E9", "#F97362", "#F59E0B", "#14B8A6", "#84CC16"), low)
    for ((name, c) in palettes.filter { it.second.isDark }) {
      assertEquals(name, emptyList<Color>(), c.chart.series.filter { contrast(it, c.surface) < 3f })
    }
  }

  @Test
  fun `طرحِ متریال از توکن‌ها می‌آید`() {
    for ((_, c) in palettes) {
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
      //  سطح‌های متریال رنگِ برند نمی‌گیرند (بی تهِ رنگی روی پنجره و منو)
      assertEquals(c.surface, m.surfaceTint)
      assertEquals(c.surface, m.surfaceContainerHigh)
    }
    assertEquals(LightColors.button.danger.bg, materialScheme(LightColors).error)
    //  رنگ‌های قدیمیِ صفحه‌ها نامِ دیگرِ همان توکن‌اند
    with(LightColors) {
      assertEquals(background, bg); assertEquals(textPrimary, text); assertEquals(textSecondary, muted)
      assertEquals(borderCard, border); assertEquals(surfaceElevated, surface2); assertEquals(brandText, primary)
      assertEquals(surfaceSelected, primaryTint); assertEquals(status.delivered.fg, success); assertEquals(status.cancelled.fg, danger)
      assertEquals(status.awaitingApproval.fg, warning)
      assertEquals(backgroundSubtle, zoneNav); assertEquals(borderInput, fieldBorder)
    }
  }

  /*
   *  آزمونِ «بنفشِ پیشین هیچ‌جای پالت نیست» برداشته شد: THEME-V2 بنفش را عمداً برمی‌گرداند —
   *  رنگِ همراهِ #8B5CF6 و سرِ گرادیانِ «آسمان» (#7C3AED) و «غروب» (#4F46E5). قاعدهٔ «سبزآبی به‌جای بنفش» دیگر نیست.
   */

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
