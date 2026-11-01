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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.courier.CourierChecks
import ir.vil3ntec.tohid.core.courier.MissionCard
import ir.vil3ntec.tohid.core.courier.RoutePlan
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
 *  حالت و پیش‌سنجش در `CourierChecksState.kt` است (همان که برگه‌های نسخهٔ ۳ می‌کشند)؛ این‌جا فقط کادرهای رابطِ امروز.
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

/* ============================== اسکنِ کیسه‌ها ============================== */

/** DLV-07: اسکنِ همهٔ کیسه‌ها پیش از «بسته را گرفتم» (`BagScanState`) */
@Composable
internal fun BagScanDialog(c: MissionCard, onDismiss: () -> Unit, onDone: (List<String>) -> Unit) {
  val colors = Shop.colors
  val s = rememberBagScan(c)
  val bags = s.bags
  val missing = s.missing
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
            value = s.input,
            onValueChange = s::edit,
            label = { Text(StrCourier.scanBag) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { s.submit() }, onDone = { s.submit() }, onGo = { s.submit() }),
            modifier = Modifier.weight(1f),
          )
          Spacer(Modifier.width(8.dp))
          TextButton(enabled = s.input.isNotBlank(), onClick = { s.submit() }) { Text(StrCourier.add, color = colors.primary) }
        }
        s.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.danger) }
        Spacer(Modifier.height(6.dp))
        Text(StrPick.bags(s.scanned.size, bags), style = MaterialTheme.typography.bodyMedium, color = colors.text, fontWeight = FontWeight.SemiBold)
        if (missing.isNotEmpty()) {
          Text(StrCourier.missing(missing.joinToString("، ")), style = MaterialTheme.typography.bodySmall, color = colors.muted)
        }
      }
    },
    confirmButton = {
      TextButton(enabled = s.ready, onClick = { onDone(s.labels()) }) { Text(StrCourier.pickedUp, color = colors.primary) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(Str.cancel, color = colors.muted) } },
  )
}

/* ============================== نزدیکِ پین ============================== */

/** DLV-17: موقعیتِ همین لحظه پیش از «تحویل شد»؛ نزدیک ⇒ همان دم `onReady` (`NearPinState`) */
@Composable
internal fun NearPinFlow(c: MissionCard, onDismiss: () -> Unit, onReady: (CourierChecks.Point) -> Unit) {
  val colors = Shop.colors
  val s = rememberNearPin(c, onReady)
  val busy = s.busy
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = colors.surfaceSolid,
    title = { Text(StrCourier.pinTitle, color = colors.text) },
    text = {
      Text(
        if (busy) StrCourier.locating else s.message,
        style = MaterialTheme.typography.bodyMedium, color = if (busy) colors.muted else colors.text,
      )
    },
    confirmButton = { TextButton(enabled = !busy, onClick = { s.start() }) { Text(StrCourier.checkAgain, color = colors.primary) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(Str.cancel, color = colors.muted) } },
  )
}

/* ============================== مسیرِ پیشنهادی ============================== */

/** DLV-04: ترتیبِ پیشنهادیِ مأموریت‌های پذیرفته، روی دستگاه و بی اینترنت (`RouteState`) */
@Composable
internal fun RouteSheet(cards: List<MissionCard>, onDismiss: () -> Unit) {
  val colors = Shop.colors
  val rs = rememberRoute()
  val route = rs.plan(cards)
  TohidBottomSheet(title = StrCourier.routeTitle, onDismiss = onDismiss) {
    SheetBody {
      Text(rs.hint, style = MaterialTheme.typography.bodySmall, color = colors.muted)
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

/** DLV-18: امتیازِ پیک به یک تحویل (`RatingState`) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RateDialog(onDismiss: () -> Unit, onDone: (stars: Int, tags: List<String>) -> Unit) {
  val colors = Shop.colors
  val s = rememberRating()
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = colors.surfaceSolid,
    title = { Text(StrCourier.rateTitle, color = colors.text) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          (1..5).forEach { n -> TohidFilterChip(StrCourier.stars(n), selected = s.stars == n, onClick = { s.stars = n }) }
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          s.allowed.forEach { tag -> TohidFilterChip(ratingTagLabel(tag), selected = tag in s.tags, onClick = { s.toggle(tag) }) }
        }
      }
    },
    confirmButton = {
      TextButton(enabled = s.ok, onClick = { onDone(s.stars, s.chosen()) }) { Text(StrCourier.send, color = colors.primary) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(Str.cancel, color = colors.muted) } },
  )
}
