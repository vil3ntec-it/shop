package ir.vil3ntec.tohid.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/*
 *  تم‌های برنامه — ۶۹ p3 (صاحب‌کار ۱۴۰۵/۰۸/۲۴: «تمامِ بخش‌ها رو می‌خوام درست و اصولی، و خیلی جذاب. از رنگ‌های مزخرف توی تم و رنگ
 *  و بک‌گراندِ دارک‌مود … تم‌هایی که به دکمه‌ها داده شده — همه چی افتضاحه»؛ پیش‌تر: «رنگ صورتی کار نگیر … رنگ‌های برند بزار، لوکس»).
 *
 *  **چهار تمِ گزیده، همه از پالتِ برند** (`SectionColors`)، هر کدام روز و شب (`ShopTheme`):
 *   • «آسمان» (پیش‌فرض) — آبیِ برند؛ روزِ آن همان THEME-V3 ِ پسندیده و گرادیانِ آبی⇐بنفشِ کادرِ خانه دست نخورده؛
 *   • «سرمه‌ای» — سرمه‌ایِ عمیق با درخششِ طلایی؛
 *   • «عاجی» — خنثی‌های گرمِ عاج و دکمهٔ مفرغی/طلایی؛
 *   • «گرافیت» — زغالیِ سرد و آرام.
 *  «اقیانوس» (فیروزه‌ایِ تند) و «غروب» (صورتی/نارنجی) برداشته شدند؛ شناسهٔ ذخیره‌شده‌شان به نزدیک‌ترین تمِ مانده می‌رود و همان لحظه
 *  بازنویسی می‌شود (`ShopTheme.LEGACY`، `ThemeStore.load`، `ThemeMigrationTest`).
 *
 *  **شب** (هر چهار تم یک ساختار): زمینهٔ نزدیک‌به‌سیاهِ هم‌خانوادهٔ تم و سه پلهٔ بلندی — زمینه ⇐ کارت/نوار/برگه ⇐ سطحِ درونی/کادرِ
 *  ورودی/دکمهٔ خنثی — هر پله دست‌کم ۱٫۱۲:۱ روشن‌تر؛ خطِ مویی کم‌رنگ؛ متنِ اصلی AAA (≥ ۷:۱) روی زمینه و کارت، متنِ دوم و کم‌رنگ AA.
 *  **روز**: زمینهٔ روشنِ تمیز، کارتِ سفید با خطِ مویی و سایهٔ نرم، همان ساختار.
 *  **دکمه‌ها — یک سلسله‌مراتب در هر تم** (`buttons`): اصلی = پُرِ برند با نوشتهٔ سفید (روز و شب؛ دیگر آبیِ کم‌رنگ با نوشتهٔ تیره نیست)،
 *  دوم = تهِ‌رنگِ برند بی خط، خنثی = تهِ‌رنگِ خاکستریِ همان تم، خطر = عقیقِ برند با نوشتهٔ سفید، غیرفعال = خاکستریِ آرام؛ اندازه‌ها در
 *  `V3ButtonSpec`. رنگ‌های معنایی (وضعیت‌ها و پیام‌ها) در هر چهار تم یکی‌اند (`StatusDay/Night`، `MessageDay/Night`).
 *  همهٔ عددها با `scratchpad/p3/design.py` حساب و سنجیده شد؛ قاعده‌ها در `ThemeSetContrastTest`، `ButtonTokensTest`،
 *  `Vill3nPaletteTest`، `ThemeV2PaletteTest` و `LuxPaletteGuardTest`.
 *  انحراف‌های ثبت‌شدهٔ پیشین هم‌چنان: متنِ کم‌رنگِ روزِ «آسمان» #5B6A80⇐#526075 و خطِ کادرِ ورودی #C2CDDC (کنتراست)؛ `heroScrim` و
 *  `heroChipShade` فقط جایی که گرادیان برای متنِ سفید روشن است.
 *  `PhoneV3Colors`، `PhoneV3Roles`، `PhoneV3Palette` و `PhoneV3Ui` همه از همین `ShopColors` می‌گیرند.
 */

/**
 *  هفت رنگِ همراهِ تم (`ShopColors.accents`) به ترتیبِ `SectionColors.ORDER` — همان جاهایی که پرونده‌های قفل‌شدهٔ خانه می‌خوانند
 *  (۶۸c، پالتِ لوکسِ برند؛ صورتی و لیمویی دیگر نیستند):
 *   • جاهای ۱ تا ۴ و ۶ نشانهٔ همان خانواده‌اند (`SectionTone.glyph`، روز روی سفید ≥ ۳:۱، شب روی کارتِ شب ≥ ۶:۱): میان‌برهای خانه
 *     (`HomeAccents`: تاریخچه نیلی، سفارش‌ها نفتی، ورودِ کالا زر، دریافت زمردی) همان رنگِ بخش‌ها — در هر چهار تم یکی؛
 *   • جاهای ۰ و ۵ فقط دو درخششِ نرمِ کادرِ گرادیانیِ خانه‌اند (`V3HeroCard`، ۱۸٪) و از جدولِ بخش‌ها **جدا** — هر تم دو درخششِ خودش
 *     (`of`): «آسمان» همان آسمانیِ پیشین ([HERO_GLOW_TOP]) و بنفشِ نرمِ برند ([HERO_GLOW_BOTTOM])؛ «سرمه‌ای» و «گرافیت» طلای برند،
 *     «عاجی» طلای روشن و مفرغ.
 *  نشانهٔ کاشی‌ها (`PhoneV3Palette.glyph`) مستقیم از `SectionColors` می‌خواند، نه از این فهرست. سری‌های نمودار: [chart].
 */
object ThemeAccents {
  /** درخششِ بالای کادرِ گرادیانیِ خانهٔ «آسمان» — همان آسمانیِ پیش از ۶۸c */
  val HERO_GLOW_TOP = Color(0xFF0EA5E9)
  /** درخششِ پایینِ همان کادر — بنفشِ نرمِ برند، جای صورتیِ پیشین (#EC4899) */
  val HERO_GLOW_BOTTOM = Color(0xFF8B5CF6)

  /** هفت رنگِ همراه با دو درخششِ کادرِ خانهٔ یک تم (جاهای ۰ و ۵) */
  fun of(dark: Boolean, glowTop: Color = HERO_GLOW_TOP, glowBottom: Color = HERO_GLOW_BOTTOM): List<Color> =
    SectionColors.ORDER.map { SectionColors.glyph(it, dark) }.toMutableList().also { it[0] = glowTop; it[5] = glowBottom }

  val DAY: List<Color> = of(dark = false)
  val NIGHT: List<Color> = of(dark = true)
  /** پیش‌فرضِ `ShopColors.accents` (روز) */
  val ALL: List<Color> get() = DAY

  /** جاهای مشترکِ هر چهار تم (همه جز دو درخششِ کادر) */
  val SHARED_SLOTS: List<Int> = listOf(1, 2, 3, 4, 6)

  /** سری‌های نمودار (§۱۰): همان رنگ‌های همراه به ترتیبِ معنا — هزینه عقیق (`SectionColors.CHART`) */
  fun chart(dark: Boolean, grid: Color): ChartColors = SectionColors.CHART.map { SectionColors.glyph(it, dark) }.let { a ->
    ChartColors(sales = a[0], comparison = a[1], costs = a[2], discount = a[3], drop = a[4], stock = a[5], neutral = a[6], grid = grid)
  }
}

/** تمِ رنگیِ برنامه — جدا از روز/شب (`ThemeChoice`) */
enum class ShopTheme(val id: String) {
  SKY("sky"), NAVY("navy"), IVORY("ivory"), GRAPHITE("graphite");

  companion object {
    /**
     *  تم‌های برداشته‌شده ⇐ نزدیک‌ترین تمِ مانده (کم‌ترین ΔE ِ رنگِ برند و دکمهٔ اصلیِ روز، `ThemeMigrationTest`):
     *  «اقیانوس» ⇐ «سرمه‌ای»، «غروب» (برندِ نیلی) ⇐ «آسمان».
     */
    val LEGACY: Map<String, ShopTheme> = mapOf("ocean" to NAVY, "dusk" to SKY)

    /** شناسهٔ ذخیره‌شده ⇐ تم؛ شناسهٔ کهنه ⇐ جانشینش، ناشناخته یا تهی ⇐ «آسمان» — هرگز خطا نه */
    fun of(id: String?): ShopTheme = entries.firstOrNull { it.id == id } ?: LEGACY[id] ?: SKY

    /** شناسهٔ ذخیره‌شده‌ای که باید بازنویسی شود (کهنه یا ناشناخته) ⇐ شناسهٔ تازه؛ درست یا تهی ⇐ null */
    fun migrated(stored: String?): String? = stored?.let { s -> of(s).id.takeIf { it != s } }
  }
}

/** `ShopColors` ِ یک تم در روز یا شب */
fun shopColors(theme: ShopTheme, dark: Boolean): ShopColors = when (theme) {
  ShopTheme.SKY -> if (dark) SkyDark else SkyLight
  ShopTheme.NAVY -> if (dark) NavyDark else NavyLight
  ShopTheme.IVORY -> if (dark) IvoryDark else IvoryLight
  ShopTheme.GRAPHITE -> if (dark) GraphiteDark else GraphiteLight
}

/**
 *  تمِ انتخاب‌شده — فقط روی همین دستگاه (`tohid-theme` › `palette`)، همگام نمی‌شود، پیش‌فرض «آسمان».
 *  `mutableStateOf` است: با `set` همهٔ برنامه بی راه‌اندازیِ دوباره عوض می‌شود (`TohidTheme` گذرِ ۲۵۰ms دارد).
 *  `load` کنارِ `LocaleChoice.load` در `MainActivity` و `Main.kt` ِ کامپیوتر صدا زده می‌شود و شناسهٔ تمِ برداشته‌شده را همان‌جا به
 *  جانشینش بازنویسی می‌کند (`ShopTheme.migrated`).
 */
object ThemeStore {
  private const val PREFS = "tohid-theme"
  private const val KEY = "palette"

  var theme by mutableStateOf(ShopTheme.SKY)
    private set

  fun load(context: Context) {
    val p = runCatching { context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }.getOrNull() ?: return
    val stored = runCatching { p.getString(KEY, null) }.getOrNull()
    theme = ShopTheme.of(stored)
    ShopTheme.migrated(stored)?.let { id -> runCatching { p.edit().putString(KEY, id).apply() } }
  }

  fun set(context: Context, value: ShopTheme) {
    theme = value
    runCatching { context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, value.id).apply() }
  }

  /** فقط پیش‌نمایش و عکس (بی ذخیره) */
  fun preview(value: ShopTheme) { theme = value }
}

/** پنج گونهٔ دکمه (بی خطِ جدا: خطِ دور همان زمینه — دکمهٔ دوم تهِ‌رنگ است، نه قابِ خطی) */
private fun buttons(primary: Pair<Color, Color>, secondary: Pair<Color, Color>, neutral: Pair<Color, Color>, danger: Pair<Color, Color>, disabled: Pair<Color, Color>) =
  ButtonColorSet(
    primary = ButtonTone(bg = primary.first, text = primary.second, border = primary.first),
    secondary = ButtonTone(bg = secondary.first, text = secondary.second, border = secondary.first),
    neutral = ButtonTone(bg = neutral.first, text = neutral.second, border = neutral.first),
    danger = ButtonTone(bg = danger.first, text = danger.second, border = danger.first),
    disabled = ButtonTone(bg = disabled.first, text = disabled.second, border = disabled.first),
  )

/** رنگ‌های معنایی — در هر چهار تم یکی (THEME-V2 «رنگ‌های معنایی ثابت‌اند»): «در انتظارِ تأیید» بنفش، هشدار کهربایی */
internal val StatusDay = StatusColors(
  delivered = StatusTone(fg = Color(0xFF15803D), bg = Color(0xFFDCFCE7)),
  inDelivery = StatusTone(fg = Color(0xFF1D4ED8), bg = Color(0xFFDBEAFE)),
  awaitingApproval = StatusTone(fg = Color(0xFF6D28D9), bg = Color(0xFFEDE9FE)),
  cancelled = StatusTone(fg = Color(0xFFB91C1C), bg = Color(0xFFFEE2E2)),
  needsReview = StatusTone(fg = Color(0xFF6D28D9), bg = Color(0xFFEDE9FE)),
  preparing = StatusTone(fg = Color(0xFF0E7490), bg = Color(0xFFCFFAFE)),
  inactive = StatusTone(fg = Color(0xFF475569), bg = Color(0xFFE2E8F0)),
  warning = StatusTone(fg = Color(0xFF92400E), bg = Color(0xFFFEF3C7)),
)

internal val StatusNight = StatusColors(
  delivered = StatusTone(fg = Color(0xFF86EFAC), bg = Color(0xFF123C2B)),
  inDelivery = StatusTone(fg = Color(0xFF93C5FD), bg = Color(0xFF172F57)),
  awaitingApproval = StatusTone(fg = Color(0xFFC4B5FD), bg = Color(0xFF2E2357)),
  cancelled = StatusTone(fg = Color(0xFFFCA5A5), bg = Color(0xFF451A1E)),
  needsReview = StatusTone(fg = Color(0xFFC4B5FD), bg = Color(0xFF2E2357)),
  preparing = StatusTone(fg = Color(0xFF67E8F9), bg = Color(0xFF0E3A45)),
  inactive = StatusTone(fg = Color(0xFFCBD5E1), bg = Color(0xFF273548)),
  warning = StatusTone(fg = Color(0xFFFCD34D), bg = Color(0xFF3D2E0E)),
)

internal val MessageDay = MessageColors(
  success = MessageTone(text = Color(0xFF166534), bg = Color(0xFFF0FDF4), icon = Color(0xFF16A34A), border = Color(0xFFBBF7D0)),
  info = MessageTone(text = Color(0xFF1E40AF), bg = Color(0xFFEFF6FF), icon = Color(0xFF2563EB), border = Color(0xFFBFDBFE)),
  warning = MessageTone(text = Color(0xFF92400E), bg = Color(0xFFFFFBEB), icon = Color(0xFFB45309), border = Color(0xFFFDE68A)),
  error = MessageTone(text = Color(0xFFB91C1C), bg = Color(0xFFFEF2F2), icon = Color(0xFFDC2626), border = Color(0xFFFECACA)),
)

internal val MessageNight = MessageColors(
  success = MessageTone(text = Color(0xFF86EFAC), bg = Color(0xFF0F2A1E), icon = Color(0xFF4ADE80), border = Color(0xFF1F5135)),
  info = MessageTone(text = Color(0xFF93C5FD), bg = Color(0xFF0F1E3A), icon = Color(0xFF60A5FA), border = Color(0xFF1E3A8A)),
  warning = MessageTone(text = Color(0xFFFCD34D), bg = Color(0xFF2B1F0B), icon = Color(0xFFFBBF24), border = Color(0xFF78350F)),
  error = MessageTone(text = Color(0xFFFCA5A5), bg = Color(0xFF2D1215), icon = Color(0xFFF87171), border = Color(0xFF7F1D1D)),
)

internal val SkyLight = ShopColors(
  background = Color(0xFFF4F7FC),
  backgroundSubtle = Color(0xFFEAEFF7),
  surface = Color(0xFFFFFFFF),
  surfaceElevated = Color(0xFFF0F4FC),
  surfaceSelected = Color(0xFFE4EBFC),
  textPrimary = Color(0xFF0B1324),
  textSecondary = Color(0xFF526075),
  textMuted = Color(0xFF75839A),
  icon = Color(0xFF5E6C82),
  iconActive = Color(0xFF2563EB),
  borderCard = Color(0xFFD4DCE8),
  borderInput = Color(0xFFC2CDDC),
  divider = Color(0xFFECF0F6),
  brandPrimary = Color(0xFF2563EB),
  brandDeep = Color(0xFF1D4ED8),
  brandText = Color(0xFF1D4ED8),
  link = Color(0xFF1D4ED8),
  focus = Color(0xFF2563EB),
  inverse = Color(0xFF0B1324),
  inverseRaised = Color(0xFF1B2538),
  onInverse = Color(0xFFF2F5FB),
  onInverseMuted = Color(0xFFA3AFC2),
  button = buttons(
    primary = Color(0xFF1D4ED8) to Color(0xFFFFFFFF),
    secondary = Color(0xFFE4EBFC) to Color(0xFF1D4ED8),
    neutral = Color(0xFFEAEEF5) to Color(0xFF1E293B),
    danger = Color(0xFFB4232F) to Color(0xFFFFFFFF),
    disabled = Color(0xFFE9EDF3) to Color(0xFF98A3B5),
  ),
  status = StatusDay,
  message = MessageDay,
  chart = ThemeAccents.chart(dark = false, grid = Color(0xFFE2E8F0)),
  shadow = Color(0x520F172A),
  scrim = Color(0x990B1220),
  glow = Color(0x1F2563EB),
  sheen = Color(0x59FFFFFF),
  auroraOne = Color(0x4C2563EB),
  auroraTwo = Color(0x3D4F46E5),
  auroraThree = Color(0x337C3AED),
  heroGradient = listOf(Color(0xFF2563EB), Color(0xFF4F46E5), Color(0xFF7C3AED)),
  accents = ThemeAccents.of(dark = false, glowTop = Color(0xFF0EA5E9), glowBottom = Color(0xFF8B5CF6)),
  brandOnInverse = Color(0xFF60A5FA),
  heroScrim = 0.04f,
  heroChipShade = 0.15f,
)

internal val SkyDark = ShopColors(
  background = Color(0xFF0A0E15),
  backgroundSubtle = Color(0xFF070A10),
  surface = Color(0xFF161C27),
  surfaceElevated = Color(0xFF202835),
  surfaceSelected = Color(0xFF1B2A48),
  textPrimary = Color(0xFFEDF1F7),
  textSecondary = Color(0xFFA9B3C2),
  textMuted = Color(0xFF8D98AA),
  icon = Color(0xFFA3AEBF),
  iconActive = Color(0xFF6EA3FF),
  borderCard = Color(0xFF27303E),
  borderInput = Color(0xFF3D4759),
  divider = Color(0xFF1C2330),
  brandPrimary = Color(0xFF6EA3FF),
  brandDeep = Color(0xFF2563EB),
  brandText = Color(0xFF8DB8FF),
  link = Color(0xFF8DB8FF),
  focus = Color(0xFF6EA3FF),
  inverse = Color(0xFF05070B),
  inverseRaised = Color(0xFF161C27),
  onInverse = Color(0xFFEDF1F7),
  onInverseMuted = Color(0xFF9AA5B7),
  button = buttons(
    primary = Color(0xFF2563EB) to Color(0xFFFFFFFF),
    secondary = Color(0xFF1B2A48) to Color(0xFFA9C7FF),
    neutral = Color(0xFF232B39) to Color(0xFFE6EBF2),
    danger = Color(0xFFC42B38) to Color(0xFFFFFFFF),
    disabled = Color(0xFF1A202B) to Color(0xFF5F6A7C),
  ),
  status = StatusNight,
  message = MessageNight,
  chart = ThemeAccents.chart(dark = true, grid = Color(0xFF1C2330)),
  shadow = Color(0x80000000),
  scrim = Color(0xCC020509),
  glow = Color(0x336EA3FF),
  sheen = Color(0x1FFFFFFF),
  auroraOne = Color(0x8C1E3A8A),
  auroraTwo = Color(0x61312E81),
  auroraThree = Color(0x574C1D95),
  heroGradient = listOf(Color(0xFF1E3A8A), Color(0xFF312E81), Color(0xFF4C1D95)),
  accents = ThemeAccents.of(dark = true, glowTop = Color(0xFF0EA5E9), glowBottom = Color(0xFF8B5CF6)),
  brandOnInverse = Color(0xFF1D4ED8),
  heroScrim = 0.0f,
  heroChipShade = 0.0f,
)

internal val NavyLight = ShopColors(
  background = Color(0xFFF3F5F9),
  backgroundSubtle = Color(0xFFE8ECF2),
  surface = Color(0xFFFFFFFF),
  surfaceElevated = Color(0xFFEEF1F6),
  surfaceSelected = Color(0xFFE3E8F3),
  textPrimary = Color(0xFF0B1426),
  textSecondary = Color(0xFF4B576D),
  textMuted = Color(0xFF6F7A8F),
  icon = Color(0xFF56627A),
  iconActive = Color(0xFF1E3A8A),
  borderCard = Color(0xFFD5DBE5),
  borderInput = Color(0xFFC1C9D6),
  divider = Color(0xFFEAEDF3),
  brandPrimary = Color(0xFF1E3A8A),
  brandDeep = Color(0xFF172F6E),
  brandText = Color(0xFF1E3A8A),
  link = Color(0xFF1E3A8A),
  focus = Color(0xFF1E3A8A),
  inverse = Color(0xFF0B1426),
  inverseRaised = Color(0xFF1A2540),
  onInverse = Color(0xFFF1F4FA),
  onInverseMuted = Color(0xFFA2ADC2),
  button = buttons(
    primary = Color(0xFF1E3A8A) to Color(0xFFFFFFFF),
    secondary = Color(0xFFE3E8F3) to Color(0xFF1E3A8A),
    neutral = Color(0xFFE9ECF2) to Color(0xFF1B2537),
    danger = Color(0xFFB4232F) to Color(0xFFFFFFFF),
    disabled = Color(0xFFE8EBF1) to Color(0xFF97A1B2),
  ),
  status = StatusDay,
  message = MessageDay,
  chart = ThemeAccents.chart(dark = false, grid = Color(0xFFE2E8F0)),
  shadow = Color(0x520F172A),
  scrim = Color(0x990B1220),
  glow = Color(0x1F1E3A8A),
  sheen = Color(0x59FFFFFF),
  auroraOne = Color(0x4C172554),
  auroraTwo = Color(0x3D1E3A8A),
  auroraThree = Color(0x3325407F),
  heroGradient = listOf(Color(0xFF172554), Color(0xFF1E3A8A), Color(0xFF25407F)),
  accents = ThemeAccents.of(dark = false, glowTop = Color(0xFFC9A14A), glowBottom = Color(0xFF3B5BA9)),
  brandOnInverse = Color(0xFF93B0F0),
  heroScrim = 0.0f,
  heroChipShade = 0.0f,
)

internal val NavyDark = ShopColors(
  background = Color(0xFF090D18),
  backgroundSubtle = Color(0xFF060912),
  surface = Color(0xFF131B2D),
  surfaceElevated = Color(0xFF1D263A),
  surfaceSelected = Color(0xFF1C2A4C),
  textPrimary = Color(0xFFECF0F8),
  textSecondary = Color(0xFFA6B0C3),
  textMuted = Color(0xFF8B96AB),
  icon = Color(0xFFA0AABF),
  iconActive = Color(0xFF8CA9F0),
  borderCard = Color(0xFF242F44),
  borderInput = Color(0xFF3A4660),
  divider = Color(0xFF1A2335),
  brandPrimary = Color(0xFF8CA9F0),
  brandDeep = Color(0xFF3B5FD0),
  brandText = Color(0xFFA3BAF3),
  link = Color(0xFFA3BAF3),
  focus = Color(0xFF8CA9F0),
  inverse = Color(0xFF04060C),
  inverseRaised = Color(0xFF131B2D),
  onInverse = Color(0xFFECF0F8),
  onInverseMuted = Color(0xFF98A3B9),
  button = buttons(
    primary = Color(0xFF3B5FD0) to Color(0xFFFFFFFF),
    secondary = Color(0xFF1C2A4C) to Color(0xFFB3C6F5),
    neutral = Color(0xFF232A37) to Color(0xFFE5EAF3),
    danger = Color(0xFFC42B38) to Color(0xFFFFFFFF),
    disabled = Color(0xFF1A202B) to Color(0xFF5C6780),
  ),
  status = StatusNight,
  message = MessageNight,
  chart = ThemeAccents.chart(dark = true, grid = Color(0xFF1A2335)),
  shadow = Color(0x80000000),
  scrim = Color(0xCC020509),
  glow = Color(0x338CA9F0),
  sheen = Color(0x1FFFFFFF),
  auroraOne = Color(0x8C0F1A3D),
  auroraTwo = Color(0x61172554),
  auroraThree = Color(0x571E3A8A),
  heroGradient = listOf(Color(0xFF0F1A3D), Color(0xFF172554), Color(0xFF1E3A8A)),
  accents = ThemeAccents.of(dark = true, glowTop = Color(0xFFC9A14A), glowBottom = Color(0xFF3B5BA9)),
  brandOnInverse = Color(0xFF1E3A8A),
  heroScrim = 0.0f,
  heroChipShade = 0.0f,
)

internal val IvoryLight = ShopColors(
  background = Color(0xFFF7F5F1),
  backgroundSubtle = Color(0xFFEEEAE3),
  surface = Color(0xFFFFFFFF),
  surfaceElevated = Color(0xFFF4F1EB),
  surfaceSelected = Color(0xFFF2EADB),
  textPrimary = Color(0xFF1C1814),
  textSecondary = Color(0xFF59514A),
  textMuted = Color(0xFF7B7269),
  icon = Color(0xFF665D55),
  iconActive = Color(0xFF8C5E1A),
  borderCard = Color(0xFFE2DCD1),
  borderInput = Color(0xFFCDC4B6),
  divider = Color(0xFFEEEAE3),
  brandPrimary = Color(0xFF8C5E1A),
  brandDeep = Color(0xFF6E4A14),
  brandText = Color(0xFF7A5216),
  link = Color(0xFF7A5216),
  focus = Color(0xFF8C5E1A),
  inverse = Color(0xFF1C1814),
  inverseRaised = Color(0xFF2C2620),
  onInverse = Color(0xFFF7F3EC),
  onInverseMuted = Color(0xFFB3AA9E),
  button = buttons(
    primary = Color(0xFF8C5E1A) to Color(0xFFFFFFFF),
    secondary = Color(0xFFF2EADB) to Color(0xFF6E4A14),
    neutral = Color(0xFFEFEBE4) to Color(0xFF2A241E),
    danger = Color(0xFFB4232F) to Color(0xFFFFFFFF),
    disabled = Color(0xFFEEEAE3) to Color(0xFFA39A8F),
  ),
  status = StatusDay,
  message = MessageDay,
  chart = ThemeAccents.chart(dark = false, grid = Color(0xFFE2E8F0)),
  shadow = Color(0x520F172A),
  scrim = Color(0x990B1220),
  glow = Color(0x1F8C5E1A),
  sheen = Color(0x59FFFFFF),
  auroraOne = Color(0x4C3A2C1E),
  auroraTwo = Color(0x3D5C4322),
  auroraThree = Color(0x338C5E1A),
  heroGradient = listOf(Color(0xFF3A2C1E), Color(0xFF5C4322), Color(0xFF8C5E1A)),
  accents = ThemeAccents.of(dark = false, glowTop = Color(0xFFE4C682), glowBottom = Color(0xFFB07A2A)),
  brandOnInverse = Color(0xFFD9B260),
  heroScrim = 0.0f,
  heroChipShade = 0.15f,
)

internal val IvoryDark = ShopColors(
  background = Color(0xFF100E0B),
  backgroundSubtle = Color(0xFF0B0A08),
  surface = Color(0xFF1F1B17),
  surfaceElevated = Color(0xFF2A2620),
  surfaceSelected = Color(0xFF2E2618),
  textPrimary = Color(0xFFF4EFE7),
  textSecondary = Color(0xFFB7AEA2),
  textMuted = Color(0xFF9C9286),
  icon = Color(0xFFAEA598),
  iconActive = Color(0xFFD9B260),
  borderCard = Color(0xFF342F26),
  borderInput = Color(0xFF4A4237),
  divider = Color(0xFF221E19),
  brandPrimary = Color(0xFFD9B260),
  brandDeep = Color(0xFF8C5E1A),
  brandText = Color(0xFFE4C682),
  link = Color(0xFFE4C682),
  focus = Color(0xFFD9B260),
  inverse = Color(0xFF070605),
  inverseRaised = Color(0xFF1F1B17),
  onInverse = Color(0xFFF4EFE7),
  onInverseMuted = Color(0xFFA79E92),
  button = buttons(
    primary = Color(0xFF94621C) to Color(0xFFFFFFFF),
    secondary = Color(0xFF2E2618) to Color(0xFFE8CF95),
    neutral = Color(0xFF2A251E) to Color(0xFFEEE8DF),
    danger = Color(0xFFC42B38) to Color(0xFFFFFFFF),
    disabled = Color(0xFF201D18) to Color(0xFF6B6359),
  ),
  status = StatusNight,
  message = MessageNight,
  chart = ThemeAccents.chart(dark = true, grid = Color(0xFF221E19)),
  shadow = Color(0x80000000),
  scrim = Color(0xCC020509),
  glow = Color(0x33D9B260),
  sheen = Color(0x1FFFFFFF),
  auroraOne = Color(0x8C2A2016),
  auroraTwo = Color(0x613D2F1C),
  auroraThree = Color(0x575E4518),
  heroGradient = listOf(Color(0xFF2A2016), Color(0xFF3D2F1C), Color(0xFF5E4518)),
  accents = ThemeAccents.of(dark = true, glowTop = Color(0xFFE4C682), glowBottom = Color(0xFFB07A2A)),
  brandOnInverse = Color(0xFF8C5E1A),
  heroScrim = 0.0f,
  heroChipShade = 0.0f,
)

internal val GraphiteLight = ShopColors(
  background = Color(0xFFF5F6F8),
  backgroundSubtle = Color(0xFFEBEDF0),
  surface = Color(0xFFFFFFFF),
  surfaceElevated = Color(0xFFF1F2F5),
  surfaceSelected = Color(0xFFE6E8ED),
  textPrimary = Color(0xFF111318),
  textSecondary = Color(0xFF4A505C),
  textMuted = Color(0xFF6B717D),
  icon = Color(0xFF575D69),
  iconActive = Color(0xFF334155),
  borderCard = Color(0xFFD9DCE2),
  borderInput = Color(0xFFC5C9D1),
  divider = Color(0xFFECEEF1),
  brandPrimary = Color(0xFF334155),
  brandDeep = Color(0xFF1F2937),
  brandText = Color(0xFF273244),
  link = Color(0xFF273244),
  focus = Color(0xFF334155),
  inverse = Color(0xFF111318),
  inverseRaised = Color(0xFF22262E),
  onInverse = Color(0xFFF3F4F6),
  onInverseMuted = Color(0xFFA9AEB8),
  button = buttons(
    primary = Color(0xFF1F2937) to Color(0xFFFFFFFF),
    secondary = Color(0xFFE6E8ED) to Color(0xFF1F2937),
    neutral = Color(0xFFECEEF1) to Color(0xFF1F2329),
    danger = Color(0xFFB4232F) to Color(0xFFFFFFFF),
    disabled = Color(0xFFECEEF1) to Color(0xFF9CA1AB),
  ),
  status = StatusDay,
  message = MessageDay,
  chart = ThemeAccents.chart(dark = false, grid = Color(0xFFE2E8F0)),
  shadow = Color(0x520F172A),
  scrim = Color(0x990B1220),
  glow = Color(0x1F334155),
  sheen = Color(0x59FFFFFF),
  auroraOne = Color(0x4C111827),
  auroraTwo = Color(0x3D1F2937),
  auroraThree = Color(0x33374151),
  heroGradient = listOf(Color(0xFF111827), Color(0xFF1F2937), Color(0xFF374151)),
  accents = ThemeAccents.of(dark = false, glowTop = Color(0xFF94A3B8), glowBottom = Color(0xFFC9A14A)),
  brandOnInverse = Color(0xFFCBD5E1),
  heroScrim = 0.0f,
  heroChipShade = 0.0f,
)

internal val GraphiteDark = ShopColors(
  background = Color(0xFF0D0E11),
  backgroundSubtle = Color(0xFF09090B),
  surface = Color(0xFF191C20),
  surfaceElevated = Color(0xFF23272D),
  surfaceSelected = Color(0xFF2A2E36),
  textPrimary = Color(0xFFF1F2F4),
  textSecondary = Color(0xFFAEB2BA),
  textMuted = Color(0xFF9196A0),
  icon = Color(0xFFA7ABB4),
  iconActive = Color(0xFFCBD2DC),
  borderCard = Color(0xFF2C3036),
  borderInput = Color(0xFF43474F),
  divider = Color(0xFF1E2025),
  brandPrimary = Color(0xFFCBD2DC),
  brandDeep = Color(0xFF5E6A7D),
  brandText = Color(0xFFD5DAE2),
  link = Color(0xFFD5DAE2),
  focus = Color(0xFFCBD2DC),
  inverse = Color(0xFF060708),
  inverseRaised = Color(0xFF191C20),
  onInverse = Color(0xFFF1F2F4),
  onInverseMuted = Color(0xFFA0A5AE),
  button = buttons(
    primary = Color(0xFF5E6A7D) to Color(0xFFFFFFFF),
    secondary = Color(0xFF2A2E36) to Color(0xFFE2E6EC),
    neutral = Color(0xFF25282E) to Color(0xFFE8EAED),
    danger = Color(0xFFC42B38) to Color(0xFFFFFFFF),
    disabled = Color(0xFF1C1E22) to Color(0xFF61656E),
  ),
  status = StatusNight,
  message = MessageNight,
  chart = ThemeAccents.chart(dark = true, grid = Color(0xFF1E2025)),
  shadow = Color(0x80000000),
  scrim = Color(0xCC020509),
  glow = Color(0x33CBD2DC),
  sheen = Color(0x1FFFFFFF),
  auroraOne = Color(0x8C23272E),
  auroraTwo = Color(0x6130353E),
  auroraThree = Color(0x57454C58),
  heroGradient = listOf(Color(0xFF23272E), Color(0xFF30353E), Color(0xFF454C58)),
  accents = ThemeAccents.of(dark = true, glowTop = Color(0xFF94A3B8), glowBottom = Color(0xFFC9A14A)),
  brandOnInverse = Color(0xFF334155),
  heroScrim = 0.0f,
  heroChipShade = 0.0f,
)
