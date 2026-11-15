package ir.vil3ntec.tohid.ui.v3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Colors
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles

/*
 *  سادگیِ ردیف‌ها (۱۴۰۵/۰۸/۲۴، `claude/e-rows-63d`): یک کارِ اصلی در هر ردیف، وضعیت به متنِ آرام، بی حذفِ قابلیت.
 *   • [V3RowStatus]: وضعیتی که کنارِ مبلغ با «•» می‌آید («۳۰۰ ؋ • لغوشده») — نه قرصِ رنگی. حالتِ عادی ⇒ null (هیچ).
 *     ۶۸c: رنگ فقط برای آن‌چه کار می‌خواهد — [V3RowTone.DANGER] (خطا، تمام‌شده، سررسید گذشته)؛ [V3RowTone.ATTENTION] (نسیه، کم،
 *     در انتظار) واژهٔ آرامِ کم‌رنگ است، نه زرد.
 *   • [V3PillAction]: قرصِ نرمِ تنها کارِ اصلیِ ردیف («↻ دریافت»)؛ زدنِ خودِ ردیف جزئیات را باز می‌کند و کارهای دیگر در
 *     جزئیات، نگه‌داشتن یا «…» می‌مانند.
 */

/**
 *  رنگِ متنِ وضعیت: آرام و «توجه» هر دو خاکستریِ کم‌رنگ (۶۸c، صاحب‌کار: «این شلوغی‌ها … دقیق و منظم» — هیچ خطِ زرد/کهربایی
 *  در فهرست؛ واژه خودش «نسیه»، «کم»، «نرسیده» را می‌گوید)، فقط خطر (کار می‌خواهد: «سررسید گذشته»، «تمام‌شده») رنگِ خطرِ آرام.
 *  شدّت در داده ([V3RowTone]) می‌ماند تا جزئیات و آزمون‌ها معنا را بدانند.
 */
fun v3RowToneColor(c: PhoneV3Colors, tone: V3RowTone): Color = when (tone) {
  V3RowTone.QUIET, V3RowTone.ATTENTION -> c.inkMuted
  V3RowTone.DANGER -> PhoneV3Roles.errorText(c)
}

/**
 *  «۳۰۰ ؋ • لغوشده» در یک نوشته — همان قاعدهٔ خطِ مبلغِ ردیفِ داده ([v3MetaText]: مبلغ به رنگِ نوشته، وضعیت کم‌رنگ، دست‌بالا یک
 *  واژهٔ رنگِ خطر). [status] null ⇒ فقط مبلغ. [struck] ⇒ مبلغِ خط‌خورده و کم‌رنگ (فاکتورِ لغوشده). [second]: وضعیتِ دومِ آرام
 *  («… • نیمه‌پرداخت • رسیده»، صفحهٔ جزئیاتِ خرید) — null ⇒ همان یک وضعیت. [maxLines] ۱ در فهرست؛ جزئیات ۲.
 */
@Composable
fun V3AmountStatus(
  amount: String?,
  status: V3RowStatus?,
  modifier: Modifier = Modifier,
  struck: Boolean = false,
  align: TextAlign = TextAlign.End,
  second: V3RowStatus? = null,
  maxLines: Int = 1,
) {
  Text(
    v3MetaText(PhoneV3.colors, V3RowMeta(amount, status, second, struck)),
    style = if (amount != null) V3Type.callout else V3Type.footnote,
    textAlign = align,
    maxLines = maxLines,
    overflow = TextOverflow.Ellipsis,
    modifier = modifier,
  )
}

/** فقط متنِ آرامِ وضعیت، بی مبلغ (زیرِ موجودی، کنارِ ساعت) */
@Composable
fun V3QuietStatus(status: V3RowStatus, modifier: Modifier = Modifier) {
  Text(status.text, style = V3Type.footnote, color = v3RowToneColor(PhoneV3.colors, status.tone), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/**
 *  قرصِ نرمِ کارِ اصلیِ ردیف: زمینهٔ کم‌رنگِ برند، نوشتهٔ برند، بی مرز. ناحیهٔ لمس دست‌کم ۴۰dp بلند است
 *  (خودِ قرص ۳۲)؛ زدنش فقط همین کار را می‌کند و به زدنِ ردیف (جزئیات) نمی‌رسد. [neutral] (ردیفِ داده، ۶۸c): قرصِ خنثیِ
 *  خاکستری با نوشتهٔ تیره — همان «↻ Rebook» ِ عکسِ صاحب‌کار، بی رنگِ برند در فهرست.
 */
@Composable
fun V3PillAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, neutral: Boolean = false) {
  val c = PhoneV3.colors
  val calm = PhoneV3Roles.button(c, ir.vil3ntec.tohid.ui.theme.V3ButtonKind.NEUTRAL)
  val fg = when {
    !enabled -> c.inkMuted
    neutral -> calm.fg
    else -> PhoneV3Roles.brandText(c)
  }
  Row(
    modifier
      .heightIn(min = ir.vil3ntec.tohid.ui.theme.V3ButtonSpec.pillTouch)
      .v3Pressable(enabled = enabled, label = label, onClick = onClick),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      Modifier.heightIn(min = ir.vil3ntec.tohid.ui.theme.V3ButtonSpec.pillHeight).clip(RoundedCornerShape(percent = 50)).background(if (neutral) calm.bg else PhoneV3Palette.selected(c)).padding(horizontal = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
      Text(label, style = V3Type.subhead, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}
