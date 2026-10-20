package ir.vil3ntec.tohid.desktop

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import ir.vil3ntec.tohid.core.courier.CourierEvent
import ir.vil3ntec.tohid.core.courier.MissionCard
import ir.vil3ntec.tohid.core.courier.QueueState
import ir.vil3ntec.tohid.ui.courier.CourierBoard
import ir.vil3ntec.tohid.ui.courier.CourierJoinScreen

/**
 *  پیش‌نمایشِ حالتِ پیک برای عکس‌های `phone-v3-courier-*` — فقط دادهٔ نمایشی.
 *
 *  هیچ نام، نشانی، شماره یا کدِ واقعی این‌جا نیست؛ همه ساختگی‌اند. دکمه‌ها
 *  این‌جا به صف یا سرور نمی‌روند (پیش‌نمایش است)، ولی صفحه همان `CourierBoard`ِ
 *  برنامهٔ گوشی است.
 *
 *  نوع‌ها: join · list · detail · cod · delivered · empty
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
  val empty = kind == "empty"
  CourierBoard(
    name = "کریم احمدی",
    queue = if (empty) QueueState() else DEMO_QUEUE,
    active = if (empty) emptyList() else DEMO_ACTIVE,
    done = DEMO_DONE,
    photosWaiting = 0,
    syncing = false,
    lastError = null,
    snackbar = snackbar,
    onRefresh = {},
    onDismissRejected = {},
    onRecord = {},
    onPhoto = { _, _ -> },
    onSignOut = {},
    initialOpen = when (kind) {
      "detail" -> "demo-1043"
      "cod" -> "demo-1044"
      "delivered" -> "demo-1038"
      else -> null
    },
    initialDeliver = kind == "cod",
  )
}

/** «در راه» هنوز در صفِ گوشی است — سرور «بسته گرفته شد» می‌داند */
private val DEMO_QUEUE = QueueState(
  nextSeq = 2,
  pending = listOf(CourierEvent("cev-demo-0000001", "demo-1043", "moving", 0L, 1L, from = "picked_up")),
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
    requiresCode = true, photoAllowed = true,
  ),
  MissionCard(
    id = "demo-1041", orderNo = 1041, status = "requested",
    landmark = "روبه‌روی نانواییِ نو، کوچهٔ دوم",
    addressText = "ناحیهٔ پنجم، سرکِ اصلی، دروازهٔ آبی",
    customerName = "مریم", phoneMasked = "0799•••412",
    itemsCount = 2, expectedCollectMinor = 85_000, fareMinor = 5_000,
  ),
  MissionCard(
    id = "demo-1044", orderNo = 1044, status = "arrived",
    landmark = "پشتِ مسجدِ جامع، دکانِ کنارِ دروازه",
    addressText = "سرکِ مکتب، کوچهٔ سوم، خانهٔ 7",
    customerName = "زهرا", phoneMasked = "0788•••903",
    itemsCount = 6, expectedCollectMinor = 342_000, fareMinor = 8_000,
    photoAllowed = true,
  ),
  MissionCard(
    id = "demo-1042", orderNo = 1042, status = "assigned",
    landmark = "پشتِ مکتبِ دخترانه، دروازهٔ قهوه‌ای",
    addressText = "ناحیهٔ سوم، کوچهٔ بهار، خانهٔ 22",
    customerName = "احمد", phoneMasked = "0766•••270",
    itemsCount = 1, fareMinor = 6_000, requiresCode = true,
  ),
)

private val DEMO_DONE = listOf(
  MissionCard(id = "demo-1038", orderNo = 1038, status = "delivered", cashCollectedMinor = 98_000, fareMinor = 5_000, itemsCount = 3, expectedCollectMinor = 98_000),
  MissionCard(id = "demo-1036", orderNo = 1036, status = "returned", fareMinor = 0, itemsCount = 2),
  MissionCard(id = "demo-1035", orderNo = 1035, status = "delivered", fareMinor = 6_000, itemsCount = 1),
)
