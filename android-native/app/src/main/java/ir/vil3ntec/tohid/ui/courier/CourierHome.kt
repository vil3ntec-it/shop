package ir.vil3ntec.tohid.ui.courier

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.core.courier.CourierFlow
import ir.vil3ntec.tohid.core.courier.CourierInputError
import ir.vil3ntec.tohid.core.courier.CourierQueue
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
import ir.vil3ntec.tohid.ui.components.common.ViewModeToggle
import ir.vil3ntec.tohid.ui.components.common.rememberViewMode
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.PhotoSourceSheet
import ir.vil3ntec.tohid.ui.screens.TohidSnackbar
import ir.vil3ntec.tohid.ui.screens.openOnMap
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Colors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

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
 *
 *  ظاهر: گوشیِ نسخهٔ ۳ (design-ref/phone-v3/SPEC.md) — زمینهٔ روشن، کارتِ سفید
 *  با سایهٔ نرم، آیکنِ خطیِ تک‌رنگ، یک دکمهٔ اصلیِ بزرگ برای هر پله (کارِ
 *  یک‌دستی)، و برگهٔ پایینی به‌جای کادرِ وسطِ صفحه. `CourierHome` حالت و صف را
 *  نگه می‌دارد؛ `CourierBoard` فقط می‌کشد (پیش‌نمایشِ گوشی هم همان را با دادهٔ
 *  نمایشی صدا می‌زند).
 */

private val STATUS_LABEL = mapOf(
  "requested" to "تازه", "assigned" to "قبول شد", "picked_up" to "بسته گرفته شد",
  "moving" to "در راه", "arrived" to "رسیده", "failed" to "ناموفق",
  "delivered" to "تحویل شد", "rejected" to "رد شد", "withdrawn" to "پس گرفته شد", "returned" to "برگشت به دکان",
)

/** جملهٔ بزرگِ هر پله در صفحهٔ جزئیات */
private fun headline(s: String): String = when (s) {
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

private fun statusDot(s: String, p: PhoneV3Colors) = when (s) {
  "requested" -> p.waiting
  "failed" -> p.negative
  "delivered" -> p.positive
  "rejected", "withdrawn", "returned" -> p.inkMuted
  else -> p.ink
}

private fun actionLabel(from: String, to: String): String = when (to) {
  "assigned" -> "قبول"
  "rejected" -> "رد"
  "picked_up" -> "بسته را گرفتم"
  "moving" -> if (from == "failed") "تلاشِ دوباره" else "راه افتادم"
  "arrived" -> "رسیدم"
  "delivered" -> "تحویل شد"
  "failed" -> "ناموفق"
  else -> to
}

private fun isNegative(to: String) = to == "rejected" || to == "failed"

internal val REASON_LABEL = mapOf(
  "busy" to "سرم شلوغ است", "too_far" to "خیلی دور است", "cash_limit" to "سقفِ نقدم پر است",
  "no_answer" to "تماس بی‌پاسخ / کسی نبود", "wrong_address" to "نشانی پیدا نشد", "refused" to "مشتری نپذیرفت",
  "recalled" to "دکان پس خواست", "partial_payment" to "مشتری کمتر داد", "refused_amount" to "مبلغ را نپذیرفت",
  "damaged_or_short" to "کالا آسیب یا کسری داشت", "other" to "دلیلِ دیگر (بنویسید)",
)

/** پله‌های نوارِ پیشرفت: گرفتم ⇐ در راه ⇐ رسیدم ⇐ تحویل */
private val STEPS = listOf("گرفتم", "در راه", "رسیدم", "تحویل")

/** چند پله رسیده — منفی یعنی نوار نشان داده نمی‌شود */
private fun reached(status: String): Int = when (status) {
  "assigned" -> 0
  "picked_up" -> 1
  "moving" -> 2
  "arrived" -> 3
  "delivered" -> 4
  else -> -1
}

private fun afn(minor: Long): String = "${money(minor / 100.0)} افغانی"

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
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val snackbar = remember { SnackbarHostState() }
  val store = remember(context) { CourierStore(context) }

  var queue by remember { mutableStateOf(store.queue()) }
  var active by remember { mutableStateOf(store.cards("active")) }
  var done by remember { mutableStateOf(store.cards("done")) }
  var name by remember { mutableStateOf(store.courierName) }
  var photosWaiting by remember { mutableStateOf(store.photos().size) }
  var syncing by remember { mutableStateOf(false) }
  var lastError by remember { mutableStateOf<String?>(null) }

  fun reloadLocal() {
    queue = store.queue(); active = store.cards("active"); done = store.cards("done")
    name = store.courierName; photosWaiting = store.photos().size
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
 *  صفحهٔ پیک، بی حالتِ ماندگار: فهرست ⇐ جزئیات ⇐ برگه‌ها. هر کار با
 *  `onRecord` به صفِ گوشی می‌رود. `initial*` فقط برای پیش‌نمایشِ گوشی است.
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
  onRefresh: () -> Unit,
  onDismissRejected: (String) -> Unit,
  onRecord: (CourierAction) -> Unit,
  onPhoto: (MissionCard, ByteArray) -> Unit,
  onSignOut: () -> Unit,
  initialTab: String = "active",
  initialOpen: String? = null,
  initialDeliver: Boolean = false,
) {
  val p = PhoneV3.colors
  var tab by rememberSaveable { mutableStateOf(initialTab) }
  var openId by rememberSaveable { mutableStateOf(initialOpen) }
  var ask by remember { mutableStateOf<Ask?>(null) }
  var askSignOut by remember { mutableStateOf(false) }
  val view = rememberViewMode("courier.jobs", ScreenKind.SALE)
  val seen = remember { mutableSetOf<String>() }

  val shownActive = active.map { MissionView.forDisplay(it, queue) }
  val live = shownActive.filter { CourierFlow.isLive(it.status) }
  //  پایانِ هنوز در صف از «فعال» به «پایان‌یافته» می‌رود — بی نشانی
  val finished = (shownActive.filterNot { CourierFlow.isLive(it.status) } + done.map { MissionView.forDisplay(it, queue) })
    .distinctBy { it.id }
  val waitingTotal = queue.pending.size + photosWaiting

  fun act(c: MissionCard, to: String) {
    when (to) {
      "rejected", "failed" -> ask = Ask.Reason(c, to)
      "delivered" -> ask = Ask.Deliver(c)
      else -> onRecord(CourierAction(c, to))
    }
  }

  fun find(id: String?): MissionCard? = id?.let { k -> live.firstOrNull { it.id == k } ?: finished.firstOrNull { it.id == k } }

  LaunchedEffect(initialDeliver) {
    if (initialDeliver) find(initialOpen)?.let { ask = Ask.Deliver(it) }
  }

  androidx.activity.compose.BackHandler(enabled = openId != null) { openId = null }

  Box(Modifier.fillMaxSize().background(p.bg)) {
    Scaffold(
      containerColor = p.bg,
      snackbarHost = { TohidSnackbar(snackbar) },
    ) { pad ->
      //  فهرست ⇄ جزئیات: ورود از سمتِ پایان + محو شدن (بخشِ G)، برگشت برعکس
      AnimatedContent(
        targetState = find(openId)?.id,
        transitionSpec = {
          val ms = motionMs(240)
          if (!Motion.enabled) {
            EnterTransition.None togetherWith ExitTransition.None
          } else if (targetState != null) {
            (slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(ms)) { it / 4 } + fadeIn(tween(ms))) togetherWith
              fadeOut(tween(ms / 2))
          } else {
            fadeIn(tween(ms)) togetherWith
              (slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(ms)) { it / 4 } + fadeOut(tween(ms)))
          }
        },
        label = "courierPage",
      ) { id ->
        val opened = find(id)
        if (opened == null) {
          val list = if (tab == "active") live else finished
          LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            item(key = "head") {
              BoardHeader(name, syncing, onRefresh = onRefresh, onSignOut = { askSignOut = true }) { ViewModeToggle(view) }
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
              TwoTabs(
                first = "فعال" to live.size,
                second = "پایان‌یافته" to finished.size,
                firstSelected = tab == "active",
                onSelect = { tab = if (it) "active" else "done" },
              )
            }
            if (list.isEmpty()) {
              item(key = "empty-$tab") {
                if (tab == "active") {
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
                val appear = rememberAppear(c.id, index, seen)
                val waiting = CourierQueue.pendingFor(queue, c.id).isNotEmpty()
                val itemMod = Modifier.animateItem().graphicsLayer {
                  alpha = appear.value
                  translationY = (1f - appear.value) * 24.dp.toPx()
                }
                if (view.mode == ViewMode.CARD) {
                  MissionCardView(c, waiting, itemMod, onOpen = { openId = c.id }, onAction = { to -> act(c, to) })
                } else {
                  MissionRow(c, waiting, itemMod, onOpen = { openId = c.id })
                }
              }
            }
          }
        } else {
          DetailPage(
            c = opened,
            waiting = CourierQueue.pendingFor(queue, opened.id).isNotEmpty(),
            pad = pad,
            onBack = { openId = null },
            onAction = { to -> act(opened, to) },
            onPhoto = { ask = Ask.Photo(opened) },
          )
        }
      }
    }

    //  ── برگه‌ها ──
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

/* ============================== فهرست ============================== */

@Composable
private fun BoardHeader(
  name: String,
  syncing: Boolean,
  onRefresh: () -> Unit,
  onSignOut: () -> Unit,
  extra: @Composable () -> Unit,
) {
  val p = PhoneV3.colors
  Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
      Text("مأموریت‌ها", style = v3Style(30.sp, FontWeight.Bold), color = p.ink, maxLines = 1)
      if (name.isNotBlank()) {
        Text("پیک: $name", style = v3Style(14.sp), color = p.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
    extra()
    Spacer(Modifier.width(8.dp))
    RoundIconButton(Icons.Outlined.Refresh, "به‌روز کردن", onRefresh, enabled = !syncing, spin = syncing)
    Spacer(Modifier.width(10.dp))
    RoundIconButton(Icons.AutoMirrored.Outlined.Logout, "خروج از حسابِ پیک", onSignOut)
  }
}

/** وضعیتِ صف: متن + رنگ + نقطه، و علتِ وصل‌نبودن زیرش */
@Composable
private fun SyncLine(syncing: Boolean, waiting: Int, lastError: String?) {
  val p = PhoneV3.colors
  Column {
    val (text, dot) = when {
      syncing -> "در حالِ فرستادن…" to p.inkMuted
      waiting > 0 -> "${waiting.fa()} کار در صفِ گوشی" to p.waiting
      lastError != null -> "وصل نیست" to p.inkMuted
      else -> "همه به دکان رسید" to p.positive
    }
    DotChip(text, dot, fill = p.card)
    if (lastError != null) {
      Spacer(Modifier.height(6.dp))
      Text(lastError, style = v3Style(12.sp), color = p.inkMuted, modifier = Modifier.padding(start = 4.dp))
    }
  }
}

/** کارِ ردشده: پنهان نمی‌شود تا پیک خودش ببیند */
@Composable
private fun RejectedNote(title: String, message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
  val p = PhoneV3.colors
  Row(
    modifier.fillMaxWidth().v3Card(p, GroupShape, elevation = 6.dp).padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(8.dp).background(p.negative, CircleShape))
    Spacer(Modifier.width(10.dp))
    Column(Modifier.weight(1f)) {
      Text(title, style = v3Style(15.sp, FontWeight.Bold), color = p.ink)
      Text(message, style = v3Style(13.sp), color = p.inkMuted)
    }
    Box(
      Modifier.size(40.dp).clip(CircleShape).background(p.bg).clickable(onClick = onDismiss),
      contentAlignment = Alignment.Center,
    ) { Icon(Icons.Outlined.Close, contentDescription = "دیدم", tint = p.icon, modifier = Modifier.size(18.dp)) }
  }
}

@Composable
private fun EmptyCard(title: String, sub: String, onRefresh: (() -> Unit)?) {
  val p = PhoneV3.colors
  Column(
    Modifier.fillMaxWidth().padding(top = 8.dp).v3Card(p).padding(horizontal = 24.dp, vertical = 32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(Modifier.size(76.dp).background(p.bg, CircleShape), contentAlignment = Alignment.Center) {
      Icon(Icons.Outlined.LocalShipping, contentDescription = null, tint = p.icon, modifier = Modifier.size(34.dp))
    }
    Spacer(Modifier.height(16.dp))
    Text(title, style = v3Style(18.sp, FontWeight.Bold), color = p.ink, textAlign = TextAlign.Center)
    Spacer(Modifier.height(6.dp))
    Text(sub, style = v3Style(14.sp), color = p.inkMuted, textAlign = TextAlign.Center)
    if (onRefresh != null) {
      Spacer(Modifier.height(18.dp))
      V3SecondaryButton("به‌روز کردن", onRefresh, icon = Icons.Outlined.Refresh)
    }
  }
}

/** نوارِ پله‌ها: هر بار که وضعیت جلو می‌رود، پلهٔ تازه با تأخیرِ پلکانی پر می‌شود (بخشِ G) */
@Composable
private fun StepTrack(status: String) {
  val p = PhoneV3.colors
  val done = reached(status)
  Column(Modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      STEPS.forEachIndexed { i, _ ->
        val fill by animateFloatAsState(
          targetValue = if (i < done) 1f else 0f,
          animationSpec = if (Motion.enabled) tween(420, delayMillis = i * 70) else snap(),
          label = "step$i",
        )
        Box(Modifier.weight(1f).height(6.dp).background(p.hairline, PillShape)) {
          Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(p.ink, PillShape))
        }
      }
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      STEPS.forEachIndexed { i, label ->
        Text(
          label,
          style = v3Style(12.sp, if (i < done) FontWeight.Bold else FontWeight.Normal),
          color = if (i < done) p.ink else p.inkMuted,
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
    modifier.fillMaxWidth().v3Card(p).clip(CardShape).clickable(onClick = onOpen).padding(18.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("سفارش ${c.orderNo.fa()}", style = v3Style(13.sp, FontWeight.Medium), color = p.inkMuted, modifier = Modifier.weight(1f))
      if (waiting) { DotChip("در صف", p.waiting); Spacer(Modifier.width(6.dp)) }
      DotChip(STATUS_LABEL[c.status] ?: c.status, statusDot(c.status, p))
    }
    if (c.recallRequested && live) {
      Spacer(Modifier.height(10.dp))
      WarnNote("دکان این سفارش را پس خواسته است — بسته را برگردانید.")
    }
    //  DLV-02: «اول نشانه»
    if (c.landmark.isNotBlank()) {
      Spacer(Modifier.height(10.dp))
      Text(c.landmark, style = v3Style(19.sp, FontWeight.Bold), color = p.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    if (c.addressText.isNotBlank()) {
      Spacer(Modifier.height(2.dp))
      Text(c.addressText, style = v3Style(14.sp), color = p.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    val chips = live && (c.expectedCollectMinor > 0 || c.requiresCode) || !live && (c.cashCollectedMinor != null || c.fareMinor > 0)
    if (chips) {
      Spacer(Modifier.height(12.dp))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (live) {
          if (c.expectedCollectMinor > 0) InfoChip(Icons.Outlined.Payments, "نقد: ${afn(c.expectedCollectMinor)}")
          if (c.requiresCode) InfoChip(Icons.Outlined.Lock, "کدِ تحویل")
        } else {
          c.cashCollectedMinor?.let { InfoChip(Icons.Outlined.CheckCircle, "گرفته شد: ${afn(it)}", positive = true) }
          if (c.fareMinor > 0) InfoChip(Icons.Outlined.Payments, "کرایه: ${afn(c.fareMinor)}")
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
          primary?.let { V3PrimaryButton(actionLabel(c.status, it), { onAction(it) }, Modifier.weight(1f)) }
          actions.filter { isNegative(it) }.forEach {
            V3SecondaryButton(actionLabel(c.status, it), { onAction(it) }, Modifier.weight(1f).height(56.dp), tone = ButtonTone.NEGATIVE)
          }
        }
      } else if (primary != null) {
        //  یک دکمهٔ بزرگ برای پلهٔ بعد؛ «ناموفق» و بقیه در صفحهٔ جزئیات
        V3PrimaryButton(actionLabel(c.status, primary), { onAction(primary) }, Modifier.fillMaxWidth())
      }
    }
  }
}

/** نمای جدولیِ فشرده (صفحهٔ بزرگ) — همان داده؛ زدنِ ردیف جزئیات را باز می‌کند */
@Composable
private fun MissionRow(c: MissionCard, waiting: Boolean, modifier: Modifier, onOpen: () -> Unit) {
  val p = PhoneV3.colors
  Row(
    modifier.fillMaxWidth().v3Card(p, GroupShape, elevation = 4.dp).clip(GroupShape).clickable(onClick = onOpen)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Text(c.orderNo.fa(), style = v3Style(13.sp, FontWeight.Medium), color = p.inkMuted, modifier = Modifier.width(56.dp))
    Text(c.landmark.ifBlank { "—" }, style = v3Style(15.sp), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    Text(if (c.expectedCollectMinor > 0) afn(c.expectedCollectMinor) else "—", style = v3Style(13.sp), color = p.inkMuted)
    if (waiting) DotChip("در صف", p.waiting)
    DotChip(STATUS_LABEL[c.status] ?: c.status, statusDot(c.status, p))
  }
}

/* ============================== جزئیات ============================== */

private val BarShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

@Composable
private fun DetailPage(
  c: MissionCard,
  waiting: Boolean,
  pad: PaddingValues,
  onBack: () -> Unit,
  onAction: (String) -> Unit,
  onPhoto: () -> Unit,
) {
  val p = PhoneV3.colors
  val context = LocalContext.current
  val live = CourierFlow.isLive(c.status)
  Column(Modifier.fillMaxSize().padding(pad)) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
      RoundIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "بازگشت به فهرست", onBack)
      Text(
        "سفارش ${c.orderNo.fa()}",
        style = v3Style(18.sp, FontWeight.Bold), color = p.ink, textAlign = TextAlign.Center,
        modifier = Modifier.weight(1f),
      )
      Spacer(Modifier.width(44.dp))
    }
    Column(
      Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Spacer(Modifier.height(2.dp))
      if (live) StatusHero(c, waiting) else FinishedHero(c)
      if (c.recallRequested && live) WarnNote("دکان این سفارش را پس خواسته است — بسته را برگردانید.")
      if (live && (c.landmark.isNotBlank() || c.addressText.isNotBlank() || c.lat != null)) {
        DestinationCard(c) {
          val lat = c.lat; val lng = c.lng
          if (lat != null && lng != null) openOnMap(context, lat, lng, c.landmark.ifBlank { "مقصد" })
        }
      }
      if (live && (c.customerName.isNotBlank() || c.phoneMasked.isNotBlank() || c.leaveWith.isNotBlank() || c.callBeforeArrival)) {
        CustomerCard(c)
      }
      MoneyCard(c)
      if (c.status == "failed") {
        Text(
          "اگر دوباره نمی‌روید، بسته را به دکان برگردانید؛ برگشت را دکان هنگامِ تحویل گرفتن ثبت می‌کند.",
          style = v3Style(14.sp), color = p.inkMuted, modifier = Modifier.padding(horizontal = 4.dp),
        )
      }
      Spacer(Modifier.height(12.dp))
    }
    val actions = if (live) CourierFlow.actions(c.status) else emptyList()
    if (actions.isNotEmpty()) ActionBar(c, actions, onAction, onPhoto)
  }
}

/** پلهٔ فعلی: جملهٔ بزرگ با جابه‌جاییِ نرم وقتی پله عوض می‌شود + نوارِ پله‌ها */
@Composable
private fun StatusHero(c: MissionCard, waiting: Boolean) {
  val p = PhoneV3.colors
  Column(Modifier.fillMaxWidth().v3Card(p).padding(18.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("وضعیت", style = v3Style(13.sp, FontWeight.Medium), color = p.inkMuted, modifier = Modifier.weight(1f))
      if (waiting) { DotChip("در صف", p.waiting); Spacer(Modifier.width(6.dp)) }
      DotChip(STATUS_LABEL[c.status] ?: c.status, statusDot(c.status, p))
    }
    Spacer(Modifier.height(10.dp))
    AnimatedContent(
      targetState = c.status,
      transitionSpec = {
        if (Motion.enabled) {
          (slideInVertically(tween(280)) { it / 2 } + fadeIn(tween(280))) togetherWith
            (slideOutVertically(tween(200)) { -it / 2 } + fadeOut(tween(200)))
        } else EnterTransition.None togetherWith ExitTransition.None
      },
      label = "headline",
    ) { s ->
      Text(headline(s), style = v3Style(24.sp, FontWeight.Bold), color = p.ink)
    }
    if (reached(c.status) >= 0) {
      Spacer(Modifier.height(14.dp))
      StepTrack(c.status)
    }
  }
}

/** پایانِ کار: نشانِ بزرگ با پریدنِ کوچک؛ نشانی و مشتری دیگر نیست */
@Composable
private fun FinishedHero(c: MissionCard) {
  val p = PhoneV3.colors
  val good = c.status == "delivered"
  val pop = remember { Animatable(if (Motion.enabled) 0.6f else 1f) }
  LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 420f)) }
  Column(
    Modifier.fillMaxWidth().v3Card(p).padding(horizontal = 20.dp, vertical = 28.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    val tint = if (good) p.positive else p.inkMuted
    Box(
      Modifier.size(84.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }.background(tint.copy(alpha = 0.12f), CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      Icon(if (good) Icons.Outlined.CheckCircle else Icons.Outlined.Info, contentDescription = null, tint = if (good) p.positive else p.icon, modifier = Modifier.size(44.dp))
    }
    Spacer(Modifier.height(14.dp))
    Text(headline(c.status), style = v3Style(24.sp, FontWeight.Bold), color = p.ink, textAlign = TextAlign.Center)
    Spacer(Modifier.height(6.dp))
    Text(
      "نشانی و شمارهٔ مشتری پس از پایانِ کار دیگر نشان داده نمی‌شود.",
      style = v3Style(14.sp), color = p.inkMuted, textAlign = TextAlign.Center,
    )
  }
}

@Composable
private fun SectionLabel(text: String) {
  Text(text, style = v3Style(13.sp, FontWeight.Medium), color = PhoneV3.colors.inkMuted)
}

@Composable
private fun DestinationCard(c: MissionCard, onNavigate: () -> Unit) {
  val p = PhoneV3.colors
  Column(Modifier.fillMaxWidth().v3Card(p).padding(18.dp)) {
    SectionLabel("مقصد")
    if (c.landmark.isNotBlank()) {
      Spacer(Modifier.height(6.dp))
      Text(c.landmark, style = v3Style(20.sp, FontWeight.Bold), color = p.ink)
    }
    if (c.addressText.isNotBlank()) {
      Spacer(Modifier.height(4.dp))
      Text(c.addressText, style = v3Style(15.sp), color = p.inkMuted)
    }
    if (c.lat != null && c.lng != null) {
      Spacer(Modifier.height(14.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Place, contentDescription = null, tint = p.icon, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("پینِ مقصد روی نقشه", style = v3Style(14.sp), color = p.ink, modifier = Modifier.weight(1f))
        V3SecondaryButton("مسیریابی", onNavigate, icon = Icons.Outlined.Directions)
      }
    }
  }
}

@Composable
private fun InfoRow(icon: ImageVector, text: String, ltr: Boolean = false, divider: Boolean = true) {
  val p = PhoneV3.colors
  Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, contentDescription = null, tint = p.icon, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(12.dp))
    Text(
      text,
      style = v3Style(15.sp).copy(textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content),
      color = p.ink,
      modifier = Modifier.weight(1f),
    )
  }
  if (divider) Box(Modifier.fillMaxWidth().padding(start = 32.dp).height(1.dp).background(p.hairline))
}

@Composable
private fun CustomerCard(c: MissionCard) {
  val p = PhoneV3.colors
  val rows = buildList<Triple<ImageVector, String, Boolean>> {
    if (c.customerName.isNotBlank()) add(Triple(Icons.Outlined.Person, c.customerName, false))
    //  شماره پوشیده است (§۳.۱۱)؛ دکمهٔ «تماس» تا راهِ تماسِ امن نیامده، نیست
    if (c.phoneMasked.isNotBlank()) add(Triple(Icons.Outlined.Phone, c.phoneMasked, true))
    if (c.leaveWith.isNotBlank()) add(Triple(Icons.Outlined.Home, "سپردن به: ${c.leaveWith}", false))
    if (c.callBeforeArrival) add(Triple(Icons.Outlined.NotificationsActive, "مشتری خواسته پیش از رسیدن خبر شود", false))
  }
  Column(Modifier.fillMaxWidth().v3Card(p).padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 4.dp)) {
    SectionLabel("مشتری")
    rows.forEachIndexed { i, (icon, text, ltr) -> InfoRow(icon, text, ltr, divider = i < rows.lastIndex) }
  }
}

@Composable
private fun KeyValue(label: String, value: String, strong: Boolean = false, positive: Boolean = false, divider: Boolean = true) {
  val p = PhoneV3.colors
  Row(Modifier.fillMaxWidth().heightIn(min = 50.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(label, style = v3Style(14.sp), color = p.inkMuted, modifier = Modifier.weight(1f))
    Text(
      value,
      style = v3Style(if (strong) 18.sp else 15.sp, if (strong) FontWeight.Bold else FontWeight.Medium),
      color = if (positive) p.positive else p.ink,
    )
  }
  if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(p.hairline))
}

private data class KV(val label: String, val value: String, val strong: Boolean = false, val positive: Boolean = false)

@Composable
private fun MoneyCard(c: MissionCard) {
  val p = PhoneV3.colors
  val rows = buildList {
    if (c.expectedCollectMinor > 0) add(KV("نقدِ گرفتنی", afn(c.expectedCollectMinor), strong = true))
    else add(KV("نقد", "از مشتری گرفته نمی‌شود"))
    c.cashCollectedMinor?.let { add(KV("گرفته شد", afn(it), positive = true)) }
    if (c.fareMinor > 0) add(KV("کرایهٔ شما", afn(c.fareMinor)))
    if (c.itemsCount > 0) add(KV("تعدادِ قلم", c.itemsCount.fa()))
    if (c.requiresCode) add(KV("کدِ تحویل", "از مشتری بپرسید"))
    if (c.photoAllowed) add(KV("عکسِ بسته", "مشتری رضایت داده"))
  }
  Column(Modifier.fillMaxWidth().v3Card(p).padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 4.dp)) {
    SectionLabel("پول و تحویل")
    rows.forEachIndexed { i, r -> KeyValue(r.label, r.value, r.strong, r.positive, divider = i < rows.lastIndex) }
  }
}

/** دکمه‌های پایینِ صفحه — زیرِ شست: یک دکمهٔ بزرگ برای پلهٔ بعد، بقیه کوچک‌تر */
@Composable
private fun ActionBar(c: MissionCard, actions: List<String>, onAction: (String) -> Unit, onPhoto: () -> Unit) {
  val p = PhoneV3.colors
  val positive = actions.filterNot { isNegative(it) }
  val negative = actions.filter { isNegative(it) }
  val photo = c.photoAllowed && c.status == "arrived"
  Column(
    Modifier.fillMaxWidth().v3Card(p, BarShape, elevation = 18.dp).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    positive.firstOrNull()?.let { to ->
      V3PrimaryButton(actionLabel(c.status, to), { onAction(to) }, Modifier.fillMaxWidth())
    }
    val rest = positive.drop(1)
    if (rest.isNotEmpty() || photo || negative.isNotEmpty()) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        rest.forEach { to -> V3SecondaryButton(actionLabel(c.status, to), { onAction(to) }, Modifier.weight(1f)) }
        if (photo) V3SecondaryButton("عکسِ بسته", onPhoto, Modifier.weight(1f), icon = Icons.Outlined.PhotoCamera)
        negative.forEach { to ->
          V3SecondaryButton(actionLabel(c.status, to), { onAction(to) }, Modifier.weight(1f), tone = ButtonTone.NEGATIVE)
        }
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
  V3Sheet(onDismiss) {
    SheetTitle(if (type == "rejected") "ردِ این کار" else "تحویل نشد", "دلیل را انتخاب کنید")
    Spacer(Modifier.height(14.dp))
    ChoiceGroup(CourierFlow.reasonsFor(type).map { it to (REASON_LABEL[it] ?: it) }, reason) { reason = it }
    Spacer(Modifier.height(14.dp))
    V3Field(
      note, { note = it.take(CourierQueue.MAX_NOTE) },
      "توضیح" + if (reason == "other") " (لازم)" else " (اختیاری)",
      singleLine = false,
    )
    Spacer(Modifier.height(18.dp))
    V3PrimaryButton(
      "ثبت", { onDone(reason, note.trim()) }, Modifier.fillMaxWidth(),
      enabled = ready, tone = if (type == "failed") ButtonTone.NEGATIVE else ButtonTone.INK,
    )
    Spacer(Modifier.height(8.dp))
    V3SecondaryButton("انصراف", onDismiss, Modifier.fillMaxWidth())
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
  V3Sheet(onDismiss) {
    SheetTitle("تحویل به مشتری", "سفارش ${c.orderNo.fa()}")
    if (collects) {
      Spacer(Modifier.height(16.dp))
      Column(Modifier.fillMaxWidth().background(p.bg, GroupShape).padding(vertical = 16.dp)) {
        BigAmount("نقدِ این سفارش", afn(c.expectedCollectMinor))
      }
    }
    if (c.requiresCode) {
      Spacer(Modifier.height(16.dp))
      V3Field(
        code, { code = numberInput(it).filter { ch -> ch != '.' }.take(12) },
        "کدِ تحویل — از مشتری بپرسید", placeholder = "••••", ltr = true,
        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number, big = true,
      )
    }
    if (collects) {
      Spacer(Modifier.height(14.dp))
      V3Field(
        cash, { cash = numberInput(it) }, "نقدِ گرفته‌شده (افغانی)", ltr = true,
        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal, big = true,
      )
      if (differs) {
        Spacer(Modifier.height(14.dp))
        Text("مبلغ با سفارش فرق دارد — دلیل:", style = v3Style(14.sp, FontWeight.Bold), color = p.ink)
        Spacer(Modifier.height(8.dp))
        ChoiceGroup(CourierFlow.CASH_REASONS.map { it to (REASON_LABEL[it] ?: it) }, cashReason) { cashReason = it }
        Spacer(Modifier.height(12.dp))
        V3Field(
          note, { note = it.take(CourierQueue.MAX_NOTE) },
          "توضیح" + if (cashReason == "other") " (لازم)" else " (اختیاری)",
          singleLine = false,
        )
      }
    }
    if (!c.requiresCode && !collects) {
      Spacer(Modifier.height(12.dp))
      Text("بسته به مشتری رسید؟", style = v3Style(16.sp), color = p.ink)
    }
    Spacer(Modifier.height(18.dp))
    val label = if (collects && minor != null) "تحویل شد · ${afn(minor)} گرفتم" else "تحویل شد"
    V3PrimaryButton(
      label,
      { onDone(CourierQueue.latinDigits(code), minor, if (differs) cashReason else "", if (differs) note.trim() else "") },
      Modifier.fillMaxWidth(),
      enabled = codeOk && cashOk,
    )
    Spacer(Modifier.height(8.dp))
    V3SecondaryButton("انصراف", onDismiss, Modifier.fillMaxWidth())
  }
}

@Composable
private fun SignOutSheet(waiting: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
  val p = PhoneV3.colors
  V3Sheet(onDismiss) {
    SheetTitle("خروج از حسابِ پیک")
    Spacer(Modifier.height(8.dp))
    Text(
      if (waiting > 0)
        "${waiting.fa()} کار هنوز به دکان نرسیده و با خروج از این گوشی پاک می‌شود. اول به اینترنت وصل شوید و «به‌روز کردن» را بزنید."
      else "مأموریت‌ها و نشانی‌ها از این گوشی پاک می‌شوند.",
      style = v3Style(15.sp), color = if (waiting > 0) p.negative else p.inkMuted,
    )
    Spacer(Modifier.height(18.dp))
    V3PrimaryButton(
      if (waiting > 0) "با وجودِ این خارج شو" else "خروج",
      onConfirm, Modifier.fillMaxWidth(),
      tone = if (waiting > 0) ButtonTone.NEGATIVE else ButtonTone.INK,
    )
    Spacer(Modifier.height(8.dp))
    V3SecondaryButton("انصراف", onDismiss, Modifier.fillMaxWidth())
  }
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
    V3Sheet(onDismiss) {
      SheetTitle("عکسِ بسته")
      Spacer(Modifier.height(8.dp))
      Text(
        "مشتریِ سفارشِ ${c.orderNo.fa()} به عکسِ تحویل رضایت داده است. عکس فقط از بسته گرفته شود؛ چهرهٔ کسی در عکس نباشد.",
        style = v3Style(15.sp), color = p.inkMuted,
      )
      Spacer(Modifier.height(12.dp))
      Row(
        Modifier.fillMaxWidth().clip(GroupShape).background(p.bg).clickable { parcelOnly = !parcelOnly }.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Checkbox(
          checked = parcelOnly, onCheckedChange = { parcelOnly = it },
          colors = CheckboxDefaults.colors(checkedColor = p.ink, uncheckedColor = p.chevron, checkmarkColor = p.card),
        )
        Text("عکس فقط از بسته است", style = v3Style(15.sp, FontWeight.Medium), color = p.ink)
      }
      error?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = v3Style(13.sp), color = p.negative)
      }
      Spacer(Modifier.height(18.dp))
      V3PrimaryButton(
        "گرفتنِ عکس", { pick = true }, Modifier.fillMaxWidth(),
        enabled = parcelOnly && !busy, busy = busy, icon = Icons.Outlined.PhotoCamera,
      )
      Spacer(Modifier.height(8.dp))
      V3SecondaryButton("انصراف", onDismiss, Modifier.fillMaxWidth())
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
