package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.StrPlaces
import ir.vil3ntec.tohid.core.product.PackBarcodes
import ir.vil3ntec.tohid.core.stock.Lot
import ir.vil3ntec.tohid.core.stock.Pick
import ir.vil3ntec.tohid.data.Bom
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ProductPlansMap
import ir.vil3ntec.tohid.data.Repack
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.StockActions
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

private fun milliText(milli: Long): String = (if (milli < 0) "−" else "") + qty(kotlin.math.abs(milli) / 1000.0)

/** عددِ تایپ‌شده ⇐ هزارم؛ خالی یا نادرست ⇒ ۰ */
private fun milliOf(raw: String): Long = raw.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }?.let(StockLedger::milli) ?: 0L

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

/** «سری ۱۲ · انقضا ۱۴۰۵/۱۰/۰۱ · ۶ عدد» */
private fun lotText(l: Lot, unit: String): String = listOf(
  StockActions.lotLabel(l.lotId),
  l.expiryDate?.let { StrPlaces.expires(I18n.dateNumeric(it, I18n.lang, I18n.CAL_SOLAR)) } ?: StrPlaces.noExpiry,
  "${milliText(l.qtyMilli)} $unit",
).joinToString(" · ")

/**
 *  سری‌های مبدأ و انتخاب: FEFO (پیش‌فرض، همان `StockPlaces.fefo`) با پیش‌نمایشِ آنچه می‌رود، یا مقدارِ هر سری.
 *  [picks] متنِ تایپ‌شدهٔ هر سری؛ بی سری فقط با FEFO (`StockPlaces.transfer` همان را می‌سنجد).
 */
@Composable
private fun LotChoice(lots: List<Lot>, unit: String, byLot: Boolean, onMode: (Boolean) -> Unit, amountMilli: Long, picks: Map<String, String>, onPick: (String, String) -> Unit) {
  Spacer(Modifier.height(8.dp))
  Text(StrPlaces.lotsHere, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
  SegmentedChoice(listOf("fefo" to StrPlaces.byFefo, "lot" to StrPlaces.byLot), if (byLot) "lot" else "fefo", { onMode(it == "lot") })
  Spacer(Modifier.height(4.dp))
  for (l in lots) {
    if (byLot && l.lotId.isNotEmpty()) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(lotText(l, unit), style = MaterialTheme.typography.bodySmall, color = Shop.colors.text, modifier = Modifier.weight(1f))
        NumberField(picks[l.lotId].orEmpty(), { onPick(l.lotId, it) }, StrPlaces.amount, Modifier.width(110.dp))
      }
    } else {
      Text(lotText(l, unit), style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
    }
  }
  if (byLot) {
    if (lots.any { it.lotId.isEmpty() }) Text(StrPlaces.lotlessHint, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
  } else if (amountMilli > 0) {
    val go = StockPlaces.fefo(lots, amountMilli).picks
    if (go.isNotEmpty()) {
      Text(
        StrPlaces.moves(go.joinToString("، ") { "${StockActions.lotLabel(it.lotId)} ${milliText(it.qtyMilli)}" }),
        style = MaterialTheme.typography.labelSmall, color = Shop.colors.success,
      )
    }
  }
}

@Composable
fun StockPlacesDialog(
  d: ShopData,
  onDismiss: () -> Unit,
  onAdd: (name: String) -> Unit,
  onArchive: (name: String) -> Unit,
  /** [lots]: سری‌های برگزیده؛ `null` ⇒ FEFO */
  onTransfer: (productId: String, from: String, to: String, qtyMilli: Long, reason: String, lots: List<Pick>?) -> Unit,
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
    //  سری‌های مبدأ (همان `StockPlaces.lotsAt`)؛ مبدأِ بی سری ⇒ همان پنجرهٔ پیشین
    val lots = remember(d, stock, productId, from) {
      productId?.let { pid -> StockPlaces.lotsAt(d, pid, stock)[from].orEmpty().filter { it.qtyMilli > 0 } }.orEmpty()
    }
    val lotted = lots.any { it.lotId.isNotEmpty() }
    var byLot by remember(productId, from) { mutableStateOf(false) }
    var picks by remember(productId, from) { mutableStateOf(mapOf<String, String>()) }
    val chosen = if (lotted && byLot) picks.mapNotNull { (lot, raw) -> milliOf(raw).takeIf { it > 0 }?.let { Pick(lot, it) } } else null
    val qtyMilli = chosen?.sumOf { it.qtyMilli } ?: milliOf(amount)
    if (chosen == null) NumberField(amount, { amount = it }, StrPlaces.amount, Modifier.fillMaxWidth())
    if (lotted) {
      val unit = d.products.find { it.id == productId }?.unit.orEmpty()
      LotChoice(lots, unit, byLot, { byLot = it }, qtyMilli, picks) { lot, raw -> picks = picks + (lot to raw) }
      if (chosen != null) Text(StrPlaces.total(milliText(qtyMilli), unit), style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
    }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(reason, { reason = it.take(80) }, label = { Text(StrPlaces.reason) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(StrPlaces.close) }
      Button(
        enabled = productId != null && from != to && qtyMilli > 0,
        onClick = { onTransfer(productId ?: return@Button, from, to, qtyMilli, reason, chosen); amount = ""; picks = emptyMap() },
        modifier = Modifier.weight(1f),
      ) { Text(StrPlaces.transfer) }
    }
  }
}

/* ======================= INV-21 · بسته‌بندیِ دوباره و ساختِ بسته ======================= */

private data class PartDraft(val productId: String, val qty: String)

/** درصدِ تایپ‌شده ⇐ یک‌دهم‌هزارم (۲٫۵ ⇒ ۲۵۰)؛ خالی ⇒ ۰، نادرست ⇒ ‎-1 */
private fun lossOf(raw: String): Int {
  val v = raw.trim().ifEmpty { return 0 }.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: return -1
  return kotlin.math.round(v * 100).toInt()
}

/** «A1 ۲ · بی سری ۱» — برداشتِ سری‌دارِ یک جزء */
private fun usesText(uses: List<Repack.Use>): String =
  uses.joinToString("، ") { "${StockActions.lotLabel(it.lotId)} ${milliText(it.qtyMilli)}" }

@Composable
fun RepackDialog(
  d: ShopData,
  /** محل‌ها وقتی «محل‌ها و انتقال» روشن است؛ `null` ⇒ موجودیِ کل */
  places: List<String>?,
  onDismiss: () -> Unit,
  onBreak: (fromId: String, packsMilli: Long, toId: String, perPackMilli: Long, location: String?, note: String, lots: List<Pick>?) -> Unit,
  onBuild: (bundleId: String, countMilli: Long, parts: List<Repack.Part>, location: String?, note: String, lossBp: Int) -> Unit,
  /** INV-21 — «دستورِ ساخت و سریِ تولید» (`StockPlacesPref.bom`)؛ خاموش ⇒ همان پنجرهٔ امروز */
  bomOn: Boolean = false,
  /** ساختن از دستورِ ثبت‌شدهٔ همان کالا، بی تغییر در اجزا */
  onBuildBom: (productId: String, countMilli: Long, location: String?, note: String) -> Unit = { _, _, _, _ -> },
  onSaveBom: (productId: String, parts: List<Repack.Part>, lossBp: Int) -> Unit = { _, _, _ -> },
  onRemoveBom: (productId: String) -> Unit = {},
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
  var loss by remember { mutableStateOf("") }
  /** دستوری که فرم از آن پر شد؛ هر تغییر در اجزا یا اتلاف ⇒ `null` (ساختن با همان فرم) */
  var recipeId by remember { mutableStateOf<String?>(null) }
  var byLot by remember { mutableStateOf(false) }
  var lotPicks by remember { mutableStateOf(mapOf<String, String>()) }
  val loc = if (places != null) location else null
  val byId = remember(d) { d.products.associateBy { it.id } }
  val recipes = if (bomOn) remember(d) { Bom.all(d) } else emptyMap()
  fun fill(r: Bom.Recipe) {
    parts = r.parts.map { PartDraft(it.productId, PackBarcodes.sizeText(it.qtyMilli)) }
    loss = if (r.lossBp > 0) qty(r.lossBp / 100.0) else ""
    recipeId = r.id
  }

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
      //  INV-21 — سری‌های کارتن در همین محل: FEFO (پیش‌فرض) یا مقدارِ هر سری
      val cartonLots = if (bomOn && f != null) remember(d, f, loc) { Repack.lotsOf(d, f, loc) } else emptyList()
      val chosen = if (byLot && cartonLots.any { it.lotId.isNotEmpty() }) {
        lotPicks.mapNotNull { (lot, raw) -> milliOf(raw).takeIf { it > 0 }?.let { Pick(lot, it) } }
      } else null
      if (cartonLots.any { it.lotId.isNotEmpty() }) {
        LotChoice(cartonLots, byId[f]?.unit.orEmpty(), byLot, { byLot = it }, milliOf(packs), lotPicks) { lot, raw -> lotPicks = lotPicks + (lot to raw) }
      }
      if (f != null && t != null) Repack.planBreak(d, f, chosen?.sumOf { it.qtyMilli } ?: milliOf(packs), t, milliOf(perPack), loc, lots = bomOn, chosen = chosen) else null
    } else {
      if (bomOn) {
        //  INV-21 — «دستورِ ساخت»: انتخاب از دستورهای ثبت‌شده (فرم را پر می‌کند)، ثبت یا برداشتنِ دستورِ همین کالا
        Text(StrPlaces.bomPick, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
        if (recipes.isEmpty()) Text(StrPlaces.bomNone, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
        recipes.values.sortedBy { byId[it.productId]?.name.orEmpty() }.forEach { r ->
          Text(
            "${byId[r.productId]?.name.orEmpty()} — ${StrPlaces.bomLine(r.parts.size, qty(r.lossBp / 100.0))}",
            style = MaterialTheme.typography.bodyMedium, color = if (r.id == recipeId) Shop.colors.success else Shop.colors.text,
            modifier = Modifier.fillMaxWidth().clip(Shape.button).clickable { bundleId = r.productId; fill(r) }.padding(10.dp),
          )
        }
        Spacer(Modifier.height(8.dp))
      }
      ProductPick(d, StrPlaces.bundleProduct, bundleId) { id ->
        bundleId = id
        recipeId = null
        val r = id?.let { recipes[it] }
        if (r != null) fill(r)
        else if (id != null && parts.isEmpty()) parts = Repack.lastRecipe(d, id).map { PartDraft(it.productId, PackBarcodes.sizeText(it.qtyMilli)) }
      }
      Spacer(Modifier.height(6.dp))
      if (bomOn) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          NumberField(count, { count = it }, StrPlaces.count, Modifier.weight(1f))
          NumberField(loss, { loss = it; recipeId = null }, StrPlaces.bomLoss, Modifier.weight(1f))
        }
      } else {
        NumberField(count, { count = it }, StrPlaces.count, Modifier.fillMaxWidth())
      }
      Spacer(Modifier.height(8.dp))
      Text(StrPlaces.parts, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
      parts.forEachIndexed { i, pt ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(byId[pt.productId]?.name.orEmpty(), color = Shop.colors.text, modifier = Modifier.weight(1f))
          NumberField(pt.qty, { v -> parts = parts.toMutableList().also { it[i] = pt.copy(qty = v) }; recipeId = null }, StrPlaces.amount, Modifier.width(110.dp))
          TextButton(onClick = { parts = parts.filterIndexed { j, _ -> j != i }; recipeId = null }) { Text(StrPlaces.remove) }
        }
      }
      if (adding) {
        ProductPick(d, StrPlaces.addPart, null) { id -> if (id != null && parts.none { it.productId == id }) { parts = parts + PartDraft(id, "1"); recipeId = null }; adding = false }
      } else if (parts.size < Repack.MAX_PARTS) {
        TextButton(onClick = { adding = true }) { Text(StrPlaces.addPart) }
      }
      val b = bundleId
      val lossBp = lossOf(loss)
      if (bomOn && b != null && parts.isNotEmpty()) {
        val form = parts.map { Repack.Part(it.productId, milliOf(it.qty)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TextButton(enabled = lossBp >= 0, onClick = { onSaveBom(b, form, lossBp) }) { Text(StrPlaces.bomSave) }
          if (recipes[b] != null) TextButton(onClick = { onRemoveBom(b); recipeId = null }) { Text(StrPlaces.bomRemove) }
        }
      }
      when {
        b == null || parts.isEmpty() -> null
        !bomOn -> Repack.planBuild(d, b, milliOf(count), parts.map { Repack.Part(it.productId, milliOf(it.qty)) }, loc)
        lossBp < 0 -> Repack.Planned.Bad(ir.vil3ntec.tohid.core.i18n.StrGoods.bomLossBad(qty(Bom.MAX_LOSS_BP / 100.0)))
        recipeId != null && recipes[b]?.id == recipeId -> Bom.plan(d, b, milliOf(count), loc)
        else -> Bom.planParts(d, b, milliOf(count), parts.map { Repack.Part(it.productId, milliOf(it.qty)) }, lossBp, loc)
      }
    }
    Spacer(Modifier.height(8.dp))
    when (planned) {
      is Repack.Planned.Bad -> Text(planned.message, style = MaterialTheme.typography.labelSmall, color = Shop.colors.danger)
      is Repack.Planned.Ok -> {
        for (l in planned.plan.inputs) {
          val p = byId[l.productId]
          Text("− " + StrPlaces.need(milliText(l.qtyMilli), p?.unit.orEmpty(), p?.name.orEmpty(), milliText(l.haveMilli)), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
          //  INV-21 — سری‌هایی که برداشته می‌شوند (فقط وقتی جزء سری دارد)
          val us = planned.plan.uses?.filter { it.productId == l.productId }.orEmpty()
          if (us.any { it.lotId.isNotEmpty() }) Text("   " + StrPlaces.lotsOut(usesText(us)), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
        }
        val o = planned.plan.output
        val p = byId[o.productId]
        Text(StrPlaces.result(milliText(o.qtyMilli), p?.unit.orEmpty(), p?.name.orEmpty(), money(o.unitMinor / 100.0)), style = MaterialTheme.typography.bodyMedium, color = Shop.colors.success)
        planned.plan.outs?.filter { it.expiryDate.isNotEmpty() || it.batch.isNotEmpty() }?.forEach { out ->
          Text(
            "   " + listOfNotNull(
              out.batch.ifEmpty { null }?.let { StockActions.lotLabel(it) },
              out.expiryDate.ifEmpty { null }?.let { StrPlaces.expires(I18n.dateNumeric(it, I18n.lang, I18n.CAL_SOLAR)) },
              milliText(out.qtyMilli),
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall, color = Shop.colors.success,
          )
        }
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
          if (!build) {
            val lotted = byLot && lotPicks.isNotEmpty()
            val chosen = if (lotted) lotPicks.mapNotNull { (lot, raw) -> milliOf(raw).takeIf { it > 0 }?.let { Pick(lot, it) } } else null
            onBreak(fromId ?: return@Button, chosen?.sumOf { it.qtyMilli } ?: milliOf(packs), toId ?: return@Button, milliOf(perPack), loc, note, chosen)
          } else {
            val b = bundleId ?: return@Button
            if (bomOn && recipeId != null && recipes[b]?.id == recipeId) onBuildBom(b, milliOf(count), loc, note)
            else onBuild(b, milliOf(count), parts.map { Repack.Part(it.productId, milliOf(it.qty)) }, loc, note, lossOf(loss).coerceAtLeast(0))
          }
        },
        modifier = Modifier.weight(1f),
      ) { Text(StrPlaces.save) }
    }
  }
}
