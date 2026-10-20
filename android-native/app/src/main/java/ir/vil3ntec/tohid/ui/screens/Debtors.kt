package ir.vil3ntec.tohid.ui.screens

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
  var confirmTx by remember { mutableStateOf<String?>(null) }
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

  // قرض‌داری که حذف شده نباید صفحه‌اش باز بماند
  LaunchedEffect(d.debtors.size) {
    if (openId != null && d.debtors.none { it.id == openId }) openId = null
  }
  BackHandler(enabled = openId != null) { openId = null }

  val account = openId?.let { DebtorEngine.account(d, it) }

  if (account != null) {
    AccountView(
      d = d,
      account = account,
      onBack = { openId = null },
      onNewTx = { txFor = account.debtor.id to DebtorEngine.Kind.GIVE },
      onEdit = { form = DebtorFormState.of(account.debtor) },
      onDelete = { confirmDelete = account.debtor },
      onDeleteTx = { confirmTx = it },
      onOpenTx = { receiptFor = it },
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
    val rows = remember(d, term, sort) { DebtorListModel.rows(d, term, sort, System.currentTimeMillis()) }
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

  /* ---------------------------- پنجره‌ها ---------------------------- */

  form?.let { state ->
    DebtorDialog(
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
    TransactionDialog(
      debtor = d.debtors.find { it.id == debtorId },
      balance = ShopStore.debt(d, debtorId),
      kind = kind,
      onDismiss = { txFor = null },
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
      OverLimitDialog(
        credit = credit,
        onConfirm = {
          apply(pending.submit(d, overLimit = true), "با موفقیت ثبت شد") { txFor = null }
          overLimit = null
        },
        onDismiss = { overLimit = null },
      )
    }
  }

  receiptFor?.let { txId ->
    val tx = d.transactions.find { it.id == txId }
    if (tx == null) {
      LaunchedEffect(txId) { receiptFor = null }
    } else {
      DebtReceiptDialog(
        d = d,
        debtorId = tx.debtorId,
        txId = txId,
        onDismiss = { receiptFor = null },
        onDue = if (tx.type == "give") ({ dueFor = txId; receiptFor = null }) else null,
      )
    }
  }

  dueFor?.let { txId ->
    val tx = d.transactions.find { it.id == txId }
    val entry = tx?.let { DebtBook.book(d, it.debtorId, todayIso())?.entry(txId) }
    if (entry == null) {
      LaunchedEffect(txId) { dueFor = null }
    } else {
      DueDateDialog(
        entry = entry,
        onDismiss = { dueFor = null },
        onSave = { due -> apply(DebtorEngine.setDueDate(d, txId, due), "سررسید ثبت شد") { dueFor = null } },
      )
    }
  }

  confirmDelete?.let { debtor ->
    AlertDialog(
      onDismissRequest = { confirmDelete = null },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text("حذف قرض‌دار؟", color = Shop.colors.text) },
      text = {
        Text(
          DebtorEngine.deleteWarning(d, debtor.id),
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
      },
      confirmButton = {
        TextButton(onClick = {
          apply(DebtorEngine.delete(d, debtor.id), "با موفقیت حذف شد") {
            confirmDelete = null
            openId = null
          }
        }) { Text("حذف", color = Shop.colors.danger) }
      },
      dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("انصراف") } },
    )
  }

  confirmTx?.let { txId ->
    AlertDialog(
      onDismissRequest = { confirmTx = null },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text("حذف تراکنش؟", color = Shop.colors.text) },
      text = {
        Text(
          "این تراکنش پاک می‌شود و مانده حساب دوباره حساب می‌شود.",
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
      },
      confirmButton = {
        TextButton(onClick = {
          apply(DebtorEngine.deleteTransaction(d, txId), "تراکنش حذف شد") { confirmTx = null }
        }) { Text("حذف", color = Shop.colors.danger) }
      },
      dismissButton = { TextButton(onClick = { confirmTx = null }) { Text("انصراف") } },
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

/** حسابِ یک نفر — مانده، دکمه‌ها، و همهٔ تراکنش‌هایش */
@Composable
private fun AccountView(
  d: ShopData,
  account: DebtorEngine.Account,
  onBack: () -> Unit,
  onNewTx: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onDeleteTx: (String) -> Unit,
  onOpenTx: (String) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var pending by remember { mutableStateOf<ReportCsv.Sheet?>(null) }
  //  دفترِ همین مشتری: اقلام، تخصیصِ پرداخت، سررسید و ماندهٔ پس از هر ردیف
  val book = remember(d, account.debtor.id) { DebtBook.book(d, account.debtor.id, todayIso()) }
  //  صورت‌حساب با انتخابگرِ خودِ سیستم ذخیره می‌شود — بی اجازهٔ حافظه
  val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
    val sheet = pending
    pending = null
    if (uri == null || sheet == null) return@rememberLauncherForActivityResult
    scope.launch {
      runCatching {
        withContext(Dispatchers.IO) {
          context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(ReportCsv.text(sheet).toByteArray(Charsets.UTF_8))
          } ?: error("فایل ساخته نشد")
        }
      }
    }
  }
  Box(Modifier.fillMaxSize()) {
  LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp)) {
    item {
      Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        TextButton(onClick = onBack) {
          Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = Shop.colors.primary)
          Spacer(Modifier.width(6.dp))
          Text("بازگشت به قرض‌داران", color = Shop.colors.primary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = "ویرایش", tint = Shop.colors.muted)
          }
          IconButton(onClick = onDelete) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = "حذف", tint = Shop.colors.danger)
          }
        }
      }
      Spacer(Modifier.height(6.dp))

      Text(account.debtor.name, style = MaterialTheme.typography.headlineMedium, color = Shop.colors.text)
      if (account.debtor.phone.isNotBlank()) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
          Text(ir.vil3ntec.tohid.LocaleText.phoneDisplay(account.debtor.phone), style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
        }
        Spacer(Modifier.height(10.dp))
        /*
         *  یادآوری به قرض‌دار.
         *
         *  شماره‌اش را از اول می‌گرفتیم و هیچ کاری با آن نمی‌شد کرد؛
         *  فروشنده باید شماره را می‌خواند و در برنامهٔ دیگری دستی
         *  می‌زد. در دکانی که قرض می‌دهد، این کارِ هر روز است.
         */
        ReminderRow(account.debtor.name, account.debtor.phone, account.balance)
      }
      if (account.debtor.notes.isNotBlank()) {
        Spacer(Modifier.height(4.dp))
        Text(account.debtor.notes, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
      }

      //  «چند وقت است بدهکار است» — با تاریخِ همان قرض، تا بشود سرش حرف زد
      account.since?.let { since ->
        Spacer(Modifier.height(8.dp))
        Row(
          Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Shop.colors.dangerTint)
            .padding(horizontal = 10.dp, vertical = 5.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          Icon(
            Icons.Filled.Schedule,
            contentDescription = null,
            tint = Shop.colors.danger,
            modifier = Modifier.size(13.dp),
          )
          Text(
            "${spanText(since)} است که بدهکار است — از ${formatMillis(since)}",
            style = MaterialTheme.typography.labelMedium,
            color = Shop.colors.danger,
          )
        }
      }
      book?.let {
        Spacer(Modifier.height(8.dp))
        DebtBookBanner(it)
      }
      Spacer(Modifier.height(14.dp))

      // سه کارتِ بالای حساب — همان چیدمانِ وب: کل برد، کل رسید، الباقی
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AccountTile(
          "کل برد", money(account.given), "؋",
          Shop.colors.danger, Shop.colors.dangerTint, Modifier.weight(1f),
        )
        AccountTile(
          "کل رسید", money(account.received), "؋",
          Shop.colors.success, Shop.colors.successTint, Modifier.weight(1f),
        )
        AccountTile(
          "الباقی",
          if (account.balance == 0.0) "تسویه" else money(kotlin.math.abs(account.balance)),
          if (account.balance == 0.0) "حساب صاف" else DebtorEngine.stateText(account.balance),
          when {
            account.balance > 0 -> Shop.colors.warning
            account.balance < 0 -> Shop.colors.success
            else -> Shop.colors.success
          },
          Shop.colors.surface,
          Modifier.weight(1f),
        )
      }

      //  صورت‌حساب: همان ردیف‌ها با ماندهٔ پس از هر کدام — برای دادن به مشتری
      if (account.transactions.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = {
              val sheet = ReportCsv.statement(d, account.debtor.id, todayIso())
              pending = sheet
              saveCsv.launch("${sheet.name}.csv")
            },
            modifier = Modifier.weight(1f),
          ) {
            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text("صورت‌حساب (CSV)")
          }
          OutlinedButton(
            onClick = {
              val sheet = ReportCsv.statement(d, account.debtor.id, todayIso())
              ReportPrint.print(context, sheet, "صورت‌حسابِ ${account.debtor.name}")
            },
            modifier = Modifier.weight(1f),
          ) {
            Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text("چاپ صورت‌حساب")
          }
        }
      }

      //  فاکتورهای همین مشتری — تا حالا فقط ردیفِ بدهی دیده می‌شد، نه خودِ خرید
      if (account.sales.isNotEmpty()) {
        Spacer(Modifier.height(18.dp))
        SectionTitle("فاکتورهای این مشتری (${account.sales.size})")
        Panel {
          account.sales.take(SALES_SHOWN).forEachIndexed { i, sale ->
            if (i > 0) HorizontalDivider(color = Shop.colors.border)
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
              Column(Modifier.weight(1f)) {
                Text(
                  "فاکتور ${sale.invoiceLabel() ?: "—"}${if (sale.status == "cancelled") " (لغوشده)" else ""}",
                  style = MaterialTheme.typography.bodyMedium,
                  color = if (sale.status == "cancelled") Shop.colors.muted2 else Shop.colors.text,
                )
                Text(formatDate(sale.date), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
              }
              Column(horizontalAlignment = Alignment.End) {
                Text(money(sale.finalTotal), style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
                if (sale.remaining > 0 && sale.status != "cancelled") {
                  Text("مانده ${money(sale.remaining)}", style = MaterialTheme.typography.labelSmall, color = Shop.colors.danger)
                }
              }
            }
          }
          if (account.sales.size > SALES_SHOWN) {
            Text(
              "${account.sales.size - SALES_SHOWN} فاکتورِ قدیمی‌تر در «فروش‌ها»",
              style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted,
            )
          }
        }
      }

      Spacer(Modifier.height(18.dp))
      SectionTitle("تاریخچهٔ تراکنش‌ها")
    }

    if (account.transactions.isEmpty()) {
      item { Panel { EmptyNote("هنوز تراکنشی برای این حساب ثبت نشده.") } }
    } else {
      items(account.transactions, key = { it.id }) { tx ->
        /*
         *  برد قرمز، رسید سبز — و اسمش روی خودش نوشته.
         *
         *  تا حالا هر دو یک کارتِ خاکستریِ یک‌شکل بودند و تنها فرقشان
         *  علامتِ + و − کنارِ عدد بود. کسی که فهرستِ حسابِ یک مشتری را
         *  بالا و پایین می‌کند، از روی رنگ می‌فهمد نه از روی یک علامتِ
         *  ریز؛ و «قرض داده شد» هم همان کلمه‌ای نبود که بالای همین صفحه
         *  («کل برد» و «کل رسید») نوشته شده.
         */
        val isGive = tx.type == "give"
        val tint = if (isGive) Shop.colors.danger else Shop.colors.success
        val wash = if (isGive) Shop.colors.dangerTint else Shop.colors.successTint
        val kind = if (isGive) "برد" else "رسید"

        Row(
          Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background(wash)
            .border(1.dp, tint.copy(alpha = 0.45f), RoundedCornerShape(Radius.sm))
            //  لمسِ ردیف ⇒ رسیدش (چاپ/فرستادن) و برای برد، سررسید
            .clickable { onOpenTx(tx.id) }
            .padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          // نوارِ رنگیِ کنارِ کارت: پیش از خواندنِ هر کلمه‌ای معلوم است
          // این ردیف برد بوده یا رسید
          Box(
            Modifier
              .width(4.dp)
              .height(38.dp)
              .clip(RoundedCornerShape(2.dp))
              .background(tint)
          )
          Spacer(Modifier.width(10.dp))

          Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                Modifier
                  .clip(RoundedCornerShape(6.dp))
                  .background(tint)
                  .padding(horizontal = 8.dp, vertical = 2.dp),
              ) {
                Text(
                  kind,
                  style = MaterialTheme.typography.labelSmall,
                  color = Color.White,
                  fontWeight = FontWeight.Bold,
                )
              }
              Spacer(Modifier.width(8.dp))
              Text(
                if (isGive) "جنس یا پول برد" else "پول رساند",
                style = MaterialTheme.typography.bodyMedium,
                color = Shop.colors.text,
              )
            }
            Spacer(Modifier.height(3.dp))
            Text(
              "${formatDate(tx.date)}${if (tx.notes.isNotBlank()) " — ${tx.notes}" else ""}",
              style = MaterialTheme.typography.labelSmall,
              color = Shop.colors.muted,
            )
            book?.let { bk ->
              bk.entry(tx.id)?.let { e ->
                Spacer(Modifier.height(4.dp))
                DebtEntryDetail(bk, e)
              }
            }
          }
          Text(
            "${if (isGive) "+" else "−"}${money(tx.amount)}",
            style = MaterialTheme.typography.titleSmall,
            color = tint,
            fontWeight = FontWeight.Bold,
          )
          IconButton(onClick = { onDeleteTx(tx.id) }) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = "حذف تراکنش", tint = Shop.colors.muted2)
          }
        }
        Spacer(Modifier.height(8.dp))
      }
    }
  }

  // نوارِ پایین برای ثبت تراکنش — همان نوارِ چسبیدهٔ نسخهٔ وب
  Row(
    Modifier
      .align(Alignment.BottomCenter)
      .fillMaxWidth()
      .padding(12.dp)
      .clip(RoundedCornerShape(Radius.md))
      .background(Shop.colors.surface)
      .border(1.dp, Shop.colors.primary.copy(alpha = 0.4f), RoundedCornerShape(Radius.md))
      .clickable(onClick = onNewTx)
      .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(
      Modifier.size(38.dp).clip(RoundedCornerShape(19.dp)).background(Shop.colors.primary),
      contentAlignment = Alignment.Center,
    ) {
      Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White)
    }
    Column(Modifier.weight(1f)) {
      Text(
        "ثبت تراکنش جدید",
        style = MaterialTheme.typography.titleSmall,
        color = Shop.colors.text,
        fontWeight = FontWeight.Bold,
      )
      Text(
        "مبلغ را بنویسید و برد یا رسید را انتخاب کنید",
        style = MaterialTheme.typography.labelSmall,
        color = Shop.colors.muted,
      )
    }
  }
  }
}

/** چند فاکتورِ آخرِ مشتری در صفحهٔ حسابش — بقیه در «فروش‌ها» */
private const val SALES_SHOWN = 20

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

@Composable
private fun DebtorDialog(
  state: DebtorFormState,
  onDismiss: () -> Unit,
  onSave: (DebtorEngine.DebtorDraft) -> Unit,
) {
  var form by remember(state.editingId) { mutableStateOf(state) }

  Dialog(onDismissRequest = onDismiss) {
    DialogEntry {
    Surface(color = Shop.colors.surfaceSolid, shape = RoundedCornerShape(Radius.lg), modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {
        Text(
          if (form.editingId == null) "قرض‌دار تازه" else "ویرایش قرض‌دار",
          style = MaterialTheme.typography.titleMedium,
          color = Shop.colors.text,
        )
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(form.name, { form = form.copy(name = it) }, label = { Text("نام") },
          singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(form.phone, { form = form.copy(phone = it) }, label = { Text("تلفن (اختیاری)") },
          singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(form.notes, { form = form.copy(notes = it) }, label = { Text("یادداشت (اختیاری)") },
          modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        CreditLimitField(form.creditLimit) { form = form.copy(creditLimit = it) }

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("انصراف") }
          Button(
            onClick = { onSave(DebtorEngine.DebtorDraft(form.name, form.phone, form.notes, parseLimit(form.creditLimit))) },
            modifier = Modifier.weight(1f),
          ) { Text("ذخیره") }
        }
      }
    }
    }
  }
}

@Composable
private fun TransactionDialog(
  debtor: Debtor?,
  balance: Double,
  kind: DebtorEngine.Kind,
  onDismiss: () -> Unit,
  onSave: (DebtorEngine.Kind, Double, String, String, String?) -> Unit,
) {
  var amount by remember { mutableStateOf("") }
  var date by remember { mutableStateOf(todayIso()) }
  var notes by remember { mutableStateOf("") }
  var due by remember { mutableStateOf("") }
  // نوعِ تراکنش داخلِ همین کادر عوض می‌شود — مثل نسخهٔ وب، که یک کادر
  // دارد با دو کلید، نه دو دکمهٔ جدا در صفحهٔ حساب
  var chosen by remember { mutableStateOf(kind) }
  val receiving = chosen == DebtorEngine.Kind.RECEIVE

  Dialog(onDismissRequest = onDismiss) {
    DialogEntry {
    Surface(color = Shop.colors.surfaceSolid, shape = RoundedCornerShape(Radius.lg), modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {
        Text(
          if (receiving) "ثبت رسید جدید" else "ثبت برد جدید",
          style = MaterialTheme.typography.titleMedium,
          color = Shop.colors.text,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          KindChoice(
            title = "برد",
            detail = "افزایش بدهی",
            selected = !receiving,
            tint = Shop.colors.danger,
            modifier = Modifier.weight(1f),
          ) { chosen = DebtorEngine.Kind.GIVE }
          KindChoice(
            title = "رسید",
            detail = "کاهش بدهی",
            selected = receiving,
            tint = Shop.colors.success,
            modifier = Modifier.weight(1f),
          ) { chosen = DebtorEngine.Kind.RECEIVE }
        }
        if (debtor != null) {
          Spacer(Modifier.height(4.dp))
          Text(
            "${debtor.name} — ${DebtorEngine.stateText(balance)}",
            style = MaterialTheme.typography.bodySmall,
            color = Shop.colors.muted,
          )
        }

        Spacer(Modifier.height(14.dp))
        AmountField(amount, { amount = it }, "مبلغ")

        // میان‌بُرِ «تسویهٔ کامل» فقط وقتی معنی دارد که بدهکار باشد
        if (receiving && balance > 0) {
          Spacer(Modifier.height(6.dp))
          TextButton(onClick = { amount = Math.round(balance).toString() }) {
            Text("تسویهٔ کامل", color = Shop.colors.primary)
          }
        }

        Spacer(Modifier.height(8.dp))
        DateField(date) { date = it }

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(notes, { notes = it }, label = { Text("یادداشت (اختیاری)") },
          modifier = Modifier.fillMaxWidth())

        //  سررسید فقط برای برد (اختیاری) — رسید سررسید ندارد
        if (!receiving) {
          Spacer(Modifier.height(10.dp))
          DueDateField(due) { due = it }
        }

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("انصراف") }
          Button(
            onClick = { onSave(chosen, amount.toDoubleOrNull() ?: 0.0, date, notes, due.ifBlank { null }.takeIf { !receiving }) },
            modifier = Modifier.weight(1f),
          ) { Text(if (receiving) "ثبت رسید" else "ثبت برد") }
        }
      }
    }
    }
  }
}


/**
 *  سه راهِ یادآوری: زنگ، پیامک، واتساپ.
 *
 *  متنِ آماده عمدی است — نوشتنِ «سلام، … افغانی طلب دارید» برای هر
 *  مشتری، همان کاری است که فروشنده از انجامش طفره می‌رود.
 */
@Composable
private fun ReminderRow(name: String, phone: String, balance: Double) {
  val context = LocalContext.current
  val clean = remember(phone) { ir.vil3ntec.tohid.normalizeDigits(phone).filter { it in '0'..'9' || it == '+' } }
  if (clean.isBlank()) return

  val message = remember(name, balance) {
    if (balance > 0) "سلام $name عزیز، یادآوری می‌کنم که ${money(balance)} افغانی از حساب شما باقی مانده. ممنون."
    else "سلام $name عزیز،"
  }

  fun open(uri: String) {
    runCatching {
      context.startActivity(
        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri))
      )
    }
  }

  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    ReminderChip("زنگ", Icons.Filled.Call, Shop.colors.primary) {
      runCatching {
        context.startActivity(
          android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$clean"))
        )
      }
    }
    ReminderChip("پیامک", Icons.Filled.Sms, Shop.colors.accent) {
      open("smsto:$clean?body=" + android.net.Uri.encode(message))
    }
    ReminderChip("واتساپ", Icons.Filled.Chat, Color(0xFF25D366)) {
      val number = clean.removePrefix("+")
      open("https://wa.me/$number?text=" + android.net.Uri.encode(message))
    }
  }
}

@Composable
private fun ReminderChip(
  text: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  tint: Color,
  onClick: () -> Unit,
) {
  Row(
    Modifier
      .clip(RoundedCornerShape(Radius.sm))
      .background(tint.copy(alpha = 0.13f))
      .clickable(onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
    Text(
      text,
      style = MaterialTheme.typography.labelMedium,
      color = tint,
      fontWeight = FontWeight.Bold,
    )
  }
}

/** کارتِ رنگیِ بالای حساب — همان سه کارتِ «کل برد / کل رسید / الباقی» وب */
@Composable
private fun AccountTile(
  label: String,
  value: String,
  hint: String,
  tint: Color,
  background: Color,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier
      .clip(RoundedCornerShape(Radius.md))
      .background(background)
      .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(Radius.md))
      .padding(12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
    Spacer(Modifier.height(4.dp))
    Text(value, style = MaterialTheme.typography.titleMedium, color = tint, fontWeight = FontWeight.Bold)
    Text(hint, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
  }
}


/** یکی از دو کلیدِ «برد» و «رسید» در کادرِ تراکنش */
@Composable
private fun KindChoice(
  title: String,
  detail: String,
  selected: Boolean,
  tint: Color,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  Column(
    modifier
      .clip(RoundedCornerShape(Radius.md))
      .background(if (selected) tint.copy(alpha = 0.12f) else Shop.colors.surface)
      .border(
        if (selected) 1.5.dp else 1.dp,
        if (selected) tint else Shop.colors.border,
        RoundedCornerShape(Radius.md),
      )
      .clickable(onClick = onClick)
      .padding(vertical = 10.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      title,
      style = MaterialTheme.typography.labelLarge,
      color = if (selected) tint else Shop.colors.text,
      fontWeight = FontWeight.Bold,
    )
    Text(detail, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
  }
}
