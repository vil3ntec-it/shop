package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ir.vil3ntec.tohid.data.Expense
import ir.vil3ntec.tohid.data.LedgerEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/**
 *  مصارف.
 *
 *  خرجِ دکان: کرایه، برق، معاش، ترانسپورت. جمعِ ماه و تقسیمش بین
 *  دسته‌بندی‌ها همان‌جا دیده می‌شود، چون همین دو عدد است که معلوم می‌کند
 *  سودِ فروش کجا رفته.
 */
@Composable
fun ExpensesScreen(store: ShopStore, d: ShopData, snackbar: SnackbarHostState) {
  val scope = rememberCoroutineScope()

  var month by rememberSaveable { mutableStateOf(todayIso().take(7)) }
  var category by rememberSaveable { mutableStateOf<String?>(null) }
  var form by remember { mutableStateOf<ExpenseFormState?>(null) }
  var confirmDelete by remember { mutableStateOf<Expense?>(null) }

  fun toast(text: String) {
    scope.launch { snackbar.showSnackbar(text) }
  }

  fun apply(result: LedgerEngine.Result, done: String, after: () -> Unit = {}) {
    when (result) {
      is LedgerEngine.Result.Failed -> toast(result.message)
      is LedgerEngine.Result.Ok -> {
        scope.launch { store.save(result.data) }
        toast(done)
        after()
      }
    }
  }

  var breakdown by rememberSaveable { mutableStateOf(false) }

  // ماه‌هایی که واقعاً مصرفی در آن‌ها ثبت شده، از تازه به کهنه
  val months = remember(d.expenses) {
    (d.expenses.map { it.date.take(7) } + todayIso().take(7)).distinct().sortedDescending()
  }
  val from = "$month-01"
  val to = "$month-31"

  val shown = d.expenses
    .filter { it.date.startsWith(month) && (category == null || it.category == category) }
    .sortedByDescending { it.createdAt }

  val total = LedgerEngine.expenseTotal(d, from, to)
  val byCategory = LedgerEngine.expensesByCategory(d, from, to)

  Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp)) {
      item {
        Text(
          "هزینه‌های ثبت‌شده فروشگاه",
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
        Spacer(Modifier.height(14.dp))
      }

      item {
        // ماه‌ها کشویی‌اند، نه یک ردیفِ افقی: با گذشتِ چند ماه، ردیف از
        // صفحه بیرون می‌زد و معلوم نبود چند ماهِ دیگر مانده
        MonthPicker(
          months = months,
          selected = month,
          onSelect = { month = it; category = null },
        )
        Spacer(Modifier.height(12.dp))
      }

      item {
        StatTile(
          label = "جمع مصارف ${monthLabel(month)}",
          value = "${money(total)} افغانی",
          tint = Shop.colors.danger,
          hint = "${plain(shown.size)} ردیف",
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
      }

      if (byCategory.isNotEmpty()) {
        item {
          /*
           *  تفکیکِ دسته‌بندی رفت به صفحهٔ خودش.
           *
           *  اینجا با هر دسته یک نوار اضافه می‌شد و بعد از چند مصرف،
           *  تمامِ صفحه را می‌گرفت — کاربر باید از رویش رد می‌شد تا به
           *  خودِ فهرستِ مصارف برسد. حالا یک ردیفِ کوچک است که می‌گوید
           *  چند دسته هست، و با زدنش باز می‌شود.
           */
          SettingsRow(
            icon = Icons.Filled.PieChart,
            title = "به تفکیک دسته‌بندی",
            description = "${plain(byCategory.size)} دسته در ${monthLabel(month)}",
            tint = Shop.colors.primary,
            onClick = { breakdown = true },
          )
          Spacer(Modifier.height(16.dp))
        }
      }

      item {
        SectionTitle(if (category == null) "همهٔ مصارف" else "مصارف «$category»")
      }

      if (shown.isEmpty()) {
        item { Panel { EmptyNote("در این ماه مصرفی ثبت نشده است.") } }
      } else {
        itemsIndexed(shown, key = { _, e -> e.id }) { index, expense ->
          StaggeredItem(index) {
          ExpenseRow(
            expense = expense,
            onEdit = { form = ExpenseFormState.of(expense) },
            onDelete = { confirmDelete = expense },
          )
          Spacer(Modifier.height(8.dp))
        }
        }
      }
    }

    // دکمهٔ کوچکِ گِرد، نه دکمهٔ پهنِ متن‌دار: آن یکی روی ردیف‌های
    // پایینِ فهرست می‌نشست و زیرش قابلِ زدن نبود
    FloatingActionButton(
      onClick = { form = ExpenseFormState(date = todayIso()) },
      containerColor = Shop.colors.success,
      contentColor = Color.White,
      modifier = Modifier.align(Alignment.BottomStart).padding(16.dp).popIn(),
    ) {
      Icon(Icons.Filled.Add, contentDescription = "مصرف تازه")
    }
  }

  if (breakdown) {
    BreakdownSheet(
      title = "مصارف ${monthLabel(month)} به تفکیک دسته",
      rows = byCategory,
      total = total,
      selected = category,
      onPick = { category = it; breakdown = false },
      onDismiss = { breakdown = false },
    )
  }

  form?.let { state ->
    ExpenseDialog(
      d = d,
      state = state,
      onDismiss = { form = null },
      onSave = { draft ->
        val result = if (state.editingId == null) {
          LedgerEngine.addExpense(d, draft, todayIso(), System.currentTimeMillis(), ::newId)
        } else {
          LedgerEngine.editExpense(d, state.editingId, draft, todayIso())
        }
        apply(result, if (state.editingId == null) "با موفقیت ثبت شد" else "با موفقیت ویرایش شد") {
          form = null
        }
      },
    )
  }

  confirmDelete?.let { expense ->
    AlertDialog(
      onDismissRequest = { confirmDelete = null },
      containerColor = Shop.colors.surface,
      title = { Text("حذف مصرف؟", color = Shop.colors.text) },
      text = {
        Text(
          "«${expense.title}» به مبلغ ${money(expense.amount)} افغانی حذف می‌شود.",
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
      },
      confirmButton = {
        TextButton(onClick = {
          apply(LedgerEngine.deleteExpense(d, expense.id), "با موفقیت حذف شد") { confirmDelete = null }
        }) { Text("حذف", color = Shop.colors.danger) }
      },
      dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("انصراف") } },
    )
  }
}

/* ============================ تکه‌ها ============================ */

@Composable
private fun ExpenseRow(expense: Expense, onEdit: () -> Unit, onDelete: () -> Unit) {
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Radius.md))
      .background(Shop.colors.surface)
      .border(1.dp, Shop.colors.border, RoundedCornerShape(Radius.md))
      .padding(14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(expense.title, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
      Spacer(Modifier.height(3.dp))
      Text(
        "${expense.category} — ${formatDate(expense.date)}",
        style = MaterialTheme.typography.labelSmall,
        color = Shop.colors.muted,
      )
    }
    Text(
      "${money(expense.amount)} افغانی",
      style = MaterialTheme.typography.titleSmall,
      color = Shop.colors.danger,
    )
    IconButton(onClick = onEdit) {
      Icon(Icons.Filled.Edit, contentDescription = "ویرایش", tint = Shop.colors.muted)
    }
    IconButton(onClick = onDelete) {
      Icon(Icons.Filled.DeleteOutline, contentDescription = "حذف", tint = Shop.colors.muted)
    }
  }
}

/** یک ردیف با نوارِ سهمِ آن دسته از کلِ مصارفِ ماه */
@Composable
private fun CategoryBar(
  name: String,
  amount: Double,
  share: Float,
  selected: Boolean,
  onClick: () -> Unit,
) {
  Column(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Radius.sm))
      .background(if (selected) Shop.colors.primaryTint else Color.Transparent)
      .clickable(onClick = onClick)
      .padding(vertical = 7.dp, horizontal = 6.dp)
  ) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(name, style = MaterialTheme.typography.bodySmall, color = Shop.colors.text)
      Text("${money(amount)} افغانی", style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
    }
    Spacer(Modifier.height(5.dp))
    Box(
      Modifier
        .fillMaxWidth()
        .height(5.dp)
        .clip(RoundedCornerShape(999.dp))
        .background(Shop.colors.surface2)
    ) {
      Box(
        Modifier
          .fillMaxWidth(share.coerceIn(0f, 1f))
          .height(5.dp)
          .clip(RoundedCornerShape(999.dp))
          .background(Shop.colors.primary)
      )
    }
  }
}

/* ============================ فرم ============================ */

data class ExpenseFormState(
  val editingId: String? = null,
  val title: String = "",
  val category: String = "",
  val amount: String = "",
  val date: String = "",
) {
  companion object {
    fun of(e: Expense) = ExpenseFormState(
      editingId = e.id,
      title = e.title,
      category = e.category,
      amount = if (e.amount == Math.floor(e.amount)) e.amount.toLong().toString() else e.amount.toString(),
      date = e.date,
    )
  }
}

@Composable
private fun ExpenseDialog(
  d: ShopData,
  state: ExpenseFormState,
  onDismiss: () -> Unit,
  onSave: (LedgerEngine.ExpenseDraft) -> Unit,
) {
  var form by remember(state.editingId) { mutableStateOf(state) }

  Dialog(onDismissRequest = onDismiss) {
    DialogEntry {
    Surface(color = Shop.colors.surface, shape = RoundedCornerShape(Radius.lg), modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {
        Text(
          if (form.editingId == null) "مصرف تازه" else "ویرایش مصرف",
          style = MaterialTheme.typography.titleMedium,
          color = Shop.colors.text,
        )
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
          value = form.title,
          onValueChange = { form = form.copy(title = it) },
          label = { Text("بابت چی؟ (اختیاری)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))
        AmountField(
          value = form.amount,
          onValueChange = { form = form.copy(amount = it) },
          label = "مبلغ (افغانی)",
        )

        Spacer(Modifier.height(14.dp))
        CategoryPicker(
          options = d.expenseCategories,
          selected = form.category,
          onSelect = { form = form.copy(category = it) },
        )

        Spacer(Modifier.height(14.dp))
        DateField(value = form.date, onValueChange = { form = form.copy(date = it) })

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("انصراف") }
          Button(
            onClick = {
              onSave(
                LedgerEngine.ExpenseDraft(
                  title = form.title,
                  category = form.category,
                  amount = form.amount.toDoubleOrNull() ?: 0.0,
                  date = form.date,
                )
              )
            },
            modifier = Modifier.weight(1f),
          ) { Text("ذخیره") }
        }
      }
    }
    }
  }
}

/** `2026-08` → «سنبله ۱۴۰۵» — ماهِ خورشیدی، چون تاریخ‌ها خورشیدی دیده می‌شوند */
//  برچسبِ ماه یک جا نوشته شده و از آنجا خوانده می‌شود — تاریخچهٔ فروش
//  هم همین برچسب را نشان می‌دهد
private fun monthLabel(month: String): String = ir.vil3ntec.tohid.formatMonth(month)

/**
 *  انتخابِ ماه — کشویی.
 *
 *  ردیفِ افقیِ ماه‌ها بعد از یک سال، دوازده تراشه می‌شد که نصفشان بیرونِ
 *  صفحه می‌ماند و کاربر نمی‌دانست چند تای دیگر هست.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthPicker(
  months: List<String>,
  selected: String,
  onSelect: (String) -> Unit,
) {
  var open by remember { mutableStateOf(false) }
  ExposedDropdownMenuBox(
    expanded = open,
    onExpandedChange = { open = it },
    modifier = Modifier.fillMaxWidth(),
  ) {
    OutlinedTextField(
      value = monthLabel(selected),
      onValueChange = {},
      readOnly = true,
      singleLine = true,
      label = { Text("ماه") },
      shape = RoundedCornerShape(Radius.sm),
      trailingIcon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) },
      modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
    )
    ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      months.forEach { m ->
        DropdownMenuItem(
          text = { Text(monthLabel(m)) },
          onClick = { onSelect(m); open = false },
        )
      }
    }
  }
}

/**
 *  تفکیکِ دسته‌بندی، در صفحهٔ خودش.
 *
 *  زدنِ هر دسته، همان دسته را در فهرستِ پشتِ سر فیلتر می‌کند و شیت بسته
 *  می‌شود — همان کاری که نوارهای قبلی می‌کردند، بدونِ اینکه تمامِ صفحه
 *  را بگیرند.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BreakdownSheet(
  title: String,
  rows: List<Pair<String, Double>>,
  total: Double,
  selected: String?,
  onPick: (String?) -> Unit,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Shop.colors.bg) {
    Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 28.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
      Spacer(Modifier.height(4.dp))
      Text(
        "روی هر دسته بزنید تا فقط مصارفِ همان دیده شود",
        style = MaterialTheme.typography.labelSmall,
        color = Shop.colors.muted2,
      )
      Spacer(Modifier.height(14.dp))
      rows.forEach { (name, amount) ->
        CategoryBar(
          name = name,
          amount = amount,
          share = if (total > 0) (amount / total).toFloat() else 0f,
          selected = selected == name,
          onClick = { onPick(if (selected == name) null else name) },
        )
      }
      if (selected != null) {
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = { onPick(null) }) { Text("نمایش همهٔ دسته‌ها") }
      }
    }
  }
}
