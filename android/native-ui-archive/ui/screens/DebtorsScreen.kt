package af.tohid.shop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import af.tohid.shop.TohidApp
import af.tohid.shop.data.db.DebtorEntity
import af.tohid.shop.data.repo.OpResult
import af.tohid.shop.ui.components.*
import af.tohid.shop.ui.theme.Radius
import af.tohid.shop.ui.theme.T
import af.tohid.shop.util.Format
import af.tohid.shop.util.Ids

@Composable
fun DebtorsScreen(addTick: Int = 0, onOpenDebtor: (String) -> Unit) {
    val app = TohidApp.instance
    val scope = rememberCoroutineScope()

    val debtors by app.db.debtors().observeAll().collectAsState(initial = emptyList())
    val txns by app.db.transactions().observeAll().collectAsState(initial = emptyList())

    val balances = remember(debtors, txns) {
        debtors.associate { d ->
            d.id to txns.filter { it.debtorId == d.id }
                .sumOf { if (it.type == "give") it.amount else -it.amount }
        }
    }
    val totalDebt = remember(balances) { balances.values.filter { it > 0 }.sum() }

    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<DebtorEntity?>(null) }
    var isNew by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<Pair<String, Tone>?>(null) }

    LaunchedEffect(addTick) {
        if (addTick > 0) { editing = DebtorEntity(id = Ids.new(), name = ""); isNew = true }
    }

    val visible = remember(debtors, query) {
        val q = query.trim()
        if (q.isEmpty()) debtors
        else debtors.filter { it.name.contains(q, true) || it.phone.contains(q) }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            PageToolbar(
                title = "قرض‌داران",
                subtitle = "مجموع بدهی باز: ${Format.money(totalDebt)} افغانی",
            )
            SearchField(query, { query = it }, "جستجوی قرض‌دار…")
            notice?.let {
                Spacer(Modifier.height(12.dp))
                Notice(it.first, it.second)
            }
        }

        if (visible.isEmpty()) {
            EmptyStateFor(
                hasAny = debtors.isNotEmpty(),
                emptyTitle = "هنوز اطلاعاتی ثبت نشده",
                emptySub = "با افزودن اولین قرض‌دار، فهرست بدهی‌های فروشگاه اینجا نمایش داده می‌شود.",
                onAdd = { editing = DebtorEntity(id = Ids.new(), name = ""); isNew = true },
                addLabel = "+ افزودن قرض‌دار",
                icon = Icons.Outlined.People,
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(visible, key = { it.id }) { d ->
                    DebtorRow(
                        debtor = d,
                        balance = balances[d.id] ?: 0.0,
                        onOpen = { onOpenDebtor(d.id) },
                        onEdit = { editing = d; isNew = false },
                    )
                }
            }
        }
    }

    editing?.let { draft ->
        DebtorSheet(
            draft = draft,
            isNew = isNew,
            onDismiss = { editing = null },
            onSave = { d ->
                scope.launch {
                    when (val r = app.catalog.saveDebtor(d, isNew)) {
                        is OpResult.Refused -> notice = r.message to Tone.Red
                        is OpResult.OkWithWarning -> { notice = r.message to Tone.Orange; editing = null }
                        OpResult.Ok -> { notice = null; editing = null }
                    }
                }
            },
            onDelete = { id ->
                scope.launch {
                    when (val r = app.catalog.deleteDebtor(id)) {
                        is OpResult.Refused -> notice = r.message to Tone.Red
                        is OpResult.OkWithWarning -> { notice = r.message to Tone.Orange; editing = null }
                        OpResult.Ok -> { notice = null; editing = null }
                    }
                }
            },
            warningOf = { id -> app.catalog.debtorDeleteWarning(id) },
        )
    }
}

/* ------------------------------------------------------------------ */

@Composable
private fun DebtorRow(
    debtor: DebtorEntity,
    balance: Double,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
) {
    val tone = if (balance > 0) Tone.Red else Tone.Green
    TCard(Modifier.fillMaxWidth(), padding = 14.dp, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(toneBg(tone)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    debtor.name.trim().take(1),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = toneFg(tone),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    debtor.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = T.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    if (debtor.phone.isBlank()) "بدون شماره" else Format.toFa(debtor.phone),
                    fontSize = 11.5.sp,
                    color = T.muted,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (balance > 0) Format.money(balance) else "تسویه",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = toneFg(tone),
                )
                if (balance > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text("افغانی بدهی", fontSize = 10.5.sp, color = T.muted)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TButton("حساب", onOpen, Modifier.weight(1f), kind = BtnKind.Secondary, small = true)
            TButton("ویرایش", onEdit, Modifier.weight(1f), kind = BtnKind.Secondary, small = true)
        }
    }
}

/* ------------------------------------------------------------------ */

@Composable
private fun DebtorSheet(
    draft: DebtorEntity,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (DebtorEntity) -> Unit,
    onDelete: (String) -> Unit,
    warningOf: suspend (String) -> String?,
) {
    var name by remember(draft.id) { mutableStateOf(draft.name) }
    var phone by remember(draft.id) { mutableStateOf(draft.phone) }
    var notes by remember(draft.id) { mutableStateOf(draft.notes) }
    var confirmDelete by remember { mutableStateOf(false) }
    var warning by remember { mutableStateOf<String?>(null) }

    FormSheet(if (isNew) "افزودن قرض‌دار" else "ویرایش قرض‌دار", onDismiss) {
        TField("نام و تخلص", name, { name = it }, placeholder = "مثلاً احمد رضایی")
        Spacer(Modifier.height(12.dp))
        TField("شماره تماس", phone, { phone = digitsOnly(it) }, placeholder = "۰۷…", numeric = true)
        Spacer(Modifier.height(12.dp))
        TField("یادداشت", notes, { notes = it }, placeholder = "اختیاری")

        FormActions(
            confirmLabel = if (isNew) "ثبت قرض‌دار" else "ذخیره",
            onConfirm = { onSave(draft.copy(name = name, phone = phone.trim(), notes = notes.trim())) },
            onCancel = onDismiss,
            deleteLabel = if (isNew) null else "حذف قرض‌دار",
            onDelete = if (isNew) null else ({ confirmDelete = true }),
        )
    }

    if (confirmDelete) {
        LaunchedEffect(draft.id) { warning = warningOf(draft.id) }
        ConfirmDialog(
            title = "حذف «${draft.name}»؟",
            message = warning
                ?: "این شخص و تمام تراکنش‌هایش برای همیشه از دفتر پاک می‌شوند.",
            confirmLabel = "حذف",
            danger = true,
            onConfirm = { confirmDelete = false; onDelete(draft.id) },
            onDismiss = { confirmDelete = false },
        )
    }
}
