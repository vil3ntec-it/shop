package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.vil3ntec.tohid.core.i18n.StrCount
import ir.vil3ntec.tohid.core.stock.CountBlind
import ir.vil3ntec.tohid.core.stock.CountMerge
import ir.vil3ntec.tohid.core.stock.CountScan
import ir.vil3ntec.tohid.data.CountSheetModel
import ir.vil3ntec.tohid.data.ReportCsv
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.theme.Shop

/*
 *  «انبارگردانیِ تازه» (S20): اسکن با هر اسکن یک واحد (INV-06)، برگهٔ چند دستگاه (INV-07) و شمارشِ کور (INV-08).
 *
 *  // i18n: سه‌زبانه — نوشته‌ها در `StrCount` (LOC-06)؛ نام و واحدِ کالا داده‌اند و همان می‌مانند.
 *
 *  حساب در `core/stock/CountTools` و `CountSheetModel` است؛ ثبت همان `WarehouseEngine.stockCount` ِ امروز
 *  (`onSave`)، پس شمرده‌نشده دست نمی‌خورد و هر اختلاف «اصلاحِ موجودی» با دلیلِ «انبارگردانی» می‌شود.
 *  فایل و دوربین در مرزِ پلتفرم (`StockCountToolsWired`). پیش‌فرض خاموش (`CountToolsPref`).
 */
@Composable
fun StockCountToolsSheet(
  d: ShopData,
  blind: Boolean,
  ownSheetId: String,
  imported: List<CountMerge.Sheet>,
  notice: String?,
  onDismiss: () -> Unit,
  onExport: (ReportCsv.Sheet) -> Unit,
  onShare: (Map<String, Long>) -> Unit,
  onImport: () -> Unit,
  onRemoveSheet: (String) -> Unit,
  onSave: (Map<String, Double>, String) -> Unit,
) {
  var texts by remember { mutableStateOf(mapOf<String, String>()) }
  var search by remember { mutableStateOf("") }
  var note by remember { mutableStateOf("") }
  var scanText by remember { mutableStateOf("") }
  var message by remember { mutableStateOf<String?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  var camera by remember { mutableStateOf(false) }

  val index = remember(d) { ShopStore.barcodeIndex(d) }
  val names = remember(d) { d.products.associate { it.id to it.name } }
  val (own, bad) = remember(texts) { CountSheetModel.own(texts) }
  val merged = remember(own, imported, ownSheetId) { CountSheetModel.total(own, ownSheetId, imported) }
  val counted = remember(merged) { CountSheetModel.asDouble(merged.counts) }
  val lines = remember(d, counted, blind) { if (blind) emptyList() else WarehouseEngine.countLines(d, counted) }
  val stock = remember(d) { d.products.associate { it.id to ShopStore.stock(d, it.id) } }
  val others = remember(imported, ownSheetId) { CountMerge.merge(imported.filter { it.sheetId != ownSheetId }).counts }
  val term = search.trim()
  val shown = remember(d, term) {
    d.products.filter { it.active }
      .filter { term.isEmpty() || it.name.contains(term, ignoreCase = true) || it.barcodes.any { b -> b.contains(term) } }
      .sortedBy { it.name }
  }

  fun scan(code: String) {
    if (code.isBlank()) return
    val (next, r) = CountSheetModel.scan(texts, code, d, index)
    texts = next
    message = when (r) {
      is CountScan.Result.Counted -> StrCount.scanned(names[r.productId].orEmpty(), CountSheetModel.text(r.countedMilli))
      is CountScan.Result.Unknown -> StrCount.unknown(r.code)
      is CountScan.Result.Weighed -> StrCount.weighed(names[r.productId].orEmpty())
      is CountScan.Result.Inactive -> StrCount.inactive(names[r.productId].orEmpty())
    }
    scanText = ""
  }

  if (camera) {
    BarcodeScanSheet(onDismiss = { camera = false }, onCode = { scan(it) }, title = StrCount.scan)
  }

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
              Text(StrCount.title, style = MaterialTheme.typography.titleLarge, color = Shop.colors.text)
              Text(if (blind) StrCount.blindSubtitle else StrCount.subtitle, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
            }
            TextButton(onClick = onDismiss) { Text(StrCount.cancel) }
          }
          Spacer(Modifier.height(10.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
              value = scanText,
              onValueChange = { scanText = it },
              placeholder = { Text(StrCount.scan) },
              singleLine = true,
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
              keyboardActions = KeyboardActions(onDone = { scan(scanText) }),
              modifier = Modifier
                .weight(1f)
                .onPreviewKeyEvent { e ->
                  if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) { scan(scanText); true } else false
                },
            )
            if (!ir.vil3ntec.tohid.ui.Platform.DESKTOP) {
              IconButton(onClick = { camera = true }) { Icon(Icons.Filled.PhotoCamera, contentDescription = StrCount.camera) }
            }
          }
          message?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Shop.colors.text, modifier = Modifier.padding(vertical = 4.dp)) }
          Spacer(Modifier.height(6.dp))
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = search, onValueChange = { search = it },
              placeholder = { Text(StrCount.search) },
              singleLine = true, modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
              value = note, onValueChange = { note = it },
              placeholder = { Text(StrCount.note) },
              singleLine = true, modifier = Modifier.weight(1f),
            )
          }
          //  چند دستگاه (INV-07)
          Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            TextButton(onClick = { onShare(own) }, enabled = own.isNotEmpty()) { Text(StrCount.share) }
            TextButton(onClick = onImport) { Text(StrCount.importSheet) }
            imported.forEach { s ->
              InputChip(
                selected = false,
                onClick = { onRemoveSheet(s.sheetId) },
                label = { Text(StrCount.sheetChip(s.device.ifBlank { StrCount.thisDevice }, plain(s.counts.size))) },
                trailingIcon = { Text(StrCount.remove, style = MaterialTheme.typography.labelSmall, color = Shop.colors.danger) },
              )
            }
          }
          if (imported.isEmpty()) Text(StrCount.multiNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
          notice?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Shop.colors.text) }
          Spacer(Modifier.height(6.dp))

          LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { p ->
              val sysMilli = Math.round((stock[p.id] ?: 0.0) * 1000)
              val total = merged.counts[p.id]
              val hint = CountBlind.hint(sysMilli, total, blind)
              val other = others[p.id]
              val cell = CountSheetModel.parse(texts[p.id].orEmpty())
              Row(
                Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(10.dp))
                  .background(Shop.colors.surface)
                  .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(Modifier.weight(1f)) {
                  Text(p.name, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text, maxLines = 1)
                  val diff = hint.diffMilli
                  hint.systemMilli?.let { sys ->
                    Text(
                      StrCount.system(qty(sys / 1000.0), p.unit) + when {
                        diff == null || diff == 0L -> ""
                        diff > 0 -> " · +${qty(diff / 1000.0)}"
                        else -> " · −${qty(-diff / 1000.0)}"
                      },
                      style = MaterialTheme.typography.labelSmall,
                      color = when {
                        diff == null || diff == 0L -> Shop.colors.muted
                        diff > 0 -> Shop.colors.success
                        else -> Shop.colors.danger
                      },
                    )
                  }
                  if (other != null) {
                    Text(StrCount.others(qty(other / 1000.0)), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
                  }
                  if (p.id in merged.shared) {
                    Text(StrCount.sharedMark, style = MaterialTheme.typography.labelSmall, color = Shop.colors.warning)
                  }
                }
                OutlinedTextField(
                  value = texts[p.id].orEmpty(),
                  onValueChange = { v -> texts = texts + (p.id to v) },
                  placeholder = { Text(StrCount.counted) },
                  singleLine = true,
                  isError = cell == CountSheetModel.Cell.Bad,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                  modifier = Modifier.width(110.dp),
                )
              }
            }
          }

          error?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Shop.colors.danger, modifier = Modifier.padding(vertical = 6.dp)) }
          Spacer(Modifier.height(8.dp))
          Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              StrCount.summaryCounted(plain(merged.counts.size)) +
                if (blind) "" else " · " + StrCount.summaryDiff(plain(lines.size), money(lines.sumOf { it.value })),
              style = MaterialTheme.typography.labelSmall,
              color = Shop.colors.muted,
              maxLines = 2,
              modifier = Modifier.weight(1f),
            )
            //  شمارشِ کور خروجیِ اختلاف ندارد
            if (!blind) {
              OutlinedButton(onClick = { onExport(ReportCsv.stockCount(lines, todayIso())) }, enabled = lines.isNotEmpty()) { Text(StrCount.csv) }
            }
            TohidButton(
              text = StrCount.save,
              icon = Icons.Filled.Inventory2,
              enabled = merged.counts.isNotEmpty(),
              onClick = {
                if (bad != null) {
                  error = StrCount.badNumber(names[bad].orEmpty())
                } else {
                  error = null
                  onSave(counted, note)
                }
              },
            )
          }
          Spacer(Modifier.height(10.dp))
        }
      }
    }
  }
}

/** بخشِ «تنظیمات» — خواندن و نوشتنِ کلیدها در مرزِ پلتفرم است (`CountToolsSettingsWired`) */
@Composable
fun CountToolsSettings(on: Boolean, blind: Boolean, onChange: (Boolean) -> Unit, onBlind: (Boolean) -> Unit) {
  SettingsSection(
    icon = Icons.Filled.Inventory2,
    title = StrCount.settingsTitle,
    subtitle = if (on) StrCount.settingsOn else StrCount.off,
    tint = if (on) Shop.colors.success else Shop.colors.muted,
  ) {
    Text(StrCount.settingsNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf("off" to StrCount.off, "on" to StrCount.on),
      selected = if (on) "on" else "off",
      onSelect = { onChange(it == "on") },
    )
    if (on) {
      Spacer(Modifier.height(14.dp))
      Text(StrCount.blindTitle, style = MaterialTheme.typography.labelLarge, color = Shop.colors.text)
      Text(StrCount.blindNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
      Spacer(Modifier.height(8.dp))
      SegmentedChoice(
        options = listOf("off" to StrCount.off, "on" to StrCount.on),
        selected = if (blind) "on" else "off",
        onSelect = { onBlind(it == "on") },
      )
    }
  }
}
