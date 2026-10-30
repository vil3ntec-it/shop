package ir.vil3ntec.tohid.ui.theme

import androidx.compose.ui.graphics.Color

/*
 *  ⛔ پالتِ رابطِ امروز (سبزآبیِ VILL3N، ۱۴۰۵/۰۷/۲۸) — رونوشتِ `LightColors`/`DarkColors` ِ main.
 *  تا پرچمِ `UiV3` خاموش است (پیش‌فرض) `TohidTheme` همین را می‌دهد، هر تمی که انتخاب شده باشد؛
 *  تمِ آبیِ THEME-V2 و سه تمِ `ShopThemes.kt` فقط با پرچمِ روشن. تنها افزوده: `status.warning`
 *  (در main نبود) = همان `awaitingApproval`، چون آن‌جا «هشدار» همین رنگ بود.
 */

/** سبزآبیِ برندِ رابطِ امروز */
internal object Vill3nBrand {
  val PRIMARY = Color(0xFF108B81)
  val DEEP = Color(0xFF08665F)
}

/** پالتِ صفحه: پرچمِ نسخهٔ ۳ روشن ⇒ تمِ انتخاب‌شده؛ خاموش ⇒ سبزآبیِ امروز (آزمون: `UiV3Test`) */
fun paletteFor(v3: Boolean, theme: ShopTheme, dark: Boolean): ShopColors =
  if (v3) shopColors(theme, dark) else if (dark) Vill3nDark else Vill3nLight

internal val Vill3nLight = ShopColors(
  background = Color(0xFFF3F6FA),
  backgroundSubtle = Color(0xFFEAF0F6),
  surface = Color(0xFFFFFFFF),
  surfaceElevated = Color(0xFFF8FAFC),
  surfaceSelected = Color(0xFFE0F2EF),
  textPrimary = Color(0xFF17212B),
  textSecondary = Color(0xFF5B687A),
  textMuted = Color(0xFF7B8798),
  icon = Color(0xFF64748B),
  iconActive = Vill3nBrand.PRIMARY,
  borderCard = Color(0xFFD5DEE9),
  borderInput = Color(0xFFC5D0DE),
  divider = Color(0xFFE2E8F0),
  brandPrimary = Vill3nBrand.PRIMARY,
  brandDeep = Vill3nBrand.DEEP,
  //  #108B81 روی سفید ۴٫۱۷:۱ ⇒ متنِ کوچک با سبزآبیِ پررنگ (۶٫۸:۱)
  brandText = Vill3nBrand.DEEP,
  link = Brand.blue,
  focus = Vill3nBrand.PRIMARY,
  inverse = Color(0xFF0B1220),
  inverseRaised = Color(0xFF162235),
  onInverse = Color(0xFFF1F5F9),
  onInverseMuted = Color(0xFFB6C2D2),
  button = ButtonColorSet(
    /*
     *  ⚠️ انحراف از فایل: صاحب محصول زمینهٔ #108B81 با متنِ سفید خواسته؛ آن جفت
     *  ۴٫۱۷:۱ است و برچسبِ ۱۳–۱۴ نقطه‌ایِ دکمه زیرِ ۴٫۵:۱ِ AA می‌ماند. پس دکمهٔ پُر
     *  `brandDeep` است (۶٫۸:۱). برگرداندن = همین یک خط.
     */
    primary = ButtonTone(bg = Vill3nBrand.DEEP, text = Color(0xFFFFFFFF), border = Vill3nBrand.DEEP),
    secondary = ButtonTone(bg = Color(0xFFFFFFFF), text = Vill3nBrand.DEEP, border = Color(0xFFB8DCD5)),
    neutral = ButtonTone(bg = Color(0xFFE8EDF3), text = Color(0xFF344256), border = Color(0xFFE8EDF3)),
    danger = ButtonTone(bg = Color(0xFFDC2626), text = Color(0xFFFFFFFF), border = Color(0xFFDC2626)),
    disabled = ButtonTone(bg = Color(0xFFE2E8F0), text = Color(0xFF94A3B8), border = Color(0xFFE2E8F0)),
  ),
  status = StatusColors(
    delivered = StatusTone(fg = Color(0xFF15803D), bg = Color(0xFFDCFCE7)),
    inDelivery = StatusTone(fg = Color(0xFF1D4ED8), bg = Color(0xFFDBEAFE)),
    awaitingApproval = StatusTone(fg = Color(0xFF92400E), bg = Color(0xFFFEF3C7)),
    //  رابطِ امروز «هشدار» و «در انتظارِ تأیید» را یک رنگ (کهربایی) داشت
    warning = StatusTone(fg = Color(0xFF92400E), bg = Color(0xFFFEF3C7)),
    cancelled = StatusTone(fg = Color(0xFFB91C1C), bg = Color(0xFFFEE2E2)),
    needsReview = StatusTone(fg = Color(0xFF6D28D9), bg = Color(0xFFEDE9FE)),
    preparing = StatusTone(fg = Color(0xFF0E7490), bg = Color(0xFFCFFAFE)),
    inactive = StatusTone(fg = Color(0xFF475569), bg = Color(0xFFE2E8F0)),
  ),
  message = MessageColors(
    success = MessageTone(text = Color(0xFF166534), bg = Color(0xFFF0FDF4), icon = Color(0xFF16A34A), border = Color(0xFFBBF7D0)),
    info = MessageTone(text = Color(0xFF1E40AF), bg = Color(0xFFEFF6FF), icon = Brand.blue, border = Color(0xFFBFDBFE)),
    warning = MessageTone(text = Color(0xFF92400E), bg = Color(0xFFFFFBEB), icon = Color(0xFFB45309), border = Color(0xFFFDE68A)),
    error = MessageTone(text = Color(0xFFB91C1C), bg = Color(0xFFFEF2F2), icon = Color(0xFFDC2626), border = Color(0xFFFECACA)),
  ),
  chart = ChartColors(
    sales = Vill3nBrand.PRIMARY, comparison = Brand.blue, costs = Brand.purple, discount = Brand.orange,
    drop = Brand.red, stock = Brand.teal, neutral = Brand.neutral, grid = Color(0xFFE2E8F0),
  ),
  //  0 3px 12px rgba(15,23,42,.06): آلفای ۳۲٪ × آلفای سایهٔ سامانه (~۱۹٪) ≈ ۶٪
  shadow = Color(0x520F172A),
  scrim = Color(0x990B1220),
  glow = Color(0x1F108B81),
  sheen = Color(0x59FFFFFF),
  auroraOne = Color(0x4D108B81),
  auroraTwo = Color(0x3D2563EB),
  auroraThree = Color(0x3314B8A6),
)

/**
 *  شب — کارت (#162235) همیشه از زمینه (#0B1220) جداست و خطِ دورِ خاکستریِ
 *  روشن (#34445A) دارد: «کارت در شب هرگز با زمینه یکی نشود».
 *  رنگ‌هایی که فایل برای شب نداده (زمینهٔ فرعی، شش وضعیت، پیام‌ها، غیرفعال) این‌جا
 *  ساخته شده‌اند: همان رنگ، تیره برای زمینه و روشن برای متن، همه ≥ ۴٫۵:۱.
 */
internal val Vill3nDark = ShopColors(
  background = Color(0xFF0B1220),
  //  فایل برای شب ندارد ⇒ همان سطحِ وارونهٔ شب (#050A14): یک پله تیره‌تر از زمینه،
  //  تا منوی کناری و شیارِ کنترل از کارت (۱٫۲۴:۱) و از محتوا جدا دیده شوند
  backgroundSubtle = Color(0xFF050A14),
  surface = Color(0xFF162235),
  surfaceElevated = Color(0xFF203149),
  surfaceSelected = Color(0xFF123C3A),
  textPrimary = Color(0xFFF1F5F9),
  textSecondary = Color(0xFFB6C2D2),
  textMuted = Color(0xFF94A3B8),
  icon = Color(0xFFA3B2C5),
  iconActive = Color(0xFF6EE7D1),
  borderCard = Color(0xFF34445A),
  borderInput = Color(0xFF40516A),
  divider = Color(0xFF29374A),
  //  دکمهٔ اصلیِ شب (#19A985) — همان رنگِ پُرِ برند در شب
  brandPrimary = Color(0xFF19A985),
  brandDeep = Vill3nBrand.DEEP,
  brandText = Color(0xFF6EE7D1),
  link = Color(0xFF93C5FD),
  focus = Color(0xFF6EE7D1),
  inverse = Color(0xFF050A14),
  inverseRaised = Color(0xFF162235),
  onInverse = Color(0xFFF1F5F9),
  onInverseMuted = Color(0xFFB6C2D2),
  button = ButtonColorSet(
    primary = ButtonTone(bg = Color(0xFF19A985), text = Color(0xFF041B15), border = Color(0xFF19A985)),
    secondary = ButtonTone(bg = Color(0xFF162235), text = Color(0xFF6EE7D1), border = Color(0xFF40516A)),
    neutral = ButtonTone(bg = Color(0xFF203149), text = Color(0xFFF1F5F9), border = Color(0xFF203149)),
    danger = ButtonTone(bg = Color(0xFFDC2626), text = Color(0xFFFFFFFF), border = Color(0xFFDC2626)),
    //  ساخته‌شده: غیرفعالِ شب — کم‌رنگ ولی روی کارت پیدا
    disabled = ButtonTone(bg = Color(0xFF1B2839), text = Color(0xFF64748B), border = Color(0xFF29374A)),
  ),
  status = StatusColors(
    delivered = StatusTone(fg = Color(0xFF86EFAC), bg = Color(0xFF123C2B)),
    inDelivery = StatusTone(fg = Color(0xFF93C5FD), bg = Color(0xFF172F57)),
    awaitingApproval = StatusTone(fg = Color(0xFFFCD34D), bg = Color(0xFF3D2E0E)),
    warning = StatusTone(fg = Color(0xFFFCD34D), bg = Color(0xFF3D2E0E)),
    cancelled = StatusTone(fg = Color(0xFFFCA5A5), bg = Color(0xFF451A1E)),
    needsReview = StatusTone(fg = Color(0xFFC4B5FD), bg = Color(0xFF2E2357)),
    preparing = StatusTone(fg = Color(0xFF67E8F9), bg = Color(0xFF0E3A45)),
    inactive = StatusTone(fg = Color(0xFFCBD5E1), bg = Color(0xFF273548)),
  ),
  message = MessageColors(
    success = MessageTone(text = Color(0xFF86EFAC), bg = Color(0xFF0F2A1E), icon = Color(0xFF4ADE80), border = Color(0xFF1F5135)),
    info = MessageTone(text = Color(0xFF93C5FD), bg = Color(0xFF0F1E3A), icon = Color(0xFF60A5FA), border = Color(0xFF1E3A8A)),
    warning = MessageTone(text = Color(0xFFFCD34D), bg = Color(0xFF2B1F0B), icon = Color(0xFFFBBF24), border = Color(0xFF78350F)),
    error = MessageTone(text = Color(0xFFFCA5A5), bg = Color(0xFF2D1215), icon = Color(0xFFF87171), border = Color(0xFF7F1D1D)),
  ),
  chart = ChartColors(
    sales = Vill3nBrand.PRIMARY, comparison = Brand.blue, costs = Brand.purple, discount = Brand.orange,
    drop = Brand.red, stock = Brand.teal, neutral = Brand.neutral, grid = Color(0xFF29374A),
  ),
  shadow = Color(0x66000000),
  scrim = Color(0xCC020509),
  glow = Color(0x3319A985),
  sheen = Color(0x1FFFFFFF),
  auroraOne = Color(0x8C108B81),
  auroraTwo = Color(0x612563EB),
  auroraThree = Color(0x5714B8A6),
)
