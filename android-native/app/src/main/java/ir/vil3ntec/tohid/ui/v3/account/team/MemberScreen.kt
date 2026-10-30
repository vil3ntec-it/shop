package ir.vil3ntec.tohid.ui.v3.account.team

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.model.MemberDto
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3MenuItem
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Skeleton
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3ToggleRow
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.account.AccountFormat
import ir.vil3ntec.tohid.ui.v3.account.AccountPaths
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Stagger
import kotlinx.coroutines.launch

/** کارهای صفحهٔ یک کارمند */
internal class MemberActions(
  val back: (() -> Unit)?,
  val toggle: (key: String, on: Boolean) -> Unit,
  val block: (deviceId: String) -> Unit,
  val unblock: (deviceId: String) -> Unit,
  val remove: () -> Unit,
)

/**
 *  «کارمند»: نام و قرصِ نقش، کارتِ «مجوزِ کارمندان» (کلیدهایی که سرور می‌پذیرد —
 *  فقط صاحب دکان عوض می‌کند) و دستگاه‌های همین کارمند با وضعیت (رنگ + برچسب) و
 *  «از کار انداختن» (تأیید) یا «برگرداندن».
 */
@Composable
internal fun MemberContent(team: TeamData, member: MemberDto?, canEdit: Boolean, busy: Boolean, actions: MemberActions) {
  val c = PhoneV3.colors
  val stagger = rememberV3Stagger(member?.id)
  val row = member?.let(TeamModel::memberRow)
  V3Scaffold(
    title = row?.name ?: StrAccount.staffMember,
    subtitle = row?.role,
    onBack = actions.back,
    overflow = if (row?.removable == true) listOf(V3MenuItem(Icons.Outlined.PersonRemove, StrAccount.remove, danger = true, onClick = actions.remove)) else emptyList(),
  ) { pad ->
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding())) {
        when {
          member == null && !team.loaded -> { Spacer(Modifier.height(16.dp)); V3Skeleton(rows = 3) }
          member == null || row == null -> V3EmptyState(Icons.Outlined.PersonOff, StrAccount.memberMissing)
          else -> {
            Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
              RolePill(row.role)
              Spacer(Modifier.width(6.dp))
              V3StatusPill(if (row.active) V3Status.DELIVERED else V3Status.INACTIVE, if (row.active) StrAccount.stateActive else StrAccount.stateClosed)
            }
            val perms = MemberModel.permRows(team.perms, MemberModel.permsOf(team.perms, member))
            if (perms.isNotEmpty()) {
              V3Section(StrAccount.permsTitle, footer = if (canEdit) StrAccount.permsHelper else StrAccount.permsOwnerOnly) {
                perms.forEachIndexed { i, p ->
                  if (i > 0) V3Divider(start = 16.dp)
                  V3ToggleRow(p.label, p.on, { actions.toggle(p.key, it) }, enabled = canEdit && !busy, modifier = Modifier.v3Stagger(stagger, p.key, i))
                }
              }
            }
            val devices = MemberModel.devicesOf(team.devices, member)
            V3Section(StrAccount.devicesTitle) {
              if (team.devices == null) V3Skeleton(rows = 2)
              else if (devices.isEmpty()) Text(StrAccount.noDevices, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.padding(16.dp))
              devices.forEachIndexed { i, d ->
                if (i > 0) V3Divider(start = 64.dp)
                val (status, label) = MemberModel.pill(d)
                val action = MemberModel.actionOf(d, canEdit)
                V3ListRow(
                  title = MemberModel.deviceName(d),
                  subtitle = d.lastSeenAt?.takeIf { it > 0 }?.let { StrAccount.lastSeen(AccountFormat.date(it)) },
                  icon = Icons.Outlined.Devices,
                  chevron = false,
                  modifier = Modifier.v3Stagger(stagger, d.id, i + perms.size),
                  trailing = {
                    Column(horizontalAlignment = Alignment.End) {
                      V3StatusPill(status, label)
                      when (action) {
                        MemberModel.DeviceAction.BLOCK -> V3TextButton(StrAccount.block, { actions.block(d.id) }, danger = true)
                        MemberModel.DeviceAction.UNBLOCK -> V3TextButton(StrAccount.unblock, { actions.unblock(d.id) })
                        null -> Unit
                      }
                    }
                  },
                )
              }
            }
          }
        }
      }
    }
  }
}

/** مسیرِ `team/members/{id}`: همان فراخوان‌های `MemberPermsSection`/`DevicesSection` ِ پیشین */
@Composable
internal fun RouteScope.MemberRoute(id: String) {
  val context = LocalContext.current
  val shops = remember { Backend.shop(context) }
  val team = TeamSession.data
  val canEdit = remember { ShopRole.current(context) == ShopRole.OWNER }
  var busy by remember { mutableStateOf(false) }
  //  پیوندِ سرد (اعلان، کامپیوتر): داده هنوز خوانده نشده
  LaunchedEffect(id) { if (!team.loaded && Backend.isReady(context)) team.reload(shops, canEdit) }
  val member = team.member(id)
  MemberContent(
    team, member, canEdit, busy,
    MemberActions(
      back = backOrNull,
      toggle = { key, on ->
        val layer = member?.let { MemberModel.permsOf(team.perms, it) }
        if (layer != null) {
          busy = true
          TeamSession.scope.launch {
            shops.setMemberPerms(layer.id, MemberModel.withMode(layer, key, on))
              .onSuccess { shops.permissions().onSuccess { team.perms = it.shop } }
              .onFailure { toasts.show(it.userMessage, V3Tone.ERROR) }
            busy = false
          }
        }
      },
      block = { nav.open(AccountPaths.blockDevice(it)) },
      unblock = { deviceId ->
        TeamSession.scope.launch {
          shops.enableDevice(deviceId)
            .onSuccess { toasts.show(StrAccount.deviceEnabled, V3Tone.SUCCESS); shops.devices().onSuccess { team.devices = it } }
            .onFailure { toasts.show(it.userMessage, V3Tone.ERROR) }
        }
      },
      remove = { nav.open(AccountPaths.removeMember(id)) },
    ),
  )
}
