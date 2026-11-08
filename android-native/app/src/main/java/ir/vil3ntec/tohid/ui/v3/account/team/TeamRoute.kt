package ir.vil3ntec.tohid.ui.v3.account.team

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.KeyOff
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.account.AccountLinks
import ir.vil3ntec.tohid.ui.v3.account.AccountPaths
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Ask { ROTATE, DISABLE }

/** همان `ShopRepository` ِ برگه‌های کارمندان (یک جا، نه در هر برگه) */
private val RouteScope.shopsRepo: ir.vil3ntec.tohid.data.repo.ShopRepository
  @Composable get() {
    val context = LocalContext.current
    return remember { Backend.shop(context) }
  }

private val RouteScope.isOwner: Boolean
  @Composable get() {
    val context = LocalContext.current
    return remember { ShopRole.current(context) == ShopRole.OWNER }
  }

/**
 *  مسیرِ `team`: همان فراخوان‌های صفحهٔ پیشین (`ShopRepository`) — فقط ظاهر نو شد.
 *  کدِ تازهٔ دکان پیش از ساخته شدن می‌پرسد (هشدارِ امروز)، خاموش کردن هم.
 */
@Composable
internal fun RouteScope.TeamRoute() {
  val context = LocalContext.current
  val shops = remember { Backend.shop(context) }
  val signedIn = Backend.isReady(context)
  val owner = isOwner
  val team = TeamSession.data
  val clipboard = LocalClipboardManager.current
  var rotating by remember { mutableStateOf(false) }
  var ask by remember { mutableStateOf<Ask?>(null) }
  LaunchedEffect(signedIn) { if (signedIn) team.reload(shops, owner) }

  fun rotateNow() {
    rotating = true
    TeamSession.scope.launch {
      shops.rotateStandingCode()
        .onSuccess { team.standing = it.code; team.standingOff = false; toasts.show(StrAccount.codeRotated, V3Tone.SUCCESS) }
        .onFailure { team.error = it.userMessage }
      rotating = false
    }
  }

  TeamContent(
    team, signedIn, owner, rotating,
    TeamActions(
      back = backOrNull,
      signIn = { nav.open(AccountLinks.SIGN_IN) },
      copy = { code ->
        clipboard.setText(AnnotatedString(code))
        Haptics.confirm(context)
        toasts.show(StrAccount.codeCopied, V3Tone.SUCCESS)
      },
      rotate = { if (team.standingOff) rotateNow() else ask = Ask.ROTATE },
      disable = { ask = Ask.DISABLE },
      member = { nav.open(AccountPaths.member(it)) },
      remove = { nav.open(AccountPaths.removeMember(it)) },
      revoke = { id ->
        TeamSession.scope.launch {
          shops.revokeStaffCode(id)
            .onSuccess { toasts.show(StrAccount.codeRevoked, V3Tone.SUCCESS); team.reload(shops, owner) }
            .onFailure { toasts.show(it.userMessage, V3Tone.ERROR) }
        }
      },
      newCode = { nav.open(AccountPaths.NEW_CODE) },
      closeFresh = { team.fresh = null },
    ),
    staff = { TeamStaffSections(shops, owner) },
  )

  when (ask) {
    Ask.ROTATE -> V3ConfirmSheet(
      title = StrAccount.rotateTitle, message = StrAccount.rotateBody, confirm = StrAccount.newShopCode,
      icon = Icons.Outlined.Refresh, danger = false,
      onConfirm = ::rotateNow, onDismiss = { ask = null },
    )
    Ask.DISABLE -> V3ConfirmSheet(
      title = StrAccount.disableTitle, message = StrAccount.disableBody, confirm = StrAccount.disableConfirm,
      icon = Icons.Outlined.KeyOff,
      onConfirm = {
        TeamSession.scope.launch {
          shops.disableStandingCode()
            .onSuccess { team.standing = ""; team.standingOff = true; toasts.show(StrAccount.codeDisabled, V3Tone.SUCCESS) }
            .onFailure { toasts.show(it.userMessage, V3Tone.ERROR) }
        }
      },
      onDismiss = { ask = null },
    )
    null -> Unit
  }
}

/** برگهٔ `team/members/{id}/remove`: «برداشتنِ X؟» با متنِ امروز؛ صاحب دکان برداشتنی نیست */
@Composable
internal fun RouteScope.RemoveMemberRoute(id: String) {
  val shops = shopsRepo
  val team = TeamSession.data
  val owner = isOwner
  val member = team.member(id)
  if (member != null && !TeamModel.memberRow(member).removable) { LaunchedEffect(id) { back() }; return }
  V3ConfirmSheet(
    title = StrAccount.removeTitle(TeamModel.removeName(member)),
    message = StrAccount.removeBody,
    confirm = StrAccount.removeConfirm,
    icon = Icons.Outlined.PersonRemove,
    onConfirm = {
      TeamSession.scope.launch {
        shops.removeMember(id)
          .onSuccess {
            toasts.show(StrAccount.memberRemoved, V3Tone.SUCCESS)
            //  ردیف اول نرم جمع می‌شود، بعد فهرست از سرور
            team.leaving = team.leaving + id
            delay(MotionTokens.LONG.toLong())
            team.reload(shops, owner)
            team.leaving = team.leaving - id
          }
          .onFailure { toasts.show(it.userMessage, V3Tone.ERROR) }
      }
      //  از صفحهٔ همان کارمند آمده بود؟ آن صفحه دیگر معنا ندارد
      //  (برگه پیش‌تر بسته شده، پس `nav.back()` نه `back()` ِ همین مسیر)
      if (nav.current.startsWith("team/members/")) nav.back()
    },
    onDismiss = { back() },
  )
}

/** برگهٔ `team/devices/{id}/block`: دو جملهٔ هشدارِ امروز، فقط صاحب دکان */
@Composable
internal fun RouteScope.BlockDeviceRoute(id: String) {
  val shops = shopsRepo
  val team = TeamSession.data
  val owner = isOwner
  if (!owner) { LaunchedEffect(id) { back() }; return }
  V3ConfirmSheet(
    title = StrAccount.blockTitle,
    message = StrAccount.blockBody,
    confirm = StrAccount.blockConfirm,
    icon = Icons.Outlined.Block,
    onConfirm = {
      TeamSession.scope.launch {
        shops.disableDevice(id)
          .onSuccess { toasts.show(StrAccount.deviceDisabled, V3Tone.SUCCESS); team.reload(shops, owner) }
          .onFailure { toasts.show(it.userMessage, V3Tone.ERROR) }
      }
    },
    onDismiss = { back() },
  )
}

/**
 *  برگهٔ `team/device-keys/{id}/revoke`: «باطل کردنِ این دستگاه» — رازِ ورود با کد و نشست‌های همان دستگاه.
 *  سرور تصمیم می‌گیرد (members.manage) و دوباره‌زدنی است؛ فهرست پس از کار از سرور خوانده می‌شود.
 */
@Composable
internal fun RouteScope.RevokeDeviceKeyRoute(id: String) {
  val shops = shopsRepo
  val team = TeamSession.data
  val owner = isOwner
  if (team.staffKeys?.canRevoke != true) { LaunchedEffect(id) { back() }; return }
  V3ConfirmSheet(
    title = StrAccount.revokeDeviceKey,
    message = StrAccount.revokeDeviceKeyBody,
    confirm = StrAccount.revokeDeviceKeyConfirm,
    icon = Icons.Outlined.KeyOff,
    onConfirm = {
      TeamSession.scope.launch {
        shops.revokeStaffKey(id)
          .onSuccess { toasts.show(StrAccount.deviceKeyRevoked, V3Tone.SUCCESS); team.reload(shops, owner) }
          .onFailure { toasts.show(it.userMessage, V3Tone.ERROR) }
      }
    },
    onDismiss = { back() },
  )
}

/** برگهٔ `team/members/{id}/logout-all`: «خروج از همهٔ دستگاه‌ها» برای همین کارمند */
@Composable
internal fun RouteScope.LogoutEverywhereRoute(id: String) {
  val shops = shopsRepo
  val team = TeamSession.data
  val owner = isOwner
  val member = team.member(id)
  if (member == null || !MemberModel.canRevoke(team.staffKeys, member)) { LaunchedEffect(id) { back() }; return }
  V3ConfirmSheet(
    title = StrAccount.logoutEverywhereTitle(TeamModel.removeName(member)),
    message = StrAccount.logoutEverywhereBody,
    confirm = StrAccount.logoutEverywhereConfirm,
    icon = Icons.AutoMirrored.Outlined.Logout,
    onConfirm = {
      TeamSession.scope.launch {
        shops.logoutMemberEverywhere(id)
          .onSuccess { toasts.show(StrAccount.loggedOutEverywhere, V3Tone.SUCCESS); team.reload(shops, owner) }
          .onFailure { toasts.show(it.userMessage, V3Tone.ERROR) }
      }
    },
    onDismiss = { back() },
  )
}

/**
 *  برگهٔ `team/members/{id}/stale/{old}`: «برداشتنِ عضوِ کهنه» از نشانهٔ «شاید همان» — همان برداشتنِ امروز
 *  (`DELETE /api/shop/members/:id`)؛ فروش و سابقهٔ عضوِ کهنه می‌ماند و ⛔ هیچ حسابی یکی نمی‌شود. صفحهٔ عضوِ تازه می‌ماند.
 */
@Composable
internal fun RouteScope.RemoveStaleRoute(id: String, old: String) {
  val shops = shopsRepo
  val team = TeamSession.data
  val owner = isOwner
  val member = team.member(id)
  val hint = member?.let { m -> MemberModel.hintsOf(team.staffKeys, m, team.members).firstOrNull { it.oldMemberId == old } }
  if (member == null || hint == null || !MemberModel.canRevoke(team.staffKeys, member)) { LaunchedEffect(id, old) { back() }; return }
  V3ConfirmSheet(
    title = StrAccount.removeStaleTitle(hint.oldName),
    message = StrAccount.removeStaleBody,
    confirm = StrAccount.removeConfirm,
    icon = Icons.Outlined.PersonRemove,
    onConfirm = {
      TeamSession.scope.launch {
        shops.removeMember(old)
          .onSuccess { toasts.show(StrAccount.memberRemoved, V3Tone.SUCCESS); team.reload(shops, owner) }
          .onFailure { toasts.show(it.userMessage, V3Tone.ERROR) }
      }
    },
    onDismiss = { back() },
  )
}
