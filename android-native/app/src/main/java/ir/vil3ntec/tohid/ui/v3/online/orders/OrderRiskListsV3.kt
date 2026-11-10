package ir.vil3ntec.tohid.ui.v3.online.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WrongLocation
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrRisk
import ir.vil3ntec.tohid.core.orders.OrderRisk
import ir.vil3ntec.tohid.core.shopper.GeoPin
import ir.vil3ntec.tohid.core.shopper.PinText
import ir.vil3ntec.tohid.data.repo.OrdersRepository
import ir.vil3ntec.tohid.ui.OrdersLive
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3Skeleton
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import kotlinx.coroutines.launch

/**
 *  RSK-04 (§۶.۲۱) — «فهرستِ بسته و مطمئن» ِ دکان: صفحهٔ جدا (`orders/risk-lists`، از «…» ِ صفِ سفارش‌ها) و همان فهرست در
 *  برگهٔ کارتِ سابقهٔ هر سفارش. افزودنِ شماره (تایپ‌شده یا شمارهٔ همین سفارش)، **جا** (پینِ همین سفارش یا مختصات/پیوندِ
 *  نقشهٔ چسبانده، با شعاع) و برداشتنِ نرم. مرزها و بدنه‌ها `core/orders/OrderRisk` (commonMain)؛ این‌جا فقط کشیدن.
 *
 *  ⛔ فقط صاحب و مدیر (`orders.contact`) و فقط با `risk` ِ `/config` (پرچمِ `shop.order_risk`)؛ سرور هم همین را می‌سنجد
 *  (۴۰۳ `order_risk_disabled`). دلیل فقط برای خودِ دکان است؛ مشتری هرگز نمی‌بیند.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RouteScope.RiskListsRoute() {
  val context = LocalContext.current
  val repo = remember(context) { OrdersHub.repo(context) }
  val cfg = OrdersLive.config.value
  val allowed = cfg != null && cfg.risk && cfg.permissions.contact
  //  (فهرست، پین؟) ِ برگهٔ باز؛ null ⇒ هیچ
  var adding by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
  var reload by remember { mutableStateOf(0) }
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
    if (!allowed) {
      V3Message(V3Tone.INFO, StrRisk.listsOnlyManagers)
    } else {
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        V3Button(StrRisk.blockANumber, { adding = OrderRisk.BLOCK to false }, kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.Block)
        V3Button(StrRisk.trustANumber, { adding = OrderRisk.ALLOW to false }, kind = V3ButtonKind.NEUTRAL, icon = Icons.Outlined.VerifiedUser)
        V3Button(StrRisk.blockAPlace, { adding = OrderRisk.BLOCK to true }, kind = V3ButtonKind.NEUTRAL, icon = Icons.Outlined.WrongLocation)
      }
      Spacer(Modifier.height(14.dp))
      RiskEntriesList(repo, reload)
    }
  }
  adding?.let { (list, pin) ->
    RiskEntrySheet(repo, list, pinKind = pin, fixedPhone = null, fixedPin = null, onClose = { adding = null }, onDone = { adding = null; reload++ })
  }
}

/**
 *  برگهٔ افزودن: [pinKind] ⇒ «جای بسته» با شعاع، وگرنه شماره در فهرستِ [list]. [fixedPhone]/[fixedPin] از همان سفارش ⇒
 *  میدانِ شماره/جا نیست؛ null ⇒ تایپ یا چسباندن. دلیل (۳ تا ۲۰۰)، انقضا (۱ تا ۳۶۵ روز، پیش‌فرض ۳۰) و شعاع (۱۰ تا ۲۰۰۰، پیش‌فرض ۱۰۰).
 */
@Composable
internal fun RiskEntrySheet(
  repo: OrdersRepository,
  list: String,
  pinKind: Boolean,
  fixedPhone: String?,
  fixedPin: GeoPin?,
  onClose: () -> Unit,
  onDone: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  var phoneText by remember { mutableStateOf("") }
  var placeText by remember { mutableStateOf("") }
  var radius by remember { mutableStateOf(OrderRisk.RADIUS_DEFAULT.toString()) }
  var reason by remember { mutableStateOf("") }
  var days by remember { mutableStateOf(OrderRisk.DAYS_DEFAULT.toString()) }
  var tried by remember { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  var failure by remember { mutableStateOf<String?>(null) }
  val n = ir.vil3ntec.tohid.latinDigits(days).trim().toIntOrNull()
  val r = ir.vil3ntec.tohid.latinDigits(radius).trim().toIntOrNull()
  val phone = fixedPhone ?: OrderRisk.typedPhone(phoneText)
  val pin = fixedPin ?: PinText.parse(placeText)
  val err = if (pinKind) OrderRisk.pinEntryError(pin, r, reason, n) else OrderRisk.entryError(list, phone, reason, n)
  val title = when {
    pinKind -> if (fixedPin != null) StrRisk.blockPlace else StrRisk.blockAPlace
    list == OrderRisk.BLOCK -> if (fixedPhone != null) StrRisk.blockNumber else StrRisk.blockANumber
    else -> if (fixedPhone != null) StrRisk.trustNumber else StrRisk.trustANumber
  }
  val help = when {
    pinKind -> StrRisk.blockPlaceHelp
    list == OrderRisk.BLOCK -> StrRisk.blockHelp
    else -> StrRisk.trustHelp
  }
  V3Sheet(onDismiss = onClose, title = title) { _ ->
    Text(help, style = V3Type.subhead, color = PhoneV3.colors.inkMuted)
    Spacer(Modifier.height(10.dp))
    if (!pinKind && fixedPhone == null) {
      V3TextField(phoneText, { phoneText = it.take(24); failure = null }, StrRisk.entryPhone, kind = V3FieldKind.PHONE, required = true,
        error = StrRisk.entryPhoneError.takeIf { tried && err == "phone_required" })
      Spacer(Modifier.height(10.dp))
    }
    if (pinKind && fixedPin == null) {
      V3TextField(placeText, { placeText = it.take(2000); failure = null }, StrRisk.entryPlace, helper = StrRisk.entryPlaceHelp, required = true,
        error = StrRisk.entryPlaceError.takeIf { tried && err == "pin_required" })
      Spacer(Modifier.height(10.dp))
    }
    if (pinKind) {
      V3TextField(radius, { radius = it.take(4); failure = null }, StrRisk.entryRadius, kind = V3FieldKind.NUMBER, required = true,
        error = StrRisk.entryRadiusError.takeIf { tried && err == "bad_radius" })
      Spacer(Modifier.height(10.dp))
    }
    V3TextField(reason, { reason = it.take(OrderRisk.REASON_MAX); failure = null }, StrRisk.entryReason, required = true,
      error = StrRisk.entryReasonError.takeIf { tried && err == "reason_required" })
    Spacer(Modifier.height(10.dp))
    V3TextField(days, { days = it.take(3); failure = null }, StrRisk.entryDays, kind = V3FieldKind.NUMBER, required = true,
      error = StrRisk.entryDaysError.takeIf { tried && err == "bad_expiry" })
    failure?.let { V3Message(V3Tone.ERROR, it, Modifier.padding(top = 8.dp)) }
    Spacer(Modifier.height(14.dp))
    V3Button(StrKit.save, {
      tried = true
      val body = if (err != null || n == null) null
      else if (pinKind) pin?.let { p -> r?.let { m -> OrderRisk.pinEntryBody(p, m, reason, n) } }
      else OrderRisk.phoneEntryBody(list, phone, reason, n)
      if (body != null && !busy) {
        busy = true
        scope.launch {
          val res = repo.addRiskEntry(body)
          busy = false
          if (res.valueOrNull() != null) onDone() else failure = res.errorMessage()
        }
      }
    }, Modifier.fillMaxWidth(), loading = busy, haptic = true)
  }
}

/** همان فهرست در برگهٔ کارتِ سابقهٔ سفارش */
@Composable
internal fun RiskListSheet(repo: OrdersRepository, onClose: () -> Unit) {
  V3Sheet(onDismiss = onClose, title = StrRisk.listsTitle) { _ -> RiskEntriesList(repo, 0) }
}

/** ردیف‌های فعالِ فهرست با «برداشتن» (نرم؛ سابقه می‌ماند)؛ جای بسته ⇒ زدن روی ردیف آن را روی نقشه باز می‌کند */
@Composable
internal fun RiskEntriesList(repo: OrdersRepository, reloadKey: Int) {
  val scope = rememberCoroutineScope()
  val uri = LocalUriHandler.current
  var items by remember { mutableStateOf<List<OrderRisk.Entry>?>(null) }
  var failure by remember { mutableStateOf<String?>(null) }
  var reload by remember { mutableStateOf(0) }
  var busy by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(reload, reloadKey) {
    val r = repo.riskLists()
    r.valueOrNull()?.let { items = it; failure = null } ?: run { failure = r.errorMessage() }
  }
  Column(Modifier.fillMaxWidth()) {
    val list = items
    when {
      list == null && failure != null -> V3Message(V3Tone.ERROR, failure.orEmpty(), action = StrKit.retry, onAction = { reload++ })
      list == null -> V3Skeleton(rows = 3)
      list.isEmpty() -> Text(StrRisk.listsEmpty, style = V3Type.callout, color = PhoneV3.colors.inkMuted)
      else -> list.forEach { e ->
        val p = e.phone
        val lat = e.lat
        val lng = e.lng
        val geo = if (p == null && lat != null && lng != null) PinText.geoUri(GeoPin(lat, lng)) else null
        val open: (() -> Unit)? = geo?.let { g -> { runCatching { uri.openUri(g) }; Unit } }
        V3ListRow(
          when {
            p != null && e.list == OrderRisk.BLOCK -> StrRisk.entryBlocked(p)
            p != null -> StrRisk.entryTrusted(p)
            else -> StrRisk.entryPin(e.radiusM ?: 0L)
          },
          subtitle = StrRisk.entrySubtitle(e.reason, ir.vil3ntec.tohid.formatMillis(e.expiresAt)),
          icon = when {
            p == null -> Icons.Outlined.WrongLocation
            e.list == OrderRisk.BLOCK -> Icons.Outlined.Block
            else -> Icons.Outlined.VerifiedUser
          },
          chevron = false, subtitleLines = 3,
          onClick = open,
          trailing = {
            V3Button(StrRisk.remove, {
              if (busy == null) {
                busy = e.id
                scope.launch {
                  val r = repo.removeRiskEntry(e.id)
                  busy = null
                  if (r.valueOrNull() != null) reload++ else failure = r.errorMessage()
                }
              }
            }, kind = V3ButtonKind.NEUTRAL, loading = busy == e.id)
          },
        )
      }
    }
    if (items != null) failure?.let { V3Message(V3Tone.ERROR, it, Modifier.padding(top = 8.dp)) }
  }
}
