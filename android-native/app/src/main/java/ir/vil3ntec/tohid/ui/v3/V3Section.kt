package ir.vil3ntec.tohid.ui.v3

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.screens.V3Card
import ir.vil3ntec.tohid.ui.screens.V3GroupTitle
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles

/**
 *  بخشِ گروهی — سرخطِ خاکستری **بیرونِ** کارت، کارتِ سفیدِ گرد با مرزِ دیدنی
 *  (`V3Card`: سطحِ یک روی زمینهٔ سطحِ صفر، VILL3N §۶)، و پانویسِ اختیاری.
 *  `action` پیوندِ کوچکِ برندرنگ در پایانِ سرخط («همه»، «ویرایش»).
 *  ردیف‌ها داخلش `V3ListRow`اند و میانشان `V3Divider`.
 */
@Composable
fun V3Section(
  title: String?,
  modifier: Modifier = Modifier,
  footer: String? = null,
  action: String? = null,
  onAction: (() -> Unit)? = null,
  padding: PaddingValues = PaddingValues(vertical = 4.dp),
  content: @Composable ColumnScope.() -> Unit,
) {
  val c = PhoneV3.colors
  Column(modifier.fillMaxWidth().padding(top = 16.dp)) {
    if (title != null || action != null) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        V3GroupTitle(title.orEmpty(), Modifier.weight(1f))
        if (action != null && onAction != null) {
          Text(
            action,
            style = v3Text(14, FontWeight.SemiBold, 20),
            color = PhoneV3Roles.brandText(c),
            modifier = Modifier.padding(bottom = 9.dp).v3Pressable(label = action, onClick = onAction).padding(horizontal = 4.dp),
          )
        }
      }
    }
    V3Card(padding = padding, content = content)
    if (footer != null) {
      Text(
        footer,
        style = v3Text(12, FontWeight.Medium, 17),
        color = c.inkMuted,
        modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp),
      )
    }
  }
}
