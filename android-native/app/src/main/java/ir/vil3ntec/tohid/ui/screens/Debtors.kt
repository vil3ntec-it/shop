package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.ui.v3.money.DebtorLedgerModel

import ir.vil3ntec.tohid.data.invoiceLabel
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Print
import ir.vil3ntec.tohid.data.ReportCsv
import ir.vil3ntec.tohid.print.ReportPrint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ir.vil3ntec.tohid.data.DebtBook
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.daysText
import ir.vil3ntec.tohid.formatMillis
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.spanText
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.pressMenu
import ir.vil3ntec.tohid.ui.components.common.ScreenKind
import ir.vil3ntec.tohid.ui.components.common.ViewMode
import ir.vil3ntec.tohid.ui.components.common.ViewModeToggle
import ir.vil3ntec.tohid.ui.components.common.rememberViewMode
import ir.vil3ntec.tohid.ui.theme.PhoneV3Debt
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/**
 *  قرض‌داران.
 *
 *  مانده هیچ‌جا ذخیره نمی‌شود: همیشه «آنچه گرفته منهای آنچه پس داده».
 *  فروشِ نسیه هم از همین راه می‌آید، پس حسابِ یک نفر چه از صندوق پر شود
 *  چه با دست، یک عدد است.
 */
@Composable
fun DebtorsScreen(
  store: ShopStore,
  d: ShopData,
  snackbar: SnackbarHostState,
  /**
   *  سربرگِ سبکِ خودِ صفحه (عنوان + برگشت) — برای پوستهٔ گوشیِ v3 که نوارِ بالا
   *  ندارد. پیش‌فرض خاموش: پوستهٔ امروز (`TohidTopBar`) عنوان را خودش دارد.
   */
  showHeader: Boolean = false,
  /** باز شده از «بیشتر» ⇒ دکمهٔ برگشتِ سربرگ؛ `null` ⇒ بی دکمه */
  onBack: (() -> Unit)? = null,
) {
  val scope = rememberCoroutineScope()

  var search by rememberSaveable { mutableStateOf("") }
  //  ترتیبِ فهرست بین باز و بسته شدنِ صفحه می‌ماند: کسی که «قدیمی‌ترین
  //  قرض» را انتخاب کرده، هر بار از نو انتخابش نمی‌کند
  var sortName by rememberSaveable { mutableStateOf(DebtorListModel.Sort.MOST.name) }
  val sort = DebtorListModel.Sort.parse(sortName)
  var openId by rememberSaveable { mutableStateOf<String?>(null) }
  var form by remember { mutableStateOf<DebtorFormState?>(null) }
  var txFor by remember { mutableStateOf<Pair<String, DebtorEngine.Kind>?>(null) }
  var confirmDelete by remember { mutableStateOf<Debtor?>(null) }
  //  «حذف با برگرداندن»: ردیف/قرض‌دار همین حالا پنهان می‌شود و دفتر فقط وقتی عوض
  //  می‌شود که «برگرداندن» زده نشود (`DebtorLedgerModel.PendingDelete`)
  var pendingDeletes by remember { mutableStateOf(listOf<DebtorLedgerModel.PendingDelete>()) }
  //  جای اسکرولِ فهرست با رفتن به حسابِ یک نفر و برگشتن می‌ماند
  val gridState = rememberLazyGridState()
  //  دفترِ قرض (v3 §22.2-A5): رسیدِ یک ردیف، سررسیدِ یک برد، و بردی که از سقف می‌گذرد
  var receiptFor by remember { mutableStateOf<String?>(null) }
  var dueFor by remember { mutableStateOf<String?>(null) }
  var overLimit by remember { mutableStateOf<PendingTx?>(null) }

  fun toast(text: String) {
    scope.launch { snackbar.showSnackbar(text) }
  }

  fun apply(result: DebtorEngine.Result, done: String, after: () -> Unit = {}) {
    when (result) {
      is DebtorEngine.Result.Failed -> toast(result.message)
      is DebtorEngine.Result.Ok -> {
        scope.launch { store.save(result.data) }
        toast(done)
        after()
      }
    }
  }

  val latestData = rememberUpdatedState(d)
  fun deleteResult(data: ShopData, p: DebtorLedgerModel.PendingDelete): DebtorEngine.Result = when (p) {
    is DebtorLedgerModel.PendingDelete.Transaction -> DebtorEngine.deleteTransaction(data, p.txId)
    is DebtorLedgerModel.PendingDelete.Debtor -> DebtorEngine.delete(data, p.debtorId)
  }
  fun commit(p: DebtorLedgerModel.PendingDelete) {
    pendingDeletes = pendingDeletes - p
    when (val r = deleteResult(latestData.value, p)) {
      is DebtorEngine.Result.Failed -> toast(r.message)
      is DebtorEngine.Result.Ok -> scope.launch { store.save(r.data) }
    }
  }
  /** پنهان کن، پیامِ «برگرداندن» بده؛ بی‌پاسخ ⇒ حذف، «برگرداندن» ⇒ هیچ چیز پاک نشده است */
  fun deleteLater(p: DebtorLedgerModel.PendingDelete, message: String) {
    pendingDeletes = pendingDeletes + p
    scope.launch {
      val answer = snackbar.showSnackbar(message, actionLabel = StrDebtor.undo, duration = SnackbarDuration.Short)
      if (answer == SnackbarResult.ActionPerformed) pendingDeletes = pendingDeletes - p else commit(p)
    }
  }
  //  صفحه پیش از بسته شدنِ پیام رفت ⇒ حذف‌های در انتظار همین حالا انجام می‌شوند
  val pendingNow = rememberUpdatedState(pendingDeletes)
  DisposableEffect(Unit) {
    onDispose {
      var data = latestData.value
      var changed = false
      pendingNow.value.forEach { p ->
        (deleteResult(data, p) as? DebtorEngine.Result.Ok)?.let { data = it.data; changed = true }
      }
      if (changed) {
        val final = data
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        kotlinx.coroutines.GlobalScope.launch { store.save(final) }
      }
    }
  }
  val hiddenTx = DebtorLedgerModel.hiddenTx(pendingDeletes)
  val hiddenDebtors = DebtorLedgerModel.hiddenDebtors(pendingDeletes)

  //  پیش‌نمایشِ گوشی (`DevShot`): حسابِ یک نفر و برگهٔ ثبت
  val shotOpen = DebtorDetailShot.open.value
  LaunchedEffect(shotOpen) { if (shotOpen != null) openId = shotOpen }
  val shotComposer = DebtorDetailShot.composer.value
  LaunchedEffect(shotComposer, openId) { if (shotComposer) openId?.let { txFor = it to DebtorEngine.Kind.GIVE } }

  // قرض‌داری که حذف شده نباید صفحه‌اش باز بماند
  LaunchedEffect(d.debtors.size) {
    if (openId != null && d.debtors.none { it.id == openId }) openId = null
  }
  BackHandler(enabled = openId != null) { openId = null }

  val shownId = openId?.takeIf { it !in hiddenDebtors && d.debtors.any { x -> x.id == it } }

  //  فهرست ⇄ حساب: رفتن از لبهٔ پایان می‌آید، برگشتن برعکس (`V3Pages`)
  V3Pages(target = shownId, depth = { if (it == null) 0 else 1 }) { pageId ->
  val account = pageId?.let { DebtorEngine.account(d, it) }
  if (account != null) {
    DebtorDetailScreen(
      d = d,
      account = account,
      hiddenTx = hiddenTx,
      onBack = { openId = null },
      onCompose = { kind -> txFor = account.debtor.id to kind },
      onEdit = { form = DebtorFormState.of(account.debtor) },
      onDelete = { confirmDelete = account.debtor },
      onDeleteTx = { deleteLater(DebtorLedgerModel.PendingDelete.Transaction(it), StrDebtor.txDeleted) },
      onOpenTx = { receiptFor = it },
      onDue = { dueFor = it },
    )
  } else {
    /*
     *  ردیف‌ها یک بار حساب می‌شوند، نه با هر کشیدنِ فهرست.
     *
     *  مدتِ قرضِ هر نفر از همهٔ تراکنش‌هایش حساب می‌شود؛ در دکانی با صد
     *  قرض‌دار و هزار تراکنش، انجامِ این کار در هر بار کشیده شدنِ صفحه
     *  اسکرول را کند می‌کرد. جست‌وجو، ترتیب و «چند روز» از `DebtorListModel`.
     */
    val term = search.trim()
    val allRows = remember(d, term, sort) { DebtorListModel.rows(d, term, sort, System.currentTimeMillis()) }
    val rows = if (hiddenDebtors.isEmpty()) allRows else allRows.filterNot { it.debtor.id in hiddenDebtors }
    //  سه عددِ بالا از همهٔ دفتر — جست‌وجو «جمع تمام قرض‌ها» را عوض نمی‌کند
    val summary = remember(d) { DebtorListModel.summary(d) }

    //  نمای کارتی/جدولی (VISUAL-DIRECTION §۱٫۵): انتخابِ همین کاربر روی همین دستگاه؛
    //  گوشی همیشه کارتی (پیش‌نمایشِ گوشی روی کامپیوتر هم). جست‌وجو، ترتیب و
    //  قرض‌دارِ بازشده با عوض‌شدنِ نما می‌مانند.
    val view = rememberViewMode("debtors", ScreenKind.MANAGEMENT)
    val phone = screenSize() == Screen.PHONE
    val wideTable = !phone && view.mode == ViewMode.TABLE
    val motion = Motion.enabled

    //  شکلِ r9 با رنگ‌های VILL3N (`PhoneDebtors.kt`): کارتِ سه‌عددی، جستجو با
    //  میکروفون، تراشه‌های ترتیب و شبکهٔ کارت‌های قرمز/سبز
    PhoneDebtorsTheme {
      DebtorsBackdrop {
       Column(Modifier.fillMaxSize()) {
        if (showHeader) DebtorsHeader(onBack)
        // شبکهٔ کارت‌ها — همان `.debtor-list` نسخهٔ وب. قرض‌دار در یک نگاه
        // از رنگِ کارتش شناخته می‌شود، نه از خواندنِ عدد.
        //  پایین ۸۸dp: آخرین ردیف از زیرِ دکمهٔ گردِ «+» (۱۶ + ۵۶ + فاصله) بیرون می‌آید
        LazyVerticalGrid(
          columns = GridCells.Adaptive(minSize = gridMinSize(phone = PhoneV3Debt.tileMin, tablet = PhoneV3Debt.tileMinTablet)),
          modifier = Modifier.fillMaxWidth().weight(1f),
          state = gridState,
          contentPadding = PaddingValues(PhoneV3Debt.gutter, 8.dp, PhoneV3Debt.gutter, 88.dp),
          horizontalArrangement = Arrangement.spacedBy(PhoneV3Debt.gap),
          verticalArrangement = Arrangement.spacedBy(PhoneV3Debt.gap),
        ) {
          item(key = "debtors-head", span = { GridItemSpan(maxLineSpan) }) {
            // این `Column` لازم است، تزئینی نیست: خانهٔ یک شبکه هرچه داخلش
            // باشد را **روی هم** می‌گذارد، نه زیرِ هم.
            Column {
              /*
               *  سه عددِ هم‌وزن: چند نفر، جمعِ قرض، و چقدر وصول شده — همان
               *  سه چیزی که دکاندار می‌خواهد بداند.
               */
              DebtorsStatsCard(summary)
              Spacer(Modifier.height(12.dp))

              DebtorsSearchField(value = search, onValueChange = { search = it })
              Spacer(Modifier.height(10.dp))

              /*
               *  ترتیبِ فهرست، در دسترسِ همان صفحه.
               *
               *  «کی بیشتر برده» و «قرضِ کی مانده‌تر است» دو سؤالِ جدا
               *  هستند؛ در دکان دومی مهم‌تر است: قرضِ کوچکی که شش ماه
               *  مانده، از قرضِ بزرگِ دیروز بدتر است.
               */
              if (!phone && view.toggleVisible) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { ViewModeToggle(view) }
                Spacer(Modifier.height(6.dp))
              }
              //  دفترِ خالی چیزی برای مرتب کردن ندارد؛ جست‌وجوی بی‌نتیجه «۰ مورد» را نشان می‌دهد
              if (d.debtors.isNotEmpty()) {
                DebtorsSortRow(current = sort, count = rows.size) { sortName = it.name }
                Spacer(Modifier.height(4.dp))
              }
            }
          }

          if (rows.isEmpty()) {
            item(key = "debtors-empty", span = { GridItemSpan(maxLineSpan) }) {
              if (d.debtors.isEmpty()) {
                DebtorsEmptyCard(
                  icon = Icons.Filled.Groups,
                  title = "هنوز قرض‌داری ثبت نشده",
                  description = "کسانی که از شما نسیه می‌برند را اینجا اضافه کنید تا حساب هرکدام جدا بماند.",
                  actionText = "قرض‌دار تازه",
                  onAction = { form = DebtorFormState() },
                )
              } else {
                DebtorsEmptyCard(
                  icon = Icons.Filled.Search,
                  title = "چیزی پیدا نشد",
                  description = "قرض‌داری با این نام یا شماره نیست.",
                )
              }
            }
          } else if (wideTable) {
            //  تبلت/کامپیوتر: جدول — نام، شماره، مانده، مدت؛ ترتیب از همان تراشه‌های بالا
            item(key = "debtor-head", span = { GridItemSpan(maxLineSpan) }) { DebtorTableHead() }
            items(rows, key = { "t-" + it.debtor.id }, span = { GridItemSpan(maxLineSpan) }) { row ->
              DebtorTableRow(
                row = row,
                onOpen = { openId = row.debtor.id },
                onReceive = { txFor = row.debtor.id to DebtorEngine.Kind.RECEIVE },
                onGive = { txFor = row.debtor.id to DebtorEngine.Kind.GIVE },
                onEdit = { form = DebtorFormState.of(row.debtor) },
                onDelete = { confirmDelete = row.debtor },
              )
            }
          } else {
            itemsIndexed(rows, key = { _, row -> row.debtor.id }) { index, row ->
              //  ورودِ پله‌پله در نخستین نمایش؛ با عوضِ ترتیب، هر کارت به جای
              //  تازه‌اش سُر می‌خورد (`animateItem`). «حذفِ انیمیشن‌ها» ⇐ هیچ‌کدام
              Box(if (motion) Modifier.animateItem() else Modifier) {
                StaggeredItem(index) {
                  DebtorTile(
                    row = row,
                    onOpen = { openId = row.debtor.id },
                    onEdit = { form = DebtorFormState.of(row.debtor) },
                    onDelete = { confirmDelete = row.debtor },
                  )
                }
              }
            }
          }
        }
       }

        //  ۸dp + ۸dpِ داخلِ دکمه (جای سایه) = همان ۱۶dp از لبه. دفترِ خالی دکمهٔ
        //  «قرض‌دار تازه» ِ خودش را دارد، پس دایره آن‌جا کشیده نمی‌شود
        DebtorsAddButton(
          onClick = { form = DebtorFormState() },
          modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).popIn(),
          visible = d.debtors.isNotEmpty(),
        )
      }
    }
  }
  }

  /* ---------------------------- پنجره‌ها ---------------------------- */

  form?.let { state ->
    DebtorFormSheet(
      state = state,
      onDismiss = { form = null },
      onSave = { draft ->
        val result = if (state.editingId == null) {
          DebtorEngine.add(d, draft, System.currentTimeMillis(), ::newId)
        } else {
          DebtorEngine.editWithLimit(d, state.editingId, draft)
        }
        apply(result, if (state.editingId == null) "با موفقیت ثبت شد" else "با موفقیت ویرایش شد") {
          form = null
        }
      },
    )
  }

  txFor?.let { (debtorId, kind) ->
    TxComposerSheet(
      debtor = d.debtors.find { it.id == debtorId },
      balance = ShopStore.debt(d, debtorId),
      kind = kind,
      onDismiss = { txFor = null; DebtorDetailShot.composer.value = false },
      onSave = { chosenKind, amount, date, notes, due ->
        val pending = PendingTx(debtorId, chosenKind, amount, date, notes, due)
        //  CRM-03: بردِ بیش از سقف فقط با تأییدِ صریح — مشتریِ بی سقف همان رفتارِ همیشه
        val credit = if (chosenKind == DebtorEngine.Kind.GIVE && !amount.isNaN() && amount > 0) {
          DebtBook.creditCheck(d, debtorId, DebtBook.minor(amount))
        } else null
        if (credit != null && credit.over) overLimit = pending
        else apply(pending.submit(d, overLimit = false), "با موفقیت ثبت شد") { txFor = null }
      },
    )
  }

  overLimit?.let { pending ->
    val credit = DebtBook.creditCheck(d, pending.debtorId, DebtBook.minor(pending.amount))
    if (credit == null) {
      //  سقف در همین فاصله برداشته شد ⇒ پرسشی نمانده
      LaunchedEffect(pending) { overLimit = null }
    } else {
      V3MaterialColors { OverLimitDialog(
        credit = credit,
        onConfirm = {
          apply(pending.submit(d, overLimit = true), "با موفقیت ثبت شد") { txFor = null }
          overLimit = null
        },
        onDismiss = { overLimit = null },
      ) }
    }
  }

  receiptFor?.let { txId ->
    val tx = d.transactions.find { it.id == txId }
    if (tx == null) {
      LaunchedEffect(txId) { receiptFor = null }
    } else {
      V3MaterialColors { DebtReceiptDialog(
        d = d,
        debtorId = tx.debtorId,
        txId = txId,
        onDismiss = { receiptFor = null },
        onDue = if (tx.type == "give") ({ dueFor = txId; receiptFor = null }) else null,
      ) }
    }
  }

  dueFor?.let { txId ->
    val tx = d.transactions.find { it.id == txId }
    val entry = tx?.let { DebtBook.book(d, it.debtorId, todayIso())?.entry(txId) }
    if (entry == null) {
      LaunchedEffect(txId) { dueFor = null }
    } else {
      V3MaterialColors { DueDateDialog(
        entry = entry,
        onDismiss = { dueFor = null },
        onSave = { due -> apply(DebtorEngine.setDueDate(d, txId, due), "سررسید ثبت شد") { dueFor = null } },
      ) }
    }
  }

  confirmDelete?.let { debtor ->
    DeleteDebtorSheet(
      warning = DebtorEngine.deleteWarning(d, debtor.id),
      onDismiss = { confirmDelete = null },
      onConfirm = {
        confirmDelete = null
        openId = null
        deleteLater(DebtorLedgerModel.PendingDelete.Debtor(debtor.id), StrDebtor.debtorDeleted(debtor.name))
      },
    )
  }
}

/* ============================ تکه‌ها ============================ */

@Composable
private fun DebtorTableHead() {
  Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Shop.colors.surface2).padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    listOf("نام" to 0f, "شماره" to 160f, "مانده" to 150f, "مدت" to 110f, "وضعیت" to 110f).forEach { (t, w) ->
      Text(t, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Shop.colors.muted,
        modifier = if (w == 0f) Modifier.weight(1f) else Modifier.width(w.dp))
    }
  }
}

/** یک ردیفِ جدولِ قرض‌داران — زدن ⇐ حساب؛ کلیکِ راست ⇐ رسید، قرضِ تازه، ویرایش، حذف */
@Composable
private fun DebtorTableRow(
  row: DebtorListModel.Row,
  onOpen: () -> Unit,
  onReceive: () -> Unit,
  onGive: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  var menu by remember { mutableStateOf(false) }
  val owes = row.balance > 0
  val tint = if (owes) Shop.colors.danger else Shop.colors.success
  Box {
    Row(
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(Shop.colors.surface)
        .pressMenu(onClick = onOpen, onMenu = { menu = true })
        .padding(horizontal = 12.dp, vertical = 9.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(row.debtor.name.ifBlank { "بی‌نام" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
        color = Shop.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
      Text(ir.vil3ntec.tohid.LocaleText.phoneDisplay(row.debtor.phone).ifBlank { "—" }, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted,
        maxLines = 1, modifier = Modifier.width(160.dp))
      Text(money(kotlin.math.abs(row.balance)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
        color = tint, maxLines = 1, modifier = Modifier.width(150.dp))
      Text(row.days?.let { daysText(it) } ?: "—", style = MaterialTheme.typography.bodySmall,
        color = if ((row.days ?: 0) >= 30) Shop.colors.danger else Shop.colors.muted, maxLines = 1, modifier = Modifier.width(110.dp))
      Text(DebtorEngine.stateText(row.balance), style = MaterialTheme.typography.labelSmall, color = tint,
        maxLines = 1, modifier = Modifier.width(110.dp))
    }
    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
      DropdownMenuItem(text = { Text("باز کردنِ حساب") }, onClick = { menu = false; onOpen() })
      DropdownMenuItem(text = { Text("ثبتِ رسید (پول گرفتم)") }, onClick = { menu = false; onReceive() })
      DropdownMenuItem(text = { Text("قرضِ تازه") }, onClick = { menu = false; onGive() })
      DropdownMenuItem(text = { Text("ویرایش") }, onClick = { menu = false; onEdit() })
      DropdownMenuItem(text = { Text("حذف", color = Shop.colors.danger) }, onClick = { menu = false; onDelete() })
    }
  }
}

/* ============================ فرم‌ها ============================ */

data class DebtorFormState(
  val editingId: String? = null,
  val name: String = "",
  val phone: String = "",
  val notes: String = "",
  /** سقفِ اعتبار (CRM-03) — خالی یعنی بی سقف */
  val creditLimit: String = "",
) {
  companion object {
    fun of(d: Debtor) = DebtorFormState(d.id, d.name, d.phone, d.notes, limitText(d.creditLimit))
  }
}

/** بردی که منتظرِ تأییدِ «بیش از سقف» است */
private data class PendingTx(
  val debtorId: String,
  val kind: DebtorEngine.Kind,
  val amount: Double,
  val date: String,
  val notes: String,
  val due: String?,
) {
  fun submit(d: ShopData, overLimit: Boolean) = DebtorEngine.addTransaction(
    d, debtorId, kind, amount, date, notes, todayIso(), System.currentTimeMillis(), ::newId,
    dueDate = due, overLimit = overLimit,
  )
}
