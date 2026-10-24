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
      TableColumn<PromoCore.Rule>("نام", 220.dp, sortKey = { it.name }) { it.name + if (it.code.isNotEmpty()) "  · ${it.code}" else "" },
      TableColumn<PromoCore.Rule>("نوع", 130.dp, sortKey = { it.kind }) { PromoText.kindLabel(it.kind) },
      TableColumn<PromoCore.Rule>("مقدار", 170.dp) { PromoText.valueText(it) },
      TableColumn<PromoCore.Rule>("دامنه", 160.dp) { PromoText.scopeText(it, d) },
      TableColumn<PromoCore.Rule>("زمان", 150.dp) { PromoText.windowText(it) },
      TableColumn<PromoCore.Rule>("ترکیب", 100.dp, sortKey = { it.stack }) { PromoText.stackLabel(it.stack) },
      TableColumn<PromoCore.Rule>("وضعیت", 110.dp, sortKey = { PromoText.status(it, today).ordinal },
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
      say(if (failed == 0) "انجام شد" else "$failed قاعده ذخیره نشد")
    }
  }

  Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(Space.md)) {
      item(key = "head") {
        //  نامِ صفحه را سربرگ می‌گوید؛ این‌جا فقط توضیح و «قاعدهٔ تازه» (بی تکرار)
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("قاعده‌های خودکار و کدهای تخفیفِ صندوق", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = c.muted)
          if (snap.manage) Button(onClick = addNew) { Text("+ قاعدهٔ تازه") }
        }
        Spacer(Modifier.height(Space.md))
      }
      if (!ready) {
        item(key = "signin") { Panel { EmptyNote("تخفیف‌ها مالِ حسابِ دکان‌اند. برای دیدن و ساختنشان واردِ حسابِ دکان شوید.") } }
      }
      item(key = "switch") {
        Row(
          Modifier.fillMaxWidth().background(c.surfaceSolid, Shape.card).padding(Space.md),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(Modifier.weight(1f)) {
            Text("تخفیف‌های خودکار در صندوق", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = c.text)
            Text(
              if (snap.enabled) "روشن: قاعده‌های فعال در سبدِ همهٔ دستگاه‌های این دکان حساب می‌شوند."
              else "خاموش: صندوق دقیقاً همان جمعِ همیشه را حساب می‌کند.",
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
                say(err ?: if (on) "تخفیف‌های خودکار روشن شد" else "تخفیف‌های خودکار خاموش شد")
              }
            },
          )
        }
        Spacer(Modifier.height(Space.sm))
      }
      item(key = "stats") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
          val active = snap.rules.count { PromoText.status(it, today) == PromoText.Status.ACTIVE }
          StatCard("فعال", active.toString(), "همین حالا", Modifier.weight(1f), icon = Icons.Filled.CheckCircle,
            selected = tab == PromoText.Tab.ACTIVE, onClick = { tab = PromoText.Tab.ACTIVE })
          StatCard("با کد", snap.rules.count { it.code.isNotEmpty() }.toString(), "کدِ تخفیف", Modifier.weight(1f), icon = Icons.Filled.Sell)
          StatCard("زمان‌دار", snap.rules.count { PromoText.windowText(it) != "همیشه" }.toString(), "ساعت یا روز", Modifier.weight(1f), icon = Icons.Filled.Schedule)
          StatCard("همه", snap.rules.size.toString(), "قاعده", Modifier.weight(1f), icon = Icons.Filled.LocalOffer,
            selected = tab == PromoText.Tab.ALL, onClick = { tab = PromoText.Tab.ALL })
        }
        Spacer(Modifier.height(Space.sm))
      }
      item(key = "toolbar") {
        TableToolbar(
          query = query,
          onQuery = { query = it },
          placeholder = "جست‌وجو در نام یا کد",
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
                loading -> "در حالِ خواندن…"
                snap.rules.isEmpty() -> "هنوز قاعده‌ای نیست. با «قاعدهٔ تازه» اولی را بسازید."
                else -> "چیزی با این جست‌وجو نیست."
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
          BulkAction("فعال", Icons.Filled.CheckCircle) { bulkActive(true) },
          BulkAction("غیرفعال", Icons.Filled.Block) { bulkActive(false) },
          BulkAction("حذف", Icons.Filled.Delete, destructive = true) { deleting = selectedRules },
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
      onSaved = { editing = null; say("قاعده ذخیره شد") },
    )
  }

  if (deleting.isNotEmpty()) {
    val names = deleting.joinToString("، ") { "«${it.name}»" }
    val codes = deleting.mapNotNull { it.code.takeIf { c -> c.isNotEmpty() } }
    AlertDialog(
      onDismissRequest = { if (!busy) deleting = emptyList() },
      title = { Text("حذفِ قاعده") },
      text = {
        Text(
          "$names از صندوق برداشته می‌شود." +
            if (codes.isNotEmpty()) " کدِ ${codes.joinToString("، ")} آزاد نمی‌شود و دوباره به کار نمی‌رود." else "",
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
            say(if (failed == 0) "حذف شد" else "$failed قاعده حذف نشد")
          }
        }) { Text("حذف", color = Shop.colors.danger) }
      },
      dismissButton = { TextButton(enabled = !busy, onClick = { deleting = emptyList() }) { Text("انصراف") } },
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
      .then(if (canEdit) Modifier.clickable(onClickLabel = "ویرایشِ قاعده", onClick = onEdit) else Modifier)
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
        TextButton(onClick = onEdit) { Text("ویرایش") }
        TextButton(onClick = onDelete) { Text("حذف", color = c.danger) }
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
  var f by remember(rule.id) { mutableStateOf(PromoForm.of(rule).let { if (isNew) it.copy(name = "") else it }) }
  var error by remember { mutableStateOf("") }
  var saving by remember { mutableStateOf(false) }
  val categories = remember(d.products) { d.products.map { it.category }.filter { it.isNotBlank() }.distinct().sorted() }
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
      Text(if (isNew) "قاعدهٔ تازه" else "ویرایشِ قاعده", style = MaterialTheme.typography.titleMedium, color = c.text)
      OutlinedTextField(
        value = f.name, onValueChange = { f = f.copy(name = it.take(80)) },
        label = { Text("نام (روی سبد دیده می‌شود)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
      )
      Choice("نوع", PromoText.KINDS, f.kind) { f = f.copy(kind = it, tiers = listOf("" to ""), tierAmount = false, pct = if (it == "bxgy") "100" else f.pct) }

      when (f.kind) {
        "percent" -> NumField("درصدِ تخفیف", f.pct) { f = f.copy(pct = it) }
        "amount" -> NumField("مبلغ از هر واحد (؋)", f.amount) { f = f.copy(amount = it) }
        "price" -> {
          ProductPick("کالا", if (f.priceProduct.isEmpty()) emptySet() else setOf(f.priceProduct), d, single = true) {
            f = f.copy(priceProduct = it.firstOrNull().orEmpty())
          }
          NumField("قیمتِ ویژهٔ هر واحد (؋)", f.priceUnit) { f = f.copy(priceUnit = it) }
        }
        "tiers", "threshold" -> {
          if (f.kind == "threshold") {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
              FilterChip(selected = !f.tierAmount, onClick = { f = f.copy(tierAmount = false) }, label = { Text("درصد") })
              FilterChip(selected = f.tierAmount, onClick = { f = f.copy(tierAmount = true) }, label = { Text("مبلغ") })
            }
          }
          f.tiers.forEachIndexed { i, (a, b) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
              NumField(if (f.kind == "threshold") "از مبلغِ (؋)" else "از تعدادِ", a, Modifier.weight(1f)) { v ->
                f = f.copy(tiers = f.tiers.mapIndexed { j, t -> if (j == i) v to t.second else t })
              }
              NumField(if (f.kind == "threshold" && f.tierAmount) "تخفیف (؋)" else "تخفیف (٪)", b, Modifier.weight(1f)) { v ->
                f = f.copy(tiers = f.tiers.mapIndexed { j, t -> if (j == i) t.first to v else t })
              }
              if (f.tiers.size > 1) TextButton(onClick = { f = f.copy(tiers = f.tiers.filterIndexed { j, _ -> j != i }) }) { Text("✕") }
            }
          }
          OutlinedButton(onClick = { f = f.copy(tiers = f.tiers + ("" to "")) }) { Text("+ پله") }
        }
        "bxgy" -> {
          Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            NumField("بخر", f.buy, Modifier.weight(1f)) { f = f.copy(buy = it) }
            NumField("بگیر", f.get, Modifier.weight(1f)) { f = f.copy(get = it) }
            NumField("تخفیفِ «بگیر» (٪)", f.pct, Modifier.weight(1f)) { f = f.copy(pct = it) }
          }
          Hint("ارزان‌ترین واحدهای دامنه تخفیف می‌گیرند — «هر ۵ تا، دو ارزان‌تر رایگان» = بخر ۳ بگیر ۲.")
        }
        "bundle" -> Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
          NumField("تعدادِ بسته", f.bundleQty, Modifier.weight(1f)) { f = f.copy(bundleQty = it) }
          NumField("قیمتِ بسته (؋)", f.bundlePrice, Modifier.weight(1f)) { f = f.copy(bundlePrice = it) }
        }
      }

      Section("روی کدام کالاها")
      if (categories.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
          categories.forEach { cat ->
            FilterChip(
              selected = cat in f.categories,
              onClick = { f = f.copy(categories = if (cat in f.categories) f.categories - cat else f.categories + cat) },
              label = { Text(cat) },
            )
          }
        }
      }
      if (f.kind != "price") ProductPick("کالاهای مشخص", f.products, d) { f = f.copy(products = it) }
      ProductPick("جز این کالاها", f.exProducts, d) { f = f.copy(exProducts = it) }
      Hint("هیچ دسته یا کالایی نزنید ⇐ همهٔ کالاها. «جز» بر «شامل» مقدم است.")

      Section("چه وقت")
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
        TextIn("از ساعت (۱۷:۰۰)", f.start, Modifier.weight(1f)) { f = f.copy(start = it) }
        TextIn("تا ساعت (۱۹:۰۰)", f.end, Modifier.weight(1f)) { f = f.copy(end = it) }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        TextIn("از تاریخ (2026-10-01)", f.from, Modifier.weight(1f)) { f = f.copy(from = it) }
        TextIn("تا تاریخ", f.to, Modifier.weight(1f)) { f = f.copy(to = it) }
      }
      Hint("هیچ‌کدام ⇐ همیشه. ساعتی که از نیمه‌شب رد شود (۲۲:۰۰ تا ۰۲:۰۰) به روزِ شروعش تعلق دارد.")

      Section("ترکیب و کد")
      Choice("ترکیب با تخفیف‌های دیگر", PromoText.STACKS, f.stack) { f = f.copy(stack = it) }
      Hint(PromoText.STACK_HINTS[f.stack].orEmpty())
      Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        NumField("اولویت", f.priority, Modifier.weight(1f)) { f = f.copy(priority = it) }
        TextIn("کدِ تخفیف (اختیاری)", f.code, Modifier.weight(2f)) { f = f.copy(code = it) }
      }
      Hint("بی کد ⇐ خودکار. کد را صندوقدار در سبد می‌زند؛ کدِ هر قاعده در این دکان یکتاست.")

      Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = f.active, onCheckedChange = { f = f.copy(active = it) })
        Text("فعال", color = c.text)
      }
      if (canBelowCost) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = f.allowBelowCost, onCheckedChange = { f = f.copy(allowBelowCost = it) })
          Text("اجازهٔ فروش زیرِ بهای خرید (فقط صاحبِ دکان)", color = c.text)
        }
      } else if (lockedBelow) {
        Hint("صاحبِ دکان برای این قاعده «زیرِ بهای خرید» را روشن کرده؛ ذخیره با نقشِ شما آن را خاموش می‌کند.")
      }
      Hint("بی آن، تخفیف هیچ کالایی را زیرِ بهای خریدش نمی‌برد.")

      if (error.isNotEmpty()) Text(error, color = c.danger, style = MaterialTheme.typography.bodySmall)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs, Alignment.End)) {
        TextButton(enabled = !saving, onClick = onDismiss) { Text("انصراف") }
        Button(enabled = !saving, onClick = { save() }) { Text(if (saving) "در حالِ ذخیره…" else "ذخیره") }
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
            label = { Text((d.products.firstOrNull { it.id == id }?.name ?: "کالای حذف‌شده") + "  ✕") },
          )
        }
      }
    }
    OutlinedTextField(
      value = q, onValueChange = { q = it }, label = { Text("$label — جست‌وجوی نام یا بارکد") },
      singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    val term = ir.vil3ntec.tohid.normalizeDigits(q).trim()
    if (term.isNotEmpty()) {
      val hits = d.products.asSequence()
        .filter { it.active && it.id !in selected && (it.name.contains(term, ignoreCase = true) || it.barcodes.any { b -> b.startsWith(term) }) }
        .take(8).toList()
      if (hits.isEmpty()) Hint("کالایی پیدا نشد.")
      hits.forEach { p ->
        Text(
          p.name,
          Modifier.fillMaxWidth().clickable(onClickLabel = "افزودن") {
            onChange(if (single) setOf(p.id) else selected + p.id)
            q = ""
          }.padding(vertical = Space.xs, horizontal = Space.sm),
          color = c.text,
        )
      }
    }
  }
}
