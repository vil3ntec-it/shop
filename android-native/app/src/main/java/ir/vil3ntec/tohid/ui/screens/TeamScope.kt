package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.model.StaffScopeMembersDto
import ir.vil3ntec.tohid.core.staff.StaffScope
import ir.vil3ntec.tohid.data.repo.ShopRepository
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

private fun placeLabel(code: String): String =
  if (code == StaffScope.WAREHOUSE) StrAccount.placeWarehouse else StrAccount.placeShopFloor

/**
 *  STF-06 — مکان‌های مجازِ هر کارمند (فقط صاحب تغییر می‌دهد). فقط وقتی سرور پرچمِ
 *  `shop.staff_scope` را برای این دکان روشن کرده دیده می‌شود؛ اجرا با سرور است
 *  (`location_out_of_scope`)، این‌جا فقط کلیدها.
 */
@Composable
fun StaffScopeSection(shops: ShopRepository, canEdit: Boolean, snackbar: SnackbarHostState) {
  val scope = rememberCoroutineScope()
  var page by remember { mutableStateOf<StaffScopeMembersDto?>(null) }
  var busy by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { page = shops.staffScopeMembers() }

  val p = page ?: return
  val staff = p.members.filter { it.role != "courier" }
  if (staff.isEmpty()) return

  SectionTitle(StrAccount.scopeTitle)
  Panel {
    Text(StrAccount.scopeHelper, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    staff.forEachIndexed { i, m ->
      if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Shop.colors.muted2.copy(alpha = 0.2f))
      Text(m.name.ifBlank { StrAccount.staffMember }, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
      StaffScope.PLACES.forEach { place ->
        val on = StaffScope.isOn(m.places, place)
        val next = StaffScope.toggle(m.places, place, !on)
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(placeLabel(place), style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text, modifier = Modifier.weight(1f))
          Switch(
            checked = on,
            enabled = canEdit && !busy && next != null,
            onCheckedChange = {
              if (next != null) {
                busy = true
                scope.launch {
                  shops.setStaffScope(m.id, next)
                    .onSuccess { page = shops.staffScopeMembers() }
                    .onFailure { snackbar.showSnackbar(it.userMessage) }
                  busy = false
                }
              }
            },
          )
        }
      }
    }
  }
  Spacer(Modifier.height(18.dp))
}
