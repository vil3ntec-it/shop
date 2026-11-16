package ir.vil3ntec.tohid.ui.v3.settings.sections

import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.i18n.StrSyncPlain
import ir.vil3ntec.tohid.core.sync.SyncOutage
import ir.vil3ntec.tohid.sync.AutoSync
import ir.vil3ntec.tohid.sync.v1.SyncStatus
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

  /**
   *  خطای خامِ موتور ⇒ یک جملهٔ کوتاهِ بی جزئیاتِ فنی برای خانه، نوارِ وضعیت و برگهٔ خروج (قاعدهٔ «هیچ جزئیاتِ فنی/زیرساخت
   *  در صفحه»). اینترنت/نشست/سرور/دفترِ دستگاه ⇒ همان سرخطِ «گره»؛ پیامِ ناشناختهٔ ساده (فارسیِ بی نشانی، بی کدِ لاتین و
   *  بی عدد، مثلاً «اشتراک تمام شده») همان می‌ماند، وگرنه «همگام‌سازی کامل نشد». خالی ⇒ خالی. متنِ خام فقط برای پشتیبانی می‌رود.
   */
  fun reason(raw: String): String {
    val e = raw.trim()
    return when (if (e.isEmpty()) Problem.NONE else classify(e)) {
      Problem.NONE -> ""
      Problem.OFFLINE -> StrSettings.diagOfflineTitle
      Problem.SIGN_IN -> StrSettings.diagSignInTitle
      Problem.SERVER -> StrSettings.diagServerTitle
      Problem.LOCAL -> StrSettings.diagLocalTitle
      else -> e.takeIf { plain(it) } ?: StrSettings.diagUnknownTitle
    }
  }

  /** متنِ بی هیچ نشانهٔ فنی: نه حرف/رقمِ لاتین، نه رقمِ دیگر، نه نشانی، و کوتاه */
  fun plain(text: String): Boolean = text.length <= PLAIN_MAX && !TECHNICAL.containsMatchIn(text)

  private const val PLAIN_MAX = 120
  private val TECHNICAL = Regex("[A-Za-z0-9۰-۹٠-٩]|://|[{}<>\\/_=]")

  /**
   *  جزئیاتِ فنیِ کنارِ گره — **فقط برای پشتیبانی** (`SupportDiagAndroid`)، هرگز در صفحه: نسخهٔ Schema ِ دستگاه و سرور،
   *  نسخه و راهِ API ِ سرور، صف، ردشده‌ها، حکمِ موتور. نشانیِ سرور نیست (گزارش به همان سرور می‌رود).
   */
  fun facts(v1: SyncStatus, serverVersion: String, apiPrefix: String): Map<String, String> = linkedMapOf(
    "schema" to v1.schemaVersion.toString(),
    "serverSchema" to v1.serverSchema.toString(),
    "serverVersion" to serverVersion,
    "apiPrefix" to apiPrefix,
    "queued" to v1.queued.toString(),
    "dropped" to v1.dropped.toString(),
    "holding" to v1.holding.toString(),
    "outage" to v1.outage.name,
    "lastOkAt" to v1.lastOkAt.toString(),
  )

  /** یادداشتِ زیرِ «وضعیتِ سرور»: سرورِ بالا ولی ناآماده ⇒ همان جملهٔ ساده؛ جواب نداد ⇒ [reason]؛ بقیه هیچ */
  fun pulseNote(state: ServerPulse.State, note: String?): String? = when {
    note.isNullOrBlank() -> null
    state == ServerPulse.State.UP -> note.takeIf { plain(it) }
    state == ServerPulse.State.DOWN -> reason(note).ifBlank { null }
    else -> null
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
   *  SYN-12: سرخطِ نوارِ وضعیتِ کامپیوتر برای شکستِ بی هشدار (حکمِ موتور) — همان `dotHead` ِ وب: «دوباره می‌کوشد»،
   *  «به سرور نمی‌رسیم» یا «سرور الان جواب نمی‌دهد» به‌جای «در صف» ِ زرد؛ بی شکست یا سرخ/خاکستری ⇒ `null` (همان پیشین)
   */
  fun quietHead(outage: SyncOutage.Kind?): String? = when (outage) {
    SyncOutage.Kind.RETRYING -> StrSettings.diagRetryingTitle
    SyncOutage.Kind.UNREACHABLE -> StrSettings.diagUnreachableTitle
    SyncOutage.Kind.SERVER -> StrSettings.diagServerTitle
    else -> null
  }

  /**
   *  ترتیب: سرورِ عقب‌مانده (چیزی نمی‌رود تا به‌روز شود) ⇐ نبودنِ دکان ⇐ حکمِ موتور (SYN-12) ⇐ نتِ دستگاه ⇐
   *  خطای آخرین دور ⇐ تغییرهای ردشده. `detail` همان متنِ خام **فقط برای پشتیبانی** است (`SupportDiagAndroid`) و
   *  هرگز در صفحه نشان داده نمی‌شود. سرورِ گرفتار (حکمِ موتور) زرد است، نه سرخ: تقصیرِ این دستگاه نیست و خودش دوباره می‌رود.
   */
  /** «از پشتیبانی بخواه» ِ یک گره: کدِ `SupportProblem` و واقعیت‌های مجاز (نامکِ صفحه، کدِ کوتاه، شمار) — هرگز متنِ خام */
  data class Ask(val code: String, val facts: Map<String, Any>)

  /** فقط گرهی که کارش با پشتیبانی است: دکانِ حساب پیدا نمی‌شود، یا سرور تغییرهایی را نپذیرفت */
  fun askOf(d: Diagnosis, dropped: Int): Ask? = when (d.problem) {
    Problem.NO_SHOP -> Ask(ir.vil3ntec.tohid.core.support.SupportProblem.SYNC_NO_SHOP, mapOf("screen" to "settings:sync", "syncError" to "no_shop"))
    Problem.REJECTED -> Ask(ir.vil3ntec.tohid.core.support.SupportProblem.SYNC_REFUSED, mapOf("screen" to "settings:sync", "syncError" to "rejected", "rejected" to dropped))
    else -> null
  }

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
      Problem.REJECTED -> Diagnosis(problem, StrSyncPlain.rejectedTitle(dropped), StrNav.droppedChanges(dropped), StrSettings.diagRejectedDo, V3Tone.WARNING, null)
      Problem.UNKNOWN -> Diagnosis(problem, StrSettings.diagUnknownTitle, StrSettings.diagUnknownBody, StrSettings.diagUnknownDo, V3Tone.ERROR, detail)
    }
  }
}
