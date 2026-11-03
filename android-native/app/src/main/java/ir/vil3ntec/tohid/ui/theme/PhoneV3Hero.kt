package ir.vil3ntec.tohid.ui.theme

import androidx.compose.ui.graphics.Color

/**
 *  رنگ‌های کادرِ بالای خانه (HOME-V3، تصویرِ انتخابیِ صاحب‌کار) — **فقط مشتق** از `ShopColors` ِ تمِ جاری، بی HEX ِ
 *  تازه؛ پس «روشن»، «تیره» و «خودکار» (`ThemeChoice`) و هر سه تم را خودبه‌خود دنبال می‌کند.
 *
 *  • شب: شیشهٔ سیاهِ عمیق (`inverseRaised` ⇐ `inverse`)، متنِ روشن، برقِ نرمِ سفید روی خط و لبه — همان تصویر.
 *  • روز: همان چیدمان روی سطحِ روشن (`surface` ⇐ `surfaceElevated`)، متنِ تیره و قرصِ تیره — نه کادرِ تیره روی صفحهٔ روشن.
 *  • سبز و سرخِ آمار در هر دو همان رنگِ معناییِ وضعیت‌اند (`status.delivered`/`status.cancelled`)؛ رنگ تنها نیست (پیکان هم هست).
 */
data class HeroColors(
  val top: Color,
  val bottom: Color,
  /** برقِ نرمِ بالای کادر */
  val sheen: Color,
  val edge: Color,
  val shadow: Color,
  val ink: Color,
  val muted: Color,
  /** برچسب‌های کم‌اهمیتِ محور */
  val faint: Color,
  /** شیارِ کنترلِ بازه، دکمهٔ گرد و نقطه‌های صفحه */
  val track: Color,
  /** قرصِ گزینهٔ انتخاب‌شده، نشانِ «زنده» و برچسبِ عددِ نمودار */
  val pill: Color,
  val onPill: Color,
  val line: Color,
  val fillTop: Color,
  val glow: Color,
  val grid: Color,
  /** کادرِ آمارِ پایین */
  val tile: Color,
  val tileEdge: Color,
  val up: Color,
  val upSoft: Color,
  val down: Color,
  val downSoft: Color,
  /** نقطهٔ نشانِ همگام‌سازی */
  val live: Color,
  val wait: Color,
  val error: Color,
  val idle: Color,
  val dark: Boolean,
) {
  companion object {
    fun from(s: ShopColors): HeroColors = if (s.isDark) {
      val white = Color.White
      HeroColors(
        top = s.inverseRaised,
        bottom = s.inverse,
        sheen = white.copy(alpha = 0.07f),
        edge = white.copy(alpha = 0.09f),
        shadow = Color.Black.copy(alpha = 0.55f),
        ink = s.onInverse,
        muted = s.onInverseMuted,
        faint = s.onInverseMuted.copy(alpha = 0.7f),
        track = white.copy(alpha = 0.08f),
        pill = s.onInverse,
        onPill = s.inverse,
        line = s.onInverse,
        fillTop = white.copy(alpha = 0.22f),
        glow = white.copy(alpha = 0.13f),
        grid = white.copy(alpha = 0.07f),
        tile = white.copy(alpha = 0.05f),
        tileEdge = white.copy(alpha = 0.07f),
        up = s.status.delivered.fg,
        upSoft = white.copy(alpha = 0.08f),
        down = s.status.cancelled.fg,
        downSoft = white.copy(alpha = 0.08f),
        live = s.message.success.icon,
        wait = s.status.warning.fg,
        error = s.message.error.icon,
        idle = s.onInverseMuted,
        dark = true,
      )
    } else {
      HeroColors(
        top = s.surface,
        bottom = s.surfaceElevated,
        sheen = s.brandPrimary.copy(alpha = 0.06f),
        edge = s.borderCard,
        shadow = s.shadow,
        ink = s.textPrimary,
        muted = s.textSecondary,
        faint = s.textSecondary.copy(alpha = 0.8f),
        track = s.backgroundSubtle,
        pill = s.textPrimary,
        onPill = s.surface,
        line = s.textPrimary,
        fillTop = s.textPrimary.copy(alpha = 0.10f),
        glow = s.textPrimary.copy(alpha = 0.06f),
        grid = s.divider,
        tile = s.surface,
        tileEdge = s.divider,
        up = s.status.delivered.fg,
        upSoft = s.status.delivered.bg,
        down = s.status.cancelled.fg,
        downSoft = s.status.cancelled.bg,
        live = s.message.success.icon,
        wait = s.status.warning.fg,
        error = s.status.cancelled.fg,
        idle = s.textMuted,
        dark = false,
      )
    }
  }
}
