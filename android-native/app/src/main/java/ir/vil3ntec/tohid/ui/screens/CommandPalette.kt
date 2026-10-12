package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  جست‌وجوی سریع — «برو به هر جا» (Ctrl+K).
 *
 *  یک کادر: نامِ صفحه، نام یا بارکدِ کالا، یا نام و شمارهٔ قرض‌دار را
 *  بنویسید؛ ↑/↓ انتخاب، Enter باز کردن، Esc بستن. کالا صفحهٔ خودش را باز
 *  می‌کند (موجودی و قیمت همین‌جا پیداست) و قرض‌دار صفحهٔ قرض‌داران را.
 *
 *  جست‌وجو روی دادهٔ همین دستگاه است — بی اینترنت هم کار می‌کند — و
 *  فقط ۴۰ نتیجهٔ اول ساخته می‌شود تا با هزاران کالا هم فوری بماند.
 */
class PaletteHit(val kind: String, val id: String, val title: String, val hint: String, val icon: ImageVector)

/** همهٔ صفحه‌هایی که از جست‌وجو باز می‌شوند — همان نام‌های سربرگ */
val PALETTE_PAGES = listOf(
  "dashboard" to "داشبورد", "admin" to "مرکزِ مدیریت", "sale" to "فروش", "quick" to "فروشِ سریع",
  "debtors" to "قرض‌داران", "products" to "محصولات", "warehouse" to "انبار", "sales" to "تاریخچهٔ فروش و صندوق",
  "purchasing" to "خرید و تأمین‌کننده", "reports" to "گزارش‌ها", "expenses" to "مصارف", "receipts" to "رسیدها",
  "team" to "کارمندان", "audit" to "سابقهٔ عملیات", "settings" to "تنظیمات و پشتیبان", "myplan" to "اشتراکِ من",
  "support" to "پشتیبانی", "more" to "بیشتر",
)

private fun norm(s: String) = ir.vil3ntec.tohid.latinDigits(s).replace('ي', 'ی').replace('ك', 'ک').lowercase().trim()

fun paletteSearch(d: ShopData, query: String, limit: Int = 40): List<PaletteHit> {
  val q = norm(query)
  val out = ArrayList<PaletteHit>()
  PALETTE_PAGES.filter { q.isEmpty() || norm(it.second).contains(q) || it.first.contains(q) }
    .forEach { out += PaletteHit("page", it.first, it.second, "صفحه", Icons.Filled.SpaceDashboard) }
  if (q.isEmpty()) return out
  for (p in d.products) {
    if (out.size >= limit) break
    if (norm(p.name).contains(q) || p.barcodes.any { it.contains(q) }) {
      val stock = ShopStore.stock(d, p.id)
      out += PaletteHit("product", p.id, p.name, "کالا · موجودی ${money(stock)} · ${money(p.salePrice)} ؋", Icons.Filled.Inventory2)
    }
  }
  for (b in d.debtors) {
    if (out.size >= limit) break
    if (norm(b.name).contains(q) || norm(b.phone).contains(q)) {
      out += PaletteHit("debtor", b.id, b.name, "قرض‌دار · بدهی ${money(ShopStore.debt(d, b.id))} ؋", Icons.Filled.Person)
    }
  }
  return out
}

@Composable
fun CommandPalette(d: ShopData, onDismiss: () -> Unit, onPick: (PaletteHit) -> Unit) {
  var query by remember { mutableStateOf("") }
  var index by remember { mutableStateOf(0) }
  val hits = remember(d, query) { paletteSearch(d, query) }
  val focus = remember { FocusRequester() }
  val list = rememberLazyListState()
  LaunchedEffect(Unit) { focus.requestFocus() }
  LaunchedEffect(hits) { index = 0 }
  LaunchedEffect(index) { if (hits.isNotEmpty()) list.animateScrollToItem(index.coerceIn(0, hits.lastIndex)) }

  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Box(Modifier.fillMaxSize().clickable(onClick = onDismiss), contentAlignment = Alignment.TopCenter) {
      Column(
        Modifier
          .padding(top = 72.dp, start = 16.dp, end = 16.dp)
          .widthIn(max = 640.dp)
          .fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .background(Shop.colors.surfaceSolid)
          //  کلیک درونِ کادر به زمینه نرسد (که پنجره را می‌بندد)
          .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
          .padding(12.dp),
      ) {
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          singleLine = true,
          placeholder = { Text("صفحه، کالا، بارکد یا قرض‌دار…") },
          leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
          modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus)
            .onPreviewKeyEvent { e ->
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
        Spacer(Modifier.height(8.dp))
        if (hits.isEmpty()) {
          Text("چیزی پیدا نشد.", color = Shop.colors.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
        }
        LazyColumn(state = list, modifier = Modifier.heightIn(max = 420.dp)) {
          itemsIndexed(hits, key = { _, h -> h.kind + h.id }) { i, h ->
            Row(
              Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (i == index) Shop.colors.primary.copy(alpha = 0.12f) else Shop.colors.surfaceSolid)
                .clickable { onPick(h) }
                .padding(horizontal = 10.dp, vertical = 9.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(h.icon, contentDescription = null, tint = Shop.colors.primary, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(10.dp))
              Text(h.title, color = Shop.colors.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
              Text(h.hint, color = Shop.colors.muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
          }
        }
        Text("↑ ↓ انتخاب · Enter باز کردن · Esc بستن", color = Shop.colors.muted, style = MaterialTheme.typography.labelSmall,
          modifier = Modifier.padding(top = 8.dp, start = 4.dp))
      }
    }
  }
}
