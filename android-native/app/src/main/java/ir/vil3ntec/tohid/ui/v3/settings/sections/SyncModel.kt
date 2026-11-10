package ir.vil3ntec.tohid.ui.v3.settings.sections

import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.sync.SyncOutage
import ir.vil3ntec.tohid.sync.AutoSync
import ir.vil3ntec.tohid.ui.screens.ServerPulse
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.settings.SyncState

/**
 *  نوشته‌های صفحهٔ همگام‌سازی از روی حالِ موتورها (`AutoSync`، `ServerPulse`) — همان
 *  قاعده‌های برگهٔ نقطهٔ همگام‌سازیِ سربرگِ قدیم، خالص و آزمون‌پذیر.
 */
internal object SyncModel {

  data class Line(val text: String, val tone: V3Tone?)

  fun state(health: AutoSync.Health): SyncState = when (health) {
    AutoSync.Health.OK -> SyncState.OK
    AutoSync.Health.WAITING -> SyncState.WAITING
    AutoSync.Health.FAILED -> SyncState.FAILED
  }

  fun headline(health: AutoSync.Health, running: Boolean, pending: Int): Line = when (health) {
    AutoSync.Health.OK -> Line(StrNav.syncAllOnServer, V3Tone.SUCCESS)
    AutoSync.Health.WAITING -> Line(if (running) Str.sending else StrNav.pendingItems(pending), V3Tone.WARNING)
    AutoSync.Health.FAILED -> Line(StrNav.lastAttemptFailed, V3Tone.ERROR)
  }

  fun server(state: ServerPulse.State): Line = when (state) {
    ServerPulse.State.UP -> Line(StrNav.serverUp, V3Tone.SUCCESS)
    ServerPulse.State.DOWN -> Line(StrNav.serverDown, V3Tone.ERROR)
    ServerPulse.State.NO_NET -> Line(StrNav.phoneOffline, V3Tone.WARNING)
    ServerPulse.State.CHECKING -> Line(StrNav.checking, null)
    ServerPulse.State.UNKNOWN -> Line(StrNav.notCheckedYet, null)
    ServerPulse.State.NO_SERVER -> Line(StrNav.noServerBuild, null)
  }

  fun queue(queued: Int): Line =
    if (queued > 0) Line(StrNav.changesQueued(queued), V3Tone.WARNING) else Line(StrNav.noChangesQueued, V3Tone.SUCCESS)

  /** گرهِ همگام‌سازی به زبانِ آدم — چه شده، چه نگه داشته شده، کاربر چه کند */
  enum class Problem { NONE, OFFLINE, SIGN_IN, SERVER, SERVER_BEHIND, NO_SHOP, LOCAL, REJECTED, UNKNOWN, UNREACHABLE, RETRYING }

  data class Diagnosis(val problem: Problem, val title: String, val body: String, val action: String, val tone: V3Tone, val detail: String?)

  private val OFFLINE_HINTS = listOf("unable to resolve", "failed to connect", "timeout", "timed out", "network", "connection", "socket", "اینترنت", "انترنت", "وصل نشد", "نت نیست")
  private val SIGN_IN_HINTS = listOf("401", "unauthor", "token", "نشست", "دوباره وارد", "وارد حساب")
  private val SERVER_HINTS = listOf("500", "502", "503", "504", "server error", "bad gateway", "سرور")
  private val LOCAL_HINTS = listOf("ژورنال", "سایه", "journal")

  /** دستهٔ خطای خام (پیامِ لایهٔ شبکه یا موتور) */
  fun classify(raw: String): Problem {
    val low = raw.lowercase()
    return when {
      raw.isBlank() -> Problem.NONE
      LOCAL_HINTS.any { it in low } -> Problem.LOCAL
      SIGN_IN_HINTS.any { it in low } -> Problem.SIGN_IN
      OFFLINE_HINTS.any { it in low } -> Problem.OFFLINE
      SERVER_HINTS.any { it in low } -> Problem.SERVER
      else -> Problem.UNKNOWN
    }
  }

  /** SYN-12: حکمِ موتور (`SyncStatus.outage`، از کدِ HTTP و مدتِ شکست) جای نشانه‌های متنی؛ «خطا»/«همگام» ⇒ همان نشانه‌ها */
  fun fromOutage(outage: SyncOutage.Kind?): Problem? = when (outage) {
    SyncOutage.Kind.OFFLINE -> Problem.OFFLINE
    SyncOutage.Kind.RETRYING -> Problem.RETRYING
    SyncOutage.Kind.UNREACHABLE -> Problem.UNREACHABLE
    SyncOutage.Kind.SERVER -> Problem.SERVER
    SyncOutage.Kind.AUTH -> Problem.SIGN_IN
    else -> null
  }

  /**
   *  ترتیب: سرورِ عقب‌مانده (چیزی نمی‌رود تا به‌روز شود) ⇐ نبودنِ دکان ⇐ حکمِ موتور (SYN-12) ⇐ نتِ دستگاه ⇐
   *  خطای آخرین دور ⇐ تغییرهای ردشده. `detail` همان متنِ خام برای پشتیبانی است. سرورِ گرفتار (حکمِ
   *  موتور) زرد است، نه سرخ: تقصیرِ این دستگاه نیست و خودش دوباره می‌رود.
   */
  fun diagnose(error: String, holding: Boolean, dropped: Int, needsShop: Boolean, server: ServerPulse.State, outage: SyncOutage.Kind? = null): Diagnosis {
    val raw = error.trim()
    val judged = if (raw.isNotEmpty()) fromOutage(outage) else null
    val problem = when {
      holding -> Problem.SERVER_BEHIND
      needsShop -> Problem.NO_SHOP
      judged != null -> judged
      server == ServerPulse.State.NO_NET && raw.isNotEmpty() -> Problem.OFFLINE
      raw.isNotEmpty() -> classify(raw)
      dropped > 0 -> Problem.REJECTED
      else -> Problem.NONE
    }
    val detail = raw.takeIf { it.isNotEmpty() && problem != Problem.SERVER_BEHIND }
    return when (problem) {
      Problem.NONE -> Diagnosis(problem, StrNav.syncAllOnServer, "", "", V3Tone.SUCCESS, null)
      Problem.OFFLINE -> Diagnosis(problem, StrSettings.diagOfflineTitle, StrSettings.diagOfflineBody, StrSettings.diagOfflineDo, V3Tone.WARNING, detail)
      Problem.SIGN_IN -> Diagnosis(problem, StrSettings.diagSignInTitle, StrSettings.diagSignInBody, StrSettings.diagSignInDo, V3Tone.ERROR, detail)
      Problem.SERVER -> Diagnosis(problem, StrSettings.diagServerTitle, StrSettings.diagServerBody, StrSettings.diagServerDo, if (judged == Problem.SERVER) V3Tone.WARNING else V3Tone.ERROR, detail)
      Problem.UNREACHABLE -> Diagnosis(problem, StrSettings.diagUnreachableTitle, StrSettings.diagUnreachableBody, StrSettings.diagUnreachableDo, V3Tone.WARNING, detail)
      Problem.RETRYING -> Diagnosis(problem, StrSettings.diagRetryingTitle, StrSettings.diagRetryingBody, StrSettings.diagRetryingDo, V3Tone.INFO, detail)
      Problem.SERVER_BEHIND -> Diagnosis(problem, StrSettings.diagBehindTitle, StrNav.serverBehind, StrSettings.diagBehindDo, V3Tone.WARNING, null)
      Problem.NO_SHOP -> Diagnosis(problem, StrSettings.diagNoShopTitle, StrSettings.diagNoShopBody, StrSettings.diagNoShopDo, V3Tone.WARNING, detail)
      Problem.LOCAL -> Diagnosis(problem, StrSettings.diagLocalTitle, StrSettings.diagLocalBody, StrSettings.diagLocalDo, V3Tone.ERROR, detail)
      Problem.REJECTED -> Diagnosis(problem, StrSettings.diagRejectedTitle(dropped), StrNav.droppedChanges(dropped), StrSettings.diagRejectedDo, V3Tone.WARNING, null)
      Problem.UNKNOWN -> Diagnosis(problem, StrSettings.diagUnknownTitle, StrSettings.diagUnknownBody, StrSettings.diagUnknownDo, V3Tone.ERROR, detail)
    }
  }
}
