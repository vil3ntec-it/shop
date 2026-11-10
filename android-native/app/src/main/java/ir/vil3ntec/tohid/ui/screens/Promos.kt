package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrFields
import ir.vil3ntec.tohid.core.i18n.StrGrid
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.core.i18n.StrInvoices
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrLine
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.i18n.StrOrderPages
import ir.vil3ntec.tohid.core.i18n.StrPlaces
import ir.vil3ntec.tohid.core.i18n.StrProductPages
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.core.i18n.StrPromoPages
import ir.vil3ntec.tohid.core.i18n.StrReports
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.i18n.StrShrink
import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.core.promo.PromoCore
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.PromoLive
import ir.vil3ntec.tohid.ui.components.common.BulkAction
import ir.vil3ntec.tohid.ui.components.common.BulkActionBar
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.ScreenKind
import ir.vil3ntec.tohid.ui.components.common.SegmentTab
import ir.vil3ntec.tohid.ui.components.common.SegmentedTabs
import ir.vil3ntec.tohid.ui.components.common.StatCard
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.components.common.TableToolbar
import ir.vil3ntec.tohid.ui.components.common.ViewMode
import ir.vil3ntec.tohid.ui.components.common.rememberViewMode
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.Space
import kotlinx.coroutines.launch

/**
 *  «تخفیف‌ها» — صفحهٔ صاحب و مدیر (PRM-01..09، 12، 17).
 *
 *  کلیدِ دکان (پیش‌فرض خاموش)، آمار، جست‌وجو و زبانه‌ها، فهرستِ کارتی/جدولی
 *  (`ViewModePref`)، کارِ گروهی (فعال/غیرفعال/حذف) و ویرایشگرِ قاعده. هر
 *  قاعده پیش از فرستادن با `PromoCore.validate` سنجیده می‌شود و سرور هم
 *  دوباره می‌سنجد. گروهِ مشتری (CRM-08) و کانالِ ویترین و سقفِ مصرفِ کد این‌جا
 *  نیامده‌اند — هنوز چیزی پشتشان نیست و دکمهٔ بی اثر نمی‌سازیم.
 */
@Composable
fun PromosScreen(d: ShopData, snackbar: SnackbarHostState) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val c = Shop.colors
  val snap = PromoLive.snap.value
  val ready = remember { Backend.isReady(context) } || PromoLive.isDemo
  var loading by remember { mutableStateOf(true) }
  LaunchedEffect(Unit) {
    PromoLive.refresh(context)?.let { snackbar.showSnackbar(it) }
    loading = false
  }
  var query by remember { mutableStateOf("") }
  var tab by remember { mutableStateOf(PromoText.Tab.ALL) }
  val view = rememberViewMode("promos", ScreenKind.MANAGEMENT)
  var editing by remember { mutableStateOf<PromoCore.Rule?>(null) }
  var deleting by remember { mutableStateOf<List<PromoCore.Rule>>(emptyList()) }
  var busy by remember { mutableStateOf(false) }
  val today = todayIso()
  fun say(m: String) { scope.launch { snackbar.showSnackbar(m) } }
  //  «قاعدهٔ تازه» با Ctrl+N هم (`AppNav.addAction`) — فقط برای صاحب و مدیر
  val addNew = remember { { editing = PromoCore.Rule(id = newPromoId(), channels = listOf("pos")) } }
  androidx.compose.runtime.DisposableEffect(snap.manage) {
    if (snap.manage) ir.vil3ntec.tohid.ui.AppNav.addAction.value = addNew
    onDispose { if (ir.vil3ntec.tohid.ui.AppNav.addAction.value === addNew) ir.vil3ntec.tohid.ui.AppNav.addAction.value = null }
  }

  val columns = remember(d) {
    listOf(
      TableColumn<PromoCore.Rule>(StrGrid.name, 220.dp, sortKey = { it.name }) { it.name + if (it.code.isNotEmpty()) "  · ${it.code}" else "" },
      TableColumn<PromoCore.Rule>(StrInvoices.colKind, 130.dp, sortKey = { it.kind }) { PromoText.kindLabel(it.kind) },
      TableColumn<PromoCore.Rule>(StrPlaces.amount, 170.dp) { PromoText.valueText(it) },
      TableColumn<PromoCore.Rule>(StrPromoPages.scope, 160.dp) { PromoText.scopeText(it, d) },
      TableColumn<PromoCore.Rule>(StrReports.when_, 150.dp) { PromoText.windowText(it) },
      TableColumn<PromoCore.Rule>(StrPromoPages.stacking, 100.dp, sortKey = { it.stack }) { PromoText.stackLabel(it.stack) },
      TableColumn<PromoCore.Rule>(StrMoney.status, 110.dp, sortKey = { PromoText.status(it, today).ordinal },
        tint = { r -> if (PromoText.status(r, today) == PromoText.Status.ACTIVE) Shop.colors.success else Shop.colors.muted }) {
        PromoText.status(it, today).label
      },
    )
  }
  val table = rememberTableState(columns)
  val shown = PromoText.filter(snap.rules, query, tab, today)
  val selectedRules = snap.rules.filter { it.id in table.selected }

  fun bulkActive(on: Boolean) {
    scope.launch {
      busy = true
      var failed = 0
      for (r in selectedRules) if (r.active != on && PromoLive.save(context, r.copy(active = on)) != null) failed++
      busy = false
      table.selected = emptySet()
      say(if (failed == 0) StrKit.done else StrPromoPages.rulesNotSaved(failed))
    }
  }

  Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(Space.md)) {
      item(key = "head") {
        //  نامِ صفحه را سربرگ می‌گوید؛ این‌جا فقط توضیح و «قاعدهٔ تازه» (بی تکرار)
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(StrPromoPages.subtitle, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = c.muted)
          if (snap.manage) Button(onClick = addNew) { Text(StrPromoPages.newRuleBtn) }
        }
        Spacer(Modifier.height(Space.md))
      }
      if (!ready) {
        item(key = "signin") { Panel { EmptyNote(StrPromoPages.signInNote) } }
      }
      item(key = "switch") {
        Row(
          Modifier.fillMaxWidth().background(c.surfaceSolid, Shape.card).padding(Space.md),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(Modifier.weight(1f)) {
            Text(StrPromoPages.autoAtTill, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = c.text)
            Text(
              if (snap.enabled) StrPromoPages.autoOnNote
              else StrPromoPages.autoOffNote,
              style = MaterialTheme.typography.bodySmall, color = c.muted,
            )
          }
          Switch(
            checked = snap.enabled,
            enabled = snap.manage && !busy && ready,
            onCheckedChange = { on ->
              scope.launch {
                busy = true
                val err = PromoLive.setEnabled(context, on)
                busy = false
                say(err ?: if (on) StrPromoPages.autoTurnedOn else StrPromoPages.autoTurnedOff)
              }
            },
          )
        }
        Spacer(Modifier.height(Space.sm))
      }
      item(key = "stats") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
          val active = snap.rules.count { PromoText.status(it, today) == PromoText.Status.ACTIVE }
          StatCard(StrSettings.active, active.toString(), StrHome.justNow, Modifier.weight(1f), icon = Icons.Filled.CheckCircle,
            selected = tab == PromoText.Tab.ACTIVE, onClick = { tab = PromoText.Tab.ACTIVE })
          StatCard(StrPromoPages.withCode, snap.rules.count { it.code.isNotEmpty() }.toString(), StrSale.promoCode, Modifier.weight(1f), icon = Icons.Filled.Sell)
          StatCard(StrPromoPages.timed, snap.rules.count { PromoText.windowText(it) != ir.vil3ntec.tohid.core.i18n.StrMarketPages.always }.toString(), StrPromoPages.hourOrDay, Modifier.weight(1f), icon = Icons.Filled.Schedule)
          StatCard(Str.all, snap.rules.size.toString(), StrPromoPages.ruleWord, Modifier.weight(1f), icon = Icons.Filled.LocalOffer,
            selected = tab == PromoText.Tab.ALL, onClick = { tab = PromoText.Tab.ALL })
        }
        Spacer(Modifier.height(Space.sm))
      }
      item(key = "toolbar") {
        TableToolbar(
          query = query,
          onQuery = { query = it },
          placeholder = StrPromoPages.searchNameCode,
          viewMode = view,
          tabs = {
            SegmentedTabs(
              tabs = PromoText.Tab.entries.map { SegmentTab(it, it.label) },
              selected = tab,
              onSelect = { tab = it },
            )
          },
        )
        Spacer(Modifier.height(Space.sm))
      }
      when {
        shown.isEmpty() -> item(key = "empty") {
          Panel {
            EmptyNote(
              when {
                loading -> StrReports.loading
                snap.rules.isEmpty() -> StrPromoPages.noRulesYet
                else -> StrPromoPages.noSearchMatch
              },
            )
          }
        }
        view.mode == ViewMode.TABLE -> dataTable(
          rows = sortRows(shown, columns, table),
          columns = columns,
          state = table,
          key = { it.id },
          onOpen = { if (snap.manage) editing = it },
          onMenu = { if (snap.manage) editing = it },
        )
        else -> items(shown, key = { it.id }) { r ->
          PromoCard(
            r, d, today, snap.manage,
            onEdit = { editing = r },
            onDelete = { deleting = listOf(r) },
          )
          Spacer(Modifier.height(Space.xs))
        }
      }
      item(key = "foot") { Spacer(Modifier.height(96.dp)) }
    }

    if (snap.manage && table.selected.isNotEmpty() && view.mode == ViewMode.TABLE) {
      BulkActionBar(
        count = table.selected.size,
        actions = listOf(
          BulkAction(StrProducts.activate, Icons.Filled.CheckCircle) { bulkActive(true) },
          BulkAction(StrProducts.deactivate, Icons.Filled.Block) { bulkActive(false) },
          BulkAction(StrMoney.delete, Icons.Filled.Delete, destructive = true) { deleting = selectedRules },
        ),
        onClose = { table.selected = emptySet() },
        modifier = Modifier.align(Alignment.BottomCenter).padding(Space.md),
      )
    }
  }

  editing?.let { r ->
    PromoEditor(
      rule = r,
      d = d,
      canBelowCost = snap.belowCost,
      isNew = snap.rules.none { it.id == r.id },
      onDismiss = { editing = null },
      onSaved = { editing = null; say(StrPromoPages.ruleSaved) },
    )
  }

  if (deleting.isNotEmpty()) {
    val names = deleting.joinToString("، ") { "«${it.name}»" }
    val codes = deleting.mapNotNull { it.code.takeIf { c -> c.isNotEmpty() } }
    AlertDialog(
      onDismissRequest = { if (!busy) deleting = emptyList() },
      title = { Text(StrPromoPages.deleteRule) },
      text = {
        Text(
          StrPromoPages.removedFromTill(names) +
            if (codes.isNotEmpty()) StrPromoPages.codesNotFreed(codes.joinToString("، ")) else "",
        )
      },
      confirmButton = {
        TextButton(enabled = !busy, onClick = {
          scope.launch {
            busy = true
            var failed = 0
            for (r in deleting) if (PromoLive.delete(context, r.id) != null) failed++
            busy = false
            deleting = emptyList()
            table.selected = emptySet()
            say(if (failed == 0) StrStock.deleted else StrPromoPages.rulesNotDeleted(failed))
          }
        }) { Text(StrMoney.delete, color = Shop.colors.danger) }
      },
      dismissButton = { TextButton(enabled = !busy, onClick = { deleting = emptyList() }) { Text(Str.cancel) } },
    )
  }
}

/** شناسهٔ قاعدهٔ تازه — ULID، همان `newId()`ِ صفحه‌ها */
private fun newPromoId(): String = "prm_" + ir.vil3ntec.tohid.sync.v1.Ulid.next().lowercase()

@Composable
private fun PromoCard(
  r: PromoCore.Rule,
  d: ShopData,
  today: String,
  canEdit: Boolean,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  val c = Shop.colors
  val st = PromoText.status(r, today)
  Column(
    Modifier
      .fillMaxWidth()
      .background(c.surfaceSolid, Shape.card)
      .then(if (canEdit) Modifier.clickable(onClickLabel = StrPromoPages.editRule, onClick = onEdit) else Modifier)
      .padding(Space.md),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(r.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
        color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
      StatusPill(st.label, if (st == PromoText.Status.ACTIVE) PillTone.SUCCESS else if (st == PromoText.Status.SCHEDULED) PillTone.WARNING else PillTone.NEUTRAL)
      if (r.code.isNotEmpty()) {
        Spacer(Modifier.width(Space.xxs))
        StatusPill(r.code, PillTone.NEUTRAL)
      }
    }
    Spacer(Modifier.height(Space.xxs))
    Text(PromoText.valueText(r), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c.primary)
    Spacer(Modifier.height(Space.xxs))
    Text(
      listOf(PromoText.kindLabel(r.kind), PromoText.scopeText(r, d), PromoText.windowText(r), PromoText.stackLabel(r.stack)).joinToString(" · "),
      style = MaterialTheme.typography.bodySmall, color = c.muted,
    )
    if (canEdit) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onEdit) { Text(StrMoney.edit) }
        TextButton(onClick = onDelete) { Text(StrMoney.delete, color = c.danger) }
      }
    }
  }
}

/* ============================ ویرایشگر ============================ */

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PromoEditor(
  rule: PromoCore.Rule,
  d: ShopData,
  canBelowCost: Boolean,
  isNew: Boolean,
  onDismiss: () -> Unit,
  onSaved: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val c = Shop.colors
  //  PRM-02: «سقفِ مصرف» فقط وقتی سرور مصرف را می‌شمارد (پرچمِ `redeem`)
  val counted = PromoLive.snap.value.redeem
  //  PRM-11: «کجا اعمال شود» فقط وقتی ویترین تخفیف می‌خواند (`online`) یا قاعده از پیش ویترین دارد
  val online = PromoLive.snap.value.online || PromoText.ONLINE in rule.channels
  var f by remember(rule.id) { mutableStateOf(PromoForm.of(rule, uses = counted, online = online).let { if (isNew) it.copy(name = "") else it }) }
  var error by remember { mutableStateOf("") }
  var saving by remember { mutableStateOf(false) }
  val categories = remember(d.products) { d.products.map { it.category }.filter { it.isNotBlank() }.distinct().sorted() }
  //  PRM-08: برندهای کالاها (PRD-06) و هر برندی که خودِ قاعده دارد (تا قاعدهٔ کهنه بی‌صدا برندی را گم نکند)
  val brands = remember(d.products, rule.id) {
    (d.products.mapNotNull { it.brand?.trim()?.takeIf { b -> b.isNotEmpty() } } + rule.scope.brands + rule.scope.exBrands).distinct().sorted()
  }
  //  PRM-08: تأمین‌کنندگانِ دکان و هر شناسه‌ای که خودِ قاعده دارد (تا قاعدهٔ کهنه بی‌صدا چیزی را گم نکند)
  val suppliers = remember(d.suppliers, rule.id) {
    (d.suppliers.map { it.id } + rule.scope.suppliers + rule.scope.exSuppliers).distinct()
      .sortedBy { PromoText.supplierName(it, d) }
  }
  //  PRM-11: گروه‌های مشتریِ دکان (CRM-08) و هر گروهی که خودِ قاعده دارد (تا قاعدهٔ کهنه گروهی را بی‌صدا گم نکند)
  val groups = remember(d.debtors, rule.id) {
    (ir.vil3ntec.tohid.data.CustomerCrm.groups(d).map { it.first } + rule.groups)
      .distinctBy { ir.vil3ntec.tohid.core.text.TextNormalizer.compareKey(it) }.sorted()
  }
  val lockedBelow = rule.allowBelowCost && !canBelowCost

  fun save() {
    if (saving) return
    val next = f.toRule(rule).let { if (!canBelowCost) it.copy(allowBelowCost = false) else it }
    val errs = PromoCore.validate(next)
    if (errs.isNotEmpty()) { error = PromoText.errors(errs); return }
    error = ""
    saving = true
    scope.launch {
      val err = PromoLive.save(context, next)
      saving = false
      if (err == null) onSaved() else error = err
    }
  }

  AdaptiveSheet(onDismissRequest = { if (!saving) onDismiss() }, containerColor = c.bg) {
    Column(
      Modifier
        .fillMaxWidth()
        .then(if (Platform.DESKTOP) Modifier else Modifier.verticalScroll(rememberScrollState()))
        .padding(start = 18.dp, end = 18.dp, bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
      Text(if (isNew) StrPromoPages.newRule else StrPromoPages.editRule, style = MaterialTheme.typography.titleMedium, color = c.text)
      OutlinedTextField(
        value = f.name, onValueChange = { f = f.copy(name = it.take(80)) },
        label = { Text(StrPromoPages.nameOnCart) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
      )
      //  PRM-10: «نزدیکِ انقضا» فقط با پرچمِ `shop.promo_expiry` (یا قاعده‌ای که از پیش چنین است)
      Choice(StrInvoices.colKind, PromoText.kinds(PromoLive.snap.value.expiry || rule.kind == "expiry"), f.kind) { f = f.copy(kind = it, tiers = listOf("" to ""), tierAmount = false, pct = if (it == "bxgy") "100" else f.pct) }

      when (f.kind) {
        "percent" -> NumField(StrPromoPages.discountPercent, f.pct) { f = f.copy(pct = it) }
        "amount" -> NumField(StrPromoPages.amountPerUnit, f.amount) { f = f.copy(amount = it) }
        "price" -> {
          ProductPick(StrMoney.item, if (f.priceProduct.isEmpty()) emptySet() else setOf(f.priceProduct), d, single = true) {
            f = f.copy(priceProduct = it.firstOrNull().orEmpty())
          }
          NumField(StrPromoPages.specialUnitPrice, f.priceUnit) { f = f.copy(priceUnit = it) }
        }
        "tiers", "threshold" -> {
          if (f.kind == "threshold") {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
              FilterChip(selected = !f.tierAmount, onClick = { f = f.copy(tierAmount = false) }, label = { Text(StrLine.percent) })
              FilterChip(selected = f.tierAmount, onClick = { f = f.copy(tierAmount = true) }, label = { Text(StrLine.amount) })
            }
          }
          //  POS-19: پلهٔ «قیمتِ ثابتِ هر واحد» فقط با پرچمِ `shop.promo_tier_price` (یا قاعده‌ای که از پیش چنین است)
          if (f.kind == "tiers" && (PromoLive.snap.value.tierPrice || f.tierAmount)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
              FilterChip(selected = !f.tierAmount, onClick = { f = f.copy(tierAmount = false) }, label = { Text(StrLine.percent) })
              FilterChip(selected = f.tierAmount, onClick = { f = f.copy(tierAmount = true) }, label = { Text(StrLine.tierFixedPrice) })
            }
          }
          f.tiers.forEachIndexed { i, (a, b) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
              NumField(if (f.kind == "threshold") StrPromoPages.fromAmount else StrPromoPages.fromCount, a, Modifier.weight(1f)) { v ->
                f = f.copy(tiers = f.tiers.mapIndexed { j, t -> if (j == i) v to t.second else t })
              }
              NumField(if (f.kind == "threshold" && f.tierAmount) StrPromoPages.discountAfn else if (f.tierAmount) StrLine.tierUnitPrice else StrSale.discountPct, b, Modifier.weight(1f)) { v ->
                f = f.copy(tiers = f.tiers.mapIndexed { j, t -> if (j == i) t.first to v else t })
              }
              if (f.tiers.size > 1) TextButton(onClick = { f = f.copy(tiers = f.tiers.filterIndexed { j, _ -> j != i }) }) { Text("✕") }
            }
          }
          OutlinedButton(onClick = { f = f.copy(tiers = f.tiers + ("" to "")) }) { Text(StrPromoPages.addTier) }
        }
        "bxgy" -> {
          Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            NumField(StrPromoPages.buyN, f.buy, Modifier.weight(1f)) { f = f.copy(buy = it) }
            NumField(StrPromoPages.getN, f.get, Modifier.weight(1f)) { f = f.copy(get = it) }
            NumField(StrPromoPages.getDiscount, f.pct, Modifier.weight(1f)) { f = f.copy(pct = it) }
          }
          Hint(StrPromoPages.bxgyHint)
        }
        "bundle" -> Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
          NumField(StrPlaces.count, f.bundleQty, Modifier.weight(1f)) { f = f.copy(bundleQty = it) }
          NumField(StrPromoPages.bundlePrice, f.bundlePrice, Modifier.weight(1f)) { f = f.copy(bundlePrice = it) }
        }
        "expiry" -> {
          Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            NumField(StrPromoPages.discountPercent, f.pct, Modifier.weight(1f)) { f = f.copy(pct = it) }
            NumField(StrPromoPages.daysBeforeExpiry, f.nearDays, Modifier.weight(1f)) { f = f.copy(nearDays = it) }
          }
          Hint(StrPromoPages.expiryHint)
        }
      }

      Section(StrPromoPages.onWhichItems)
      if (categories.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
          categories.forEach { cat ->
            FilterChip(
              selected = cat in f.categories,
              onClick = { f = f.copy(categories = if (cat in f.categories) f.categories - cat else f.categories + cat, exCategories = f.exCategories - cat) },
              label = { Text(cat) },
            )
          }
        }
      }
      if (brands.isNotEmpty()) {
        Text(StrProductPages.brand, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
          brands.forEach { b ->
            FilterChip(
              selected = b in f.brands,
              onClick = { f = f.copy(brands = if (b in f.brands) f.brands - b else f.brands + b, exBrands = f.exBrands - b) },
              label = { Text(b) },
            )
          }
        }
      }
      if (suppliers.isNotEmpty()) {
        Text(StrMoney.supplierLabel, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
          suppliers.forEach { id ->
            FilterChip(
              selected = id in f.suppliers,
              onClick = { f = f.copy(suppliers = if (id in f.suppliers) f.suppliers - id else f.suppliers + id, exSuppliers = f.exSuppliers - id) },
              label = { Text(PromoText.supplierName(id, d)) },
            )
          }
        }
      }
      if (f.kind != "price") ProductPick(StrPromoPages.specificItems, f.products, d) { f = f.copy(products = it) }
      ProductPick(StrPromoPages.exceptItems, f.exProducts, d) { f = f.copy(exProducts = it) }
      if (brands.isNotEmpty()) {
        Text(StrPromoPages.exceptBrands, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
          brands.forEach { b ->
            FilterChip(
              selected = b in f.exBrands,
              onClick = { f = f.copy(exBrands = if (b in f.exBrands) f.exBrands - b else f.exBrands + b, brands = f.brands - b) },
              label = { Text(b) },
            )
          }
        }
      }
      if (categories.isNotEmpty()) {
        Text(StrPromoPages.exceptCategories, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
          categories.forEach { cat ->
            FilterChip(
              selected = cat in f.exCategories,
              onClick = { f = f.copy(exCategories = if (cat in f.exCategories) f.exCategories - cat else f.exCategories + cat, categories = f.categories - cat) },
              label = { Text(cat) },
            )
          }
        }
      }
      if (suppliers.isNotEmpty()) {
        Text(StrPromoPages.exceptSuppliers, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
          suppliers.forEach { id ->
            FilterChip(
              selected = id in f.exSuppliers,
              onClick = { f = f.copy(exSuppliers = if (id in f.exSuppliers) f.exSuppliers - id else f.exSuppliers + id, suppliers = f.suppliers - id) },
              label = { Text(PromoText.supplierName(id, d)) },
            )
          }
        }
      }
      Hint(StrPromoPages.scopeHint)

      if (groups.isNotEmpty()) {
        Section(ir.vil3ntec.tohid.core.i18n.StrLine.promoGroupsTitle)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
          groups.forEach { g ->
            FilterChip(
              selected = g in f.groups,
              onClick = { f = f.copy(groups = if (g in f.groups) f.groups - g else f.groups + g) },
              label = { Text(g) },
            )
          }
        }
        Hint(ir.vil3ntec.tohid.core.i18n.StrLine.promoGroupsHint)
      }
      f.channel?.let { ch ->
        Section(StrLine.promoWhereTitle)
        Choice(StrLine.promoWhereTitle, PromoText.CHANNELS, ch) { f = f.copy(channel = it) }
        Hint(StrLine.promoWhereHint)
      }

      Section(StrPromoPages.whenSection)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
        PromoText.DAYS.forEach { (iso, label) ->
          FilterChip(
            selected = iso in f.days,
            onClick = { f = f.copy(days = if (iso in f.days) f.days - iso else f.days + iso) },
            label = { Text(label) },
          )
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        TextIn(StrPromoPages.fromHour, f.start, Modifier.weight(1f)) { f = f.copy(start = it) }
        TextIn(StrPromoPages.toHour, f.end, Modifier.weight(1f)) { f = f.copy(end = it) }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        TextIn(StrPromoPages.fromDate, f.from, Modifier.weight(1f)) { f = f.copy(from = it) }
        TextIn(StrPromoPages.toDate, f.to, Modifier.weight(1f)) { f = f.copy(to = it) }
      }
      Hint(StrPromoPages.whenHint)

      Section(StrPromoPages.stackCodeSection)
      Choice(StrPromoPages.stackWithOthers, PromoText.STACKS, f.stack) { f = f.copy(stack = it) }
      Hint(PromoText.STACK_HINTS[f.stack].orEmpty())
      Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        NumField(StrPromoPages.priority, f.priority, Modifier.weight(1f)) { f = f.copy(priority = it) }
        TextIn(StrPromoPages.codeOptional, f.code, Modifier.weight(2f)) { f = f.copy(code = it) }
      }
      Hint(StrPromoPages.codeHint)
      val cap = f.maxUses
      if (cap != null && f.code.isNotBlank()) {
        NumField(StrPromoPages.codeCap, cap) { f = f.copy(maxUses = it) }
        Hint(
          StrPromoPages.codeCapNote +
            if (rule.uses > 0) StrPromoPages.usedSoFar(rule.uses) else "",
        )
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = f.active, onCheckedChange = { f = f.copy(active = it) })
        Text(StrSettings.active, color = c.text)
      }
      if (canBelowCost) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = f.allowBelowCost, onCheckedChange = { f = f.copy(allowBelowCost = it) })
          Text(StrPromoPages.belowCost, color = c.text)
        }
      } else if (lockedBelow) {
        Hint(StrPromoPages.belowCostOwner)
      }
      Hint(StrPromoPages.belowCostOff)

      if (error.isNotEmpty()) Text(error, color = c.danger, style = MaterialTheme.typography.bodySmall)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs, Alignment.End)) {
        TextButton(enabled = !saving, onClick = onDismiss) { Text(Str.cancel) }
        Button(enabled = !saving, onClick = { save() }) { Text(if (saving) StrOrderPages.saving else StrKit.save) }
      }
    }
  }
}

@Composable
private fun Section(text: String) {
  Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Shop.colors.text)
}

@Composable
private fun Hint(text: String) {
  if (text.isNotEmpty()) Text(text, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
}

@Composable
private fun NumField(label: String, value: String, modifier: Modifier = Modifier.fillMaxWidth(), onChange: (String) -> Unit) {
  OutlinedTextField(
    value = value,
    onValueChange = { onChange(ir.vil3ntec.tohid.numericInput(it)) },
    label = { Text(label) },
    singleLine = true,
    isError = ir.vil3ntec.tohid.numberInvalid(value),
    modifier = modifier,
  )
}

@Composable
private fun TextIn(label: String, value: String, modifier: Modifier = Modifier.fillMaxWidth(), onChange: (String) -> Unit) {
  OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, modifier = modifier)
}

/** انتخاب از چند گزینه (نوع، شیوهٔ ترکیب) — همان منوی کشوییِ برنامه (`TohidDropdown`) */
@Composable
private fun Choice(label: String, options: List<Pair<String, String>>, value: String, onPick: (String) -> Unit) {
  TohidDropdown(
    label = label,
    options = options.map { it.second },
    selected = options.firstOrNull { it.first == value }?.second.orEmpty(),
    onSelect = { text -> options.firstOrNull { it.second == text }?.let { onPick(it.first) } },
    modifier = Modifier.fillMaxWidth(),
  )
}

/**
 *  انتخابِ کالا: کالاهای انتخاب‌شده چیپ‌اند (× ⇐ برداشتن)؛ نوشتن در کادر تا
 *  هشت کالای فعال را نشان می‌دهد و زدن روی هرکدام اضافه‌اش می‌کند.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProductPick(label: String, selected: Set<String>, d: ShopData, single: Boolean = false, onChange: (Set<String>) -> Unit) {
  val c = Shop.colors
  var q by remember { mutableStateOf("") }
  Column(Modifier.fillMaxWidth()) {
    if (selected.isNotEmpty()) {
      FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        selected.forEach { id ->
          InputChip(
            selected = true,
            onClick = { onChange(selected - id) },
            label = { Text((d.products.firstOrNull { it.id == id }?.name ?: StrShrink.deletedProduct) + "  ✕") },
          )
        }
      }
    }
    OutlinedTextField(
      value = q, onValueChange = { q = it }, label = { Text(StrPromoPages.searchFor(label)) },
      singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    val term = ir.vil3ntec.tohid.normalizeDigits(q).trim()
    if (term.isNotEmpty()) {
      val hits = d.products.asSequence()
        .filter { it.active && it.id !in selected && (it.name.contains(term, ignoreCase = true) || it.barcodes.any { b -> b.startsWith(term) }) }
        .take(8).toList()
      if (hits.isEmpty()) Hint(StrPromoPages.noItemFound)
      hits.forEach { p ->
        Text(
          p.name,
          Modifier.fillMaxWidth().clickable(onClickLabel = StrFields.add) {
            onChange(if (single) setOf(p.id) else selected + p.id)
            q = ""
          }.padding(vertical = Space.xs, horizontal = Space.sm),
          color = c.text,
        )
      }
    }
  }
}
