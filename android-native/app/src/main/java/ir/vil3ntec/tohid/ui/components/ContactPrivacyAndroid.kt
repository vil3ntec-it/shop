package ir.vil3ntec.tohid.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.data.ShopRole

/**
 *  CRM-15 — مرزِ سکو: «این کاربر شمارهٔ مشتری را می‌بیند؟» = مجوزِ `customers.contact` (فقط نمایش؛ پیش‌فرض همه جز
 *  انباردار و جمع‌آور). با هر تغییرِ نقش یا لایهٔ مجوز (`ShopRole.policyVersion`) دوباره سنجیده می‌شود.
 */
@Composable
fun rememberCanSeeContacts(): Boolean {
  val context = LocalContext.current
  val version by ShopRole.policyVersion.collectAsState()
  return remember(version) { ShopRole.allows(context, "customers.contact") }
}
