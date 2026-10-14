package ir.vil3ntec.tohid.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.DraftRestore
import ir.vil3ntec.tohid.data.SaleDraftRules
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  POS-13 — پرسشِ بازیابیِ پیش‌نویسِ فاکتور، یک بار پس از باز شدنِ برنامه.
 *
 *  • پیش‌نویسی که فروشش پیش‌تر ثبت شده (برق میانِ نوشتنِ دفتر و پاک کردنِ
 *    پیش‌نویس رفته بود) بی‌پرسش کنار می‌رود — دوباره ثبت نمی‌شود.
 *  • وگرنه می‌پرسد: «ادامه» یا «دور انداختن». بستنِ پنجره = ادامه؛ داده
 *    هرگز بی‌اجازه دور ریخته نمی‌شود.
 */
@Composable
fun DraftRecoveryPrompt(cartStore: CartStore, d: ShopData, loaded: Boolean, snackbar: SnackbarHostState) {
  val draft by cartStore.recovered.collectAsState()
  val pending = draft ?: return
  if (!loaded) return
  val decision = remember(pending, d) { SaleDraftRules.decide(pending, d) }

  when (decision) {
    DraftRestore.None -> LaunchedEffect(pending) { cartStore.resolveRecovery(null) }
    is DraftRestore.Posted -> LaunchedEffect(pending) {
      cartStore.resolveRecovery(null)
      snackbar.showSnackbar("فاکتورِ نیمه‌کارهٔ پیش پیش‌تر ثبت شده بود — دوباره ثبت نشد")
    }
    is DraftRestore.Offer -> {
      val offer = decision.draft
      val total = remember(offer, d) { SalesEngine.cartTotal(d, offer.cart()) }
      AlertDialog(
        onDismissRequest = { cartStore.resolveRecovery(offer) },
        containerColor = Shop.colors.surfaceSolid,
        title = { Text("فاکتورِ نیمه‌کاره پیدا شد", color = Shop.colors.text) },
        text = {
          Text(
            "از بارِ پیش، پیش از بسته شدنِ برنامه: ${plain(offer.lines.size)} قلم، ${money(total)} ؋." +
              (if (decision.dropped > 0) " ${plain(decision.dropped)} قلم که کالایش حذف یا غیرفعال شده کنار گذاشته شد." else "") +
              " این فاکتور هنوز ثبت نشده است.",
            color = Shop.colors.muted,
          )
        },
        confirmButton = { Button(onClick = { cartStore.resolveRecovery(offer) }) { Text("ادامهٔ همین فاکتور") } },
        dismissButton = { TextButton(onClick = { cartStore.resolveRecovery(null) }) { Text("دور انداختن") } },
      )
    }
  }
}
