package ir.vil3ntec.tohid.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.perm.CreditLimitPerm
import ir.vil3ntec.tohid.data.ShopRole

/**
 *  CRM-03 / §۱۰.۱ — مرزِ سکو: قاعدهٔ «تغییرِ سقفِ اعتبار» (`customers.credit_limit`) برای برگهٔ مشتری (`CreditLimitGate`):
 *  همین کاربر (نقش و لایه‌های `policy`) و این‌که سرور این کلید را برای همین دکان اجرا می‌کند (`policy.editable` ⇐ پرچمِ
 *  `shop.credit_limit_perm`). با هر تغییرِ نقش یا لایهٔ مجوز (`ShopRole.policyVersion`) دوباره ساخته می‌شود.
 */
@Composable
fun rememberCreditLimitRules(): CreditLimitPerm.Rules {
  val context = LocalContext.current
  val version by ShopRole.policyVersion.collectAsState()
  return remember(version) {
    CreditLimitPerm.Rules(ShopRole.current(context), ShopRole.layers(context), ShopRole.enforces(context, CreditLimitPerm.PERM))
  }
}
