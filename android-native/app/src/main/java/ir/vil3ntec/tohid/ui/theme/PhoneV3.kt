package ir.vil3ntec.tohid.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 *  توکن‌های ظاهرِ گوشی، نسخهٔ ۳. شکل از تصویرهای مرجعِ صاحب محصول (سبدِ r2/r3،
 *  حسابِ r4، تنظیماتِ r5، کارتِ کششیِ r6، خانهٔ r7، آمارِ r8) و رنگ از سیستمِ
 *  رنگِ VILL3N (دستورِ صاحب محصول، ۱۴۰۵/۰۷/۲۸): برندِ سبزآبیِ #108B81 به‌جای بنفش،
 *  زمینه/کارت/سطحِ درونی با مرزِ دیدنی در هر دو تم. رنگِ تازه فقط از همین‌جا.
 */
data class PhoneV3Colors(
  /** زمینهٔ صفحه (سطحِ صفر) */
  val bg: Color,
  /** کارت‌ها و سطحِ کالاها در صندوق (سطحِ یک) */
  val card: Color,
  /** کارتِ حساب (r4) */
  val cardGlass: Color,
  val ink: Color,
  val inkMuted: Color,
  /** مرزِ کارت و خطِ میانِ ردیف‌های یک گروه */
  val hairline: Color,
  /** آیکنِ خطیِ تک‌رنگ */
  val icon: Color,
  val chevron: Color,
  /** سطحِ تیرهٔ وارونه: زمینهٔ سبد، قرصِ فعالِ نوار، کارتِ کششی، دکمهٔ اسکن */
  val black: Color,
  /** دایرهٔ آیکن روی سطحِ تیره (ردیفِ «مشتری» در سبد) */
  val blackRaised: Color,
  val onBlack: Color,
  val onBlackMuted: Color,
  /** رنگِ برند: سرِ خانه، آمار، کارِ اصلی */
  val accent: Color,
  val accentDeep: Color,
  val onAccent: Color,
  /** سبد روی سطحِ تیره: دایرهٔ شمار و دکمهٔ «پرداخت» */
  val cart: Color,
  val onCart: Color,
  /** کلیدِ روشن */
  val toggleOn: Color,
  /** کاشیِ بخش‌ها و سطحِ درونی (سطحِ دو) */
  val tile: Color,
  val positive: Color,
  val negative: Color,
  /**
   *  «در انتظار» (حالتِ پیک: کارِ هنوز به دکان نرسیده، مأموریتِ تازه) — همان
   *  رنگِ متنِ وضعیتِ «در انتظارِ تأیید»ِ سیستمِ رنگ (§۸)؛ شب روشن‌تر.
   */
  val waiting: Color = Color(0xFF92400E),
)

val PhoneV3Light = PhoneV3Colors(
  bg = Color(0xFFF3F6FA),
  card = Color(0xFFFFFFFF),
  cardGlass = Color(0xF2FFFFFF),
  ink = Color(0xFF17212B),
  inkMuted = Color(0xFF5B687A),
  hairline = Color(0xFFD5DEE9),
  icon = Color(0xFF64748B),
  chevron = Color(0xFF7B8798),
  black = Color(0xFF0B1220),
  blackRaised = Color(0xFF162235),
  onBlack = Color(0xFFF1F5F9),
  onBlackMuted = Color(0xFFB6C2D2),
  accent = Color(0xFF108B81),
  accentDeep = Color(0xFF08665F),
  onAccent = Color(0xFFFFFFFF),
  cart = Color(0xFF19A985),
  onCart = Color(0xFF041B15),
  toggleOn = Color(0xFF108B81),
  tile = Color(0xFFF8FAFC),
  positive = Color(0xFF15803D),
  negative = Color(0xFFB91C1C),
)

//  شب: کارت هرگز در زمینه گم نمی‌شود (#0B1220 ⇐ #162235 ⇐ #203149) و سبد روی تیره‌ترین سطح است
val PhoneV3Dark = PhoneV3Colors(
  bg = Color(0xFF0B1220),
  card = Color(0xFF162235),
  cardGlass = Color(0xF2162235),
  ink = Color(0xFFF1F5F9),
  inkMuted = Color(0xFFB6C2D2),
  hairline = Color(0xFF34445A),
  icon = Color(0xFFA3B2C5),
  chevron = Color(0xFF94A3B8),
  black = Color(0xFF050A14),
  blackRaised = Color(0xFF162235),
  onBlack = Color(0xFFF1F5F9),
  onBlackMuted = Color(0xFFB6C2D2),
  //  سرِ خانه و کارت‌های آمار متنِ سفید دارند ⇒ همان برندِ روز (سفید روی #108B81 و #08665F خواناست)
  accent = Color(0xFF108B81),
  accentDeep = Color(0xFF08665F),
  onAccent = Color(0xFFFFFFFF),
  cart = Color(0xFF19A985),
  onCart = Color(0xFF041B15),
  toggleOn = Color(0xFF19A985),
  tile = Color(0xFF203149),
  positive = Color(0xFF86EFAC),
  negative = Color(0xFFFCA5A5),
  waiting = Color(0xFFFCD34D),
)

object PhoneV3 {
  /** تمِ جاری از روشنیِ زمینهٔ `Shop.colors` — مثلِ `PhoneSheet` */
  val colors: PhoneV3Colors
    @Composable get() = of(Shop.colors.bg)

  fun of(bg: Color): PhoneV3Colors = if (bg.luminance() < 0.5f) PhoneV3Dark else PhoneV3Light

  /** گوشهٔ پایینِ سطحِ سفیدِ کالاها و کارتِ کششی (r2/r3/r6) */
  val sheetRadius = 36.dp
  /** کارتِ اصلیِ حساب (r4) */
  val cardRadius = 28.dp
  /** کارتِ گروهِ تنظیمات و کادرهای آمار (r4/r5) */
  val groupRadius = 18.dp
  /** کاشیِ بخش‌ها (r7) */
  val tileRadius = 16.dp
  /** بلندیِ نوارِ قرصیِ پایین */
  val navHeight = 64.dp
  /** بلندیِ ردیفِ تنظیمات (r5) */
  val rowHeight = 52.dp

  /** دکمه‌ها، آیکنِ فعال و رنگِ وضعیت‌ها (سیستمِ رنگِ VILL3N §۷/§۸) برای تمِ جاری */
  val ui: PhoneV3Ui
    @Composable get() = if (Shop.colors.bg.luminance() < 0.5f) PhoneV3UiDark else PhoneV3UiLight
}

/** یک جفتِ رنگِ وضعیت: متن/آیکن روی زمینهٔ کم‌رنگ — هرگز رنگ بی برچسب */
data class PhoneV3Tone(val fg: Color, val bg: Color)

/**
 *  رنگ‌های کار و وضعیت از سیستمِ رنگِ VILL3N (۱۴۰۵/۰۷/۲۸) — جدا از `PhoneV3Colors`
 *  تا سازنده‌های آن دست نخورند. وضعیتِ سفارش برای مشتری، دکان، پیک و مدیر یک معنا
 *  دارد (§۸). `onDark*` برای سطحِ تیرهٔ سرمه‌ای در هر دو تم (کارتِ «مأموریتِ جاری»،
 *  برگهٔ پیگیری): دکمهٔ اصلی #19A985 با متنِ #041B15.
 */
data class PhoneV3Ui(
  val primary: Color,
  val onPrimary: Color,
  val primaryOnDark: Color,
  val onPrimaryOnDark: Color,
  val secondaryBg: Color,
  val secondaryFg: Color,
  val secondaryBorder: Color,
  val neutralBg: Color,
  val neutralFg: Color,
  val danger: Color,
  val onDanger: Color,
  val disabledBg: Color,
  val disabledFg: Color,
  /** آیکنِ فعال/برند */
  val iconActive: Color,
  val success: PhoneV3Tone,
  val delivery: PhoneV3Tone,
  val awaiting: PhoneV3Tone,
  val cancelled: PhoneV3Tone,
  val preparing: PhoneV3Tone,
  val inactive: PhoneV3Tone,
)

private val StatusDark = listOf(
  PhoneV3Tone(Color(0xFF86EFAC), Color(0xFF123C2B)),
  PhoneV3Tone(Color(0xFF93C5FD), Color(0xFF172A4D)),
  PhoneV3Tone(Color(0xFFFCD34D), Color(0xFF3A2C0B)),
  PhoneV3Tone(Color(0xFFFCA5A5), Color(0xFF3F1A1A)),
  PhoneV3Tone(Color(0xFF67E8F9), Color(0xFF0F3640)),
  PhoneV3Tone(Color(0xFFB6C2D2), Color(0xFF203149)),
)

val PhoneV3UiLight = PhoneV3Ui(
  primary = Color(0xFF108B81), onPrimary = Color(0xFFFFFFFF),
  primaryOnDark = Color(0xFF19A985), onPrimaryOnDark = Color(0xFF041B15),
  secondaryBg = Color(0xFFFFFFFF), secondaryFg = Color(0xFF08665F), secondaryBorder = Color(0xFFB8DCD5),
  neutralBg = Color(0xFFE8EDF3), neutralFg = Color(0xFF344256),
  danger = Color(0xFFDC2626), onDanger = Color(0xFFFFFFFF),
  disabledBg = Color(0xFFE2E8F0), disabledFg = Color(0xFF94A3B8),
  iconActive = Color(0xFF108B81),
  success = PhoneV3Tone(Color(0xFF15803D), Color(0xFFDCFCE7)),
  delivery = PhoneV3Tone(Color(0xFF1D4ED8), Color(0xFFDBEAFE)),
  awaiting = PhoneV3Tone(Color(0xFF92400E), Color(0xFFFEF3C7)),
  cancelled = PhoneV3Tone(Color(0xFFB91C1C), Color(0xFFFEE2E2)),
  preparing = PhoneV3Tone(Color(0xFF0E7490), Color(0xFFCFFAFE)),
  inactive = PhoneV3Tone(Color(0xFF475569), Color(0xFFE2E8F0)),
)

val PhoneV3UiDark = PhoneV3Ui(
  primary = Color(0xFF19A985), onPrimary = Color(0xFF041B15),
  primaryOnDark = Color(0xFF19A985), onPrimaryOnDark = Color(0xFF041B15),
  secondaryBg = Color(0xFF162235), secondaryFg = Color(0xFF6EE7D1), secondaryBorder = Color(0xFF40516A),
  neutralBg = Color(0xFF203149), neutralFg = Color(0xFFF1F5F9),
  danger = Color(0xFFDC2626), onDanger = Color(0xFFFFFFFF),
  disabledBg = Color(0xFF203149), disabledFg = Color(0xFF64748B),
  iconActive = Color(0xFF6EE7D1),
  success = StatusDark[0], delivery = StatusDark[1], awaiting = StatusDark[2],
  cancelled = StatusDark[3], preparing = StatusDark[4], inactive = StatusDark[5],
)
