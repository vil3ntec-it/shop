package ir.vil3ntec.tohid.ui.courier

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TableRows
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.core.courier.CourierFlow
import ir.vil3ntec.tohid.core.courier.CourierInputError
import ir.vil3ntec.tohid.core.courier.CourierQueue
import ir.vil3ntec.tohid.core.courier.CourierSummary
import ir.vil3ntec.tohid.core.courier.MissionCard
import ir.vil3ntec.tohid.core.courier.MissionView
import ir.vil3ntec.tohid.core.courier.QueueState
import ir.vil3ntec.tohid.data.CourierStore
import ir.vil3ntec.tohid.data.CourierSync
import ir.vil3ntec.tohid.data.PhotoStore
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.fa
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.components.common.ScreenKind
import ir.vil3ntec.tohid.ui.components.common.ViewMode
import ir.vil3ntec.tohid.ui.components.common.ViewModePref
import ir.vil3ntec.tohid.ui.components.common.rememberViewMode
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.PhotoSourceSheet
import ir.vil3ntec.tohid.ui.components.common.openOnMap
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3IconButton
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3SnackbarHost
import ir.vil3ntec.tohid.ui.v3.V3StaggerState
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3ToggleRow
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Stagger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/*
 *  حالتِ پیک — DLV-01/02/08/09/10/11/12، v3 §۳.۱۱.
 *
 *  وقتی نقشِ نشست «پیک» است، برنامه به‌جای دکان همین را باز می‌کند. چهار برگه
 *  (نوارِ قرصیِ پایین مثلِ مرجعِ صاحب محصول): «خانه» (مأموریتِ جاری، میان‌برها،
 *  کارهای اخیر)، «مسیر» (همهٔ مأموریت‌ها)، «آمار» و «حساب»؛ و دو زیرصفحه:
 *  «نقدِ من» و جزئیاتِ هر مأموریت («جزئیاتِ پیگیری» / «جزئیاتِ ارسال»).
 *  هر کار اول در صفِ گوشی می‌نشیند (eventId + زمان و ترتیبِ دستگاه) و هر وقت
 *  اینترنت بود می‌رود.
 *
 *  ⛔ بی زمان‌سنج برای پیک (DLV-20). شماره همیشه پوشیده؛ نشانی پس از پایانِ
 *  مأموریت دیده نمی‌شود (حتی وقتی پایان هنوز در صف است). بی نقشهٔ ساختگی:
 *  «مسیریابی» نقطه را به برنامهٔ نقشهٔ گوشی می‌دهد.
 *
 *  `CourierHome` حالت و صف را نگه می‌دارد؛ `CourierBoard` فقط می‌کشد (پیش‌نمایشِ
 *  گوشی همان را با دادهٔ نمایشی صدا می‌زند).
 */

internal val STATUS_LABEL = mapOf(
  "requested" to "تازه", "assigned" to "قبول شد", "picked_up" to "بسته گرفته شد",
  "moving" to "در راه", "arrived" to "رسیده", "failed" to "ناموفق",
  "delivered" to "تحویل شد", "rejected" to "رد شد", "withdrawn" to "پس گرفته شد", "returned" to "برگشت به دکان",
)

/** جملهٔ بزرگِ هر پله */
internal fun headline(s: String): String = when (s) {
  "requested" -> "کارِ تازه — قبول یا رد"
  "assigned" -> "بسته را از دکان بگیرید"
  "picked_up" -> "بسته دستِ شماست"
  "moving" -> "در راهِ مشتری"
  "arrived" -> "پیشِ مشتری هستید"
  "failed" -> "تحویل نشد"
  "delivered" -> "تحویل شد"
  "rejected" -> "این کار را رد کردید"
  "withdrawn" -> "دکان این کار را پس گرفت"
  "returned" -> "بسته به دکان برگشت"
  else -> STATUS_LABEL[s] ?: s
}

internal fun actionLabel(from: String, to: String): String = when (to) {
  "assigned" -> "قبول"
  "rejected" -> "رد"
  "picked_up" -> "بسته را گرفتم"
  "moving" -> if (from == "failed") "تلاشِ دوباره" else "راه افتادم"
  "arrived" -> "رسیدم"
  "delivered" -> "تحویل شد"
  "failed" -> "ناموفق"
  else -> to
}

internal fun isNegative(to: String) = to == "rejected" || to == "failed"

internal val REASON_LABEL = mapOf(
  "busy" to "سرم شلوغ است", "too_far" to "خیلی دور است", "cash_limit" to "سقفِ نقدم پر است",
  "no_answer" to "تماس بی‌پاسخ / کسی نبود", "wrong_address" to "نشانی پیدا نشد", "refused" to "مشتری نپذیرفت",
  "recalled" to "دکان پس خواست", "partial_payment" to "مشتری کمتر داد", "refused_amount" to "مبلغ را نپذیرفت",
  "damaged_or_short" to "کالا آسیب یا کسری داشت", "other" to "دلیلِ دیگر (بنویسید)",
)

/** یادداشتِ «دکان پس خواست» روی کارت و جزئیات */
internal const val RECALL_NOTE = "دکان این سفارش را پس خواسته است — بسته را برگردانید."

/** پله‌های نوارِ پیشرفت: گرفتم ⇐ در راه ⇐ رسیدم ⇐ تحویل */
internal val STEPS = listOf("گرفتم", "در راه", "رسیدم", "تحویل")

/** چند پله رسیده — منفی یعنی نوار نشان داده نمی‌شود */
internal fun reached(status: String): Int = when (status) {
  "assigned" -> 0
  "picked_up" -> 1
  "moving" -> 2
  "arrived" -> 3
  "delivered" -> 4
  else -> -1
}

internal fun afn(minor: Long): String = "${money(minor / 100.0)} افغانی"

/** ساعت:دقیقهٔ گوشی */
internal fun clock(at: Long): String {
  if (at <= 0) return "—"
  val c = java.util.Calendar.getInstance().apply { timeInMillis = at }
  return String.format(java.util.Locale.US, "%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
}

/** ورودیِ پول به افغانی (رقمِ فارسی هم) ⇒ واحدِ کوچک */
private fun minorOf(raw: String): Long? {
  val t = buildString { for (c in raw) when (c) { in '0'..'9', '.' -> append(c); in '۰'..'۹' -> append('0' + (c - '۰')); '٫' -> append('.') } }
  val v = t.toDoubleOrNull() ?: return null
  if (v < 0 || v > 1_000_000_000) return null
  return Math.round(v * 100)
}

/** همان پالایشِ `NumberField`: رقمِ فارسی/عربی ⇒ لاتین، فقط رقم و نقطه */
private fun numberInput(raw: String): String = buildString {
  for (c in raw) when (c) {
    in '0'..'9', '.' -> append(c)
    in '۰'..'۹' -> append('0' + (c - '۰'))
    in '٠'..'٩' -> append('0' + (c - '٠'))
    '٫' -> append('.')
  }
}

private sealed interface Ask {
  data class Reason(val card: MissionCard, val type: String) : Ask
  data class Deliver(val card: MissionCard) : Ask
  data class Photo(val card: MissionCard) : Ask
}

/** یک کارِ پیک برای صف — همان پارامترهای `CourierQueue.enqueue` */
internal data class CourierAction(
  val card: MissionCard,
  val type: String,
  val reason: String = "",
  val note: String = "",
  val cash: Long? = null,
  val cashReason: String = "",
  val code: String = "",
)

@Composable
fun CourierHome(onSignedOut: () -> Unit) {
  //  پرچمِ نسخهٔ ۳ خاموش (پیش‌فرض) ⇒ همان رابطِ امروز (`ui/legacy`، `UiV3`)
  if (!ir.vil3ntec.tohid.ui.UiV3.on) { CourierHomeLegacy(onSignedOut); return }
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val snackbar = remember { SnackbarHostState() }
  val store = remember(context) { CourierStore(context) }

  var queue by remember { mutableStateOf(store.queue()) }
  var active by remember { mutableStateOf(store.cards("active")) }
  var done by remember { mutableStateOf(store.cards("done")) }
  var name by remember { mutableStateOf(store.courierName) }
  var summary by remember { mutableStateOf(store.summary()) }
  var lastSyncAt by remember { mutableStateOf(store.lastSyncAt) }
  var photosWaiting by remember { mutableStateOf(store.photos().size) }
  var syncing by remember { mutableStateOf(false) }
  var lastError by remember { mutableStateOf<String?>(null) }

  fun reloadLocal() {
    queue = store.queue(); active = store.cards("active"); done = store.cards("done")
    name = store.courierName; photosWaiting = store.photos().size
    summary = store.summary(); lastSyncAt = store.lastSyncAt
  }

  suspend fun sync() {
    if (syncing) return
    syncing = true
    val out = runCatching { CourierSync.flush(context, store) }.getOrNull()
    syncing = false
    reloadLocal()
    lastError = out?.failure?.let { f ->
      when (f.code) {
        "offline", "unreachable", "timeout" -> "اینترنت نیست — کارها در صفِ گوشی می‌مانند"
        else -> f.userMessage
      }
    }
    out?.photoErrors?.firstOrNull()?.let { snackbar.showSnackbar(it) }
  }

  /** هر کار: اول صفِ گوشی، بعد اگر شد سرور */
  fun record(card: MissionCard, type: String, reason: String = "", note: String = "", cash: Long? = null, cashReason: String = "", code: String = "") {
    try {
      val (next, _) = CourierQueue.enqueue(
        store.queue(), "cev-" + ir.vil3ntec.tohid.sync.v1.Ulid.next(), card.id, type, System.currentTimeMillis(),
        //  CAS (§۲۸.۵): همان وضعیتی که پیک روی کارت دید (سرور + صف)
        from = card.status, reason = reason, note = note,
        cashCollectedMinor = cash, cashReason = cashReason, code = code,
      )
      store.saveQueue(next)
      queue = next
      scope.launch { sync() }
    } catch (e: CourierInputError) {
      scope.launch { snackbar.showSnackbar(e.message ?: "ثبت نشد") }
    }
  }

  //  باز شدن و هر نیم دقیقه تا صفحه باز است (بی سرویسِ پس‌زمینه — §۳.۱۱)
  LaunchedEffect(Unit) {
    while (true) {
      sync()
      delay(30_000)
    }
  }

  CourierBoard(
    name = name,
    queue = queue,
    active = active,
    done = done,
    photosWaiting = photosWaiting,
    syncing = syncing,
    lastError = lastError,
    snackbar = snackbar,
    summary = summary,
    lastSyncAt = lastSyncAt,
    onRefresh = { scope.launch { sync() } },
    onDismissRejected = { eventId ->
      val next = CourierQueue.dismissRejected(store.queue(), eventId)
      store.saveQueue(next); queue = next
    },
    onRecord = { a -> record(a.card, a.type, reason = a.reason, note = a.note, cash = a.cash, cashReason = a.cashReason, code = a.code) },
    onPhoto = { card, bytes ->
      val eventId = "cph-" + ir.vil3ntec.tohid.sync.v1.Ulid.next()
      store.addPhoto(CourierStore.PendingPhoto(eventId, card.id, System.currentTimeMillis()), bytes)
      photosWaiting = store.photos().size
      scope.launch { snackbar.showSnackbar("عکسِ بسته در صف است"); sync() }
    },
    onCallShop = { phone ->
      //  فقط صفحهٔ شماره‌گیر با شمارهٔ دکان باز می‌شود؛ تماس را خودِ پیک می‌زند
      val opened = runCatching {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
      }.isSuccess
      if (!opened) scope.launch { snackbar.showSnackbar("شماره‌گیرِ گوشی باز نشد") }
    },
    onNavigate = { c ->
      val lat = c.lat; val lng = c.lng
      if (lat != null && lng != null) openOnMap(context, lat, lng, c.landmark.ifBlank { "مقصد" })
    },
    onSignOut = {
      scope.launch {
        runCatching { Backend.auth(context).signOut() }
        store.wipe()
        ir.vil3ntec.tohid.data.ShopRole.forget(context)
        onSignedOut()
      }
    },
  )
}

/**
 *  صفحهٔ پیک، بی حالتِ ماندگار: چهار برگه + «نقدِ من» + جزئیات + برگه‌های پایینی.
 *  هر کار با `onRecord` به صفِ گوشی می‌رود. `initial*` فقط برای پیش‌نمایشِ گوشی است.
 */
@Composable
internal fun CourierBoard(
  name: String,
  queue: QueueState,
  active: List<MissionCard>,
  done: List<MissionCard>,
  photosWaiting: Int,
  syncing: Boolean,
  lastError: String?,
  snackbar: SnackbarHostState,
  summary: CourierSummary?,
  lastSyncAt: Long,
  onRefresh: () -> Unit,
  onDismissRejected: (String) -> Unit,
  onRecord: (CourierAction) -> Unit,
  onPhoto: (MissionCard, ByteArray) -> Unit,
  onCallShop: (String) -> Unit,
  onNavigate: (MissionCard) -> Unit,
  onSignOut: () -> Unit,
  initialTab: String = "home",
  initialRouteTab: String = "active",
  initialOpen: String? = null,
  initialPage: String? = null,
  initialDetailTab: String = "track",
  initialDeliver: Boolean = false,
) {
  val p = PhoneV3.colors
  var tab by rememberSaveable { mutableStateOf(initialTab) }
  var routeTab by rememberSaveable { mutableStateOf(initialRouteTab) }
  var openId by rememberSaveable { mutableStateOf(initialOpen) }
  var page by rememberSaveable { mutableStateOf(initialPage) }
  var detailTab by rememberSaveable { mutableStateOf(initialDetailTab) }
  var statsWeek by rememberSaveable { mutableStateOf(false) }
  var query by rememberSaveable { mutableStateOf("") }
  var ask by remember { mutableStateOf<Ask?>(null) }
  var askSignOut by remember { mutableStateOf(false) }
  var bellOpen by remember { mutableStateOf(false) }
  val view = rememberViewMode("courier.jobs", ScreenKind.SALE)
  val seen = rememberV3Stagger()
  val scope = rememberCoroutineScope()

  val shownActive = active.map { MissionView.forDisplay(it, queue) }
  val live = shownActive.filter { CourierFlow.isLive(it.status) }
  //  پایانِ هنوز در صف از «فعال» به «پایان‌یافته» می‌رود — بی نشانی
  val finished = (shownActive.filterNot { CourierFlow.isLive(it.status) } + done.map { MissionView.forDisplay(it, queue) })
    .distinctBy { it.id }
  val waitingTotal = queue.pending.size + photosWaiting
  val shopPhone = summary?.shopPhone.orEmpty()

  fun act(c: MissionCard, to: String) {
    when (to) {
      "rejected", "failed" -> ask = Ask.Reason(c, to)
      "delivered" -> ask = Ask.Deliver(c)
      else -> onRecord(CourierAction(c, to))
    }
  }

  fun find(id: String?): MissionCard? = id?.let { k -> live.firstOrNull { it.id == k } ?: finished.firstOrNull { it.id == k } }

  fun open(c: MissionCard) { detailTab = "track"; openId = c.id }

  fun callShop() {
    if (shopPhone.isBlank()) scope.launch { snackbar.showSnackbar("شمارهٔ دکان هنوز ثبت نشده است") }
    else onCallShop(shopPhone)
  }

  LaunchedEffect(initialDeliver) {
    if (initialDeliver) find(initialOpen)?.let { ask = Ask.Deliver(it) }
  }

  //  برگشت: جزئیات ⇐ «نقدِ من» ⇐ برگهٔ دیگر ⇐ خانه
  androidx.activity.compose.BackHandler(enabled = openId != null || page != null || tab != "home") {
    when {
      openId != null -> openId = null
      page != null -> page = null
      else -> tab = "home"
    }
  }

  val screen = when {
    find(openId) != null -> "d:$openId"
    page == "cash" -> "cash"
    else -> "t:$tab"
  }

  Box(Modifier.fillMaxSize().background(p.bg)) {
    Scaffold(
      containerColor = p.bg,
      snackbarHost = { V3SnackbarHost(snackbar) },
    ) { pad ->
      //  برگه‌ها نرم جابه‌جا می‌شوند؛ زیرصفحه از سمتِ پایان می‌آید (بخشِ G)
      AnimatedContent(
        targetState = screen,
        transitionSpec = {
          val ms = motionMs(240)
          val deeper = !targetState.startsWith("t:") && initialState.startsWith("t:")
          val back = targetState.startsWith("t:") && !initialState.startsWith("t:")
          when {
            !Motion.enabled -> EnterTransition.None togetherWith ExitTransition.None
            deeper -> (slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(ms)) { it / 4 } + fadeIn(tween(ms))) togetherWith
              fadeOut(tween(ms / 2))
            back -> fadeIn(tween(ms)) togetherWith
              (slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(ms)) { it / 4 } + fadeOut(tween(ms)))
            else -> fadeIn(tween(ms)) togetherWith fadeOut(tween(ms / 2))
          }
        },
        label = "courierScreen",
      ) { key ->
        Box(Modifier.fillMaxSize().padding(pad)) {
          when {
            key.startsWith("d:") -> {
              val c = find(key.removePrefix("d:"))
              if (c != null) {
                CourierDetailPage(
                  c = c,
                  queue = queue,
                  shopName = summary?.shopName.orEmpty(),
                  detailTab = detailTab,
                  onDetailTab = { detailTab = it },
                  onBack = { openId = null },
                  onAction = { to -> act(c, to) },
                  onPhoto = { ask = Ask.Photo(c) },
                  onNavigate = { onNavigate(c) },
                  seen = seen,
                )
              }
            }
            key == "cash" -> CourierCashPage(summary, lastSyncAt, syncing, onBack = { page = null }, onRefresh = onRefresh)
            key == "t:route" -> RouteTab(
              live = live, finished = finished, queue = queue, routeTab = routeTab, onRouteTab = { routeTab = it },
              syncing = syncing, waitingTotal = waitingTotal, lastError = lastError, view = view.mode,
              wideToggle = {
                //  کلیدِ کارت/جدول فقط روی صفحهٔ پهن (گوشی همیشه کارت است)؛ تک‌رنگ
                if (LocalConfiguration.current.screenWidthDp >= 600 && view.toggleVisible) {
                  val toTable = view.mode == ViewMode.CARD
                  V3IconButton(
                    if (toTable) Icons.Outlined.TableRows else Icons.Outlined.GridView,
                    if (toTable) "نمای جدولی" else "نمای کارتی",
                    onClick = { view.set(ViewModePref.other(view.mode)) },
                  )
                }
              },
              seen = seen, onRefresh = onRefresh, onDismissRejected = onDismissRejected,
              onOpen = { open(it) }, onAction = { c, to -> act(c, to) },
            )
            key == "t:stats" -> CourierStatsTab(
              summary = summary, week = statsWeek, onWeek = { statsWeek = it }, lastSyncAt = lastSyncAt,
              syncing = syncing, onRefresh = onRefresh, onCash = { page = "cash" },
            )
            key == "t:account" -> CourierAccountTab(
              name = name, summary = summary, syncing = syncing, waiting = waitingTotal, lastError = lastError,
              lastSyncAt = lastSyncAt, onRefresh = onRefresh, onCallShop = { callShop() }, onCash = { page = "cash" },
              onSignOut = { askSignOut = true },
            )
            else -> CourierHomeTab(
              name = name, summary = summary, live = live, finished = finished, queue = queue,
              query = query, onQuery = { query = it }, seen = seen,
              onBell = { bellOpen = true },
              onOpen = { open(it) },
              onMissions = { routeTab = "active"; tab = "route" },
              onDelivered = { routeTab = "done"; tab = "route" },
              onCash = { page = "cash" },
              onCallShop = { callShop() },
            )
          }
        }
      }
    }

    //  نوارِ قرصیِ پایین فقط روی چهار برگه
    if (screen.startsWith("t:")) {
      CourierNavBar(
        current = tab,
        onPick = { tab = it },
        modifier = Modifier.align(Alignment.BottomCenter),
      )
    }

    //  ── برگه‌ها ──
    if (bellOpen) {
      CourierBellSheet(
        live = live, queue = queue, onDismiss = { bellOpen = false },
        onNewMissions = { bellOpen = false; routeTab = "active"; tab = "route" },
        onOpen = { c -> bellOpen = false; open(c) },
        onRejected = { bellOpen = false; routeTab = "active"; tab = "route" },
      )
    }
    when (val a = ask) {
      is Ask.Reason -> ReasonSheet(a.type, onDismiss = { ask = null }) { reason, note ->
        ask = null; onRecord(CourierAction(a.card, a.type, reason = reason, note = note))
      }
      is Ask.Deliver -> DeliverSheet(a.card, onDismiss = { ask = null }) { code, cash, cashReason, note ->
        ask = null; onRecord(CourierAction(a.card, "delivered", code = code, cash = cash, cashReason = cashReason, note = note))
      }
      is Ask.Photo -> PhotoFlow(a.card, onDismiss = { ask = null }) { bytes ->
        ask = null
        onPhoto(a.card, bytes)
      }
      null -> {}
    }

    if (askSignOut) {
      SignOutSheet(waitingTotal, onDismiss = { askSignOut = false }) {
        askSignOut = false
        onSignOut()
      }
    }
  }
}

/* ============================== «مسیر»: همهٔ مأموریت‌ها ============================== */

/** فاصلهٔ پایینِ فهرست‌ها تا زیرِ نوارِ قرصی نروند */
internal val NavClearance = 112.dp

@Composable
private fun RouteTab(
  live: List<MissionCard>,
  finished: List<MissionCard>,
  queue: QueueState,
  routeTab: String,
  onRouteTab: (String) -> Unit,
  syncing: Boolean,
  waitingTotal: Int,
  lastError: String?,
  view: ViewMode,
  wideToggle: @Composable () -> Unit,
  seen: V3StaggerState,
  onRefresh: () -> Unit,
  onDismissRejected: (String) -> Unit,
  onOpen: (MissionCard) -> Unit,
  onAction: (MissionCard, String) -> Unit,
) {
  val p = PhoneV3.colors
  val list = if (routeTab == "active") live else finished
  LazyColumn(
    Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = NavClearance),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item(key = "head") {
      Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("مسیر", style = courierText(28.sp, FontWeight.Bold), color = p.ink, modifier = Modifier.weight(1f))
        wideToggle()
        Spacer(Modifier.width(8.dp))
        RefreshButton(syncing, onRefresh)
      }
    }
    item(key = "sync") { SyncLine(syncing, waitingTotal, lastError) }
    itemsIndexed(queue.rejected, key = { _, r -> "rej-" + r.event.eventId }) { _, r ->
      RejectedNote(
        title = "«${actionLabel("", r.event.type)}» ثبت نشد",
        message = r.message.ifBlank { r.code },
        onDismiss = { onDismissRejected(r.event.eventId) },
        modifier = Modifier.animateItem(),
      )
    }
    item(key = "tabs") {
      V3Segmented(
        listOf("فعال · ${live.size.fa()}", "پایان‌یافته · ${finished.size.fa()}"),
        selected = if (routeTab == "active") 0 else 1,
        onSelect = { onRouteTab(if (it == 0) "active" else "done") },
      )
    }
    if (list.isEmpty()) {
      item(key = "empty-$routeTab") {
        if (routeTab == "active") {
          EmptyCard(
            title = "فعلاً مأموریتی ندارید",
            sub = "دکان که بسپارد، همین‌جا دیده می‌شود.",
            onRefresh = if (syncing) null else onRefresh,
          )
        } else {
          EmptyCard(title = "هنوز مأموریتی تمام نشده است", sub = "کارهای تحویل‌شده و برگشتی این‌جا می‌آیند.", onRefresh = null)
        }
      }
    } else {
      itemsIndexed(list, key = { _, c -> c.id }) { index, c ->
        val waiting = CourierQueue.pendingFor(queue, c.id).isNotEmpty()
        val itemMod = Modifier.animateItem().v3Stagger(seen, c.id, index)
        if (view == ViewMode.CARD) {
          MissionCardView(c, waiting, itemMod, onOpen = { onOpen(c) }, onAction = { to -> onAction(c, to) })
        } else {
          MissionRow(c, waiting, itemMod, onOpen = { onOpen(c) })
        }
      }
    }
  }
}

/** وضعیتِ صف: متن + رنگ + نقطه، و علتِ وصل‌نبودن زیرش */
@Composable
internal fun SyncLine(syncing: Boolean, waiting: Int, lastError: String?) {
  val p = PhoneV3.colors
  Column {
    val (text, tone) = when {
      syncing -> "در حالِ فرستادن…" to V3Status.INACTIVE
      waiting > 0 -> "${waiting.fa()} کار در صفِ گوشی" to V3Status.WARNING
      lastError != null -> "وصل نیست" to V3Status.INACTIVE
      else -> "همه به دکان رسید" to V3Status.DELIVERED
    }
    Row { V3StatusPill(tone, text) }
    if (lastError != null) {
      Spacer(Modifier.height(6.dp))
      Text(lastError, style = courierText(12.sp), color = p.inkMuted, modifier = Modifier.padding(start = 4.dp))
    }
  }
}

/** کارِ ردشده: پنهان نمی‌شود تا پیک خودش ببیند */
@Composable
private fun RejectedNote(title: String, message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
  val p = PhoneV3.colors
  val ui = PhoneV3.ui
  Row(
    modifier.fillMaxWidth().courierCard(p, CourierGroupShape).padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(8.dp).background(ui.cancelled.fg, CircleShape))
    Spacer(Modifier.width(10.dp))
    Column(Modifier.weight(1f)) {
      Text(title, style = courierText(15.sp, FontWeight.Bold), color = p.ink)
      Text(message, style = courierText(13.sp), color = p.inkMuted)
    }
    Box(
      Modifier.size(40.dp).clip(CircleShape).background(p.tile).clickable(onClick = onDismiss),
      contentAlignment = Alignment.Center,
    ) { Icon(Icons.Outlined.Close, contentDescription = "دیدم", tint = p.icon, modifier = Modifier.size(18.dp)) }
  }
}

@Composable
internal fun EmptyCard(title: String, sub: String, onRefresh: (() -> Unit)?) {
  val p = PhoneV3.colors
  Column(
    Modifier.fillMaxWidth().padding(top = 8.dp).courierCard(p).padding(horizontal = 24.dp, vertical = 28.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(Modifier.size(72.dp).background(p.tile, CircleShape), contentAlignment = Alignment.Center) {
      Icon(Icons.Outlined.LocalShipping, contentDescription = null, tint = PhoneV3.ui.iconActive, modifier = Modifier.size(32.dp))
    }
    Spacer(Modifier.height(14.dp))
    Text(title, style = courierText(17.sp, FontWeight.Bold), color = p.ink, textAlign = TextAlign.Center)
    Spacer(Modifier.height(6.dp))
    Text(sub, style = courierText(14.sp), color = p.inkMuted, textAlign = TextAlign.Center)
    if (onRefresh != null) {
      Spacer(Modifier.height(16.dp))
      CourierSecondary("به‌روز کردن", onRefresh, icon = Icons.Outlined.Refresh)
    }
  }
}

/** نوارِ پله‌ها: هر بار که وضعیت جلو می‌رود، پلهٔ تازه با تأخیرِ پلکانی پر می‌شود (بخشِ G) */
@Composable
internal fun StepTrack(status: String, onDark: Boolean = false) {
  val p = PhoneV3.colors
  val ui = PhoneV3.ui
  val done = reached(status)
  val track = if (onDark) p.blackRaised else p.hairline
  val fillColor = if (onDark) ui.primaryOnDark else ui.primary
  val on = if (onDark) p.onBlack else p.ink
  val off = if (onDark) p.onBlackMuted else p.inkMuted
  Column(Modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      STEPS.forEachIndexed { i, _ ->
        val fill by animateFloatAsState(
          targetValue = if (i < done) 1f else 0f,
          animationSpec = if (Motion.enabled) tween(420, delayMillis = i * 70) else snap(),
          label = "step$i",
        )
        Box(Modifier.weight(1f).height(6.dp).background(track, CourierPillShape)) {
          Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(fillColor, CourierPillShape))
        }
      }
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      STEPS.forEachIndexed { i, label ->
        Text(
          label,
          style = courierText(12.sp, if (i < done) FontWeight.Bold else FontWeight.Normal),
          color = if (i < done) on else off,
          textAlign = TextAlign.Center,
          maxLines = 1,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MissionCardView(
  c: MissionCard,
  waiting: Boolean,
  modifier: Modifier,
  onOpen: () -> Unit,
  onAction: (String) -> Unit,
) {
  val p = PhoneV3.colors
  val live = CourierFlow.isLive(c.status)
  Column(
    modifier.fillMaxWidth().courierCard(p).clip(CourierCardShape).clickable(onClick = onOpen).padding(18.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("سفارشِ #${c.orderNo.fa()}", style = courierText(14.sp, FontWeight.Bold), color = p.ink, modifier = Modifier.weight(1f))
      if (waiting) { QueuedPill(); Spacer(Modifier.width(6.dp)) }
      MissionStatus(c.status)
    }
    if (c.recallRequested && live) {
      Spacer(Modifier.height(10.dp))
      V3Message(V3Tone.ERROR, RECALL_NOTE)
    }
    //  DLV-02: «اول نشانه»
    if (c.landmark.isNotBlank()) {
      Spacer(Modifier.height(10.dp))
      Text(c.landmark, style = courierText(18.sp, FontWeight.Bold), color = p.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    if (c.addressText.isNotBlank()) {
      Spacer(Modifier.height(2.dp))
      Text(c.addressText, style = courierText(14.sp), color = p.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    val chips = live && (c.expectedCollectMinor > 0 || c.requiresCode) || !live && (c.cashCollectedMinor != null || c.fareMinor > 0)
    if (chips) {
      Spacer(Modifier.height(12.dp))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (live) {
          if (c.expectedCollectMinor > 0) CourierInfoChip(Icons.Outlined.Payments, "نقد: ${afn(c.expectedCollectMinor)}")
          if (c.requiresCode) CourierInfoChip(Icons.Outlined.Lock, "کدِ تحویل")
        } else {
          c.cashCollectedMinor?.let { CourierInfoChip(Icons.Outlined.CheckCircle, "گرفته شد: ${afn(it)}", positive = true) }
          if (c.fareMinor > 0) CourierInfoChip(Icons.Outlined.Payments, "کرایه: ${afn(c.fareMinor)}")
        }
      }
    }
    if (reached(c.status) >= 0 && live) {
      Spacer(Modifier.height(14.dp))
      StepTrack(c.status)
    }
    val actions = if (live) CourierFlow.actions(c.status) else emptyList()
    if (actions.isNotEmpty()) {
      Spacer(Modifier.height(16.dp))
      val primary = actions.firstOrNull { !isNegative(it) }
      if (c.status == "requested") {
        //  کارِ تازه: قبول و رد کنارِ هم، هم‌اندازه
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          primary?.let { StepButton(actionLabel(c.status, it), { onAction(it) }, Modifier.weight(1f)) }
          actions.filter { isNegative(it) }.forEach {
            CourierSecondary(actionLabel(c.status, it), { onAction(it) }, Modifier.weight(1f), negative = true, height = 56.dp)
          }
        }
      } else if (primary != null) {
        //  یک دکمهٔ بزرگ برای پلهٔ بعد؛ «ناموفق» و بقیه در صفحهٔ جزئیات
        StepButton(actionLabel(c.status, primary), { onAction(primary) }, Modifier.fillMaxWidth())
      }
    }
  }
}

/** نمای جدولیِ فشرده (صفحهٔ بزرگ) — همان داده؛ زدنِ ردیف جزئیات را باز می‌کند */
@Composable
private fun MissionRow(c: MissionCard, waiting: Boolean, modifier: Modifier, onOpen: () -> Unit) {
  val p = PhoneV3.colors
  Row(
    modifier.fillMaxWidth().courierCard(p, CourierGroupShape).clip(CourierGroupShape).clickable(onClick = onOpen)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Text(c.orderNo.fa(), style = courierText(13.sp, FontWeight.Medium), color = p.inkMuted, modifier = Modifier.width(56.dp))
    Text(c.landmark.ifBlank { "—" }, style = courierText(15.sp), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    Text(if (c.expectedCollectMinor > 0) afn(c.expectedCollectMinor) else "—", style = courierText(13.sp), color = p.inkMuted)
    if (waiting) QueuedPill()
    MissionStatus(c.status)
  }
}

/* ============================== برگه‌ها ============================== */

@Composable
private fun ReasonSheet(type: String, onDismiss: () -> Unit, onDone: (String, String) -> Unit) {
  var reason by remember { mutableStateOf("") }
  var note by remember { mutableStateOf("") }
  val ready = reason.isNotBlank() && (reason != "other" || note.isNotBlank())
  V3Sheet(onDismiss = onDismiss, title = if (type == "rejected") "ردِ این کار" else "تحویل نشد") { _ ->
    CourierSheetSub("دلیل را انتخاب کنید")
    Spacer(Modifier.height(14.dp))
    ReasonList(CourierFlow.reasonsFor(type).map { it to (REASON_LABEL[it] ?: it) }, reason) { reason = it }
    Spacer(Modifier.height(14.dp))
    V3TextField(
      note, { note = it.take(CourierQueue.MAX_NOTE) },
      "توضیح" + if (reason == "other") " (لازم)" else " (اختیاری)",
      Modifier.fillMaxWidth(), singleLine = false,
    )
    Spacer(Modifier.height(18.dp))
    StepButton(
      "ثبت", { onDone(reason, note.trim()) }, Modifier.fillMaxWidth(),
      kind = if (type == "failed") V3ButtonKind.DANGER else V3ButtonKind.PRIMARY, enabled = ready,
    )
    Spacer(Modifier.height(8.dp))
    CourierSecondary("انصراف", onDismiss, Modifier.fillMaxWidth())
  }
}

/** یکی از چند دلیل — ردیف‌های انتخابیِ کیت در یک کارت */
@Composable
private fun ReasonList(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
  V3Card(padding = PaddingValues(0.dp)) {
    options.forEachIndexed { i, (id, label) ->
      if (i > 0) V3Divider(start = 16.dp)
      V3ListRow(label, tile = false, chevron = false, selected = id == selected, onClick = { onSelect(id) })
    }
  }
}

/** تحویل: کدِ مشتری، نقدِ گرفته‌شده (و دلیلِ فرق)، و تأیید — همان قواعدِ پیشین */
@Composable
private fun DeliverSheet(c: MissionCard, onDismiss: () -> Unit, onDone: (code: String, cash: Long?, cashReason: String, note: String) -> Unit) {
  val p = PhoneV3.colors
  val collects = c.expectedCollectMinor > 0
  var code by remember { mutableStateOf("") }
  var cash by remember { mutableStateOf(if (collects) money(c.expectedCollectMinor / 100.0).replace(",", "") else "") }
  var cashReason by remember { mutableStateOf("") }
  var note by remember { mutableStateOf("") }
  val minor = if (collects) minorOf(cash) else null
  val differs = collects && minor != null && minor != c.expectedCollectMinor
  val codeOk = !c.requiresCode || CourierQueue.latinDigits(code).length >= 4
  val cashOk = !collects || (minor != null && (!differs || (cashReason.isNotBlank() && (cashReason != "other" || note.isNotBlank()))))
  V3Sheet(onDismiss = onDismiss, title = "تحویل به مشتری") { _ ->
    CourierSheetSub("سفارشِ #${c.orderNo.fa()}")
    if (collects) {
      Spacer(Modifier.height(16.dp))
      Column(Modifier.fillMaxWidth().background(p.tile, CourierGroupShape).padding(vertical = 16.dp)) {
        CourierBigAmount("نقدِ این سفارش", afn(c.expectedCollectMinor))
      }
    }
    if (c.requiresCode) {
      Spacer(Modifier.height(16.dp))
      V3TextField(
        code, { code = numberInput(it).filter { ch -> ch != '.' }.take(12) },
        "کدِ تحویل — از مشتری بپرسید", Modifier.fillMaxWidth(), kind = V3FieldKind.BARCODE, placeholder = "••••",
      )
    }
    if (collects) {
      Spacer(Modifier.height(14.dp))
      V3TextField(
        cash, { cash = numberInput(it) }, "نقدِ گرفته‌شده (افغانی)", Modifier.fillMaxWidth(),
        kind = V3FieldKind.MONEY,
      )
      if (differs) {
        Spacer(Modifier.height(14.dp))
        Text("مبلغ با سفارش فرق دارد — دلیل:", style = courierText(14.sp, FontWeight.Bold), color = p.ink)
        Spacer(Modifier.height(8.dp))
        ReasonList(CourierFlow.CASH_REASONS.map { it to (REASON_LABEL[it] ?: it) }, cashReason) { cashReason = it }
        Spacer(Modifier.height(12.dp))
        V3TextField(
          note, { note = it.take(CourierQueue.MAX_NOTE) },
          "توضیح" + if (cashReason == "other") " (لازم)" else " (اختیاری)",
          Modifier.fillMaxWidth(), singleLine = false,
        )
      }
    }
    if (!c.requiresCode && !collects) {
      Spacer(Modifier.height(12.dp))
      Text("بسته به مشتری رسید؟", style = courierText(16.sp), color = p.ink)
    }
    Spacer(Modifier.height(18.dp))
    val label = if (collects && minor != null) "تحویل شد · ${afn(minor)} گرفتم" else "تحویل شد"
    StepButton(
      label,
      { onDone(CourierQueue.latinDigits(code), minor, if (differs) cashReason else "", if (differs) note.trim() else "") },
      Modifier.fillMaxWidth(),
      enabled = codeOk && cashOk,
    )
    Spacer(Modifier.height(8.dp))
    CourierSecondary("انصراف", onDismiss, Modifier.fillMaxWidth())
  }
}

/** خروج: با کارِ در صف، هشدار و دکمهٔ خطر (برگهٔ تأییدِ کیت) */
@Composable
private fun SignOutSheet(waiting: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
  V3ConfirmSheet(
    title = "خروج از حسابِ پیک",
    message = if (waiting > 0)
      "${waiting.fa()} کار هنوز به دکان نرسیده و با خروج از این گوشی پاک می‌شود. اول به اینترنت وصل شوید و «به‌روز کردن» را بزنید."
    else "مأموریت‌ها و نشانی‌ها از این گوشی پاک می‌شوند.",
    confirm = if (waiting > 0) "با وجودِ این خارج شو" else "خروج",
    onConfirm = onConfirm,
    onDismiss = onDismiss,
    icon = Icons.AutoMirrored.Outlined.Logout,
    danger = waiting > 0,
  )
}

/**
 *  عکسِ بسته (DLV-08): فقط با رضایتِ مشتری (`photoAllowed` از سرور) و فقط از
 *  بسته، نه از شخص — پیک پیش از دوربین تأیید می‌کند.
 */
@Composable
private fun PhotoFlow(c: MissionCard, onDismiss: () -> Unit, onReady: (ByteArray) -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val p = PhoneV3.colors
  var parcelOnly by remember { mutableStateOf(false) }
  var pick by remember { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  if (!pick) {
    V3Sheet(onDismiss = onDismiss, title = "عکسِ بسته") { _ ->
      Text(
        "مشتریِ سفارشِ #${c.orderNo.fa()} به عکسِ تحویل رضایت داده است. عکس فقط از بسته گرفته شود؛ چهرهٔ کسی در عکس نباشد.",
        style = courierText(15.sp), color = p.inkMuted,
      )
      Spacer(Modifier.height(12.dp))
      V3Card(padding = PaddingValues(0.dp)) {
        V3ToggleRow("عکس فقط از بسته است", parcelOnly, { parcelOnly = it }, icon = Icons.Outlined.PhotoCamera, hue = V3Hue.SKY)
      }
      error?.let {
        Spacer(Modifier.height(8.dp))
        V3Message(V3Tone.ERROR, it)
      }
      Spacer(Modifier.height(18.dp))
      StepButton(
        "گرفتنِ عکس", { pick = true }, Modifier.fillMaxWidth(),
        enabled = parcelOnly && !busy, loading = busy, icon = Icons.Outlined.PhotoCamera,
      )
      Spacer(Modifier.height(8.dp))
      CourierSecondary("انصراف", onDismiss, Modifier.fillMaxWidth())
    }
  }
  PhotoSourceSheet(
    open = pick,
    onDismiss = { pick = false },
    note = "فقط بسته؛ عکس پس از رسیدن به دکان از این گوشی پاک می‌شود",
  ) { uri ->
    busy = true
    scope.launch {
      val bytes = withContext(Dispatchers.IO) { runCatching { parcelJpeg(context, uri) }.getOrNull() }
      busy = false
      if (bytes == null) error = "عکس خوانده نشد؛ دوباره بگیرید" else onReady(bytes)
    }
  }
}

/** کوچک و فشرده — زیرِ سقفِ ۶۰۰ کیلوبایتِ سرور */
private fun parcelJpeg(context: Context, uri: Uri): ByteArray? {
  val bmp = PhotoStore.decode(context, uri) ?: return null
  val edge = maxOf(bmp.width, bmp.height)
  val scaled = if (edge > 1280) Bitmap.createScaledBitmap(bmp, bmp.width * 1280 / edge, bmp.height * 1280 / edge, true) else bmp
  var quality = 80
  var out: ByteArray
  do {
    out = ByteArrayOutputStream().use { s -> scaled.compress(Bitmap.CompressFormat.JPEG, quality, s); s.toByteArray() }
    quality -= 15
  } while (out.size > 550 * 1024 && quality >= 35)
  if (scaled !== bmp) scaled.recycle()
  bmp.recycle()
  return out.takeIf { it.size in 100..(600 * 1024) }
}
