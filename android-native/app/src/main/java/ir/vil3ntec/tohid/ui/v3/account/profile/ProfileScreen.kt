package ir.vil3ntec.tohid.ui.v3.account.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.InsertChart
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.sync.License
import ir.vil3ntec.tohid.sync.LicenseGuard
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.ui.screens.SUBSCRIPTION_WARN_DAYS
import ir.vil3ntec.tohid.ui.screens.SubscriptionPulse
import ir.vil3ntec.tohid.ui.screens.frosted
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.screens.rememberAlerts
import ir.vil3ntec.tohid.ui.v3.V3Action
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.account.AccountFormat
import ir.vil3ntec.tohid.ui.v3.account.AccountLinks
import ir.vil3ntec.tohid.ui.v3.account.AccountPaths
import ir.vil3ntec.tohid.ui.v3.account.AccountSession
import ir.vil3ntec.tohid.ui.v3.modes.ModeSwitcher
import ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Pressable
import ir.vil3ntec.tohid.ui.v3.v3Stagger

/** آن‌چه صفحهٔ «حساب من» نشان می‌دهد — از دادهٔ واقعی (`ProfileRoute`) یا عکس‌ها */
internal data class ProfileUi(
  val account: ProfileHomeModel.Account,
  val shopName: String,
  val accountId: String,
  val avatarVersion: Int = 0,
  val doors: List<ProfileHomeModel.Door>,
  val banner: ProfileHomeModel.Banner,
  val menu: List<ProfileHomeModel.MenuItem>,
  val alerts: Boolean,
)

/**
 *  «حساب من» — r4 روی کیتِ نسخهٔ ۳: عنوانِ درشتِ جمع‌شونده و زنگ (⇐ `notifications`)،
 *  عکس روی لبهٔ کارتِ شیشه‌ای، قرصِ نقش، سه کادرِ آمار که درِ بخشِ خودشان‌اند،
 *  نوارِ تیرهٔ اشتراک، فهرستِ سفید و سوییچرِ حالتِ برنامه (فقط حالت‌هایی که کاربر دارد).
 */
@Composable
internal fun ProfileContent(
  ui: ProfileUi,
  bottomReserve: Dp,
  onOpen: (String) -> Unit,
  modes: (@Composable () -> Unit)? = null,
) {
  val stagger = rememberV3Stagger()
  V3Scaffold(
    title = Str.myAccount,
    //  زمینهٔ مه‌گرفتهٔ r4 زیرِ عنوانِ درشت هم (سربرگِ شفاف)
    backdrop = Modifier.frosted(PhoneV3.colors),
    actions = listOf(V3Action(Icons.Outlined.Notifications, StrKit.notifications, dot = ui.alerts) { onOpen(AccountLinks.NOTIFICATIONS) }),
    bottomReserve = bottomReserve,
  ) { pad ->
    Column(
      Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(4.dp))
        val avatar = 120.dp
        val overlap = 26.dp
        Box(Modifier.fillMaxWidth().v3Stagger(stagger, "card", 0)) {
          ProfileCard(
            name = ProfileHomeModel.displayName(ui.account),
            contact = ProfileHomeModel.contact(ui.account, ui.shopName),
            contactLtr = ui.account.signedIn,
            role = ProfileHomeModel.badge(ui.account),
            roleKind = AccountFormat.roleOf(ui.account.role),
            doors = ui.doors,
            banner = ui.banner,
            onDoor = onOpen,
            onBanner = { onOpen(ui.banner.action.route) },
            modifier = Modifier.padding(top = avatar - overlap).zIndex(0f),
          )
          ProfileAvatar(
            accountId = ui.accountId,
            version = ui.avatarVersion,
            size = avatar,
            modifier = Modifier
              .align(Alignment.TopCenter)
              .zIndex(1f)
              .v3Pressable(label = StrAccount.changePhotoA11y, scale = 0.96f) {
                onOpen(if (ui.account.signedIn) AccountPaths.EDIT else AccountLinks.SIGN_IN)
              },
          )
        }
        Spacer(Modifier.height(16.dp))
        V3Card(radius = 24.dp, padding = PaddingValues(vertical = 4.dp)) {
          ui.menu.forEachIndexed { i, item ->
            if (i > 0) V3Divider(start = 58.dp)
            V3ListRow(
              title = item.label,
              icon = menuIcon(item.item),
              tile = false,
              minHeight = 60.dp,
              danger = item.danger,
              chevron = !item.danger,
              modifier = Modifier.v3Stagger(stagger, item.item, i + 1),
              onClick = { onOpen(item.route) },
            )
          }
        }
        modes?.invoke()
        Spacer(Modifier.height(24.dp + pad.calculateBottomPadding()))
      }
    }
  }
}

private fun menuIcon(item: ProfileHomeModel.Item) = when (item) {
  ProfileHomeModel.Item.EDIT -> Icons.Outlined.ManageAccounts
  ProfileHomeModel.Item.DEVICES -> Icons.Outlined.Devices
  ProfileHomeModel.Item.PLAN -> Icons.Outlined.StarBorder
  ProfileHomeModel.Item.STATS -> Icons.Outlined.InsertChart
  ProfileHomeModel.Item.SETTINGS -> Icons.Outlined.Settings
  ProfileHomeModel.Item.SUPPORT -> Icons.Outlined.SupportAgent
  ProfileHomeModel.Item.SIGN_OUT -> Icons.AutoMirrored.Outlined.Logout
}

/**
 *  مسیرِ `profile`: حساب از `SyncStore` (بی‌اینترنت هم)، و اگر آنتن بود بی‌صدا از
 *  سرور تازه می‌شود؛ اشتراک از مجوزِ گوشی و `SubscriptionPulse` (همان حرفِ سربرگ).
 */
@Composable
internal fun RouteScope.ProfileRoute() {
  val context = LocalContext.current
  //  خروج (برگهٔ روی همین صفحه) نسخه را بالا می‌برد ⇒ حساب از نو خوانده می‌شود
  val session = AccountSession.version.intValue
  val state = remember(session) { SyncStore(context) }
  val signedIn = !state.accessToken.isNullOrBlank()
  var name by remember(session) { mutableStateOf(state.accountName) }
  var email by remember(session) { mutableStateOf(state.accountEmail) }
  var phone by remember(session) { mutableStateOf(state.accountPhone) }
  LaunchedEffect(signedIn) {
    if (!signedIn || !Backend.isReady(context)) return@LaunchedEffect
    Backend.account(context).me().onSuccess { me ->
      state.accountName = me.user.name
      state.accountEmail = me.user.email.orEmpty()
      state.accountPhone = me.user.phone.orEmpty()
      state.accountCreatedAt = me.user.createdAt
      name = me.user.name
      email = me.user.email.orEmpty()
      phone = me.user.phone.orEmpty()
    }
  }
  val shopName = remember { context.getSharedPreferences("tohid", android.content.Context.MODE_PRIVATE).getString("store_name", "").orEmpty() }
  val role = remember(signedIn) { if (signedIn) ShopRole.current(context) else "" }
  val canSettings = remember { ShopRole.canOpenSettings(context) }
  val plan = if (signedIn) rememberPlan() else null
  val alerts = rememberAlerts(data).isNotEmpty()
  val ui = ProfileUi(
    account = ProfileHomeModel.Account(signedIn, name, email, phone, role),
    shopName = shopName,
    accountId = state.accountId.ifBlank { "local" },
    doors = ProfileHomeModel.doors(data.sales.size, data.products.size, data.debtors.size),
    banner = ProfileHomeModel.banner(signedIn, plan, SUBSCRIPTION_WARN_DAYS),
    menu = ProfileHomeModel.menu(signedIn, canSettings),
    alerts = alerts,
  )
  ProfileContent(ui, bottomReserve, onOpen = { nav.open(it) }, modes = if (LocalDeskLayout.current) null else ({ ModeSwitcher() }))
}

/** وضعیتِ اشتراکِ همین حساب — مجوزِ امضاشدهٔ گوشی، وگرنه حرفِ سرور (`SubscriptionPulse`) */
@Composable
private fun rememberPlan(): ProfileHomeModel.Plan {
  val context = LocalContext.current
  val status = remember { runCatching { LicenseGuard.status(context, SyncStore(context)) }.getOrNull() }
  LaunchedEffect(Unit) { SubscriptionPulse.refresh(context) }
  return ProfileHomeModel.plan(
    localActive = status?.state == License.State.ACTIVE,
    localDays = status?.daysLeft() ?: 0,
    localExpired = status?.state == License.State.EXPIRED || status?.state == License.State.GRACE,
    serverActive = SubscriptionPulse.active,
    serverDays = SubscriptionPulse.days,
    serverTrial = SubscriptionPulse.trial,
    warnDays = SUBSCRIPTION_WARN_DAYS,
  )
}
