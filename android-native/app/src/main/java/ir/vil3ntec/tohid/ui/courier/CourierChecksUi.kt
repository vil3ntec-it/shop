package ir.vil3ntec.tohid.ui.courier

// i18n: سه‌زبانه

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.courier.CourierChecks
import ir.vil3ntec.tohid.core.courier.MissionCard
import ir.vil3ntec.tohid.core.courier.RoutePlan
import ir.vil3ntec.tohid.core.delivery.DeliveryRating
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrCourier
import ir.vil3ntec.tohid.core.i18n.StrPick
import ir.vil3ntec.tohid.ui.screens.SheetBody
import ir.vil3ntec.tohid.ui.screens.TohidBottomSheet
import ir.vil3ntec.tohid.ui.screens.TohidCard
import ir.vil3ntec.tohid.ui.screens.TohidFilterChip
import ir.vil3ntec.tohid.ui.theme.Shop

/*
 *  صفحه‌های پیش‌شرط و کمکیِ پیک — همه فقط وقتی دیده می‌شوند که پرچمِ خاموشِ دکان روشن باشد (کارت یا `/courier/me`):
 *    • اسکنِ کیسه‌ها پیش از «بسته را گرفتم» (DLV-07، `MissionCard.bagScanBags`)
 *    • موقعیتِ همین لحظه پیش از «تحویل شد» (DLV-17، `MissionCard.nearPinMaxM`)
 *    • مسیرِ پیشنهادی (DLV-04) — روی خودِ دستگاه با `RoutePlan.forCards`، بی اینترنت
 *    • امتیازِ پیک به تحویل (DLV-18)
 *  قاعده‌ها همه در core‌اند (`CourierChecks`، `RoutePlan`، `DeliveryRating`) و سرور تصمیمِ آخر را می‌گیرد؛
 *  این‌جا فقط پیش‌سنجش تا پیک پیش از فرستادن بداند چه کم است.
 */

/** کلیدِ برچسبِ امتیازِ سرور ⇒ نوشتهٔ سه‌زبانه (کلید همان می‌ماند و به سرور می‌رود) */
internal fun ratingTagLabel(tag: String): String = when (tag) {
  "easy_to_find" -> StrCourier.tagEasyToFind
  "ready" -> StrCourier.tagReady
  "hard_to_find" -> StrCourier.tagHardToFind
  "not_ready" -> StrCourier.tagNotReady
  "wrong_pin" -> StrCourier.tagWrongPin
  "on_time" -> StrCourier.tagOnTime
  "polite" -> StrCourier.tagPolite
  "careful" -> StrCourier.tagCareful
  "late" -> StrCourier.tagLate
  "rude" -> StrCourier.tagRude
  "damaged" -> StrCourier.tagDamaged
  else -> tag
}

/** فاصله برای صفحه: زیرِ هزار متر «متر»، وگرنه کیلومتر */
private fun distanceText(m: Long): String = if (m < 1000) StrCourier.meters(m) else StrCourier.km(RoutePlan.km(m))

/* ============================== اسکنِ کیسه‌ها ============================== */

/**
 *  DLV-07: برچسبِ هر کیسه (بارکدخوانِ HID با Enter، یا تایپ). کیسهٔ سفارشِ دیگر یا برچسبِ کهنه همین‌جا رد
 *  می‌شود و کیسه‌های پیشین می‌مانند (`CourierChecks.scanOne`). «بسته را گرفتم» فقط با همهٔ کیسه‌ها؛ صف همان
 *  برچسب‌های سرور (`labelOf`) را می‌برد.
 */
@Composable
internal fun BagScanDialog(c: MissionCard, onDismiss: () -> Unit, onDone: (List<String>) -> Unit) {
  val colors = Shop.colors
  val bags = c.bagScanBags ?: 0
  var scanned by remember { mutableStateOf(emptySet<Int>()) }
  var input by remember { mutableStateOf("") }
  var note by remember { mutableStateOf<String?>(null) }

  fun submit() {
    val raw = input.trim()
    if (raw.isEmpty()) return
    input = ""
    val (next, code) = CourierChecks.scanOne(scanned, raw, c.orderNo, bags)
    scanned = next
    note = when (code) {
      "" -> null
      "bag_wrong_order" -> StrCourier.wrongOrder
      "label_mismatch" -> StrCourier.mismatch
      else -> StrPick.notLabel
    }
  }

  val missing = CourierChecks.missing(scanned, bags)
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = colors.surfaceSolid,
    title = { Text(StrCourier.bagsTitle, color = colors.text) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(StrCourier.bagsHint(bags), style = MaterialTheme.typography.bodySmall, color = colors.muted)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          OutlinedTextField(
            value = input,
            onValueChange = { input = it.take(64) },
            label = { Text(StrCourier.scanBag) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submit() }, onDone = { submit() }, onGo = { submit() }),
            modifier = Modifier.weight(1f),
          )
          Spacer(Modifier.width(8.dp))
          TextButton(enabled = input.isNotBlank(), onClick = { submit() }) { Text(StrCourier.add, color = colors.primary) }
        }
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.danger) }
        Spacer(Modifier.height(6.dp))
        Text(StrPick.bags(scanned.size, bags), style = MaterialTheme.typography.bodyMedium, color = colors.text, fontWeight = FontWeight.SemiBold)
        if (missing.isNotEmpty()) {
          Text(StrCourier.missing(missing.joinToString("، ")), style = MaterialTheme.typography.bodySmall, color = colors.muted)
        }
      }
    },
    confirmButton = {
      TextButton(enabled = bags > 0 && missing.isEmpty(), onClick = {
        onDone(scanned.sorted().map { CourierChecks.labelOf(c.orderNo, it, bags) })
      }) { Text(StrCourier.pickedUp, color = colors.primary) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(Str.cancel, color = colors.muted) } },
  )
}

/* ============================== نزدیکِ پین ============================== */

/** موقعیتِ «همین لحظه»: نقطهٔ کهنه‌تر از یک دقیقه (مثلاً هنوز از دکان) پذیرفته نیست */
private const val FIX_MAX_AGE_MS = 60_000L

/** مسیر فقط پیشنهاد است: نقطهٔ تا پنج دقیقه کهنه بس است */
private const val ROUTE_MAX_AGE_MS = 5 * 60_000L

/**
 *  DLV-17: پیش از «تحویل شد» موقعیتِ همین لحظه گرفته و با پینِ کارت سنجیده می‌شود. نزدیک ⇒ همان دم `onReady`؛
 *  دور ⇒ فاصله و «دوباره بسنج». بی اجازه یا بی مکان‌یاب ⇒ پیامِ روشن (سرور هم بی موقعیت رد می‌کرد).
 *  اجازه و خودِ مکان‌یاب پشتِ مرزِ پلتفرم‌اند (`rememberCourierFix`).
 *  ⛔ موقعیت فقط به همین رویداد می‌رود؛ سرور فقط فاصلهٔ گرد را نگه می‌دارد.
 */
@Composable
internal fun NearPinFlow(c: MissionCard, onDismiss: () -> Unit, onReady: (CourierChecks.Point) -> Unit) {
  val colors = Shop.colors
  val maxM = c.nearPinMaxM ?: CourierChecks.DEFAULT_MAX_M
  val la = c.lat
  val ln = c.lng
  val pin = if (la != null && ln != null) CourierChecks.Point(la, ln) else null
  var busy by remember { mutableStateOf(true) }
  var message by remember { mutableStateOf("") }

  //  مرزِ پلتفرم: `CourierLocationAndroid.kt` (اجازه و `DeviceLocation`)
  val locate = rememberCourierFix(FIX_MAX_AGE_MS, ask = true) { here ->
    busy = false
    if (here == null) {
      message = StrCourier.noLocation
    } else {
      val v = CourierChecks.nearPin(here, pin, maxM)
      if (v.ok) onReady(here) else message = StrCourier.tooFar(v.distanceM ?: 0L, maxM)
    }
  }
  fun start() {
    busy = true
    message = ""
    locate()
  }
  LaunchedEffect(Unit) { start() }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = colors.surfaceSolid,
    title = { Text(StrCourier.pinTitle, color = colors.text) },
    text = {
      Text(
        if (busy) StrCourier.locating else message,
        style = MaterialTheme.typography.bodyMedium, color = if (busy) colors.muted else colors.text,
      )
    },
    confirmButton = { TextButton(enabled = !busy, onClick = { start() }) { Text(StrCourier.checkAgain, color = colors.primary) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(Str.cancel, color = colors.muted) } },
  )
}

/* ============================== مسیرِ پیشنهادی ============================== */

/**
 *  DLV-04: ترتیبِ پیشنهادیِ مأموریت‌های پذیرفته، از جای همین لحظهٔ پیک (اگر بود) — همان الگوریتمِ سرور
 *  (`RoutePlan.forCards`)، روی دستگاه و بی اینترنت. فقط نمایش است؛ هیچ وضعیتی عوض نمی‌شود.
 */
@Composable
internal fun RouteSheet(cards: List<MissionCard>, onDismiss: () -> Unit) {
  val colors = Shop.colors
  var start by remember { mutableStateOf<CourierChecks.Point?>(null) }
  var looked by remember { mutableStateOf(false) }
  //  بی اجازه نمی‌پرسد: مسیر بی موقعیت هم ساخته می‌شود (از یکی از ایستگاه‌ها)
  val locate = rememberCourierFix(ROUTE_MAX_AGE_MS, ask = false) { here -> start = here; looked = true }
  LaunchedEffect(Unit) { locate() }
  val route = RoutePlan.forCards(start, cards)
  TohidBottomSheet(title = StrCourier.routeTitle, onDismiss = onDismiss) {
    SheetBody {
      Text(
        when {
          !looked -> StrCourier.locating
          start == null -> StrCourier.routeNoStart
          else -> StrCourier.routeHint
        },
        style = MaterialTheme.typography.bodySmall, color = colors.muted,
      )
      if (route.stops.isEmpty() && route.unpinned.isEmpty()) {
        Text(StrCourier.routeEmpty, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
      }
      route.stops.forEach { s ->
        TohidCard {
          Text(StrCourier.stop(s.seq, s.card.landmark.ifBlank { StrCourier.noLandmark }), fontWeight = FontWeight.SemiBold, color = colors.text)
          if (s.legM > 0) Text(distanceText(s.legM), style = MaterialTheme.typography.bodySmall, color = colors.muted)
        }
      }
      route.unpinned.forEach { u ->
        TohidCard {
          Text(u.landmark.ifBlank { StrCourier.noLandmark }, fontWeight = FontWeight.SemiBold, color = colors.text)
          Text(StrCourier.noPin, style = MaterialTheme.typography.bodySmall, color = colors.muted)
        }
      }
      if (route.totalM > 0) {
        Text(StrCourier.total(RoutePlan.km(route.totalM)), style = MaterialTheme.typography.bodyMedium, color = colors.text, fontWeight = FontWeight.SemiBold)
      }
    }
  }
}

/* ============================== امتیاز ============================== */

/**
 *  DLV-18: امتیازِ پیک به یک تحویل — ستارهٔ ۱ تا ۵ و حداکثر ۴ برچسب از فهرستِ ثابتِ پیک (بی متنِ آزاد).
 *  همان سنجشِ سرور پیش از فرستادن (`DeliveryRating.parse`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RateDialog(onDismiss: () -> Unit, onDone: (stars: Int, tags: List<String>) -> Unit) {
  val colors = Shop.colors
  val allowed = DeliveryRating.TAGS.getValue("courier")
  var stars by remember { mutableIntStateOf(0) }
  var tags by remember { mutableStateOf(emptySet<String>()) }
  val ok = DeliveryRating.parse(stars, tags.toList(), "courier").second.isEmpty()
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = colors.surfaceSolid,
    title = { Text(StrCourier.rateTitle, color = colors.text) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          (1..5).forEach { n -> TohidFilterChip(StrCourier.stars(n), selected = stars == n, onClick = { stars = n }) }
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          allowed.forEach { tag ->
            TohidFilterChip(ratingTagLabel(tag), selected = tag in tags, onClick = {
              tags = when {
                tag in tags -> tags - tag
                tags.size < DeliveryRating.MAX_TAGS -> tags + tag
                else -> tags
              }
            })
          }
        }
      }
    },
    confirmButton = {
      TextButton(enabled = ok, onClick = { onDone(stars, allowed.filter { it in tags }) }) { Text(StrCourier.send, color = colors.primary) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(Str.cancel, color = colors.muted) } },
  )
}
