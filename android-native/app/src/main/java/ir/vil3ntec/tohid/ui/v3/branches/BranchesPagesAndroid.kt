package ir.vil3ntec.tohid.ui.v3.branches

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.branch.BranchBook
import ir.vil3ntec.tohid.core.branch.BranchKey
import ir.vil3ntec.tohid.core.branch.BranchTally
import ir.vil3ntec.tohid.core.i18n.StrBranchDesk
import ir.vil3ntec.tohid.core.i18n.StrBranches
import ir.vil3ntec.tohid.core.report.BranchesReport
import ir.vil3ntec.tohid.data.BranchDesk
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.ShopBranchesRepository
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.LocalV3Toasts
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import kotlinx.coroutines.launch

/*
 *  شعبه درونِ یک دکان، فازِ ۲ ِ برنامه — صفحه‌ها (حال و منطق در `BranchesState.kt`):
 *   • [BranchHost] (در `AppRoot`): پس از ورود/شروع `GET /shop/branches`؛ یک شعبه ⇒ همان بی پرسش؛ چند ⇒ «در کدام شعبه…»؛
 *     هیچ ⇒ پیامِ روشن؛ شعبه‌ها خاموش و میز روی شعبهٔ دیگر ⇒ میز به اصلی برمی‌گردد.
 *   • [BranchProfileCard] (در «حسابِ من»): نامِ شعبهٔ جاری و راهِ «شعبه‌ها».
 *   • [BranchesPage] (`branches`): شعبهٔ این دستگاه و تعویض؛ برای صاحب ساختن، نام، بایگانی، کارمند ⇐ شعبه.
 *   • [BranchReportPage] (`branches/report`): گزارشِ هر شعبه و «جمعِ همه» (فقط صاحب، از سرور).
 *  ⛔ پرچمِ خاموش ⇒ `BranchLive.book` null ⇒ هیچ‌کدام دیده نمی‌شود.
 */

private const val REPROBE_MS = 10 * 60_000L

/** میزبانِ «انتخابِ شعبه» — یک بار زیرِ `AppRoot` */
@Composable
fun BranchHost(store: ShopStore) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var tick by remember { mutableStateOf(0) }
  var note by remember { mutableStateOf<String?>(null) }

  suspend fun go(b: BranchBook.Branch) {
    val o = BranchDesk.switchTo(context, store, b)
    if (o == BranchDesk.Outcome.SAME || o == BranchDesk.Outcome.SWITCHED) {
      BranchDesk.markPicked(context)
      BranchLive.pick = null
    }
    BranchLive.current = BranchDesk.current(context)
    BranchLive.catalogReadOnly = BranchDesk.refreshCatalog(context).isNotEmpty()
    note = BranchLive.outcomeText(o, BranchLive.label(b))
  }

  suspend fun probe(owner: String) {
    val current = BranchDesk.current(context)
    BranchLive.current = current
    val p = BranchLive.probe(ShopBranchesRepository(Backend.api(context)), current, afterSignIn = BranchDesk.pickedFor(context) != owner)
    if (BranchLive.book != null) BranchDesk.confirmed(current, BranchLive.dismissible(current))
    BranchLive.catalogReadOnly = (if (BranchLive.book != null) BranchDesk.refreshCatalog(context) else BranchDesk.catalogReadOnly(context)).isNotEmpty()
    when {
      p == null -> if (BranchLive.off && !BranchKey.isMain(current)) go(BranchLive.main)
      p is BranchBook.Pick.Stay -> BranchDesk.markPicked(context)
      p is BranchBook.Pick.Use -> go(p.branch)
      else -> Unit
    }
  }

  //  ورود/خروج و دکانِ تازه بی بازسازیِ این جزء هم دیده شود؛ پرچمِ دکان هر ۱۰ دقیقه دوباره (خاموش ⇒ میز به اصلی)
  ir.vil3ntec.tohid.ui.v3.V3LiveEffect(tick) {
    var seen: String? = null
    var at = 0L
    while (true) {
      val owner = if (Backend.isReady(context)) BranchDesk.ownerOf(context) else ""
      val now = System.nanoTime() / 1_000_000
      if (owner != seen || (owner.isNotEmpty() && now - at > REPROBE_MS)) {
        seen = owner
        at = now
        if (owner.isEmpty()) BranchLive.reset() else probe(owner)
      }
      kotlinx.coroutines.delay(3_000)
    }
  }

  val pick = BranchLive.pick
  if (pick is BranchBook.Pick.Ask || pick is BranchBook.Pick.None) {
    val current = BranchLive.current
    val canClose = BranchLive.dismissible(current)
    V3Sheet(onDismiss = { if (canClose) { BranchDesk.markPicked(context); BranchLive.pick = null } }, title = StrBranchDesk.pickTitle, dismissible = canClose) {
      if (pick is BranchBook.Pick.Ask) {
        Text(StrBranchDesk.pickNote, style = V3Type.footnote, color = PhoneV3.colors.inkMuted, modifier = Modifier.padding(bottom = 8.dp))
        V3Card(padding = PaddingValues(vertical = 4.dp)) {
          pick.choices.forEachIndexed { i, b ->
            if (i > 0) V3Divider()
            V3ListRow(BranchLive.label(b), icon = Icons.Outlined.Store, value = if (b.id == current) StrBranchDesk.currentLabel else null, onClick = { scope.launch { go(b) } })
          }
        }
      } else {
        V3EmptyState(Icons.Outlined.Store, StrBranchDesk.none, action = StrBranchDesk.retry, onAction = { tick++ })
      }
      note?.let { Text(it, style = V3Type.footnote, color = PhoneV3.colors.inkMuted, modifier = Modifier.padding(top = 8.dp)) }
    }
  } else {
    val toasts = LocalV3Toasts.current
    LaunchedEffect(note) { note?.let { toasts?.show(it); note = null } }
  }
}

/** ردیفِ «شعبه» در «حسابِ من» — فقط با شعبه‌های روشن */
@Composable
fun BranchProfileCard(onOpen: (String) -> Unit) {
  if (!BranchLive.visible) return
  Spacer(Modifier.height(16.dp))
  V3Card(radius = 24.dp, padding = PaddingValues(vertical = 4.dp)) {
    V3ListRow(
      title = StrBranchDesk.title,
      subtitle = StrBranchDesk.current(BranchLive.nameOf(BranchLive.current)),
      icon = Icons.Outlined.Store,
      tile = false,
      minHeight = 60.dp,
      onClick = { onOpen(BranchPaths.ROOT) },
    )
  }
}

@Composable
internal fun RouteScope.BranchesPage() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val api = if (Backend.isReady(context)) Backend.api(context) else null
  val state = remember(api) { api?.let { BranchesPageState(ShopBranchesRepository(it)) } }
  var tick by remember { mutableStateOf(0) }
  if (state != null) LaunchedEffect(state, tick) { state.load() }
  var newName by remember { mutableStateOf("") }
  var renaming by remember { mutableStateOf<BranchBook.Branch?>(null) }
  var renameTo by remember { mutableStateOf("") }
  V3Scaffold(title = StrBranchDesk.title, onBack = backOrNull, bottomReserve = bottomReserve) { pad ->
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding() + 24.dp)) {
      val book = state?.book
      when {
        state == null || state.off -> V3EmptyState(Icons.Outlined.Store, StrBranchDesk.off)
        book == null && state.failed -> V3EmptyState(Icons.Outlined.CloudOff, StrBranchDesk.failed, action = StrBranchDesk.retry, onAction = { tick++ })
        book == null -> Text(StrBranchDesk.loading, style = V3Type.callout, color = PhoneV3.colors.inkMuted, modifier = Modifier.padding(top = 16.dp))
        else -> {
          val current = BranchLive.current
          V3Section(StrBranchDesk.currentLabel, footer = StrBranchDesk.pickNote) {
            V3ListRow(ir.vil3ntec.tohid.core.i18n.branchName(book.nameOf(current).orEmpty(), BranchKey.isMain(current)), icon = Icons.Outlined.Store, chevron = false)
            if (book.choices.size > 1) {
              V3Divider()
              V3ListRow(StrBranchDesk.switch, onClick = { BranchLive.pick = BranchBook.Pick.Ask(book.choices) })
            }
          }
          V3Section(null) { V3ListRow(StrBranchDesk.report, onClick = { nav.open(BranchPaths.REPORT) }) }
          if (book.owner) OwnerParts(state, book, newName, { newName = it }, onCreate = {
            scope.launch { if (state.create(newName)) newName = "" }
          }, onRename = { renaming = it; renameTo = it.name })
          state.message?.let { Text(it, style = V3Type.footnote, color = PhoneV3.colors.inkMuted, modifier = Modifier.padding(top = 12.dp)) }
        }
      }
    }
  }
  renaming?.let { b ->
    V3Sheet(onDismiss = { renaming = null }, title = StrBranchDesk.rename) {
      V3TextField(renameTo, { renameTo = it.take(60) }, StrBranchDesk.nameLabel, Modifier.fillMaxWidth())
      Spacer(Modifier.height(12.dp))
      V3Button(StrBranchDesk.save, { scope.launch { if (state?.rename(b.id, renameTo) == true) renaming = null } }, Modifier.fillMaxWidth(), enabled = renameTo.isNotBlank() && state?.busy != true)
    }
  }
}

@Composable
private fun OwnerParts(
  state: BranchesPageState,
  book: BranchBook.Book,
  newName: String,
  onName: (String) -> Unit,
  onCreate: () -> Unit,
  onRename: (BranchBook.Branch) -> Unit,
) {
  val scope = rememberCoroutineScope()
  V3Section(StrBranchDesk.title, footer = StrBranchDesk.archiveNote) {
    book.branches.forEachIndexed { i, b ->
      if (i > 0) V3Divider()
      V3ListRow(
        BranchLive.label(b),
        subtitle = if (b.archived) StrBranchDesk.archived else null,
        icon = Icons.Outlined.Store,
        chevron = false,
        trailing = {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            V3Button(StrBranchDesk.rename, { onRename(b) }, kind = V3ButtonKind.SECONDARY, height = 36.dp)
            if (!b.main) {
              V3Button(
                if (b.archived) StrBranchDesk.restore else StrBranchDesk.archive,
                { scope.launch { state.setArchived(b.id, !b.archived) } },
                kind = V3ButtonKind.SECONDARY, height = 36.dp, enabled = !state.busy,
              )
            }
          }
        },
      )
    }
  }
  V3Section(StrBranchDesk.newBranch) {
    V3TextField(newName, { onName(it.take(60)) }, StrBranchDesk.nameLabel, Modifier.fillMaxWidth().padding(horizontal = 12.dp))
    V3Button(StrBranchDesk.create, onCreate, Modifier.fillMaxWidth().padding(12.dp), enabled = newName.isNotBlank() && !state.busy)
  }
  val active = book.branches.filter { !it.archived }
  if (state.members.isNotEmpty()) {
    V3Section(StrBranchDesk.members, footer = StrBranchDesk.memberNote) {
      state.members.forEachIndexed { i, m ->
        if (i > 0) V3Divider()
        Column(Modifier.padding(vertical = 8.dp)) {
          Text(m.name.ifBlank { m.userId }, style = V3Type.callout, color = PhoneV3.colors.ink, modifier = Modifier.padding(horizontal = 12.dp))
          V3ChipRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
            active.forEach { b ->
              val on = b.id in m.branches.ifEmpty { listOf(BranchKey.MAIN) }
              V3Chip(BranchLive.label(b), on, { scope.launch { state.setMember(m.id, state.toggled(m, b.id)) } })
            }
          }
        }
      }
    }
  }
}

@Composable
internal fun RouteScope.BranchReportPage() {
  val context = LocalContext.current
  val api = if (Backend.isReady(context)) Backend.api(context) else null
  val state = remember(api) { api?.let { BranchReportState(ShopBranchesRepository(it)) } }
  var tick by remember { mutableStateOf(0) }
  if (state != null) LaunchedEffect(state, state.days, tick) { state.load(todayIso()) }
  V3Scaffold(title = StrBranchDesk.report, onBack = backOrNull, bottomReserve = bottomReserve) { pad ->
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding() + 24.dp)) {
      V3ChipRow(contentPadding = PaddingValues(0.dp)) {
        BranchTally.SPANS.forEach { d -> V3Chip(StrBranches.span(d), state?.days == d, { state?.days = d }) }
      }
      Text(StrBranchDesk.reportNote, style = V3Type.footnote, color = PhoneV3.colors.inkMuted, modifier = Modifier.padding(top = 8.dp))
      val v = state?.view
      when {
        state == null || state.off -> V3EmptyState(Icons.Outlined.Store, StrBranchDesk.off)
        v == null && state.failed -> V3EmptyState(Icons.Outlined.CloudOff, StrBranchDesk.failed, action = StrBranchDesk.retry, onAction = { tick++ })
        v == null -> Text(StrBranchDesk.loading, style = V3Type.callout, color = PhoneV3.colors.inkMuted, modifier = Modifier.padding(top = 16.dp))
        else -> {
          if (state.failed) Text(StrBranchDesk.failed, style = V3Type.footnote, color = PhoneV3.colors.inkMuted, modifier = Modifier.padding(top = 8.dp))
          v.totals?.let { SummarySection(StrBranchDesk.allTogether, it, StrBranches.totalsNote) }
          v.rows.forEach { r -> SummarySection(ir.vil3ntec.tohid.core.i18n.branchName(r.name, r.main) + if (r.archived) " · " + StrBranchDesk.archived else "", r.summary, null) }
        }
      }
    }
  }
}

private fun shown(key: String, v: Long?): String = when {
  v == null -> "—"
  key in BranchesReport.COUNT_KEYS -> v.toString()
  else -> ir.vil3ntec.tohid.ui.v3.analytics.afn(PayFlow.major(v))
}

private fun label(key: String): String = when (key) {
  "invoices" -> StrBranches.invoices
  "sales" -> StrBranches.sales
  "discount" -> StrBranches.discount
  "returnCount" -> StrBranches.returnCount
  "returns" -> StrBranches.returns
  "netSales" -> StrBranches.netSales
  "cashNet" -> StrBranches.cashNet
  else -> StrBranches.otherNet
}

@Composable
private fun SummarySection(title: String, s: BranchesReport.Summary, footer: String?) {
  V3Section(title, footer = footer) {
    if (s.empty) {
      V3ListRow(StrBranches.noneInRange, chevron = false)
    } else {
      BranchesReport.SUM_KEYS.forEachIndexed { i, k ->
        if (i > 0) V3Divider()
        V3ListRow(label(k), value = shown(k, s[k]), chevron = false)
      }
    }
    if (s.partial) {
      V3Divider()
      V3ListRow(StrBranches.partial, chevron = false, danger = true)
    }
  }
}

/**
 *  فازِ ۳: در شعبه‌ای که کاتالوگش فقط‌خواندنی است (`branch.catalog_read_only` ِ hello) صفحه‌های ساختن/ویرایشِ کالا و مدیریتِ دسته
 *  به‌جای فرم پیامِ روشن می‌دهند؛ هر نوشتنِ دیگری را هم سدِ `ShopStore` (`BranchDesk.catalogRefused`) می‌بندد. پرچمِ خاموش،
 *  شعبهٔ اصلی یا سرورِ پیش از فازِ ۳ ⇒ همان [content].
 */
@Composable
fun RouteScope.BranchCatalogGate(content: @Composable () -> Unit) {
  if (!BranchLive.catalogReadOnly) return content()
  V3Scaffold(title = StrBranchDesk.catalogLocked, onBack = backOrNull, largeTitle = false) { pad ->
    Column(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) {
      V3EmptyState(Icons.Outlined.Store, StrBranchDesk.catalogLocked, body = StrBranchDesk.refusalCatalogMainOnly)
    }
  }
}
