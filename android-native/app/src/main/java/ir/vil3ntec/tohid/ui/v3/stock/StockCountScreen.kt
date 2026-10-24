package ir.vil3ntec.tohid.ui.v3.stock

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.Icon
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
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ReportCsv
import ir.vil3ntec.tohid.data.ShopData
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
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3Divider
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
import ir.vil3ntec.tohid.ui.v3.v3Stagger
import ir.vil3ntec.tohid.ui.screens.v3Text
import androidx.compose.ui.text.font.FontWeight

/**
 *  `stock/count` — «انبارگردانی»: جست‌وجو (چسبان زیرِ سربرگ)، یادداشت، و برای هر کالا
 *  «برنامه: ۱۲ عدد» با کادرِ عددیِ ۹۶dp و تراشهٔ زندهٔ اختلاف (+۲/−۳، با علامت، نه فقط
 *  رنگ)؛ ردیفِ شمرده نشانِ تیک می‌گیرد. نوارِ چسبانِ پایین جمع‌ها و «ثبتِ اختلاف‌ها» را
 *  دارد و ثبت از برگهٔ تأییدِ فهرستِ اختلاف‌ها می‌گذرد. نوشتهٔ ذخیره‌نشده + برگشت ⇐
 *  «دور ریخته شود؟». (`V3Form` بدنهٔ ستونی دارد؛ این فهرست تنبل است، پس همان تکه‌های
 *  کیت — `V3Scaffold` + `V3FormBar` — کنارِ هم.)
 *
 *  @param onSave null یعنی ثبت شد؛ وگرنه دلیلِ ردِ `WarehouseEngine.stockCount`
 */
@Composable
fun StockCountScreen(
  data: ShopData,
  today: String,
  onExport: (ReportCsv.Sheet) -> Unit,
  onSave: (Map<String, Double>, String) -> String?,
  onBack: () -> Unit,
  /** حالتِ آغازِ دلخواه (عکس‌های پیش‌نمایش) */
  start: CountState = CountState(),
) {
  var state by remember { mutableStateOf(start) }
  var query by remember { mutableStateOf("") }
  var confirm by remember { mutableStateOf(false) }
  var askDiscard by remember { mutableStateOf(false) }
  var failure by remember { mutableStateOf<String?>(null) }
  val lines = remember(data, state) { state.lines(data) }
  val system = remember(data) { CountState.system(data) }
  val rows = remember(data, query) { CountState.rows(data, query) }
  val stagger = rememberV3Stagger()
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
          if (bad == null) confirm = true
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
          V3TextField(state.note, { state = state.copy(note = it) }, StrStock.countNote, Modifier.fillMaxWidth())
          failure?.let { V3Message(V3Tone.ERROR, it, Modifier.padding(top = 10.dp)) }
        }
      }
      itemsIndexed(rows, key = { _, p -> p.id }) { i, p ->
        CountRow(p, system[p.id] ?: 0.0, state, draftItem().v3Stagger(stagger, p.id, i)) { v -> state = state.copy(counts = state.counts + (p.id to v)); failure = null }
      }
    }
  }

  if (confirm) ConfirmLines(lines, onDismiss = { confirm = false }) {
    failure = onSave(state.valid, state.note)
    if (failure == null) onBack()
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
  V3Card(modifier.draftResize(), radius = V3Space.cardRadius, padding = PaddingValues(0.dp)) {
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
    if (invalid) {
      Text(
        StrStock.badNumber(p.name),
        style = V3Type.footnote,
        color = PhoneV3Roles.errorText(c),
        modifier = Modifier.padding(start = 68.dp, end = 16.dp, bottom = 10.dp),
      )
    }
  }
}

/** عکسِ کالا با نشانِ «شمرده» که با فنر روی گوشه‌اش می‌نشیند */
@Composable
private fun CountedThumb(p: Product, counted: Boolean) {
  val c = PhoneV3.colors
  val brand = PhoneV3Roles.button(c, V3ButtonKind.PRIMARY)
  Box {
    V3ProductThumb(p.id, p.name, size = 44.dp)
    AnimatedVisibility(
      counted,
      modifier = Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp),
      enter = DraftMotion.pop(),
      exit = DraftMotion.unpop(),
    ) {
      Box(
        Modifier.size(20.dp).clip(CircleShape).background(c.card).padding(2.dp).clip(CircleShape).background(brand.bg)
          .semantics { contentDescription = StrStock.counted },
        contentAlignment = Alignment.Center,
      ) { Icon(Icons.Outlined.Check, contentDescription = null, tint = brand.fg, modifier = Modifier.size(12.dp)) }
    }
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
