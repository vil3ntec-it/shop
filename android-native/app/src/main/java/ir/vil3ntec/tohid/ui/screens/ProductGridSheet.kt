package ir.vil3ntec.tohid.ui.screens

// i18n: سه‌زبانه (`StrGrid`)

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.vil3ntec.tohid.core.i18n.StrGrid
import ir.vil3ntec.tohid.core.keymap.KeyMap
import ir.vil3ntec.tohid.core.product.DeskGrid
import ir.vil3ntec.tohid.core.product.DeskGrid.Col
import ir.vil3ntec.tohid.data.ProductGridSave
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/**
 *  جدولِ ویرایشِ درجای کالا (DeskGrid) — PRD-22، §۵ بندِ ۱۳. از «جدول» ِ نوارِ کارِ گروهیِ کالاهای
 *  کامپیوتر، فقط با `ShopPolicy.productTools` (پیش‌فرض خاموش ⇒ دکمه نیست و همان صفحهٔ پیشین). [by] نامِ
 *  کاربر برای تاریخچهٔ قیمت از صدازننده می‌آید (این صفحه وابستگیِ پلتفرمی ندارد).
 *
 *  کلیدها از `keymap.json` (دامنهٔ `productGrid`): F2 ویرایش، Enter ثبتِ خانه و پایین، Tab/Shift+Tab،
 *  Ctrl+V چسباندن از Excel، Ctrl+D تکرارِ ردیفِ بالا، Ctrl+Z برگرداندن، Ctrl+S ثبت، Esc لغو یا بستن.
 *  تایپِ حرف روی خانهٔ انتخاب‌شده ویرایش را با همان حرف آغاز می‌کند (مثلِ Excel).
 *
 *  پیش‌نمایش: خانهٔ عوض‌شده رنگی، خانهٔ نادرست سرخ با دلیل در نوارِ پایین؛ ثبت تا ایراد هست بسته است
 *  و یا همه یا هیچ (`ProductGridSave.apply`). منطقِ جدول در `core/product/DeskGrid`.
 */
@Composable
fun ProductGridSheet(store: ShopStore, d: ShopData, ids: List<String>, by: String, toast: (String) -> Unit, onClose: () -> Unit) {
  val c = Shop.colors
  val scope = rememberCoroutineScope()
  val clipboard = LocalClipboardManager.current
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  //  ترتیبِ ردیف‌ها همان ترتیبِ فهرست؛ جدول یک بار ساخته می‌شود و با همگام‌سازی از نو نمی‌شود
  var sheet by remember { mutableStateOf(DeskGrid.sheet(ProductGridSave.rows(ids.mapNotNull { id -> d.products.find { it.id == id } }))) }
  var at by remember { mutableStateOf(DeskGrid.Cell(0, Col.NAME)) }
  var editing by remember { mutableStateOf<TextFieldValue?>(null) }
  var askDiscard by remember { mutableStateOf(false) }
  val problems = remember(sheet, d) { ProductGridSave.problems(d, sheet) }
  val gridFocus = remember { FocusRequester() }
  val cellFocus = remember { FocusRequester() }
  val list = rememberLazyListState()
  val last = sheet.rows.lastIndex

  fun move(dr: Int, dc: Int) {
    if (last < 0) return
    val col = (at.col.ordinal + dc).coerceIn(0, DeskGrid.COLS.lastIndex)
    at = DeskGrid.Cell((at.row + dr).coerceIn(0, last), DeskGrid.COLS[col])
  }
  fun startEdit(seed: String? = null) {
    if (last < 0) return
    val text = seed ?: sheet.rows[at.row][at.col]
    editing = TextFieldValue(text, TextRange(text.length))
  }
  fun commit() {
    editing?.let { sheet = sheet.set(at.row, at.col, it.text) }
    editing = null
    runCatching { gridFocus.requestFocus() }
  }
  fun paste() {
    val clip = clipboard.getText()?.text.orEmpty()
    if (clip.isEmpty()) { toast(StrGrid.clipEmpty); return }
    val p = sheet.paste(at.row, at.col, clip)
    sheet = p.sheet
    toast(buildList {
      add(StrGrid.pasted(plain(p.cells.size)))
      if (p.cutRows > 0) add(StrGrid.cutRows(plain(p.cutRows)))
      if (p.cutCols > 0) add(StrGrid.cutCols(plain(p.cutCols)))
    }.joinToString(" · "))
  }
  fun save() {
    if (editing != null) commit()
    when (val r = ProductGridSave.apply(d, sheet, ir.vil3ntec.tohid.todayIso(), ir.vil3ntec.tohid.data.ReportBridge.nowMs(), ::newId, by)) {
      is ProductGridSave.Result.Failed -> {
        toast(r.message)
        r.productId?.let { id -> sheet.rows.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { at = DeskGrid.Cell(it, Col.NAME) } }
      }
      is ProductGridSave.Result.Ok -> {
        scope.launch { store.save(r.data) }
        toast(StrGrid.saved(plain(r.saved)))
        onClose()
      }
    }
  }
  fun close() { if (sheet.changed.isEmpty()) onClose() else askDiscard = true }

  LaunchedEffect(Unit) { runCatching { gridFocus.requestFocus() } }
  LaunchedEffect(at.row) { if (at.row >= 0 && last >= 0) list.animateScrollToItem(at.row) }
  LaunchedEffect(editing != null) { if (editing != null) runCatching { cellFocus.requestFocus() } }

  Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
    Surface(color = c.surfaceSolid, modifier = Modifier.fillMaxSize().padding(24.dp)) {
      Column(
        Modifier.padding(14.dp)
          .onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            if (editing != null) {
              //  در حالِ ویرایش: فقط ثبت، رفتن و لغو؛ بقیه (حرف، Ctrl+V/Z ِ درونِ کادر) کارِ خودِ کادر است
              when {
                e.matches(KeyMap.PRODUCT_GRID_CANCEL) -> { editing = null; runCatching { gridFocus.requestFocus() }; true }
                e.matches(KeyMap.PRODUCT_GRID_DOWN) -> { commit(); move(1, 0); true }
                e.matches(KeyMap.PRODUCT_GRID_NEXT_CELL) -> { commit(); move(0, 1); true }
                e.matches(KeyMap.PRODUCT_GRID_PREV_CELL) -> { commit(); move(0, -1); true }
                e.matches(KeyMap.PRODUCT_GRID_SAVE) -> { save(); true }
                else -> false
              }
            } else when {
              e.matches(KeyMap.PRODUCT_GRID_SAVE) -> { save(); true }
              e.matches(KeyMap.PRODUCT_GRID_CANCEL) -> { close(); true }
              e.matches(KeyMap.PRODUCT_GRID_EDIT) -> { startEdit(); true }
              e.matches(KeyMap.PRODUCT_GRID_DOWN) -> { move(1, 0); true }
              e.matches(KeyMap.PRODUCT_GRID_NEXT_CELL) -> { move(0, 1); true }
              e.matches(KeyMap.PRODUCT_GRID_PREV_CELL) -> { move(0, -1); true }
              e.matches(KeyMap.PRODUCT_GRID_PASTE) -> { paste(); true }
              e.matches(KeyMap.PRODUCT_GRID_DUPLICATE_ROW) -> { sheet = sheet.duplicateAbove(at.row); true }
              e.matches(KeyMap.PRODUCT_GRID_UNDO) -> { sheet = sheet.undo(); true }
              e.key == androidx.compose.ui.input.key.Key.DirectionUp -> { move(-1, 0); true }
              e.key == androidx.compose.ui.input.key.Key.DirectionDown -> { move(1, 0); true }
              //  ستونِ نخست در راست است (راست‌به‌چپ): ← خانهٔ بعد، → خانهٔ قبل
              e.key == androidx.compose.ui.input.key.Key.DirectionLeft -> { move(0, if (rtl) 1 else -1); true }
              e.key == androidx.compose.ui.input.key.Key.DirectionRight -> { move(0, if (rtl) -1 else 1); true }
              //  تایپِ حرف ⇒ ویرایش با همان حرف
              !e.isPrimaryModifier && !e.isAltPressed && e.utf16CodePoint > 31 && e.utf16CodePoint != 127 -> {
                startEdit(e.utf16CodePoint.toChar().toString()); true
              }
              else -> false
            }
          }
          .focusRequester(gridFocus)
          .focusable(),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(StrGrid.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = c.text)
          Text(StrGrid.changed(plain(sheet.changed.size)), style = MaterialTheme.typography.bodySmall, color = c.muted)
          Spacer(Modifier.width(12.dp))
          OutlinedButton(onClick = { sheet = sheet.undo() }, enabled = sheet.canUndo) { Text("${StrGrid.undo} ${KeyMap.PRODUCT_GRID_UNDO.hint}") }
          Spacer(Modifier.width(8.dp))
          Button(onClick = ::save, enabled = sheet.changed.isNotEmpty() && problems.isEmpty()) {
            Text("${StrGrid.save(plain(sheet.changed.size))} ${KeyMap.PRODUCT_GRID_SAVE.hint}")
          }
          Spacer(Modifier.width(8.dp))
          TextButton(onClick = ::close) { Text("${StrGrid.close} Esc") }
        }
        Spacer(Modifier.height(10.dp))
        val hScroll = rememberScrollState()
        Column(Modifier.weight(1f).horizontalScroll(hScroll)) {
          Row(Modifier.background(c.surface2)) {
            for (col in DeskGrid.COLS) {
              Text(
                header(col), Modifier.width(width(col)).padding(horizontal = 8.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium, color = c.muted, maxLines = 1,
              )
            }
          }
          LazyColumn(state = list, modifier = Modifier.weight(1f)) {
            itemsIndexed(sheet.rows, key = { _, r -> r.id }) { i, r ->
              Row {
                for (col in DeskGrid.COLS) {
                  val here = at.row == i && at.col == col
                  val bad = problems[DeskGrid.Cell(i, col)] != null
                  val bg = when {
                    bad -> c.dangerTint
                    sheet.isChanged(i, col) -> c.primaryTint
                    else -> c.surfaceSolid
                  }
                  Box(
                    Modifier.width(width(col)).height(34.dp)
                      .background(bg)
                      .border(if (here) 2.dp else 0.5.dp, if (here) c.fieldFocus else c.border)
                      .clickable {
                        if (editing != null) commit()
                        if (here) startEdit() else at = DeskGrid.Cell(i, col)
                        runCatching { gridFocus.requestFocus() }
                      }
                      .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                  ) {
                    val ed = editing
                    if (here && ed != null) {
                      BasicTextField(
                        value = ed,
                        onValueChange = { editing = it.copy(text = it.text.take(DeskGrid.MAX_CELL)) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.text),
                        modifier = Modifier.fillMaxWidth().focusRequester(cellFocus),
                      )
                    } else {
                      Text(
                        r[col], style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (sheet.isChanged(i, col)) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (bad) c.danger else c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                      )
                    }
                  }
                }
              }
            }
          }
        }
        Spacer(Modifier.height(8.dp))
        //  ایرادها با شمارهٔ ردیف و ستون — همان‌ها ثبت را بسته‌اند
        problems.entries.take(SHOWN_PROBLEMS).forEach { (cell, p) ->
          Text(StrGrid.cellProblem(plain(cell.row + 1), header(cell.col), problemText(p)), style = MaterialTheme.typography.labelSmall, color = c.danger)
        }
        if (problems.size > SHOWN_PROBLEMS) Text(StrGrid.moreProblems(plain(problems.size - SHOWN_PROBLEMS)), style = MaterialTheme.typography.labelSmall, color = c.danger)
        Text(StrGrid.hint, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = c.muted)
      }
    }
  }

  if (askDiscard) AlertDialog(
    onDismissRequest = { askDiscard = false },
    title = { Text(StrGrid.discardTitle) },
    text = { Text(StrGrid.discardText) },
    confirmButton = { TextButton(onClick = { askDiscard = false; onClose() }) { Text(StrGrid.discard, color = c.danger) } },
    dismissButton = { TextButton(onClick = { askDiscard = false }) { Text(StrGrid.keep) } },
  )
}

private const val SHOWN_PROBLEMS = 4

private fun header(col: Col): String = when (col) {
  Col.NAME -> StrGrid.name
  Col.CATEGORY -> StrGrid.category
  Col.UNIT -> StrGrid.unit
  Col.BUY -> StrGrid.buy
  Col.SELL -> StrGrid.sell
  Col.MIN -> StrGrid.min
  Col.BARCODE -> StrGrid.barcode
}

private fun width(col: Col): Dp = when (col) {
  Col.NAME -> 260.dp
  Col.CATEGORY, Col.BARCODE -> 150.dp
  Col.UNIT -> 90.dp
  Col.BUY, Col.SELL, Col.MIN -> 110.dp
}

private fun problemText(p: DeskGrid.Problem): String = when (p) {
  DeskGrid.Problem.NAME_EMPTY -> StrGrid.nameEmpty
  DeskGrid.Problem.NAME_TAKEN -> StrGrid.nameTaken
  DeskGrid.Problem.BAD_NUMBER -> StrGrid.badNumber
  DeskGrid.Problem.NEGATIVE -> StrGrid.negative
  DeskGrid.Problem.BARCODE_TAKEN -> StrGrid.barcodeTaken
}
