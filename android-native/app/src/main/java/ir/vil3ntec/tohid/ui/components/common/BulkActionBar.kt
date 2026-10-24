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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
      "$count انتخاب‌شده",
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
        .semantics { contentDescription = "لغوِ انتخاب" }
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
  val c = Shop.colors
  var reason by remember { mutableStateOf("") }
  val changing = rows.count { it.changes }
  val ready = changing > 0 && (!destructive || BulkModel.reasonValid(reason))
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = c.surfaceSolid,
    title = { Text(title, color = c.text) },
    text = {
      Column(Modifier.widthIn(min = 360.dp, max = 560.dp)) {
        Text(
          "$changing مورد تغییر می‌کند" + if (rows.size > changing) " · ${rows.size - changing} مورد بی‌تغییر می‌ماند" else "",
          style = MaterialTheme.typography.titleSmall, color = c.text,
        )
        if (note != null) {
          Spacer(Modifier.height(4.dp))
          Text(note, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        for (w in warnings) {
          Spacer(Modifier.height(4.dp))
          Text(w, style = MaterialTheme.typography.bodySmall, color = c.warning)
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp).border(1.dp, c.border, Shape.card)) {
          items(rows, key = { it.id }) { r ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
              Text(r.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
              Spacer(Modifier.width(8.dp))
              StatusPill(if (r.changes) "تغییر" else "بی‌تغییر", if (r.changes) (if (destructive) PillTone.DANGER else PillTone.SUCCESS) else PillTone.NEUTRAL)
            }
            if (r.note.isNotEmpty()) Text(r.note, Modifier.padding(start = 10.dp, end = 10.dp, bottom = 6.dp), style = MaterialTheme.typography.bodySmall, color = c.muted)
            HorizontalDivider(color = c.border)
          }
        }
        if (destructive) {
          Spacer(Modifier.height(10.dp))
          OutlinedTextField(
            value = reason,
            onValueChange = { reason = it },
            label = { Text("دلیل (اجباری — در سابقهٔ عملیات می‌ماند)") },
            singleLine = true,
            isError = reason.isNotEmpty() && !BulkModel.reasonValid(reason),
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(reason.trim()) },
        enabled = ready,
        colors = if (destructive) ButtonDefaults.buttonColors(containerColor = c.button.danger.bg, contentColor = c.button.danger.text) else ButtonDefaults.buttonColors(),
      ) { Text(confirmLabel) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
  )
}

/** گزارشِ نتیجهٔ هر ردیف پس از کارِ گروهی — ناموفق‌ها اول */
@Composable
fun BulkResultDialog(title: String, results: List<BulkRowResult>, onDismiss: () -> Unit) {
  val c = Shop.colors
  val (ok, failed) = BulkModel.summary(results)
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = c.surfaceSolid,
    title = { Text(title, color = c.text) },
    text = {
      Column(Modifier.widthIn(min = 360.dp, max = 560.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          StatusPill("$ok موفق", PillTone.SUCCESS)
          if (failed > 0) {
            Spacer(Modifier.width(8.dp))
            StatusPill("$failed ناموفق", PillTone.DANGER)
          }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp).border(1.dp, c.border, Shape.card)) {
          items(BulkModel.ordered(results), key = { it.id }) { r ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
              Column(Modifier.weight(1f)) {
                Text(r.label, style = MaterialTheme.typography.bodyMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(r.note, style = MaterialTheme.typography.bodySmall, color = c.muted)
              }
              Spacer(Modifier.width(8.dp))
              StatusPill(if (r.ok) "موفق" else "ناموفق", if (r.ok) PillTone.SUCCESS else PillTone.DANGER)
            }
            HorizontalDivider(color = c.border)
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } },
  )
}
