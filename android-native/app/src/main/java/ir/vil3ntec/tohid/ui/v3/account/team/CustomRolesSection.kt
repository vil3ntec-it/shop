package ir.vil3ntec.tohid.ui.v3.account.team

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrSetupPages
import ir.vil3ntec.tohid.core.model.CatalogEntryDto
import ir.vil3ntec.tohid.core.model.CustomRoleDto
import ir.vil3ntec.tohid.data.repo.ShopRepository
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3FormSheet
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/** برگهٔ باز: نقشِ تازه (`role` = null) یا ویرایشِ یک نقش */
private data class Editing(val role: CustomRoleDto?)

/**
 *  STF-01 — «نقش‌های سفارشی» زیرِ کارمندان (گوشیِ نسخهٔ ۳ و کامپیوتر، همان کد): فهرستِ نقش‌ها با نقشِ پایه و
 *  شمارِ کارمند، و برای صاحب ساختن/ویرایش/پاک کردن. بی پرچمِ `shop.custom_roles` (یا سرورِ قدیمی) هیچ نمی‌کشد.
 *  ⛔ تصمیم با سرور است (`/api/shop/custom-roles`)؛ خطای سرور (گشادتر از نقش، نامِ تکراری) همان‌جا در برگه دیده می‌شود.
 */
@Composable
internal fun RouteScope.CustomRolesSection(shops: ShopRepository, isOwner: Boolean) {
  val team = TeamSession.data
  val page = team.perms ?: return
  val roles = page.customRoles ?: return
  val c = PhoneV3.colors
  var editing by remember { mutableStateOf<Editing?>(null) }

  V3Section(
    StrAccount.customRolesTitle,
    footer = StrAccount.customRolesHelper,
    action = if (isOwner) StrAccount.customRoleNew else null,
    onAction = if (isOwner) ({ editing = Editing(null) }) else null,
  ) {
    if (roles.isEmpty()) {
      Text(StrAccount.customRoleNone, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.padding(16.dp))
    }
    roles.forEachIndexed { i, r ->
      if (i > 0) V3Divider(start = 64.dp)
      V3ListRow(
        title = r.name,
        subtitle = CustomRoleModel.subtitle(r),
        icon = Icons.Outlined.Badge,
        chevron = isOwner,
        onClick = if (isOwner) ({ editing = Editing(r) }) else null,
      )
    }
  }

  editing?.let { e ->
    CustomRoleSheet(
      catalog = page.catalog,
      role = e.role,
      onDismiss = { editing = null },
      onSave = { name, base, perms, done ->
        TeamSession.scope.launch {
          val res = if (e.role == null) shops.createCustomRole(name, base, perms) else shops.updateCustomRole(e.role.id, name, perms)
          res.onSuccess {
            shops.permissions().onSuccess { team.perms = it.shop }
            toasts.show(StrAccount.customRoleSaved, V3Tone.SUCCESS)
            done(null)
          }.onFailure { done(it.userMessage) }
        }
      },
      onDelete = { id, done ->
        TeamSession.scope.launch {
          shops.deleteCustomRole(id)
            .onSuccess {
              team.reload(shops, isOwner)
              toasts.show(StrAccount.customRoleDeleted, V3Tone.SUCCESS)
              done(null)
            }
            .onFailure { done(it.userMessage) }
        }
      },
    )
  }
}

/**
 *  برگهٔ نام، نقشِ پایه (فقط برای نقشِ تازه) و حالت/سقفِ هر کلیدِ قابل‌تغییرِ همان نقشِ پایه. خطای سرور درجا.
 *  @param onSave (نام، نقشِ پایه، لایه، پایان(خطا یا null))
 */
@Composable
private fun CustomRoleSheet(
  catalog: List<CatalogEntryDto>,
  role: CustomRoleDto?,
  onDismiss: () -> Unit,
  onSave: (String, String, JsonObject, (String?) -> Unit) -> Unit,
  onDelete: (String, (String?) -> Unit) -> Unit,
) {
  val c = PhoneV3.colors
  var name by remember { mutableStateOf(role?.name.orEmpty()) }
  var base by remember { mutableStateOf(role?.baseRole ?: "staff") }
  var rows by remember(base) { mutableStateOf(CustomRoleModel.rows(catalog, base, role?.takeIf { it.baseRole == base }?.perms ?: JsonObject(emptyMap()))) }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var askDelete by remember { mutableStateOf(false) }
  val nameBad = CustomRoleModel.nameProblem(name)
  val capsBad = rows.any(CustomRoleModel::capProblem)

  if (askDelete && role != null) {
    V3ConfirmSheet(
      title = StrAccount.customRoleDeleteTitle(role.name),
      message = StrAccount.customRoleDeleteBody,
      confirm = StrAccount.customRoleDelete,
      icon = Icons.Outlined.Delete,
      onConfirm = { onDelete(role.id) { err -> if (err == null) onDismiss() else { error = err; askDelete = false } } },
      onDismiss = { askDelete = false },
    )
    return
  }

  V3FormSheet(title = if (role == null) StrAccount.customRoleNew else StrAccount.customRoleEdit, dirty = !busy && (role == null && name.isNotBlank()), onClose = onDismiss) { scope ->
    V3TextField(name, { name = it; error = null }, StrAccount.customRoleName, error = if (name.isNotEmpty()) nameBad else null, required = true)
    Spacer(Modifier.height(12.dp))
    Text(StrAccount.customRoleBase, style = V3Type.subhead, color = c.inkMuted)
    Spacer(Modifier.height(6.dp))
    if (role == null) {
      V3ChipRow(contentPadding = PaddingValues(0.dp)) {
        CustomRoleModel.BASE_ROLES.forEach { r -> V3Chip(CustomRoleModel.baseLabel(r), selected = base == r, onClick = { base = r; error = null }) }
      }
    } else {
      Text(CustomRoleModel.baseLabel(base), style = V3Type.headline, color = c.ink)
    }
    Spacer(Modifier.height(16.dp))
    Text(StrAccount.customRolePerms, style = V3Type.subhead, color = c.inkMuted)
    rows.forEachIndexed { i, r ->
      Spacer(Modifier.height(10.dp))
      Text(r.label, style = V3Type.callout, color = c.ink)
      Spacer(Modifier.height(4.dp))
      V3Segmented(
        options = r.options.map(CustomRoleModel::modeLabel),
        selected = r.options.indexOf(r.mode).coerceAtLeast(0),
        onSelect = { k -> rows = rows.toMutableList().also { it[i] = r.copy(mode = r.options[k]) }; error = null },
      )
      if (r.showCaps) {
        if ("maxPct" in r.caps) {
          Spacer(Modifier.height(6.dp))
          V3TextField(r.pct, { v -> rows = rows.toMutableList().also { it[i] = r.copy(pct = v) } }, StrSetupPages.maxPctField, kind = V3FieldKind.NUMBER)
        }
        if ("maxAmount" in r.caps) {
          Spacer(Modifier.height(6.dp))
          V3TextField(r.amount, { v -> rows = rows.toMutableList().also { it[i] = r.copy(amount = v) } }, StrSetupPages.maxAmountField, kind = V3FieldKind.MONEY)
        }
      }
    }
    error?.let { Spacer(Modifier.height(12.dp)); V3Message(V3Tone.ERROR, it) }
    Spacer(Modifier.height(18.dp))
    V3Button(
      StrKit.save,
      {
        busy = true
        onSave(name.trim(), base, CustomRoleModel.layer(rows)) { err ->
          busy = false
          if (err == null) scope.finish { onDismiss() } else error = err
        }
      },
      Modifier.fillMaxWidth(),
      enabled = nameBad == null && !capsBad && !busy,
      loading = busy,
      haptic = true,
    )
    if (role != null) {
      Spacer(Modifier.height(8.dp))
      V3Button(StrAccount.customRoleDelete, { askDelete = true }, Modifier.fillMaxWidth(), kind = V3ButtonKind.DANGER, enabled = !busy)
    }
  }
}
