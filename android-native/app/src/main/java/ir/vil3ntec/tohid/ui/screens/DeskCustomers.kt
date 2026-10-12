package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.data.ReportCsv
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.Xlsx
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/**
 *  مشتریان و صورت‌حساب — کامپیوتر.
 *
 *  خواستهٔ صاحب مخزن: «حساب‌های قرض‌داران رو چه جوری درست کنم که دقیق
 *  باشه و فایل اکسل یا پرینت داره». مثلِ دفترِ معینِ حسابداری:
 *
 *  • چپ: همهٔ مشتری‌ها با مانده و «چند روز است»؛ ردیفِ «مشتریِ تازه»
 *    بالای جدول (نام، تلفن، Enter).
 *  • راست: **صورت‌حساب** — تاریخ، شرح، برد (بدهکار)، رسید (بستانکار)،
 *    **ماندهٔ پس از هر ردیف**. مانده همیشه از خودِ ردیف‌ها حساب می‌شود
 *    (`ShopStore.debt`)، هیچ عددی جدا ذخیره نمی‌شود ⇐ ناجور نمی‌شود.
 *  • **F7 دریافتِ وجه**، **F6 قرضِ دستی**، **Ctrl+P چاپ**، **Ctrl+E اکسل** —
 *    همان صورت‌حسابی که به مشتری داده می‌شود.
 *  • اشتباه پاک نمی‌شود؛ با ردیفِ اصلاحیِ مخالف درست می‌شود و ردش می‌ماند.
 *
 *  منطق همان `DebtorEngine` است که گوشی دارد.
 */
@Composable
fun DeskCustomersScreen(
  store: ShopStore,
  d: ShopData,
  snackbar: SnackbarHostState,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  fun toast(t: String) { scope.launch { snackbar.showSnackbar(t) } }

  var search by remember { mutableStateOf("") }
  var onlyDebt by remember { mutableStateOf(false) }
  var openId by remember { mutableStateOf<String?>(null) }
  var entry by remember { mutableStateOf<DebtorEngine.Kind?>(null) }

  data class CRow(val id: String, val name: String, val phone: String, val balance: Double, val days: Long?)
  val rows = remember(d, search, onlyDebt) {
    val q = search.trim()
    d.debtors
      .filter { q.isEmpty() || it.name.contains(q, true) || it.phone.contains(q) }
      .map { CRow(it.id, it.name, it.phone, ShopStore.debt(d, it.id), DebtorEngine.debtDays(d, it.id)) }
      .filter { !onlyDebt || it.balance > 0.5 }
      .sortedWith(compareByDescending<CRow> { it.balance }.thenBy { it.name })
  }
  val totalDebt = remember(d) { d.debtors.sumOf { ShopStore.debt(d, it.id).coerceAtLeast(0.0) } }
  if (openId == null && rows.isNotEmpty()) openId = rows.first().id

  val open = openId?.let { id -> d.debtors.find { it.id == id } }
  val today = ir.vil3ntec.tohid.todayIso()
  fun sheet() = open?.let { ReportCsv.statement(d, it.id, today) }
  fun print() { sheet()?.let { printSheet(context, it) } }
  fun excel() {
    val s = sheet() ?: return
    val path = Platform.saveFile("${s.name}.xlsx", Xlsx.of(s))
    if (path != null) toast("ذخیره شد: $path")
  }

  Row(
    Modifier
      .fillMaxSize()
      .onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown || open == null) return@onPreviewKeyEvent false
        when {
          e.key == Key.F7 -> { entry = DebtorEngine.Kind.RECEIVE; true }
          e.key == Key.F6 -> { entry = DebtorEngine.Kind.GIVE; true }
          e.isCtrlPressed && e.key == Key.P -> { print(); true }
          e.isCtrlPressed && e.key == Key.E -> { excel(); true }
          else -> false
        }
      }
      .padding(12.dp),
  ) {
    /* ─────────── فهرستِ مشتری‌ها ─────────── */
    Column(Modifier.width(420.dp).fillMaxHeight()) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
          value = search, onValueChange = { search = it }, singleLine = true,
          placeholder = { Text("جست‌وجوی نام یا تلفن") }, modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        FilterChip(selected = onlyDebt, onClick = { onlyDebt = !onlyDebt }, label = { Text("فقط بدهکار") })
      }
      Spacer(Modifier.height(6.dp))
      Text(
        "${d.debtors.size} مشتری · طلبِ کل ${money(totalDebt)} ؋",
        style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted,
      )
      Spacer(Modifier.height(6.dp))
      NewCustomerRow(store, d, ::toast) { openId = it }
      Spacer(Modifier.height(6.dp))
      HeadRow(listOf("نام" to 0.dp, "مانده" to 110.dp, "روز" to 50.dp))
      LazyColumn(Modifier.weight(1f).fillMaxWidth().border(1.dp, Shop.colors.border, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))) {
        itemsIndexed(rows, key = { _, r -> r.id }) { i, r ->
          Row(
            Modifier
              .fillMaxWidth()
              .background(if (r.id == openId) Shop.colors.primary.copy(alpha = 0.12f) else if (i % 2 == 1) Shop.colors.surface2.copy(alpha = 0.45f) else Shop.colors.surface)
              .clickable { openId = r.id }
              .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(Modifier.weight(1f)) {
              Text(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
              if (r.phone.isNotBlank()) Text(r.phone, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
            }
            Text(
              money(r.balance), Modifier.width(110.dp), textAlign = TextAlign.End,
              style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
              color = when { r.balance > 0.5 -> Shop.colors.danger; r.balance < -0.5 -> Shop.colors.success; else -> Shop.colors.muted },
            )
            Text(r.days?.toString() ?: "", Modifier.width(50.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall, color = if ((r.days ?: 0) > 60) Shop.colors.danger else Shop.colors.muted)
          }
        }
      }
    }

    Spacer(Modifier.width(12.dp))

    /* ─────────── صورت‌حساب ─────────── */
    Column(Modifier.weight(1f).fillMaxHeight()) {
      if (open == null) {
        Text("مشتری‌ای انتخاب نشده — در ردیفِ «مشتریِ تازه» نام را بنویسید و Enter بزنید.", color = Shop.colors.muted)
        return@Column
      }
      val balance = ShopStore.debt(d, open.id)
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(open.name, style = MaterialTheme.typography.titleLarge, color = Shop.colors.text)
          Text(listOf(open.phone, DebtorEngine.stateText(balance)).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
        }
        Column(horizontalAlignment = Alignment.End) {
          Text("مانده", style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
          Text("${money(balance)} ؋", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = if (balance > 0.5) Shop.colors.danger else Shop.colors.success)
        }
      }
      Spacer(Modifier.height(8.dp))
      Row {
        Button(onClick = { entry = DebtorEngine.Kind.RECEIVE }) { Text("دریافتِ وجه  F7") }
        Spacer(Modifier.width(6.dp))
        OutlinedButton(onClick = { entry = DebtorEngine.Kind.GIVE }) { Text("قرضِ دستی  F6") }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = ::print) { Text("چاپ  Ctrl+P") }
        Spacer(Modifier.width(6.dp))
        OutlinedButton(onClick = ::excel) { Text("اکسل  Ctrl+E") }
      }
      entry?.let { kind ->
        Spacer(Modifier.height(8.dp))
        key(open.id, kind) {
          EntryRow(kind, onCancel = { entry = null }) { amount, notes ->
            when (val r = DebtorEngine.addTransaction(d, open.id, kind, amount, today, notes, today, System.currentTimeMillis(), ::newId)) {
              is DebtorEngine.Result.Failed -> toast(r.message)
              is DebtorEngine.Result.Ok -> {
                scope.launch { store.save(r.data) }
                entry = null
                toast(if (kind == DebtorEngine.Kind.RECEIVE) "دریافتِ ${money(amount)} ؋ ثبت شد" else "قرضِ ${money(amount)} ؋ ثبت شد")
              }
            }
          }
        }
      }
      Spacer(Modifier.height(8.dp))

      //  ردیف‌ها به ترتیبِ تاریخ، ماندهٔ پس از هر ردیف — همان قاعدهٔ `ReportCsv.statement`
      val lines = remember(d, open.id) {
        var run = 0.0
        d.transactions.filter { it.debtorId == open.id }
          .sortedWith(compareBy<DebtTransaction> { it.date }.thenBy { it.createdAt })
          .map { tx -> run += if (tx.type == "give") tx.amount else -tx.amount; tx to run }
      }
      HeadRow(listOf("تاریخ" to 100.dp, "شرح" to 0.dp, "برد (بدهکار)" to 130.dp, "رسید (بستانکار)" to 130.dp, "مانده" to 130.dp))
      LazyColumn(Modifier.weight(1f).fillMaxWidth().border(1.dp, Shop.colors.border, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))) {
        if (lines.isEmpty()) item {
          Text("هنوز ردیفی نیست.", Modifier.fillMaxWidth().padding(24.dp), color = Shop.colors.muted, textAlign = TextAlign.Center)
        }
        itemsIndexed(lines, key = { _, l -> l.first.id }) { i, (tx, run) ->
          val give = tx.type == "give"
          Row(
            Modifier.fillMaxWidth().background(if (i % 2 == 1) Shop.colors.surface2.copy(alpha = 0.45f) else Shop.colors.surface).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(ir.vil3ntec.tohid.formatDate(tx.date), Modifier.width(100.dp), style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
            Text(tx.notes.ifBlank { if (give) "برد" else "رسید" }, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
            Text(if (give) money(tx.amount) else "", Modifier.width(130.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.danger)
            Text(if (!give) money(tx.amount) else "", Modifier.width(130.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.success)
            Text(money(run), Modifier.width(130.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Shop.colors.text)
          }
        }
      }
      val given = lines.filter { it.first.type == "give" }.sumOf { it.first.amount }
      val received = lines.filter { it.first.type != "give" }.sumOf { it.first.amount }
      Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
        Text("جمع", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = Shop.colors.text)
        Text(money(given), Modifier.width(130.dp), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, color = Shop.colors.danger)
        Text(money(received), Modifier.width(130.dp), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, color = Shop.colors.success)
        Text(money(given - received), Modifier.width(130.dp), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, color = Shop.colors.text)
      }
      Text(
        "اشتباه پاک نمی‌شود: با «دریافت» یا «قرضِ دستی»ِ مخالف و یادداشتِ «اصلاحِ ردیفِ …» درست کنید تا ردش بماند.",
        style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted,
      )
    }
  }
}

/** چاپِ همان برگه با چاپگرِ سیستم */
private fun printSheet(context: android.content.Context, sheet: ReportCsv.Sheet) =
  ir.vil3ntec.tohid.print.ReportPrint.print(context, sheet, sheet.name)

@Composable
internal fun HeadRow(cols: List<Pair<String, Dp>>) {
  Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).background(Shop.colors.surface2).padding(horizontal = 8.dp, vertical = 7.dp),
  ) {
    cols.forEachIndexed { i, (t, w) ->
      Text(
        t, if (w == 0.dp) Modifier.weight(1f) else Modifier.width(w),
        style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted,
        textAlign = if (i > 0 && w != 0.dp) TextAlign.End else TextAlign.Start,
      )
    }
  }
}

@Composable
private fun NewCustomerRow(store: ShopStore, d: ShopData, toast: (String) -> Unit, onAdded: (String) -> Unit) {
  val scope = rememberCoroutineScope()
  var name by remember { mutableStateOf("") }
  var phone by remember { mutableStateOf("") }
  fun save() {
    when (val r = DebtorEngine.add(d, DebtorEngine.DebtorDraft(name = name, phone = phone), System.currentTimeMillis(), ::newId)) {
      is DebtorEngine.Result.Failed -> toast(r.message)
      is DebtorEngine.Result.Ok -> { scope.launch { store.save(r.data) }; onAdded(r.id); name = ""; phone = "" }
    }
  }
  val enter = Modifier.onPreviewKeyEvent { e ->
    if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) { save(); true } else false
  }
  Row(verticalAlignment = Alignment.CenterVertically) {
    OutlinedTextField(name, { name = it }, label = { Text("مشتریِ تازه") }, singleLine = true, modifier = Modifier.weight(1f).then(enter))
    Spacer(Modifier.width(6.dp))
    OutlinedTextField(phone, { phone = it }, label = { Text("تلفن") }, singleLine = true, modifier = Modifier.width(140.dp).then(enter))
    Spacer(Modifier.width(6.dp))
    Button(onClick = ::save, enabled = name.isNotBlank()) { Text("↵") }
  }
}

/** ردیفِ ثبتِ دریافت/قرض — مبلغ، شرح، Enter */
@Composable
private fun EntryRow(kind: DebtorEngine.Kind, onCancel: () -> Unit, onSave: (Double, String) -> Unit) {
  var amount by remember { mutableStateOf("") }
  var notes by remember { mutableStateOf("") }
  val focus = remember { FocusRequester() }
  LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
  fun go() { amount.toDoubleOrNull()?.let { onSave(it, notes.trim()) } }
  val keys = Modifier.onPreviewKeyEvent { e ->
    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    when (e.key) {
      Key.Enter, Key.NumPadEnter -> { go(); true }
      Key.Escape -> { onCancel(); true }
      else -> false
    }
  }
  val receive = kind == DebtorEngine.Kind.RECEIVE
  Surface(
    color = (if (receive) Shop.colors.success else Shop.colors.danger).copy(alpha = 0.08f),
    shape = RoundedCornerShape(8.dp),
  ) {
    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(if (receive) "دریافتِ وجه" else "قرضِ دستی", style = MaterialTheme.typography.labelLarge, color = Shop.colors.text)
      Spacer(Modifier.width(8.dp))
      OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("مبلغ (؋)") }, singleLine = true, modifier = Modifier.width(160.dp).focusRequester(focus).then(keys))
      Spacer(Modifier.width(6.dp))
      OutlinedTextField(notes, { notes = it }, label = { Text("شرح") }, singleLine = true, modifier = Modifier.weight(1f).then(keys))
      Spacer(Modifier.width(6.dp))
      Button(onClick = ::go, enabled = (amount.toDoubleOrNull() ?: 0.0) > 0) { Text("ثبت ↵") }
      TextButton(onClick = onCancel) { Text("Esc") }
    }
  }
}
