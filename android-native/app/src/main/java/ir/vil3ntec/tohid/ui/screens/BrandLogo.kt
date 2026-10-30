package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import ir.vil3ntec.tohid.R

/**
 *  نشانِ برنامه — همان تصویری که آیکنِ روی صفحهٔ گوشی از آن ساخته شده.
 *
 *  ── چرا دیگر کشیده نمی‌شود ────────────────────────────────────────
 *  اولین بار نشان را با مسیرهای برداری از نو کشیدم، چون تصویری که
 *  داشتم ۴۱۳ پیکسل بود و بزرگ کردنش تار می‌شد. ولی آن بازکشیدن، هرچه
 *  دقیق، نسخهٔ **ساده‌شده** بود: برجستگی، سایه و درخششِ لبه‌ها را
 *  نداشت. یعنی کاربر روی صفحهٔ گوشی یک چیز می‌دید و داخلِ برنامه چیزِ
 *  کمی متفاوتی.
 *
 *  حالا منبع یکی است: همان فایلِ اصلی، برای هر تراکمِ صفحه یک بار
 *  کوچک شده. آیکنِ لانچر، لوگوی صفحهٔ ورود و نشانِ ستونِ کناری، هر سه
 *  از همین یک تصویر می‌آیند.
 *  ──────────────────────────────────────────────────────────────────
 */
@Composable
fun TohidMark(modifier: Modifier = Modifier) {
  Image(
    painter = painterResource(R.drawable.logo_mark),
    contentDescription = null,
    modifier = modifier,
    //  نسبت‌ها دست‌نخورده می‌مانند؛ تصویر خودش مربع است
    contentScale = ContentScale.Fit,
  )
}
