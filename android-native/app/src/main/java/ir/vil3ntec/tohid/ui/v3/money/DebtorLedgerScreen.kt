package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.data.DebtBook
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.LocalV3SystemBottom
import ir.vil3ntec.tohid.ui.v3.V3Action
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3StaggerState
import ir.vil3ntec.tohid.ui.v3.V3TwoPane
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3AnimateItem
import ir.vil3ntec.tohid.ui.v3.v3Stagger

/** کارهای حسابِ یک قرض‌دار — هر کدام یک مسیر (`MoneyRoutes`) یا کارِ همان دستگاه */
internal class DebtorLedgerActions(
  val onBack: (() -> Unit)?,
  /** برگهٔ ثبتِ تراکنش (`debtors/{id}/tx?kind=`) */
  val onCompose: (DebtorEngine.Kind) -> Unit,
  val onEdit: () -> Unit,
  val onDelete: () -> Unit,
  /** برگهٔ جزئیاتِ یک ردیف (`debtors/{id}/tx/{txId}`) */
  val onOpenTx: (String) -> Unit,
  /** حذف با برگرداندن (`MoneyUndo`) */
  val onDeleteTx: (String) -> Unit,
  /** فاکتور ⇐ `sales/{saleId}`؛ «قدیمی‌تر» ⇐ `sales?q=` */
  val onOpenSale: (String) -> Unit,
  val onOlderSales: () -> Unit,
)

/**
 *  حسابِ یک قرض‌دار (`debtors/{id}`) — همان چیدمانِ تأییدشدهٔ #354 روی کیت: سربرگِ فشرده با
 *  نام و «⋯»، یک کشوی تاشو با همهٔ کارها (زنگ، پیامک، واتساپ، ویرایش، صورت‌حساب، چاپ، حذف) و
 *  «چند روز بدهکار»، کارتِ سه‌عددی، ردیف‌های یک‌خطیِ برد/رسید بی سرخطِ روز، فاکتورهای مشتری، و
 *  نوارِ چسبانِ «ثبتِ تراکنش». پهن (تبلت/کامپیوتر): کشو و عددها در ستونِ آغاز، ردیف‌ها کنارش.
 */
@Composable
internal fun DebtorLedgerScreen(
  id: String,
  d: ShopData,
  wide: Boolean,
  actions: DebtorLedgerActions,
  /** کشو باز/بسته از بیرون (عکس‌ها)؛ null ⇒ همان که کاربر روی دستگاه گذاشته */
  drawerOpen: Boolean? = null,
) {
  val hidden = MoneyUndo.hiddenTx
  val account = remember(d, id) { DebtorEngine.account(d, id) }
  if (account == null || id in MoneyUndo.hiddenDebtors) {
    V3Scaffold(title = StrMoney.debtor, onBack = actions.onBack, largeTitle = false) { pad ->
      Column(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) {
        V3EmptyState(Icons.Outlined.PersonOff, StrMoney.debtorGone, body = StrMoney.debtorGoneBody)
      }
    }
    return
  }
  val context = LocalContext.current
  val book = remember(d, id) { DebtBook.book(d, id, todayIso()) }
  val rows = remember(account.transactions, hidden) { DebtorLedgerModel.rows(account.transactions, hidden) }
  val totals = DebtorLedgerModel.totals(account)
  val drawer = rememberDrawerState(context, drawerOpen)
  val stagger = rememberV3Stagger(id)
  var menuFor by rememberSaveable { mutableStateOf<String?>(null) }
  val tasks = rememberDebtorTasks(d, account, actions)
  val available = DebtorLedgerModel.actions(
    account.debtor.phone, account.transactions.isNotEmpty(),
    //  CRM-05: «تصویرِ صورت‌حساب» فقط با «گزارش‌های مشتری» روشن (پیش‌فرض خاموش)
    image = ir.vil3ntec.tohid.data.ShopPolicy.crmReports(context),
  )
  val navInset = LocalV3SystemBottom.current ?: WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

  //  دو ستون فقط وقتی جا هست (کامپیوتر، تبلت) — پنجرهٔ باریک همان چیدمانِ گوشی
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val twoPane = wide && maxWidth >= WIDE_MIN
    V3Scaffold(
      title = account.debtor.name.ifBlank { StrMoney.debtor },
      onBack = actions.onBack,
      largeTitle = false,
      actions = if (twoPane) emptyList() else listOf(V3Action(Icons.Outlined.MoreHoriz, StrMoney.actionsAndInfo) { drawer.toggle() }),
      bottomBar = {
        LedgerActionBar(
          title = StrMoney.newTx,
          hint = StrMoney.newTxHint,
          onMain = { actions.onCompose(DebtorEngine.Kind.GIVE) },
          quick = listOf(
            LedgerQuick(StrMoney.giveTag, LedgerTone.OWE) { actions.onCompose(DebtorEngine.Kind.GIVE) },
            LedgerQuick(StrMoney.receiveTag, LedgerTone.SETTLED) { actions.onCompose(DebtorEngine.Kind.RECEIVE) },
          ),
          modifier = Modifier.padding(bottom = navInset),
        )
      },
    ) { pad ->
      val listPad = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = pad.calculateBottomPadding())
      val totalCells = ledgerCells(totals)
      if (twoPane) {
        V3TwoPane(
          listWidth = 400.dp,
          list = {
            Column(
              Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = pad.calculateBottomPadding()),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              DebtorDrawer(account, book, available, open = true, onToggle = null, onAction = tasks::run)
              LedgerTotals(totalCells)
            }
          },
          detail = {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = listPad.copyTop(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              ledgerItems(rows, book, account, actions, stagger, onMenu = { menuFor = it })
            }
          },
        )
      } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = listPad, verticalArrangement = Arrangement.spacedBy(4.dp)) {
          item(key = "drawer") {
            Box(Modifier.padding(bottom = 4.dp)) {
              DebtorDrawer(account, book, available, open = drawer.open, onToggle = drawer::toggle, onAction = tasks::run)
            }
          }
          item(key = "totals") { LedgerTotals(totalCells) }
          ledgerItems(rows, book, account, actions, stagger, onMenu = { menuFor = it })
        }
      }
    }
  }

  //  نگه‌داشتنِ ردیف ⇐ کارهای همان ردیف
  menuFor?.let { txId ->
    V3Sheet(onDismiss = { menuFor = null }) { closer ->
      V3Card(padding = PaddingValues(vertical = 4.dp)) {
        V3ListRow(StrMoney.details, icon = Icons.AutoMirrored.Outlined.ReceiptLong, onClick = { closer.close { actions.onOpenTx(txId) } })
        V3Divider(start = 64.dp)
        V3ListRow(StrMoney.delete, icon = Icons.Outlined.DeleteOutline, danger = true, chevron = false, onClick = { closer.close { actions.onDeleteTx(txId) } })
      }
    }
  }
}

/** پهنای کمینهٔ چیدمانِ دوستونه (۴۰۰ کشو + ۳۲۰ ردیف‌ها) */
private val WIDE_MIN = 720.dp

private fun PaddingValues.copyTop(top: androidx.compose.ui.unit.Dp) = PaddingValues(start = 16.dp, end = 16.dp, top = top, bottom = calculateBottomPadding())

/** کل برد (سرخ) · کل رسید (سبز) · الباقی؛ صفر خنثی است، نه سرخ و نه سبز */
private fun ledgerCells(t: DebtorLedgerModel.Totals): List<LedgerTotal> = listOf(
  LedgerTotal(StrMoney.given, t.given, if (DebtorLedgerModel.isZero(t.given)) LedgerTone.NEUTRAL else LedgerTone.OWE),
  LedgerTotal(StrMoney.received, t.received, if (DebtorLedgerModel.isZero(t.received)) LedgerTone.NEUTRAL else LedgerTone.SETTLED),
  LedgerTotal(
    StrMoney.balance,
    kotlin.math.abs(t.balance),
    when {
      t.settled -> LedgerTone.NEUTRAL
      t.owes -> LedgerTone.OWE
      else -> LedgerTone.SETTLED
    },
    text = if (t.settled) StrMoney.settled else null,
  ),
)

/** «تراکنش‌ها · N»، ردیف‌های یک‌خطی (تازه‌ترین اول؛ تاریخ داخلِ ردیف)، و فاکتورهای مشتری */
private fun LazyListScope.ledgerItems(
  rows: List<ir.vil3ntec.tohid.data.DebtTransaction>,
  book: DebtBook.Book?,
  account: DebtorEngine.Account,
  actions: DebtorLedgerActions,
  stagger: V3StaggerState,
  onMenu: (String) -> Unit,
) {
  item(key = "tx-title") {
    Text(
      "${StrMoney.transactions} · ${plain(rows.size)}",
      style = v3Text(15, FontWeight.Bold, 20),
      color = PhoneV3.colors.ink,
      modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 2.dp).semantics { heading() },
    )
  }
  if (rows.isEmpty()) {
    item(key = "tx-empty") {
      V3Card {
        V3EmptyState(
          Icons.AutoMirrored.Outlined.ReceiptLong,
          StrMoney.noTx,
          body = StrMoney.noTxHint,
          action = StrMoney.firstTx,
          onAction = { actions.onCompose(DebtorEngine.Kind.GIVE) },
        )
      }
    }
  }
  itemsIndexed(rows, key = { _, tx -> tx.id }) { i, tx ->
    val give = tx.type == "give"
    LedgerRow(
      date = MoneyText.date(tx.date),
      amount = MoneyText.signed(tx.amount, give),
      after = book?.entry(tx.id)?.let { StrMoney.balanceAfterShort(DebtBook.show(it.balanceAfter)) },
      tone = if (give) LedgerTone.OWE else LedgerTone.SETTLED,
      up = give,
      kindLabel = if (give) StrMoney.giveTag else StrMoney.receiveTag,
      onClick = { actions.onOpenTx(tx.id) },
      onLongClick = { onMenu(tx.id) },
      onDelete = { actions.onDeleteTx(tx.id) },
      modifier = Modifier.v3AnimateItem(this).v3Stagger(stagger, tx.id, i),
    )
  }
  if (account.sales.isNotEmpty()) {
    item(key = "sales") { DebtorInvoices(account, actions.onOpenSale, actions.onOlderSales) }
  }
}
