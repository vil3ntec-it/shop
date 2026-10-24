package ir.vil3ntec.tohid.ui.v3.account.subscription

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WorkspacePremium
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.net.ApiEndpoints
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.sync.v1.HeartbeatCache
import ir.vil3ntec.tohid.sync.v1.SoftLock
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Dark
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Space
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Action
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Skeleton
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.account.AccountLinks
import ir.vil3ntec.tohid.ui.v3.account.AccountPaths
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** آن‌چه «اشتراکِ من» نشان می‌دهد */
internal data class SubscriptionUi(
  val signedIn: Boolean,
  val sub: JsonObject?,
  val devices: List<JsonObject> = emptyList(),
  val payments: List<JsonObject> = emptyList(),
  val notices: List<JsonObject> = emptyList(),
  val note: String = "",
  val busy: Boolean = false,
)

/**
 *  «اشتراکِ من»: کارتِ وارونهٔ پلن (روزِ مانده / دائمی ✓ / تمام شده، نوارِ دوره،
 *  آغاز و پایان) و «تمدید» ⇐ `subscription/plans`؛ هشدارِ هفت‌روزه و تمام‌شده با
 *  همان متن‌های امروز (هیچ داده‌ای پاک نمی‌شود)؛ دستگاه‌ها، پرداخت‌ها و پیام‌های مدیر.
 */
@Composable
internal fun SubscriptionContent(ui: SubscriptionUi, onBack: (() -> Unit)?, onRefresh: () -> Unit, onOpen: (String) -> Unit) {
  V3Scaffold(
    title = Str.mySubscription,
    onBack = onBack,
    notice = null,
    actions = if (ui.signedIn) listOf(V3Action(Icons.Outlined.Refresh, StrAccount.refreshFromServer, onClick = onRefresh)) else emptyList(),
  ) { pad ->
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding())) {
        if (!ui.signedIn) {
          V3EmptyState(Icons.Outlined.WorkspacePremium, StrAccount.signInFirst, body = StrAccount.subSignInBody, action = StrAccount.codeSignIn, onAction = { onOpen(AccountLinks.CODE_SIGN_IN) })
          return@Column
        }
        val sub = ui.sub
        when {
          sub == null && ui.busy -> { Spacer(Modifier.height(8.dp)); V3Skeleton(rows = 3) }
          sub == null -> V3EmptyState(Icons.Outlined.CloudOff, StrAccount.nothingYet, body = ui.note.ifBlank { null }, action = StrAccount.refreshFromServer, onAction = onRefresh)
          else -> {
            Spacer(Modifier.height(8.dp))
            HeroCard(SubscriptionModel.hero(sub))
            when (SubscriptionModel.notice(sub)) {
              SubscriptionModel.Notice.ENDING -> { Spacer(Modifier.height(12.dp)); V3Message(V3Tone.WARNING, StrAccount.warnTitle(SubscriptionModel.hero(sub).days), body = StrAccount.warnBody) }
              SubscriptionModel.Notice.EXPIRED -> { Spacer(Modifier.height(12.dp)); V3Message(V3Tone.ERROR, StrAccount.bannerExpiredTitle, body = StrAccount.expiredBody) }
              SubscriptionModel.Notice.NONE -> Unit
            }
            Spacer(Modifier.height(14.dp))
            V3Button(Str.renew, { onOpen(AccountPaths.PLANS) }, Modifier.fillMaxWidth(), icon = Icons.Outlined.WorkspacePremium, haptic = true)
          }
        }
        if (ui.note.isNotBlank() && sub != null) { Spacer(Modifier.height(10.dp)); V3Message(V3Tone.ERROR, ui.note) }
        Lines(StrAccount.activeDevices, SubscriptionModel.devices(ui.devices), StrAccount.noActiveDevices)
        Lines(StrAccount.payments, SubscriptionModel.payments(ui.payments), StrAccount.noPayments)
        Lines(StrAccount.adminNotices, SubscriptionModel.notices(ui.notices), StrAccount.noNotices)
        Spacer(Modifier.height(24.dp))
      }
    }
  }
}

@Composable
private fun Lines(title: String, lines: List<SubscriptionModel.Line>, empty: String) {
  V3Section(title) {
    if (lines.isEmpty()) Text(empty, style = V3Type.callout, color = PhoneV3.colors.inkMuted, modifier = Modifier.padding(16.dp))
    lines.forEachIndexed { i, l ->
      if (i > 0) V3Divider(start = 16.dp)
      V3ListRow(l.title.ifBlank { "—" }, value = l.value, chevron = false)
    }
  }
}

/** کارتِ وارونهٔ پلن (r16): نام، سرخطِ درشت، نوارِ دوره به رنگِ سرور، آغاز و پایان */
@Composable
private fun HeroCard(h: SubscriptionModel.Hero) {
  val c = PhoneV3.colors
  //  رنگ‌های روشنِ شب تا روی سطحِ وارونه خوانا باشند (مثلِ نقطهٔ پیامِ کوتاه)
  val tone = PhoneV3Roles.status(PhoneV3Dark, when (h.tone) {
    SubscriptionModel.Tone.GREEN -> V3Status.DELIVERED
    SubscriptionModel.Tone.YELLOW -> V3Status.AWAITING
    SubscriptionModel.Tone.RED -> V3Status.CANCELLED
    SubscriptionModel.Tone.GREY -> V3Status.INACTIVE
  }).fg
  val shape = RoundedCornerShape(V3Space.featuredRadius)
  val fill by animateFloatAsState(h.progress, MotionSpec.sheet(), label = "period")
  Column(
    Modifier.fillMaxWidth().clip(shape).background(Brush.linearGradient(listOf(c.black, c.blackRaised))).padding(20.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(StrAccount.plan, style = V3Type.footnote, color = c.onBlackMuted, modifier = Modifier.weight(1f))
      Text(h.plan, style = V3Type.callout, color = c.onBlack, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(10.dp))
    Text(h.headline, style = V3Type.largeTitle, color = tone)
    Spacer(Modifier.height(12.dp))
    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(c.onBlack.copy(alpha = 0.16f))) {
      Box(Modifier.fillMaxWidth(fill.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(tone))
    }
    Spacer(Modifier.height(14.dp))
    Row {
      Column(Modifier.weight(1f)) {
        Text(StrAccount.starts, style = V3Type.caption, color = c.onBlackMuted)
        Text(h.starts, style = V3Type.callout, color = c.onBlack)
      }
      Column(Modifier.weight(1f)) {
        Text(StrAccount.ends, style = V3Type.caption, color = c.onBlackMuted)
        Text(h.ends, style = V3Type.callout, color = c.onBlack)
      }
      Column(Modifier.weight(1f)) {
        Text(StrAccount.status, style = V3Type.caption, color = c.onBlackMuted)
        Text(h.status, style = V3Type.callout, color = c.onBlack, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}

/**
 *  مسیرِ `subscription`: تپشِ کش‌شده (آفلاین هم چیزی دارد)، بعد `heartbeat` و سه
 *  فهرستِ `/me/devices`، `/me/payments`، `/me/notices` — همان فراخوان‌های صفحهٔ پیشین. ورود با کدِ شش‌رقمی ⇐ `auth/code`.
 */
@Composable
internal fun RouteScope.SubscriptionRoute() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val cache = remember { HeartbeatCache(context) }
  //  پس از بسته شدنِ برگهٔ ورود (روی همین صفحه) دوباره سنجیده می‌شود
  val signedIn = remember(nav.current) { Backend.isReady(context) }
  var ui by remember { mutableStateOf(SubscriptionUi(signedIn = signedIn, sub = cache.read()?.get("subscription") as? JsonObject)) }

  suspend fun refresh() {
    if (!Backend.isReady(context)) { ui = ui.copy(signedIn = false); return }
    ui = ui.copy(signedIn = true, busy = true)
    Backend.codeLogin(context).heartbeat()
      .onSuccess { body ->
        cache.write(body)
        ui = ui.copy(sub = body["subscription"] as? JsonObject, note = "")
        //  قفلِ نرم از همین تپش تصمیم می‌گیرد، نه از ساعتِ گوشی
        SoftLock.refresh(context)
      }
      .onFailure { ui = ui.copy(note = it.userMessage) }
    val api = Backend.api(context)
    fun list(body: JsonObject, key: String) = (body[key] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
    runCatching { ui = ui.copy(devices = list(api.get(ApiEndpoints.Me.DEVICES), "devices")) }
    runCatching { ui = ui.copy(payments = list(api.get(ApiEndpoints.Me.PAYMENTS), "payments")) }
    runCatching { ui = ui.copy(notices = list(api.get(ApiEndpoints.Me.NOTICES), "notices")) }
    ui = ui.copy(busy = false)
  }

  LaunchedEffect(signedIn) { refresh() }
  SubscriptionContent(
    ui,
    onBack = if (nav.canBack) ({ back() }) else null,
    onRefresh = { scope.launch { refresh() } },
    onOpen = { nav.open(it) },
  )
}
