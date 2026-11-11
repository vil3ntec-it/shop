package ir.vil3ntec.tohid.ui.courier

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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TableRows
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.core.courier.CourierChecks
import ir.vil3ntec.tohid.core.courier.CourierFlow
import ir.vil3ntec.tohid.core.courier.CourierInputError
import ir.vil3ntec.tohid.core.courier.CourierQueue
import ir.vil3ntec.tohid.core.courier.CourierSummary
import ir.vil3ntec.tohid.core.courier.LocationConsent
import ir.vil3ntec.tohid.core.courier.MissionCard
import ir.vil3ntec.tohid.core.courier.MissionView
import ir.vil3ntec.tohid.core.courier.QueueState
import ir.vil3ntec.tohid.core.i18n.StrCourier
import ir.vil3ntec.tohid.core.i18n.StrCourierApp
import ir.vil3ntec.tohid.data.CourierStore
import ir.vil3ntec.tohid.data.CourierSync
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.fa
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.components.common.ScreenKind
import ir.vil3ntec.tohid.ui.components.common.ViewMode
import ir.vil3ntec.tohid.ui.components.common.ViewModePref
import ir.vil3ntec.tohid.ui.components.common.rememberViewMode
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3IconButton
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3SnackbarHost
import ir.vil3ntec.tohid.ui.v3.V3StaggerState
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Stagger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

/** S94: «اشتراکِ موقعیت» فقط آنلاین — همین کدها ⇒ «به اینترنت وصل شوید» */
private val CONSENT_ONLINE = setOf("offline", "unreachable", "timeout")

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
  /** DLV-07: برچسبِ همهٔ کیسه‌ها با «بسته را گرفتم»؛ DLV-17: موقعیتِ همین لحظه با «تحویل شد» */
  val bags: List<String> = emptyList(),
  val position: CourierChecks.Point? = null,
)

@Composable
fun CourierHome(onSignedOut: () -> Unit) {
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
  var summaryError by remember { mutableStateOf<String?>(null) }
  var extras by remember { mutableStateOf(CourierExtras.of(store)) }   //  پرچم‌های مسیر، امتیاز و تماس (DLV-04/18/02)
  var calling by remember { mutableStateOf(false) }   //  یک «تماس با مشتری» در هر لحظه (هر بار در سابقهٔ دکان می‌نشیند)
  var consent by remember { mutableStateOf(store.locationConsent) }   //  S94: «اشتراکِ موقعیت» ِ خودِ پیک
  var consentBusy by remember { mutableStateOf(false) }

  fun reloadLocal() {
    queue = store.queue(); active = store.cards("active"); done = store.cards("done")
    name = store.courierName; photosWaiting = store.photos().size
    summary = store.summary(); lastSyncAt = store.lastSyncAt; extras = CourierExtras.of(store)
    consent = store.locationConsent
  }

  /**
   *  S94: «اشتراکِ موقعیت» — فقط آنلاین و فقط پس از پاسخِ سرور (کلید حدس نمی‌زند)؛ «نه» نقطه‌های فرستاده را روی سرور پاک
   *  می‌کند و سرویسِ موقعیت همان دم خاموش می‌شود (`key(consent.mayShare)` پایین). یک درخواست در هر لحظه.
   */
  fun setConsent(granted: Boolean) {
    if (consentBusy) return
    consentBusy = true
    scope.launch {
      val r = try { Backend.courier(context).setLocationConsent(granted) } finally { consentBusy = false }
      val text = when (r) {
        is ApiResult.Success -> {
          store.locationConsent = store.locationConsent.answered(r.value.granted, r.value.grantedAt)
          consent = store.locationConsent
          if (r.value.granted) StrCourierApp.shareLocationDone else StrCourierApp.shareLocationStopped
        }
        is ApiResult.Failure -> if (r.error.code in CONSENT_ONLINE) StrCourierApp.shareLocationOnline else r.error.userMessage
      }
      snackbar.showSnackbar(text)
    }
  }

  suspend fun sync() {
    if (syncing) return
    syncing = true
    val out = runCatching { CourierSync.flush(context, store) }.getOrNull()
    syncing = false
    reloadLocal()
    lastError = out?.failure?.let { courierFailureText(it, StrCourierApp.offlineQueued) }
    summaryError = out?.summaryFailure?.let { courierFailureText(it, StrCourierApp.offlineShort) }   //  «نقدِ من» تازه نشد ⇒ نقد و آمار می‌گویند
    out?.photoErrors?.firstOrNull()?.let { snackbar.showSnackbar(it) }
  }

  /** هر کار: اول صفِ گوشی، بعد اگر شد سرور */
  fun record(a: CourierAction) {
    try {
      val (next, _) = CourierQueue.enqueue(
        store.queue(), "cev-" + ir.vil3ntec.tohid.sync.v1.Ulid.next(), a.card.id, a.type, System.currentTimeMillis(),
        //  CAS (§۲۸.۵): همان وضعیتی که پیک روی کارت دید (سرور + صف)
        from = a.card.status, reason = a.reason, note = a.note,
        cashCollectedMinor = a.cash, cashReason = a.cashReason, code = a.code, bags = a.bags, position = a.position,
      )
      store.saveQueue(next)
      queue = next
      scope.launch { sync() }
    } catch (e: CourierInputError) {
      scope.launch { snackbar.showSnackbar(ir.vil3ntec.tohid.core.i18n.courierInputText(e.message) ?: StrCourierApp.notSaved) }
    }
  }

  //  باز شدن و هر نیم دقیقه تا صفحه باز است (همگام‌سازی بی سرویسِ پس‌زمینه — §۳.۱۱)
  LaunchedEffect(Unit) { while (true) { sync(); delay(30_000) } }
  //  DLV-16: تنها سرویسِ پیش‌زمینهٔ پیک — موقعیت فقط برای مأموریتِ «در راه» که سرور خواست (`CourierLiveTrackAndroid.kt`)
  //  S94: رضایتِ پیک عوض شد ⇒ از نو (روشن/خاموش کردنِ سرویس با همان قاعده)
  key(consent.mayShare) { CourierLiveTrack(active, queue) }

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
    summaryError = summaryError,
    extras = extras,
    consent = consent,
    consentBusy = consentBusy,
    onConsent = ::setConsent,
    onRefresh = { scope.launch { sync() } },
    onDismissRejected = { eventId ->
      val next = CourierQueue.dismissRejected(store.queue(), eventId)
      store.saveQueue(next); queue = next
    },
    onRecord = ::record,
    onRate = { card, stars, tags ->
      scope.launch { val text = rateMission(Backend.courier(context), store, card, stars, tags); extras = CourierExtras.of(store); snackbar.showSnackbar(text) }
    },
    onPhoto = { card, bytes ->
      val eventId = "cph-" + ir.vil3ntec.tohid.sync.v1.Ulid.next()
      store.addPhoto(CourierStore.PendingPhoto(eventId, card.id, System.currentTimeMillis()), bytes)
      photosWaiting = store.photos().size
      scope.launch { snackbar.showSnackbar(StrCourierApp.photoQueued); sync() }
    },
    onCallShop = { phone ->
      //  فقط صفحهٔ شماره‌گیر با شمارهٔ دکان باز می‌شود؛ تماس را خودِ پیک می‌زند
      val opened = runCatching {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
      }.isSuccess
      if (!opened) scope.launch { snackbar.showSnackbar(StrCourierApp.dialerFailed) }
    },
    onNavigate = { c -> openMissionRoute(context, c) },   // DLV-05: پیوندهای سرور، وگرنه همان پیشین
    onCallCustomer = { c ->   //  DLV-02: شماره همین لحظه از سرور ⇐ شماره‌گیر؛ نشد ⇒ نوارِ پیام
      if (!calling) { calling = true; scope.launch { val text = try { callCustomerFrom(context, c) } finally { calling = false }; text?.let { snackbar.showSnackbar(it) } } }
    },
    onSignOut = {
      scope.launch {
        runCatching { Backend.auth(context).signOut() }
        CourierTrackPlatform.sync(context, false)   //  DLV-16: خروج ⇒ هیچ موقعیتی
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
  summaryError: String? = null,
  extras: CourierExtras = CourierExtras(),
  onRate: (MissionCard, Int, List<String>) -> Unit = { _, _, _ -> },
  onCallCustomer: (MissionCard) -> Unit = {},
  consent: LocationConsent = LocationConsent(),
  consentBusy: Boolean = false,
  onConsent: (Boolean) -> Unit = {},
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
  var check by remember { mutableStateOf<CheckAsk?>(null) }   //  DLV-04/07/17/18 (`CourierChecksV3.kt`)
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
  val staleError = summaryError ?: lastError   //  نقد و آمار کهنه‌اند اگر «نقدِ من» یا کلِ همگام‌سازی نشد

  //  کارِ آماده: پیش‌شرط دارد (کیسه، موقعیت) ⇒ اول برگه‌اش، وگرنه همان دم به صف
  fun submit(a: CourierAction) { check = checkFor(a); if (check == null) onRecord(a) }
  val rateFor: (MissionCard) -> (() -> Unit)? = { c -> if (extras.canRate(c)) ({ check = CheckAsk.Rate(c) }) else null }
  //  DLV-02: همان دروازهٔ رابطِ پیشین — پرچمِ دکان و پس از «قبول» تا پایان (کارتِ نمایشی: وضعیتِ صف هم حساب است)
  val callFor: (MissionCard) -> (() -> Unit)? = { c -> if (extras.canCall(c)) ({ onCallCustomer(c) }) else null }

  fun act(c: MissionCard, to: String) {
    when (to) {
      "rejected", "failed" -> ask = Ask.Reason(c, to)
      "delivered" -> ask = Ask.Deliver(c)
      else -> submit(CourierAction(c, to))
    }
  }

  fun find(id: String?): MissionCard? = id?.let { k -> live.firstOrNull { it.id == k } ?: finished.firstOrNull { it.id == k } }

  fun open(c: MissionCard) { detailTab = "track"; openId = c.id }

  fun callShop() {
    if (shopPhone.isBlank()) scope.launch { snackbar.showSnackbar(StrCourierApp.shopPhoneMissing) }
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
                  onRate = rateFor(c),
                  onCall = callFor(c),
                )
              }
            }
            key == "cash" -> CourierCashPage(summary, staleError, syncing, onBack = { page = null }, onRefresh = onRefresh)
            key == "t:route" -> RouteTab(
              live = live, finished = finished, queue = queue, routeTab = routeTab, onRouteTab = { routeTab = it },
              syncing = syncing, waitingTotal = waitingTotal, lastError = lastError, view = view.mode,
              wideToggle = {
                //  کلیدِ کارت/جدول فقط روی صفحهٔ پهن (گوشی همیشه کارت است)؛ تک‌رنگ
                if (LocalConfiguration.current.screenWidthDp >= 600 && view.toggleVisible) {
                  val toTable = view.mode == ViewMode.CARD
                  V3IconButton(
                    if (toTable) Icons.Outlined.TableRows else Icons.Outlined.GridView,
                    if (toTable) ir.vil3ntec.tohid.core.i18n.Str.tableView else ir.vil3ntec.tohid.core.i18n.Str.cardView,
                    onClick = { view.set(ViewModePref.other(view.mode)) },
                  )
                }
              },
              seen = seen, onRefresh = onRefresh, onDismissRejected = onDismissRejected,
              onOpen = { open(it) }, onAction = { c, to -> act(c, to) },
              onRoutePlan = if (extras.routeEnabled) ({ check = CheckAsk.Route }) else null, rateFor = rateFor, callFor = callFor,
            )
            key == "t:stats" -> CourierStatsTab(
              summary = summary, week = statsWeek, onWeek = { statsWeek = it }, staleError = staleError,
              syncing = syncing, onRefresh = onRefresh, onCash = { page = "cash" },
            )
            key == "t:account" -> CourierAccountTab(
              name = name, summary = summary, syncing = syncing, waiting = waitingTotal, lastError = lastError,
              lastSyncAt = lastSyncAt, onRefresh = onRefresh, onCallShop = { callShop() }, onCash = { page = "cash" },
              onSignOut = { askSignOut = true }, consent = consent, consentBusy = consentBusy, onConsent = onConsent,
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
        ask = null; submit(CourierAction(a.card, "delivered", code = code, cash = cash, cashReason = cashReason, note = note))
      }
      is Ask.Photo -> PhotoFlow(a.card, onDismiss = { ask = null }) { bytes ->
        ask = null
        onPhoto(a.card, bytes)
      }
      null -> {}
    }
    CourierCheckSheets(check, live, onDismiss = { check = null }, onRecord = onRecord, onRate = onRate)

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
  onRoutePlan: (() -> Unit)?,
  rateFor: (MissionCard) -> (() -> Unit)?,
  callFor: (MissionCard) -> (() -> Unit)?,
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
        Text(StrCourierApp.route, style = courierText(28.sp, FontWeight.Bold), color = p.ink, modifier = Modifier.weight(1f))
        wideToggle()
        //  DLV-04: «مسیرِ پیشنهادی» فقط با پرچمِ دکان (`/courier/me`)
        onRoutePlan?.let { Spacer(Modifier.width(8.dp)); V3IconButton(Icons.Outlined.Place, StrCourier.routeTitle, onClick = it) }
        Spacer(Modifier.width(8.dp))
        RefreshButton(syncing, onRefresh)
      }
    }
    item(key = "sync") { SyncLine(syncing, waitingTotal, lastError) }
    itemsIndexed(queue.rejected, key = { _, r -> "rej-" + r.event.eventId }) { _, r ->
      RejectedNote(
        title = StrCourierApp.actionNotSaved(actionLabel("", r.event.type)),
        message = r.message.ifBlank { r.code },
        onDismiss = { onDismissRejected(r.event.eventId) },
        modifier = Modifier.animateItem(),
      )
    }
    item(key = "tabs") {
      V3Segmented(
        listOf(StrCourierApp.tabActive(live.size.fa()), StrCourierApp.tabFinished(finished.size.fa())),
        selected = if (routeTab == "active") 0 else 1,
        onSelect = { onRouteTab(if (it == 0) "active" else "done") },
      )
    }
    if (list.isEmpty()) {
      item(key = "empty-$routeTab") {
        if (routeTab == "active") {
          EmptyCard(
            title = StrCourierApp.noMissionsTitle,
            sub = StrCourierApp.noMissionsBody,
            onRefresh = if (syncing) null else onRefresh,
          )
        } else {
          EmptyCard(title = StrCourierApp.noFinishedTitle, sub = StrCourierApp.noFinishedBody, onRefresh = null)
        }
      }
    } else {
      itemsIndexed(list, key = { _, c -> c.id }) { index, c ->
        val waiting = CourierQueue.pendingFor(queue, c.id).isNotEmpty()
        val itemMod = Modifier.animateItem().v3Stagger(seen, c.id, index)
        if (view == ViewMode.CARD) {
          MissionCardView(c, waiting, itemMod, onOpen = { onOpen(c) }, onAction = { to -> onAction(c, to) }, onRate = rateFor(c), onCall = callFor(c))
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
      syncing -> StrCourierApp.sendingDots to V3Status.INACTIVE
      waiting > 0 -> StrCourierApp.queuedOnPhone(waiting.fa()) to V3Status.WARNING
      lastError != null -> StrCourierApp.notConnected to V3Status.INACTIVE
      else -> StrCourierApp.allReachedShop to V3Status.DELIVERED
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
    ) { Icon(Icons.Outlined.Close, contentDescription = StrCourierApp.seen, tint = p.icon, modifier = Modifier.size(18.dp)) }
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
      CourierSecondary(StrCourierApp.refresh, onRefresh, icon = Icons.Outlined.Refresh)
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

/* ============================== برگه‌ها ============================== */

@Composable
private fun ReasonSheet(type: String, onDismiss: () -> Unit, onDone: (String, String) -> Unit) {
  var reason by remember { mutableStateOf("") }
  var note by remember { mutableStateOf("") }
  val ready = reason.isNotBlank() && (reason != "other" || note.isNotBlank())
  V3Sheet(onDismiss = onDismiss, title = if (type == "rejected") StrCourierApp.rejectTitle else StrCourierApp.hlFailed) { _ ->
    CourierSheetSub(StrCourierApp.pickReason)
    Spacer(Modifier.height(14.dp))
    ReasonList(CourierFlow.reasonsFor(type).map { it to (REASON_LABEL[it] ?: it) }, reason) { reason = it }
    Spacer(Modifier.height(14.dp))
    V3TextField(
      note, { note = it.take(CourierQueue.MAX_NOTE) },
      if (reason == "other") StrCourierApp.noteRequired else StrCourierApp.noteOptional,
      Modifier.fillMaxWidth(), singleLine = false,
    )
    Spacer(Modifier.height(18.dp))
    StepButton(
      StrCourierApp.save, { onDone(reason, note.trim()) }, Modifier.fillMaxWidth(),
      kind = if (type == "failed") V3ButtonKind.DANGER else V3ButtonKind.PRIMARY, enabled = ready,
    )
    Spacer(Modifier.height(8.dp))
    CourierSecondary(StrCourierApp.cancel, onDismiss, Modifier.fillMaxWidth())
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
  V3Sheet(onDismiss = onDismiss, title = StrCourierApp.deliverTitle) { _ ->
    CourierSheetSub(StrCourierApp.orderNo(c.orderNo.fa()))
    if (collects) {
      Spacer(Modifier.height(16.dp))
      Column(Modifier.fillMaxWidth().background(p.tile, CourierGroupShape).padding(vertical = 16.dp)) {
        CourierBigAmount(StrCourierApp.orderCash, afn(c.expectedCollectMinor))
      }
    }
    if (c.requiresCode) {
      Spacer(Modifier.height(16.dp))
      V3TextField(
        code, { code = numberInput(it).filter { ch -> ch != '.' }.take(12) },
        StrCourierApp.codeAsk, Modifier.fillMaxWidth(), kind = V3FieldKind.BARCODE, placeholder = "••••",
      )
    }
    if (collects) {
      Spacer(Modifier.height(14.dp))
      V3TextField(
        cash, { cash = numberInput(it) }, StrCourierApp.cashTakenField, Modifier.fillMaxWidth(),
        kind = V3FieldKind.MONEY,
      )
      if (differs) {
        Spacer(Modifier.height(14.dp))
        Text(StrCourierApp.amountDiffers, style = courierText(14.sp, FontWeight.Bold), color = p.ink)
        Spacer(Modifier.height(8.dp))
        ReasonList(CourierFlow.CASH_REASONS.map { it to (REASON_LABEL[it] ?: it) }, cashReason) { cashReason = it }
        Spacer(Modifier.height(12.dp))
        V3TextField(
          note, { note = it.take(CourierQueue.MAX_NOTE) },
          if (cashReason == "other") StrCourierApp.noteRequired else StrCourierApp.noteOptional,
          Modifier.fillMaxWidth(), singleLine = false,
        )
      }
    }
    if (!c.requiresCode && !collects) {
      Spacer(Modifier.height(12.dp))
      Text(StrCourierApp.reachedCustomerQ, style = courierText(16.sp), color = p.ink)
    }
    Spacer(Modifier.height(18.dp))
    val label = if (collects && minor != null) StrCourierApp.deliveredTook(afn(minor)) else StrCourierApp.stDelivered
    StepButton(
      label,
      { onDone(CourierQueue.latinDigits(code), minor, if (differs) cashReason else "", if (differs) note.trim() else "") },
      Modifier.fillMaxWidth(),
      enabled = codeOk && cashOk,
    )
    Spacer(Modifier.height(8.dp))
    CourierSecondary(StrCourierApp.cancel, onDismiss, Modifier.fillMaxWidth())
  }
}

/** خروج: با کارِ در صف، هشدار و دکمهٔ خطر (برگهٔ تأییدِ کیت) */
@Composable
private fun SignOutSheet(waiting: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
  V3ConfirmSheet(
    title = StrCourierApp.signOutTitle,
    message = if (waiting > 0)
      StrCourierApp.signOutWaiting(waiting.fa())
    else StrCourierApp.signOutBody,
    confirm = if (waiting > 0) StrCourierApp.signOutAnyway else StrCourierApp.signOut,
    onConfirm = onConfirm,
    onDismiss = onDismiss,
    icon = Icons.AutoMirrored.Outlined.Logout,
    danger = waiting > 0,
  )
}
