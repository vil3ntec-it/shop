package ir.vil3ntec.tohid.ui.v3.money

import ir.vil3ntec.tohid.ui.theme.V3Section
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.i18n.StrPurchasePages
import ir.vil3ntec.tohid.core.i18n.StrSupplierBills
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.screens.AccountBalanceTile
import ir.vil3ntec.tohid.ui.screens.PhoneDebtorsTheme
import ir.vil3ntec.tohid.ui.screens.gridMinSize
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Debt
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Fab
import ir.vil3ntec.tohid.ui.v3.V3Summary
import ir.vil3ntec.tohid.ui.v3.V3SummaryAction
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.rememberCountUpState
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Stagger
import ir.vil3ntec.tohid.ui.v3.V3Search
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.core.i18n.StrPurchases
import ir.vil3ntec.tohid.data.PurchaseOrderDto
import ir.vil3ntec.tohid.data.PurchaseViews
import ir.vil3ntec.tohid.todayIso
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.saveable.rememberSaveable

/** کارهای فهرستِ تأمین‌کننده‌ها — هر کدام یک مسیر، جز حذف که موتور همین‌جا رد یا قبول می‌کند */
internal class SuppliersActions(
  val onBack: (() -> Unit)?,
  val onOpen: (String) -> Unit,
  val onPay: (String) -> Unit,
  val onEdit: (String) -> Unit,
  /** `LedgerEngine.deleteSupplier` — تأمین‌کننده با خرید/پرداخت حذف نمی‌شود (پیامِ موتور) */
  val onDelete: (String) -> Unit,
  val onNewSupplier: () -> Unit,
  val onNewPurchase: () -> Unit,
  /** PUR-14: «فاکتورهای تأمین‌کننده» — `null` ⇒ پرچمِ سفارشِ خرید خاموش یا کاربر مدیر/صاحب نیست (تراشه پنهان) */
  val onSupplierBills: (() -> Unit)? = null,
  /** PUR-02/03/04: «سفارش‌های خرید» — `null` ⇒ پرچمِ `shop.purchase_orders` خاموش یا بی اینترنت (تراشه پنهان) */
  val onPurchaseOrders: (() -> Unit)? = null,
  /** e-rows-63b: زبانهٔ «خریدها» — `null` ⇒ فقط فهرستِ تأمین‌کنندگان (آزمون‌های پیشین) */
  val purchases: PurchasesActions? = null,
  /** اسکنِ بارکد درونِ کادرِ جست‌وجوی خریدها ⇒ نوشتهٔ جست‌وجو */
  val onScan: (((String) -> Unit) -> Unit)? = null,
)

/** زبانهٔ آغازینِ «خرید و تأمین‌کننده»: `?tab=suppliers` ⇒ تأمین‌کنندگان، وگرنه خریدها */
internal const val SUPPLIERS_TAB = "suppliers"

/**
 *  خرید و تأمین‌کننده (`purchasing`) — دو زبانه زیرِ عنوان (e-rows-63b، خواستهٔ صاحب‌کار: «نمی‌شود خریدهایی که کردم را ببینم»):
 *   • **خریدها** (پیش‌فرض): همهٔ خریدها تازه‌ترین بالا ([purchasesContent])، یک ردیفِ کادرهای کشوییِ پالایه (تأمین‌کننده، پرداخت،
 *     رسیدن، دوره) و جست‌وجوی بخش (نامِ کالا، بارکد، تأمین‌کننده، «#شماره»)؛ [orders] = سفارش‌های بازِ سرور (خالی ⇒ فقط دفتر).
 *   • **تأمین‌کنندگان**: کارتِ مستقلِ هر حساب با رنگ و مانده، مثلِ فهرستِ قرض‌داران. لمسِ طولانی کارهای همان حساب را می‌آورد.
 *  تنها دکمهٔ شناور «خریدِ تازه» است.
 */
@Composable
internal fun SuppliersScreen(
  d: ShopData,
  actions: SuppliersActions,
  orders: List<PurchaseOrderDto> = emptyList(),
  tabArg: String? = null,
  payArg: String? = null,
) {
  val buying = actions.purchases
  var tab by rememberSaveable { mutableStateOf(if (buying == null || tabArg == SUPPLIERS_TAB) 1 else 0) }
  var filter by remember { mutableStateOf(PurchaseViews.Filter(pay = PurchaseViews.PayFilter.entries.firstOrNull { it.name == payArg?.uppercase() } ?: PurchaseViews.PayFilter.ALL)) }
  var query by rememberSaveable { mutableStateOf("") }
  val today = remember { todayIso() }
  val views = remember(d, orders, today, tab) { if (tab == 0) PurchaseViews.all(d, today, orders) else emptyList() }
  val greg = PurchaseRowText.gregorian()
  val shown = remember(views, filter, query, greg) {
    PurchaseViews.search(d, views.filter { PurchaseViews.matches(it, filter, greg, today) }, query)
  }
  val stagger = rememberV3Stagger()
  var confirmDelete by remember { mutableStateOf<String?>(null) }
  val doors = listOfNotNull(
    actions.onPurchaseOrders?.let { V3SummaryAction(StrPurchasePages.purchaseOrders, Icons.Outlined.LocalShipping, onClick = it) },
    actions.onSupplierBills?.let { V3SummaryAction(StrSupplierBills.title, Icons.AutoMirrored.Outlined.ReceiptLong, onClick = it) },
  )

  V3Scaffold(
    title = Str.purchasing,
    onBack = actions.onBack,
    search = if (buying != null && tab == 0) V3Search(query, { query = ir.vil3ntec.tohid.latinDigits(it) }, StrPurchases.searchHint, onScan = actions.onScan?.let { scan -> { scan { code -> query = code } } }) else null,
    chips = if (buying == null) null else ({
      Column {
        Box(Modifier.padding(horizontal = 16.dp)) {
          V3Segmented(listOf(StrPurchases.tabPurchases, StrPurchasePages.suppliers), tab, { tab = it })
        }
        if (tab == 0) {
          Spacer(Modifier.height(8.dp))
          PurchaseFilterRow(d, views, today, filter) { filter = it }
        }
      }
    }),
    fab = { expanded -> V3Fab(Icons.Outlined.AddShoppingCart, StrPurchases.newPurchase, expanded, actions.onNewPurchase) },
  ) { pad ->
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      val contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = pad.calculateBottomPadding() + 72.dp)
      if (buying != null && tab == 0) {
        LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxWidth(), contentPadding = contentPadding) {
          purchasesContent(shown, views.isNotEmpty(), today, buying, doors) { filter = PurchaseViews.Filter(); query = "" }
        }
      } else {
        val rows = remember(d) { SuppliersModel.rows(d) }
        val owed = rememberCountUpState(remember(d) { SuppliersModel.owedTotal(d) })
        PhoneDebtorsTheme {
          LazyVerticalGrid(
            columns = GridCells.Adaptive(gridMinSize(PhoneV3Debt.tileMin, PhoneV3Debt.tileMinTablet)),
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(PhoneV3Debt.gap),
            verticalArrangement = Arrangement.spacedBy(PhoneV3Debt.gap),
          ) {
            item(key = "summary", span = { GridItemSpan(maxLineSpan) }) {
              V3Summary(
                label = StrMoney.supplierDebtTotal,
                value = MoneyText.preciseAfn(owed.value),
                caption = StrMoney.suppliersCaption(d.suppliers.size, d.purchases.size),
                actions = listOf(V3SummaryAction(StrMoney.newSupplier, Icons.Outlined.PersonAddAlt, onClick = actions.onNewSupplier)) + doors,
                collapseKey = "purchasing/suppliers",
                section = V3Section.PURCHASES,
              )
            }
            if (rows.isEmpty()) {
              item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                V3Card {
                  V3EmptyState(Icons.Outlined.LocalShipping, StrMoney.emptySuppliers, body = StrMoney.emptySuppliersBody, action = StrMoney.newSupplier, onAction = actions.onNewSupplier)
                }
              }
            }
            itemsIndexed(rows, key = { _, row -> row.supplier.id }) { index, row ->
              SupplierTile(
                row,
                actions,
                onAskDelete = { confirmDelete = row.supplier.id },
                modifier = Modifier.v3Stagger(stagger, row.supplier.id, index),
              )
            }
          }
        }
      }
    }
  }

  confirmDelete?.let { id ->
    val name = d.suppliers.find { it.id == id }?.name.orEmpty()
    V3ConfirmSheet(
      title = StrMoney.deleteSupplierTitle,
      message = StrMoney.deleteSupplierBody(name),
      confirm = StrMoney.delete,
      icon = Icons.Outlined.DeleteOutline,
      onConfirm = { actions.onDelete(id) },
      onDismiss = { confirmDelete = null },
    )
  }
}

@Composable
private fun SupplierTile(
  row: SuppliersModel.Row,
  actions: SuppliersActions,
  onAskDelete: () -> Unit,
  modifier: Modifier,
) {
  val colors = PhoneV3Debt.colors
  val supplier = row.supplier
  val state = supplierStateWord(row.state)
  AccountBalanceTile(
    name = supplier.name,
    amount = MoneyText.preciseAfn(kotlin.math.abs(row.debt)),
    subtitle = listOfNotNull(state, supplier.phone.takeIf(String::isNotBlank)?.let(ir.vil3ntec.tohid.LocaleText::phoneDisplay)).joinToString(" · "),
    tint = supplierTint(row.state),
    fill = when (row.state) {
      SuppliersModel.State.OWE -> colors.oweTint
      SuppliersModel.State.PREPAID -> colors.infoTint
      SuppliersModel.State.SETTLED -> colors.settledTint
    },
    modifier = modifier,
    onOpen = { actions.onOpen(supplier.id) },
    onPay = { actions.onPay(supplier.id) },
    onEdit = { actions.onEdit(supplier.id) },
    onDelete = onAskDelete,
  )
}

/** واژهٔ حالِ حسابِ تأمین‌کننده: «بدهکاریم»، «پیش‌پرداخت»، «تسویه» */
internal fun supplierStateWord(state: SuppliersModel.State): String = when (state) {
  SuppliersModel.State.OWE -> StrMoney.weOwe
  SuppliersModel.State.PREPAID -> StrMoney.prepaidToSupplier
  SuppliersModel.State.SETTLED -> StrMoney.accountSettled
}

@Composable
internal fun supplierTint(state: SuppliersModel.State): Color = when (state) {
  SuppliersModel.State.OWE -> PhoneV3Debt.colors.owe
  SuppliersModel.State.PREPAID -> PhoneV3Debt.colors.info
  SuppliersModel.State.SETTLED -> PhoneV3Debt.colors.settled
}

/** آواتارِ حرفِ اول روی زمینهٔ نرمِ رنگِ همراهِ بخش (پیشنهادِ کیت: `Draft`) */
@Composable
internal fun LetterAvatar(name: String, hue: V3Hue?) {
  val c = PhoneV3.colors
  val tint = hue?.let { PhoneV3Palette.glyph(c, it) } ?: c.icon
  val soft = hue?.let { PhoneV3Palette.soft(c, it) } ?: c.tile
  Box(Modifier.size(40.dp).clip(CircleShape).background(soft), contentAlignment = Alignment.Center) {
    Text(name.trim().take(1).ifBlank { "؟" }, style = v3Text(17, FontWeight.Bold, 22), color = tint)
  }
}
