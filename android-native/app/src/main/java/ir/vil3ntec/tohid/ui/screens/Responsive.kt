package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 *  اندازهٔ صفحه.
 *
 *  برنامه روی گوشی ساخته شده بود و روی تبلت همان یک ستون تمام‌عرض کش
 *  می‌آمد: خطی به عرضِ بیست سانتی‌متر که چشم برای خواندنش باید سر بچرخاند،
 *  و کارت‌هایی که وسطشان خالی می‌ماند.
 *
 *  سه اندازه کافی است. بیشتر از این، هر صفحه سه حالت پیدا می‌کند و
 *  نگه‌داشتنشان سخت می‌شود.
 */
enum class Screen { PHONE, TABLET, WIDE }

@Composable
fun screenSize(): Screen {
  val width = LocalConfiguration.current.screenWidthDp
  return when {
    width >= 900 -> Screen.WIDE
    width >= 600 -> Screen.TABLET
    else -> Screen.PHONE
  }
}

@Composable
fun isTablet(): Boolean = screenSize() != Screen.PHONE

/**
 *  پهنای بیشینهٔ محتوا.
 *
 *  روی تبلت، محتوا وسطِ صفحه می‌ماند و از یک حدی پهن‌تر نمی‌شود. متنِ
 *  خیلی پهن خوانده نمی‌شود — چشم سرِ خط بعدی را گم می‌کند. حاشیه‌های
 *  خالیِ دو طرف عیب نیستند؛ همان چیزی‌اند که صفحه را قابلِ خواندن نگه
 *  می‌دارد.
 */
@Composable
fun contentMaxWidth(): Dp = when (screenSize()) {
  Screen.PHONE -> Dp.Unspecified
  Screen.TABLET -> 680.dp
  //  کامپیوتر: پهنای مفیدِ مانیتور (با منوی کناری)، نه ستونِ باریکِ گوشی
  //  برنامهٔ کامپیوتر کلِ پنجره را می‌گیرد (مثلِ هر نرم‌افزارِ رومیزی)؛ فقط روی
  //  مانیتورِ خیلی پهن سقف دارد تا ردیف‌های جدول از هم دور نیفتند
  Screen.WIDE -> if (ir.vil3ntec.tohid.ui.Platform.DESKTOP) 1680.dp else 1200.dp
}

/**
 *  محتوای صفحه را وسط نگه می‌دارد و پهنایش را محدود می‌کند.
 *
 *  روی گوشی پهنا را محدود نمی‌کند، ولی `modifier` (فاصله‌های پوسته: جای نوارِ شناور،
 *  نوارِ سیستم و منوی کناری) **همیشه** اعمال می‌شود — پیش‌تر روی گوشی دور انداخته می‌شد
 *  و نوارِ پایین تهِ همهٔ صفحه‌ها را می‌پوشاند (AUDIT-NAV S1).
 */
@Composable
fun PageWidth(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val max = contentMaxWidth()
  if (max == Dp.Unspecified) {
    //  رابطِ امروز (پرچمِ نسخهٔ ۳ خاموش) روی گوشی `modifier` را نمی‌گرفت — همان رفتار
    if (!ir.vil3ntec.tohid.ui.UiV3.on) content() else Box(modifier.fillMaxSize()) { content() }
    return
  }
  Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Box(Modifier.widthIn(max = max).fillMaxWidth()) { content() }
  }
}

/** چند ستون برای شبکه‌ها — روی تبلت جای بیشتری هست، پس کارتِ بزرگ‌تر نه، کارتِ بیشتر */
@Composable
fun gridMinSize(phone: Dp, tablet: Dp = phone): Dp =
  if (isTablet()) tablet else phone
