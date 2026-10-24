package ir.vil3ntec.tohid.ui.v3.sales.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AssignmentReturn
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.ReturnFlow
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.LocalV3SystemBottom
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3MenuItem
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Section

/** کارهای صفحهٔ فاکتور — `null` یعنی برای این فاکتور یا این کاربر نیست */
@Stable
class InvoiceActions(
  val print: () -> Unit,
  val send: (() -> Unit)?,
  val ret: (() -> Unit)?,
  val fix: (() -> Unit)?,
  val cancel: (() -> Unit)?,
  val returnReceipt: (String) -> Unit,
  val debtor: (String) -> Unit,
  val back: (() -> Unit)?,
)

/**
 *  صفحهٔ فاکتور (`sales/{saleId}`): رسیدِ کامل (`ReceiptCard`)، رسیدهای برگشتِ همین فاکتور
 *  (برگشت و تعویض روشن)، نوارِ پایین «چاپ» و «فرستادن»، و «کارها» در سربرگ: مرجوعی، اصلاحِ
 *  فاکتور، لغو فروش (هر کدام برگهٔ خودش). نامِ مشتری ⇐ حسابش.
 */
@Composable
fun InvoiceScreen(
  d: ShopData,
  sale: Sale,
  storeName: String,
  returnsV2: Boolean,
  actions: InvoiceActions,
) {
  val docs = if (returnsV2) HistoryModel.returnDocs(d, sale) else emptyList()
  V3Scaffold(
    title = ReceiptModel.title(sale),
    largeTitle = false,
    onBack = actions.back,
    overflow = buildList {
      actions.ret?.let { add(V3MenuItem(Icons.AutoMirrored.Outlined.AssignmentReturn, if (returnsV2) StrSale.returnOrExchange else StrSale.returnWord, onClick = it)) }
      actions.fix?.let { add(V3MenuItem(Icons.Outlined.Edit, StrSale.fixInvoice, onClick = it)) }
      actions.cancel?.let { add(V3MenuItem(Icons.Outlined.Block, StrSale.cancelSale, danger = true, onClick = it)) }
    },
    bottomBar = {
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 10.dp + (LocalV3SystemBottom.current ?: WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        V3Button(StrSale.print, actions.print, Modifier.weight(1f), icon = Icons.Outlined.Print)
        actions.send?.let { V3Button(StrSale.send, it, Modifier.weight(1f), kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.Share) }
      }
    },
  ) { pad ->
    Column(
      Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = pad.calculateBottomPadding() + 16.dp),
    ) {
      ReceiptCard(d, sale, storeName, onDebtor = actions.debtor)
      if (docs.isNotEmpty()) {
        V3Section(StrSale.returnReceipts) {
          docs.forEachIndexed { i, doc ->
            if (i > 0) V3Divider(start = 64.dp)
            V3ListRow(
              StrSale.returnReceiptOf(ReturnFlow.label(doc)),
              icon = Icons.AutoMirrored.Outlined.ReceiptLong,
              onClick = { actions.returnReceipt(doc) },
            )
          }
        }
      }
      Spacer(Modifier.height(8.dp))
    }
  }
}
