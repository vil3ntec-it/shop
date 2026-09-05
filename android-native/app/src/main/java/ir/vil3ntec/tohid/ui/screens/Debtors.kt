package ir.vil3ntec.tohid.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.ui.window.Dialog
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
fun DebtorsScreen(store: ShopStore, d: ShopData, snackbar: SnackbarHostState) {
  val scope = rememberCoroutineScope()

  var search by rememberSaveable { mutableStateOf("") }
  //  ترتیبِ فهرست بین باز و بسته شدنِ صفحه می‌ماند: کسی که «قدیمی‌ترین
  //  قرض» را انتخاب کرده، هر بار از نو انتخابش نمی‌کند
  var sortName by rememberSaveable { mutableStateOf(DebtorSort.MOST.name) }
  val sort = runCatching { DebtorSort.valueOf(sortName) }.getOrDefault(DebtorSort.MOST)
  var openId by rememberSaveable { mutableStateOf<String?>(null) }
  var form by remember { mutableStateOf<DebtorFormState?>(null) }
  var txFor by remember { mutableStateOf<Pair<String, DebtorEngine.Kind>?>(null) }
  var confirmDelete by remember { mutableStateOf<Debtor?>(null) }
  var confirmTx by remember { mutableStateOf<String?>(null) }

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
      account = account,
      onBack = { openId = null },
      onNewTx = { txFor = account.debtor.id to DebtorEngine.Kind.GIVE },
      onEdit = { form = DebtorFormState.of(account.debtor) },
      onDelete = { confirmDelete = account.debtor },
      onDeleteTx = { confirmTx = it },
    )
  } else {
    /*
     *  ردیف‌ها یک بار حساب می‌شوند، نه با هر کشیدنِ فهرست.
     *
     *  مدتِ قرضِ هر نفر از همهٔ تراکنش‌هایش حساب می‌شود؛ در دکانی با صد
     *  قرض‌دار و هزار تراکنش، انجامِ این کار در هر بار کشیده شدنِ صفحه
     *  اسکرول را کند می‌کرد.
     */
    val term = search.trim()
    val rows = remember(d, term, sort) {
      val byDebtor = d.transactions.groupBy { it.debtorId }
      d.debtors
        .filter {
          term.isBlank() || it.name.contains(term, ignoreCase = true) || it.phone.contains(term)
        }
        .map { debtor ->
          val mine = byDebtor[debtor.id].orEmpty()
          DebtorRow(
            debtor = debtor,
            balance = ShopStore.debt(d, debtor.id),
            days = DebtorEngine.debtDays(d, debtor.id),
            given = mine.filter { it.type == "give" }.sumOf { it.amount },
            received = mine.filter { it.type == "receive" }.sumOf { it.amount },
          )
        }
        .sortedWith(sort.order)
    }

    /*
     *  عقب‌افتاده‌ها همیشه بالای فهرست‌اند، هر ترتیبی که انتخاب شده
     *  باشد. کسی که یک ماه است خبری ازش نیست، پایینِ فهرست فراموش
     *  می‌شود — و فراموش‌شدن، همان چیزی است که این صفحه باید جلویش را
     *  بگیرد. حساب‌های صاف هم ته می‌روند و جمع می‌شوند.
     */
    val owing = rows.filter { it.balance > 0 }
      .sortedWith(compareByDescending<DebtorRow> { (it.days ?: 0L) >= 30 }.thenComparator { x, y -> sort.order.compare(x, y) })
    val settled = rows.filter { it.balance <= 0 }

    var settledOpen by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          DebtorsHero(
            owed = owing.sumOf { it.balance },
            people = owing.size,
            late = owing.count { (it.days ?: 0L) >= 30 },
            collected = remember(d) {
              val month = todayIso().take(7)
              d.transactions.filter { it.type == "receive" && it.date.startsWith(month) }.sumOf { it.amount }
            },
          )
        }

        item {
          VoiceSearchField(
            value = search,
            onValueChange = { search = it },
            label = "جستجوی نام یا شماره",
          )
        }

        item {
          DebtorSortRow(current = sort, count = rows.size) { sortName = it.name }
        }

        if (rows.isEmpty()) {
          item {
            Panel {
              if (d.debtors.isEmpty()) {
                TohidEmptyState(
                  icon = Icons.Filled.Groups,
                  title = "هنوز قرض‌داری ثبت نشده",
                  description = "کسانی که از شما نسیه می‌برند را اینجا اضافه کنید تا حساب هرکدام جدا بماند.",
                  actionText = "قرض‌دار تازه",
                  onAction = { form = DebtorFormState() },
                )
              } else {
                TohidEmptyState(
                  icon = Icons.Filled.Search,
                  title = "چیزی پیدا نشد",
                  description = "قرض‌داری با این نام یا شماره نیست.",
                )
              }
            }
          }
        } else {
          //  یک پنلِ شیشه‌ای که ردیف‌ها داخلش‌اند — نه یک شیشه به ازای
          //  هر ردیف. صد کارتِ شناور، فهرست را به هم می‌ریزد.
          item {
            Panel(Modifier.fillMaxWidth()) {
              owing.forEachIndexed { index, row ->
                DebtorLine(
                  row = row,
                  onOpen = { openId = row.debtor.id },
                  onSwipedAway = {
                    val before = d
                    apply(DebtorEngine.delete(d, row.debtor.id), "${row.debtor.name.ifBlank { "قرض‌دار" }} حذف شد") {
                      scope.launch {
                        val answer = snackbar.showSnackbar(
                          message = "${row.debtor.name.ifBlank { "قرض‌دار" }} حذف شد",
                          actionLabel = "واگرد",
                          duration = SnackbarDuration.Short,
                        )
                        if (answer == SnackbarResult.ActionPerformed) store.save(before)
                      }
                    }
                  },
                )
                if (index != owing.lastIndex) RowDivider()
              }

              if (owing.isEmpty()) {
                EmptyNote("کسی بدهکار نیست — همهٔ حساب‌ها صاف است.")
              }

              /*
               *  حساب‌های صاف، هم‌وزنِ بدهکارها نیستند.
               *
               *  کسی که پولش را داده، دیگر کارِ امروزِ دکاندار نیست؛ ولی
               *  باید بشود پیدایش کرد. پس یک ردیفِ جمع‌شده ته پنل.
               */
              if (settled.isNotEmpty()) {
                if (owing.isNotEmpty()) RowDivider()
                SettledSection(settled, settledOpen, { settledOpen = !settledOpen }) { openId = it }
              }
            }
          }
        }

        item {
          GradientWideButton("+ قرض‌دار جدید") { form = DebtorFormState() }
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
          DebtorEngine.edit(d, state.editingId, draft)
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
      onSave = { chosenKind, amount, date, notes ->
        apply(
          DebtorEngine.addTransaction(
            d, debtorId, chosenKind, amount, date, notes, todayIso(), System.currentTimeMillis(), ::newId,
          ),
          "با موفقیت ثبت شد",
        ) { txFor = null }
      },
    )
  }

  confirmDelete?.let { debtor ->
    AlertDialog(
      onDismissRequest = { confirmDelete = null },
      containerColor = Shop.colors.surface,
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
      containerColor = Shop.colors.surface,
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

/** یک ردیفِ فهرست: خودِ قرض‌دار، مانده‌اش، و چند روز است که بدهکار است */
private data class DebtorRow(
  val debtor: Debtor,
  val balance: Double,
  /** `null` یعنی بدهکار نیست */
  val days: Long?,
  /** جمعِ قرضِ داده‌شده و پولِ گرفته‌شده — برای نوارِ نسبتِ پرداخت */
  val given: Double = 0.0,
  val received: Double = 0.0,
)

/**
 *  ترتیبِ فهرستِ قرض‌داران.
 *
 *  چهار تا، نه بیشتر: هر ترتیبِ اضافه یک تراشهٔ دیگر در ردیف است و
 *  انتخاب را سخت‌تر می‌کند، نه آسان‌تر.
 */
private enum class DebtorSort(val label: String, val order: Comparator<DebtorRow>) {
  /** بیشترین مبلغ اول — همان ترتیبی که فهرست از روزِ اول داشت */
  MOST("بیشترین قرض", compareByDescending<DebtorRow> { it.balance }),

  /** کمترین اول؛ کسی که پیشِ ما موجودی دارد (مانده‌ی منفی) سرِ فهرست */
  LEAST("کمترین قرض", compareBy<DebtorRow> { it.balance }),

  /**
   *  قرضی که بیشتر مانده، اول.
   *
   *  کسی که بدهکار نیست (`days == null`) ته می‌رود، نه اول: `-1` او را
   *  از هر مدتی کوچک‌تر می‌کند.
   */
  OLDEST(
    "قدیمی‌ترین قرض",
    compareByDescending<DebtorRow> { it.days ?: -1L }.thenByDescending { it.balance },
  ),

  NAME("بر اساس نام", compareBy<DebtorRow> { it.debtor.name }),
}

/** ردیفِ تراشه‌های ترتیب — روی گوشیِ باریک افقی اسکرول می‌شود */
@Composable
private fun DebtorSortRow(current: DebtorSort, count: Int, onPick: (DebtorSort) -> Unit) {
  val colors = Shop.colors
  Row(
    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    //  شمارنده تراشه نیست، پس زده نمی‌شود: فقط می‌گوید فهرست چند تاست
    Text(
      "${plain(count)} مورد",
      style = MaterialTheme.typography.labelSmall,
      color = colors.muted2,
      modifier = Modifier.padding(end = 2.dp),
    )
    DebtorSort.entries.forEach { option ->
      val picked = option == current
      Row(
        Modifier
          .clip(RoundedCornerShape(999.dp))
          .background(if (picked) colors.primary else colors.surface)
          .border(
            1.dp,
            if (picked) colors.primary else colors.border,
            RoundedCornerShape(999.dp),
          )
          .clickable { onPick(option) }
          .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        if (picked) {
          Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(13.dp),
          )
        }
        Text(
          option.label,
          style = MaterialTheme.typography.labelMedium,
          color = if (picked) Color.White else colors.muted,
          fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal,
          maxLines = 1,
        )
      }
    }
  }
}

/** حسابِ یک نفر — مانده، دکمه‌ها، و همهٔ تراکنش‌هایش */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccountView(
  account: DebtorEngine.Account,
  onBack: () -> Unit,
  onNewTx: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onDeleteTx: (String) -> Unit,
) {
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
          Text(account.debtor.phone, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
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
      Spacer(Modifier.height(14.dp))

      /*
       *  یک عددِ اصلی، نه سه کارتِ رنگیِ رقیب.
       *
       *  «کل برد» و «کل رسید» هم‌اندازه‌ی «الباقی» بودند و چشم نمی‌دانست
       *  کدام را بخواند؛ در حالی که تنها عددی که کارِ امروزِ دکاندار است،
       *  باقی‌مانده است. اسم‌ها هم روشن شدند: «قرض گرفته» و «پرداخت
       *  کرده».
       */
      Panel(Modifier.fillMaxWidth()) {
        Text("باقی مانده", fontSize = 12.sp, color = Shop.colors.muted)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
          Text(
            if (account.balance == 0.0) "تسویه" else money(kotlin.math.abs(account.balance)),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = when {
              account.balance > 0 -> Shop.colors.danger
              else -> Shop.colors.success
            },
          )
          if (account.balance != 0.0) {
            Spacer(Modifier.width(4.dp))
            Text("؋", fontSize = 15.sp, color = Shop.colors.muted)
          }
        }
        Spacer(Modifier.height(2.dp))
        Text(DebtorEngine.stateText(account.balance), fontSize = 11.sp, color = Shop.colors.muted2)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
          HeroStat("قرض گرفته", money(account.given), Shop.colors.muted, Modifier.weight(1f))
          HeroDivider()
          HeroStat("پرداخت کرده", money(account.received), Shop.colors.muted, Modifier.weight(1f))
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

        /*
         *  تایم‌لاین، نه فهرستِ کارت.
         *
         *  هر تراکنش یک نقطه روی یک خطِ عمودی است — همان شکلی که آدم
         *  حسابِ یک نفر را در ذهنش می‌چیند: از بالا به پایین، جدیدترین
         *  اول. آیکنِ حذف از کنارِ هر ردیف برداشته شد؛ نگه‌داشتنِ انگشت
         *  روی ردیف همان کار را می‌کند بدونِ اینکه همیشه زیرِ دست باشد.
         */
        Row(
          Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = {}, onLongClick = { onDeleteTx(tx.id) })
            .padding(vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          //  خطِ عمودی و نقطه‌ی رنگی
          Box(Modifier.width(18.dp).height(46.dp), contentAlignment = Alignment.Center) {
            Box(
              Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(Shop.colors.border.copy(alpha = Shop.colors.border.alpha * 0.7f))
            )
            Box(
              Modifier
                .size(9.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(tint)
            )
          }
          Spacer(Modifier.width(10.dp))
          Column(Modifier.weight(1f)) {
            Text(
              if (isGive) "قرض گرفت" else "پرداخت کرد",
              fontSize = 13.sp,
              fontWeight = FontWeight.Medium,
              color = Shop.colors.text,
            )
            Spacer(Modifier.height(2.dp))
            Text(
              "${formatDate(tx.date)}${if (tx.notes.isNotBlank()) " · ${tx.notes}" else ""}",
              fontSize = 10.5.sp,
              color = Shop.colors.muted2,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Text(
            "${if (isGive) "+" else "−"}${money(tx.amount)}",
            fontSize = 14.sp,
            color = tint,
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }
  }

  //  یک اقدامِ اصلی ته صفحه — همان کاری که آدم روی حسابِ یک قرض‌دار
  //  می‌کند: پولی که داده را ثبت می‌کند
  Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)) {
    GradientWideButton("ثبت پرداخت", onNewTx)
  }
  }
}

/* ============================ فرم‌ها ============================ */

data class DebtorFormState(
  val editingId: String? = null,
  val name: String = "",
  val phone: String = "",
  val notes: String = "",
) {
  companion object {
    fun of(d: Debtor) = DebtorFormState(d.id, d.name, d.phone, d.notes)
  }
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
    Surface(color = Shop.colors.surface, shape = RoundedCornerShape(Radius.lg), modifier = Modifier.fillMaxWidth()) {
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

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("انصراف") }
          Button(
            onClick = { onSave(DebtorEngine.DebtorDraft(form.name, form.phone, form.notes)) },
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
  onSave: (DebtorEngine.Kind, Double, String, String) -> Unit,
) {
  var amount by remember { mutableStateOf("") }
  var date by remember { mutableStateOf(todayIso()) }
  var notes by remember { mutableStateOf("") }
  // نوعِ تراکنش داخلِ همین کادر عوض می‌شود — مثل نسخهٔ وب، که یک کادر
  // دارد با دو کلید، نه دو دکمهٔ جدا در صفحهٔ حساب
  var chosen by remember { mutableStateOf(kind) }
  val receiving = chosen == DebtorEngine.Kind.RECEIVE

  Dialog(onDismissRequest = onDismiss) {
    DialogEntry {
    Surface(color = Shop.colors.surface, shape = RoundedCornerShape(Radius.lg), modifier = Modifier.fillMaxWidth()) {
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

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("انصراف") }
          Button(
            onClick = { onSave(chosen, amount.toDoubleOrNull() ?: 0.0, date, notes) },
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
  val clean = remember(phone) { phone.filter { it.isDigit() || it == '+' } }
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

/* ==================== ردیف‌های تازه‌ی فهرست ==================== */

/** کارتِ قهرمانِ بالای فهرست — جمعِ طلب و سه عددِ کنارش */
@Composable
private fun DebtorsHero(owed: Double, people: Int, late: Int, collected: Double) {
  val colors = Shop.colors
  Panel(Modifier.fillMaxWidth()) {
    Text("جمع طلب از مشتریان", fontSize = 12.sp, color = colors.muted)
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.Bottom) {
      Text(
        money(owed),
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        color = if (owed > 0) colors.danger else colors.success,
      )
      Spacer(Modifier.width(4.dp))
      Text("؋", fontSize = 15.sp, color = colors.muted)
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      HeroStat("قرض‌دار", plain(people), colors.text, Modifier.weight(1f))
      HeroDivider()
      HeroStat("وعده گذشته", plain(late), colors.danger, Modifier.weight(1f))
      HeroDivider()
      HeroStat("وصول این ماه", money(collected), colors.success, Modifier.weight(1f))
    }
  }
}

@Composable
private fun HeroStat(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
  Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = tint, maxLines = 1)
    Spacer(Modifier.height(2.dp))
    Text(label, fontSize = 10.sp, color = Shop.colors.muted2, maxLines = 1)
  }
}

@Composable
private fun HeroDivider() {
  Box(
    Modifier
      .height(24.dp)
      .width(1.dp)
      .background(Shop.colors.border.copy(alpha = Shop.colors.border.alpha * 0.7f))
  )
}

/** خطِ جداکننده‌ی بینِ ردیف‌های داخلِ یک پنل */
@Composable
private fun RowDivider() {
  Box(
    Modifier
      .fillMaxWidth()
      .height(1.dp)
      .background(Shop.colors.border.copy(alpha = Shop.colors.border.alpha * 0.5f))
  )
}

/**
 *  یک قرض‌دار، در یک ردیف.
 *
 *  ── چرا کارت رفت و ردیف آمد ───────────────────────────────────────
 *  کارتِ مربعی، عرضِ صفحه را به دو ستون می‌شکست و در هر کارت دو دکمه‌ی
 *  ریز — حذف و ویرایش — همیشه زیرِ انگشت بود؛ همان دو دکمه‌ای که اگر
 *  اشتباهی زده شوند، حسابِ یک نفر پاک می‌شود. حالا حذف با کشیدنِ ردیف
 *  است و پنج ثانیه فرصتِ واگرد دارد، و ویرایش داخلِ خودِ حساب.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  رنگِ نوارِ کنارِ ردیف، حرفِ اصلی را می‌زند: قرمز یعنی از وعده گذشته،
 *  نارنجی یعنی نزدیک است، نعنایی یعنی منظم.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebtorLine(row: DebtorRow, onOpen: () -> Unit, onSwipedAway: () -> Unit) {
  val colors = Shop.colors
  val days = row.days ?: 0L
  val tint = when {
    days >= 30 -> colors.danger
    days >= 14 -> colors.warning
    else -> colors.success
  }
  val state = rememberSwipeToDismissBoxState(
    confirmValueChange = { value ->
      if (value == SwipeToDismissBoxValue.EndToStart) { onSwipedAway(); true } else false
    },
  )

  SwipeToDismissBox(
    state = state,
    enableDismissFromStartToEnd = false,
    enableDismissFromEndToStart = true,
    backgroundContent = {
      Box(
        Modifier
          .fillMaxSize()
          .clip(RoundedCornerShape(14.dp))
          .background(colors.danger.copy(alpha = 0.14f))
          .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart,
      ) {
        Icon(Icons.Filled.Close, contentDescription = "حذف", tint = colors.danger, modifier = Modifier.size(18.dp))
      }
    },
  ) {
    Row(
      Modifier
        .fillMaxWidth()
        .background(colors.surface.copy(alpha = 0f))
        .clickable(onClick = onOpen)
        .padding(vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      //  نوارِ رنگیِ سمتِ راستِ ردیف — در راست‌به‌چپ، اولین فرزندِ Row
      Box(Modifier.width(3.dp).height(38.dp).clip(RoundedCornerShape(2.dp)).background(tint))
      Spacer(Modifier.width(10.dp))
      LetterAvatar(row.debtor.name.ifBlank { "؟" }, 40.dp, 13.dp)
      Spacer(Modifier.width(10.dp))
      Column(Modifier.weight(1f)) {
        Text(
          row.debtor.name.ifBlank { "بی‌نام" },
          fontSize = 14.sp,
          fontWeight = FontWeight.Medium,
          color = colors.text,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
          when {
            row.days == null -> DebtorEngine.stateText(row.balance)
            days >= 30 -> "${daysText(days)} از وعده گذشته"
            else -> "${daysText(days)} است که مانده"
          },
          fontSize = 11.sp,
          color = if (days >= 30) colors.danger else colors.muted2,
          maxLines = 1,
        )
        if (row.given > 0) {
          Spacer(Modifier.height(6.dp))
          ProgressLine((row.received / row.given).toFloat().coerceIn(0f, 1f), tint)
        }
      }
      Spacer(Modifier.width(8.dp))
      Column(horizontalAlignment = Alignment.End) {
        Text(
          money(kotlin.math.abs(row.balance)),
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          color = if (row.balance > 0) tint else colors.success,
          maxLines = 1,
        )
        if (row.given > 0) {
          Text("از ${money(row.given)}", fontSize = 10.sp, color = colors.muted2, maxLines = 1)
        }
      }
    }
  }
}

/** نوارِ نسبتِ پرداخت‌شده — یک بار، هنگام آمدنِ ردیف */
@Composable
private fun ProgressLine(ratio: Float, tint: Color) {
  var shown by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { shown = true }
  val width by animateFloatAsState(
    targetValue = if (shown) ratio else 0f,
    animationSpec = Springs.progress,
    label = "paid",
  )
  Box(
    Modifier
      .fillMaxWidth()
      .height(5.dp)
      .clip(RoundedCornerShape(3.dp))
      .background(Shop.colors.border.copy(alpha = Shop.colors.border.alpha * 0.5f))
  ) {
    Box(
      Modifier
        .fillMaxWidth(width)
        .height(5.dp)
        .clip(RoundedCornerShape(3.dp))
        .background(Brush.horizontalGradient(listOf(tint, Shop.colors.accent)))
    )
  }
}

/** حساب‌های صاف — جمع‌شده، تا هم‌وزنِ بدهکارها نباشند */
@Composable
private fun SettledSection(
  settled: List<DebtorRow>,
  open: Boolean,
  onToggle: () -> Unit,
  onOpen: (String) -> Unit,
) {
  val colors = Shop.colors
  val turn by animateFloatAsState(
    targetValue = if (open) 180f else 0f,
    animationSpec = Springs.press,
    label = "chevron",
  )
  Column(Modifier.fillMaxWidth().animateContentSize(Springs.size)) {
    Row(
      Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        "حساب‌های صاف (${plain(settled.size)}) — ${settled.take(2).joinToString("، ") { it.debtor.name.ifBlank { "بی‌نام" } }}",
        fontSize = 12.sp,
        color = colors.muted2,
        modifier = Modifier.weight(1f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Icon(
        Icons.Filled.ExpandMore,
        contentDescription = null,
        tint = colors.muted2,
        modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = turn },
      )
    }
    if (open) {
      settled.forEach { row ->
        Row(
          Modifier.fillMaxWidth().clickable { onOpen(row.debtor.id) }.padding(vertical = 9.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            row.debtor.name.ifBlank { "بی‌نام" },
            fontSize = 13.sp,
            color = colors.text,
            modifier = Modifier.weight(1f),
            maxLines = 1,
          )
          Text(DebtorEngine.stateText(row.balance), fontSize = 11.sp, color = colors.success)
        }
      }
    }
  }
}

/** دکمه‌ی گرادینتیِ تمام‌عرضِ ته صفحه */
@Composable
private fun GradientWideButton(text: String, onClick: () -> Unit) {
  val colors = Shop.colors
  var pressed by remember { mutableStateOf(false) }
  Box(
    Modifier
      .fillMaxWidth()
      .height(52.dp)
      .pressScale(pressed)
      .clip(RoundedCornerShape(Radius.sm))
      .background(Brush.linearGradient(listOf(colors.primary, colors.accent)))
      .clickable { pressed = true; onClick() },
    contentAlignment = Alignment.Center,
  ) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
  }
  LaunchedEffect(pressed) {
    if (pressed) { kotlinx.coroutines.delay(120); pressed = false }
  }
}
