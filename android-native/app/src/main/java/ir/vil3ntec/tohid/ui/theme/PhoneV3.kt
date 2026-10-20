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
}

/**
 *  رنگِ نشانه‌های کاشیِ «بخش‌ها»ی خانه (r7 «Payment List») — phone-v3-home.
 *
 *  r7 تنها جایی است که آیکنِ رنگی روی کاشیِ خیلی روشن می‌نشیند (SPEC §D)؛ آیکن‌های
 *  بقیهٔ گوشی تک‌رنگ می‌مانند. رنگ‌های `Shop.colors` برای متن تیره شده‌اند و روی
 *  کاشی کدر دیده می‌شدند، پس این هشت رنگ از خودِ r7 برداشته شد. شب کمی روشن‌تر.
 */
object PhoneV3Glyph {
  private val light = listOf(
    Color(0xFF7B2FF7), Color(0xFFF5A524), Color(0xFFF04848), Color(0xFF3DBE4A),
    Color(0xFF2F5CE0), Color(0xFF22B5E0), Color(0xFFE040C8), Color(0xFF6C2BEA),
  )
  private val dark = listOf(
    Color(0xFF9D6BFF), Color(0xFFFFB547), Color(0xFFFF6B6B), Color(0xFF5CD66A),
    Color(0xFF6B8BFF), Color(0xFF4CC9F0), Color(0xFFF06BDB), Color(0xFF8B5CF6),
  )

  /** رنگِ `i`امین کاشی (چرخشی) */
  fun of(colors: PhoneV3Colors, i: Int): Color {
    val list = if (colors === PhoneV3Dark) dark else light
    return list[((i % list.size) + list.size) % list.size]
  }
}
