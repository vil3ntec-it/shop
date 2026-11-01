package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.ui.v3.money.DebtorLedgerModel

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.DebtBook
import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.data.ReportCsv
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.invoiceLabel
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.formatMillis
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.print.ReportPrint
import ir.vil3ntec.tohid.print.StatementImage
import ir.vil3ntec.tohid.ui.v3.LocalV3Toasts
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.spanText
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Debt
import ir.vil3ntec.tohid.ui.theme.PhoneV3DebtColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 *  حسابِ یک قرض‌دار — گوشی نسخهٔ ۳، از صفر (دستورِ صاحب محصول، owner-feedback-2).
 *
 *  «ببین چقدر جا اشغال کرده؛ همهٔ اینایی که آبی کشیدم توی یک کادرِ کشویی بذار»:
 *  سربرگِ فشرده (برگشت، نام، شماره، «⋯»)؛ یک کشوی تاشو با همهٔ کارها (زنگ، پیامک،
 *  واتساپ، ویرایش، صورت‌حساب، چاپ، حذف) و «چند روز بدهکار»؛ سه عدد در یک ردیف؛
 *  تراکنش‌ها بی‌فاصله زیرش با سرخطِ چسبانِ روز؛ و «ثبتِ تراکنش» نوارِ پایین که
 *  برگه می‌شود. منطق همان `DebtorEngine`/`DebtBook`؛ این‌جا فقط کشیده می‌شود.
 */

/** فقط برای عکسِ خودکارِ پیش‌نمایشِ گوشی (`DevShot`)، مثلِ `PhoneCartShot` — در کارِ عادی دست نمی‌خورد */
object DebtorDetailShot {
  /** حسابِ این شناسه باز شود */
  val open = mutableStateOf<String?>(null)
  /** کشو باز/بسته (تهی = همان که کاربر گذاشته) */
  val drawer = mutableStateOf<Boolean?>(null)
  /** برگهٔ «ثبتِ تراکنش» باز باشد */
  val composer = mutableStateOf(false)
  /** برگهٔ جزئیاتِ این تراکنش باز باشد */
  val txSheet = mutableStateOf<String?>(null)
}

private const val PREFS = "tohid-debtor-detail"
private const val KEY_DRAWER = "drawer-open"

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DebtorDetailScreen(
  d: ShopData,
  account: DebtorEngine.Account,
  /** ردیف‌هایی که «حذف با برگرداندن» در انتظار دارند */
  hiddenTx: Set<String>,
  onBack: () -> Unit,
  onCompose: (DebtorEngine.Kind) -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onDeleteTx: (String) -> Unit,
  /** رسیدِ چاپی/فرستادنی */
  onOpenTx: (String) -> Unit,
  /** گذاشتن/عوض کردنِ سررسیدِ یک برد — همان پنجرهٔ قبلی */
  onDue: (String) -> Unit,
) {
  val c = PhoneV3.colors
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val debtor = account.debtor
  //  دفترِ همین مشتری: اقلام، تخصیصِ پرداخت، سررسید و ماندهٔ پس از هر ردیف
  val book = remember(d, debtor.id) { DebtBook.book(d, debtor.id, todayIso()) }
  val rows = remember(account.transactions, hiddenTx) { DebtorLedgerModel.rows(account.transactions, hiddenTx) }
  //  برگهٔ جزئیاتِ یک ردیف (زدنِ ردیف)
  var sheetTx by rememberSaveable { mutableStateOf<String?>(null) }
  val shotTx = DebtorDetailShot.txSheet.value
  LaunchedEffect(shotTx) { if (shotTx != null) sheetTx = shotTx }
  val totals = DebtorLedgerModel.totals(account)

  //  کشو: بازی/بستگی‌اش روی دستگاه می‌ماند — کسی که بسته‌اش کرده، هر بار نمی‌بندد
  val prefs = remember(context) { runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }.getOrNull() }
  var drawerOpen by rememberSaveable { mutableStateOf(runCatching { prefs?.getBoolean(KEY_DRAWER, false) }.getOrNull() ?: false) }
  val forced = DebtorDetailShot.drawer.value
  LaunchedEffect(forced) { if (forced != null) drawerOpen = forced }
  fun toggleDrawer() {
    drawerOpen = !drawerOpen
    runCatching { prefs?.edit()?.putBoolean(KEY_DRAWER, drawerOpen)?.apply() }
  }

  //  صورت‌حساب با انتخابگرِ خودِ سیستم ذخیره می‌شود — بی اجازهٔ حافظه
  var pendingCsv by remember { mutableStateOf<ReportCsv.Sheet?>(null) }
  val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
    val sheet = pendingCsv
    pendingCsv = null
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

  fun open(uri: String, action: String = android.content.Intent.ACTION_VIEW) {
    runCatching { context.startActivity(android.content.Intent(action, android.net.Uri.parse(uri))) }
  }

  //  CRM-05: نتیجهٔ «تصویرِ صورت‌حساب» — پیامِ پوسته
  val toasts = LocalV3Toasts.current

  fun run(action: DebtorLedgerModel.Action) {
    val number = DebtorLedgerModel.dialable(debtor.phone)
    val message = DebtorLedgerModel.reminder(debtor.name, account.balance)
    when (action) {
      DebtorLedgerModel.Action.CALL -> open("tel:$number", android.content.Intent.ACTION_DIAL)
      DebtorLedgerModel.Action.SMS -> open("smsto:$number?body=" + android.net.Uri.encode(message))
      DebtorLedgerModel.Action.WHATSAPP ->
        open("https://wa.me/${DebtorLedgerModel.whatsappNumber(debtor.phone)}?text=" + android.net.Uri.encode(message))
      DebtorLedgerModel.Action.EDIT -> onEdit()
      DebtorLedgerModel.Action.STATEMENT_CSV -> {
        val sheet = ReportCsv.statement(d, debtor.id, todayIso())
        pendingCsv = sheet
        saveCsv.launch("${sheet.name}.csv")
      }
      DebtorLedgerModel.Action.PRINT ->
        ReportPrint.print(context, ReportCsv.statement(d, debtor.id, todayIso()), StrDebtor.printTitle(debtor.name))
      DebtorLedgerModel.Action.STATEMENT_IMAGE ->
        scope.launch { toasts?.show(StatementImage.share(context, d, debtor.id, todayIso())) }
      DebtorLedgerModel.Action.DELETE -> onDelete()
    }
  }

  val actions = DebtorLedgerModel.actions(
    debtor.phone, account.transactions.isNotEmpty(),
    image = ir.vil3ntec.tohid.data.ShopPolicy.crmReports(context),
  )
  val wide = screenSize() != Screen.PHONE

  V3MaterialColors {
    Box(Modifier.fillMaxSize().background(c.bg)) {
      if (wide) {
        //  تبلت: ستونِ آغاز سربرگ و کشوی باز و عددها؛ ستونِ پایان تراکنش‌ها
        Row(Modifier.fillMaxSize()) {
          Column(Modifier.width(400.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            DetailHeader(debtor.name, debtor.phone, drawerOpen, onBack, ::toggleDrawer)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              ActionDrawer(account, book, actions, open = true, onToggle = null, onAction = ::run)
              TotalsCard(totals)
            }
          }
          Box(Modifier.weight(1f).fillMaxHeight()) {
            LazyColumn(
              Modifier.fillMaxSize(),
              contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              transactionItems(rows, book, account, onCompose, onDeleteTx, onShow = { sheetTx = it }, onReceipt = onOpenTx)
            }
            ComposerBar(Modifier.align(Alignment.BottomCenter), onCompose)
          }
        }
      } else {
        Column(Modifier.fillMaxSize()) {
          DetailHeader(debtor.name, debtor.phone, drawerOpen, onBack, ::toggleDrawer)
          Box(Modifier.weight(1f)) {
            LazyColumn(
              Modifier.fillMaxSize(),
              contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 104.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              item(key = "drawer") {
                Box(Modifier.padding(bottom = 4.dp)) {
                  ActionDrawer(account, book, actions, open = drawerOpen, onToggle = ::toggleDrawer, onAction = ::run)
                }
              }
              item(key = "totals") { TotalsCard(totals) }
              transactionItems(rows, book, account, onCompose, onDeleteTx, onShow = { sheetTx = it }, onReceipt = onOpenTx)
            }
            ComposerBar(Modifier.align(Alignment.BottomCenter), onCompose)
          }
        }
      }
    }
  }

  sheetTx?.let { id ->
    val tx = account.transactions.find { it.id == id }
    if (tx == null || id in hiddenTx) {
      LaunchedEffect(id) { sheetTx = null; DebtorDetailShot.txSheet.value = null }
    } else {
      TxDetailSheet(
        tx = tx,
        entry = book?.entry(id),
        book = book,
        onDismiss = { sheetTx = null; DebtorDetailShot.txSheet.value = null },
        onReceipt = { sheetTx = null; onOpenTx(id) },
        onDue = if (tx.type == "give") ({ sheetTx = null; onDue(id) }) else null,
        onDelete = { sheetTx = null; onDeleteTx(id) },
      )
    }
  }
}

/* ------------------------------ سربرگ ------------------------------ */

/** سربرگِ فشرده: برگشت در آغاز، نام و شماره وسط، «⋯» (کشو) در پایان */
@Composable
private fun DetailHeader(name: String, phone: String, drawerOpen: Boolean, onBack: () -> Unit, onMore: () -> Unit) {
  val c = PhoneV3.colors
  Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
    Box(Modifier.align(Alignment.CenterStart)) { V3BackButton(onBack) }
    Column(Modifier.padding(horizontal = 56.dp).semantics { heading() }, horizontalAlignment = Alignment.CenterHorizontally) {
      Text(name.ifBlank { "—" }, style = v3Text(18, FontWeight.Bold, 24), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (phone.isNotBlank()) {
        //  شماره چپ‌به‌راست، حتی در صفحهٔ راست‌به‌چپ
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
          Text(ir.vil3ntec.tohid.LocaleText.phoneDisplay(phone), style = v3Text(13, FontWeight.Normal, 18), color = c.inkMuted, maxLines = 1)
        }
      }
    }
    Box(Modifier.align(Alignment.CenterEnd)) {
      val turn by animateFloatAsState(if (drawerOpen) 90f else 0f, if (Motion.enabled) spring(stiffness = 500f) else tween(0), label = "more")
      Box(Modifier.rotate(turn)) {
        V3RoundButton(Icons.Outlined.MoreHoriz, StrDebtor.actionsAndInfo, onClick = onMore)
      }
    }
  }
}

/* ------------------------------ کشو ------------------------------ */

/**
 *  کشوی کارها و اطلاعات — بسته: یک ردیفِ کوتاه با خلاصه («۶ روز بدهکار»)؛ باز:
 *  دکمه‌های کارها در ردیف‌های چهارتایی و زیرشان «چند وقت بدهکار»، سررسیدها،
 *  سقفِ اعتبار و یادداشت. باز و بسته شدن با فنر.
 */
@Composable
private fun ActionDrawer(
  account: DebtorEngine.Account,
  book: DebtBook.Book?,
  actions: List<DebtorLedgerModel.Action>,
  open: Boolean,
  onToggle: (() -> Unit)?,
  onAction: (DebtorLedgerModel.Action) -> Unit,
) {
  val c = PhoneV3.colors
  val since = account.since
  val summary = if (since != null) StrDebtor.inDebtShort(spanText(since)) else StrDebtor.noDebt
  V3Card(padding = PaddingValues(0.dp)) {
    if (onToggle != null) {
      Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).v3Press(c.pressed, label = StrDebtor.actionsAndInfo, onClick = onToggle).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(c.iconTile), contentAlignment = Alignment.Center) {
          Icon(Icons.Outlined.Tune, contentDescription = null, tint = c.icon, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
          Text(StrDebtor.actionsAndInfo, style = v3Text(15, FontWeight.SemiBold, 20), color = c.ink)
          Text(summary, style = v3Text(12, FontWeight.Normal, 17), color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val turn by animateFloatAsState(if (open) 180f else 0f, if (Motion.enabled) spring(stiffness = 500f) else tween(0), label = "chevron")
        Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = c.chevron, modifier = Modifier.size(22.dp).rotate(turn))
      }
    }
    AnimatedVisibility(
      visible = open,
      enter = if (Motion.enabled) expandVertically(spring(dampingRatio = 0.86f, stiffness = 420f)) + fadeIn(tween(180)) else EnterTransition.None,
      exit = if (Motion.enabled) shrinkVertically(spring(dampingRatio = 0.9f, stiffness = 520f)) + fadeOut(tween(120)) else ExitTransition.None,
    ) {
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
  val dc = PhoneV3Debt.colors
  val (icon, label) = when (action) {
    DebtorLedgerModel.Action.CALL -> Icons.Outlined.Call to StrDebtor.call
    DebtorLedgerModel.Action.SMS -> Icons.Outlined.Sms to StrDebtor.sms
    DebtorLedgerModel.Action.WHATSAPP -> Icons.AutoMirrored.Outlined.Chat to StrDebtor.whatsapp
    DebtorLedgerModel.Action.EDIT -> Icons.Outlined.Edit to StrDebtor.edit
    DebtorLedgerModel.Action.STATEMENT_CSV -> Icons.Outlined.FileDownload to StrDebtor.statementCsv
    DebtorLedgerModel.Action.PRINT -> Icons.Outlined.Print to StrDebtor.print
    DebtorLedgerModel.Action.STATEMENT_IMAGE -> Icons.Outlined.Image to StrDebtor.statementImage
    DebtorLedgerModel.Action.DELETE -> Icons.Outlined.DeleteOutline to StrDebtor.delete
  }
  val danger = action == DebtorLedgerModel.Action.DELETE
  Column(
    modifier.clip(RoundedCornerShape(14.dp)).v3PressScale(onClick, label).padding(vertical = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      Modifier.size(44.dp).clip(CircleShape).background(if (danger) dc.oweTint else c.iconTile),
      contentAlignment = Alignment.Center,
    ) {
      Icon(icon, contentDescription = null, tint = if (danger) dc.owe else c.icon, modifier = Modifier.size(21.dp))
    }
    Spacer(Modifier.height(5.dp))
    Text(
      label,
      style = v3Text(12, FontWeight.Medium, 16),
      color = if (danger) dc.owe else c.ink,
      textAlign = TextAlign.Center,
      maxLines = 2,
    )
  }
}

/** «چند وقت بدهکار»، سررسیدها، سقفِ اعتبار و یادداشت — هر کدام یک قرصِ وضعیت (متن + رنگ + نقطه) */
@Composable
private fun DrawerInfo(account: DebtorEngine.Account, book: DebtBook.Book?) {
  val c = PhoneV3.colors
  val dc = PhoneV3Debt.colors
  val pills = buildList {
    account.since?.let { since -> add(Triple(StrDebtor.daysInDebt(spanText(since), formatMillis(since)), dc.owe, dc.oweTint)) }
    book?.let { b ->
      if (b.overdue.isNotEmpty()) {
        add(Triple("${b.overdue.size} برد از سررسید گذشته — ${DebtBook.show(b.overdueAmount)} افغانی", dc.owe, dc.oweTint))
      }
      b.nextDue?.let { add(Triple("سررسیدِ بعدی ${formatDate(it.dueDate!!)} — ${DebtBook.show(it.open)} افغانی", dc.info, dc.infoTint)) }
      b.credit?.let { cr ->
        if (cr.over) add(Triple("${DebtBook.show(cr.excess)} افغانی بیش از سقفِ اعتبار (${DebtBook.show(cr.limit)})", dc.owe, dc.oweTint))
        else add(Triple("سقفِ اعتبار ${DebtBook.show(cr.limit)} — تا سقف ${DebtBook.show(cr.headroom)} افغانی", dc.onPill, dc.pill))
      }
    }
  }
  val notes = account.debtor.notes
  if (pills.isEmpty() && notes.isBlank()) return
  Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    pills.forEach { (text, fg, bg) -> StatusPill(text, fg, bg) }
    if (notes.isNotBlank()) {
      Text("${StrDebtor.notes}: $notes", style = v3Text(13, FontWeight.Normal, 19), color = c.inkMuted)
    }
  }
}

/** قرصِ وضعیت (VILL3N §۸): نقطه + متن به رنگِ وضعیت روی زمینهٔ همان وضعیت */
@Composable
internal fun StatusPill(text: String, fg: Color, bg: Color, icon: ImageVector? = null) {
  Row(
    Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 5.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(13.dp))
    else Box(Modifier.size(6.dp).clip(CircleShape).background(fg))
    Spacer(Modifier.width(6.dp))
    Text(text, style = v3Text(12, FontWeight.SemiBold, 17), color = fg)
  }
}

/* ------------------------------ سه عدد ------------------------------ */

/**
 *  کل برد · کل رسید · الباقی — یک کارت، سه ستونِ هم‌وزن، عددها با شمارشِ نرم.
 *  صفر خنثی است (`inkMuted`): نه قرمزِ بدهی، نه سبزِ رسید.
 */
@Composable
private fun TotalsCard(t: DebtorLedgerModel.Totals) {
  val c = PhoneV3.colors
  val dc = PhoneV3Debt.colors
  V3Card(radius = 16.dp, padding = PaddingValues(vertical = 9.dp)) {
    Row(Modifier.fillMaxWidth()) {
      TotalCell(StrDebtor.given, t.given, if (DebtorLedgerModel.isZero(t.given)) c.inkMuted else dc.owe, Modifier.weight(1f))
      TotalCell(StrDebtor.received, t.received, if (DebtorLedgerModel.isZero(t.received)) c.inkMuted else dc.settled, Modifier.weight(1f))
      TotalCell(
        StrDebtor.balance,
        kotlin.math.abs(t.balance),
        when {
          t.settled -> c.inkMuted
          t.owes -> dc.owe
          else -> dc.settled
        },
        Modifier.weight(1f),
        settled = t.settled,
      )
    }
  }
}

@Composable
private fun TotalCell(label: String, value: Double, tint: Color, modifier: Modifier, settled: Boolean = false) {
  val c = PhoneV3.colors
  val shown = rememberCountUp(value)
  Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    Text(label, style = v3Text(11, FontWeight.Medium, 15), color = c.inkMuted, maxLines = 1)
    Spacer(Modifier.height(1.dp))
    Text(
      if (settled) StrDebtor.settled else money(shown),
      style = v3Text(17, FontWeight.Bold, 23),
      color = tint,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

/** شمارشِ نرمِ عدد از صفر تا مقدار (۶۵۰ میلی‌ثانیه)؛ «حذفِ انیمیشن‌ها» ⇒ همان عدد */
@Composable
private fun rememberCountUp(target: Double): Double {
  val anim = remember { Animatable(if (Motion.enabled) 0f else target.toFloat()) }
  LaunchedEffect(target) {
    if (Motion.enabled) anim.animateTo(target.toFloat(), tween(650, easing = FastOutSlowInEasing)) else anim.snapTo(target.toFloat())
  }
  //  پس از پایان همان عددِ دقیق (Float رقم‌های عددِ بزرگ را گرد می‌کند)
  return if (anim.isRunning) anim.value.toDouble() else target
}

/* ------------------------------ تراکنش‌ها ------------------------------ */

/**
 *  «تراکنش‌ها · N» در یک خطِ فشرده، بعد ردیف‌های یک‌خطی (تازه‌ترین اول). سرخطِ
 *  روز نیست — تاریخ داخلِ خودِ ردیف است. زدنِ ردیف ⇐ برگهٔ جزئیات.
 */
private fun LazyListScope.transactionItems(
  rows: List<DebtTransaction>,
  book: DebtBook.Book?,
  account: DebtorEngine.Account,
  onCompose: (DebtorEngine.Kind) -> Unit,
  onDeleteTx: (String) -> Unit,
  onShow: (String) -> Unit,
  onReceipt: (String) -> Unit,
) {
  item(key = "tx-title") {
    val c = PhoneV3.colors
    Text(
      "${StrDebtor.transactions} · ${ir.vil3ntec.tohid.plain(rows.size)}",
      style = v3Text(15, FontWeight.Bold, 20),
      color = c.ink,
      modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 2.dp).semantics { heading() },
    )
  }
  if (rows.isEmpty()) {
    item(key = "tx-empty") { EmptyTransactions { onCompose(DebtorEngine.Kind.GIVE) } }
  }
  itemsIndexed(rows, key = { _, tx -> tx.id }) { i, tx ->
    val p = rememberAppear(i.coerceAtMost(10) * 25)
    Box(Modifier.appear(p).then(if (Motion.enabled) Modifier.animateItem() else Modifier)) {
      TxRow(tx, book?.entry(tx.id), onShow = { onShow(tx.id) }, onReceipt = { onReceipt(tx.id) }, onDelete = { onDeleteTx(tx.id) })
    }
  }
  if (account.sales.isNotEmpty()) {
    item(key = "sales") { InvoicesCard(account) }
  }
}

/**
 *  ردیفِ یک‌خطیِ تراکنش (~۴۶dp): همهٔ ردیف به رنگِ نوعش — برد قرمز، رسید سبز —
 *  و فقط سه چیز: تاریخ، مبلغ با علامت، و ماندهٔ پس از همین ردیف. بقیه در برگه.
 *  زدن ⇐ برگهٔ جزئیات؛ لمسِ طولانی ⇐ منو؛ کشیدن به سمتِ آغاز ⇐ حذف با برگرداندن.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TxRow(tx: DebtTransaction, entry: DebtBook.Entry?, onShow: () -> Unit, onReceipt: () -> Unit, onDelete: () -> Unit) {
  val c = PhoneV3.colors
  val dc = PhoneV3Debt.colors
  val give = tx.type == "give"
  val tint = if (give) dc.owe else dc.settled
  val wash = if (give) dc.oweTint else dc.settledTint
  val shape = RoundedCornerShape(12.dp)
  var menu by remember { mutableStateOf(false) }
  @Suppress("DEPRECATION")
  val swipe = rememberSwipeToDismissBoxState(
    confirmValueChange = { value ->
      if (value == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
    },
  )
  SwipeToDismissBox(
    state = swipe,
    enableDismissFromStartToEnd = false,
    backgroundContent = {
      Box(Modifier.fillMaxSize().clip(shape).background(c.tile).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterEnd) {
        Icon(Icons.Outlined.DeleteOutline, contentDescription = StrDebtor.delete, tint = dc.owe, modifier = Modifier.size(20.dp))
      }
    },
  ) {
    Box {
      Row(
        Modifier
          .fillMaxWidth()
          .height(46.dp)
          .clip(shape)
          .background(wash)
          .combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
            onClickLabel = StrDebtor.details,
            onLongClick = { menu = true },
            onClick = onShow,
          )
          .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(if (give) Icons.Outlined.NorthEast else Icons.Outlined.SouthWest, contentDescription = if (give) StrDebtor.giveTag else StrDebtor.receiveTag, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Text(
          if (tx.date.isBlank()) DebtorLedgerModel.UNDATED else formatDate(tx.date),
          style = v3Text(12, FontWeight.Normal, 16),
          color = c.inkMuted,
          maxLines = 1,
          modifier = Modifier.width(74.dp),
        )
        Text(
          "${if (give) "+" else "−"}${money(tx.amount)}",
          style = v3Text(15, FontWeight.Bold, 20),
          color = tint,
          maxLines = 1,
          textAlign = TextAlign.Center,
          modifier = Modifier.weight(1f),
        )
        entry?.let { e ->
          Text(
            StrDebtor.balanceAfterShort(DebtBook.show(e.balanceAfter)),
            style = v3Text(12, FontWeight.Medium, 16),
            color = c.inkMuted,
            maxLines = 1,
          )
        }
      }
      DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        DropdownMenuItem(text = { Text(StrDebtor.details) }, onClick = { menu = false; onShow() })
        DropdownMenuItem(text = { Text(StrDebtor.receiptPrint) }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ReceiptLong, null) }, onClick = { menu = false; onReceipt() })
        DropdownMenuItem(text = { Text(StrDebtor.delete, color = dc.owe) }, leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = dc.owe) }, onClick = { menu = false; onDelete() })
      }
    }
  }
}

/** هنوز تراکنشی نیست: آیکن + عنوان + یک کارِ اصلی */
@Composable
private fun EmptyTransactions(onAdd: () -> Unit) {
  val c = PhoneV3.colors
  val dc = PhoneV3Debt.colors
  V3Card(radius = 18.dp, padding = PaddingValues(horizontal = 20.dp, vertical = 24.dp)) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
      Box(Modifier.size(52.dp).clip(CircleShape).background(dc.accentSoft), contentAlignment = Alignment.Center) {
        Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = null, tint = dc.accentInk, modifier = Modifier.size(24.dp))
      }
      Spacer(Modifier.height(10.dp))
      Text(StrDebtor.noTx, style = v3Text(16, FontWeight.Bold, 22), color = c.ink)
      Spacer(Modifier.height(4.dp))
      Text(StrDebtor.noTxHint, style = v3Text(13, FontWeight.Normal, 19), color = c.inkMuted, textAlign = TextAlign.Center)
      Spacer(Modifier.height(14.dp))
      V3PrimaryButton(StrDebtor.firstTx, Modifier.width(220.dp), onClick = onAdd)
    }
  }
}

/** فاکتورهای همین مشتری — خودِ خرید، نه فقط ردیفِ بدهی */
@Composable
private fun InvoicesCard(account: DebtorEngine.Account) {
  val c = PhoneV3.colors
  val dc = PhoneV3Debt.colors
  Column(Modifier.padding(top = 8.dp)) {
    Text(StrDebtor.invoices(account.sales.size), style = v3Text(15, FontWeight.Bold, 21), color = c.ink, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp))
    V3Card(radius = 16.dp, padding = PaddingValues(vertical = 4.dp)) {
      account.sales.take(SALES_SHOWN).forEachIndexed { i, sale ->
        if (i > 0) V3Divider(start = 14.dp)
        val cancelled = sale.status == "cancelled"
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(
              "${StrDebtor.invoice} ${sale.invoiceLabel() ?: "—"}${if (cancelled) " (${StrDebtor.cancelled})" else ""}",
              style = v3Text(14, FontWeight.Medium, 20),
              color = if (cancelled) c.inkMuted else c.ink,
            )
            Text(formatDate(sale.date), style = v3Text(12, FontWeight.Normal, 17), color = c.inkMuted)
          }
          Column(horizontalAlignment = Alignment.End) {
            Text(money(sale.finalTotal), style = v3Text(14, FontWeight.SemiBold, 20), color = c.ink)
            if (sale.remaining > 0 && !cancelled) {
              Text(StrDebtor.remaining(money(sale.remaining)), style = v3Text(12, FontWeight.Medium, 17), color = dc.owe)
            }
          }
        }
      }
      if (account.sales.size > SALES_SHOWN) {
        Text(
          StrDebtor.olderInvoices(account.sales.size - SALES_SHOWN),
          style = v3Text(12, FontWeight.Normal, 17), color = c.inkMuted,
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
      }
    }
  }
}

/** چند فاکتورِ آخرِ مشتری در صفحهٔ حسابش — بقیه در «فروش‌ها» */
private const val SALES_SHOWN = 20

/* ------------------------------ نوارِ ثبت ------------------------------ */

/**
 *  «ثبتِ تراکنش» — نوارِ فشردهٔ چسبیده به پایین. زدنِ نوار ⇐ برگهٔ ثبت (برد)؛ دو
 *  قرصِ «برد» و «رسید» همان برگه را با نوعِ انتخاب‌شده باز می‌کنند.
 */
@Composable
private fun ComposerBar(modifier: Modifier, onCompose: (DebtorEngine.Kind) -> Unit) {
  val c = PhoneV3.colors
  val dc = PhoneV3Debt.colors
  val shape = RoundedCornerShape(22.dp)
  Row(
    modifier
      .padding(horizontal = 16.dp, vertical = 12.dp)
      .fillMaxWidth()
      .shadow(10.dp, shape, clip = false, ambientColor = c.ink.copy(alpha = 0.10f), spotColor = c.ink.copy(alpha = 0.10f))
      .clip(shape)
      .background(c.card)
      .border(1.dp, c.hairline, shape)
      .v3Press(c.pressed, label = StrDebtor.newTx) { onCompose(DebtorEngine.Kind.GIVE) }
      .padding(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(dc.accent), contentAlignment = Alignment.Center) {
      Icon(Icons.Outlined.Add, contentDescription = null, tint = dc.onAccent, modifier = Modifier.size(24.dp))
    }
    Spacer(Modifier.width(10.dp))
    Column(Modifier.weight(1f)) {
      Text(StrDebtor.newTx, style = v3Text(15, FontWeight.Bold, 20), color = c.ink, maxLines = 1)
      Text(StrDebtor.newTxHint, style = v3Text(11, FontWeight.Normal, 15), color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    QuickKind(StrDebtor.giveTag, dc.owe, dc.oweTint) { onCompose(DebtorEngine.Kind.GIVE) }
    Spacer(Modifier.width(6.dp))
    QuickKind(StrDebtor.receiveTag, dc.settled, dc.settledTint) { onCompose(DebtorEngine.Kind.RECEIVE) }
  }
}

@Composable
private fun QuickKind(label: String, fg: Color, bg: Color, onClick: () -> Unit) {
  Row(
    Modifier.v3PressScale(onClick, label).clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 12.dp, vertical = 9.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(6.dp).clip(CircleShape).background(fg))
    Spacer(Modifier.width(6.dp))
    Text(label, style = v3Text(13, FontWeight.Bold, 18), color = fg)
  }
}
