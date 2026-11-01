package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.vil3ntec.tohid.core.i18n.StrLabel
import ir.vil3ntec.tohid.core.product.LabelPlan
import ir.vil3ntec.tohid.data.InternalBarcodes
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.theme.Shop

/*
 *  «چاپِ برچسب» ِ انبار (PRD-23) با ساختِ بارکدِ داخلی برای کالای بی‌بارکد (PRD-05).
 *
 *  // i18n: سه‌زبانه — نوشته‌ها در `StrLabel` (LOC-06)؛ نام و بارکدِ کالا داده‌اند و همان می‌مانند.
 *
 *  فقط جمع می‌کند و نشان می‌دهد: ترتیب و سقف‌ها در `core/product/LabelPlan`، کد در `InternalBarcodes`،
 *  چاپ در مرزِ پلتفرم (`LabelSheetWired` ⇐ `ProductLabelPrint`). پیش‌فرض خاموش (`LabelToolsPref`).
 */
@Composable
fun LabelSheet(
  d: ShopData,
  hasLabelPrinter: Boolean,
  busy: Boolean,
  message: String?,
  onDismiss: () -> Unit,
  onMakeCodes: (Set<String>) -> Unit,
  onPrint: (LabelPlan.Plan, LabelPlan.Kind, Boolean) -> Unit,
) {
  var search by remember { mutableStateOf("") }
  //  کالای انتخاب‌شده ⇒ متنِ کادرِ تعداد؛ ترتیبِ انتخاب همان ترتیبِ چاپ است
  var picked by remember { mutableStateOf(mapOf<String, String>()) }
  var kind by remember { mutableStateOf(LabelPlan.Kind.BARCODE) }
  var a4 by remember { mutableStateOf(!hasLabelPrinter) }
  val toA4 = a4 || !hasLabelPrinter

  val term = search.trim()
  val shown = remember(d, term) {
    d.products.filter { it.active }
      .filter { term.isEmpty() || it.name.contains(term, ignoreCase = true) || it.barcodes.any { b -> b.contains(term) } }
      .sortedBy { it.name }
  }
  val byId = remember(d) { d.products.associateBy { it.id } }
  val plan = remember(picked, kind, byId) {
    LabelPlan.plan(
      picked.mapNotNull { (id, text) ->
        val p = byId[id] ?: return@mapNotNull null
        LabelPlan.Item(
          productId = id,
          copies = ir.vil3ntec.tohid.latinDigits(text.trim()).toIntOrNull() ?: 0,
          hasCode = p.barcodes.any { it.isNotBlank() },
          weighed = SalesEngine.isFractionalUnit(p.unit),
        )
      },
      kind,
    )
  }
  val missing = remember(picked, d) { InternalBarcodes.missing(d, picked.keys) }

  //  بلندیِ نوارهای سیستم بیرونِ Dialog خوانده می‌شود؛ درونش صفر است (همان قاعدهٔ `BulkFrame`)
  val bars = WindowInsets.systemBars.asPaddingValues()
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
  ) {
    Surface(color = Shop.colors.bg, modifier = Modifier.fillMaxSize()) {
      PageWidth {
        Column(
          Modifier
            .fillMaxSize()
            .padding(top = bars.calculateTopPadding())
            .imePadding()
            .padding(bottom = bars.calculateBottomPadding())
            .padding(horizontal = 16.dp),
        ) {
          Spacer(Modifier.height(14.dp))
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text(StrLabel.title, style = MaterialTheme.typography.titleLarge, color = Shop.colors.text)
              Text(StrLabel.subtitle, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
            }
            TextButton(onClick = onDismiss) { Text(StrLabel.close) }
          }
          Spacer(Modifier.height(10.dp))
          OutlinedTextField(
            value = search, onValueChange = { search = it },
            placeholder = { Text(StrLabel.search) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
          )
          Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { picked = picked + shown.filter { it.id !in picked }.associate { it.id to "1" } }) { Text(StrLabel.selectShown) }
            TextButton(onClick = { picked = emptyMap() }, enabled = picked.isNotEmpty()) { Text(StrLabel.clear) }
          }

          LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(shown, key = { it.id }) { p ->
              val on = p.id in picked
              val code = p.barcodes.firstOrNull { it.isNotBlank() }
              Row(
                Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(10.dp))
                  .background(Shop.colors.surface)
                  .clickable { picked = if (on) picked - p.id else picked + (p.id to "1") }
                  .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Checkbox(checked = on, onCheckedChange = { picked = if (on) picked - p.id else picked + (p.id to "1") })
                Column(Modifier.weight(1f)) {
                  Text(p.name, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text, maxLines = 1)
                  Text(
                    code ?: StrLabel.noBarcode,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (code == null) Shop.colors.warning else Shop.colors.muted,
                    maxLines = 1,
                  )
                }
                if (on) {
                  if (SalesEngine.isFractionalUnit(p.unit)) {
                    Text(StrLabel.weighedOne, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
                  } else {
                    OutlinedTextField(
                      value = picked[p.id].orEmpty(),
                      onValueChange = { v -> picked = picked + (p.id to v.take(3)) },
                      label = { Text(StrLabel.copies) },
                      singleLine = true,
                      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                      modifier = Modifier.width(96.dp),
                    )
                  }
                }
              }
            }
          }

          Spacer(Modifier.height(8.dp))
          SegmentedChoice(
            options = listOf(LabelPlan.Kind.BARCODE.name to StrLabel.kindBarcode, LabelPlan.Kind.SHELF.name to StrLabel.kindShelf),
            selected = kind.name,
            onSelect = { k -> kind = LabelPlan.Kind.valueOf(k) },
          )
          Spacer(Modifier.height(6.dp))
          if (hasLabelPrinter) {
            SegmentedChoice(
              options = listOf("printer" to StrLabel.toPrinter, "a4" to StrLabel.toA4),
              selected = if (toA4) "a4" else "printer",
              onSelect = { a4 = it == "a4" },
            )
          } else {
            Text(StrLabel.noLabelPrinter, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
          }
          if (kind == LabelPlan.Kind.BARCODE && plan.missingCode.isNotEmpty()) {
            Text(StrLabel.missingCodes(plan.missingCode.size), style = MaterialTheme.typography.labelSmall, color = Shop.colors.warning)
          }
          if (plan.dropped > 0) {
            Text(StrLabel.dropped(plan.dropped, LabelPlan.MAX_TOTAL), style = MaterialTheme.typography.labelSmall, color = Shop.colors.warning)
          }
          if (message != null) {
            Text(message, style = MaterialTheme.typography.labelMedium, color = Shop.colors.text, modifier = Modifier.padding(vertical = 4.dp))
          }
          Spacer(Modifier.height(8.dp))
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (missing.isNotEmpty()) {
              OutlinedButton(onClick = { onMakeCodes(missing.map { it.id }.toSet()) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text(StrLabel.makeCodes(missing.size), maxLines = 2)
              }
            } else {
              Spacer(Modifier.weight(1f))
            }
            TohidButton(
              text = StrLabel.printN(plan.labels.size),
              onClick = { onPrint(plan, kind, toA4) },
              enabled = !plan.isEmpty && !busy,
              icon = Icons.Filled.Print,
              busy = busy,
            )
          }
          if (missing.isNotEmpty()) {
            Text(StrLabel.codesNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2, modifier = Modifier.padding(top = 4.dp))
          }
          Spacer(Modifier.height(10.dp))
        }
      }
    }
  }
}

/** بخشِ «تنظیمات» — خواندن و نوشتنِ کلید در مرزِ پلتفرم است (`LabelToolsSettingsWired`) */
@Composable
fun LabelToolsSettings(on: Boolean, onChange: (Boolean) -> Unit) {
  SettingsSection(
    icon = Icons.Filled.Print,
    title = StrLabel.settingsTitle,
    subtitle = if (on) StrLabel.settingsOn else StrLabel.off,
    tint = if (on) Shop.colors.success else Shop.colors.muted,
  ) {
    Text(StrLabel.settingsNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf("off" to StrLabel.off, "on" to StrLabel.on),
      selected = if (on) "on" else "off",
      onSelect = { onChange(it == "on") },
    )
  }
}
