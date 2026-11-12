package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSupportAccess
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.data.repo.SupportAccessRepository
import ir.vil3ntec.tohid.data.repo.SupportGrantDto
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 *  اجازه‌های پشتیبانیِ همین دکان برای همهٔ نمونه‌های نوار (یک پرسش برای کلِ برنامه، نه با هر جابه‌جاییِ صفحه).
 *  `off`: غیرِ صاحب (۴۰۳ `owner_only`) یا سرورِ کهنه (۴۰۴) ⇒ تا بازشدنِ دوبارهٔ برنامه دیگر پرسیده نمی‌شود.
 */
object SupportAccessWatch {
  val grants = MutableStateFlow<List<SupportGrantDto>>(emptyList())
  @Volatile internal var checkedAt = 0L
  @Volatile internal var off = false

  internal fun seen(r: ApiResult<List<SupportGrantDto>>, now: Long) {
    checkedAt = now
    when (r) {
      is ApiResult.Success -> grants.value = r.value
      is ApiResult.Failure -> if (r.error.status == 403 || r.error.status == 404) { off = true; grants.value = emptyList() }
    }
  }

  /** پاسخِ یک کار (پذیرفتن/رد/بستن): همان ردیف جایگزین می‌شود */
  internal fun put(g: SupportGrantDto) { grants.value = grants.value.map { if (it.id == g.id) g else it } }
}

/**
 *  TEN-04 — نوارِ «پشتیبانی در حالِ مشاهده» در پوستهٔ برنامهٔ صاحبِ دکان (`ShellNotice`، همهٔ صفحه‌ها، صندوق هم):
 *   • اجازهٔ زنده ⇒ نوارِ پیدا با «چه کسی، چرا، تا کی، چه دامنه‌ای» و «بستنِ دسترسی» (فقط اجازهٔ با رضایتِ دکان؛ «شکستنِ
 *     شیشه» خودش بسته می‌شود و نوار همین را می‌گوید)؛
 *   • درخواستِ بی‌پاسخ ⇒ نوار با «دیدن و پاسخ» ⇐ برگه با «پذیرفتن» (تأییدِ دوبارهٔ ورود، `ReauthHost`) و «رد».
 *  هیچ دسترسی‌ای این‌جا باز یا بسته نمی‌شود جز با پاسخِ سرور. [enabled] = صاحبِ دکانِ واردشده (`SupportAccessNoticeAndroid.kt`).
 */
@Composable
internal fun SupportAccessNoticeContent(repo: SupportAccessRepository, now: () -> Long, enabled: Boolean, say: (String) -> Unit = {}) {
  val grants by SupportAccessWatch.grants.collectAsState()
  val scope = rememberCoroutineScope()
  var sheet by remember { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  LaunchedEffect(enabled) {
    while (enabled && !SupportAccessWatch.off) {
      val t = now()
      if (t - SupportAccessWatch.checkedAt >= SupportAccessModel.pollMs(SupportAccessWatch.grants.value, t)) {
        SupportAccessWatch.seen(repo.list(), t)
      }
      delay(15_000L)
    }
  }
  if (!enabled) return
  val t = now()
  val live = SupportAccessModel.active(grants, t)
  val asks = SupportAccessModel.pending(grants, t)
  fun act(block: suspend () -> ApiResult<SupportGrantDto>, done: String) {
    if (busy) return
    busy = true
    scope.launch {
      when (val r = block()) {
        is ApiResult.Success -> { SupportAccessWatch.put(r.value); say(done) }
        is ApiResult.Failure -> { say(r.error.userMessage); SupportAccessWatch.seen(repo.list(), now()) }
      }
      busy = false
    }
  }
  live.firstOrNull()?.let { g ->
    DraftNoticeBar(
      tone = if (SupportAccessModel.canRevoke(g)) V3Tone.WARNING else V3Tone.ERROR,
      icon = Icons.Outlined.SupportAgent,
      text = StrSupportAccess.watching(SupportAccessModel.minutesLeft(g, t)),
      detail = StrSupportAccess.who(g.requestedBy.ifBlank { "—" }, g.reason, SupportAccessModel.scope(g.scope)) +
        (if (SupportAccessModel.canRevoke(g)) "" else " — " + StrSupportAccess.breakGlass),
      action = if (SupportAccessModel.canRevoke(g) && !busy) StrSupportAccess.close else null,
      onAction = { act({ repo.revoke(g.id) }, StrSupportAccess.closed) },
      onClick = { sheet = true },
    )
  }
  if (live.isEmpty()) asks.firstOrNull()?.let { g ->
    DraftNoticeBar(
      tone = V3Tone.WARNING,
      icon = Icons.Outlined.SupportAgent,
      text = StrSupportAccess.request,
      detail = StrSupportAccess.requestDetail(g.requestedBy.ifBlank { "—" }, g.reason, g.minutes),
      action = StrSupportAccess.answer,
      onAction = { sheet = true },
      onClick = { sheet = true },
    )
  }
  if (sheet) V3Sheet(onDismiss = { sheet = false }, title = StrSupportAccess.title) { _ ->
    Text(StrSupportAccess.intro, style = V3Type.subhead, color = PhoneV3.colors.inkMuted)
    Spacer(Modifier.height(12.dp))
    if (live.isEmpty() && asks.isEmpty()) V3Message(V3Tone.INFO, StrSupportAccess.nothing)
    live.forEach { g ->
      V3Message(if (SupportAccessModel.canRevoke(g)) V3Tone.WARNING else V3Tone.ERROR, StrSupportAccess.watching(SupportAccessModel.minutesLeft(g, t)),
        body = StrSupportAccess.who(g.requestedBy.ifBlank { "—" }, g.reason, SupportAccessModel.scope(g.scope)) + " · " + StrSupportAccess.uses(g.uses) +
          (if (SupportAccessModel.canRevoke(g)) "" else "\n" + StrSupportAccess.breakGlass))
      if (SupportAccessModel.canRevoke(g)) V3Button(StrSupportAccess.close, { act({ repo.revoke(g.id) }, StrSupportAccess.closed) }, kind = V3ButtonKind.SECONDARY, loading = busy)
      Spacer(Modifier.height(10.dp))
    }
    asks.forEach { g ->
      V3Message(V3Tone.WARNING, StrSupportAccess.request,
        body = StrSupportAccess.requestDetail(g.requestedBy.ifBlank { "—" }, g.reason, g.minutes) + " · " + SupportAccessModel.scope(g.scope))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        V3Button(StrSupportAccess.accept, { act({ repo.accept(g.id) }, StrSupportAccess.accepted) }, loading = busy)
        V3Button(StrSupportAccess.deny, { act({ repo.deny(g.id) }, StrSupportAccess.denied) }, kind = V3ButtonKind.NEUTRAL, enabled = !busy)
      }
      Spacer(Modifier.height(10.dp))
    }
  }
}
