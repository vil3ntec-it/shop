package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Pressable
import ir.vil3ntec.tohid.ui.v3.v3Stagger
import ir.vil3ntec.tohid.ui.theme.PhoneV3

/** مسیرِ فهرستِ کاملِ تراکنش‌ها (`transactions?kind=sale|online|purchase`) */
object TransactionPaths {
  const val LIST = "transactions"
  fun kinds(purchases: Boolean): List<Transactions.Kind?> =
    listOf(null, Transactions.Kind.SALE, Transactions.Kind.ONLINE) + if (purchases) listOf(Transactions.Kind.PURCHASE) else emptyList()
  fun kindOf(q: String?): Transactions.Kind? = Transactions.Kind.entries.firstOrNull { it.name.equals(q, ignoreCase = true) }
}

/** «تراکنش‌ها» روی خانه: پنج تراکنشِ تازه + «همه» (⇐ فهرستِ کامل)؛ زدن ⇐ برگهٔ اقلام */
@Composable
internal fun HomeTransactions(d: ShopData, purchases: Boolean, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
  val c = PhoneV3.colors
  //  نوشته‌ها (مشتریِ گذری) در زبانِ همین لحظه
  val recent = remember(d, purchases, I18n.lang) { Transactions.recent(d, purchases = purchases) }
  val today = ir.vil3ntec.tohid.todayIso()
  var open by remember { mutableStateOf<Transactions.Tx?>(null) }
  val stagger = rememberV3Stagger()
  Column(modifier.padding(horizontal = 22.dp)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      HomeTitle(StrHome.transactions, Modifier.weight(1f))
      if (recent.isNotEmpty()) Text(
        Str.all,
        color = PhoneV3Roles.brandText(c),
        style = v3Text(15, FontWeight.Bold, 22),
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).v3Pressable(label = Str.all) { onOpen(TransactionPaths.LIST) }
          .padding(horizontal = 6.dp, vertical = 4.dp),
      )
    }
    Spacer(Modifier.height(12.dp))
    if (recent.isEmpty()) {
      Text(StrHome.txEmptyBody, style = v3Text(13, FontWeight.Medium, 18), color = c.inkMuted)
    } else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      recent.forEachIndexed { i, tx -> TxRow(tx, today, onClick = { open = tx }, modifier = Modifier.v3Stagger(stagger, tx.key, i)) }
    }
  }
  open?.let { tx -> TxSheet(d, tx, onDismiss = { open = null }, onOpen = onOpen) }
}

/**
 *  فهرستِ کاملِ تراکنش‌ها (`transactions`، PAGE) — همان ردیف‌های خانه با تبِ نوع (همه /
 *  فروش / سفارشِ آنلاین / خرید)؛ زدن ⇐ برگهٔ اقلام.
 */
@Composable
fun TransactionsScreen(d: ShopData, initialKind: Transactions.Kind?, onOpen: (String) -> Unit, onBack: (() -> Unit)?, bottomReserve: Dp) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val purchases = remember { ir.vil3ntec.tohid.data.ShopRole.canSeeMoney(context) }
  val kinds = TransactionPaths.kinds(purchases)
  var tab by rememberSaveable { mutableIntStateOf(kinds.indexOf(initialKind).coerceAtLeast(0)) }
  val kind = kinds[tab.coerceIn(0, kinds.lastIndex)]
  val list = remember(d, kind, purchases, I18n.lang) { Transactions.list(d, kind, purchases) }
  val today = ir.vil3ntec.tohid.todayIso()
  var open by remember { mutableStateOf<Transactions.Tx?>(null) }
  V3Scaffold(
    title = StrHome.transactions,
    onBack = onBack,
    bottomReserve = bottomReserve,
    chips = {
      V3Segmented(
        options = kinds.map { k -> k?.let(Transactions::kindLabel) ?: StrHome.txAll },
        selected = tab,
        onSelect = { tab = it },
        modifier = Modifier.padding(horizontal = 16.dp),
      )
    },
  ) { pad ->
    if (list.isEmpty()) {
      Box(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) {
        V3EmptyState(Icons.AutoMirrored.Outlined.ReceiptLong, StrHome.txEmpty, body = StrHome.txEmptyBody)
      }
    } else {
      val stagger = rememberV3Stagger(kind)
      LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = pad.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        itemsIndexed(list, key = { _, t -> t.key }) { i, tx ->
          TxRow(tx, today, onClick = { open = tx }, modifier = Modifier.v3Stagger(stagger, tx.key, i))
        }
      }
    }
  }
  open?.let { tx -> TxSheet(d, tx, onDismiss = { open = null }, onOpen = onOpen) }
}
