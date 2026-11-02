package androidx.activity.compose

import androidx.compose.runtime.Composable

/*
 *  shimِ iOS ِ `BackHandler` ِ اندروید. آیفون دکمهٔ «بازگشت» ِ سیستمی ندارد؛ هر صفحه دکمهٔ
 *  بازگشتِ خودش را دارد (همان دکمهٔ رابطِ مشترک). کشیدنِ لبهٔ صفحه برای بازگشت کارِ گامِ ۲ است
 *  (`androidx.compose.ui.backhandler` ِ Compose Multiplatform).
 */
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
}
