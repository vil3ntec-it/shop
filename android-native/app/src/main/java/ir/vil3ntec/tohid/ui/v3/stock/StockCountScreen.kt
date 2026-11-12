package ir.vil3ntec.tohid.ui.v3.stock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrCount
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ReportCsv
import ir.vil3ntec.tohid.core.stock.CountScan
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Space
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Action
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3FormBar
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3ProductThumb
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3SearchField
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3ScanGuard
import ir.vil3ntec.tohid.ui.v3.V3Collapse
import ir.vil3ntec.tohid.ui.v3.V3SuccessCheck
import ir.vil3ntec.tohid.ui.v3.v3AnimateItem
import ir.vil3ntec.tohid.ui.v3.v3Shake
import androidx.compose.runtime.mutableIntStateOf
import ir.vil3ntec.tohid.ui.v3.v3Stagger
import ir.vil3ntec.tohid.ui.screens.v3Text
import androidx.compose.ui.text.font.FontWeight

/**
 *  `stock/count` — «انبارگردانی»: جست‌وجو (چسبان زیرِ سربرگ)، یادداشت، و برای هر کالا
 *  «برنامه: ۱۲ عدد» با کادرِ عددیِ ۹۶dp و تراشهٔ زندهٔ اختلاف (+۲/−۳، با علامت، نه فقط
 *  رنگ)؛ ردیفِ شمرده نشانِ تیک می‌گیرد. نوارِ چسبانِ پایین جمع‌ها و «ثبتِ اختلاف‌ها» را
 *  دارد و ثبت از برگهٔ تأییدِ فهرستِ اختلاف‌ها می‌گذرد. نوشتهٔ ذخیره‌نشده + برگشت ⇐
 *  «دور ریخته شود؟». (`V3Form` بدنهٔ ستونی دارد؛ این فهرست تنبل است، پس همان تکه‌های
 *  کیت — `V3Scaffold` + `V3FormBar` — کنارِ هم.) INV-05: تراشه‌های صافیِ دسته، تأمین‌کننده، برند و قفسه (همان
 *  `CountFilter` ِ «انبارگردانیِ تازه»؛ هر ردیف فقط وقتی دست‌کم یک کالای فعال آن را دارد) فقط فهرستِ دیده‌شده را کم می‌کنند.
 *
 *  @param onSave null یعنی ثبت شد؛ وگرنه دلیلِ ردِ `WarehouseEngine.stockCount`. سومی (INV-10): لحظهٔ آخرین
 *  نوشتن یا اسکنِ هر کالا روی همین صفحه، با ساعتِ همین دستگاه
 */
@Composable
fun StockCountScreen(
  data: ShopData,
  today: String,
  onExport: (ReportCsv.Sheet) -> Unit,
  onSave: (Map<String, Double>, String, Map<String, Long>) -> String?,
  onBack: () -> Unit,
  /** INV-10: ساعتِ لحظهٔ هر کالا (`StockWriter.now`) */
  clock: () -> Long,
  /** حالتِ آغازِ دلخواه (عکس‌های پیش‌نمایش) */
  start: CountState = CountState(),
) {
  var state by remember { mutableStateOf(start) }
  //  INV-10: لحظهٔ آخرین تغییرِ عددِ هر کالا (نوشتن یا اسکن) ⇒ ردیفِ شمارش (`WarehouseEngine.stockCount(countedAt)`)
  var at by remember { mutableStateOf(emptyMap<String, Long>()) }
  //  «اسکن‌های در انتظار» ⇒ انبارگردانی: شمارِ بارکدخوان به خانهٔ هر کالا افزوده می‌شود (`ScanApply.counts`)
  androidx.compose.runtime.LaunchedEffect(Unit) {
    ir.vil3ntec.tohid.scan.ScanHandoff.take(ir.vil3ntec.tohid.core.hw.ScanTarget.COUNT)?.let { h ->
      val next = ir.vil3ntec.tohid.scan.ScanApply.counts(state.counts, data, h.scans).counts
      val now = clock()
      at = at + next.keys.filter { next[it] != state.counts[it] }.associateWith { now }
      state = state.copy(counts = next)
    }
  }
  var query by remember { mutableStateOf("") }
  //  INV-05: صافیِ دیدن (شمرده‌ها، جمع‌ها، CSV و ثبت همان)
  var scope by remember { mutableStateOf(CountScope()) }
  val options = remember(data) { CountState.options(data) }
  var confirm by remember { mutableStateOf(false) }
  var askDiscard by remember { mutableStateOf(false) }
  var failure by remember { mutableStateOf<String?>(null) }
  //  بارکدخوان بی کادرِ فوکوس‌دار (پنجرهٔ کامپیوتر، بارکدخوانِ وصل‌شده، گوشیِ جفت‌شده) همین صفحه را می‌گیرد، نه صندوق: رفتن به
  //  صندوق صفحه و شمارشِ ذخیره‌نشده را می‌بست. «ابزارهای تازهٔ دستگاه» ⇒ +۱ (`CountState.scan`)؛ خاموش ⇒ فقط پیام، بی تغییرِ عدد
  var scanNote by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
  val index = remember(data) { ShopStore.barcodeIndex(data) }
  val onScan by androidx.compose.runtime.rememberUpdatedState<(String) -> Unit>({ code ->
    val (next, r) = state.scan(data, code, index, ir.vil3ntec.tohid.print.HwTools.on.value)
    if (r is CountScan.Result.Counted) { state = next; at = at + (r.productId to clock()); failure = null }
    scanNote = (r is CountScan.Result.Counted) to CountState.scanMessage(r, data)
  })
  val scanOwner = remember { Any() }
  androidx.compose.runtime.DisposableEffect(scanOwner) {
    ir.vil3ntec.tohid.scan.ScanRouter.register(scanOwner) { code -> onScan(code); true }
    onDispose { ir.vil3ntec.tohid.scan.ScanRouter.unregister(scanOwner) }
  }
  val lines = remember(data, state) { state.lines(data) }
  val system = remember(data) { CountState.system(data) }
  val rows = remember(data, query, scope) { CountState.rows(data, query, scope) }
  val stagger = rememberV3Stagger()
  var shake by remember { mutableIntStateOf(0) }
  val back = { if (state.dirty) askDiscard = true else onBack() }
  BackHandler(enabled = state.dirty) { askDiscard = true }

  V3Scaffold(
    title = StrStock.menuCount,
    onBack = back,
    largeTitle = false,
    notice = null,
    actions = listOf(V3Action(Icons.Outlined.FileDownload, StrStock.exportCsv) { onExport(ReportCsv.stockCount(lines, today)) }),
    search = { V3SearchField(query, { query = it }, StrStock.searchHint) },
    bottomBar = {
      V3FormBar(
        primary = StrStock.saveDiffs,
        onPrimary = {
          val bad = state.firstInvalid(data)
          failure = bad?.let { StrStock.badNumber(it.name) }
          if (bad == null) confirm = true else shake++
        },
        primaryEnabled = lines.isNotEmpty(),
        summary = StrStock.countSummary(money(state.parsed.size.toDouble()), money(lines.size.toDouble()), money(lines.sumOf { it.value })),
      )
    },
  ) { pad ->
    LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding()),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      item(key = "head") {
        Column {
          Text(StrStock.countHelper, style = V3Type.footnote, color = PhoneV3.colors.inkMuted)
          Spacer(Modifier.height(10.dp))
          if (!options.isEmpty) {
            CountScopeChips(options, scope) { scope = it }
            Spacer(Modifier.height(10.dp))
          }
          V3TextField(state.note, { state = state.copy(note = it) }, StrStock.countNote, Modifier.fillMaxWidth())
          failure?.let { V3Message(V3Tone.ERROR, it, Modifier.padding(top = 10.dp).v3Shake(shake)) }
          scanNote?.let { (ok, text) -> V3Message(if (ok) V3Tone.SUCCESS else V3Tone.WARNING, text, Modifier.padding(top = 10.dp)) }
        }
      }
      itemsIndexed(rows, key = { _, p -> p.id }) { i, p ->
        CountRow(p, system[p.id] ?: 0.0, state, Modifier.v3AnimateItem(this).v3Stagger(stagger, p.id, i)) { v -> state = state.copy(counts = state.counts + (p.id to v)); at = at + (p.id to clock()); failure = null }
      }
    }
  }

  if (confirm) ConfirmLines(lines, onDismiss = { confirm = false }) {
    failure = onSave(state.valid, state.note, at)
    if (failure == null) onBack() else shake++
  }
  if (askDiscard) {
    V3ConfirmSheet(StrKit.discardTitle, StrKit.discardBody, StrKit.discard, onConfirm = onBack, onDismiss = { askDiscard = false })
  }
}

@Composable
private fun CountRow(p: Product, system: Double, state: CountState, modifier: Modifier, onValue: (String) -> Unit) {
  val c = PhoneV3.colors
  val text = state.counts[p.id].orEmpty()
  val invalid = state.invalid(p.id)
  val diff = state.diff(p.id, system)
  val counted = text.isNotBlank() && !invalid
  V3Card(modifier, radius = V3Space.cardRadius, padding = PaddingValues(0.dp)) {
    V3ListRow(
      title = p.name,
      subtitle = StrStock.systemQty(listOf(qty(system), p.unit).filter { it.isNotBlank() }.joinToString(" ")),
      leading = { CountedThumb(p, counted) },
      chevron = false,
      minHeight = 64.dp,
      trailing = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (diff != null && diff != 0.0) {
            V3StatusPill(if (diff > 0) V3Status.DELIVERED else V3Status.CANCELLED, (if (diff > 0) "+" else "−") + qty(kotlin.math.abs(diff)))
            Spacer(Modifier.width(8.dp))
          }
          DraftCountField(text, onValue, p.name, invalid)
        }
      },
    )
    V3Collapse(invalid) {
      Text(
        StrStock.badNumber(p.name),
        style = V3Type.footnote,
        color = PhoneV3Roles.errorText(c),
        modifier = Modifier.padding(start = 68.dp, end = 16.dp, bottom = 10.dp),
      )
    }
  }
}

/** INV-05: ردیف‌های تراشهٔ صافی — «همه…» و گزینه‌ها؛ زدنِ دوبارهٔ تراشهٔ انتخاب‌شده صافی را برمی‌دارد */
@Composable
private fun CountScopeChips(o: CountScopeOptions, scope: CountScope, onScope: (CountScope) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (o.categories.isNotEmpty()) ScopeRow(StrCount.allCategories, o.categories, scope.category) { onScope(scope.copy(category = it)) }
    if (o.suppliers.isNotEmpty()) ScopeRow(StrCount.allSuppliers, o.suppliers, scope.supplierId) { onScope(scope.copy(supplierId = it)) }
    if (o.brands.isNotEmpty()) ScopeRow(StrCount.allBrands, o.brands, scope.brand) { onScope(scope.copy(brand = it)) }
    if (o.shelves.isNotEmpty()) ScopeRow(StrCount.allShelves, o.shelves, scope.shelf) { onScope(scope.copy(shelf = it)) }
  }
}

@Composable
private fun ScopeRow(all: String, options: List<Pair<String, String>>, picked: String?, onPick: (String?) -> Unit) {
  V3ChipRow(contentPadding = PaddingValues(0.dp)) {
    V3Chip(all, selected = picked == null, onClick = { onPick(null) })
    options.forEach { (id, label) -> V3Chip(label, selected = picked == id, onClick = { onPick(if (picked == id) null else id) }) }
  }
}

/** عکسِ کالا با تیکِ «شمرده» (`V3SuccessCheck`، فنرِ شاد و کشیدنِ تیک) روی گوشه‌اش */
@Composable
private fun CountedThumb(p: Product, counted: Boolean) {
  Box(Modifier.semantics(mergeDescendants = true) { if (counted) contentDescription = StrStock.counted }) {
    V3ProductThumb(p.id, p.name, size = 44.dp)
    V3SuccessCheck(counted, Modifier.align(Alignment.BottomEnd).offset(x = 5.dp, y = 5.dp), size = 20.dp, haptic = false)
  }
}

/**
 *  کادرِ عددیِ کوچکِ ردیف (۹۶dp): همان حالت‌های `V3TextField` — عادی، تمرکز (برند)، خطا —
 *  بی برچسبِ بالای کادر تا ردیف کوتاه بماند. پیشنهادِ کیت (`Draft`): «کادرِ درونِ ردیف».
 */
@Composable
internal fun DraftCountField(value: String, onValue: (String) -> Unit, label: String, error: Boolean) {
  val c = PhoneV3.colors
  val shape = RoundedCornerShape(V3Space.controlRadius)
  val ring = if (error) PhoneV3Roles.errorText(c) else PhoneV3Roles.inputBorder(c)
  BasicTextField(
    value = value,
    onValueChange = onValue,
    singleLine = true,
    textStyle = v3Text(16, FontWeight.SemiBold, 22).copy(color = c.ink, textAlign = TextAlign.Center),
    cursorBrush = SolidColor(PhoneV3Roles.focus(c)),
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    modifier = Modifier
      .width(96.dp)
      .heightIn(min = 44.dp)
      //  HW-05: «تعداد» است — اسکن در آن نمی‌نشیند (همان نگهبانِ `V3TextField`، فقط با «ابزارهای تازهٔ دستگاه»)؛
      //  کد فقط به صفحهٔ گوش‌دهنده (انتقال)، نه صندوق: شمارشِ نوشته‌نشده با بسته شدنِ صفحه گم می‌شد
      .v3ScanGuard(V3FieldKind.NUMBER, value, onValue, toCart = false)
      .semantics {
        contentDescription = "${StrStock.countField}: $label"
        if (error) error(StrStock.badNumber(label))
      },
    decorationBox = { inner ->
      Box(
        Modifier.width(96.dp).heightIn(min = 44.dp).clip(shape).background(PhoneV3Roles.inputFill(c))
          .border(if (error) 2.dp else 1.dp, ring, shape).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
      ) {
        if (value.isEmpty()) Text(StrStock.countField, style = V3Type.callout, color = c.inkMuted, textAlign = TextAlign.Center)
        inner()
      }
    },
  )
}

/** برگهٔ تأیید: فهرستِ اختلاف‌ها (برنامه ← شمرده و فرق) و «ثبتِ اختلاف‌ها» */
@Composable
private fun ConfirmLines(lines: List<WarehouseEngine.CountLine>, onDismiss: () -> Unit, onConfirm: () -> Unit) {
  val c = PhoneV3.colors
  V3Sheet(onDismiss = onDismiss, title = StrStock.confirmTitle) { closer ->
    Text(StrStock.confirmBody, style = V3Type.callout, color = c.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    V3Card(padding = PaddingValues(vertical = 4.dp)) {
      LazyColumn(Modifier.heightIn(max = 360.dp)) {
        items(lines, key = { it.product.id }) { l ->
          if (l !== lines.first()) V3Divider(start = 16.dp)
          V3ListRow(
            title = l.product.name,
            subtitle = StrStock.preview(qty(l.system), qty(l.counted), l.product.unit).trim(),
            value = (if (l.diff > 0) "+" else "−") + qty(kotlin.math.abs(l.diff)),
            valueColor = if (l.diff > 0) PhoneV3Roles.status(c, V3Status.DELIVERED).fg else PhoneV3Roles.errorText(c),
            tile = false,
            chevron = false,
          )
        }
      }
    }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      V3Button(StrKit.cancel, { closer.close() }, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL)
      V3Button(StrStock.saveDiffs, { closer.close(onConfirm) }, Modifier.weight(1f), haptic = true)
    }
  }
}
