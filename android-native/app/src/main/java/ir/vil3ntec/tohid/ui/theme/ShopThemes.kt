package ir.vil3ntec.tohid.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/*
 *  سه تمِ THEME-V2 (۱۴۰۵/۰۸/۰۲): «آسمان» (پیش‌فرض)، «اقیانوس» و «غروب» — هر کدام روشن و تیره.
 *  پایه آبی و سفید؛ کادرِ بزرگ گرادیانِ چندرنگ، جزئیات با هفت رنگِ همراه. رنگ‌های معنایی (موفق، خطر،
 *  هشدار، در انتظارِ تأیید) ثابت‌اند. عددها از فایلِ صاحب‌کار؛ مشتق‌ها (سطحِ ۲ِ دو تمِ دیگر، مرزها، شیشه)
 *  با `scratchpad/v3f/gen_themes.py` یک بار حساب و کنتراستشان سنجیده شده (`ThemeV2PaletteTest`).
 *  انحراف‌های ثبت‌شده:
 *   • متنِ کم‌رنگِ روز #5B6A80 به‌جای #64748B — #64748B روی زمینه‌های رنگیِ تازه زیرِ ۴٫۵:۱ است.
 *   • `heroScrim`: پردهٔ سیاهِ کم‌رنگ روی سرِ روشنِ گرادیانِ «اقیانوس» و «غروب» تا متنِ سفید خوانا بماند.
 *   • خطِ کادرِ ورودیِ روز #C8D2E0 به‌جای #CBD5E1 — #CBD5E1 روی سفید ۱٫۴۸:۱ است (کفِ ما ۱٫۵).
 *   • متنِ کم‌رنگ‌ترِ شب (`textMuted`) #8897AC به‌جای #7C8BA1 — در شب متنِ عادی است و باید ≥ ۴٫۵:۱ بماند.
 *   • زمینهٔ گزینهٔ فعالِ شب ۱۴٪ِ برند (نه ۱۸٪) تا متنِ برند و کم‌رنگ رویش ≥ ۴٫۵:۱ بماند.
 *   • «در انتظارِ تأیید» بنفش است (`status.awaitingApproval`، همان بنفشِ «نیازِ بررسی»)؛ هشدار کهربایی
 *     (`status.warning`). نشان‌ها: `PillTone.AWAITING` / `WARNING`، کیت: `V3Status.AWAITING` / `WARNING`.
 *   • `heroChipShade`: سایهٔ سیاهِ زیرِ شیشهٔ سفیدِ تراشه‌های کادرِ بزرگ تا برچسبِ ۱۲sp روی هر جای
 *     گرادیان ≥ ۴٫۵:۱ بماند (روز: آسمان و غروب ۱۵٪، اقیانوس ۳۰٪؛ شب لازم نیست).
 *  `PhoneV3Colors`، `PhoneV3Roles`، `PhoneV3Palette` و `PhoneV3Ui` همه از همین `ShopColors` می‌گیرند.
 */

/** هفت رنگِ همراه — به همین ترتیب: آسمانی، بنفش، مرجانی، کهربایی، نعنایی، صورتی، لیمویی */
object ThemeAccents {
  val SKY = Color(0xFF0EA5E9)
  val VIOLET = Color(0xFF8B5CF6)
  val CORAL = Color(0xFFF97362)
  val AMBER = Color(0xFFF59E0B)
  val MINT = Color(0xFF14B8A6)
  val PINK = Color(0xFFEC4899)
  val LIME = Color(0xFF84CC16)
  val ALL: List<Color> = listOf(SKY, VIOLET, CORAL, AMBER, MINT, PINK, LIME)
}

/** تمِ رنگیِ برنامه — جدا از روز/شب (`ThemeChoice`) */
enum class ShopTheme(val id: String) {
  SKY("sky"), OCEAN("ocean"), DUSK("dusk");

  companion object {
    fun of(id: String?): ShopTheme = entries.firstOrNull { it.id == id } ?: SKY
  }
}

/** `ShopColors` ِ یک تم در روز یا شب */
fun shopColors(theme: ShopTheme, dark: Boolean): ShopColors = when (theme) {
  ShopTheme.SKY -> if (dark) SkyDark else SkyLight
  ShopTheme.OCEAN -> if (dark) OceanDark else OceanLight
  ShopTheme.DUSK -> if (dark) DuskDark else DuskLight
}

/**
 *  تمِ انتخاب‌شده — فقط روی همین دستگاه (`tohid-theme` › `palette`)، همگام نمی‌شود، پیش‌فرض «آسمان».
 *  `mutableStateOf` است: با `set` همهٔ برنامه بی راه‌اندازیِ دوباره عوض می‌شود (`TohidTheme` گذرِ ۲۵۰ms دارد).
 *  `load` کنارِ `LocaleChoice.load` در `MainActivity` و `Main.kt` ِ کامپیوتر صدا زده می‌شود.
 */
object ThemeStore {
  private const val PREFS = "tohid-theme"
  private const val KEY = "palette"

  var theme by mutableStateOf(ShopTheme.SKY)
    private set

  fun load(context: Context) {
    val p = runCatching { context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }.getOrNull() ?: return
    theme = ShopTheme.of(p.getString(KEY, null))
  }

  fun set(context: Context, value: ShopTheme) {
    theme = value
    runCatching { context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, value.id).apply() }
  }

  /** فقط پیش‌نمایش و عکس (بی ذخیره) */
  fun preview(value: ShopTheme) { theme = value }
}

internal val SkyLight = ShopColors(
  background = Color(0xFFF5F8FF),
  backgroundSubtle = Color(0xFFECF0F8),
  surface = Color(0xFFFFFFFF),
  surfaceElevated = Color(0xFFEEF3FF),
  surfaceSelected = Color(0xFFE5ECFD),
  textPrimary = Color(0xFF0F172A),
  textSecondary = Color(0xFF5B6A80),
  textMuted = Color(0xFF7C8BA1),
  icon = Color(0xFF64748B),
  iconActive = Color(0xFF2563EB),
  borderCard = Color(0xFFD5DEE9),
  borderInput = Color(0xFFC8D2E0),
  divider = Color(0xFFEDF1F7),
  brandPrimary = Color(0xFF2563EB),
  brandDeep = Color(0xFF1D4ED8),
  brandText = Color(0xFF1D4ED8),
  link = Color(0xFF1D4ED8),
  focus = Color(0xFF2563EB),
  inverse = Color(0xFF0F172A),
  inverseRaised = Color(0xFF1E293B),
  onInverse = Color(0xFFF1F5F9),
  onInverseMuted = Color(0xFF94A3B8),
  button = ButtonColorSet(
    primary = ButtonTone(bg = Color(0xFF1D4ED8), text = Color(0xFFFFFFFF), border = Color(0xFF1D4ED8)),
    secondary = ButtonTone(bg = Color(0xFFFFFFFF), text = Color(0xFF1D4ED8), border = Color(0xFFB3C8F8)),
    neutral = ButtonTone(bg = Color(0xFFE8EEF8), text = Color(0xFF334155), border = Color(0xFFE8EEF8)),
    danger = ButtonTone(bg = Color(0xFFDC2626), text = Color(0xFFFFFFFF), border = Color(0xFFDC2626)),
    disabled = ButtonTone(bg = Color(0xFFE2E8F0), text = Color(0xFF94A3B8), border = Color(0xFFE2E8F0)),
  ),
  status = StatusColors(
    delivered = StatusTone(fg = Color(0xFF15803D), bg = Color(0xFFDCFCE7)),
    inDelivery = StatusTone(fg = Color(0xFF1D4ED8), bg = Color(0xFFDBEAFE)),
    awaitingApproval = StatusTone(fg = Color(0xFF6D28D9), bg = Color(0xFFEDE9FE)),
    cancelled = StatusTone(fg = Color(0xFFB91C1C), bg = Color(0xFFFEE2E2)),
    needsReview = StatusTone(fg = Color(0xFF6D28D9), bg = Color(0xFFEDE9FE)),
    preparing = StatusTone(fg = Color(0xFF0E7490), bg = Color(0xFFCFFAFE)),
    inactive = StatusTone(fg = Color(0xFF475569), bg = Color(0xFFE2E8F0)),
    warning = StatusTone(fg = Color(0xFF92400E), bg = Color(0xFFFEF3C7)),
  ),
  message = MessageColors(
    success = MessageTone(text = Color(0xFF166534), bg = Color(0xFFF0FDF4), icon = Color(0xFF16A34A), border = Color(0xFFBBF7D0)),
    info = MessageTone(text = Color(0xFF1E40AF), bg = Color(0xFFEFF6FF), icon = Color(0xFF2563EB), border = Color(0xFFBFDBFE)),
    warning = MessageTone(text = Color(0xFF92400E), bg = Color(0xFFFFFBEB), icon = Color(0xFFB45309), border = Color(0xFFFDE68A)),
    error = MessageTone(text = Color(0xFFB91C1C), bg = Color(0xFFFEF2F2), icon = Color(0xFFDC2626), border = Color(0xFFFECACA)),
  ),
  //  سری‌های نمودار به ترتیبِ رنگ‌های همراه (THEME-V2)
  chart = ChartColors(sales = Color(0xFF0EA5E9), comparison = Color(0xFF8B5CF6), costs = Color(0xFFF97362), discount = Color(0xFFF59E0B), drop = Color(0xFF14B8A6), stock = Color(0xFFEC4899), neutral = Color(0xFF84CC16), grid = Color(0xFFE2E8F0)),
  shadow = Color(0x520F172A),
  scrim = Color(0x990B1220),
  glow = Color(0x1F2563EB),
  sheen = Color(0x59FFFFFF),
  auroraOne = Color(0x4C2563EB),
  auroraTwo = Color(0x3D4F46E5),
  auroraThree = Color(0x337C3AED),
  heroGradient = listOf(Color(0xFF2563EB), Color(0xFF4F46E5), Color(0xFF7C3AED)),
  accents = ThemeAccents.ALL,
  brandOnInverse = Color(0xFF60A5FA),
  heroScrim = 0.04f,
  heroChipShade = 0.15f,
)

internal val SkyDark = ShopColors(
  background = Color(0xFF0B1020),
  backgroundSubtle = Color(0xFF070A15),
  surface = Color(0xFF121A2E),
  surfaceElevated = Color(0xFF1A2440),
  surfaceSelected = Color(0xFF1D2D4B),
  textPrimary = Color(0xFFE6ECFF),
  textSecondary = Color(0xFF94A3B8),
  textMuted = Color(0xFF8897AC),
  icon = Color(0xFFA3B2C8),
  iconActive = Color(0xFF60A5FA),
  borderCard = Color(0xFF2E3547),
  borderInput = Color(0xFF3D4354),
  divider = Color(0xFF252C3F),
  brandPrimary = Color(0xFF60A5FA),
  brandDeep = Color(0xFF1D4ED8),
  brandText = Color(0xFF60A5FA),
  link = Color(0xFF60A5FA),
  focus = Color(0xFF60A5FA),
  inverse = Color(0xFF060912),
  inverseRaised = Color(0xFF121A2E),
  onInverse = Color(0xFFE6ECFF),
  onInverseMuted = Color(0xFF94A3B8),
  button = ButtonColorSet(
    primary = ButtonTone(bg = Color(0xFF60A5FA), text = Color(0xFF0B1020), border = Color(0xFF60A5FA)),
    secondary = ButtonTone(bg = Color(0xFF121A2E), text = Color(0xFF60A5FA), border = Color(0xFF315280)),
    neutral = ButtonTone(bg = Color(0xFF1A2440), text = Color(0xFFE6ECFF), border = Color(0xFF1A2440)),
    danger = ButtonTone(bg = Color(0xFFDC2626), text = Color(0xFFFFFFFF), border = Color(0xFFDC2626)),
    disabled = ButtonTone(bg = Color(0xFF1E2538), text = Color(0xFF64748B), border = Color(0xFF252C3F)),
  ),
  status = StatusColors(
    delivered = StatusTone(fg = Color(0xFF86EFAC), bg = Color(0xFF123C2B)),
    inDelivery = StatusTone(fg = Color(0xFF93C5FD), bg = Color(0xFF172F57)),
    awaitingApproval = StatusTone(fg = Color(0xFFC4B5FD), bg = Color(0xFF2E2357)),
    cancelled = StatusTone(fg = Color(0xFFFCA5A5), bg = Color(0xFF451A1E)),
    needsReview = StatusTone(fg = Color(0xFFC4B5FD), bg = Color(0xFF2E2357)),
    preparing = StatusTone(fg = Color(0xFF67E8F9), bg = Color(0xFF0E3A45)),
    inactive = StatusTone(fg = Color(0xFFCBD5E1), bg = Color(0xFF273548)),
    warning = StatusTone(fg = Color(0xFFFCD34D), bg = Color(0xFF3D2E0E)),
  ),
  message = MessageColors(
    success = MessageTone(text = Color(0xFF86EFAC), bg = Color(0xFF0F2A1E), icon = Color(0xFF4ADE80), border = Color(0xFF1F5135)),
    info = MessageTone(text = Color(0xFF93C5FD), bg = Color(0xFF0F1E3A), icon = Color(0xFF60A5FA), border = Color(0xFF1E3A8A)),
    warning = MessageTone(text = Color(0xFFFCD34D), bg = Color(0xFF2B1F0B), icon = Color(0xFFFBBF24), border = Color(0xFF78350F)),
    error = MessageTone(text = Color(0xFFFCA5A5), bg = Color(0xFF2D1215), icon = Color(0xFFF87171), border = Color(0xFF7F1D1D)),
  ),
  //  سری‌های نمودار به ترتیبِ رنگ‌های همراه (THEME-V2)
  chart = ChartColors(sales = Color(0xFF0EA5E9), comparison = Color(0xFF8B5CF6), costs = Color(0xFFF97362), discount = Color(0xFFF59E0B), drop = Color(0xFF14B8A6), stock = Color(0xFFEC4899), neutral = Color(0xFF84CC16), grid = Color(0xFF252C3F)),
  shadow = Color(0x66000000),
  scrim = Color(0xCC020509),
  glow = Color(0x3360A5FA),
  sheen = Color(0x1FFFFFFF),
  auroraOne = Color(0x8C1E3A8A),
  auroraTwo = Color(0x61312E81),
  auroraThree = Color(0x574C1D95),
  heroGradient = listOf(Color(0xFF1E3A8A), Color(0xFF312E81), Color(0xFF4C1D95)),
  accents = ThemeAccents.ALL,
  brandOnInverse = Color(0xFF1D4ED8),
  heroScrim = 0.0f,
  heroChipShade = 0.0f,
)

internal val OceanLight = ShopColors(
  background = Color(0xFFF2F9FC),
  backgroundSubtle = Color(0xFFEAF0F6),
  surface = Color(0xFFFFFFFF),
  surfaceElevated = Color(0xFFEDF6FB),
  surfaceSelected = Color(0xFFE1F0F8),
  textPrimary = Color(0xFF0F172A),
  textSecondary = Color(0xFF5B6A80),
  textMuted = Color(0xFF7C8BA1),
  icon = Color(0xFF64748B),
  iconActive = Color(0xFF0284C7),
  borderCard = Color(0xFFD5DEE9),
  borderInput = Color(0xFFC8D2E0),
  divider = Color(0xFFEDF1F7),
  brandPrimary = Color(0xFF0284C7),
  brandDeep = Color(0xFF0369A1),
  brandText = Color(0xFF0369A1),
  link = Color(0xFF0369A1),
  focus = Color(0xFF0284C7),
  inverse = Color(0xFF0F172A),
  inverseRaised = Color(0xFF1E293B),
  onInverse = Color(0xFFF1F5F9),
  onInverseMuted = Color(0xFF94A3B8),
  button = ButtonColorSet(
    primary = ButtonTone(bg = Color(0xFF0369A1), text = Color(0xFFFFFFFF), border = Color(0xFF0369A1)),
    secondary = ButtonTone(bg = Color(0xFFFFFFFF), text = Color(0xFF0369A1), border = Color(0xFFA6D4EB)),
    neutral = ButtonTone(bg = Color(0xFFE8EEF8), text = Color(0xFF334155), border = Color(0xFFE8EEF8)),
    danger = ButtonTone(bg = Color(0xFFDC2626), text = Color(0xFFFFFFFF), border = Color(0xFFDC2626)),
    disabled = ButtonTone(bg = Color(0xFFE2E8F0), text = Color(0xFF94A3B8), border = Color(0xFFE2E8F0)),
  ),
  status = StatusColors(
    delivered = StatusTone(fg = Color(0xFF15803D), bg = Color(0xFFDCFCE7)),
    inDelivery = StatusTone(fg = Color(0xFF1D4ED8), bg = Color(0xFFDBEAFE)),
    awaitingApproval = StatusTone(fg = Color(0xFF6D28D9), bg = Color(0xFFEDE9FE)),
    cancelled = StatusTone(fg = Color(0xFFB91C1C), bg = Color(0xFFFEE2E2)),
    needsReview = StatusTone(fg = Color(0xFF6D28D9), bg = Color(0xFFEDE9FE)),
    preparing = StatusTone(fg = Color(0xFF0E7490), bg = Color(0xFFCFFAFE)),
    inactive = StatusTone(fg = Color(0xFF475569), bg = Color(0xFFE2E8F0)),
    warning = StatusTone(fg = Color(0xFF92400E), bg = Color(0xFFFEF3C7)),
  ),
  message = MessageColors(
    success = MessageTone(text = Color(0xFF166534), bg = Color(0xFFF0FDF4), icon = Color(0xFF16A34A), border = Color(0xFFBBF7D0)),
    info = MessageTone(text = Color(0xFF1E40AF), bg = Color(0xFFEFF6FF), icon = Color(0xFF2563EB), border = Color(0xFFBFDBFE)),
    warning = MessageTone(text = Color(0xFF92400E), bg = Color(0xFFFFFBEB), icon = Color(0xFFB45309), border = Color(0xFFFDE68A)),
    error = MessageTone(text = Color(0xFFB91C1C), bg = Color(0xFFFEF2F2), icon = Color(0xFFDC2626), border = Color(0xFFFECACA)),
  ),
  //  سری‌های نمودار به ترتیبِ رنگ‌های همراه (THEME-V2)
  chart = ChartColors(sales = Color(0xFF0EA5E9), comparison = Color(0xFF8B5CF6), costs = Color(0xFFF97362), discount = Color(0xFFF59E0B), drop = Color(0xFF14B8A6), stock = Color(0xFFEC4899), neutral = Color(0xFF84CC16), grid = Color(0xFFE2E8F0)),
  shadow = Color(0x520F172A),
  scrim = Color(0x990B1220),
  glow = Color(0x1F0284C7),
  sheen = Color(0x59FFFFFF),
  auroraOne = Color(0x4C0369A1),
  auroraTwo = Color(0x3D0EA5E9),
  auroraThree = Color(0x3322D3EE),
  heroGradient = listOf(Color(0xFF0369A1), Color(0xFF0EA5E9), Color(0xFF22D3EE)),
  accents = ThemeAccents.ALL,
  brandOnInverse = Color(0xFF38BDF8),
  heroScrim = 0.3f,
  heroChipShade = 0.3f,
)

internal val OceanDark = ShopColors(
  background = Color(0xFF07141C),
  backgroundSubtle = Color(0xFF050D12),
  surface = Color(0xFF0F2230),
  surfaceElevated = Color(0xFF1D2F3C),
  surfaceSelected = Color(0xFF15384C),
  textPrimary = Color(0xFFE6ECFF),
  textSecondary = Color(0xFF94A3B8),
  textMuted = Color(0xFF8897AC),
  icon = Color(0xFFA3B2C8),
  iconActive = Color(0xFF38BDF8),
  borderCard = Color(0xFF2C3D49),
  borderInput = Color(0xFF3A4A55),
  divider = Color(0xFF223441),
  brandPrimary = Color(0xFF38BDF8),
  brandDeep = Color(0xFF0369A1),
  brandText = Color(0xFF38BDF8),
  link = Color(0xFF38BDF8),
  focus = Color(0xFF38BDF8),
  inverse = Color(0xFF040B0F),
  inverseRaised = Color(0xFF0F2230),
  onInverse = Color(0xFFE6ECFF),
  onInverseMuted = Color(0xFF94A3B8),
  button = ButtonColorSet(
    primary = ButtonTone(bg = Color(0xFF38BDF8), text = Color(0xFF07141C), border = Color(0xFF38BDF8)),
    secondary = ButtonTone(bg = Color(0xFF0F2230), text = Color(0xFF38BDF8), border = Color(0xFF1F6080)),
    neutral = ButtonTone(bg = Color(0xFF1D2F3C), text = Color(0xFFE6ECFF), border = Color(0xFF1D2F3C)),
    danger = ButtonTone(bg = Color(0xFFDC2626), text = Color(0xFFFFFFFF), border = Color(0xFFDC2626)),
    disabled = ButtonTone(bg = Color(0xFF1B2D3A), text = Color(0xFF64748B), border = Color(0xFF223441)),
  ),
  status = StatusColors(
    delivered = StatusTone(fg = Color(0xFF86EFAC), bg = Color(0xFF123C2B)),
    inDelivery = StatusTone(fg = Color(0xFF93C5FD), bg = Color(0xFF172F57)),
    awaitingApproval = StatusTone(fg = Color(0xFFC4B5FD), bg = Color(0xFF2E2357)),
    cancelled = StatusTone(fg = Color(0xFFFCA5A5), bg = Color(0xFF451A1E)),
    needsReview = StatusTone(fg = Color(0xFFC4B5FD), bg = Color(0xFF2E2357)),
    preparing = StatusTone(fg = Color(0xFF67E8F9), bg = Color(0xFF0E3A45)),
    inactive = StatusTone(fg = Color(0xFFCBD5E1), bg = Color(0xFF273548)),
    warning = StatusTone(fg = Color(0xFFFCD34D), bg = Color(0xFF3D2E0E)),
  ),
  message = MessageColors(
    success = MessageTone(text = Color(0xFF86EFAC), bg = Color(0xFF0F2A1E), icon = Color(0xFF4ADE80), border = Color(0xFF1F5135)),
    info = MessageTone(text = Color(0xFF93C5FD), bg = Color(0xFF0F1E3A), icon = Color(0xFF60A5FA), border = Color(0xFF1E3A8A)),
    warning = MessageTone(text = Color(0xFFFCD34D), bg = Color(0xFF2B1F0B), icon = Color(0xFFFBBF24), border = Color(0xFF78350F)),
    error = MessageTone(text = Color(0xFFFCA5A5), bg = Color(0xFF2D1215), icon = Color(0xFFF87171), border = Color(0xFF7F1D1D)),
  ),
  //  سری‌های نمودار به ترتیبِ رنگ‌های همراه (THEME-V2)
  chart = ChartColors(sales = Color(0xFF0EA5E9), comparison = Color(0xFF8B5CF6), costs = Color(0xFFF97362), discount = Color(0xFFF59E0B), drop = Color(0xFF14B8A6), stock = Color(0xFFEC4899), neutral = Color(0xFF84CC16), grid = Color(0xFF223441)),
  shadow = Color(0x66000000),
  scrim = Color(0xCC020509),
  glow = Color(0x3338BDF8),
  sheen = Color(0x1FFFFFFF),
  auroraOne = Color(0x8C0C4A6E),
  auroraTwo = Color(0x61075985),
  auroraThree = Color(0x57155E75),
  heroGradient = listOf(Color(0xFF0C4A6E), Color(0xFF075985), Color(0xFF155E75)),
  accents = ThemeAccents.ALL,
  brandOnInverse = Color(0xFF0369A1),
  heroScrim = 0.0f,
  heroChipShade = 0.0f,
)

internal val DuskLight = ShopColors(
  background = Color(0xFFF7F6FF),
  backgroundSubtle = Color(0xFFECEFF8),
  surface = Color(0xFFFFFFFF),
  surfaceElevated = Color(0xFFF3F2FD),
  surfaceSelected = Color(0xFFEAE9FC),
  textPrimary = Color(0xFF0F172A),
  textSecondary = Color(0xFF5B6A80),
  textMuted = Color(0xFF7C8BA1),
  icon = Color(0xFF64748B),
  iconActive = Color(0xFF4F46E5),
  borderCard = Color(0xFFD5DEE9),
  borderInput = Color(0xFFC8D2E0),
  divider = Color(0xFFEDF1F7),
  brandPrimary = Color(0xFF4F46E5),
  brandDeep = Color(0xFF4338CA),
  brandText = Color(0xFF4338CA),
  link = Color(0xFF4338CA),
  focus = Color(0xFF4F46E5),
  inverse = Color(0xFF0F172A),
  inverseRaised = Color(0xFF1E293B),
  onInverse = Color(0xFFF1F5F9),
  onInverseMuted = Color(0xFF94A3B8),
  button = ButtonColorSet(
    primary = ButtonTone(bg = Color(0xFF4338CA), text = Color(0xFFFFFFFF), border = Color(0xFF4338CA)),
    secondary = ButtonTone(bg = Color(0xFFFFFFFF), text = Color(0xFF4338CA), border = Color(0xFFC1BEF6)),
    neutral = ButtonTone(bg = Color(0xFFE8EEF8), text = Color(0xFF334155), border = Color(0xFFE8EEF8)),
    danger = ButtonTone(bg = Color(0xFFDC2626), text = Color(0xFFFFFFFF), border = Color(0xFFDC2626)),
    disabled = ButtonTone(bg = Color(0xFFE2E8F0), text = Color(0xFF94A3B8), border = Color(0xFFE2E8F0)),
  ),
  status = StatusColors(
    delivered = StatusTone(fg = Color(0xFF15803D), bg = Color(0xFFDCFCE7)),
    inDelivery = StatusTone(fg = Color(0xFF1D4ED8), bg = Color(0xFFDBEAFE)),
    awaitingApproval = StatusTone(fg = Color(0xFF6D28D9), bg = Color(0xFFEDE9FE)),
    cancelled = StatusTone(fg = Color(0xFFB91C1C), bg = Color(0xFFFEE2E2)),
    needsReview = StatusTone(fg = Color(0xFF6D28D9), bg = Color(0xFFEDE9FE)),
    preparing = StatusTone(fg = Color(0xFF0E7490), bg = Color(0xFFCFFAFE)),
    inactive = StatusTone(fg = Color(0xFF475569), bg = Color(0xFFE2E8F0)),
    warning = StatusTone(fg = Color(0xFF92400E), bg = Color(0xFFFEF3C7)),
  ),
  message = MessageColors(
    success = MessageTone(text = Color(0xFF166534), bg = Color(0xFFF0FDF4), icon = Color(0xFF16A34A), border = Color(0xFFBBF7D0)),
    info = MessageTone(text = Color(0xFF1E40AF), bg = Color(0xFFEFF6FF), icon = Color(0xFF2563EB), border = Color(0xFFBFDBFE)),
    warning = MessageTone(text = Color(0xFF92400E), bg = Color(0xFFFFFBEB), icon = Color(0xFFB45309), border = Color(0xFFFDE68A)),
    error = MessageTone(text = Color(0xFFB91C1C), bg = Color(0xFFFEF2F2), icon = Color(0xFFDC2626), border = Color(0xFFFECACA)),
  ),
  //  سری‌های نمودار به ترتیبِ رنگ‌های همراه (THEME-V2)
  chart = ChartColors(sales = Color(0xFF0EA5E9), comparison = Color(0xFF8B5CF6), costs = Color(0xFFF97362), discount = Color(0xFFF59E0B), drop = Color(0xFF14B8A6), stock = Color(0xFFEC4899), neutral = Color(0xFF84CC16), grid = Color(0xFFE2E8F0)),
  shadow = Color(0x520F172A),
  scrim = Color(0x990B1220),
  glow = Color(0x1F4F46E5),
  sheen = Color(0x59FFFFFF),
  auroraOne = Color(0x4C4F46E5),
  auroraTwo = Color(0x3DDB2777),
  auroraThree = Color(0x33F97316),
  heroGradient = listOf(Color(0xFF4F46E5), Color(0xFFDB2777), Color(0xFFF97316)),
  accents = ThemeAccents.ALL,
  brandOnInverse = Color(0xFF818CF8),
  heroScrim = 0.3f,
  heroChipShade = 0.15f,
)

internal val DuskDark = ShopColors(
  background = Color(0xFF100E1F),
  backgroundSubtle = Color(0xFF0A0914),
  surface = Color(0xFF1B1830),
  surfaceElevated = Color(0xFF29263C),
  surfaceSelected = Color(0xFF29284C),
  textPrimary = Color(0xFFE6ECFF),
  textSecondary = Color(0xFF94A3B8),
  textMuted = Color(0xFF8897AC),
  icon = Color(0xFFA3B2C8),
  iconActive = Color(0xFF818CF8),
  borderCard = Color(0xFF363449),
  borderInput = Color(0xFF444255),
  divider = Color(0xFF2D2A41),
  brandPrimary = Color(0xFF818CF8),
  brandDeep = Color(0xFF4338CA),
  brandText = Color(0xFF818CF8),
  link = Color(0xFF818CF8),
  focus = Color(0xFF818CF8),
  inverse = Color(0xFF090811),
  inverseRaised = Color(0xFF1B1830),
  onInverse = Color(0xFFE6ECFF),
  onInverseMuted = Color(0xFF94A3B8),
  button = ButtonColorSet(
    primary = ButtonTone(bg = Color(0xFF818CF8), text = Color(0xFF100E1F), border = Color(0xFF818CF8)),
    secondary = ButtonTone(bg = Color(0xFF1B1830), text = Color(0xFF818CF8), border = Color(0xFF444680)),
    neutral = ButtonTone(bg = Color(0xFF29263C), text = Color(0xFFE6ECFF), border = Color(0xFF29263C)),
    danger = ButtonTone(bg = Color(0xFFDC2626), text = Color(0xFFFFFFFF), border = Color(0xFFDC2626)),
    disabled = ButtonTone(bg = Color(0xFF26243A), text = Color(0xFF64748B), border = Color(0xFF2D2A41)),
  ),
  status = StatusColors(
    delivered = StatusTone(fg = Color(0xFF86EFAC), bg = Color(0xFF123C2B)),
    inDelivery = StatusTone(fg = Color(0xFF93C5FD), bg = Color(0xFF172F57)),
    awaitingApproval = StatusTone(fg = Color(0xFFC4B5FD), bg = Color(0xFF2E2357)),
    cancelled = StatusTone(fg = Color(0xFFFCA5A5), bg = Color(0xFF451A1E)),
    needsReview = StatusTone(fg = Color(0xFFC4B5FD), bg = Color(0xFF2E2357)),
    preparing = StatusTone(fg = Color(0xFF67E8F9), bg = Color(0xFF0E3A45)),
    inactive = StatusTone(fg = Color(0xFFCBD5E1), bg = Color(0xFF273548)),
    warning = StatusTone(fg = Color(0xFFFCD34D), bg = Color(0xFF3D2E0E)),
  ),
  message = MessageColors(
    success = MessageTone(text = Color(0xFF86EFAC), bg = Color(0xFF0F2A1E), icon = Color(0xFF4ADE80), border = Color(0xFF1F5135)),
    info = MessageTone(text = Color(0xFF93C5FD), bg = Color(0xFF0F1E3A), icon = Color(0xFF60A5FA), border = Color(0xFF1E3A8A)),
    warning = MessageTone(text = Color(0xFFFCD34D), bg = Color(0xFF2B1F0B), icon = Color(0xFFFBBF24), border = Color(0xFF78350F)),
    error = MessageTone(text = Color(0xFFFCA5A5), bg = Color(0xFF2D1215), icon = Color(0xFFF87171), border = Color(0xFF7F1D1D)),
  ),
  //  سری‌های نمودار به ترتیبِ رنگ‌های همراه (THEME-V2)
  chart = ChartColors(sales = Color(0xFF0EA5E9), comparison = Color(0xFF8B5CF6), costs = Color(0xFFF97362), discount = Color(0xFFF59E0B), drop = Color(0xFF14B8A6), stock = Color(0xFFEC4899), neutral = Color(0xFF84CC16), grid = Color(0xFF2D2A41)),
  shadow = Color(0x66000000),
  scrim = Color(0xCC020509),
  glow = Color(0x33818CF8),
  sheen = Color(0x1FFFFFFF),
  auroraOne = Color(0x8C312E81),
  auroraTwo = Color(0x61831843),
  auroraThree = Color(0x579A3412),
  heroGradient = listOf(Color(0xFF312E81), Color(0xFF831843), Color(0xFF9A3412)),
  accents = ThemeAccents.ALL,
  brandOnInverse = Color(0xFF4338CA),
  heroScrim = 0.0f,
  heroChipShade = 0.0f,
)
