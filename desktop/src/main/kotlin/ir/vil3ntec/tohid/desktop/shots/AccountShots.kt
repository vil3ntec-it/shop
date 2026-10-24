package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import ir.vil3ntec.tohid.core.model.CatalogEntryDto
import ir.vil3ntec.tohid.core.model.DeviceMemberDto
import ir.vil3ntec.tohid.core.model.MemberDto
import ir.vil3ntec.tohid.core.model.MemberPermsDto
import ir.vil3ntec.tohid.core.model.PermStateDto
import ir.vil3ntec.tohid.core.model.ShopDeviceDto
import ir.vil3ntec.tohid.core.model.ShopPermsDto
import ir.vil3ntec.tohid.core.model.StaffCodeDto
import ir.vil3ntec.tohid.core.model.SupportMessageDto
import ir.vil3ntec.tohid.sync.License
import ir.vil3ntec.tohid.ui.components.common.PhoneNav
import ir.vil3ntec.tohid.ui.v3.account.profile.ProfileContent
import ir.vil3ntec.tohid.ui.v3.account.profile.ProfileEditContent
import ir.vil3ntec.tohid.ui.v3.account.profile.ProfileEditUi
import ir.vil3ntec.tohid.ui.v3.account.profile.ProfileHomeModel
import ir.vil3ntec.tohid.ui.v3.account.profile.ProfileUi
import ir.vil3ntec.tohid.ui.v3.account.profile.SignOutActions
import ir.vil3ntec.tohid.ui.v3.account.profile.SignOutContent
import ir.vil3ntec.tohid.ui.v3.account.profile.SignOutModel
import ir.vil3ntec.tohid.ui.v3.account.profile.SignOutUi
import ir.vil3ntec.tohid.ui.v3.account.subscription.PlansActions
import ir.vil3ntec.tohid.ui.v3.account.subscription.PlansContent
import ir.vil3ntec.tohid.ui.v3.account.subscription.PlansModel
import ir.vil3ntec.tohid.ui.v3.account.subscription.PlansUi
import ir.vil3ntec.tohid.ui.v3.account.subscription.RedeemActions
import ir.vil3ntec.tohid.ui.v3.account.subscription.RedeemContent
import ir.vil3ntec.tohid.ui.v3.account.subscription.RedeemUi
import ir.vil3ntec.tohid.ui.v3.account.subscription.SubscriptionContent
import ir.vil3ntec.tohid.ui.v3.account.subscription.SubscriptionUi
import ir.vil3ntec.tohid.ui.v3.account.support.SupportContent
import ir.vil3ntec.tohid.ui.v3.account.support.SupportModel
import ir.vil3ntec.tohid.ui.v3.account.support.SupportUi
import ir.vil3ntec.tohid.ui.v3.account.team.MemberActions
import ir.vil3ntec.tohid.ui.v3.account.team.MemberContent
import ir.vil3ntec.tohid.ui.v3.account.team.NewCodeContent
import ir.vil3ntec.tohid.ui.v3.account.team.RevealContent
import ir.vil3ntec.tohid.ui.v3.account.team.TeamActions
import ir.vil3ntec.tohid.ui.v3.account.team.TeamContent
import ir.vil3ntec.tohid.ui.v3.account.team.TeamData
import ir.vil3ntec.tohid.ui.v3.modes.ModeSwitcher
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 *  عکس‌های گروهِ G7 حساب — هر حالتِ صفحه‌های تازه با دادهٔ نمایشی (هیچ دادهٔ واقعیِ
 *  دکانی نیست): «حساب من» برای صاحب/مدیر/فروشنده/مهمان، تمام‌شده و دو روز مانده؛
 *  ویرایش (شمارهٔ قفل)؛ خروج A/B/C؛ کارمندان (بی‌حساب / کدِ خاموش / پُر)؛ کدِ تازه و
 *  نمایشِ یک‌باره؛ مجوزِ کارمند؛ اشتراک فعال/رو به پایان/تمام/مهمان؛ پلن‌ها؛ کدِ
 *  اشتراک؛ گفت‌وگوی پشتیبانی. روشن/تیره × فارسی/انگلیسی با `groupshots:account`.
 */
object AccountShots : GroupShots {
  override val group = "account"
  override val shots = listOf(
    Shot("profile-owner", content = { Profile("owner", localDays = 40, modes = true) }),
    Shot("profile-manager", content = { Profile("manager", localDays = 40) }),
    Shot("profile-seller", content = { Profile("staff", localDays = 40, settings = false) }),
    Shot("profile-guest", content = { Profile(null) }),
    Shot("profile-expired", content = { Profile("owner", expired = true) }),
    Shot("profile-2days", content = { Profile("owner", localDays = 2) }),
    Shot("profile-route", route = "profile"),
    Shot("edit-locked", content = { Edit(phone = "0700123456") }),
    Shot("edit-claim", content = { Edit(phone = "", error = true) }),
    Shot("signout-a", content = { SignOut(SignOutModel.Stage.CONFIRM) }),
    Shot("signout-b", content = { SignOut(SignOutModel.Stage.BLOCKED) }),
    Shot("signout-c", content = { SignOut(SignOutModel.Stage.WRITING) }),
    Shot("signout-force", content = { SignOut(SignOutModel.Stage.FORCE) }),
    Shot("team-guest", content = { Team(signedIn = false) }),
    Shot("team-code-off", content = { Team(codeOff = true) }),
    Shot("team-full", content = { Team() }),
    Shot("team-new-code", content = { Box { Team(); NewCodeContent("manager", "3", "7", true, false, {}, {}, {}, {}, {}) } }),
    Shot("team-reveal", content = { Box { Team(); RevealContent("K7Q2-M9XP", {}, {}, {}) } }),
    Shot("member-perms", content = { Member() }),
    Shot("subscription-active", content = { Subscription(days = 40, active = true, color = "green", progress = 30) }),
    Shot("subscription-ending", content = { Subscription(days = 2, active = true, color = "red", progress = 94) }),
    Shot("subscription-expired", content = { Subscription(days = 0, active = false, color = "grey", progress = 100) }),
    Shot("subscription-guest", content = { SubscriptionContent(SubscriptionUi(signedIn = false, sub = null), {}, {}, {}) }),
    Shot("plans", content = { Plans() }),
    Shot("plans-guest", content = { Plans(signedIn = false, license = null) }),
    Shot("redeem", content = { Box { Plans(); Redeem() } }),
    Shot("support", content = { Support() }),
    Shot("support-empty", content = { SupportContent(SupportUi(emptyList()), {}, {}, {}) }),
  )
}

private const val DAY = 86_400_000L

@Composable
private fun Profile(role: String?, localDays: Int = 0, expired: Boolean = false, settings: Boolean = true, modes: Boolean = false) {
  val signedIn = role != null
  val plan = if (signedIn) ProfileHomeModel.plan(localDays > 0, localDays, expired, false, 0, false, 7) else null
  val ui = ProfileUi(
    account = ProfileHomeModel.Account(signedIn, if (signedIn) "کریم احمدی" else "", "karim@example.af", if (signedIn) "+93 700 123 456" else "", role.orEmpty()),
    shopName = "دکانِ نمونه",
    accountId = "shot",
    doors = ProfileHomeModel.doors(128, 342, 17),
    banner = ProfileHomeModel.banner(signedIn, plan, 7),
    menu = ProfileHomeModel.menu(signedIn, settings),
    alerts = true,
  )
  ProfileContent(ui, PhoneNav.RESERVE, onOpen = {}, modes = if (modes) ({ ModeSwitcher() }) else null)
}

@Composable
private fun Edit(phone: String, error: Boolean = false) {
  ProfileEditContent(
    ProfileEditUi(
      accountId = "shot", avatarVersion = 0, name = "کریم احمدی", email = "karim@example.af", phone = phone,
      newPhone = if (error) "0700123456" else "", phoneError = if (error) ir.vil3ntec.tohid.core.i18n.StrAccount.phoneNeedsInternet else null,
      shopName = "دکانِ نمونه", createdAt = System.currentTimeMillis() - 430 * DAY, dirty = error,
    ),
    {}, {}, {}, {}, {}, {},
  )
}

@Composable
private fun SignOut(stage: SignOutModel.Stage) {
  Box {
    Profile("owner", localDays = 40)
    SignOutContent(
      SignOutUi(stage, pending = if (stage == SignOutModel.Stage.CONFIRM) 0 else 3, online = false, backupPath = "/Documents/MyShop/backup-1405-07-28.zip"),
      SignOutActions({}, {}, {}, {}, {}),
      onDismiss = {},
    )
  }
}

private fun demoTeam(codeOff: Boolean): TeamData = TeamData().apply {
  loaded = true
  members = listOf(
    MemberDto(id = "m1", userId = "u1", name = "کریم احمدی", phone = "0700123456", role = "owner"),
    MemberDto(id = "m2", userId = "u2", name = "نوید", phone = "0799111222", role = "manager"),
    MemberDto(id = "m3", userId = "u3", name = "سمیع", email = "sami@example.af", role = "staff"),
    MemberDto(id = "m4", userId = "u4", name = "", role = "courier", status = "disabled"),
  )
  maxMembers = 5
  codes = listOf(
    StaffCodeDto(id = "c1", hint = "AB", role = "staff", maxUses = 5, usedCount = 2, expiresAt = System.currentTimeMillis() + 6 * DAY),
    StaffCodeDto(id = "c2", hint = "QX", role = "manager", maxUses = 0),
    StaffCodeDto(id = "c3", hint = "ZK", role = "staff", maxUses = 1, usedCount = 1),
  )
  standing = if (codeOff) "" else "SHOP-7K2Q"
  standingOff = codeOff
  courierAllowed = true
  perms = ShopPermsDto(
    catalog = listOf(
      CatalogEntryDto("settings.write", edit = "restrict", defaults = mapOf("manager" to "allow")),
      CatalogEntryDto("audit.view", edit = "restrict", defaults = mapOf("manager" to "allow")),
      CatalogEntryDto("reports.money", edit = "full", defaults = mapOf("manager" to "allow", "staff" to "allow")),
    ),
    members = listOf(
      MemberPermsDto(id = "m2", userId = "u2", role = "manager", effective = mapOf("settings.write" to PermStateDto("allow"), "audit.view" to PermStateDto("deny"), "reports.money" to PermStateDto("allow"))),
    ),
  )
  devices = listOf(
    ShopDeviceDto(id = "d1", name = "Galaxy A52", lastSeenAt = System.currentTimeMillis() - DAY, member = DeviceMemberDto("u2", "manager", "نوید")),
    ShopDeviceDto(id = "d2", name = "Redmi 9", disabledAt = 1, blockedAttempts = 3, member = DeviceMemberDto("u2", "manager", "نوید")),
    ShopDeviceDto(id = "d3", platform = "desktop", status = "signed_out", member = DeviceMemberDto("u2", "manager", "نوید")),
  )
}

private val noTeam = TeamActions(null, {}, {}, {}, {}, {}, {}, {}, {}, {})

@Composable
private fun Team(signedIn: Boolean = true, codeOff: Boolean = false) {
  val team = remember(codeOff) { demoTeam(codeOff) }
  TeamContent(team, signedIn, isOwner = true, rotating = false, actions = noTeam)
}

@Composable
private fun Member() {
  val team = remember { demoTeam(false) }
  MemberContent(team, team.member("m2"), canEdit = true, busy = false, actions = MemberActions({}, { _, _ -> }, {}, {}, {}))
}

private fun json(vararg kv: Pair<String, Any>): JsonObject = buildJsonObject {
  kv.forEach { (k, v) ->
    when (v) {
      is Boolean -> put(k, JsonPrimitive(v))
      is Number -> put(k, JsonPrimitive(v))
      else -> put(k, JsonPrimitive(v.toString()))
    }
  }
}

@Composable
private fun Subscription(days: Int, active: Boolean, color: String, progress: Int) {
  val now = System.currentTimeMillis()
  val sub = json(
    "plan" to "۶ ماهه", "active" to active, "status" to if (active) "active" else "expired", "daysLeft" to days,
    "color" to color, "progress" to progress, "startsAt" to now - (180 - days) * DAY, "endsAt" to now + days * DAY,
  )
  SubscriptionContent(
    SubscriptionUi(
      signedIn = true, sub = sub,
      devices = listOf(json("name" to "Galaxy A52", "last_seen_at" to now - DAY), json("name" to "Desktop", "last_seen_at" to now)),
      payments = listOf(json("planTitle" to "۶ ماهه", "amountLabel" to "2,500 افغانی")),
      notices = emptyList(),
    ),
    {}, {}, {},
  )
}

@Composable
private fun Plans(signedIn: Boolean = true, license: License.State? = License.State.ACTIVE) {
  val options = PlansModel.options(null)
  PlansContent(
    PlansUi(
      state = PlansModel.state(license, 12, System.currentTimeMillis() + 12 * DAY, "۶ ماهه", false, 0, false, 7, 7),
      options = options, picked = PlansModel.pick(options, null), currency = "افغانی", signedIn = signedIn, salesNumber = "",
      storeBuild = false,
    ),
    PlansActions({}, {}, {}, {}, {}),
  )
}

@Composable
private fun Redeem() {
  RedeemContent(RedeemUi(code = "4821", signedIn = true, computerId = "7F3A-91C2-55D0"), RedeemActions({}, {}, {}, {}, {}, {}, {}), onDismiss = {})
}

@Composable
private fun Support() {
  val now = System.currentTimeMillis()
  val messages = listOf(
    SupportMessageDto(id = "1", sender = "user", body = "سلام، چاپگرِ رسید وصل نمی‌شود.", createdAt = now - DAY - 3_600_000),
    SupportMessageDto(id = "2", sender = "admin", senderName = "پشتیبانی", body = "سلام! از «تنظیمات ⇐ دستگاه‌ها» چاپگر را دوباره جفت کنید.", createdAt = now - DAY),
    SupportMessageDto(id = "3", sender = "system", body = "اشتراکِ شما سه روزِ دیگر تمام می‌شود.", createdAt = now - 7_200_000),
    SupportMessageDto(id = "4", sender = "user", body = "درست شد، ممنون.", createdAt = now - 600_000),
  )
  SupportContent(SupportUi(SupportModel.items(messages, now, pending = "یک سؤالِ دیگر هم دارم"), draft = ""), {}, {}, {})
}
