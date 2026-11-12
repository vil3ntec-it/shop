package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.stock.OnlineHolds
import ir.vil3ntec.tohid.data.HeldBook
import ir.vil3ntec.tohid.data.HeldStock
import ir.vil3ntec.tohid.data.LedgerOwner
import ir.vil3ntec.tohid.data.OnlineHoldsStore
import ir.vil3ntec.tohid.data.ShopData
import kotlinx.coroutines.delay

/**
 *  POS-05 / INV-03 — «کنار رفته» ِ کاشیِ صندوق (گوشی: `SaleRoute`، کامپیوتر: `DeskSale`) برای `HeldStock.sellable`: نگه‌داشته‌های
 *  همین صندوق + رزروِ سفارش‌های اینترنتی (`OnlineHoldsStore`، هر [OnlineHolds.REFRESH_MS] تا صفحه باز است). قلمی که فروشِ
 *  تحویلش (`o-<سفارش>-<قلم>`) در دفترِ همین دستگاه هست کنار می‌رود. پرچمِ سرور خاموش ⇒ همان `HeldStock.reserved(book)`.
 */
@Composable
fun rememberSellableHeld(book: HeldBook, d: ShopData): Map<String, Double> {
  val context = LocalContext.current
  val shop = remember { LedgerOwner.currentShop(context) }
  var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(Unit) {
    OnlineHoldsStore.current(context)
    while (true) {
      OnlineHoldsStore.refresh(context)
      now = System.currentTimeMillis()
      delay(OnlineHolds.REFRESH_MS)
    }
  }
  val state by OnlineHoldsStore.state
  val snap = state.second.takeIf { state.first == shop }
  val sold = remember(d.saleItems) { d.saleItems.asSequence().map { it.id }.filter { it.startsWith("o-") }.toHashSet() }
  val local = remember(book) { HeldStock.reserved(book) }
  return remember(local, snap, sold, now) { HeldStock.withOnline(local, OnlineHolds.reserved(snap, now) { it in sold }) }
}
