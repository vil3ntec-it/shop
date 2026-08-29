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

private fun style(size: Int, weight: FontWeight, line: Int, scale: Float) =
  TextStyle(
    fontFamily = Vazirmatn,
    fontSize = (size * scale).sp,
    fontWeight = weight,
    lineHeight = (line * scale).sp,
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
fun shopTypography(scale: Float = 1f) = Typography(
  displaySmall = style(28, FontWeight.Bold, 36, scale),
  headlineMedium = style(22, FontWeight.Bold, 30, scale),
  headlineSmall = style(18, FontWeight.SemiBold, 26, scale),
  titleMedium = style(16, FontWeight.SemiBold, 24, scale),
  titleSmall = style(14, FontWeight.Medium, 20, scale),
  bodyLarge = style(15, FontWeight.Normal, 24, scale),
  bodyMedium = style(14, FontWeight.Normal, 22, scale),
  bodySmall = style(12, FontWeight.Normal, 18, scale),
  labelLarge = style(14, FontWeight.Medium, 20, scale),
  labelMedium = style(12, FontWeight.Medium, 16, scale),
  labelSmall = style(11, FontWeight.Medium, 14, scale),
)
