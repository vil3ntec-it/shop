package ir.vil3ntec.tohid.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 *  توکن‌های ظاهرِ گوشی، نسخهٔ ۳ — بازسازیِ دقیق از روی تصویرهای مرجعِ صاحب محصول
 *  (سبدِ r2/r3، حسابِ r4، تنظیماتِ r5، کارتِ کششیِ r6، خانهٔ r7، آمارِ r8).
 *
 *  پایه خنثی است (سفید، خاکستریِ روشن، سیاه) و آیکن‌ها تک‌رنگ؛ بنفش فقط برای
 *  خانه و آمار (r7/r8) و زرد فقط برای سبد (r2/r3). رنگِ تازه فقط از همین‌جا.
 */
data class PhoneV3Colors(
  /** زمینهٔ صفحه (r5) */
  val bg: Color,
  /** کارت‌ها و سطحِ سفیدِ کالاها در صندوق */
  val card: Color,
  /** کارتِ نیمه‌شفافِ حساب (r4) */
  val cardGlass: Color,
  val ink: Color,
  val inkMuted: Color,
  /** خطِ مویی میانِ ردیف‌های یک گروه */
  val hairline: Color,
  /** آیکنِ خطیِ تک‌رنگ */
  val icon: Color,
  val chevron: Color,
  /** سطحِ سیاه: زمینهٔ سبد، قرصِ فعالِ نوار، کارتِ کششی، دکمهٔ اسکن */
  val black: Color,
  /** دایرهٔ آیکن روی سیاه (ردیفِ «مشتری» در سبد) */
  val blackRaised: Color,
  val onBlack: Color,
  val onBlackMuted: Color,
  /** بنفشِ خانه و آمار (r7/r8) */
  val accent: Color,
  val accentDeep: Color,
  val onAccent: Color,
  /** زردِ سبد: دایرهٔ شمار و دکمهٔ «پرداخت» (r2/r3) */
  val cart: Color,
  val onCart: Color,
  /** کلیدِ روشن (r5) */
  val toggleOn: Color,
  /** کاشیِ بخش‌ها (r7) */
  val tile: Color,
  val positive: Color,
  val negative: Color,
)

val PhoneV3Light = PhoneV3Colors(
  bg = Color(0xFFF2F2F5),
  card = Color(0xFFFFFFFF),
  cardGlass = Color(0xD9FFFFFF),
  ink = Color(0xFF111114),
  inkMuted = Color(0xFF8A8A94),
  hairline = Color(0xFFECECF0),
  icon = Color(0xFF1C1C21),
  chevron = Color(0xFFB9B9C2),
  black = Color(0xFF0D0D0F),
  blackRaised = Color(0xFF1F1F23),
  onBlack = Color(0xFFFFFFFF),
  onBlackMuted = Color(0xFF9A9AA3),
  accent = Color(0xFF6C2BEA),
  accentDeep = Color(0xFF2A0E5E),
  onAccent = Color(0xFFFFFFFF),
  cart = Color(0xFFFFC24A),
  onCart = Color(0xFF111114),
  toggleOn = Color(0xFF34C759),
  tile = Color(0xFFF4F2FA),
  positive = Color(0xFF1E9E5A),
  negative = Color(0xFFE5484D),
)

//  شب: سطحِ کالاها تیره می‌شود ولی زمینهٔ سبد همچنان سیاهِ کامل است
val PhoneV3Dark = PhoneV3Colors(
  bg = Color(0xFF0B0B0D),
  card = Color(0xFF17171B),
  cardGlass = Color(0xCC1C1C21),
  ink = Color(0xFFF2F2F5),
  inkMuted = Color(0xFF9A9AA5),
  hairline = Color(0xFF26262C),
  icon = Color(0xFFE6E6EB),
  chevron = Color(0xFF5C5C66),
  black = Color(0xFF000000),
  blackRaised = Color(0xFF1F1F23),
  onBlack = Color(0xFFFFFFFF),
  onBlackMuted = Color(0xFF9A9AA3),
  accent = Color(0xFF8B5CF6),
  accentDeep = Color(0xFF2A0E5E),
  onAccent = Color(0xFFFFFFFF),
  cart = Color(0xFFFFC24A),
  onCart = Color(0xFF111114),
  toggleOn = Color(0xFF30D158),
  tile = Color(0xFF1E1B26),
  positive = Color(0xFF34C77B),
  negative = Color(0xFFFF6B6B),
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
