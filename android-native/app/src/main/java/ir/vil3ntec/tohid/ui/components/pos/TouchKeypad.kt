package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrLine
import ir.vil3ntec.tohid.core.i18n.StrTouch
import ir.vil3ntec.tohid.core.pos.Keypad
import ir.vil3ntec.tohid.core.pos.LinePricing
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.core.pos.TouchMode
import ir.vil3ntec.tohid.ui.theme.SegmentInnerShape
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.segmentSelected
import ir.vil3ntec.tohid.ui.theme.segmentTrack
import ir.vil3ntec.tohid.ui.theme.softCard
import ir.vil3ntec.tohid.ui.theme.tabular
import ir.vil3ntec.tohid.ui.theme.Shape as ShopShape
import kotlinx.coroutines.launch

// i18n: سه‌زبانه

/*
 *  حالتِ لمسیِ صندوقِ کامپیوتر (POS-31) و صفحه‌کلیدِ عددیِ روی صفحه (POS-07) — §۵.۳.
 *  فقط وقتی `PosTouchPrefs` روشن است دیده می‌شوند؛ خاموش ⇒ صندوقِ کامپیوتر همان امروز.
 *  قاعده‌ها در هسته: `TouchMode` (کِی لمسی، صفحه به کجا) و `Keypad` (کلید ⇒ عدد).
 */

/** حالِ صفحه‌کلیدِ صندوق (POS-07): حالت و متن، و متنِ ردِ آخرین «ثبت» — برای هر سیاستِ قیمتِ ردیف یکی */
@Stable
class TouchPadUi(val policy: LinePricing.Policy) {
  var state by mutableStateOf(Keypad.State())
  var problem by mutableStateOf<String?>(null)
  val canDiscount: Boolean = PosKeypad.canDiscount(policy)

  /** «ثبت» روی ردیفِ [id] (`PosKeypad.apply`)؛ [set] سبدِ تازه را می‌نشاند (`CartStore.set`). `false` ⇒ سبد نپذیرفت */
  fun apply(cart: List<SalesEngine.CartLine>, id: String?, product: Product?, set: (List<SalesEngine.CartLine>) -> Boolean): Boolean {
    when (val r = PosKeypad.apply(Keypad.entry(state), cart, id, product, policy)) {
      is PosKeypad.Applied.Done -> {
        if (!set(r.cart)) return false
        state = state.copy(text = "")
        problem = null
      }
      is PosKeypad.Applied.Problem -> problem = r.text
      PosKeypad.Applied.Unchanged -> problem = null
    }
    return true
  }
}

/** با عوض شدنِ ردیفِ انتخاب‌شده کادر خالی می‌شود (عددِ ردیفِ پیشین به ردیفِ تازه نمی‌رود) */
@Composable
fun rememberTouchPad(policy: LinePricing.Policy, selected: String?): TouchPadUi {
  val ui = remember(policy) { TouchPadUi(policy) }
  LaunchedEffect(ui, selected) {
    ui.state = ui.state.copy(text = "")
    ui.problem = null
  }
  return ui
}

/**
 *  سبد با دکمه‌های صفحه و صفحه‌کلیدِ عددی زیرش — کامپیوترِ لمسی (POS-31، POS-07).
 *  [onFocusBack] پس از هر زدن: کادرِ اسکنِ صندوق همیشه فوکوس دارد (بارکدخوان).
 */
@Composable
fun TouchCartColumn(
  pad: TouchPadUi,
  listState: LazyListState,
  lineName: String?,
  target: Dp,
  onApply: () -> Unit,
  onFocusBack: () -> Unit,
  modifier: Modifier = Modifier,
  cart: @Composable (Modifier) -> Unit,
) {
  Column(modifier) {
    cart(Modifier.weight(1f).fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    ListPageButtons(listState, target, onDone = onFocusBack)
    Spacer(Modifier.height(8.dp))
    TouchKeypad(
      state = pad.state,
      onState = { pad.state = it; pad.problem = null; onFocusBack() },
      lineName = lineName,
      canDiscount = pad.canDiscount,
      problem = pad.problem,
      target = target,
      onApply = { onApply(); onFocusBack() },
    )
  }
}

/** کلیدِ «حالتِ لمسی» در نوارِ بالای صندوق — لمسی ↔ موشی، برای همین دستگاه */
@Composable
fun TouchToggle(active: Boolean, target: Dp, onChange: (Boolean) -> Unit) {
  val c = Shop.colors
  val label = if (active) StrTouch.toggleOn else StrTouch.toggleOff
  OutlinedButton(
    onClick = { onChange(!active) },
    shape = ShopShape.button,
    colors = if (active) {
      ButtonDefaults.outlinedButtonColors(containerColor = c.surfaceSelected, contentColor = c.primaryDark)
    } else {
      ButtonDefaults.outlinedButtonColors(contentColor = c.muted)
    },
    contentPadding = PaddingValues(horizontal = 12.dp),
    modifier = Modifier.heightIn(min = target).semantics { contentDescription = label },
  ) {
    Icon(Icons.Filled.TouchApp, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(6.dp))
    Text(StrTouch.toggle, style = MaterialTheme.typography.labelLarge, maxLines = 1)
  }
}

/** دو دکمهٔ بزرگِ «صفحهٔ قبل / بعد» — کشیدنِ انگشت در Compose Desktop فهرست را اسکرول نمی‌کند (§۵.۳) */
@Composable
fun PageButtons(
  canPrev: Boolean,
  canNext: Boolean,
  target: Dp,
  onPrev: () -> Unit,
  onNext: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TouchMode.DANGER_GAP_DP.dp)) {
    OutlinedButton(onClick = onPrev, enabled = canPrev, shape = ShopShape.button, modifier = Modifier.weight(1f).heightIn(min = target)) {
      Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(20.dp))
      Spacer(Modifier.width(6.dp))
      Text(StrTouch.pagePrev, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
    OutlinedButton(onClick = onNext, enabled = canNext, shape = ShopShape.button, modifier = Modifier.weight(1f).heightIn(min = target)) {
      Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(20.dp))
      Spacer(Modifier.width(6.dp))
      Text(StrTouch.pageNext, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
  }
}

/** دکمه‌های صفحهٔ شبکهٔ کارت‌ها؛ [onDone] پس از هر زدن (صندوق فوکوس را به کادرِ اسکن برمی‌گرداند) */
@Composable
fun GridPageButtons(state: LazyGridState, target: Dp, modifier: Modifier = Modifier, onDone: () -> Unit = {}) {
  val scope = rememberCoroutineScope()
  val first = state.firstVisibleItemIndex
  val visible = state.layoutInfo.visibleItemsInfo.size
  val total = state.layoutInfo.totalItemsCount
  PageButtons(
    canPrev = TouchMode.canPage(first, visible, total, forward = false),
    canNext = TouchMode.canPage(first, visible, total, forward = true),
    target = target,
    onPrev = { scope.launch { runCatching { state.animateScrollToItem(TouchMode.page(first, visible, total, forward = false)) } }; onDone() },
    onNext = { scope.launch { runCatching { state.animateScrollToItem(TouchMode.page(first, visible, total, forward = true)) } }; onDone() },
    modifier = modifier,
  )
}

/** دکمه‌های صفحهٔ فهرست (جدولِ کالاها، ردیف‌های سبد) */
@Composable
fun ListPageButtons(state: LazyListState, target: Dp, modifier: Modifier = Modifier, onDone: () -> Unit = {}) {
  val scope = rememberCoroutineScope()
  val first = state.firstVisibleItemIndex
  val visible = state.layoutInfo.visibleItemsInfo.size
  val total = state.layoutInfo.totalItemsCount
  PageButtons(
    canPrev = TouchMode.canPage(first, visible, total, forward = false),
    canNext = TouchMode.canPage(first, visible, total, forward = true),
    target = target,
    onPrev = { scope.launch { runCatching { state.animateScrollToItem(TouchMode.page(first, visible, total, forward = false)) } }; onDone() },
    onNext = { scope.launch { runCatching { state.animateScrollToItem(TouchMode.page(first, visible, total, forward = true)) } }; onDone() },
    modifier = modifier,
  )
}

/**
 *  صفحه‌کلیدِ عددی با حالت‌های «تعداد، قیمت، تخفیف» (POS-07). خودش چیزی را عوض نمی‌کند: [onState]
 *  حالتِ تازه و [onApply] «ثبت» را به صدازننده می‌دهد (`PosKeypad.apply`).
 *
 *  @param lineName نامِ ردیفِ انتخاب‌شدهٔ سبد؛ `null` ⇒ «ثبت» خاموش
 *  @param canDiscount کلیدِ «تخفیف» (`PosKeypad.canDiscount`)
 *  @param problem متنِ ردِ آخرین «ثبت»
 */
@Composable
fun TouchKeypad(
  state: Keypad.State,
  onState: (Keypad.State) -> Unit,
  lineName: String?,
  canDiscount: Boolean,
  problem: String?,
  target: Dp,
  onApply: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val c = Shop.colors
  Column(
    modifier.fillMaxWidth().softCard(c, ShopShape.card).padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    val modes = buildList {
      add(Keypad.Mode.QTY to StrTouch.modeQty)
      add(Keypad.Mode.PRICE to StrTouch.modePrice)
      if (canDiscount) add(Keypad.Mode.DISCOUNT to StrTouch.modeDiscount)
    }
    Segments(modes.map { it.second }, modes.indexOfFirst { it.first == state.mode }, target) { i ->
      onState(Keypad.mode(state, modes[i].first))
    }
    if (state.mode == Keypad.Mode.DISCOUNT) {
      Segments(listOf(StrLine.percent, StrLine.amount), if (state.percent) 0 else 1, target) { i ->
        onState(Keypad.percent(state, i == 0))
      }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        lineName?.let { StrTouch.forLine(it) } ?: StrTouch.pickLine,
        Modifier.weight(1f),
        style = MaterialTheme.typography.labelMedium, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.width(8.dp))
      val suffix = when {
        state.mode != Keypad.Mode.DISCOUNT -> ""
        state.percent -> " ٪"
        else -> " ؋"
      }
      Text(
        state.text.ifEmpty { "0" } + suffix,
        style = MaterialTheme.typography.titleLarge.tabular(), fontWeight = FontWeight.Bold, color = c.text, maxLines = 1,
      )
    }
    if (problem != null) Text(problem, style = MaterialTheme.typography.labelSmall, color = c.danger)
    val rows = listOf(
      listOf(Keypad.Key.D7, Keypad.Key.D8, Keypad.Key.D9, Keypad.Key.BACK),
      listOf(Keypad.Key.D4, Keypad.Key.D5, Keypad.Key.D6, Keypad.Key.CLEAR),
      listOf(Keypad.Key.D1, Keypad.Key.D2, Keypad.Key.D3, Keypad.Key.DOT),
      listOf(Keypad.Key.D0, Keypad.Key.D00),
    )
    rows.forEachIndexed { i, row ->
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        row.forEach { k -> PadKey(k, target, Modifier.weight(1f)) { onState(Keypad.press(state, k)) } }
        if (i == rows.lastIndex) {
          Button(
            onClick = onApply,
            enabled = lineName != null && state.text.isNotEmpty(),
            shape = ShopShape.button,
            colors = ButtonDefaults.buttonColors(
              containerColor = c.button.primary.bg,
              contentColor = c.button.primary.text,
              disabledContainerColor = c.button.disabled.bg,
              disabledContentColor = c.button.disabled.text,
            ),
            modifier = Modifier.weight(2f).heightIn(min = target),
          ) { Text(StrTouch.apply, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1) }
        }
      }
    }
  }
}

/** کنترلِ قطعه‌ایِ هم‌شکلِ روشِ پرداخت (`PaymentMethodGrid`) با هدفِ لمسِ [target] */
@Composable
private fun Segments(labels: List<String>, selected: Int, target: Dp, onSelect: (Int) -> Unit) {
  val c = Shop.colors
  Row(Modifier.fillMaxWidth().segmentTrack(c).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
    labels.forEachIndexed { i, label ->
      val on = i == selected
      Box(
        Modifier
          .weight(1f)
          .heightIn(min = target)
          .then(if (on) Modifier.segmentSelected(c) else Modifier.clip(SegmentInnerShape))
          .selectable(selected = on, role = Role.RadioButton) { onSelect(i) },
        contentAlignment = Alignment.Center,
      ) {
        Text(
          label,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
          color = if (on) c.primaryDark else c.muted,
          maxLines = 1,
        )
      }
    }
  }
}

@Composable
private fun PadKey(k: Keypad.Key, target: Dp, modifier: Modifier, onClick: () -> Unit) {
  OutlinedButton(onClick = onClick, shape = ShopShape.button, contentPadding = PaddingValues(0.dp), modifier = modifier.heightIn(min = target)) {
    when (k) {
      Keypad.Key.BACK -> Icon(Icons.Filled.Backspace, contentDescription = StrTouch.back, modifier = Modifier.size(20.dp))
      Keypad.Key.CLEAR -> Text(StrTouch.clear, style = MaterialTheme.typography.labelLarge, maxLines = 1)
      Keypad.Key.DOT -> Text(".", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      else -> Text(k.digits.orEmpty(), style = MaterialTheme.typography.titleMedium.tabular(), fontWeight = FontWeight.Bold)
    }
  }
}
