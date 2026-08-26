package af.tohid.shop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import af.tohid.shop.TohidApp
import af.tohid.shop.data.db.ExpenseEntity
import af.tohid.shop.data.repo.OpResult
import af.tohid.shop.ui.components.*
import af.tohid.shop.ui.theme.T
import af.tohid.shop.util.Format
import af.tohid.shop.util.Ids

@Composable
fun ExpensesScreen(addTick: Int = 0) {
    val app = TohidApp.instance
    val scope = rememberCoroutineScope()

    val expenses by app.db.expenses().observeAll().collectAsState(initial = emptyList())

    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<ExpenseEntity?>(null) }
    var isNew by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<Pair<String, Tone>?>(null) }

    LaunchedEffect(addTick) {
        if (addTick > 0) { editing = ExpenseEntity(id = Ids.new()); isNew = true }
    }

    val categories = remember(expenses) {
        expenses.map { it.category.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    }
    val visible = remember(expenses, query, category) {
        expenses.filter { e ->
            val q = query.trim()
            val mq = q.isEmpty() || e.title.contains(q, true) || e.notes.contains(q, true)
            val mc = category.isEmpty() || e.category.trim() == category
            mq && mc
        }
    }
    val total = remember(visible) { visible.sumOf { it.amount } }
    val thisMonth = remember(expenses) {
        val prefix = Format.today().take(7)      // YYYY-MM
        expenses.filter { it.date.startsWith(prefix) }.sumOf { it.amount }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            PageToolbar("مصارف", "خرج‌های روزمره‌ی دکان")

            StatRow {
                StatCard(
                    "مصارف این ماه", Format.money(thisMonth),
                    Icons.Outlined.DateRange, Tone.Orange, "افغانی", Modifier.weight(1f),
                )
                StatCard(
                    "مجموع نمایش‌داده‌شده", Format.money(total),
                    Icons.Outlined.ReceiptLong, Tone.Blue, "افغانی", Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(12.dp))
            SearchField(query, { query = it }, "جستجوی مصرف…")

            if (categories.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                LazyRowChips(
                    options = listOf("" to "همه") + categories.map { it to it },
                    selected = category,
                    onSelect = { category = it },
                )
            }
            notice?.let {
                Spacer(Modifier.height(12.dp))
                Notice(it.first, it.second)
            }
        }

        if (visible.isEmpty()) {
            EmptyStateFor(
                hasAny = expenses.isNotEmpty(),
                emptyTitle = "هنوز مصرفی ثبت نشده",
                emptySub = "کرایه، برق، حمل‌ونقل و هر خرج دیگر دکان را اینجا ثبت کنید.",
                onAdd = { editing = ExpenseEntity(id = Ids.new()); isNew = true },
                addLabel = "+ ثبت مصرف",
                icon = Icons.Outlined.ReceiptLong,
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(visible, key = { it.id }) { e ->
                    ExpenseRow(e) { editing = e; isNew = false }
                }
            }
        }
    }

    editing?.let { draft ->
        ExpenseSheet(
            draft = draft,
            isNew = isNew,
            knownCategories = categories,
            onDismiss = { editing = null },
            onSave = { e ->
                scope.launch {
                    when (val r = app.catalog.saveExpense(e, isNew)) {
                        is OpResult.Refused -> notice = r.message to Tone.Red
                        is OpResult.OkWithWarning -> { notice = r.message to Tone.Orange; editing = null }
                        OpResult.Ok -> { notice = null; editing = null }
                    }
                }
            },
            onDelete = { id ->
                scope.launch {
                    when (val r = app.catalog.deleteExpense(id)) {
                        is OpResult.Refused -> notice = r.message to Tone.Red
                        is OpResult.OkWithWarning -> { notice = r.message to Tone.Orange; editing = null }
                        OpResult.Ok -> { notice = null; editing = null }
                    }
                }
            },
        )
    }
}

/* ------------------------------------------------------------------ */

@Composable
private fun ExpenseRow(e: ExpenseEntity, onClick: () -> Unit) {
    TCard(Modifier.fillMaxWidth(), padding = 14.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    e.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = T.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    if (e.category.isBlank()) Format.shortDate(e.date)
                    else "${e.category} — ${Format.shortDate(e.date)}",
                    fontSize = 11.5.sp,
                    color = T.muted,
                )
            }
            Text(
                Format.money(e.amount),
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = T.warning,
            )
        }
        if (e.notes.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(e.notes, fontSize = 11.5.sp, color = T.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/* ------------------------------------------------------------------ */

@Composable
private fun ExpenseSheet(
    draft: ExpenseEntity,
    isNew: Boolean,
    knownCategories: List<String>,
    onDismiss: () -> Unit,
    onSave: (ExpenseEntity) -> Unit,
    onDelete: (String) -> Unit,
) {
    var title by remember(draft.id) { mutableStateOf(draft.title) }
    var category by remember(draft.id) { mutableStateOf(draft.category) }
    var amount by remember(draft.id) { mutableStateOf(numText(draft.amount)) }
    var notes by remember(draft.id) { mutableStateOf(draft.notes) }
    var confirmDelete by remember { mutableStateOf(false) }

    FormSheet(if (isNew) "ثبت مصرف" else "ویرایش مصرف", onDismiss) {
        TField("عنوان", title, { title = it }, placeholder = "مثلاً کرایه دکان")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { TField("دسته‌بندی", category, { category = it }, placeholder = "کرایه") }
            Box(Modifier.weight(1f)) {
                TField("مبلغ (افغانی)", amount, { amount = digitsOnly(it) }, numeric = true)
            }
        }
        if (knownCategories.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            LazyRowChips(knownCategories.map { it to it }, category) { category = it }
        }
        Spacer(Modifier.height(12.dp))
        TField("یادداشت", notes, { notes = it }, placeholder = "اختیاری")

        FormActions(
            confirmLabel = if (isNew) "ثبت مصرف" else "ذخیره",
            onConfirm = {
                onSave(
                    draft.copy(
                        title = title,
                        category = category.trim(),
                        amount = amount.toDoubleOrNull() ?: 0.0,
                        notes = notes.trim(),
                    )
                )
            },
            onCancel = onDismiss,
            deleteLabel = if (isNew) null else "حذف مصرف",
            onDelete = if (isNew) null else ({ confirmDelete = true }),
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "حذف «${draft.title}»؟",
            message = "این مصرف از دفتر پاک می‌شود و از گزارش‌ها کم می‌شود.",
            confirmLabel = "حذف",
            danger = true,
            onConfirm = { confirmDelete = false; onDelete(draft.id) },
            onDismiss = { confirmDelete = false },
        )
    }
}
