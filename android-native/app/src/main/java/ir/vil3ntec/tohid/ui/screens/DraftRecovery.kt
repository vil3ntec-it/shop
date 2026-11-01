package ir.vil3ntec.tohid.ui.screens

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.v3.sales.draft.DraftPrompt

/**
 *  POS-13 — پرسشِ بازیابیِ پیش‌نویسِ فاکتور، یک بار پس از باز شدنِ برنامه. پوسته (`AppRoot`)
 *  همین نام را صدا می‌زند؛ برگهٔ نسخهٔ ۳ و قاعده‌اش در `ui/v3/sales/draft/DraftSheet.kt` است.
 *  فاز ۳: پوسته مستقیم `DraftPrompt` را صدا می‌زند و این فایل برداشته می‌شود.
 */
@Composable
fun DraftRecoveryPrompt(cartStore: CartStore, d: ShopData, loaded: Boolean, snackbar: SnackbarHostState) =
  DraftPrompt(cartStore, d, loaded, snackbar)
