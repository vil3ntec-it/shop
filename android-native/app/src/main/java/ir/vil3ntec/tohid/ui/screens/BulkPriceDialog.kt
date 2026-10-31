package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.product.BulkPrice
import ir.vil3ntec.tohid.data.ProductImport
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  قیمتِ گروهی با پیش‌نمایشِ رنگی، هشدار و تأیید (PRD-21) و دلیلِ اجباری با نامِ کاربر در سابقه
 *  (PRD-14) — گوشیِ پهن و کامپیوتر، فقط با `ShopPolicy.productTools`.
 *
 *  همهٔ حساب در `BulkPrice` ِ core (گرد به یک افغانی با همان قاعدهٔ پروژه)؛ این‌جا فقط کادر و
 *  رنگ: بالا آبی، پایین زرد، همان خاکستری، رد یا زیرِ بها قرمز. پیش‌نمایشِ هشدارداده بی تیکِ
 *  «هشدارها را دیدم» اعمال نمی‌شود؛ موتور هم بی `confirmed` رد می‌کند.
 *  `onDone` نتیجهٔ موتور است؛ ذخیره با صفحهٔ صدازننده.
 */
@Composable
fun BulkPriceDialog(
  d: ShopData,
  ids: Set<String>,
  onDismiss: () -> Unit,
  onDone: (WarehouseEngine.Result) -> Unit,
) {
  val c = Shop.colors
  val context = LocalContext.current
  val me = remember { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrDefault("").trim() }
  var field by remember { mutableStateOf("sale") }
  var mode by remember { mutableStateOf("percent") }
  var amount by remember { mutableStateOf("") }
  var reason by remember { mutableStateOf("") }
  var ack by remember { mutableStateOf(false) }

  val value = ProductImport.number(amount)
  val op: BulkPrice.Op? = when {
    amount.isBlank() || value.isNaN() || value.isInfinite() -> null
    mode == "percent" -> BulkPrice.Op.Percent(Math.round(value * 100))
    mode == "add" -> BulkPrice.Op.Add(Math.round(value * 100))
    else -> BulkPrice.Op.SetTo(Math.round(value * 100))
  }?.takeIf { BulkPrice.valid(it) }
  val f = if (field == "sale") BulkPrice.Field.SALE else BulkPrice.Field.PURCHASE
  val preview = remember(d, ids, f, op) { op?.let { WarehouseEngine.bulkPricePreview(d, ids, f, it) } }
  val ready = preview != null && preview.changed > 0 && reason.isNotBlank() && (!preview.needsConfirm || ack)

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = c.surfaceSolid,
    title = { Text("قیمتِ ${plain(ids.size)} کالا", color = c.text) },
    text = {
      Column(Modifier.widthIn(min = 360.dp, max = 560.dp)) {
        SegmentedChoice(
          options = listOf("sale" to "قیمت فروش", "purchase" to "قیمت خرید"),
          selected = field,
          onSelect = { field = it; ack = false },
        )
        Spacer(Modifier.height(6.dp))
        SegmentedChoice(
          options = listOf("percent" to "درصد", "add" to "افزودن/کاستن", "set" to "همه یک مبلغ"),
          selected = mode,
          onSelect = { mode = it; ack = false },
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
          value = amount,
          onValueChange = { amount = it; ack = false },
          label = { Text(if (mode == "percent") "درصد (مثلاً ۱۰ یا -۵)" else "افغانی (منفی یعنی کم)") },
          singleLine = true,
          isError = amount.isNotBlank() && op == null,
          modifier = Modifier.fillMaxWidth(),
        )
        Text("گرد به یک افغانی. ردیفِ منفی اعمال نمی‌شود.", style = MaterialTheme.typography.labelSmall, color = c.muted)
        if (preview != null) {
          Spacer(Modifier.height(6.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill("${plain(preview.changed)} عوض می‌شود", PillTone.INFO)
            if (preview.warned > 0) { Spacer(Modifier.width(6.dp)); StatusPill("${plain(preview.warned)} هشدار", PillTone.WARNING) }
            if (preview.blocked > 0) { Spacer(Modifier.width(6.dp)); StatusPill("${plain(preview.blocked)} رد", PillTone.DANGER) }
          }
          Spacer(Modifier.height(6.dp))
          LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp).border(1.dp, c.border, Shape.card)) {
            items(preview.rows, key = { it.id }) { r ->
              Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(r.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(8.dp))
                StatusPill("${money(r.oldMinor / 100.0)} ⇐ ${money(r.newMinor / 100.0)}", toneOf(r.tone))
              }
              if (r.warns.isNotEmpty()) {
                Text(
                  r.warns.joinToString("، ") { warnText(it) },
                  Modifier.padding(start = 10.dp, end = 10.dp, bottom = 6.dp),
                  style = MaterialTheme.typography.bodySmall,
                  color = if (r.tone == BulkPrice.Tone.DANGER) c.danger else c.warning,
                )
              }
              HorizontalDivider(color = c.border)
            }
          }
          if (preview.needsConfirm) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { ack = !ack }) {
              Checkbox(checked = ack, onCheckedChange = { ack = it })
              Text("هشدارها را دیدم؛ همین قیمت‌ها را بگذار", style = MaterialTheme.typography.bodySmall, color = c.text)
            }
          }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
          value = reason,
          onValueChange = { reason = it },
          label = { Text("دلیل (اجباری؛ با نامِ شما در سابقه می‌ماند)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val o = op ?: return@Button
          onDone(
            WarehouseEngine.bulkPrice(
              d, ids, f, o, 100, confirmed = ack, by = me, reason = reason,
              today = ir.vil3ntec.tohid.todayIso(), now = System.currentTimeMillis(), newId = ::newId,
            )
          )
        },
        enabled = ready,
      ) { Text("بگذار") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
  )
}

private fun toneOf(t: BulkPrice.Tone): PillTone = when (t) {
  BulkPrice.Tone.UP -> PillTone.INFO
  BulkPrice.Tone.DOWN -> PillTone.WARNING
  BulkPrice.Tone.SAME -> PillTone.NEUTRAL
  BulkPrice.Tone.DANGER -> PillTone.DANGER
}

private fun warnText(w: BulkPrice.Warn): String = when (w) {
  BulkPrice.Warn.NEGATIVE -> "منفی می‌شود — اعمال نمی‌شود"
  BulkPrice.Warn.ZERO -> "صفر می‌شود"
  BulkPrice.Warn.BELOW_COST -> "زیرِ بهای خرید"
  BulkPrice.Warn.BIG_CHANGE -> "تغییرِ ۵۰٪ یا بیشتر"
}
