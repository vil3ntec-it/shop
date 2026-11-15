package ir.vil3ntec.tohid.ui.v3

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Colors

/*
 *  **ردیفِ فهرستِ داده** (۶۸c، صاحب‌کار: «این شلوغی‌هات رو … درست کن دقیق و منظم» — همان عکسِ «پس» ِ فهرستِ کارها): خریدها،
 *  تأمین‌کننده‌ها و صفحهٔ هر تأمین‌کننده، تاریخچهٔ فروش، رسیدها، مصارف، کالاها، انبار، سفارش‌ها، تخفیف‌ها و کارمندان همین را
 *  می‌کشند. کالبدِ ثابت:
 *
 *    [کاشی] · عنوان (یک خط، ۱۶ میانه)
 *            · خطِ دوم (یک خط، ۱۳ کم‌رنگ: «دیروز · چای سبز»، «۱۴:۰۵ · ۳ قلم · #۱۲۴۵»)
 *            · خطِ مبلغ (یک خط، ۱۴: «۸٬۳۲۰ ؋ • ۳٬۳۲۰ ؋ مانده» — مبلغ به رنگِ نوشته با رقمِ هم‌پهنا، وضعیت واژهٔ کم‌رنگ)
 *    و در پایان **یا** یک قرصِ خنثیِ کار (آیکن + واژه، [V3RowAction]) **یا** پیکان — هرگز دو قرص.
 *
 *  رنگ: در هر ردیف دست‌بالا **یک** نشانِ معنا و فقط برای آنچه کار می‌خواهد ([V3RowTone.DANGER]: «سررسید گذشته»، «تمام‌شده») به
 *  رنگِ خطرِ آرام و فقط همان واژه — هیچ خطِ تمامِ زرد/کهربایی. هر جزئیاتِ دیگر در صفحه/برگهٔ جزئیاتِ همان ردیف می‌ماند (هیچ
 *  قابلیتی حذف نمی‌شود). عدد را در صفحه ننویس؛ همین‌ها (`V3DataRowGuardTest`).
 */

/** اندازه‌های ردیفِ داده — همان ردیفِ «بیشتر» (`V3NavRowSpec`) با فاصلهٔ بالا و پایینِ دست‌ودل‌بازتر */
object V3DataRowSpec {
  val TILE: Dp = V3NavRowSpec.TILE
  val GAP: Dp = V3NavRowSpec.GAP
  val PAD_START: Dp = V3NavRowSpec.PAD_START
  val PAD_END: Dp = V3NavRowSpec.PAD_END
  /** بالا و پایین — آرام، همهٔ ردیف‌ها یک‌اندازه */
  val PAD_V: Dp = 12.dp
  /** بلندیِ کم: سه خطِ کالبد */
  val MIN_HEIGHT: Dp = 72.dp
  /**
   *  ردیفِ فشردهٔ دوخطی (`V3DataRow(dense = true)`: عنوان + خطِ مبلغ، بی خطِ دوم) — «کالاها» ِ گوشی (۱۴۰۵/۰۸/۲۴: دست‌کم ۸ ردیفِ کامل
   *  در ۳۶۰×۷۶۰ زیرِ سربرگ، تراشه‌ها و نوارِ پایین). ۱۰ + ۲۲ + ۲ + ۲۰ + ۱۰ = ۶۴.
   */
  val DENSE_PAD_V: Dp = 10.dp
  val DENSE_MIN_HEIGHT: Dp = 64.dp
  /** خطِ مویی از زیرِ متن: آغاز + کاشی + فاصله (۶۴) */
  val DIVIDER_START: Dp = PAD_START + TILE + GAP
  /** فاصلهٔ خطِ مبلغ از خطِ دوم */
  val META_GAP: Dp = 2.dp
  const val TITLE_SP = V3NavRowSpec.TITLE_SP
  const val SECONDARY_SP = V3NavRowSpec.HELP_SP
  const val META_SP = 14
  const val ROW_TAG = "v3-data-row"
  const val META_TAG = "v3-data-meta"
  const val ACTION_TAG = "v3-data-action"
}

/** تنها کارِ اصلیِ ردیف — قرصِ خنثیِ کوچک با آیکن و واژه («↻ دریافت»، «پرداخت») */
data class V3RowAction(
  val label: String,
  val icon: ImageVector?,
  val enabled: Boolean = true,
  val onClick: () -> Unit,
)

/**
 *  خطِ مبلغِ ردیف: [amount] به رنگِ نوشته (رقمِ هم‌پهنا)، و پس از «•» [status] و [second] به واژهٔ کم‌رنگ. [struck] ⇒
 *  مبلغِ خط‌خورده و کم‌رنگ (سندِ لغوشده). فقط نخستین وضعیتِ [V3RowTone.DANGER] رنگِ خطر می‌گیرد.
 */
data class V3RowMeta(
  val amount: String? = null,
  val status: V3RowStatus? = null,
  val second: V3RowStatus? = null,
  val struck: Boolean = false,
)

/** نوشتهٔ خطِ مبلغ با قاعدهٔ «یک نشانِ رنگی» — خالص تا آزمون هم بسنجد */
fun v3MetaText(c: PhoneV3Colors, meta: V3RowMeta): AnnotatedString = buildAnnotatedString {
  meta.amount?.let { a ->
    withStyle(SpanStyle(color = if (meta.struck) c.inkMuted else c.ink, textDecoration = if (meta.struck) TextDecoration.LineThrough else null, fontFeatureSettings = "tnum")) { append(a) }
  }
  var accent = false
  listOfNotNull(meta.status, meta.second).forEachIndexed { i, st ->
    if (meta.amount != null || i > 0) withStyle(SpanStyle(color = c.inkMuted)) { append(V3_ROW_DOT) }
    val loud = st.tone == V3RowTone.DANGER && !accent
    if (loud) accent = true
    withStyle(SpanStyle(color = if (loud) v3RowToneColor(c, V3RowTone.DANGER) else c.inkMuted)) { append(st.text) }
  }
}

/** خطِ مبلغِ یک‌خطی — همان [v3MetaText] */
@Composable
fun V3RowMetaLine(meta: V3RowMeta, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) {
  Text(
    v3MetaText(PhoneV3.colors, meta),
    style = V3Type.callout,
    textAlign = align,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    modifier = modifier.testTag(V3DataRowSpec.META_TAG),
  )
}

/**
 *  ردیفِ فهرستِ داده (کالبدِ بالا). [secondary] خطِ دوم (یا [secondaryContent] وقتی بخشی از آن زدنی است، مثلِ «#۱۲۴۵»)؛
 *  [meta] خطِ مبلغ (یا [metaContent] برای خطِ مبلغِ ویژهٔ تاریخچه با واژهٔ رنگیِ پرداخت). [action] تنها قرصِ کار؛ null ⇒
 *  پیکان وقتی ردیف زدنی است. کشیدن، نگه‌داشتن و انتخابِ گروهی همان `V3ListRow` است.
 */
@Composable
fun V3DataRow(
  title: String,
  modifier: Modifier = Modifier,
  secondary: String? = null,
  secondaryContent: (@Composable () -> Unit)? = null,
  meta: V3RowMeta? = null,
  metaContent: (@Composable () -> Unit)? = null,
  icon: ImageVector? = null,
  hue: V3Hue? = null,
  danger: Boolean = false,
  leading: (@Composable () -> Unit)? = null,
  action: V3RowAction? = null,
  startActions: List<V3SwipeAction> = emptyList(),
  endActions: List<V3SwipeAction> = emptyList(),
  selected: Boolean? = null,
  onLongClick: (() -> Unit)? = null,
  /** ردیفِ فشردهٔ دوخطی ([V3DataRowSpec.DENSE_MIN_HEIGHT]) — فقط برای فهرستِ بی خطِ دوم */
  dense: Boolean = false,
  onClick: (() -> Unit)? = null,
) {
  val hasBelow = secondaryContent != null || meta != null || metaContent != null
  val pill: (@Composable () -> Unit)? = action?.let { a ->
    @Composable { V3PillAction(a.label, a.onClick, Modifier.testTag(V3DataRowSpec.ACTION_TAG), icon = a.icon, enabled = a.enabled, neutral = true) }
  }
  V3ListRow(
    title = title,
    modifier = modifier,
    subtitle = secondary.takeIf { secondaryContent == null },
    icon = icon,
    hue = hue,
    tile = true,
    leading = leading,
    trailing = pill,
    danger = danger,
    chevron = action == null,
    minHeight = if (dense) V3DataRowSpec.DENSE_MIN_HEIGHT else V3DataRowSpec.MIN_HEIGHT,
    subtitleLines = 1,
    startActions = startActions,
    endActions = endActions,
    selected = selected,
    onLongClick = onLongClick,
    below = if (!hasBelow) null else ({
      Column {
        secondaryContent?.invoke()
        if (meta != null || metaContent != null) {
          Spacer(Modifier.height(V3DataRowSpec.META_GAP))
          if (metaContent != null) metaContent() else V3RowMetaLine(meta!!)
        }
      }
    }),
    look = V3RowLook.DATA,
    dense = dense,
    onClick = onClick,
  )
}
