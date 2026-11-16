package ir.vil3ntec.tohid.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.theme.Elevation
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.softCard
import ir.vil3ntec.tohid.core.i18n.Str

/** یک کارِ گروهی. `destructive` ⇒ رنگِ خطر؛ تأیید و دلیل کارِ صفحه است (`BulkConfirmDialog`) */
data class BulkAction(
  val label: String,
  val icon: ImageVector,
  val destructive: Boolean = false,
  val onClick: () -> Unit,
)

/**
 *  نوارِ کارِ گروهی که پس از انتخاب پایینِ صفحه شناور می‌شود —
 *  «۳ انتخاب‌شده · خروجی · فعال · حذف · ×» (VISUAL-DIRECTION §۳ بند ۵).
 *
 *  فقط کارهایی که واقعاً هست در `actions` می‌آیند؛ کاری که مجوزش نیست
 *  اصلاً فرستاده نمی‌شود. × (و Esc در صفحه) انتخاب را پاک می‌کند.
 */
@Composable
fun BulkActionBar(
  count: Int,
  actions: List<BulkAction>,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val c = Shop.colors
  val h = if (Platform.DESKTOP) 36.dp else 48.dp
  //  نسخهٔ ۲: نوارِ شناورِ قرصی با سایهٔ نرمِ بلند، بی خطِ دور؛ کارها دکمهٔ نرمِ پرشده
  Row(
    modifier
      .softCard(c, Shape.chip, fill = c.surfaceSolid, elevation = Elevation.lifted)
      .padding(horizontal = 8.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Text(
      Str.selectedCount(count),
      style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = c.primaryDark,
      modifier = Modifier.clip(Shape.chip).background(c.primaryTint).padding(horizontal = 12.dp, vertical = 6.dp),
    )
    for (a in actions) {
      val fg = if (a.destructive) c.danger else c.text
      Row(
        Modifier
          .heightIn(min = h)
          .clip(Shape.chip)
          .background(if (a.destructive) c.dangerTint else c.surface2)
          .clickable(role = Role.Button, onClick = a.onClick)
          .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(a.icon, null, tint = fg, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(a.label, style = MaterialTheme.typography.labelLarge, color = fg, fontWeight = FontWeight.Medium, maxLines = 1)
      }
    }
    Icon(
      Icons.Filled.Close, null, tint = c.muted,
      modifier = Modifier
        .size(h)
        .clip(Shape.chip)
        .clickable(role = Role.Button, onClick = onClose)
        .semantics { contentDescription = Str.clearSelection }
        .padding(8.dp),
    )
  }
}

/**
 *  پیش‌نمایشِ کارِ گروهی پیش از اجرا: تعداد، اثرِ هر ردیف، محدودیت‌ها و —
 *  برای کارِ خطرناک — دلیلِ اجباری (v3 §۱۹.۵ بندهای ۴ و ۱۰).
 *
 *  دکمهٔ تأیید وقتی هیچ ردیفی عوض نمی‌شود یا دلیل کم است خاموش می‌ماند.
 */
@Composable
fun BulkConfirmDialog(
  title: String,
  confirmLabel: String,
  rows: List<BulkPreviewRow>,
  onConfirm: (reason: String) -> Unit,
  onDismiss: () -> Unit,
  destructive: Boolean = false,
  /** خطِ توضیح زیرِ عنوان: برگشت‌پذیر است یا نه، چه می‌ماند */
  note: String? = null,
  /** مثلاً «۲ مورد از انتخاب‌ها در فیلترِ فعلی دیده نمی‌شوند» */
  warnings: List<String> = emptyList(),
) {
  //  کامپیوتر (`claude/e-desk-d3c`): تنها کاربر «کالاها» ِ کامپیوتر است ⇒ پنجره و دکمه‌های `ui/desk`؛ ردیفِ آرام (وضعیت به واژه، بی قرص)
  val c = ir.vil3ntec.tohid.ui.desk.Desk.colors
  var reason by remember { mutableStateOf("") }
  val changing = rows.count { it.changes }
  val ready = changing > 0 && (!destructive || BulkModel.reasonValid(reason))
  ir.vil3ntec.tohid.ui.desk.DeskDialog(
    title = title,
    onDismiss = onDismiss,
    subtitle = note,
    danger = destructive,
    width = ir.vil3ntec.tohid.ui.desk.DeskDialogSize.wide,
    scroll = false,
    confirm = ir.vil3ntec.tohid.ui.desk.DeskDialogAction(confirmLabel, enabled = ready, danger = destructive) { onConfirm(reason.trim()) },
    dismiss = ir.vil3ntec.tohid.ui.desk.DeskDialogAction(Str.cancel, onClick = onDismiss),
  ) {
    ir.vil3ntec.tohid.ui.desk.DeskDialogText(
      Str.bulkWillChange(changing) + if (rows.size > changing) " · " + Str.bulkUnchanged(rows.size - changing) else "",
      strong = true,
    )
    for (w in warnings) ir.vil3ntec.tohid.ui.desk.DeskDialogText(w, color = c.warning)
    BulkDeskList(rows.size) { i ->
      val r = rows[i]
      BulkDeskRow(r.label, r.note, if (r.changes) Str.change else Str.unchanged, strong = r.changes, color = if (r.changes && destructive) c.negative else null)
    }
    if (destructive) {
      ir.vil3ntec.tohid.ui.desk.DeskDialogField(
        Str.reasonRequired, reason, { reason = it },
        isError = reason.isNotEmpty() && !BulkModel.reasonValid(reason),
      )
    }
  }
}

/** گزارشِ نتیجهٔ هر ردیف پس از کارِ گروهی — ناموفق‌ها اول */
@Composable
fun BulkResultDialog(title: String, results: List<BulkRowResult>, onDismiss: () -> Unit) {
  val c = ir.vil3ntec.tohid.ui.desk.Desk.colors
  val (ok, failed) = BulkModel.summary(results)
  val ordered = remember(results) { BulkModel.ordered(results) }
  ir.vil3ntec.tohid.ui.desk.DeskDialog(
    title = title,
    onDismiss = onDismiss,
    subtitle = Str.okCount(ok) + if (failed > 0) " · " + Str.failedCount(failed) else "",
    width = ir.vil3ntec.tohid.ui.desk.DeskDialogSize.wide,
    scroll = false,
    confirm = ir.vil3ntec.tohid.ui.desk.DeskDialogAction(Str.close, onClick = onDismiss),
  ) {
    BulkDeskList(ordered.size) { i ->
      val r = ordered[i]
      BulkDeskRow(r.label, r.note, if (r.ok) Str.success else Str.failure, strong = !r.ok, color = if (r.ok) null else c.negative)
    }
  }
}

/** فهرستِ تنبلِ ردیف‌های پنجرهٔ گروهی — کادرِ خطِ مویی، بلندیِ بیشتر ۲۸۰ */
@Composable
private fun BulkDeskList(count: Int, row: @Composable (Int) -> Unit) {
  val c = ir.vil3ntec.tohid.ui.desk.Desk.colors
  LazyColumn(
    Modifier.fillMaxWidth().heightIn(max = 280.dp)
      .clip(ir.vil3ntec.tohid.ui.desk.DeskRadius.controlShape)
      .border(ir.vil3ntec.tohid.ui.desk.DeskSize.hairline, c.hairline, ir.vil3ntec.tohid.ui.desk.DeskRadius.controlShape),
  ) {
    items(count) { i -> row(i); ir.vil3ntec.tohid.ui.desk.DeskDivider() }
  }
}

/** ردیفِ آرامِ پنجرهٔ گروهی: نام (و یادداشتِ کم‌رنگ) و وضعیت به واژه در پایان */
@Composable
private fun BulkDeskRow(label: String, note: String, status: String, strong: Boolean, color: androidx.compose.ui.graphics.Color?) {
  val c = ir.vil3ntec.tohid.ui.desk.Desk.colors
  Row(
    Modifier.fillMaxWidth().heightIn(min = ir.vil3ntec.tohid.ui.desk.DeskSize.tableRow).padding(horizontal = ir.vil3ntec.tohid.ui.desk.DeskSpace.x3, vertical = ir.vil3ntec.tohid.ui.desk.DeskSpace.x1),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(label, style = ir.vil3ntec.tohid.ui.desk.DeskType.body, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (note.isNotEmpty()) Text(note, style = ir.vil3ntec.tohid.ui.desk.DeskType.secondary, color = c.inkSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.width(ir.vil3ntec.tohid.ui.desk.DeskSpace.x3))
    Text(status, style = if (strong) ir.vil3ntec.tohid.ui.desk.DeskType.bodyStrong else ir.vil3ntec.tohid.ui.desk.DeskType.secondary, color = color ?: if (strong) c.ink else c.inkSecondary, maxLines = 1)
  }
}
