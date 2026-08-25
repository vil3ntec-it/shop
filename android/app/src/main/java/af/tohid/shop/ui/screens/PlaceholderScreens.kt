package af.tohid.shop.ui.screens

import androidx.compose.runtime.Composable

/*
 * صفحه‌های در دست ساخت.
 * پایه‌ی برنامه (پایگاه‌داده، همگام‌سازی، به‌روزرسانی) کامل است؛
 * این صفحه‌ها یکی‌یکی از نسخه وب منتقل می‌شوند.
 */

@Composable
fun ProductsScreen() = ScreenScaffold("محصولات", "کالاها و موجودی") {
    InfoPanel("در دست ساخت", "صفحه محصولات در مرحله بعد منتقل می‌شود.")
}

@Composable
fun DebtorsScreen() = ScreenScaffold("قرض‌داران", "حساب مشتریان") {
    InfoPanel("در دست ساخت", "صفحه قرض‌داران در مرحله بعد منتقل می‌شود.")
}
