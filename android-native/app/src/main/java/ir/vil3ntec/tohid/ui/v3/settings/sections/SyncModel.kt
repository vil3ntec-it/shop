package ir.vil3ntec.tohid.ui.v3.settings.sections

import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.core.i18n.StrSettings
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
  enum class Problem { NONE, OFFLINE, SIGN_IN, SERVER, SERVER_BEHIND, NO_SHOP, LOCAL, REJECTED, UNKNOWN }

  data class Diagnosis(val problem: Problem, val title: String, val body: String, val action: String, val tone: V3Tone, val detail: String?)

  private val OFFLINE_HINTS = listOf("unable to resolve", "failed to connect", "timeout", "timed out", "network", "connection", "socket", "اینترنت", "وصل نشد", "نت نیست")
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

  /**
   *  ترتیب: سرورِ عقب‌مانده (چیزی نمی‌رود تا به‌روز شود) ⇐ نبودنِ دکان ⇐ نتِ دستگاه ⇐
   *  خطای آخرین دور ⇐ تغییرهای ردشده. `detail` همان متنِ خام برای پشتیبانی است.
   */
  fun diagnose(error: String, holding: Boolean, dropped: Int, needsShop: Boolean, server: ServerPulse.State): Diagnosis {
    val raw = error.trim()
    val problem = when {
      holding -> Problem.SERVER_BEHIND
      needsShop -> Problem.NO_SHOP
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
      Problem.SERVER -> Diagnosis(problem, StrSettings.diagServerTitle, StrSettings.diagServerBody, StrSettings.diagServerDo, V3Tone.ERROR, detail)
      Problem.SERVER_BEHIND -> Diagnosis(problem, StrSettings.diagBehindTitle, StrNav.serverBehind, StrSettings.diagBehindDo, V3Tone.WARNING, null)
      Problem.NO_SHOP -> Diagnosis(problem, StrSettings.diagNoShopTitle, StrSettings.diagNoShopBody, StrSettings.diagNoShopDo, V3Tone.WARNING, detail)
      Problem.LOCAL -> Diagnosis(problem, StrSettings.diagLocalTitle, StrSettings.diagLocalBody, StrSettings.diagLocalDo, V3Tone.ERROR, detail)
      Problem.REJECTED -> Diagnosis(problem, StrSettings.diagRejectedTitle(dropped), StrNav.droppedChanges(dropped), StrSettings.diagRejectedDo, V3Tone.WARNING, null)
      Problem.UNKNOWN -> Diagnosis(problem, StrSettings.diagUnknownTitle, StrSettings.diagUnknownBody, StrSettings.diagUnknownDo, V3Tone.ERROR, detail)
    }
  }
}
