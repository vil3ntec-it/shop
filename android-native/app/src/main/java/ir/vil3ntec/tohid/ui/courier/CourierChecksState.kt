package ir.vil3ntec.tohid.ui.courier

// i18n: سه‌زبانه

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.courier.CourierChecks
import ir.vil3ntec.tohid.core.courier.MissionCard
import ir.vil3ntec.tohid.core.courier.RoutePlan
import ir.vil3ntec.tohid.core.delivery.DeliveryRating
import ir.vil3ntec.tohid.core.i18n.StrCourier
import ir.vil3ntec.tohid.core.i18n.StrPick

/*
 *  حالتِ صفحه‌های پیش‌شرط و کمکیِ پیک (DLV-04/07/17/18) — یکی برای هر دو رابط: کادرهای امروز
 *  (`CourierChecksUi.kt`) و برگه‌های نسخهٔ ۳ (`CourierChecksV3.kt`) فقط همین را می‌کشند، پس رفتارشان یکی است.
 *  قاعده‌ها در core‌اند (`CourierChecks`، `RoutePlan`، `DeliveryRating`) و سرور تصمیمِ آخر را می‌گیرد؛
 *  این‌جا فقط پیش‌سنجش تا پیک پیش از فرستادن بداند چه کم است.
 */

/** موقعیتِ «همین لحظه»: نقطهٔ کهنه‌تر از یک دقیقه (مثلاً هنوز از دکان) پذیرفته نیست */
private const val FIX_MAX_AGE_MS = 60_000L

/** مسیر فقط پیشنهاد است: نقطهٔ تا پنج دقیقه کهنه بس است */
private const val ROUTE_MAX_AGE_MS = 5 * 60_000L

/** فاصله برای صفحه: زیرِ هزار متر «متر»، وگرنه کیلومتر */
internal fun distanceText(m: Long): String = if (m < 1000) StrCourier.meters(m) else StrCourier.km(RoutePlan.km(m))

/* ============================== اسکنِ کیسه‌ها ============================== */

/**
 *  DLV-07: برچسبِ هر کیسه (بارکدخوانِ HID با Enter، یا تایپ). کیسهٔ سفارشِ دیگر یا برچسبِ کهنه همین‌جا رد
 *  می‌شود و کیسه‌های پیشین می‌مانند (`CourierChecks.scanOne`). «بسته را گرفتم» فقط با همهٔ کیسه‌ها؛ صف همان
 *  برچسب‌های سرور (`labelOf`) را می‌برد.
 */
@Stable
internal class BagScanState(private val orderNo: Long, val bags: Int) {
  var scanned by mutableStateOf(emptySet<Int>())
    private set
  var input by mutableStateOf("")
    private set
  var note by mutableStateOf<String?>(null)
    private set

  val missing: List<Int> get() = CourierChecks.missing(scanned, bags)
  val ready: Boolean get() = bags > 0 && missing.isEmpty()

  fun edit(raw: String) { input = raw.take(64) }

  fun submit() {
    val raw = input.trim()
    if (raw.isEmpty()) return
    input = ""
    val (next, code) = CourierChecks.scanOne(scanned, raw, orderNo, bags)
    scanned = next
    note = when (code) {
      "" -> null
      "bag_wrong_order" -> StrCourier.wrongOrder
      "label_mismatch" -> StrCourier.mismatch
      else -> StrPick.notLabel
    }
  }

  /** همان برچسب‌های سرور، نه نوشتهٔ خامِ بارکدخوان */
  fun labels(): List<String> = scanned.sorted().map { CourierChecks.labelOf(orderNo, it, bags) }
}

@Composable
internal fun rememberBagScan(c: MissionCard): BagScanState = remember(c.id) { BagScanState(c.orderNo, c.bagScanBags ?: 0) }

/* ============================== نزدیکِ پین ============================== */

/**
 *  DLV-17: پیش از «تحویل شد» موقعیتِ همین لحظه گرفته و با پینِ کارت سنجیده می‌شود. نزدیک ⇒ همان نقطه برای ثبت؛
 *  دور ⇒ فاصله و «دوباره بسنج». بی اجازه یا بی مکان‌یاب ⇒ پیامِ روشن (سرور هم بی موقعیت رد می‌کرد).
 *  ⛔ موقعیت فقط به همین رویداد می‌رود؛ سرور فقط فاصلهٔ گرد را نگه می‌دارد.
 */
@Stable
internal class NearPinState {
  var busy by mutableStateOf(true)
    private set
  var message by mutableStateOf("")
    private set
  internal var locate: () -> Unit = {}

  fun start() {
    busy = true
    message = ""
    locate()
  }

  /** نتیجهٔ یک موقعیت: نزدیک ⇒ همان نقطه، وگرنه null و پیامِ روشن */
  fun settle(here: CourierChecks.Point?, c: MissionCard): CourierChecks.Point? {
    busy = false
    if (here == null) {
      message = StrCourier.noLocation
      return null
    }
    val maxM = c.nearPinMaxM ?: CourierChecks.DEFAULT_MAX_M
    val la = c.lat
    val ln = c.lng
    val v = CourierChecks.nearPin(here, if (la != null && ln != null) CourierChecks.Point(la, ln) else null, maxM)
    if (!v.ok) message = StrCourier.tooFar(v.distanceM ?: 0L, maxM)
    return here.takeIf { v.ok }
  }
}

/** اجازه و خودِ مکان‌یاب پشتِ مرزِ پلتفرم‌اند (`rememberCourierFix`)؛ باز شدن ⇒ همان دم یک بار سنجش */
@Composable
internal fun rememberNearPin(c: MissionCard, onReady: (CourierChecks.Point) -> Unit): NearPinState {
  val s = remember(c.id) { NearPinState() }
  s.locate = rememberCourierFix(FIX_MAX_AGE_MS, ask = true) { here -> s.settle(here, c)?.let(onReady) }
  LaunchedEffect(Unit) { s.start() }
  return s
}

/* ============================== مسیرِ پیشنهادی ============================== */

/**
 *  DLV-04: ترتیبِ پیشنهادیِ مأموریت‌های پذیرفته، از جای همین لحظهٔ پیک (اگر بود) — همان الگوریتمِ سرور
 *  (`RoutePlan.forCards`)، روی دستگاه و بی اینترنت. فقط نمایش است؛ هیچ وضعیتی عوض نمی‌شود.
 */
@Stable
internal class RouteState {
  var start by mutableStateOf<CourierChecks.Point?>(null)
    private set
  var looked by mutableStateOf(false)
    private set

  fun found(here: CourierChecks.Point?) {
    start = here
    looked = true
  }

  val hint: String
    get() = when {
      !looked -> StrCourier.locating
      start == null -> StrCourier.routeNoStart
      else -> StrCourier.routeHint
    }

  fun plan(cards: List<MissionCard>): RoutePlan.CardRoute = RoutePlan.forCards(start, cards)
}

/** بی اجازه نمی‌پرسد: مسیر بی موقعیت هم ساخته می‌شود (از یکی از ایستگاه‌ها) */
@Composable
internal fun rememberRoute(): RouteState {
  val s = remember { RouteState() }
  val locate = rememberCourierFix(ROUTE_MAX_AGE_MS, ask = false) { here -> s.found(here) }
  LaunchedEffect(Unit) { locate() }
  return s
}

/* ============================== امتیاز ============================== */

/**
 *  DLV-18: امتیازِ پیک به یک تحویل — ستارهٔ ۱ تا ۵ و حداکثر ۴ برچسب از فهرستِ ثابتِ پیک (بی متنِ آزاد).
 *  همان سنجشِ سرور پیش از فرستادن (`DeliveryRating.parse`).
 */
@Stable
internal class RatingState {
  val allowed: List<String> = DeliveryRating.TAGS.getValue("courier")
  var stars by mutableIntStateOf(0)
  var tags by mutableStateOf(emptySet<String>())
    private set

  val ok: Boolean get() = DeliveryRating.parse(stars, tags.toList(), "courier").second.isEmpty()

  fun toggle(tag: String) {
    tags = when {
      tag in tags -> tags - tag
      tags.size < DeliveryRating.MAX_TAGS -> tags + tag
      else -> tags
    }
  }

  /** به ترتیبِ فهرستِ ثابت */
  fun chosen(): List<String> = allowed.filter { it in tags }
}

@Composable
internal fun rememberRating(): RatingState = remember { RatingState() }
