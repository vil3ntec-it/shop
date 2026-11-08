package ir.vil3ntec.tohid.ui.v3.shell

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrConflictInbox
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.perm.RemoteApproval
import ir.vil3ntec.tohid.core.sync.ConflictInbox
import ir.vil3ntec.tohid.data.ConflictFix
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.ConflictInboxRepository
import ir.vil3ntec.tohid.ui.AppNav
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.LocalV3Nav
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.sales.history.SaleTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 *  «سندهای ردشده» ِ صاحب/مدیر (`core/sync/ConflictInbox`، `/api/shop/conflict-inbox`) — گوشیِ نسخهٔ ۳ و کامپیوتر (همان
 *  پوسته): نوارِ پیامِ پوسته ([ConflictInboxNotice]، همان الگوی «درخواست‌های تأیید» ِ #972) و صفحهٔ `conflicts`
 *  ([ConflictInboxRoute]) با فاکتورِ کامل و سه کار ([ConflictDetailSheet]).
 *
 *  ⛔ پیش‌فرض روشن، بی پرچم — ولی فقط وقتی چیزی هست: نوار فقط با سندِ باز، فقط برای صاحب/مدیر؛ شاگرد هیچ درخواستی به این
 *     در نمی‌فرستد. سرورِ کهنه (۴۰۴) یا نقشِ بی اجازه (۴۰۳) ⇒ خاموش تا [OFF_MS] ِ بعد، بی پرسیدنِ هر دقیقه.
 *  ⛔ سرور تنها داور است: هر کار پاسخِ سرور را جای ردیف می‌نشاند؛ هیچ چیزی «انجام‌شده» فرض نمی‌شود.
 */
object ConflictInboxLive {
  const val LIST_MS = 60_000L
  const val OFF_MS = 10 * 60_000L

  val rows = mutableStateOf<List<ConflictInbox.Item>>(emptyList())
  val open = mutableStateOf(0)
  val on = mutableStateOf(false)
  val failed = mutableStateOf(false)
  @Volatile private var listAt = 0L
  @Volatile private var offUntil = 0L
  private val lock = Mutex()

  fun repo(context: Context): ConflictInboxRepository? =
    if (runCatching { Backend.isReady(context) }.getOrDefault(false)) ConflictInboxRepository(Backend.api(context)) else null

  /** همان نقش‌های صندوقِ تأیید (`data.delete.any` ِ سرور: صاحب و مدیر) */
  fun resolver(context: Context): Boolean = ShopRole.current(context) in RemoteApproval.APPROVER_ROLES

  private fun clear() { rows.value = emptyList(); open.value = 0; on.value = false }

  suspend fun refresh(context: Context, force: Boolean = false): Unit = lock.withLock {
    val r = repo(context)
    if (r == null || !resolver(context)) { clear(); return@withLock }
    val t = System.currentTimeMillis()
    if (!force && (t < offUntil || t - listAt < LIST_MS)) return@withLock
    listAt = t
    when (val l = r.list(ConflictInbox.OPEN)) {
      is ApiResult.Success -> { rows.value = l.value.items; open.value = l.value.open; on.value = true; failed.value = false }
      is ApiResult.Failure -> if (l.error.retryable) failed.value = true else { clear(); offUntil = t + OFF_MS }
    }
  }

  /** پاسخِ سرور جای همان ردیف؛ بسته ⇒ از فهرستِ باز بیرون */
  fun put(item: ConflictInbox.Item) {
    val next = rows.value.map { if (it.id == item.id) item else it }.filter { it.open }
    if (next.size != rows.value.size) open.value = (open.value - 1).coerceAtLeast(next.size)
    rows.value = next
  }
}

/** نوارِ «N سندِ ردشده منتظرِ تصمیمِ شماست» — فقط صاحب/مدیر و فقط با سندِ باز؛ زدن ⇒ صفحهٔ `conflicts` */
@Composable
fun ConflictInboxNotice() {
  val context = LocalContext.current
  val nav = LocalV3Nav.current
  LaunchedEffect(Unit) {
    while (true) {
      runCatching { ConflictInboxLive.refresh(context) }
      //  «اصلاح و ثبت» ِ مانده (بی شبکه بسته نشد) ⇒ همین‌جا دوباره
      runCatching { ConflictFix.retry(context) }
      delay(ConflictInboxLive.LIST_MS)
    }
  }
  val count by ConflictInboxLive.open
  val on by ConflictInboxLive.on
  if (!on || count <= 0) return
  if (nav?.current?.let(ir.vil3ntec.tohid.core.nav.RoutePaths::base) == ShellPaths.CONFLICTS) return
  val go = { if (nav != null) nav.open(ShellPaths.CONFLICTS) else AppNav.go(ShellPaths.CONFLICTS) }
  DraftNoticeBar(
    tone = V3Tone.WARNING,
    text = StrConflictInbox.pending(count),
    icon = Icons.Outlined.CloudOff,
    action = StrConflictInbox.open,
    onAction = go,
    onClick = go,
  )
}

/** «فاکتورِ ۴۱» / «برگشتِ …» — شمارهٔ فاکتور اگر هست، وگرنه پایانِ شناسه */
internal fun conflictDoc(i: ConflictInbox.Item): String {
  val no = i.number?.toString() ?: i.docRowId.takeLast(6)
  return if (i.docTable == "returns") StrConflictInbox.returnDoc(no) else StrConflictInbox.saleDoc(no)
}

internal fun conflictMoney(minor: Long?): String = minor?.let { ir.vil3ntec.tohid.money(it / 100.0) } ?: "—"

internal fun conflictQty(milli: Long?): String = milli?.let { ir.vil3ntec.tohid.qty(it / 1000.0) } ?: "—"

internal fun conflictStatus(i: ConflictInbox.Item): String = when (i.status) {
  ConflictInbox.RESUBMITTED -> StrConflictInbox.statusResubmitted
  ConflictInbox.FIXED -> StrConflictInbox.statusFixed
  ConflictInbox.DISMISSED -> StrConflictInbox.statusDismissed
  else -> ""
}

/** `conflicts` — صندوقِ سندهای ردشده: باز و بسته‌ها؛ زدنِ هر ردیف ⇒ فاکتورِ کامل و کارها */
@Composable
fun RouteScope.ConflictInboxRoute() {
  val context = LocalContext.current
  val c = PhoneV3.colors
  var tab by remember { mutableStateOf(0) }
  var closed by remember { mutableStateOf<List<ConflictInbox.Item>?>(null) }
  var picked by remember { mutableStateOf<ConflictInbox.Item?>(null) }
  val fixedNow by ConflictFix.closed.collectAsState()
  LaunchedEffect(Unit) {
    while (true) {
      runCatching { ConflictInboxLive.refresh(context, force = true) }
      delay(ConflictInboxLive.LIST_MS / 2)
    }
  }
  LaunchedEffect(tab, fixedNow) {
    if (tab != 1) return@LaunchedEffect
    closed = null
    closed = when (val r = ConflictInboxLive.repo(context)?.list("closed")) {
      is ApiResult.Success -> r.value.items
      else -> emptyList()
    }
  }
  LaunchedEffect(fixedNow) { if (fixedNow != null) runCatching { ConflictInboxLive.refresh(context, force = true) } }
  val open by ConflictInboxLive.rows
  val on by ConflictInboxLive.on
  val failed by ConflictInboxLive.failed

  V3Scaffold(title = StrConflictInbox.title, onBack = backOrNull) { pad ->
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      Column(
        Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState())
          .padding(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(StrConflictInbox.intro, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(4.dp))
        V3Segmented(listOf(StrConflictInbox.tabOpen, StrConflictInbox.tabClosed), tab, { tab = it })
        if (failed && tab == 0) V3Message(V3Tone.WARNING, StrConflictInbox.loadFailed)
        val list = if (tab == 0) open else closed.orEmpty()
        when {
          tab == 0 && (!on || list.isEmpty()) -> V3EmptyState(Icons.Outlined.TaskAlt, StrConflictInbox.empty)
          tab == 1 && closed != null && list.isEmpty() -> V3EmptyState(Icons.Outlined.TaskAlt, StrConflictInbox.emptyClosed)
          else -> list.forEach { i -> ConflictCard(i) { picked = i } }
        }
      }
    }
  }
  picked?.let { i ->
    ConflictDetailSheet(
      item = i,
      onClose = { picked = null },
      onChanged = { next -> ConflictInboxLive.put(next); if (!next.open) closed = null },
    )
  }
}

/** یک ردیف: سند و مبلغ، دلیل، کارمند و دستگاه، قلم‌ها، زمان؛ تلاشِ پیشین یا حالِ بسته */
@Composable
private fun ConflictCard(i: ConflictInbox.Item, onOpen: () -> Unit) {
  val c = PhoneV3.colors
  val red = PhoneV3Roles.tone(c, V3Tone.ERROR).fg
  V3Card(Modifier.clickable(onClick = onOpen), padding = PaddingValues(14.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(conflictDoc(i), style = V3Type.headline, color = c.ink)
      Text(conflictMoney(i.amountMinor), style = V3Type.headline, color = c.ink)
    }
    Spacer(Modifier.height(4.dp))
    Text(StrConflictInbox.why(StrConflictInbox.reason(i.reason)), style = V3Type.subhead, color = if (i.open) red else c.inkMuted)
    Text(
      listOf(
        StrConflictInbox.staff(i.staff.name.ifBlank { "—" }),
        StrConflictInbox.device(i.deviceName.ifBlank { i.deviceId.takeLast(8).ifBlank { "—" } }),
      ).joinToString(" · "),
      style = V3Type.subhead, color = c.inkMuted,
    )
    if (i.itemCount > 0) {
      val names = i.items.joinToString("، ") { l -> listOf(l.name.ifBlank { "—" }, conflictQty(l.qtyMilli)).joinToString(" × ") }
      Text(StrConflictInbox.items(i.itemCount, names), style = V3Type.subhead, color = c.ink, maxLines = 2)
    }
    Text(SaleTime.stamp(i.at), style = V3Type.footnote, color = c.inkMuted)
    when {
      i.busy -> Text(StrConflictInbox.busy, style = V3Type.footnote, color = c.inkMuted)
      i.open && i.lastError != null -> Text(StrConflictInbox.lastTry(StrConflictInbox.reason(i.lastError!!)), style = V3Type.footnote, color = red)
      !i.open -> Text(
        StrConflictInbox.closedBy(conflictStatus(i), i.resolvedBy?.name?.ifBlank { null } ?: "—") +
          (if (i.note.isNotBlank()) " · " + i.note else ""),
        style = V3Type.footnote, color = c.inkMuted,
      )
    }
  }
}
