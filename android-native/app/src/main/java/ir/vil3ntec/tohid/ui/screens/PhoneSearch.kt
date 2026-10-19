package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.invoiceLabel
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.components.common.PhoneNav
import ir.vil3ntec.tohid.ui.theme.PhoneV3

/**
 *  جست‌وجوی کاملِ گوشی (phone v3 §F): کالا، مشتری، فاکتور و بخش — با نشانِ بارکد
 *  درونِ کادر که دوربین را باز می‌کند. همه روی دادهٔ همین دستگاه، بی اینترنت هم.
 *  کالا و مشتری و بخش همان جست‌وجوی سریعِ برنامه‌اند (`paletteSearch`)؛ فاکتور
 *  با شماره، نامِ مشتری یا مبلغ پیدا می‌شود.
 */
@Composable
fun PhoneSearchScreen(
  d: ShopData,
  onOpen: (String) -> Unit,
  onProduct: (String) -> Unit,
  onBack: () -> Unit,
  initialQuery: String = "",
  bottomReserve: Dp = PhoneNav.RESERVE,
) {
  val p = PhoneV3.colors
  var query by rememberSaveable { mutableStateOf(initialQuery) }
  var scanning by remember { mutableStateOf(false) }
  val focus = remember { FocusRequester() }
  LaunchedEffect(Unit) { if (initialQuery.isEmpty()) runCatching { focus.requestFocus() } }
  val hits = remember(d, query) { paletteSearch(d, query, limit = 60) }
  val invoices = remember(d, query) { invoiceSearch(d, query) }
  val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

  Column(Modifier.fillMaxSize().background(p.bg).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      RoundButton(Icons.Outlined.ChevronRight, "بازگشت", onBack)
      Spacer(Modifier.width(10.dp))
      Row(
        Modifier.weight(1f).height(52.dp)
          .shadow(8.dp, RoundedCornerShape(26.dp), clip = false, ambientColor = p.black.copy(alpha = 0.08f), spotColor = p.black.copy(alpha = 0.08f))
          .clip(RoundedCornerShape(26.dp)).background(p.card).padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = p.inkMuted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
          if (query.isEmpty()) Text("کالا، مشتری، فاکتور یا بخش…", color = p.inkMuted, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp), maxLines = 1)
          BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, color = p.ink),
            cursorBrush = SolidColor(p.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { hits.firstOrNull { it.kind != "page" }?.let { h -> openHit(h, onOpen, onProduct) } }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
          )
        }
        if (query.isNotEmpty()) Box(
          Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "پاک کردن") { query = "" },
          contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Close, contentDescription = "پاک کردن", tint = p.inkMuted, modifier = Modifier.size(20.dp)) }
        Box(
          Modifier.size(40.dp).clip(CircleShape).background(p.bg).clickable(role = Role.Button, onClickLabel = "اسکنِ بارکد") { scanning = true },
          contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.QrCodeScanner, contentDescription = "اسکنِ بارکد", tint = p.icon, modifier = Modifier.size(22.dp)) }
      }
    }
    Spacer(Modifier.height(20.dp))

    val pages = hits.filter { it.kind == "page" }
    val products = hits.filter { it.kind == "product" }
    val people = hits.filter { it.kind == "debtor" }
    if (query.isNotBlank() && products.isEmpty() && people.isEmpty() && invoices.isEmpty() && pages.isEmpty()) {
      Text("چیزی پیدا نشد.", color = p.inkMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(8.dp))
    }
    HitGroup("کالاها", products.map { Hit(it.title, it.hint.removePrefix("کالا · "), Icons.Outlined.LocalOffer) { openHit(it, onOpen, onProduct) } })
    HitGroup("مشتری‌ها", people.map { Hit(it.title, it.hint.removePrefix("قرض‌دار · "), Icons.Outlined.Person) { onOpen("debtors") } })
    HitGroup("فاکتورها", invoices.map { s ->
      Hit(
        "فاکتورِ ${s.invoiceLabel() ?: "—"}",
        "${ir.vil3ntec.tohid.formatDate(s.date)} · ${money(s.finalTotal)} ؋" + (s.debtorId?.let { id -> d.debtors.find { it.id == id }?.name }?.let { " · $it" } ?: ""),
        Icons.Outlined.ReceiptLong,
      ) { onOpen("sales") }
    })
    HitGroup(if (query.isBlank()) "همهٔ بخش‌ها" else "بخش‌ها", pages.map { Hit(it.title, null, Icons.Outlined.GridView) { onOpen(it.id) } })
    Spacer(Modifier.height(bottomReserve + navBottom + 16.dp))
  }

  if (scanning) BarcodeScanSheet(
    onDismiss = { scanning = false },
    onCode = { code ->
      scanning = false
      val id = ShopStore.barcodeIndex(d)[code]
      if (id != null) onProduct(id) else query = code
    },
    title = "اسکنِ بارکد برای جست‌وجو",
  )
}

private fun openHit(h: PaletteHit, onOpen: (String) -> Unit, onProduct: (String) -> Unit) = when (h.kind) {
  "product" -> onProduct(h.id)
  "debtor" -> onOpen("debtors")
  else -> onOpen(h.id)
}

/** فاکتورها: شماره (`B1-154` یا `154`)، نامِ مشتری، یا مبلغِ نهایی — تازه‌ترین اول */
internal fun invoiceSearch(d: ShopData, query: String, limit: Int = 20): List<ir.vil3ntec.tohid.data.Sale> {
  val q = ir.vil3ntec.tohid.latinDigits(query).replace('ي', 'ی').replace('ك', 'ک').trim().lowercase()
  if (q.isEmpty()) return emptyList()
  val names = d.debtors.associate { it.id to it.name.lowercase() }
  return d.sales.asSequence()
    .filter { s ->
      s.invoiceLabel()?.lowercase()?.contains(q) == true ||
        (s.debtorId?.let { names[it] }?.contains(q) == true) ||
        (q.all { it.isDigit() || it == ',' } && money(s.finalTotal).replace(",", "") == q.replace(",", ""))
    }
    .sortedByDescending { it.createdAt }
    .take(limit)
    .toList()
}

private class Hit(val title: String, val hint: String?, val icon: ImageVector, val onClick: () -> Unit)

/** گروهِ نتیجه به شکلِ r5: سرخطِ خاکستری بیرونِ کارت، ردیف‌های فشرده درونِ یک کارتِ سفید */
@Composable
private fun HitGroup(title: String, hits: List<Hit>) {
  if (hits.isEmpty()) return
  val p = PhoneV3.colors
  Text(title, color = p.inkMuted, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), modifier = Modifier.padding(start = 6.dp, bottom = 8.dp))
  Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(PhoneV3.groupRadius)).background(p.card)) {
    hits.forEachIndexed { i, h ->
      if (i > 0) Box(Modifier.padding(start = 52.dp).fillMaxWidth().height(1.dp).background(p.hairline))
      Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = h.onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(h.icon, contentDescription = null, tint = p.icon, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
          Text(h.title, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp))
          h.hint?.let { Text(it, color = p.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)) }
        }
        Icon(Icons.Outlined.ChevronLeft, contentDescription = null, tint = p.chevron, modifier = Modifier.size(20.dp))
      }
    }
  }
  Spacer(Modifier.height(18.dp))
}

/** دکمهٔ گردِ سفید (r5: دکمهٔ برگشت) */
@Composable
internal fun RoundButton(icon: ImageVector, label: String, onClick: () -> Unit) {
  val p = PhoneV3.colors
  PressBox(Modifier.size(44.dp), scaleTo = 0.92f, label = label, onClick = onClick) {
    Box(
      Modifier.size(44.dp)
        .shadow(6.dp, CircleShape, clip = false, ambientColor = p.black.copy(alpha = 0.08f), spotColor = p.black.copy(alpha = 0.08f))
        .clip(CircleShape).background(p.card),
      contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = p.icon, modifier = Modifier.size(24.dp)) }
  }
}
