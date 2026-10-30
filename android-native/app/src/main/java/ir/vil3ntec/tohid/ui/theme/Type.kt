package ir.vil3ntec.tohid.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.R

/**
 *  همان فونتِ وزیرمتن که نسخهٔ وب استفاده می‌کند — این بار داخلِ خودِ
 *  برنامه، پس بدونِ اینترنت هم درست است.
 */
val Vazirmatn = FontFamily(
  Font(R.font.vazirmatn_regular, FontWeight.Normal),
  Font(R.font.vazirmatn_medium, FontWeight.Medium),
  Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
  Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

private fun style(size: Int, weight: FontWeight, line: Int, scale: Float, features: String?) =
  TextStyle(
    fontFamily = Vazirmatn,
    fontSize = (size * scale).sp,
    fontWeight = weight,
    lineHeight = (line * scale).sp,
    //  رقمِ فارسیِ صفحه (LOC-05): `ss01`ِ وزیرمتن همان رقمِ لاتین را با شکلِ ۱۲۳
    //  می‌کشد. متن و داده ASCII می‌مانند؛ لاتین ⇒ null، یعنی همان سبکِ امروز
    fontFeatureSettings = features,
  )

/**
 *  اندازهٔ متن‌ها، با یک ضریب.
 *
 *  روی تبلت همه‌چیز با همان اندازهٔ گوشی کشیده می‌شد و سربرگ‌ها روی صفحهٔ
 *  ده‌اینچی ریز و گم به نظر می‌رسیدند — صفحه بزرگ‌تر شده بود ولی نوشته
 *  نه. متن باید با صفحه بزرگ شود، وگرنه فاصلهٔ چشم تا صفحه بیشتر است و
 *  حروف کوچک‌تر دیده می‌شوند.
 *
 *  ضریب عمداً کوچک است: بیشتر از این، چیدمانِ ردیف‌ها به هم می‌ریزد و
 *  عنوان‌های بلند دو خطی می‌شوند.
 */
fun shopTypography(scale: Float = 1f, digits: String = ir.vil3ntec.tohid.core.text.LocaleFormat.DIGITS_LATN): Typography {
  val f = ir.vil3ntec.tohid.LocaleText.fontFeatures(digits)
  return Typography(
  displaySmall = style(28, FontWeight.Bold, 36, scale, f),
  headlineMedium = style(22, FontWeight.Bold, 30, scale, f),
  headlineSmall = style(18, FontWeight.SemiBold, 26, scale, f),
  titleMedium = style(16, FontWeight.SemiBold, 24, scale, f),
  //  نسخهٔ ۲: عنوانِ بخش یک پله پررنگ‌تر — سلسله‌مراتب: عنوانِ صفحه / بخش / متن / ریزنوشت
  titleSmall = style(14, FontWeight.SemiBold, 20, scale, f),
  // متن‌های بدنه و برچسب یک درجه پُرتر شدند.
  //
  // `Normal` روی سطحِ کم‌رنگِ کارت‌ها نازک می‌افتاد و متنِ کوچک — به‌ویژه
  // فارسی که خطوطش ظریف است — در تمِ تاریک تقریباً محو می‌شد. یک درجه
  // بالاتر رفتند، نه بیشتر: `Bold` کردنِ متنِ ریز، خطوط را به هم می‌چسباند
  // و عرضِ متن را زیاد می‌کند، و آن‌وقت همان چیزی از کادر بیرون می‌زند که
  // می‌خواستیم خوانا شود.
  bodyLarge = style(15, FontWeight.Medium, 24, scale, f),
  bodyMedium = style(14, FontWeight.Medium, 22, scale, f),
  bodySmall = style(12, FontWeight.Medium, 18, scale, f),
  labelLarge = style(14, FontWeight.SemiBold, 20, scale, f),
  labelMedium = style(12, FontWeight.SemiBold, 16, scale, f),
  labelSmall = style(11, FontWeight.SemiBold, 14, scale, f),
  )
}

/**
 *  عدد با رقم‌های هم‌پهنا (`tnum`) — ستونِ پول، موجودی و جمع صاف می‌ایستد و عدد
 *  هنگامِ تغییر نمی‌لرزد. با `ss01`ِ رقمِ فارسی (LOC-05) جمع می‌شود: «ss01, tnum».
 *  کامپیوتر (`FontFeature.parseW3`) و اندروید هر دو فهرستِ ویرگول‌دار را می‌خوانند.
 */
fun TextStyle.tabular(): TextStyle =
  copy(fontFeatureSettings = listOfNotNull(fontFeatureSettings?.takeIf { it.isNotBlank() }, "tnum").joinToString(", "))
