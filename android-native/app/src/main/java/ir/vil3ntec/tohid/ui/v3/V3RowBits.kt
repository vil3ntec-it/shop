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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Colors
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Status

/*
 *  سادگیِ ردیف‌ها (۱۴۰۵/۰۸/۲۴، `claude/e-rows-63d`): یک کارِ اصلی در هر ردیف، وضعیت به متنِ آرام، بی حذفِ قابلیت.
 *   • [V3RowStatus]: وضعیتی که کنارِ مبلغ با «•» می‌آید («۳۰۰ ؋ • لغوشده») — نه قرصِ رنگی. حالتِ عادی ⇒ null (هیچ).
 *     رنگ فقط برای آن‌چه توجه می‌خواهد: [V3RowTone.ATTENTION] (نسیه، کم، در انتظار) و [V3RowTone.DANGER] (خطا، تمام‌شده).
 *   • [V3PillAction]: قرصِ نرمِ تنها کارِ اصلیِ ردیف («↻ دریافت»)؛ زدنِ خودِ ردیف جزئیات را باز می‌کند و کارهای دیگر در
 *     جزئیات، نگه‌داشتن یا «…» می‌مانند.
 */

/** رنگِ متنِ وضعیت: آرام خاکستری، توجه کهربایی، خطر قرمز؛ بدهی/رسیدِ دفتر همان سرخ/سبزِ حساب */
fun v3RowToneColor(c: PhoneV3Colors, tone: V3RowTone): Color = when (tone) {
  V3RowTone.QUIET -> c.inkMuted
  V3RowTone.ATTENTION -> PhoneV3Roles.status(c, V3Status.WARNING).fg
  V3RowTone.DANGER -> PhoneV3Roles.errorText(c)
  //  همان `PhoneV3Debt.colors.owe/settled` (= `negative`/`positive`) ِ مبلغ‌های حساب
  V3RowTone.DEBT -> c.negative
  V3RowTone.RECEIPT -> c.positive
}

/**
 *  «۳۰۰ ؋ • لغوشده» در یک نوشته (اگر جا نشد، وضعیت به خطِ دوم می‌رود). [status] null ⇒ فقط مبلغ.
 *  [struck] ⇒ مبلغِ خط‌خورده و کم‌رنگ (فاکتورِ لغوشده). [second]: وضعیتِ دومِ آرام با رنگِ خودش («… • نیمه‌پرداخت • رسیده»،
 *  خریدها ۶۳b) — null ⇒ همان یک وضعیت.
 */
@Composable
fun V3AmountStatus(
  amount: String?,
  status: V3RowStatus?,
  modifier: Modifier = Modifier,
  struck: Boolean = false,
  align: TextAlign = TextAlign.End,
  second: V3RowStatus? = null,
) {
  val c = PhoneV3.colors
  val text = buildAnnotatedString {
    if (amount != null) {
      withStyle(SpanStyle(color = if (struck) c.inkMuted else c.ink, textDecoration = if (struck) TextDecoration.LineThrough else null)) { append(amount) }
    }
    listOfNotNull(status, second).forEachIndexed { i, st ->
      if (amount != null || i > 0) withStyle(SpanStyle(color = c.inkMuted)) { append(V3_ROW_DOT) }
      withStyle(SpanStyle(color = v3RowToneColor(c, st.tone))) { append(st.text) }
    }
  }
  Text(
    text,
    style = if (amount != null) V3Type.callout else V3Type.footnote,
    textAlign = align,
    maxLines = 2,
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
 *  (خودِ قرص ۳۲)؛ زدنش فقط همین کار را می‌کند و به زدنِ ردیف (جزئیات) نمی‌رسد.
 */
@Composable
fun V3PillAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
  val c = PhoneV3.colors
  val fg = if (enabled) PhoneV3Roles.brandText(c) else c.inkMuted
  Row(
    modifier
      .heightIn(min = 40.dp)
      .v3Pressable(enabled = enabled, label = label, onClick = onClick),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      Modifier.heightIn(min = 32.dp).clip(RoundedCornerShape(percent = 50)).background(PhoneV3Palette.selected(c)).padding(horizontal = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
      Text(label, style = V3Type.subhead, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}
