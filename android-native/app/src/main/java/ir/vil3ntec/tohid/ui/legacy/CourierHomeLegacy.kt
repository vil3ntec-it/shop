/*
 *  ⛔ رابطِ امروز (پیش از نسخهٔ ۳) — رونوشتِ همان `ui/courier/CourierHome.kt` ِ main، فقط با پسوندِ `Legacy`.
 *  وقتی پرچمِ `UiV3` خاموش است (پیش‌فرض) همین دیده می‌شود؛ نسخهٔ ۳ در فایلِ اصلی است.
 *  دستی ویرایش نکنید مگر برای همان رابطِ امروز؛ با روشن‌شدنِ نسخهٔ ۳ برای همه، این پوشه برداشته می‌شود.
 */
package ir.vil3ntec.tohid.ui.courier

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.courier.CourierChecks
import ir.vil3ntec.tohid.core.courier.CourierFlow
import ir.vil3ntec.tohid.core.courier.CourierInputError
import ir.vil3ntec.tohid.core.courier.CourierQueue
import ir.vil3ntec.tohid.core.courier.MissionCard
import ir.vil3ntec.tohid.core.courier.MissionView
import ir.vil3ntec.tohid.core.delivery.DeliveryRating
import ir.vil3ntec.tohid.core.i18n.StrCourier
import ir.vil3ntec.tohid.core.orders.DeliveryPrefs
import ir.vil3ntec.tohid.data.CourierStore
import ir.vil3ntec.tohid.data.CourierSync
import ir.vil3ntec.tohid.data.PhotoStore
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.ScreenKind
import ir.vil3ntec.tohid.ui.components.common.SegmentTab
import ir.vil3ntec.tohid.ui.components.common.SegmentedTabs
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.components.common.ViewMode
import ir.vil3ntec.tohid.ui.components.common.ViewModeToggle
import ir.vil3ntec.tohid.ui.components.common.rememberViewMode
import ir.vil3ntec.tohid.ui.screens.EmptyNote
import ir.vil3ntec.tohid.ui.screens.NumberField
import ir.vil3ntec.tohid.ui.screens.PhotoSourceSheetLegacy
import ir.vil3ntec.tohid.ui.screens.TohidButton
import ir.vil3ntec.tohid.ui.screens.TohidConfirmDialog
import ir.vil3ntec.tohid.ui.screens.TohidSnackbarLegacy
import ir.vil3ntec.tohid.ui.screens.TohidTextField
import ir.vil3ntec.tohid.ui.screens.parcelJpegLegacy
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 *  حالتِ پیک — DLV-01/02/08/09/10/11، v3 §۳.۱۱.
 *
 *  وقتی نقشِ نشست «پیک» است، برنامه به‌جای دکان همین را باز می‌کند: فهرستِ
 *  مأموریت‌ها (کارت)، قبول/رد، گرفتنِ بسته / در راه / رسیدم، کدِ تحویل، عکسِ
 *  بسته با رضایت، ناموفق و برگشت، و نقدِ گرفته‌شده. هر کار اول در صفِ گوشی
 *  می‌نشیند (eventId + زمان و ترتیبِ دستگاه) و هر وقت اینترنت بود می‌رود.
 *
 *  ⛔ بی زمان‌سنج برای پیک (DLV-20). شماره همیشه پوشیده؛ نشانی پس از پایانِ
 *  مأموریت دیده نمی‌شود (حتی وقتی پایان هنوز در صف است).
 */

private val STATUS_LABELLegacy = mapOf(
  "requested" to "تازه", "assigned" to "قبول شد", "picked_up" to "بسته گرفته شد",
  "moving" to "در راه", "arrived" to "رسیده", "failed" to "ناموفق",
  "delivered" to "تحویل شد", "rejected" to "رد شد", "withdrawn" to "پس گرفته شد", "returned" to "برگشت به دکان",
)

/** همان هفت نشانِ وضعیتِ سفارش، با نام‌های پیک (`OrderStatusTone`) */
private fun statusToneLegacy(s: String): PillTone = ir.vil3ntec.tohid.ui.components.common.OrderStatusTone.courier(s)

private fun actionLabelLegacy(from: String, to: String): String = when (to) {
  "assigned" -> "قبول"
  "rejected" -> "رد"
  "picked_up" -> "بسته را گرفتم"
  "moving" -> if (from == "failed") "تلاشِ دوباره" else "راه افتادم"
  "arrived" -> "رسیدم"
  "delivered" -> "تحویل شد"
  "failed" -> "ناموفق"
  else -> to
}

internal val REASON_LABELLegacy = mapOf(
  "busy" to "سرم شلوغ است", "too_far" to "خیلی دور است", "cash_limit" to "سقفِ نقدم پر است",
  "no_answer" to "تماس بی‌پاسخ / کسی نبود", "wrong_address" to "نشانی پیدا نشد", "refused" to "مشتری نپذیرفت",
  "recalled" to "دکان پس خواست", "partial_payment" to "مشتری کمتر داد", "refused_amount" to "مبلغ را نپذیرفت",
  "damaged_or_short" to "کالا آسیب یا کسری داشت", "other" to "دلیلِ دیگر (بنویسید)",
)

private fun afnLegacy(minor: Long): String = "${money(minor / 100.0)} افغانی"

/** ورودیِ پول به افغانی (رقمِ فارسی هم) ⇒ واحدِ کوچک */
private fun minorOfLegacy(raw: String): Long? {
  val t = buildString { for (c in raw) when (c) { in '0'..'9', '.' -> append(c); in '۰'..'۹' -> append('0' + (c - '۰')); '٫' -> append('.') } }
  val v = t.toDoubleOrNull() ?: return null
  if (v < 0 || v > 1_000_000_000) return null
  return Math.round(v * 100)
}

private sealed interface AskLegacy {
  data class Reason(val card: MissionCard, val type: String) : AskLegacy
  data class Deliver(val card: MissionCard) : AskLegacy
  data class Photo(val card: MissionCard) : AskLegacy
  /** DLV-07: اسکنِ همهٔ کیسه‌ها پیش از «بسته را گرفتم» */
  data class Bags(val card: MissionCard) : AskLegacy
  /** DLV-17: موقعیتِ همین لحظه پس از کادرِ تحویل و پیش از ثبت */
  data class Locate(val card: MissionCard, val code: String, val cash: Long?, val cashReason: String, val note: String) : AskLegacy
  /** DLV-18: امتیازِ پیک به تحویل */
  data class Rate(val card: MissionCard) : AskLegacy
}

@Composable
fun CourierHomeLegacy(onSignedOut: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val snackbar = remember { SnackbarHostState() }
  val colors = Shop.colors
  val store = remember(context) { CourierStore(context) }

  var queue by remember { mutableStateOf(store.queue()) }
  var active by remember { mutableStateOf(store.cards("active")) }
  var done by remember { mutableStateOf(store.cards("done")) }
  var name by remember { mutableStateOf(store.courierName) }
  var callEnabled by remember { mutableStateOf(store.callEnabled) }
  var routeEnabled by remember { mutableStateOf(store.routeEnabled) }
  var ratingsEnabled by remember { mutableStateOf(store.ratingsEnabled) }
  var rated by remember { mutableStateOf(store.rated()) }
  var routeOpen by remember { mutableStateOf(false) }
  var photosWaiting by remember { mutableStateOf(store.photos().size) }
  var syncing by remember { mutableStateOf(false) }
  var lastError by remember { mutableStateOf<String?>(null) }
  var tab by rememberSaveable { mutableStateOf("active") }
  var ask by remember { mutableStateOf<AskLegacy?>(null) }
  var askSignOut by remember { mutableStateOf(false) }
  val view = rememberViewMode("courier.jobs", ScreenKind.SALE)

  fun reloadLocal() {
    queue = store.queue(); active = store.cards("active"); done = store.cards("done")
    name = store.courierName; photosWaiting = store.photos().size
    callEnabled = store.callEnabled
    routeEnabled = store.routeEnabled; ratingsEnabled = store.ratingsEnabled; rated = store.rated()
  }

  /**
   *  «تماس با مشتری» (DLV-02): شمارهٔ کامل فقط همین لحظه از سرور، فقط صفحهٔ شماره‌گیر باز
   *  می‌شود (تماس را خودِ پیک می‌زند) و شماره جایی نگه داشته نمی‌شود. بی اینترنت ⇒ پیامِ روشن.
   */
  fun callCustomer(card: MissionCard) {
    scope.launch {
      Backend.courier(context).callNumber(card.id)
        .onSuccess { phone ->
          if (phone.isBlank()) { snackbar.showSnackbar("شمارهٔ مشتری نیامد"); return@onSuccess }
          runCatching {
            openDialer(context, phone)   //  مرزِ پلتفرم: CourierDialAndroid.kt
          }.onFailure { snackbar.showSnackbar("برنامهٔ تماس پیدا نشد") }
        }
        .onFailure { f ->
          snackbar.showSnackbar(
            when (f.code) {
              "offline", "unreachable", "timeout" -> "تماس فقط با اینترنت — شماره روی گوشی نگه داشته نمی‌شود"
              else -> f.userMessage
            },
          )
        }
    }
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
  fun record(
    card: MissionCard, type: String, reason: String = "", note: String = "", cash: Long? = null, cashReason: String = "", code: String = "",
    bags: List<String> = emptyList(), position: CourierChecks.Point? = null,
  ) {
    try {
      val (next, _) = CourierQueue.enqueue(
        store.queue(), "cev-" + ir.vil3ntec.tohid.sync.v1.Ulid.next(), card.id, type, System.currentTimeMillis(),
        //  CAS (§۲۸.۵): همان وضعیتی که پیک روی کارت دید (سرور + صف)
        from = card.status, reason = reason, note = note,
        cashCollectedMinor = cash, cashReason = cashReason, code = code, bags = bags, position = position,
      )
      store.saveQueue(next)
      queue = next
      scope.launch { sync() }
    } catch (e: CourierInputError) {
      scope.launch { snackbar.showSnackbar(e.message ?: "ثبت نشد") }
    }
  }

  /** DLV-18: امتیاز فقط آنلاین؛ ثبت‌شده یا مهلت‌گذشته ⇒ دکمه دیگر دیده نمی‌شود */
  fun rate(card: MissionCard, stars: Int, tags: List<String>) {
    scope.launch {
      Backend.courier(context).rate(card.id, stars, tags)
        .onSuccess { store.markRated(card.id); rated = store.rated(); snackbar.showSnackbar(StrCourier.rated) }
        .onFailure { f ->
          if (f.code == "already_rated" || f.code == "rating_closed") { store.markRated(card.id); rated = store.rated() }
          snackbar.showSnackbar(
            when (f.code) {
              "already_rated" -> StrCourier.rated
              "rating_closed" -> StrCourier.rateClosed
              "offline", "unreachable", "timeout" -> StrCourier.rateOnline
              else -> f.userMessage
            },
          )
        }
    }
  }

  //  باز شدن و هر نیم دقیقه تا صفحه باز است (بی سرویسِ پس‌زمینه — §۳.۱۱)
  LaunchedEffect(Unit) {
    while (true) {
      sync()
      delay(30_000)
    }
  }

  Scaffold(
    containerColor = colors.bg,
    snackbarHost = { TohidSnackbarLegacy(snackbar) },
  ) { pad ->
    Column(Modifier.fillMaxSize().padding(pad)) {
      //  ── سربرگ ──
      Row(
        Modifier.fillMaxWidth().background(colors.surfaceSolid).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(Icons.Filled.LocalShipping, contentDescription = null, tint = colors.primary)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
          Text("مأموریت‌های من", style = MaterialTheme.typography.titleMedium, color = colors.text, fontWeight = FontWeight.Bold)
          if (name.isNotBlank()) Text(name, style = MaterialTheme.typography.labelSmall, color = colors.muted)
        }
        //  DLV-04: «مسیرِ پیشنهادی» فقط با پرچمِ دکان (`/courier/me`)
        if (routeEnabled) {
          IconButton(onClick = { routeOpen = true }) {
            Icon(Icons.Filled.Place, contentDescription = StrCourier.routeTitle, tint = colors.primary)
          }
        }
        IconButton(onClick = { scope.launch { sync() } }, enabled = !syncing) {
          Icon(Icons.Filled.Refresh, contentDescription = "به‌روز کردن", tint = colors.muted)
        }
        IconButton(onClick = { askSignOut = true }) {
          Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "خروج از حسابِ پیک", tint = colors.muted)
        }
      }
      HorizontalDivider(color = colors.border)

      //  ── وضعیتِ صف: متن + رنگ + نقطه ──
      Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        val waiting = queue.pending.size + photosWaiting
        when {
          syncing -> StatusPill("در حالِ فرستادن…", PillTone.NEUTRAL)
          waiting > 0 -> StatusPill("$waiting کار در صفِ گوشی", PillTone.WARNING)
          lastError != null -> StatusPill("وصل نیست", PillTone.NEUTRAL)
          else -> StatusPill("همه به دکان رسید", PillTone.SUCCESS)
        }
        Spacer(Modifier.weight(1f))
        ViewModeToggle(view)
      }
      lastError?.let {
        Text(it, style = MaterialTheme.typography.labelSmall, color = colors.muted, modifier = Modifier.padding(horizontal = 16.dp))
      }

      //  ── کارهای ردشده: پنهان نمی‌شوند تا پیک خودش ببیند ──
      queue.rejected.forEach { r ->
        Row(
          Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .background(colors.warningTint, Shape.card).padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(Modifier.weight(1f)) {
            Text("«${actionLabelLegacy("", r.event.type)}» ثبت نشد", style = MaterialTheme.typography.labelLarge, color = colors.text)
            Text(r.message.ifBlank { r.code }, style = MaterialTheme.typography.labelSmall, color = colors.muted)
          }
          IconButton(onClick = {
            val next = CourierQueue.dismissRejected(store.queue(), r.event.eventId)
            store.saveQueue(next); queue = next
          }) { Icon(Icons.Filled.Close, contentDescription = "دیدم", tint = colors.muted) }
        }
      }

      val shownActive = active.map { MissionView.forDisplay(it, queue) }
      val live = shownActive.filter { CourierFlow.isLive(it.status) }
      //  پایانِ هنوز در صف از «فعال» به «پایان‌یافته» می‌رود — بی نشانی
      val finished = (shownActive.filterNot { CourierFlow.isLive(it.status) } + done.map { MissionView.forDisplay(it, queue) })
        .distinctBy { it.id }

      SegmentedTabs(
        tabs = listOf(SegmentTab("active", "فعال", live.size), SegmentTab("done", "پایان‌یافته", finished.size)),
        selected = tab,
        onSelect = { tab = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
      )

      val list = if (tab == "active") live else finished
      if (list.isEmpty()) {
        EmptyNote(if (tab == "active") "فعلاً مأموریتی ندارید. دکان که بسپارد، همین‌جا دیده می‌شود." else "هنوز مأموریتی تمام نشده است.")
      } else {
        LazyColumn(
          Modifier.fillMaxSize(),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(if (view.mode == ViewMode.CARD) 12.dp else 0.dp),
        ) {
          items(list, key = { it.id }) { c ->
            val waiting = CourierQueue.pendingFor(queue, c.id).isNotEmpty()
            if (view.mode == ViewMode.CARD) {
              MissionCardViewLegacy(c, waiting, onAction = { to ->
                when (to) {
                  "rejected", "failed" -> ask = AskLegacy.Reason(c, to)
                  "delivered" -> ask = AskLegacy.Deliver(c)
                  //  DLV-07: کارت شمارِ کیسه دارد ⇒ اول اسکنِ همه
                  "picked_up" -> if (c.bagScanBags != null) ask = AskLegacy.Bags(c) else record(c, to)
                  else -> record(c, to)
                }
              }, onPhoto = { ask = AskLegacy.Photo(c) },
                onCall = if (callEnabled && CourierFlow.canCall(c.status)) ({ callCustomer(c) }) else null,
                onRate = if (ratingsEnabled && DeliveryRating.courierCanRate(c.status, c.id, rated)) ({ ask = AskLegacy.Rate(c) }) else null)
            } else {
              MissionRowLegacy(c, waiting)
            }
          }
        }
      }
    }
  }

  //  ── کادرها ──
  when (val a = ask) {
    is AskLegacy.Reason -> ReasonDialogLegacy(a.type, onDismiss = { ask = null }) { reason, note ->
      ask = null; record(a.card, a.type, reason = reason, note = note)
    }
    is AskLegacy.Deliver -> DeliverDialogLegacy(a.card, onDismiss = { ask = null }) { code, cash, cashReason, note ->
      //  DLV-17: کارت فاصلهٔ مجاز دارد ⇒ اول موقعیتِ همین لحظه
      ask = if (a.card.nearPinMaxM != null) AskLegacy.Locate(a.card, code, cash, cashReason, note) else null
      if (ask == null) record(a.card, "delivered", code = code, cash = cash, cashReason = cashReason, note = note)
    }
    is AskLegacy.Locate -> NearPinFlow(a.card, onDismiss = { ask = null }) { here ->
      ask = null
      record(a.card, "delivered", code = a.code, cash = a.cash, cashReason = a.cashReason, note = a.note, position = here)
    }
    is AskLegacy.Bags -> BagScanDialog(a.card, onDismiss = { ask = null }) { labels ->
      ask = null; record(a.card, "picked_up", bags = labels)
    }
    is AskLegacy.Rate -> RateDialog(onDismiss = { ask = null }) { stars, tags ->
      ask = null; rate(a.card, stars, tags)
    }
    is AskLegacy.Photo -> PhotoFlowLegacy(a.card, onDismiss = { ask = null }) { bytes ->
      ask = null
      val eventId = "cph-" + ir.vil3ntec.tohid.sync.v1.Ulid.next()
      store.addPhoto(CourierStore.PendingPhoto(eventId, a.card.id, System.currentTimeMillis()), bytes)
      photosWaiting = store.photos().size
      scope.launch { snackbar.showSnackbar("عکسِ بسته در صف است"); sync() }
    }
    null -> {}
  }

  if (routeOpen) {
    RouteSheet(active.map { MissionView.forDisplay(it, queue) }.filter { CourierFlow.isLive(it.status) }) { routeOpen = false }
  }

  if (askSignOut) {
    val waiting = queue.pending.size + photosWaiting
    TohidConfirmDialog(
      title = "خروج از حسابِ پیک",
      message = if (waiting > 0)
        "$waiting کار هنوز به دکان نرسیده و با خروج از این گوشی پاک می‌شود. اول به اینترنت وصل شوید و «به‌روز کردن» را بزنید."
      else "مأموریت‌ها و نشانی‌ها از این گوشی پاک می‌شوند.",
      confirmText = if (waiting > 0) "با وجودِ این خارج شو" else "خروج",
      danger = waiting > 0,
      onDismiss = { askSignOut = false },
      onConfirm = {
        askSignOut = false
        scope.launch {
          runCatching { Backend.auth(context).signOut() }
          store.wipe()
          ir.vil3ntec.tohid.data.ShopRole.forget(context)
          onSignedOut()
        }
      },
    )
  }
}

/* ============================== کارت ============================== */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MissionCardViewLegacy(
  c: MissionCard,
  waiting: Boolean,
  onAction: (String) -> Unit,
  onPhoto: () -> Unit,
  onCall: (() -> Unit)? = null,
  onRate: (() -> Unit)? = null,
) {
  val colors = Shop.colors
  Column(
    Modifier.fillMaxWidth()
      .background(colors.surfaceSolid, Shape.card)
      .border(1.dp, colors.border, Shape.card)
      .padding(14.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("سفارش ${c.orderNo}", style = MaterialTheme.typography.labelLarge, color = colors.muted, modifier = Modifier.weight(1f))
      if (waiting) { StatusPill("در صف", PillTone.WARNING); Spacer(Modifier.width(6.dp)) }
      StatusPill(STATUS_LABELLegacy[c.status] ?: c.status, statusToneLegacy(c.status))
    }
    if (c.recallRequested && CourierFlow.isLive(c.status)) {
      Spacer(Modifier.height(8.dp))
      Text("دکان این سفارش را پس خواسته است — بسته را برگردانید.", style = MaterialTheme.typography.labelLarge, color = colors.danger)
    }
    //  DLV-09: دکان پس از ناموفق نشانی را درست کرد ⇒ پیش از تلاشِ دوباره دیده شود
    if (c.addressCorrectedAt != null && CourierFlow.isLive(c.status)) {
      Spacer(Modifier.height(8.dp))
      Text("دکان نشانی را اصلاح کرد — پیش از رفتن دوباره بخوانید.", style = MaterialTheme.typography.labelLarge, color = colors.primary)
    }
    //  DLV-02: «اول نشانه» — نشانه، «ناحیه · دروازهٔ رنگ»، بعد نشانیِ متنی (MissionView.landmarkFirst)
    MissionView.landmarkFirst(c).forEachIndexed { i, line ->
      if (i == 0) {
        Spacer(Modifier.height(8.dp))
        Text(line, style = MaterialTheme.typography.titleMedium, color = colors.text, fontWeight = FontWeight.Bold)
      } else {
        Text(line, style = MaterialTheme.typography.bodyMedium, color = colors.text)
      }
    }
    if (c.customerName.isNotBlank() || c.phoneMasked.isNotBlank()) {
      Spacer(Modifier.height(6.dp))
      Text(
        listOf(c.customerName, c.phoneMasked).filter { it.isNotBlank() }.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall, color = colors.muted,
      )
    }
    if (c.leaveWith.isNotBlank()) Text("سپردن به: ${c.leaveWith}", style = MaterialTheme.typography.bodySmall, color = colors.muted)
    //  CHK-14: پول خردِ لازم، از همان مبلغِ نقدِ این مأموریت
    c.cashTenderMinor?.let { Text(DeliveryPrefs.tenderLine(it, c.expectedCollectMinor), style = MaterialTheme.typography.bodyMedium, color = colors.text, fontWeight = FontWeight.SemiBold) }
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      if (c.expectedCollectMinor > 0) StatusPill("نقد: ${afnLegacy(c.expectedCollectMinor)}", PillTone.NEUTRAL)
      if (c.fareMinor > 0) StatusPill("کرایه: ${afnLegacy(c.fareMinor)}", PillTone.NEUTRAL)
      if (c.requiresCode) StatusPill("کدِ تحویل لازم", PillTone.NEUTRAL)
      if (c.photoAllowed) StatusPill("عکسِ بسته مجاز", PillTone.NEUTRAL)
      if (c.callBeforeArrival) StatusPill("مشتری خواسته پیش از رسیدن خبر شود", PillTone.NEUTRAL)
      c.cashCollectedMinor?.let { StatusPill("گرفته شد: ${afnLegacy(it)}", PillTone.SUCCESS) }
    }
    if (c.status == "failed") {
      Spacer(Modifier.height(8.dp))
      Text(
        "اگر دوباره نمی‌روید، بسته را به دکان برگردانید؛ برگشت را دکان هنگامِ تحویل گرفتن ثبت می‌کند.",
        style = MaterialTheme.typography.bodySmall, color = colors.muted,
      )
    }
    val actions = CourierFlow.actions(c.status)
    if (actions.isNotEmpty() || (c.photoAllowed && c.status == "arrived") || onCall != null || onRate != null) {
      Spacer(Modifier.height(10.dp))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEach { to ->
          if (to == "rejected" || to == "failed") {
            TextButton(onClick = { onAction(to) }) { Text(actionLabelLegacy(c.status, to), color = colors.danger) }
          } else {
            TohidButton(text = actionLabelLegacy(c.status, to), onClick = { onAction(to) })
          }
        }
        //  DLV-02: «تماس با مشتری» — شمارهٔ کامل فقط با زدنِ همین دکمه از سرور می‌آید
        if (onCall != null) {
          TextButton(onClick = onCall) {
            Icon(Icons.Filled.Call, contentDescription = null, tint = colors.primary)
            Spacer(Modifier.width(4.dp))
            Text("تماس با مشتری", color = colors.primary)
          }
        }
        //  DLV-18: امتیازِ پیک به تحویلِ انجام‌شده — فقط با پرچمِ دکان و یک بار
        if (onRate != null) TextButton(onClick = onRate) { Text(StrCourier.rate, color = colors.primary) }
        if (c.photoAllowed && c.status == "arrived") {
          TextButton(onClick = onPhoto) {
            Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = colors.primary)
            Spacer(Modifier.width(4.dp))
            Text("عکسِ بسته", color = colors.primary)
          }
        }
      }
    }
  }
}

/** نمای جدولیِ فشرده (صفحهٔ بزرگ) — همان داده، بی دکمه؛ کار از نمای کارت */
@Composable
private fun MissionRowLegacy(c: MissionCard, waiting: Boolean) {
  val colors = Shop.colors
  Row(
    Modifier.fillMaxWidth().padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Text("${c.orderNo}", style = MaterialTheme.typography.labelLarge, color = colors.muted, modifier = Modifier.width(56.dp))
    Text(c.landmark.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, color = colors.text, modifier = Modifier.weight(1f))
    Text(if (c.expectedCollectMinor > 0) afnLegacy(c.expectedCollectMinor) else "—", style = MaterialTheme.typography.bodySmall, color = colors.muted)
    if (waiting) StatusPill("در صف", PillTone.WARNING)
    StatusPill(STATUS_LABELLegacy[c.status] ?: c.status, statusToneLegacy(c.status))
  }
  HorizontalDivider(color = colors.border)
}

/* ============================== کادرها ============================== */

@Composable
private fun ReasonRowLegacy(label: String, selected: Boolean, onSelect: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect, role = Role.RadioButton).padding(vertical = 2.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(selected = selected, onClick = null)
    Spacer(Modifier.width(6.dp))
    Text(label, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
  }
}

@Composable
private fun ReasonDialogLegacy(type: String, onDismiss: () -> Unit, onDone: (String, String) -> Unit) {
  val colors = Shop.colors
  var reason by remember { mutableStateOf("") }
  var note by remember { mutableStateOf("") }
  val ready = reason.isNotBlank() && (reason != "other" || note.isNotBlank())
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = colors.surfaceSolid,
    title = { Text(if (type == "rejected") "ردِ این کار" else "تحویل نشد", color = colors.text) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        CourierFlow.reasonsFor(type).forEach { r -> ReasonRowLegacy(REASON_LABELLegacy[r] ?: r, reason == r) { reason = r } }
        Spacer(Modifier.height(8.dp))
        TohidTextField(note, { note = it.take(CourierQueue.MAX_NOTE) }, "توضیح" + if (reason == "other") " (لازم)" else " (اختیاری)", singleLine = false)
      }
    },
    confirmButton = { TextButton(enabled = ready, onClick = { onDone(reason, note.trim()) }) { Text("ثبت", color = colors.primary) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف", color = colors.muted) } },
  )
}

@Composable
private fun DeliverDialogLegacy(c: MissionCard, onDismiss: () -> Unit, onDone: (code: String, cash: Long?, cashReason: String, note: String) -> Unit) {
  val colors = Shop.colors
  val collects = c.expectedCollectMinor > 0
  var code by remember { mutableStateOf("") }
  var cash by remember { mutableStateOf(if (collects) money(c.expectedCollectMinor / 100.0).replace(",", "") else "") }
  var cashReason by remember { mutableStateOf("") }
  var note by remember { mutableStateOf("") }
  val minor = if (collects) minorOfLegacy(cash) else null
  val differs = collects && minor != null && minor != c.expectedCollectMinor
  val codeOk = !c.requiresCode || CourierQueue.latinDigits(code).length >= 4
  val cashOk = !collects || (minor != null && (!differs || (cashReason.isNotBlank() && (cashReason != "other" || note.isNotBlank()))))
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = colors.surfaceSolid,
    title = { Text("تحویل به مشتری", color = colors.text) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        if (c.requiresCode) {
          Text("کدی را که مشتری دارد بپرسید و بنویسید.", style = MaterialTheme.typography.bodySmall, color = colors.muted)
          Spacer(Modifier.height(6.dp))
          NumberField(code, { code = it.filter { ch -> ch != '.' }.take(12) }, "کدِ تحویل")
          Spacer(Modifier.height(10.dp))
        }
        if (collects) {
          Text("مبلغِ سفارش: ${afnLegacy(c.expectedCollectMinor)}", style = MaterialTheme.typography.bodySmall, color = colors.muted)
          Spacer(Modifier.height(6.dp))
          NumberField(cash, { cash = it }, "نقدِ گرفته‌شده (افغانی)")
          if (differs) {
            Spacer(Modifier.height(8.dp))
            Text("مبلغ با سفارش فرق دارد — دلیل:", style = MaterialTheme.typography.labelMedium, color = colors.text)
            CourierFlow.CASH_REASONS.forEach { r -> ReasonRowLegacy(REASON_LABELLegacy[r] ?: r, cashReason == r) { cashReason = r } }
            TohidTextField(note, { note = it.take(CourierQueue.MAX_NOTE) }, "توضیح" + if (cashReason == "other") " (لازم)" else " (اختیاری)", singleLine = false)
          }
        }
        if (!c.requiresCode && !collects) {
          Text("بسته به مشتری رسید؟", style = MaterialTheme.typography.bodyMedium, color = colors.text)
        }
      }
    },
    confirmButton = {
      TextButton(enabled = codeOk && cashOk, onClick = {
        onDone(CourierQueue.latinDigits(code), minor, if (differs) cashReason else "", if (differs) note.trim() else "")
      }) { Text("تحویل شد", color = colors.primary) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف", color = colors.muted) } },
  )
}

/**
 *  عکسِ بسته (DLV-08): فقط با رضایتِ مشتری (`photoAllowed` از سرور) و فقط از
 *  بسته، نه از شخص — پیک پیش از دوربین تأیید می‌کند.
 */
@Composable
private fun PhotoFlowLegacy(c: MissionCard, onDismiss: () -> Unit, onReady: (ByteArray) -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val colors = Shop.colors
  var parcelOnly by remember { mutableStateOf(false) }
  var pick by remember { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  if (!pick) {
    AlertDialog(
      onDismissRequest = onDismiss,
      containerColor = colors.surfaceSolid,
      title = { Text("عکسِ بسته", color = colors.text) },
      text = {
        Column {
          Text(
            "مشتریِ سفارشِ ${c.orderNo} به عکسِ تحویل رضایت داده است. عکس فقط از بسته گرفته شود؛ چهرهٔ کسی در عکس نباشد.",
            style = MaterialTheme.typography.bodySmall, color = colors.muted,
          )
          Spacer(Modifier.height(10.dp))
          Row(
            Modifier.fillMaxWidth().clickable { parcelOnly = !parcelOnly },
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Checkbox(checked = parcelOnly, onCheckedChange = { parcelOnly = it })
            Text("عکس فقط از بسته است", style = MaterialTheme.typography.bodyMedium, color = colors.text)
          }
          error?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = colors.danger) }
        }
      },
      confirmButton = { TextButton(enabled = parcelOnly && !busy, onClick = { pick = true }) { Text("گرفتنِ عکس", color = colors.primary) } },
      dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف", color = colors.muted) } },
    )
  }
  PhotoSourceSheetLegacy(
    open = pick,
    onDismiss = { pick = false },
    note = "فقط بسته؛ عکس پس از رسیدن به دکان از این گوشی پاک می‌شود",
  ) { uri ->
    busy = true
    scope.launch {
      val bytes = withContext(Dispatchers.IO) { runCatching { parcelJpegLegacy(context, uri) }.getOrNull() }
      busy = false
      if (bytes == null) error = "عکس خوانده نشد؛ دوباره بگیرید" else onReady(bytes)
    }
  }
}
