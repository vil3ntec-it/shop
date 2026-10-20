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

/* ======================= قرض‌داران (r9-debtors.jpg) ======================= */

/**
 *  رنگ‌های صفحهٔ قرض‌داران — r9، به خواستِ صاحب محصول «همین مدلِ دقیق».
 *
 *  r9 عکسِ خودِ همین برنامه پیش از تخت شدنِ صفحه‌های کار است (۱۴۰۵/۰۷/۲۵)؛
 *  پس عددها همان پالتِ آن روز است و با همان عکس سنجیده شده: زمینهٔ بنفشِ
 *  بسیار کم‌رنگ با سه لکهٔ نورِ آرورا (همان `AuroraTone.Debt`: صورتی، بنفش،
 *  نارنجی)، سطحِ ۹۵٪ سفید با لبهٔ سفید (بی خطِ تیره)، قرمز و سبزِ نیم‌شفاف
 *  روی کارت. فقط همین صفحه این‌ها را دارد؛ بقیهٔ گوشی همان `PhoneV3Colors`.
 */
data class PhoneV3DebtColors(
  /** زمینهٔ صفحه (r9: #F2F0FB) */
  val page: Color,
  /** لکهٔ صورتیِ بالا-چپ؛ آلفا = شدتِ وسطِ لکه */
  val blobPink: Color,
  /** لکهٔ بنفشِ راست-پایین (r9: وسطش #D4CBFA) */
  val blobViolet: Color,
  /** لکهٔ گرمِ پایین-چپ، بسیار کم‌رنگ */
  val blobWarm: Color,
  /** کارتِ سه‌عددی و تراشه‌های انتخاب‌نشده */
  val surface: Color,
  /** لبهٔ سطح‌ها و دکمه‌های ریزِ × و مداد — سفید، نه خطِ تیره */
  val edge: Color,
  val ink: Color,
  /** متنِ زیرِ مبلغ، تراشهٔ انتخاب‌نشده، آیکنِ × و مداد و ساعت */
  val muted: Color,
  /** «N مورد» و برچسبِ عددهای کارتِ بالا */
  val muted2: Color,
  /** خطِ دورِ کادرِ جستجو (r9: #D8D6E1 روی زمینه) */
  val fieldEdge: Color,
  /** تراشهٔ انتخاب‌شده و دکمهٔ «قرض‌دار تازه» (پرکردن) */
  val accent: Color,
  val onAccent: Color,
  /** بنفش برای آیکن و متن روی زمینه: میکروفون، کادرِ جستجوی فعال */
  val accentInk: Color,
  /** دایرهٔ میکروفون */
  val accentSoft: Color,
  /** بدهکار: خطِ دور و مبلغ */
  val owe: Color,
  /** بدهکار: پرکردنِ کارت — روی زمینه ترکیب می‌شود و توپر کشیده می‌شود تا لکهٔ نورِ زیرش متن را کم‌رنگ نکند */
  val oweTint: Color,
  /** حسابِ صاف (یا موجودی): خطِ دور و مبلغ */
  val settled: Color,
  val settledTint: Color,
)

val PhoneV3DebtLight = PhoneV3DebtColors(
  page = Color(0xFFF2F0FA),
  blobPink = Color(0x33FF5FA2),
  blobViolet = Color(0x407C5CFF),
  //  r9 پایینِ چپ تقریباً بی‌رنگ است (#F3EDF1)
  blobWarm = Color(0x14FF9A3D),
  surface = Color(0xF2FFFFFF),
  edge = Color(0xE6FFFFFF),
  ink = Color(0xFF191627),
  muted = Color(0xFF635C86),
  muted2 = Color(0xFF6A6390),
  fieldEdge = Color(0x1F191627),
  accent = Color(0xFF6F50F0),
  onAccent = Color(0xFFFFFFFF),
  //  r9: #6F51F1؛ یک پله تیره‌تر تا برچسبِ جستجوی فعال روی زمینه ≥ ۴٫۵:۱ (۴٫۵۶ ⇐ ۴٫۸۱)
  accentInk = Color(0xFF6B4CEC),
  accentSoft = Color(0x246F50F0),
  //  r9: #BF2C2C؛ یک پله تیره‌تر تا روی کارتِ صورتی ≥ ۴٫۵:۱ (۴٫۳۸ ⇐ ۴٫۶۱) — به چشم همان
  owe = Color(0xFFB92A2A),
  oweTint = Color(0x1FD93B3B),
  //  r9: #08745E؛ یک پله تیره‌تر تا روی کارتِ سبز با فاصله ≥ ۴٫۵:۱ (۴٫۵۱ ⇐ ۴٫۷۶)
  settled = Color(0xFF07705B),
  settledTint = Color(0x1F0FA98A),
)

//  شب: همان پالتِ شبِ آن روز (بنفشِ تیره، نه سیاه). «N مورد» یک پله روشن‌تر
//  از آن روز تا روی زمینهٔ تیره ≥ ۴٫۵:۱ بماند
val PhoneV3DebtDark = PhoneV3DebtColors(
  page = Color(0xFF0B0A14),
  blobPink = Color(0x40FF5FA2),
  blobViolet = Color(0x597C5CFF),
  blobWarm = Color(0x1AFF9A3D),
  surface = Color(0xF0141221),
  edge = Color(0x1AFFFFFF),
  ink = Color(0xFFF0EEF8),
  muted = Color(0xFFA8A3BC),
  muted2 = Color(0xFF9A95B2),
  fieldEdge = Color(0x26FFFFFF),
  accent = Color(0xFF6F50F0),
  onAccent = Color(0xFFFFFFFF),
  accentInk = Color(0xFFA78BFF),
  accentSoft = Color(0x33A78BFF),
  owe = Color(0xFFFF7A7A),
  oweTint = Color(0x33FF7A7A),
  settled = Color(0xFF5CE0BC),
  settledTint = Color(0x335CE0BC),
)

/**
 *  اندازه‌های r9 به dp، از همان عکس سنجیده (۲٫۲۵ پیکسل = ۱dp: لبهٔ ۱۶dp همان
 *  ۳۶ پیکسلِ عکس است، دکمهٔ ۲۲dp همان ۴۸ پیکسل).
 */
object PhoneV3Debt {
  /** تمِ جاری از روشنیِ زمینهٔ `Shop.colors` — مثلِ `PhoneV3.colors` */
  val colors: PhoneV3DebtColors
    @Composable get() = of(Shop.colors.bg)

  fun of(bg: Color): PhoneV3DebtColors = if (bg.luminance() < 0.5f) PhoneV3DebtDark else PhoneV3DebtLight

  /**
   *  r9 روی صفحهٔ ۶۶۸dp گرفته شده که متنِ برنامه را ۱٫۱۲ برابر می‌کند
   *  (`TohidTheme`)؛ این صفحه همان ضریب را روی هر دستگاه ثابت نگه می‌دارد تا
   *  گوشی هم همان اندازه‌های r9 را ببیند.
   */
  const val typeScale = 1.12f
  /** کارتِ سه‌عددی و کارتِ هر قرض‌دار (r9: ۴۵ پیکسل) */
  val cardRadius = 20.dp
  /** کادرِ جستجو (r9: ۲۸ پیکسل) */
  val fieldRadius = 12.dp
  /** دایرهٔ میکروفون */
  val micSize = 34.dp
  /** دکمه‌های ریزِ × و مدادِ گوشهٔ کارت */
  val cornerButton = 22.dp
  val cornerButtonRadius = 7.dp
  val cardBorder = 1.2.dp
  /** لبهٔ صفحه */
  val gutter = 16.dp
  /** فاصلهٔ کارت‌ها */
  val gap = 8.dp
  /** کمینهٔ پهنای کارت: سه ستون روی گوشیِ ۳۶۰ تا ۴۷۰dp */
  val tileMin = 104.dp
  /** تبلت: همان چهار ستونِ r9 روی ۶۶۸dp */
  val tileMinTablet = 132.dp
  /** دکمهٔ گردِ «قرض‌دار تازه» */
  val fabSize = 56.dp
}
