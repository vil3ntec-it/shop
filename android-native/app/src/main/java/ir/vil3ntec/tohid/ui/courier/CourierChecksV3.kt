package ir.vil3ntec.tohid.ui.courier

// i18n: سه‌زبانه

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.core.courier.CourierChecks
import ir.vil3ntec.tohid.core.courier.MissionCard
import ir.vil3ntec.tohid.core.courier.RoutePlan
import ir.vil3ntec.tohid.core.delivery.DeliveryRating
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrCourier
import ir.vil3ntec.tohid.core.i18n.StrPick
import ir.vil3ntec.tohid.data.CourierStore
import ir.vil3ntec.tohid.data.repo.CourierRepository
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3TextField

/*
 *  پیش‌شرط‌ها و کمکی‌های پیک در نسخهٔ ۳ (DLV-04/07/17/18) — همان رفتارِ رابطِ امروز (`CourierChecksUi.kt`)
 *  با همان حالت (`CourierChecksState.kt`)، روی برگه‌ها و دکمه‌های کیت. هر کدام فقط با پرچمِ خاموشِ دکان:
 *  کارت (`bagScanBags`، `nearPinMaxM`) و `/courier/me` (`routeEnabled`، `ratingsEnabled`).
 *  `CourierBoard` فقط `CheckAsk` را نگه می‌دارد؛ کارِ نهایی همان `CourierAction` است که به صفِ گوشی می‌رود.
 */

/** برگهٔ پیش‌شرط یا کمکیِ باز */
internal sealed interface CheckAsk {
  /** DLV-07: اسکنِ همهٔ کیسه‌ها پیش از «بسته را گرفتم» */
  data class Bags(val card: MissionCard) : CheckAsk

  /** DLV-17: موقعیتِ همین لحظه پس از برگهٔ تحویل و پیش از ثبت؛ `action` همان «تحویل شد» ِ آماده است */
  data class Locate(val action: CourierAction) : CheckAsk

  /** DLV-18: امتیازِ پیک به تحویل */
  data class Rate(val card: MissionCard) : CheckAsk

  /** DLV-04: مسیرِ پیشنهادی */
  data object Route : CheckAsk
}

/** کارِ پیک پیش‌شرط دارد؟ ⇒ برگه‌اش؛ null ⇒ همان دم به صف (همان شاخه‌های `CourierHomeLegacy`) */
internal fun checkFor(a: CourierAction): CheckAsk? = when {
  a.type == "picked_up" && a.card.bagScanBags != null && a.bags.isEmpty() -> CheckAsk.Bags(a.card)
  a.type == "delivered" && a.card.nearPinMaxM != null && a.position == null -> CheckAsk.Locate(a)
  else -> null
}

/** پرچم‌های دکان برای پیک (`/courier/me`؛ آفلاین همان آخرین پاسخ) و تحویل‌هایی که امتیازشان داده شده */
internal data class CourierExtras(val routeEnabled: Boolean = false, val ratingsEnabled: Boolean = false, val rated: Set<String> = emptySet()) {
  /** دکمهٔ امتیاز فقط با پرچم، فقط «تحویل شد» و یک بار */
  fun canRate(c: MissionCard): Boolean = ratingsEnabled && DeliveryRating.courierCanRate(c.status, c.id, rated)

  companion object {
    fun of(store: CourierStore) = CourierExtras(store.routeEnabled, store.ratingsEnabled, store.rated())
  }
}

/**
 *  DLV-18: امتیاز فقط آنلاین؛ ثبت‌شده یا مهلت‌گذشته ⇒ «امتیازداده» و دکمه دیگر دیده نمی‌شود. پیامِ برگشتی برای نوارِ پیام.
 */
internal suspend fun rateMission(api: CourierRepository, store: CourierStore, card: MissionCard, stars: Int, tags: List<String>): String {
  var text = StrCourier.rated
  api.rate(card.id, stars, tags)
    .onSuccess { store.markRated(card.id) }
    .onFailure { f ->
      if (f.code == "already_rated" || f.code == "rating_closed") store.markRated(card.id)
      text = when (f.code) {
        "already_rated" -> StrCourier.rated
        "rating_closed" -> StrCourier.rateClosed
        "offline", "unreachable", "timeout" -> StrCourier.rateOnline
        else -> f.userMessage
      }
    }
  return text
}

/** برگهٔ بازِ `CheckAsk`؛ ثبتِ کارِ آماده با `onRecord`، امتیاز با `onRate` */
@Composable
internal fun CourierCheckSheets(
  ask: CheckAsk?,
  live: List<MissionCard>,
  onDismiss: () -> Unit,
  onRecord: (CourierAction) -> Unit,
  onRate: (MissionCard, Int, List<String>) -> Unit,
) {
  when (ask) {
    is CheckAsk.Bags -> BagScanSheet(ask.card, onDismiss) { labels ->
      onDismiss(); onRecord(CourierAction(ask.card, "picked_up", bags = labels))
    }
    is CheckAsk.Locate -> NearPinSheet(ask.action.card, onDismiss) { here ->
      onDismiss(); onRecord(ask.action.copy(position = here))
    }
    is CheckAsk.Rate -> RateSheet(onDismiss) { stars, tags ->
      onDismiss(); onRate(ask.card, stars, tags)
    }
    CheckAsk.Route -> RoutePlanSheet(live, onDismiss)
    null -> {}
  }
}

/** DLV-07: برچسبِ هر کیسه با Enter ِ بارکدخوان یا «افزودن»؛ «بسته را گرفتم» فقط با همه */
@Composable
private fun BagScanSheet(c: MissionCard, onDismiss: () -> Unit, onDone: (List<String>) -> Unit) {
  val p = PhoneV3.colors
  val s = rememberBagScan(c)
  V3Sheet(onDismiss = onDismiss, title = StrCourier.bagsTitle) { _ ->
    CourierSheetSub(StrCourier.bagsHint(s.bags))
    Spacer(Modifier.height(14.dp))
    V3TextField(
      s.input, s::edit, StrCourier.scanBag, Modifier.fillMaxWidth(),
      imeAction = ImeAction.Send, onImeAction = { s.submit() },
    )
    s.note?.let {
      Spacer(Modifier.height(8.dp))
      V3Message(V3Tone.ERROR, it)
    }
    Spacer(Modifier.height(10.dp))
    V3Button(StrCourier.add, { s.submit() }, Modifier.fillMaxWidth(), kind = V3ButtonKind.NEUTRAL, enabled = s.input.isNotBlank())
    Spacer(Modifier.height(14.dp))
    Text(StrPick.bags(s.scanned.size, s.bags), style = courierText(16.sp, FontWeight.Bold), color = p.ink)
    if (s.missing.isNotEmpty()) {
      Text(StrCourier.missing(s.missing.joinToString("، ")), style = courierText(14.sp), color = p.inkMuted)
    }
    Spacer(Modifier.height(18.dp))
    StepButton(StrCourier.pickedUp, { onDone(s.labels()) }, Modifier.fillMaxWidth(), enabled = s.ready)
    Spacer(Modifier.height(8.dp))
    CourierSecondary(Str.cancel, onDismiss, Modifier.fillMaxWidth())
  }
}

/** DLV-17: موقعیتِ همین لحظه؛ نزدیک ⇒ همان دم ثبت، دور یا بی موقعیت ⇒ پیام و «دوباره بسنج» */
@Composable
private fun NearPinSheet(c: MissionCard, onDismiss: () -> Unit, onReady: (CourierChecks.Point) -> Unit) {
  val s = rememberNearPin(c, onReady)
  V3Sheet(onDismiss = onDismiss, title = StrCourier.pinTitle) { _ ->
    if (s.busy) CourierSheetSub(StrCourier.locating) else V3Message(V3Tone.WARNING, s.message)
    Spacer(Modifier.height(18.dp))
    StepButton(
      StrCourier.checkAgain, { s.start() }, Modifier.fillMaxWidth(),
      icon = Icons.Outlined.MyLocation, enabled = !s.busy, loading = s.busy,
    )
    Spacer(Modifier.height(8.dp))
    CourierSecondary(Str.cancel, onDismiss, Modifier.fillMaxWidth())
  }
}

/** DLV-04: ترتیبِ پیشنهادیِ مأموریت‌های پذیرفته — فقط نمایش؛ هیچ وضعیت و ترتیبِ فهرستی عوض نمی‌شود */
@Composable
private fun RoutePlanSheet(cards: List<MissionCard>, onDismiss: () -> Unit) {
  val p = PhoneV3.colors
  val rs = rememberRoute()
  val route = rs.plan(cards)
  V3Sheet(onDismiss = onDismiss, title = StrCourier.routeTitle) { _ ->
    CourierSheetSub(rs.hint)
    Spacer(Modifier.height(14.dp))
    if (route.stops.isEmpty() && route.unpinned.isEmpty()) {
      V3Message(V3Tone.INFO, StrCourier.routeEmpty)
    } else {
      V3Card(padding = PaddingValues(0.dp)) {
        route.stops.forEachIndexed { i, stop ->
          if (i > 0) V3Divider(start = 16.dp)
          V3ListRow(
            StrCourier.stop(stop.seq, stop.card.landmark.ifBlank { StrCourier.noLandmark }),
            subtitle = if (stop.legM > 0) distanceText(stop.legM) else null, tile = false, chevron = false,
          )
        }
        route.unpinned.forEachIndexed { i, u ->
          if (i > 0 || route.stops.isNotEmpty()) V3Divider(start = 16.dp)
          V3ListRow(u.landmark.ifBlank { StrCourier.noLandmark }, subtitle = StrCourier.noPin, tile = false, chevron = false)
        }
      }
    }
    if (route.totalM > 0) {
      Spacer(Modifier.height(12.dp))
      Text(StrCourier.total(RoutePlan.km(route.totalM)), style = courierText(16.sp, FontWeight.Bold), color = p.ink)
    }
    Spacer(Modifier.height(18.dp))
    CourierSecondary(Str.close, onDismiss, Modifier.fillMaxWidth())
  }
}

/** DLV-18: ستاره و حداکثر ۴ برچسب از فهرستِ ثابتِ پیک — بی متنِ آزاد */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RateSheet(onDismiss: () -> Unit, onDone: (stars: Int, tags: List<String>) -> Unit) {
  val s = rememberRating()
  V3Sheet(onDismiss = onDismiss, title = StrCourier.rateTitle) { _ ->
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      (1..5).forEach { n -> V3Chip(StrCourier.stars(n), selected = s.stars == n, onClick = { s.stars = n }, icon = Icons.Outlined.Star) }
    }
    Spacer(Modifier.height(14.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      s.allowed.forEach { tag -> V3Chip(ratingTagLabel(tag), selected = tag in s.tags, onClick = { s.toggle(tag) }) }
    }
    Spacer(Modifier.height(18.dp))
    StepButton(StrCourier.send, { onDone(s.stars, s.chosen()) }, Modifier.fillMaxWidth(), enabled = s.ok)
    Spacer(Modifier.height(8.dp))
    CourierSecondary(Str.cancel, onDismiss, Modifier.fillMaxWidth())
  }
}
