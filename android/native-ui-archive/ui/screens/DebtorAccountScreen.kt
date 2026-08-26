package af.tohid.shop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import af.tohid.shop.TohidApp
import af.tohid.shop.data.db.DebtorEntity
import af.tohid.shop.data.db.TransactionEntity
import af.tohid.shop.data.repo.OpResult
import af.tohid.shop.ui.components.*
import af.tohid.shop.ui.theme.Radius
import af.tohid.shop.ui.theme.T
import af.tohid.shop.util.Format

@Composable
fun DebtorAccountScreen(debtorId: String) {
    val app = TohidApp.instance
    val scope = rememberCoroutineScope()

    var debtor by remember { mutableStateOf<DebtorEntity?>(null) }
    val txns by app.db.transactions().observeForDebtor(debtorId)
        .collectAsState(initial = emptyList<TransactionEntity>())

    LaunchedEffect(debtorId) { debtor = app.db.debtors().byId(debtorId) }

    val balance = remember(txns) {
        txns.sumOf { if (it.type == "give") it.amount else -it.amount }
    }
    val given = remember(txns) { txns.filter { it.type == "give" }.sumOf { it.amount } }
    val received = remember(txns) { txns.filter { it.type == "receive" }.sumOf { it.amount } }

    var sheetType by remember { mutableStateOf<String?>(null) }   // give | receive
    var notice by remember { mutableStateOf<Pair<String, Tone>?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(T.surface)
            .verticalScrollCompat()
            .padding(16.dp),
    ) {
        PageToolbar(
            title = debtor?.name ?: "حساب قرض‌دار",
            subtitle = debtor?.phone?.takeIf { it.isNotBlank() }?.let { Format.toFa(it) }
                ?: "حساب و تراکنش‌ها",
        )

        TCard(Modifier.fillMaxWidth(), padding = 18.dp) {
            Text("بدهی فعلی", fontSize = 12.5.sp, color = T.muted, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    Format.money(balance.coerceAtLeast(0.0)),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (balance > 0) T.danger else T.success,
                )
                Spacer(Modifier.width(6.dp))
                Text("افغانی", fontSize = 13.sp, color = T.muted, modifier = Modifier.padding(bottom = 4.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TButton("ثبت بدهی", { sheetType = "give" }, Modifier.weight(1f), kind = BtnKind.Secondary)
                TButton("دریافت پول", { sheetType = "receive" }, Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(12.dp))
        StatRow {
            StatCard(
                "مجموع بدهی داده‌شده", Format.money(given),
                Icons.Outlined.ArrowUpward, Tone.Red, "افغانی", Modifier.weight(1f),
            )
            StatCard(
                "مجموع دریافت‌شده", Format.money(received),
                Icons.Outlined.ArrowDownward, Tone.Green, "افغانی", Modifier.weight(1f),
            )
        }

        notice?.let {
            Spacer(Modifier.height(12.dp))
            Notice(it.first, it.second)
        }

        Spacer(Modifier.height(16.dp))
        if (txns.isEmpty()) {
            TCard(Modifier.fillMaxWidth(), padding = 0.dp) {
                EmptyState(
                    icon = Icons.Outlined.ReceiptLong,
                    title = "هنوز تراکنشی ثبت نشده",
                    subtitle = "بدهی جدید یا دریافت پول را از دکمه‌های بالا ثبت کنید.",
                )
            }
        } else {
            TPanel("تراکنش‌ها", Modifier.fillMaxWidth()) {
                txns.forEachIndexed { i, t ->
                    if (i > 0) Divider(Modifier.padding(vertical = 4.dp))
                    TxnRow(t)
                }
            }
        }

        Spacer(Modifier.height(90.dp))
    }

    sheetType?.let { type ->
        AmountSheet(
            title = if (type == "give") "ثبت بدهی جدید" else "ثبت دریافت پول",
            confirmLabel = if (type == "give") "ثبت بدهی" else "ثبت دریافت",
            hint = if (type == "give") "این مبلغ به بدهی طرف اضافه می‌شود."
                   else "این مبلغ از بدهی طرف کم می‌شود.",
            onDismiss = { sheetType = null },
            onConfirm = { amount, note ->
                scope.launch {
                    when (val r = app.catalog.addTransaction(debtorId, type, amount, note)) {
                        is OpResult.Refused -> notice = r.message to Tone.Red
                        is OpResult.OkWithWarning -> { notice = r.message to Tone.Orange; sheetType = null }
                        OpResult.Ok -> { notice = null; sheetType = null }
                    }
                }
            },
        )
    }
}

/* ------------------------------------------------------------------ */

@Composable
private fun TxnRow(t: TransactionEntity) {
    val give = t.type == "give"
    val tone = if (give) Tone.Red else Tone.Green
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .background(toneBg(tone)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (give) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                contentDescription = null,
                tint = toneFg(tone),
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (give) "بدهی داده شد" else "پول دریافت شد",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = T.text,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (t.notes.isBlank()) Format.shortDate(t.date) else "${Format.shortDate(t.date)} — ${t.notes}",
                fontSize = 11.sp,
                color = T.muted,
            )
        }
        Text(
            (if (give) "+" else "−") + Format.money(t.amount),
            fontSize = 13.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = toneFg(tone),
        )
    }
}

/** برگه‌ی ساده‌ی «مبلغ + یادداشت» — در حساب قرض‌دار و پرداخت به تأمین‌کننده. */
@Composable
fun AmountSheet(
    title: String,
    confirmLabel: String,
    hint: String,
    onDismiss: () -> Unit,
    onConfirm: (Double, String) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    FormSheet(title, onDismiss) {
        Notice(hint, Tone.Blue)
        Spacer(Modifier.height(14.dp))
        TField("مبلغ (افغانی)", amount, { amount = digitsOnly(it) }, numeric = true, placeholder = "۰")
        Spacer(Modifier.height(12.dp))
        TField("یادداشت", note, { note = it }, placeholder = "اختیاری")
        FormActions(
            confirmLabel = confirmLabel,
            onConfirm = { onConfirm(amount.toDoubleOrNull() ?: 0.0, note.trim()) },
            onCancel = onDismiss,
        )
    }
}
