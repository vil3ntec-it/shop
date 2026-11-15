package ir.vil3ntec.tohid.ui.v3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3

/** یک کارِ کوچکِ زیرِ خلاصه (چاپ، «به تفکیکِ دسته»، حالِ صندوق) */
@Immutable
data class V3SummaryAction(val label: String, val icon: ImageVector? = null, val onClick: () -> Unit)

/**
 *  خلاصهٔ آرامِ بالای هر بخش — جای کادرِ بزرگِ گرادیانی (`V3HeroCard`) که صاحب‌کار در هیچ بخشی جز خانه نمی‌خواهد
 *  (۱۴۰۵/۰۸/۲۴: «این نوع مدل سربرگ اصلاً توی برنامه نباشه»). سطحِ کارتِ معمولیِ تم، بی گرادیان و بی خطِ دور:
 *  برچسبِ کوچک، عددِ درشت، یک خطِ کم‌رنگ (شمار، دوره) و کارهای کوچکِ متنی زیرش. فقط دو وزنِ نوشته (معمولی و نیم‌پررنگ).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun V3Summary(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
  caption: String? = null,
  /** خطِ کم‌رنگِ دوم (مثلاً ساعتِ دکان زیرِ جملهٔ وضعیت) */
  note: String? = null,
  actions: List<V3SummaryAction> = emptyList(),
) {
  val c = PhoneV3.colors
  Column(
    modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(PhoneV3.groupRadius))
      .background(c.card)
      .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = if (actions.isEmpty()) 16.dp else 8.dp),
  ) {
    Text(label, style = V3Type.subhead, color = c.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(4.dp))
    Text(value, style = v3Text(if (value.length > 14) 24 else 28, FontWeight.SemiBold, 36), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    if (caption != null) {
      Spacer(Modifier.height(2.dp))
      Text(caption, style = V3Type.subhead, color = c.inkMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
    if (note != null) Text(note, style = V3Type.footnote, color = c.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    if (actions.isNotEmpty()) {
      Spacer(Modifier.height(8.dp))
      FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEach { a -> V3PillAction(a.label, a.onClick, icon = a.icon) }
      }
    }
  }
}
