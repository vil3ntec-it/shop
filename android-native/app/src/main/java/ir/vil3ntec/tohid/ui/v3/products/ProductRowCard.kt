package ir.vil3ntec.tohid.ui.v3.products

import ir.vil3ntec.tohid.ui.v3.v3Shake
import ir.vil3ntec.tohid.ui.v3.V3Collapse
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Space
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3IconButton
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.v3Pressable

/**
 *  کارتِ یک کالا در فرمِ چندردیفی (r16). باز = همهٔ کادرها؛ بسته = یک خط
 *  «۱ · بیسکویت شکلاتی · ۲۰ ؋» با قرصِ «آماده/ناقص». فقط یک کارت باز است.
 *  «…» ⇐ تکثیر یا حذفِ ردیف. `shake` که بالا برود کارت یک بار می‌لرزد (نخستین
 *  ردیفِ خراب پس از «ذخیره»، `v3Shake`؛ با «کاهشِ حرکت» فقط لرزشِ گوشی).
 */
@Composable
internal fun ProductRowCard(
  number: Int,
  row: ProductRowDraft,
  expanded: Boolean,
  ready: Boolean,
  canRemove: Boolean,
  shake: Int,
  onExpand: () -> Unit,
  onDuplicate: () -> Unit,
  onRemove: () -> Unit,
  modifier: Modifier = Modifier,
  fields: @Composable ColumnScope.() -> Unit,
) {
  val c = PhoneV3.colors
  var menu by remember { mutableStateOf(false) }
  val shape = RoundedCornerShape(V3Space.featuredRadius)
  Column(
    modifier
      .v3Shake(shake.takeIf { it > 0 })
      .fillMaxWidth()
      .clip(shape)
      .background(c.card)
      .border(if (expanded) 1.5.dp else 1.dp, if (expanded) PhoneV3Roles.focus(c).copy(alpha = 0.55f) else c.hairline, shape),
  ) {
    Row(
      Modifier.fillMaxWidth().then(if (expanded) Modifier else Modifier.v3Pressable(label = row.name.ifBlank { StrProducts.untitled }, onClick = onExpand))
        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(summary(number, row, expanded), style = V3Type.body.copy(fontWeight = FontWeight.SemiBold), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
      Spacer(Modifier.width(8.dp))
      if (ready) V3StatusPill(V3Status.DELIVERED, StrProducts.ready) else V3StatusPill(V3Status.WARNING, StrProducts.incomplete)
      Spacer(Modifier.width(6.dp))
      V3IconButton(Icons.Outlined.MoreHoriz, StrProducts.rowActions, size = 36.dp, iconSize = 20.dp) { menu = true }
    }
    V3Collapse(expanded) {
      Column(Modifier.fillMaxWidth()) {
        V3Divider()
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), content = fields)
      }
    }
  }
  if (menu) {
    V3Sheet(onDismiss = { menu = false }, title = summary(number, row, expanded = false)) { closer ->
      V3Card(padding = PaddingValues(vertical = 4.dp)) {
        V3ListRow(StrProducts.duplicateRow, icon = Icons.Outlined.ContentCopy, chevron = false, onClick = { closer.close(onDuplicate) })
        if (canRemove) {
          V3Divider(start = 64.dp)
          V3ListRow(StrProducts.removeRow, icon = Icons.Outlined.DeleteOutline, danger = true, chevron = false, onClick = { closer.close(onRemove) })
        }
      }
    }
  }
}

/** «۱ · بیسکویت شکلاتی · ۲۰ ؋» (کارتِ باز فقط شماره و نام) */
private fun summary(number: Int, row: ProductRowDraft, expanded: Boolean): String = buildString {
  append(number)
  append(" · ")
  append(row.name.trim().ifBlank { StrProducts.untitled })
  val sale = row.sale.toDoubleOrNull()
  if (!expanded && sale != null) append(" · ${money(sale)} ؋")
}

/** سرِ هر گروهِ کادرها درونِ کارتِ ردیف («اصلی»، «قیمت»، «انبار») */
internal val CardSection: FieldSection = { title, content ->
  val c = PhoneV3.colors
  Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
    Text(title, style = V3Type.caption, color = c.inkMuted, modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
    content()
  }
}
