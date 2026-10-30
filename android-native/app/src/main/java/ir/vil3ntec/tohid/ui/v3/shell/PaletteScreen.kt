package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.vil3ntec.tohid.core.i18n.StrSearch
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.v3.V3SearchField
import ir.vil3ntec.tohid.ui.v3.V3Type

/** بیشترِ ردیفِ فهرستِ فرمان (همان سقفِ پیشین) */
private const val PALETTE_MAX = 40

/** ردیف‌های فهرستِ فرمان به ترتیب: کادرِ خالی ⇐ بخش‌ها؛ وگرنه کالا، قرض‌دار، فاکتور، بخش */
fun paletteHits(r: SearchIndex.Results): List<SearchIndex.Hit> = r.groups.flatMap { it.hits }.take(PALETTE_MAX)

/**
 *  فهرستِ فرمانِ کامپیوتر (Ctrl+K، جست‌وجوی سربرگ) — کارتِ ۶۴۰dp ِ نسخهٔ ۳ روی پس‌زمینهٔ
 *  تیره؛ کادر بالا، بخش‌ها و نتیجه‌های `SearchIndex` زیرش؛ ↑ ↓ انتخاب، Enter باز، Esc بستن.
 *  هر نتیجه همان سند را باز می‌کند (صدازننده با `hit.route`).
 */
@Composable
fun V3Palette(d: ShopData, sections: List<SearchIndex.Section>, onDismiss: () -> Unit, onPick: (SearchIndex.Hit) -> Unit) {
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    PaletteBody(d, sections, onDismiss, onPick)
  }
}

/** پس‌زمینهٔ تیره و کارتِ فهرستِ فرمان (بی پنجره — برای عکس هم) */
@Composable
internal fun PaletteBody(d: ShopData, sections: List<SearchIndex.Section>, onDismiss: () -> Unit, onPick: (SearchIndex.Hit) -> Unit, initialQuery: String = "") {
  val c = PhoneV3.colors
  var query by remember { mutableStateOf(initialQuery) }
  var index by remember { mutableIntStateOf(0) }
  val hits = remember(d, query, sections) { paletteHits(SearchIndex.search(d, query, sections)) }
  val focus = remember { FocusRequester() }
  val list = rememberLazyListState()
  val appear = remember { Animatable(if (Motion.enabled) 0f else 1f) }
  LaunchedEffect(Unit) { runCatching { focus.requestFocus() }; appear.animateTo(1f, MotionSpec.sheet()) }
  LaunchedEffect(hits) { index = 0 }
  LaunchedEffect(index) { if (hits.isNotEmpty()) list.animateScrollToItem(index.coerceIn(0, hits.lastIndex)) }
  val brand = PhoneV3Roles.brandText(c)
  val shape = RoundedCornerShape(16.dp)

  Box(
    Modifier.fillMaxSize().background(c.black.copy(alpha = 0.32f * appear.value)).clickable(onClick = onDismiss),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      Modifier
        .padding(top = 72.dp, start = 16.dp, end = 16.dp)
        .widthIn(max = 640.dp)
        .fillMaxWidth()
        .graphicsLayer { alpha = appear.value; scaleX = 0.96f + 0.04f * appear.value; scaleY = scaleX }
        .shadow(24.dp, shape, ambientColor = c.black.copy(alpha = 0.25f), spotColor = c.black.copy(alpha = 0.25f))
        .clip(shape)
        .background(c.card)
        .border(1.dp, c.hairline, shape)
        //  کلیک درونِ کارت به زمینه نرسد (که پنجره را می‌بندد)
        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
        .padding(12.dp),
    ) {
      V3SearchField(
        value = query,
        onValueChange = { query = it },
        placeholder = StrSearch.palettePlaceholder,
        onSubmit = { hits.getOrNull(index)?.let(onPick) },
        modifier = Modifier.focusRequester(focus).onPreviewKeyEvent { e ->
          if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
          when (e.key) {
            Key.DirectionDown -> { if (hits.isNotEmpty()) index = (index + 1) % hits.size; true }
            Key.DirectionUp -> { if (hits.isNotEmpty()) index = (index - 1 + hits.size) % hits.size; true }
            Key.Enter, Key.NumPadEnter -> { hits.getOrNull(index)?.let(onPick); true }
            Key.Escape -> { onDismiss(); true }
            else -> false
          }
        },
      )
      if (hits.isEmpty()) {
        Text(StrSearch.nothing, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.padding(12.dp))
      }
      LazyColumn(state = list, modifier = Modifier.heightIn(max = 420.dp).padding(top = 8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
        itemsIndexed(hits, key = { _, h -> h.kind.name + h.id }) { i, h ->
          val (icon, hue) = hitLook(h)
          DraftHitRow(
            highlighted(h.title, h.match, brand), h.hint ?: StrSearch.page, icon, hue,
            onClick = { onPick(h) },
            modifier = Modifier.clip(RoundedCornerShape(10.dp)),
            selected = i == index,
          )
        }
      }
      Text(StrSearch.paletteKeys, style = V3Type.caption, color = c.inkMuted, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
    }
  }
}
