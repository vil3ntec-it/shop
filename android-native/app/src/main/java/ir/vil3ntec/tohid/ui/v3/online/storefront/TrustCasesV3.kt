package ir.vil3ntec.tohid.ui.v3.online.storefront

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.OnlineFmt
import ir.vil3ntec.tohid.core.i18n.StrTrust
import ir.vil3ntec.tohid.data.repo.TrustCaseDto
import ir.vil3ntec.tohid.data.repo.TrustConfigDto
import ir.vil3ntec.tohid.data.repo.TrustRepository
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3Skeleton
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import kotlinx.coroutines.launch

/**
 *  پیکربندیِ «شکایت و گزارش» ِ همین دکان (`GET /shop/trust/config`) — برای «…» ِ ویترین (`storefrontExtras`): پرچمِ
 *  خاموشِ `market.trust` ⇒ هیچ مدخلی. با بارِ ویترین و هر بارِ همین صفحه تازه می‌شود.
 */
internal object TrustHub {
  val config = mutableStateOf<TrustConfigDto?>(null)
  val enabled: Boolean get() = config.value?.enabled == true

  suspend fun refresh(repo: TrustRepository) {
    repo.config().onSuccess { config.value = it }
  }
}

/**
 *  ABUSE-01 — «شکایت و گزارشِ خریداران» (مسیرِ `storefront/trust`، از «…» ِ ویترین): فهرستِ پرونده‌های بازارچه دربارهٔ
 *  همین دکان (بی‌پاسخ‌ها بالا)، و برگهٔ هر پرونده با نوشتهٔ خریدار، تصمیم و دلیلِ پشتیبانی، جریمه، تاریخچه، «پاسخِ دکان» و
 *  «اعتراض به تصمیم». قاعده‌ها `TrustModel`؛ رفت‌وبرگشت `TrustRepository`. ⛔ هیچ نشانی از شاکی (سرور نمی‌دهد).
 *  بی‌پلتفرم: مسیر در `TrustCasesAndroid.kt` فقط مخزن را می‌سازد.
 */
@Composable
internal fun TrustCasesContent(
  repo: TrustRepository,
  now: () -> Long,
  productName: (String) -> String? = { null },
  say: (String) -> Unit = {},
) {
  var list by remember { mutableStateOf<List<TrustCaseDto>?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  var openId by remember { mutableStateOf<String?>(null) }
  var reload by remember { mutableStateOf(0) }
  LaunchedEffect(reload) {
    error = null
    TrustHub.refresh(repo)
    repo.cases().onSuccess { list = TrustModel.sorted(it) }.onFailure { error = it.userMessage }
  }
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
    Text(StrTrust.intro, style = V3Type.subhead, color = PhoneV3.colors.inkMuted)
    Spacer(Modifier.height(12.dp))
    val l = list
    when {
      l == null && error != null -> V3Message(V3Tone.ERROR, StrTrust.loadFailed, body = error, action = StrTrust.retry, onAction = { reload++ })
      l == null -> V3Skeleton(4)
      l.isEmpty() -> V3EmptyState(Icons.Outlined.Flag, StrTrust.empty)
      else -> l.forEach { c ->
        val parts = listOfNotNull(TrustModel.subject(c, productName), TrustModel.status(c.status),
          (c.appealOutcome ?: c.outcome)?.let(TrustModel::outcome), OnlineFmt.ago(c.createdAt, now()))
        V3ListRow(
          title = TrustModel.category(c.kind, c.category),
          subtitle = parts.joinToString(" · "),
          icon = Icons.Outlined.Flag,
          value = StrTrust.needsReply.takeIf { TrustModel.waiting(c) },
          onClick = { openId = c.id },
        )
      }
    }
  }
  openId?.let { id ->
    TrustCaseSheet(repo, id, productName, now, onClose = { openId = null }, onChanged = { msg -> say(msg); reload++ })
  }
}

/** برگهٔ یک پرونده — همیشه نسخهٔ تازهٔ سرور (`GET /cases/:id`)؛ پس از پاسخ یا اعتراض همان پاسخِ سرور نشانده می‌شود. */
@Composable
internal fun TrustCaseSheet(
  repo: TrustRepository,
  id: String,
  productName: (String) -> String?,
  now: () -> Long,
  onClose: () -> Unit,
  onChanged: (String) -> Unit,
) {
  val scope = rememberCoroutineScope()
  var c by remember(id) { mutableStateOf<TrustCaseDto?>(null) }
  var loadError by remember(id) { mutableStateOf<String?>(null) }
  var reply by remember(id) { mutableStateOf("") }
  var appeal by remember(id) { mutableStateOf("") }
  var tried by remember(id) { mutableStateOf(false) }
  var triedAppeal by remember(id) { mutableStateOf(false) }
  var busy by remember(id) { mutableStateOf(false) }
  var failure by remember(id) { mutableStateOf<String?>(null) }
  //  یک کلید برای هر برگه: «فرستادن» ِ دوباره پس از قطعِ شبکه همان پاسخ است، نه دومی
  var key by remember(id) { mutableStateOf(TrustRepository.newKey()) }
  LaunchedEffect(id) { repo.case(id).onSuccess { c = it }.onFailure { loadError = it.userMessage } }
  val cfg = TrustHub.config.value
  V3Sheet(onDismiss = onClose, title = c?.let { TrustModel.category(it.kind, it.category) } ?: StrTrust.title) { _ ->
    val k = c
    if (k == null) {
      if (loadError != null) V3Message(V3Tone.ERROR, StrTrust.loadFailed, body = loadError) else V3Skeleton(3)
      return@V3Sheet
    }
    Text(listOf(TrustModel.subject(k, productName), TrustModel.status(k.status)).joinToString(" · "), style = V3Type.subhead, color = PhoneV3.colors.inkMuted)
    Spacer(Modifier.height(10.dp))
    Text(StrTrust.customerText, style = V3Type.caption, color = PhoneV3.colors.inkMuted)
    Text(k.body.ifBlank { StrTrust.noText }, style = V3Type.body)
    if (k.evidenceCount > 0) Text(StrTrust.evidence(k.evidenceCount), style = V3Type.footnote, color = PhoneV3.colors.inkMuted)
    k.outcome?.let { o ->
      Spacer(Modifier.height(12.dp))
      V3Message(if (o == "upheld" && k.appeal?.outcome != "reversed") V3Tone.WARNING else V3Tone.INFO,
        StrTrust.decision + ": " + TrustModel.outcome(k.appeal?.outcome?.takeIf { it == "reversed" } ?: o),
        body = k.decisionReason.takeIf { it.isNotBlank() }?.let(StrTrust::reason))
    }
    k.appeal?.let { a -> Spacer(Modifier.height(8.dp)); Text(StrTrust.appealed(a.body), style = V3Type.callout) }
    k.sanctions.forEach { s ->
      val kind = TrustModel.sanctionKind(s.kind)
      if (s.active) V3Message(V3Tone.WARNING, StrTrust.sanctionActive(kind))
      else Text(StrTrust.sanctionLifted(kind), style = V3Type.callout, color = PhoneV3.colors.inkMuted)
    }
    Spacer(Modifier.height(12.dp))
    Text(StrTrust.history, style = V3Type.headline)
    k.history.forEach { e ->
      Text(TrustModel.event(e.type) + " · " + OnlineFmt.ago(e.at, now()) + (e.body?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""),
        style = V3Type.footnote, color = PhoneV3.colors.inkMuted)
    }
    Spacer(Modifier.height(14.dp))
    failure?.let { V3Message(V3Tone.ERROR, it); Spacer(Modifier.height(8.dp)) }
    when {
      cfg?.permissions?.respond != true -> V3Message(V3Tone.INFO, StrTrust.replyOnlyManagers)
      k.status == "closed" -> V3Message(V3Tone.INFO, StrTrust.closedNoReply)
      TrustModel.canReply(cfg, k) -> {
        val p = TrustModel.replyProblem(reply)
        V3TextField(reply, { reply = it.take(TrustModel.TEXT_MAX + 1); failure = null }, StrTrust.reply, helper = StrTrust.replyHelp,
          required = true, error = when {
            !tried -> null
            p == TrustModel.Problem.EMPTY -> StrTrust.replyEmpty
            p == TrustModel.Problem.LONG -> StrTrust.tooLong(TrustModel.TEXT_MAX)
            else -> null
          })
        Spacer(Modifier.height(8.dp))
        V3Button(StrTrust.sendReply, {
          tried = true
          if (p != null || busy) return@V3Button
          busy = true
          scope.launch {
            repo.respond(k.id, reply, key)
              .onSuccess { c = it; reply = ""; tried = false; key = TrustRepository.newKey(); onChanged(StrTrust.replySent) }
              .onFailure { e -> failure = e.userMessage; if (!e.retryable) key = TrustRepository.newKey() }
            busy = false
          }
        }, modifier = Modifier.fillMaxWidth(), loading = busy)
      }
    }
    if (TrustModel.canAppeal(cfg, k, now())) {
      Spacer(Modifier.height(16.dp))
      Text(StrTrust.appeal, style = V3Type.headline)
      k.appealDeadline?.let { Text(StrTrust.appealUntil(ir.vil3ntec.tohid.displayDate(ir.vil3ntec.tohid.ui.v3.shell.dayOf(it))), style = V3Type.footnote, color = PhoneV3.colors.inkMuted) }
      val p = TrustModel.appealProblem(appeal)
      V3TextField(appeal, { appeal = it.take(TrustModel.TEXT_MAX + 1); failure = null }, StrTrust.appealHelp, required = true,
        error = when {
          !triedAppeal -> null
          p == TrustModel.Problem.LONG -> StrTrust.tooLong(TrustModel.TEXT_MAX)
          p != null -> StrTrust.appealShort
          else -> null
        })
      Spacer(Modifier.height(8.dp))
      V3Button(StrTrust.sendAppeal, {
        triedAppeal = true
        if (p != null || busy) return@V3Button
        busy = true
        scope.launch {
          repo.appeal(k.id, appeal)
            .onSuccess { c = it; appeal = ""; triedAppeal = false; onChanged(StrTrust.appealSent) }
            .onFailure { e -> failure = e.userMessage }
          busy = false
        }
      }, modifier = Modifier.fillMaxWidth(), kind = V3ButtonKind.SECONDARY, loading = busy)
    }
  }
}
