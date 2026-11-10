package ir.vil3ntec.tohid.ui.v3.shell

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrRemoteApproval
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.perm.RemoteApproval
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.repo.ApprovalRequestsRepository
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.AppNav
import ir.vil3ntec.tohid.ui.components.staff.RemoteAsk
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.LocalV3Nav
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.sales.history.ReceiptModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 *  «درخواست‌های تأیید» ِ صاحب/مدیر (STF-02) — گوشیِ نسخهٔ ۳ و کامپیوتر (همان پوسته): نوارِ پیامِ پوسته
 *  ([ApprovalInboxNotice]) و صفحهٔ `approvals` ([ApprovalInboxRoute]). قاعدهٔ «چه کسی چه را می‌بیند» در
 *  `core/perm/RemoteApproval.inbox`؛ سرور هنگامِ تأیید دوباره می‌سنجد.
 *
 *  ⛔ پیش‌فرض خاموش: فقط صاحب/مدیر و فقط وقتی `/config` ِ سرور `enabled` و `approver` بگوید؛ وگرنه نه نوار، نه پرسیدنِ
 *     فهرست (شاگرد هیچ درخواستی به این در نمی‌فرستد). سوکت نیست: پرسیدنِ فهرست هر [LIST_MS] فقط وقتی پوسته جلوی چشم است،
 *     «روشن است؟» هر [CONFIG_MS]؛ پوشِ سرور (`approval-requests.notifyApprovers`) خبرِ فوری است.
 */
object ApprovalInboxLive {
  const val LIST_MS = 20_000L
  const val CONFIG_MS = 10 * 60_000L

  val config = mutableStateOf<RemoteApproval.Config?>(null)
  val rows = mutableStateOf<List<RemoteApproval.Request>>(emptyList())
  @Volatile private var configAt = 0L
  @Volatile private var listAt = 0L
  private val lock = Mutex()

  val on: Boolean get() = config.value?.let { it.enabled && it.approver } == true

  fun repo(context: Context): ApprovalRequestsRepository? =
    if (runCatching { Backend.isReady(context) }.getOrDefault(false)) ApprovalRequestsRepository(Backend.api(context)) else null

  private fun clear() { config.value = null; rows.value = emptyList() }

  /** «روشن است؟» و فهرست، هر کدام با فاصلهٔ خودش ([force] ⇒ همین حالا) */
  suspend fun refresh(context: Context, force: Boolean = false): Unit = lock.withLock {
    val role = ShopRole.current(context)
    val r = repo(context)
    if (r == null || role !in RemoteApproval.APPROVER_ROLES) { clear(); return@withLock }
    val t = System.currentTimeMillis()
    if (force || config.value == null && t - configAt >= LIST_MS || t - configAt >= CONFIG_MS) {
      configAt = t
      when (val c = r.config()) {
        is ApiResult.Success -> config.value = c.value
        //  قطعِ گذرا بخشِ روشن را خاموش نمی‌کند؛ ردِ پایدار ⇒ «خاموش» تا [CONFIG_MS] ِ بعد (نه هر بیست ثانیه)
        is ApiResult.Failure -> if (!c.error.retryable) { config.value = RemoteApproval.Config(enabled = false, approver = false); rows.value = emptyList() }
      }
    }
    if (!on) { rows.value = emptyList(); return@withLock }
    if (force || t - listAt >= LIST_MS) {
      listAt = t
      when (val l = r.list("open")) {
        is ApiResult.Success -> rows.value = l.value
        is ApiResult.Failure -> if (l.error.code == "approval_requests_disabled") {
          config.value = RemoteApproval.Config(enabled = false, approver = false); rows.value = emptyList()
        }
      }
    }
  }

  /** آنچه همین بیننده تأیید/رد می‌تواند (نقش، نه خودش، اختیارِ همان مقدار) */
  fun visible(context: Context, all: List<RemoteApproval.Request>, now: Long = System.currentTimeMillis()): List<RemoteApproval.Request> {
    val me = runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountId }.getOrDefault("")
    val role = ShopRole.current(context)
    return RemoteApproval.inbox(all, me, role, now) { q -> ShopRole.decide(context, q.perm, RemoteApproval.evalReq(q)).decision }
  }

  /** ردیف دیگر در انتظار نیست (همین دستگاه تصمیم گرفت) */
  fun drop(id: String) { rows.value = rows.value.filter { it.id != id } }
}

/** نوارِ «N درخواستِ تأیید در انتظارِ شماست» — فقط صاحب/مدیر و فقط با پرچمِ روشن؛ زدن ⇒ صفحهٔ `approvals` */
@Composable
fun ApprovalInboxNotice() {
  val context = LocalContext.current
  val nav = LocalV3Nav.current
  LaunchedEffect(Unit) {
    while (true) {
      runCatching { ApprovalInboxLive.refresh(context) }
      delay(ApprovalInboxLive.LIST_MS)
    }
  }
  val all by ApprovalInboxLive.rows
  val mine = remember(all) { ApprovalInboxLive.visible(context, all) }
  if (!ApprovalInboxLive.on || mine.isEmpty()) return
  if (nav?.current?.let(ir.vil3ntec.tohid.core.nav.RoutePaths::base) == ShellPaths.APPROVALS) return
  val open = { if (nav != null) nav.open(ShellPaths.APPROVALS) else AppNav.go(ShellPaths.APPROVALS) }
  DraftNoticeBar(
    tone = V3Tone.WARNING,
    text = StrRemoteApproval.pending(mine.size),
    icon = Icons.Outlined.VerifiedUser,
    action = StrRemoteApproval.open,
    onAction = open,
    onClick = open,
  )
}

/** متنِ کوتاهِ یک درخواست: «تخفیف ۲۰٪ — ۲٬۰۰۰ افغانی» */
internal fun approvalWhat(r: RemoteApproval.Request): String {
  val label = when (r.perm) {
    "sales.discount" -> StrRemoteApproval.permDiscount
    "sales.cancel" -> StrRemoteApproval.permCancel
    "returns.create" -> StrRemoteApproval.permReturn
    "sales.credit" -> StrRemoteApproval.permCredit // STF-03
    "sales.price" -> StrRemoteApproval.permPrice
    "sales.free_line" -> StrRemoteApproval.permFreeLine // POS-10، تصمیمِ ۱-۱۱ (۲)
    else -> r.perm
  }
  val pct = r.pctBp?.let { if (it % 100 == 0L) "${it / 100}٪" else "${it / 100.0}٪" }
  val amount = r.amount?.let { money(it / 100.0) }
  return listOfNotNull(label, listOfNotNull(pct, amount).joinToString(" — ").ifBlank { null }).joinToString(" ")
}

/** سندِ درخواست: شمارهٔ فاکتورِ همین دفتر اگر هست، وگرنه پایانِ شناسه */
internal fun approvalDoc(d: ShopData, r: RemoteApproval.Request): String {
  val (table, id) = RemoteApproval.docParts(r.doc)
  return when (table) {
    "sales" -> StrRemoteApproval.saleDoc(d.sales.find { it.id == id }?.let { ReceiptModel.number(it) } ?: id.takeLast(6))
    "returns" -> StrRemoteApproval.returnDoc(id.takeLast(6))
    else -> r.doc
  }
}

/** `approvals` — صندوقِ تأیید با تأیید/رد و پرسشِ دوباره پیش از هر کدام */
@Composable
fun RouteScope.ApprovalInboxRoute() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val c = PhoneV3.colors
  LaunchedEffect(Unit) {
    while (true) {
      runCatching { ApprovalInboxLive.refresh(context, force = true) }
      delay(ApprovalInboxLive.LIST_MS)
    }
  }
  val all by ApprovalInboxLive.rows
  var tick by remember { mutableStateOf(System.currentTimeMillis()) }
  LaunchedEffect(Unit) { while (true) { delay(1_000L); tick = System.currentTimeMillis() } }
  val mine = remember(all, tick / 15_000L) { ApprovalInboxLive.visible(context, all, tick) }
  var confirm by remember { mutableStateOf<Pair<RemoteApproval.Request, Boolean>?>(null) }
  var busy by remember { mutableStateOf<String?>(null) }
  var error by remember { mutableStateOf<String?>(null) }

  V3Scaffold(title = StrRemoteApproval.inboxTitle, onBack = backOrNull) { pad ->
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      Column(
        Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState())
          .padding(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(StrRemoteApproval.inboxIntro, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(4.dp))
        error?.let { V3Message(V3Tone.ERROR, it) }
        when {
          !ApprovalInboxLive.on -> V3EmptyState(Icons.Outlined.VerifiedUser, StrRemoteApproval.inboxOff)
          mine.isEmpty() -> V3EmptyState(Icons.Outlined.TaskAlt, StrRemoteApproval.inboxEmpty)
          else -> mine.forEach { r ->
            V3Card(padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
              Text(approvalWhat(r), style = V3Type.headline, color = c.ink)
              Spacer(Modifier.height(4.dp))
              Text(
                listOf(StrRemoteApproval.who(r.requesterName.ifBlank { "—" }), approvalDoc(data, r)).joinToString(" · "),
                style = V3Type.subhead, color = c.inkMuted,
              )
              if (r.reason.isNotBlank()) Text(StrRemoteApproval.why(r.reason), style = V3Type.subhead, color = c.ink)
              Text(
                StrRemoteApproval.left(RemoteAsk.clock(RemoteApproval.secondsLeft(r, tick))),
                style = V3Type.footnote, color = c.inkMuted,
              )
              Spacer(Modifier.height(10.dp))
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                V3Button(
                  StrRemoteApproval.approve, { confirm = r to true }, Modifier.weight(1f),
                  enabled = busy == null, loading = busy == r.id && confirm == null,
                )
                V3Button(StrRemoteApproval.deny, { confirm = r to false }, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL, enabled = busy == null)
              }
            }
          }
        }
      }
    }
  }

  confirm?.let { (r, yes) ->
    V3ConfirmSheet(
      title = if (yes) StrRemoteApproval.approve else StrRemoteApproval.deny,
      message = if (yes) StrRemoteApproval.confirmApprove(approvalWhat(r)) else StrRemoteApproval.confirmDeny(approvalWhat(r)),
      confirm = if (yes) StrRemoteApproval.approve else StrRemoteApproval.deny,
      danger = !yes,
      onDismiss = { confirm = null },
      onConfirm = {
        confirm = null
        val repo = ApprovalInboxLive.repo(context) ?: return@V3ConfirmSheet
        busy = r.id
        error = null
        scope.launch {
          val out = if (yes) repo.approve(r.id) else repo.deny(r.id)
          busy = null
          when (out) {
            is ApiResult.Success -> {
              ApprovalInboxLive.drop(r.id)
              toasts.show(if (yes) StrRemoteApproval.doneApproved else StrRemoteApproval.doneDenied, V3Tone.SUCCESS)
            }
            is ApiResult.Failure -> {
              error = out.error.userMessage
              //  بسته یا گذشته ⇒ فهرستِ تازه
              if (!out.error.retryable) runCatching { ApprovalInboxLive.refresh(context, force = true) }
            }
          }
        }
      },
    )
  }
}
