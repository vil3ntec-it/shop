package ir.vil3ntec.tohid.ui.v3.money

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.data.DebtBook
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.data.ReportCsv
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.invoiceLabel
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.print.ReportPrint
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Collapse
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Springs
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.v3Pressable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/* ------------------------------ کشو ------------------------------ */

private const val PREFS = "tohid-debtor-detail"
private const val KEY_DRAWER = "drawer-open"

/** باز/بسته بودنِ کشو روی همین دستگاه می‌ماند — کسی که بسته‌اش کرده، هر بار نمی‌بندد (#354) */
@Stable
internal class DrawerState(initial: Boolean, private val persist: (Boolean) -> Unit) {
  var open by mutableStateOf(initial)
    private set

  fun toggle() {
    open = !open
    persist(open)
  }
}

@Composable
internal fun rememberDrawerState(context: Context, forced: Boolean? = null): DrawerState {
  val prefs = remember(context) { runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }.getOrNull() }
  val initial = rememberSaveable { forced ?: runCatching { prefs?.getBoolean(KEY_DRAWER, false) }.getOrNull() ?: false }
  return remember(prefs) { DrawerState(initial) { v -> runCatching { prefs?.edit()?.putBoolean(KEY_DRAWER, v)?.apply() } } }
}

/**
 *  کشوی کارها و اطلاعات — بسته: یک ردیف با خلاصه (شماره · «۶ روز بدهکار»)؛ باز: دکمه‌های کارها
 *  چهارتا در هر ردیف و زیرشان «چند وقت بدهکار»، سررسیدها، سقفِ اعتبار و یادداشت. `onToggle`
 *  تهی (چیدمانِ پهن) ⇒ همیشه باز، بی ردیفِ سرِ کشو.
 */
@Composable
internal fun DebtorDrawer(
  account: DebtorEngine.Account,
  book: DebtBook.Book?,
  actions: List<DebtorLedgerModel.Action>,
  open: Boolean,
  onToggle: (() -> Unit)?,
  onAction: (DebtorLedgerModel.Action) -> Unit,
) {
  V3Card(padding = PaddingValues(0.dp)) {
    if (onToggle != null) {
      val turn by animateFloatAsState(if (open) 180f else 0f, V3Springs.ui(), label = "drawerChevron")
      V3ListRow(
        title = StrMoney.actionsAndInfo,
        subtitle = DebtorLedgerModel.summary(account.debtor.phone, account.since),
        subtitleLines = 1,
        icon = Icons.Outlined.Tune,
        minHeight = 56.dp,
        trailing = {
          Icon(
            Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = PhoneV3.colors.chevron,
            modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = turn },
          )
        },
        onClick = onToggle,
      )
    }
    V3Collapse(visible = open) {
      Column {
        if (onToggle != null) V3Divider()
        Column(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          actions.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
              row.forEach { a -> ActionButton(a, Modifier.weight(1f)) { onAction(a) } }
              repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
          }
        }
        DrawerInfo(account, book)
      }
    }
  }
}

@Composable
private fun ActionButton(action: DebtorLedgerModel.Action, modifier: Modifier, onClick: () -> Unit) {
  val c = PhoneV3.colors
  val (icon, label) = actionLook(action)
  val danger = action == DebtorLedgerModel.Action.DELETE
  Column(
    modifier.clip(RoundedCornerShape(PhoneV3.tileRadius)).v3Pressable(label = label, scale = 0.92f, haptic = danger, onClick = onClick).padding(vertical = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(if (danger) LedgerTone.OWE.wash() else c.tile), contentAlignment = Alignment.Center) {
      Icon(icon, contentDescription = null, tint = if (danger) LedgerTone.OWE.ink() else c.icon, modifier = Modifier.size(21.dp))
    }
    Spacer(Modifier.height(5.dp))
    Text(label, style = v3Text(12, FontWeight.Medium, 16), color = if (danger) LedgerTone.OWE.ink() else c.ink, textAlign = TextAlign.Center, maxLines = 2)
  }
}

private fun actionLook(action: DebtorLedgerModel.Action): Pair<ImageVector, String> = when (action) {
  DebtorLedgerModel.Action.CALL -> Icons.Outlined.Call to StrMoney.call
  DebtorLedgerModel.Action.SMS -> Icons.Outlined.Sms to StrMoney.sms
  DebtorLedgerModel.Action.WHATSAPP -> Icons.AutoMirrored.Outlined.Chat to StrMoney.whatsapp
  DebtorLedgerModel.Action.EDIT -> Icons.Outlined.Edit to StrMoney.edit
  DebtorLedgerModel.Action.STATEMENT_CSV -> Icons.Outlined.FileDownload to StrMoney.statementCsv
  DebtorLedgerModel.Action.PRINT -> Icons.Outlined.Print to StrMoney.print
  DebtorLedgerModel.Action.DELETE -> Icons.Outlined.DeleteOutline to StrMoney.delete
}

/** «چند وقت بدهکار»، سررسیدها، سقفِ اعتبار — هر کدام قرصِ وضعیت (متن + رنگ + نقطه)؛ و یادداشت */
@Composable
private fun DrawerInfo(account: DebtorEngine.Account, book: DebtBook.Book?) {
  val lines = remember(account, book) { DebtorLedgerModel.info(account, book) }
  val notes = account.debtor.notes
  if (lines.isEmpty() && notes.isBlank()) return
  Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    lines.forEach { line ->
      V3StatusPill(
        when (line.tone) {
          DebtorLedgerModel.InfoTone.OWE -> V3Status.CANCELLED
          DebtorLedgerModel.InfoTone.INFO -> V3Status.IN_DELIVERY
          DebtorLedgerModel.InfoTone.NEUTRAL -> V3Status.INACTIVE
        },
        line.text,
      )
    }
    if (notes.isNotBlank()) Text("${StrMoney.notes}: $notes", style = v3Text(13, FontWeight.Normal, 19), color = PhoneV3.colors.inkMuted)
  }
}

/* ------------------------------ کارهای کشو ------------------------------ */

/** کارهای کشو روی همین دستگاه: شماره‌گیر، پیامک/واتساپ با متنِ یادآوری، صورت‌حساب و چاپ */
@Stable
internal class DebtorTasks(private val run: (DebtorLedgerModel.Action) -> Unit) {
  fun run(action: DebtorLedgerModel.Action) = run.invoke(action)
}

@Composable
internal fun rememberDebtorTasks(d: ShopData, account: DebtorEngine.Account, actions: DebtorLedgerActions): DebtorTasks {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val latest by rememberUpdatedState(Triple(d, account, actions))
  //  صورت‌حساب با انتخاب‌گرِ خودِ سیستم ذخیره می‌شود — بی اجازهٔ حافظه
  var pendingCsv by remember { mutableStateOf<ReportCsv.Sheet?>(null) }
  val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
    val sheet = pendingCsv
    pendingCsv = null
    if (uri == null || sheet == null) return@rememberLauncherForActivityResult
    scope.launch {
      runCatching {
        withContext(Dispatchers.IO) {
          context.contentResolver.openOutputStream(uri)?.use { out -> out.write(ReportCsv.text(sheet).toByteArray(Charsets.UTF_8)) }
        }
      }
    }
  }
  return remember(context) {
    DebtorTasks { action ->
      val (data, acc, acts) = latest
      val debtor = acc.debtor
      fun open(uri: String, intent: String = android.content.Intent.ACTION_VIEW) {
        runCatching { context.startActivity(android.content.Intent(intent, android.net.Uri.parse(uri))) }
      }
      val number = DebtorLedgerModel.dialable(debtor.phone)
      val message = DebtorLedgerModel.reminder(debtor.name, acc.balance)
      when (action) {
        DebtorLedgerModel.Action.CALL -> open("tel:$number", android.content.Intent.ACTION_DIAL)
        DebtorLedgerModel.Action.SMS -> open("smsto:$number?body=" + android.net.Uri.encode(message))
        DebtorLedgerModel.Action.WHATSAPP -> open("https://wa.me/${DebtorLedgerModel.whatsappNumber(debtor.phone)}?text=" + android.net.Uri.encode(message))
        DebtorLedgerModel.Action.EDIT -> acts.onEdit()
        DebtorLedgerModel.Action.STATEMENT_CSV -> {
          val sheet = ReportCsv.statement(data, debtor.id, todayIso())
          pendingCsv = sheet
          saveCsv.launch("${sheet.name}.csv")
        }
        DebtorLedgerModel.Action.PRINT -> ReportPrint.print(context, ReportCsv.statement(data, debtor.id, todayIso()), StrMoney.printTitle(debtor.name))
        DebtorLedgerModel.Action.DELETE -> acts.onDelete()
      }
    }
  }
}

/* ------------------------------ فاکتورها ------------------------------ */

/** چند فاکتورِ آخرِ مشتری — بقیه در «فروش‌ها» */
private const val SALES_SHOWN = 20

/** فاکتورهای همین مشتری (خودِ خرید، نه فقط ردیفِ بدهی) — هر ردیف ⇐ همان فاکتور در «فروش‌ها» */
@Composable
internal fun DebtorInvoices(account: DebtorEngine.Account, onOpen: (String) -> Unit, onOlder: () -> Unit) {
  val older = account.sales.size - SALES_SHOWN
  V3Section(
    title = StrMoney.invoices(account.sales.size),
    action = if (older > 0) StrMoney.olderInvoices(older) else null,
    onAction = if (older > 0) onOlder else null,
  ) {
    account.sales.take(SALES_SHOWN).forEachIndexed { i, sale ->
      if (i > 0) V3Divider(start = 64.dp)
      val cancelled = sale.status == "cancelled"
      V3ListRow(
        title = StrMoney.invoiceNo(sale.invoiceLabel() ?: "—") + if (cancelled) " (${StrMoney.cancelled})" else "",
        subtitle = MoneyText.date(sale.date),
        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
        trailing = {
          Column(horizontalAlignment = Alignment.End) {
            Text(money(sale.finalTotal), style = v3Text(14, FontWeight.SemiBold, 20), color = if (cancelled) PhoneV3.colors.inkMuted else PhoneV3.colors.ink)
            if (sale.remaining > 0 && !cancelled) {
              Text(StrMoney.remaining(money(sale.remaining)), style = v3Text(12, FontWeight.Medium, 17), color = LedgerTone.OWE.ink())
            }
          }
        },
        onClick = { onOpen(sale.id) },
      )
    }
  }
}
