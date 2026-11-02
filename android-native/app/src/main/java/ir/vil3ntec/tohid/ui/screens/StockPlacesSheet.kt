package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ir.vil3ntec.tohid.core.i18n.StrPlaces
import ir.vil3ntec.tohid.core.product.PackBarcodes
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ProductPlansMap
import ir.vil3ntec.tohid.data.Repack
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.StockLedger
import ir.vil3ntec.tohid.data.StockPlaces
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.components.common.FilterMenu
import ir.vil3ntec.tohid.ui.components.common.SearchBox
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.Shop

/*
 *  «محل‌ها و انتقال» (INV-20) و «بسته‌بندیِ دوباره و ساختِ بسته» (INV-21) در انبار — مشترکِ گوشی و کامپیوتر.
 *  همهٔ عددها و قاعده‌ها از `StockPlaces`/`Repack`؛ این‌جا فقط نشان داده و پرسیده می‌شود. نوشتن و تنظیم در مرزِ پلتفرم
 *  (`StockPlacesUiAndroid.kt`). پشتِ `StockPlacesPref`، پیش‌فرض خاموش.
 */

/** عنوانِ بخشِ تنظیمات — همان که `DeskSettingsLegacy` در ستونِ چپ نشان می‌دهد (`AppRootLegacy`) */
const val STOCK_PLACES_TITLE = "محل‌ها و بسته‌بندیِ دوباره"

private fun milliText(milli: Long): String = (if (milli < 0) "−" else "") + qty(kotlin.math.abs(milli) / 1000.0)

/** عددِ تایپ‌شده ⇐ هزارم؛ خالی یا نادرست ⇒ ۰ */
private fun milliOf(raw: String): Long = raw.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }?.let(StockLedger::milli) ?: 0L

@Composable
fun StockPlacesSettings(places: Boolean, repack: Boolean, onPlaces: (Boolean) -> Unit, onRepack: (Boolean) -> Unit) {
  SettingsSection(
    icon = Icons.Filled.Inventory2,
    title = STOCK_PLACES_TITLE,
    subtitle = if (places || repack) StrPlaces.settingsOn else StrPlaces.off,
    tint = if (places || repack) Shop.colors.success else Shop.colors.muted,
  ) {
    Text(StrPlaces.settingsNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Spacer(Modifier.height(12.dp))
    Text(StrPlaces.placesSection, style = MaterialTheme.typography.labelLarge, color = Shop.colors.text)
    Text(StrPlaces.placesSectionNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Spacer(Modifier.height(6.dp))
    SegmentedChoice(listOf("off" to StrPlaces.off, "on" to StrPlaces.on), if (places) "on" else "off", { onPlaces(it == "on") })
    Spacer(Modifier.height(12.dp))
    Text(StrPlaces.repackSection, style = MaterialTheme.typography.labelLarge, color = Shop.colors.text)
    Text(StrPlaces.repackSectionNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Spacer(Modifier.height(6.dp))
    SegmentedChoice(listOf("off" to StrPlaces.off, "on" to StrPlaces.on), if (repack) "on" else "off", { onRepack(it == "on") })
  }
}

/** انتخابِ کالایی که موجودیِ خودش را دارد (نه کیت، نه خدماتی) */
@Composable
private fun ProductPick(d: ShopData, label: String, selected: String?, onPick: (String?) -> Unit) {
  var search by remember { mutableStateOf("") }
  val chosen = d.products.find { it.id == selected }
  Text(label, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
  if (chosen != null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("${chosen.name} (${chosen.unit})", style = MaterialTheme.typography.bodyLarge, color = Shop.colors.text, modifier = Modifier.weight(1f))
      TextButton(onClick = { onPick(null) }) { Text(StrPlaces.otherProduct) }
    }
    return
  }
  SearchBox(search, { search = it }, StrPlaces.search, Modifier.fillMaxWidth())
  val q = search.trim()
  d.products
    .filter { p: Product -> p.active && p.stockless != true && !ProductPlansMap.isKit(p) && (q.isEmpty() || p.name.contains(q, ignoreCase = true) || p.barcodes.any { it == q }) }
    .sortedBy { it.name }.take(6).forEach { p ->
      Text(
        p.name, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text,
        modifier = Modifier.fillMaxWidth().clip(Shape.button).clickable { onPick(p.id); search = "" }.padding(10.dp),
      )
    }
}

@Composable
private fun DialogFrame(title: String, note: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
  Dialog(onDismissRequest = onDismiss) {
    DialogEntry {
      Surface(color = Shop.colors.surfaceSolid, shape = Shape.dialog, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {
          Text(title, style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
          Text(note, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
          Spacer(Modifier.height(12.dp))
          content()
        }
      }
    }
  }
}

/* ============================== INV-20 · محل‌ها و انتقال ============================== */

@Composable
fun StockPlacesDialog(
  d: ShopData,
  onDismiss: () -> Unit,
  onAdd: (name: String) -> Unit,
  onArchive: (name: String) -> Unit,
  onTransfer: (productId: String, from: String, to: String, qtyMilli: Long, reason: String) -> Unit,
) {
  val stock = remember(d) { StockPlaces.stockAt(d) }
  val places = remember(d, stock) { StockPlaces.places(d, stock) }
  val registered = remember(d) { StockPlaces.registered(d).toSet() }
  var fresh by remember { mutableStateOf("") }
  var productId by remember { mutableStateOf<String?>(null) }
  var from by remember { mutableStateOf(StockLedger.MAIN) }
  var to by remember { mutableStateOf(places.getOrNull(1) ?: StockLedger.MAIN) }
  var amount by remember { mutableStateOf("") }
  var reason by remember { mutableStateOf("") }
  val options = places.map { it to StockPlaces.label(it) }

  DialogFrame(StrPlaces.placesTitle, StrPlaces.placesNote, onDismiss) {
    for (loc in places) {
      val n = stock.values.count { (it[loc] ?: 0L) > 0L }
      Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(StockPlaces.label(loc), style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text, modifier = Modifier.weight(1f))
        Text(StrPlaces.items(n), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
        if (loc in registered && stock.values.none { (it[loc] ?: 0L) != 0L }) TextButton(onClick = { onArchive(loc) }) { Text(StrPlaces.archive) }
      }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(fresh, { fresh = it.take(StockPlaces.MAX_NAME) }, label = { Text(StrPlaces.newPlace) }, singleLine = true, modifier = Modifier.weight(1f))
      OutlinedButton(enabled = fresh.isNotBlank(), onClick = { onAdd(fresh); fresh = "" }) { Text(StrPlaces.add) }
    }
    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Shop.colors.border)
    Text(StrPlaces.transferTitle, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
    Spacer(Modifier.height(6.dp))
    ProductPick(d, StrPlaces.transferTitle, productId) { productId = it }
    productId?.let { pid ->
      val here = stock[pid].orEmpty().filterValues { it != 0L }
      Text(
        if (here.isEmpty()) StrPlaces.noStock else here.entries.sortedBy { it.key }.joinToString(" · ") { "${StockPlaces.label(it.key)}: ${milliText(it.value)}" },
        style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted,
      )
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(StrPlaces.from, color = Shop.colors.muted)
      FilterMenu(StrPlaces.origin, options, from, { from = it ?: StockLedger.MAIN }, StockPlaces.label(StockLedger.MAIN))
      Text(StrPlaces.to, color = Shop.colors.muted)
      FilterMenu(StrPlaces.destination, options, to, { to = it ?: StockLedger.MAIN }, StockPlaces.label(StockLedger.MAIN))
    }
    Spacer(Modifier.height(8.dp))
    NumberField(amount, { amount = it }, StrPlaces.amount, Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(reason, { reason = it.take(80) }, label = { Text(StrPlaces.reason) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(StrPlaces.close) }
      Button(
        enabled = productId != null && from != to && milliOf(amount) > 0,
        onClick = { onTransfer(productId ?: return@Button, from, to, milliOf(amount), reason); amount = "" },
        modifier = Modifier.weight(1f),
      ) { Text(StrPlaces.transfer) }
    }
  }
}

/* ======================= INV-21 · بسته‌بندیِ دوباره و ساختِ بسته ======================= */

private data class PartDraft(val productId: String, val qty: String)

@Composable
fun RepackDialog(
  d: ShopData,
  /** محل‌ها وقتی «محل‌ها و انتقال» روشن است؛ `null` ⇒ موجودیِ کل */
  places: List<String>?,
  onDismiss: () -> Unit,
  onBreak: (fromId: String, packsMilli: Long, toId: String, perPackMilli: Long, location: String?, note: String) -> Unit,
  onBuild: (bundleId: String, countMilli: Long, parts: List<Repack.Part>, location: String?, note: String) -> Unit,
) {
  var build by remember { mutableStateOf(false) }
  var location by remember { mutableStateOf(StockLedger.MAIN) }
  var note by remember { mutableStateOf("") }
  var fromId by remember { mutableStateOf<String?>(null) }
  var toId by remember { mutableStateOf<String?>(null) }
  var packs by remember { mutableStateOf("1") }
  var perPack by remember { mutableStateOf("") }
  var bundleId by remember { mutableStateOf<String?>(null) }
  var count by remember { mutableStateOf("1") }
  var parts by remember { mutableStateOf(listOf<PartDraft>()) }
  var adding by remember { mutableStateOf(false) }
  val loc = if (places != null) location else null
  val byId = remember(d) { d.products.associateBy { it.id } }

  DialogFrame(StrPlaces.repackTitle, if (build) StrPlaces.buildNote else StrPlaces.breakNote, onDismiss) {
    SegmentedChoice(listOf("break" to StrPlaces.modeBreak, "build" to StrPlaces.modeBuild), if (build) "build" else "break", { build = it == "build" })
    if (places != null && places.size > 1) {
      Spacer(Modifier.height(8.dp))
      FilterMenu(StrPlaces.place, places.map { it to StockPlaces.label(it) }, location, { location = it ?: StockLedger.MAIN }, StockPlaces.label(StockLedger.MAIN))
    }
    Spacer(Modifier.height(10.dp))
    val planned: Repack.Planned? = if (!build) {
      ProductPick(d, StrPlaces.packProduct, fromId) { fromId = it }
      Spacer(Modifier.height(6.dp))
      ProductPick(d, StrPlaces.unitProduct, toId) { id ->
        toId = id
        //  اندازهٔ نخستین بستهٔ همان کالا (PRD-04: «کارتنِ ۲۴ دانه‌ای») ⇒ پیش‌فرضِ «دانه در هر کارتن»
        byId[id]?.packs?.firstOrNull()?.let { perPack = PackBarcodes.sizeText(it.sizeMilli) }
      }
      Spacer(Modifier.height(6.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(packs, { packs = it }, StrPlaces.packs, Modifier.weight(1f))
        NumberField(perPack, { perPack = it }, StrPlaces.perPack, Modifier.weight(1f))
      }
      val f = fromId
      val t = toId
      if (f != null && t != null) Repack.planBreak(d, f, milliOf(packs), t, milliOf(perPack), loc) else null
    } else {
      ProductPick(d, StrPlaces.bundleProduct, bundleId) { id ->
        bundleId = id
        if (id != null && parts.isEmpty()) parts = Repack.lastRecipe(d, id).map { PartDraft(it.productId, PackBarcodes.sizeText(it.qtyMilli)) }
      }
      Spacer(Modifier.height(6.dp))
      NumberField(count, { count = it }, StrPlaces.count, Modifier.fillMaxWidth())
      Spacer(Modifier.height(8.dp))
      Text(StrPlaces.parts, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
      parts.forEachIndexed { i, pt ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(byId[pt.productId]?.name.orEmpty(), color = Shop.colors.text, modifier = Modifier.weight(1f))
          NumberField(pt.qty, { v -> parts = parts.toMutableList().also { it[i] = pt.copy(qty = v) } }, StrPlaces.amount, Modifier.width(110.dp))
          TextButton(onClick = { parts = parts.filterIndexed { j, _ -> j != i } }) { Text(StrPlaces.remove) }
        }
      }
      if (adding) {
        ProductPick(d, StrPlaces.addPart, null) { id -> if (id != null && parts.none { it.productId == id }) parts = parts + PartDraft(id, "1"); adding = false }
      } else if (parts.size < Repack.MAX_PARTS) {
        TextButton(onClick = { adding = true }) { Text(StrPlaces.addPart) }
      }
      val b = bundleId
      if (b != null && parts.isNotEmpty()) Repack.planBuild(d, b, milliOf(count), parts.map { Repack.Part(it.productId, milliOf(it.qty)) }, loc) else null
    }
    Spacer(Modifier.height(8.dp))
    when (planned) {
      is Repack.Planned.Bad -> Text(planned.message, style = MaterialTheme.typography.labelSmall, color = Shop.colors.danger)
      is Repack.Planned.Ok -> {
        for (l in planned.plan.inputs) {
          val p = byId[l.productId]
          Text("− " + StrPlaces.need(milliText(l.qtyMilli), p?.unit.orEmpty(), p?.name.orEmpty(), milliText(l.haveMilli)), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
        }
        val o = planned.plan.output
        val p = byId[o.productId]
        Text(StrPlaces.result(milliText(o.qtyMilli), p?.unit.orEmpty(), p?.name.orEmpty(), money(o.unitMinor / 100.0)), style = MaterialTheme.typography.bodyMedium, color = Shop.colors.success)
      }
      null -> {}
    }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(note, { note = it.take(80) }, label = { Text(StrPlaces.note) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(StrPlaces.close) }
      Button(
        enabled = planned is Repack.Planned.Ok,
        onClick = {
          if (!build) onBreak(fromId ?: return@Button, milliOf(packs), toId ?: return@Button, milliOf(perPack), loc, note)
          else onBuild(bundleId ?: return@Button, milliOf(count), parts.map { Repack.Part(it.productId, milliOf(it.qty)) }, loc, note)
        },
        modifier = Modifier.weight(1f),
      ) { Text(StrPlaces.save) }
    }
  }
}
