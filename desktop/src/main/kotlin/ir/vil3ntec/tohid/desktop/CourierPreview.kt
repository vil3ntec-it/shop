package ir.vil3ntec.tohid.desktop

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import ir.vil3ntec.tohid.core.courier.CourierEvent
import ir.vil3ntec.tohid.core.courier.CourierSettlement
import ir.vil3ntec.tohid.core.courier.CourierSummary
import ir.vil3ntec.tohid.core.courier.MissionCard
import ir.vil3ntec.tohid.core.courier.MissionStep
import ir.vil3ntec.tohid.core.courier.PeriodStats
import ir.vil3ntec.tohid.core.courier.QueueState
import ir.vil3ntec.tohid.core.courier.UnsettledDelivery
import ir.vil3ntec.tohid.ui.courier.CourierBoard
import ir.vil3ntec.tohid.ui.courier.CourierJoinScreen

/**
 *  پیش‌نمایشِ حالتِ پیک برای عکس‌های `phone-v3-courier-*` — فقط دادهٔ نمایشی.
 *
 *  هیچ نام، نشانی، شماره یا کدِ واقعی این‌جا نیست؛ همه ساختگی‌اند. دکمه‌ها
 *  این‌جا به صف یا سرور نمی‌روند (پیش‌نمایش است)، ولی صفحه همان `CourierBoard`ِ
 *  برنامهٔ گوشی است.
 *
 *  نوع‌ها: join · home · route · cash · cash0 · track · ship · stats · account · cod
 *  (`cash0`: سقفِ صفر = «نقد نگیرد»، ۵۷ تحویلِ تسویه‌نشده و «تازه نشد»)
 */
@Composable
internal fun CourierPreview(kind: String) {
  val snackbar = remember { SnackbarHostState() }
  if (kind == "join") {
    CourierJoinScreen(
      name = "کریم", onName = {},
      code = "SHG-7K4M-Q2PX-9TDA", onCode = {},
      busy = false, error = null,
      onJoin = {}, onBack = {},
    )
    return
  }
  CourierBoard(
    name = "کریم احمدی",
    queue = DEMO_QUEUE,
    active = DEMO_ACTIVE,
    done = DEMO_DONE,
    photosWaiting = 0,
    syncing = false,
    lastError = null,
    snackbar = snackbar,
    summary = if (kind == "cash0") DEMO_SUMMARY.copy(cashCapMinor = 0, headroomMinor = 0, unsettledCount = 57) else DEMO_SUMMARY,
    summaryError = if (kind == "cash0") "اینترنت نیست" else null,
    lastSyncAt = at(9, 52),
    onRefresh = {},
    onDismissRejected = {},
    onRecord = {},
    onPhoto = { _, _ -> },
    onCallShop = {},
    onNavigate = {},
    onSignOut = {},
    initialTab = when (kind) {
      "route" -> "route"
      "stats" -> "stats"
      "account" -> "account"
      else -> "home"
    },
    initialPage = if (kind == "cash" || kind == "cash0") "cash" else null,
    initialOpen = when (kind) {
      "track", "ship" -> "demo-1043"
      "cod" -> "demo-1044"
      else -> null
    },
    initialDetailTab = if (kind == "ship") "ship" else "track",
    initialDeliver = kind == "cod",
  )
}

/** ۱۴۰۵/۰۷/۲۸، ساعتِ گوشی (UTC در CI) */
private const val DAY0 = 1_792_454_400_000L
private fun at(h: Int, m: Int, dayAgo: Int = 0): Long = DAY0 - dayAgo * 86_400_000L + (h * 60 + m) * 60_000L

private fun steps(vararg s: Pair<String, Long>): List<MissionStep> = s.mapIndexed { i, (to, t) ->
  when (to) {
    "photo" -> MissionStep("photo", "arrived", t, by = "courier")
    else -> MissionStep(if (i == 0) "created" else "status", to, t, by = if (i == 0) "shop" else "courier")
  }
}

/** «در راه» هنوز در صفِ گوشی است — سرور «بسته گرفته شد» می‌داند */
private val DEMO_QUEUE = QueueState(
  nextSeq = 2,
  pending = listOf(CourierEvent("cev-demo-0000001", "demo-1043", "moving", at(9, 31), 1L, from = "picked_up")),
)

private val DEMO_ACTIVE = listOf(
  MissionCard(
    id = "demo-1043", orderNo = 1043, status = "picked_up",
    landmark = "کنارِ داروخانهٔ شفا، دروازهٔ سبز",
    addressText = "سرکِ دوم، کوچهٔ گل‌ها، خانهٔ 14، طبقهٔ دوم",
    lat = 34.5, lng = 69.2,
    customerName = "حمیدالله نوری", phoneMasked = "0700•••581",
    callBeforeArrival = true, leaveWith = "نگهبانِ ساختمان",
    itemsCount = 4, expectedCollectMinor = 125_000, fareMinor = 7_000,
    requiresCode = true, photoAllowed = true, updatedAt = at(9, 20),
    timeline = steps("requested" to at(8, 55), "assigned" to at(9, 2), "picked_up" to at(9, 20)),
  ),
  MissionCard(
    id = "demo-1041", orderNo = 1041, status = "requested",
    landmark = "روبه‌روی نانواییِ نو، کوچهٔ دوم",
    addressText = "ناحیهٔ پنجم، سرکِ اصلی، دروازهٔ آبی",
    customerName = "مریم", phoneMasked = "0799•••412",
    itemsCount = 2, expectedCollectMinor = 85_000, fareMinor = 5_000, updatedAt = at(9, 50),
    timeline = steps("requested" to at(9, 50)),
  ),
  MissionCard(
    id = "demo-1044", orderNo = 1044, status = "arrived",
    landmark = "پشتِ مسجدِ جامع، دکانِ کنارِ دروازه",
    addressText = "سرکِ مکتب، کوچهٔ سوم، خانهٔ 7",
    customerName = "زهرا", phoneMasked = "0788•••903",
    itemsCount = 6, expectedCollectMinor = 342_000, fareMinor = 8_000,
    photoAllowed = true, updatedAt = at(9, 48),
    timeline = steps(
      "requested" to at(8, 40), "assigned" to at(8, 41), "picked_up" to at(9, 0),
      "moving" to at(9, 5), "arrived" to at(9, 48),
    ),
  ),
  MissionCard(
    id = "demo-1042", orderNo = 1042, status = "assigned",
    landmark = "پشتِ مکتبِ دخترانه، دروازهٔ قهوه‌ای",
    addressText = "ناحیهٔ سوم، کوچهٔ بهار، خانهٔ 22",
    customerName = "احمد", phoneMasked = "0766•••270",
    itemsCount = 1, fareMinor = 6_000, requiresCode = true, updatedAt = at(9, 10),
    timeline = steps("requested" to at(9, 6), "assigned" to at(9, 10)),
  ),
)

private val DEMO_DONE = listOf(
  MissionCard(
    id = "demo-1038", orderNo = 1038, status = "delivered", cashCollectedMinor = 98_000, fareMinor = 5_000,
    itemsCount = 3, expectedCollectMinor = 98_000, photoAllowed = true, updatedAt = at(8, 17),
    timeline = steps(
      "requested" to at(7, 30), "assigned" to at(7, 31), "picked_up" to at(7, 50), "moving" to at(7, 52),
      "arrived" to at(8, 15), "photo" to at(8, 16), "delivered" to at(8, 17),
    ),
  ),
  MissionCard(id = "demo-1036", orderNo = 1036, status = "returned", itemsCount = 2, updatedAt = at(8, 0)),
  MissionCard(id = "demo-1035", orderNo = 1035, status = "delivered", fareMinor = 6_000, itemsCount = 1, updatedAt = at(7, 10)),
)

private val DEMO_SUMMARY = CourierSummary(
  shopName = "سوپرمارکتِ نمونه",
  shopPhone = "0700000000",
  cashInHandMinor = 98_000,
  liveExpectedMinor = 552_000,
  cashCapMinor = 1_000_000,
  headroomMinor = 350_000,
  unsettled = listOf(
    UnsettledDelivery(1038, at(8, 17), 98_000, 98_000, 5_000),
    UnsettledDelivery(1035, at(7, 10), 0, 0, 6_000),
  ),
  unsettledCount = 2,
  settlements = listOf(
    CourierSettlement(12, at(20, 5, 1), "confirmed", 6, 1_845_000, 41_000, 1_845_000, 1_845_000, 0, 0),
    CourierSettlement(11, at(19, 40, 2), "confirmed", 4, 920_000, 28_000, 920_000, 900_000, 20_000, 0, "کسریِ شمارش"),
  ),
  today = PeriodStats(at(0, 0), delivered = 2, failed = 1, collectedCashMinor = 98_000, fareMinor = 11_000),
  week = PeriodStats(at(0, 0, 3), delivered = 14, failed = 2, collectedCashMinor = 3_120_000, fareMinor = 76_000),
  serverTime = at(9, 52),
)
