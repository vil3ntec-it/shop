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
import androidx.compose.material.icons.outlined.Key
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
import ir.vil3ntec.tohid.core.staff.StaffScope
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3MenuItem
import ir.vil3ntec.tohid.ui.v3.V3PickerRow
import ir.vil3ntec.tohid.ui.v3.V3PickerSheet
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
  /** STF-06: کلیدِ یک مکان */
  val place: (place: String, on: Boolean) -> Unit = { _, _ -> },
  /** STF-01: دادن (شناسه) یا برداشتنِ (null) نقشِ سفارشی */
  val customRole: (roleId: String?) -> Unit = {},
  /** «باطل کردنِ این دستگاه» (رازِ ورود با کد) — برگهٔ تأیید */
  val revokeKey: (keyId: String) -> Unit = {},
  /** «خروج از همهٔ دستگاه‌ها» برای همین کارمند — برگهٔ تأیید */
  val logoutAll: () -> Unit = {},
  /** «برداشتنِ عضوِ کهنه» از نشانهٔ «شاید همان» — برگهٔ تأیید */
  val removeStale: (oldMemberId: String) -> Unit = {},
  /** STF-04: «باز کردنِ قفلِ PIN ِ صندوق» ِ همین کارمند */
  val tillPinUnlock: () -> Unit = {},
  /** STF-04: «برداشتنِ PIN ِ صندوق» — برگهٔ تأیید */
  val tillPinRemove: () -> Unit = {},
)

/**
 *  «کارمند»: نام و قرصِ نقش، کارتِ «مجوزِ کارمندان» (کلیدهایی که سرور می‌پذیرد —
 *  فقط صاحب دکان عوض می‌کند) و دستگاه‌های همین کارمند با وضعیت (رنگ + برچسب) و
 *  «از کار انداختن» (تأیید) یا «برگرداندن».
 */
@Composable
internal fun MemberContent(
  team: TeamData, member: MemberDto?, canEdit: Boolean, busy: Boolean, actions: MemberActions,
  tillPin: TillPinMemberModel.Row? = null,
) {
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
            val keysPage = team.staffKeys
            val canRevoke = MemberModel.canRevoke(keysPage, member)
            //  «شاید همان <نام> است با دستگاهِ تازه» — فقط نشانه؛ حساب‌ها هرگز خودبه‌خود یکی نمی‌شوند
            MemberModel.hintsOf(keysPage, member, team.members).forEach { h ->
              V3Section(h.title) {
                Text(h.body, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
                if (canRevoke && !busy) {
                  V3TextButton(StrAccount.removeStale, { actions.removeStale(h.oldMemberId) }, modifier = Modifier.padding(horizontal = 8.dp), danger = true)
                } else Spacer(Modifier.height(12.dp))
              }
            }
            val permsOf = MemberModel.permsOf(team.perms, member)
            if (CustomRoleModel.visibleFor(team.perms, permsOf)) {
              CustomRoleRow(team, permsOf, canEdit && !busy, actions.customRole)
            }
            val perms = MemberModel.permRows(team.perms, permsOf)
            if (perms.isNotEmpty()) {
              V3Section(StrAccount.permsTitle, footer = if (canEdit) StrAccount.permsHelper else StrAccount.permsOwnerOnly) {
                perms.forEachIndexed { i, p ->
                  if (i > 0) V3Divider(start = 16.dp)
                  V3ToggleRow(p.label, p.on, { actions.toggle(p.key, it) }, enabled = canEdit && !busy, modifier = Modifier.v3Stagger(stagger, p.key, i))
                }
              }
            }
            val places = team.scope?.of(member.id, member.userId)?.places
            if (places != null) {
              V3Section(StrAccount.scopeTitle, footer = if (canEdit) StrAccount.scopeHelper else StrAccount.permsOwnerOnly) {
                StaffScope.PLACES.forEachIndexed { i, place ->
                  if (i > 0) V3Divider(start = 16.dp)
                  val on = StaffScope.isOn(places, place)
                  V3ToggleRow(
                    if (place == StaffScope.WAREHOUSE) StrAccount.placeWarehouse else StrAccount.placeShopFloor, on, { actions.place(place, it) },
                    enabled = canEdit && !busy && StaffScope.toggle(places, place, !on) != null,
                  )
                }
              }
            }
            TillPinMemberSection(tillPin, busy, actions.tillPinUnlock, actions.tillPinRemove)
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
            if (MemberModel.keysVisible(keysPage, member)) {
              val keys = MemberModel.keysOf(keysPage, member)
              V3Section(StrAccount.deviceKeysTitle, footer = StrAccount.deviceKeysHelper) {
                if (keys.isEmpty()) Text(StrAccount.noDevices, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.padding(16.dp))
                keys.forEachIndexed { i, k ->
                  if (i > 0) V3Divider(start = 64.dp)
                  val kr = MemberModel.keyRow(k)
                  //  «باطل کردنِ این دستگاه» زیرِ ردیف، نه کنارش: در گوشیِ ۳۶۰dp دکمهٔ بلند نام و «آخرین استفاده» را تا چند حرف می‌برید
                  V3ListRow(title = kr.title, subtitle = kr.meta, subtitleLines = 3, icon = Icons.Outlined.Key, chevron = false)
                  if (canRevoke && !busy) {
                    V3TextButton(StrAccount.revokeDeviceKey, { actions.revokeKey(kr.id) }, modifier = Modifier.padding(start = 52.dp, bottom = 6.dp), danger = true)
                  }
                }
                if (canRevoke && !busy) {
                  V3Divider(start = 16.dp)
                  V3TextButton(StrAccount.logoutEverywhere, actions.logoutAll, modifier = Modifier.padding(horizontal = 8.dp), danger = true)
                }
              }
            }
          }
        }
      }
    }
  }
}

/** STF-01: «نقشِ سفارشی» ِ همین عضو — صاحب از برگهٔ انتخاب عوض می‌کند (نقشِ عضو ⇐ نقشِ پایه) */
@Composable
private fun CustomRoleRow(team: TeamData, m: ir.vil3ntec.tohid.core.model.MemberPermsDto?, enabled: Boolean, onPick: (String?) -> Unit) {
  val roles = team.perms?.customRoles.orEmpty()
  val choices = CustomRoleModel.choices(roles)
  val current = CustomRoleModel.currentOf(team.perms, m)
  var picking by remember { mutableStateOf(false) }
  V3Section(StrAccount.customRoleSection, footer = StrAccount.customRoleMemberHelper) {
    V3PickerRow(
      label = StrAccount.customRoleSection,
      value = choices.firstOrNull { it.id == current }?.label ?: StrAccount.customRoleNone,
      onClick = { if (enabled) picking = true },
    )
  }
  if (picking) {
    V3PickerSheet(
      title = StrAccount.customRoleSection,
      options = choices.map { it.label },
      selected = choices.firstOrNull { it.id == current }?.label ?: StrAccount.customRoleNone,
      onPick = { label -> picking = false; val id = choices.firstOrNull { it.label == label }?.id; if (id != current) onPick(id) },
      onDismiss = { picking = false },
      allowNew = false,
    )
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
  //  STF-04: حالِ PIN ِ صندوق (پرچمِ `shop.till_pin`؛ خاموش ⇒ بی بخش) و اختیارِ همان سرور
  val tillPins = remember(context) { ir.vil3ntec.tohid.data.repo.TillPinRepository(Backend.api(context)) }
  LaunchedEffect(id) { if (Backend.isReady(context)) team.tillPins = if (tillPins.enabled()) tillPins.members().valueOrNull() else null }
  val viewerRole = remember { ShopRole.current(context) }
  val viewerId = remember { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountId }.getOrDefault("") }
  val pinRow = member?.let { TillPinMemberModel.rowOf(team.tillPins, it, viewerRole, viewerId) }
  var removingPin by remember { mutableStateOf(false) }
  fun pinWork(work: suspend () -> ir.vil3ntec.tohid.core.net.ApiResult<Unit>, done: String) {
    busy = true
    TeamSession.scope.launch {
      work()
        .onSuccess { toasts.show(done, V3Tone.SUCCESS); team.tillPins = tillPins.members().valueOrNull() }
        .onFailure { toasts.show(it.userMessage, V3Tone.ERROR) }
      busy = false
    }
  }
  if (removingPin && member != null) {
    ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet(
      title = StrAccount.tillPinRemoveTitle(TeamModel.removeName(member)),
      message = StrAccount.tillPinRemoveBody,
      confirm = StrAccount.tillPinRemove,
      onConfirm = { removingPin = false; pinWork({ tillPins.remove(member.id) }, StrAccount.tillPinRemoved) },
      onDismiss = { removingPin = false },
    )
  }
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
      place = { place, on ->
        val next = member?.let { m -> team.scope?.of(m.id, m.userId)?.let { StaffScope.toggle(it.places, place, on) } }
        if (member != null && next != null) {
          busy = true
          TeamSession.scope.launch {
            shops.setStaffScope(member.id, next)
              .onSuccess { team.scope = shops.staffScopeMembers() }
              .onFailure { toasts.show(it.userMessage, V3Tone.ERROR) }
            busy = false
          }
        }
      },
      customRole = { roleId ->
        val layer = member?.let { MemberModel.permsOf(team.perms, it) }
        if (layer != null) {
          busy = true
          TeamSession.scope.launch {
            shops.assignCustomRole(layer.id, roleId)
              .onSuccess { toasts.show(StrAccount.customRoleAssigned, V3Tone.SUCCESS); team.reload(shops, canEdit) }
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
      revokeKey = { nav.open(AccountPaths.revokeDeviceKey(it)) },
      logoutAll = { nav.open(AccountPaths.logoutEverywhere(id)) },
      removeStale = { nav.open(AccountPaths.removeStale(id, it)) },
      tillPinUnlock = { if (member != null && !busy) pinWork({ tillPins.unlock(member.id) }, StrAccount.tillPinUnlocked) },
      tillPinRemove = { if (!busy) removingPin = true },
    ),
    tillPin = pinRow,
  )
}
