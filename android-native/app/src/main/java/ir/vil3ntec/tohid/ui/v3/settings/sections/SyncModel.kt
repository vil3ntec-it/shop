package ir.vil3ntec.tohid.ui.v3.settings.sections

import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.i18n.StrSyncPlain
import ir.vil3ntec.tohid.core.sync.SyncOutage
import ir.vil3ntec.tohid.data.SupportDiag
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

  /** @param failed گرهِ سرخِ موتور (`diagnose(...).tone == ERROR`) — شکستِ Sync v1 در `AutoSync.health` دیده نمی‌شود */
  fun headline(health: AutoSync.Health, running: Boolean, pending: Int, failed: Boolean = false): Line = when {
    failed -> Line(StrNav.lastAttemptFailed, V3Tone.ERROR)
    else -> headlineOf(health, running, pending)
  }

  private fun headlineOf(health: AutoSync.Health, running: Boolean, pending: Int): Line = when (health) {
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

  /**
   *  گرهِ همگام‌سازی به زبانِ آدم — چه شده، چه نگه داشته شده، کاربر چه کند. e-newinstall: [NOT_SIGNED_IN] (مهمان، «ادامه بدون
   *  حساب») و [REFUSED] (سرور با پیامِ سادهٔ خودش رد کرد — همان پیام روی کارت، نه «همگام‌سازی کامل نشد»).
   */
  enum class Problem { NONE, OFFLINE, SIGN_IN, NOT_SIGNED_IN, SERVER, SERVER_BEHIND, NO_SHOP, LOCAL, REJECTED, REFUSED, UNKNOWN, UNREACHABLE, RETRYING }

  /** دکمهٔ کارتِ گره — کاری که کاربر همان‌جا می‌کند */
  enum class Fix { NONE, SIGN_IN, SHOP }

  data class Diagnosis(
    val problem: Problem,
    val title: String,
    val body: String,
    val action: String,
    val tone: V3Tone,
    val detail: String?,
    val fix: Fix = Fix.NONE,
    /** کدِ ماشینیِ همان شکست (`SyncStatus.errorCode`) — فقط برای «جزئیات» و پشتیبانی */
    val code: String = "",
  )

  /** کدهای سرور که یعنی «این حساب دکانی ندارد / دکانِ این نشست نیست» — `ApiFailure.code` (`middleware/auth.js`، `sync-v1-auth.js`) */
  private val NO_SHOP_CODES = setOf("no_shop", "shop_not_found", "no_account")

  /** کدهایی که با ورودِ دوباره درست می‌شوند (نشست، دستگاهِ این نشست، نشستِ بخشِ دیگر) */
  private val SIGN_IN_CODES = setOf("session_expired", "invalid_token", "no_session", "session_required", "device_mismatch", "app_mismatch")

  /** نشانه‌های متنیِ «دکان ندارید» — برای `lastError` ِ ساخت‌های پیش از کدِ ماندگار (`SyncStateV1.lastErrorCode`) */
  private val NO_SHOP_HINTS = listOf("دکانی ثبت نشده", "دکان بسازید", "دکانتان را بسازید", "دکان پیدا نشد")

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
      NO_SHOP_HINTS.any { it in raw } -> Problem.NO_SHOP
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
      Problem.NO_SHOP -> StrSettings.diagNoShopTitle
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

  /** کدِ ماشینیِ شکست ⇒ گره؛ `null` ⇒ کد چیزی نمی‌گوید (حکمِ موتور و نشانه‌های متنی) */
  fun fromCode(code: String): Problem? = when (code.trim()) {
    "" -> null
    in NO_SHOP_CODES -> Problem.NO_SHOP
    in SIGN_IN_CODES -> Problem.SIGN_IN
    else -> null
  }

  /** «از پشتیبانی بخواه» ِ یک گره: کدِ `SupportProblem` و واقعیت‌های مجاز (نامکِ صفحه، کدِ کوتاه، شمار) — هرگز متنِ خام */
  data class Ask(val code: String, val facts: Map<String, Any>)

  /** فقط گرهی که کارش با پشتیبانی است: دکانِ حساب پیدا نمی‌شود، یا سرور تغییرهایی را نپذیرفت */
  fun askOf(d: Diagnosis, dropped: Int): Ask? = when (d.problem) {
    Problem.NO_SHOP -> Ask(ir.vil3ntec.tohid.core.support.SupportProblem.SYNC_NO_SHOP, mapOf("screen" to "settings:sync", "syncError" to "no_shop"))
    Problem.REJECTED -> Ask(ir.vil3ntec.tohid.core.support.SupportProblem.SYNC_REFUSED, mapOf("screen" to "settings:sync", "syncError" to "rejected", "rejected" to dropped))
    Problem.REFUSED -> Ask(ir.vil3ntec.tohid.core.support.SupportProblem.SYNC_REFUSED, mapOf("screen" to "settings:sync", "syncError" to "refused"))
    else -> null
  }

  /**
   *  ترتیب: بی نشست (مهمان) ⇐ سرورِ عقب‌مانده (چیزی نمی‌رود تا به‌روز شود) ⇐ نبودنِ دکان (کدِ سرور، نشانهٔ متن یا
   *  `AutoSync`) ⇐ کدِ نشست ⇐ حکمِ موتور (SYN-12) ⇐ نتِ دستگاه ⇐ خطای آخرین دور ⇐ تغییرهای ردشده. سرورِ گرفتار
   *  (حکمِ موتور) زرد است، نه سرخ: تقصیرِ این دستگاه نیست و خودش دوباره می‌رود.
   *
   *  e-newinstall: ۴۰۳ ِ «برای این حساب دکانی ثبت نشده» پیش‌تر یا «نشست تمام شد» دیده می‌شد (حکمِ AUTH ِ هر ۴۰۳) یا پس از
   *  بازگشایی «همگام‌سازی کامل نشد» (هیچ نشانه‌ای نخورد) — و `AutoSync.needsShop` از وقتی دفتر با Sync v1 می‌رود هرگز روشن
   *  نمی‌شود. حالا کدِ ماندگار ([code]) و نشانهٔ متن «دکان» را می‌گویند؛ ردِ دیگرِ سرور با پیامِ سادهٔ فارسیِ خودش
   *  ([Problem.REFUSED])، و ۴۰۳ ِ کددار که نشست نیست دیگر «دوباره وارد شوید» نمی‌گوید.
   *
   *  `detail` همان متنِ خام است: برای پشتیبانی فرستاده می‌شود (`SupportDiagAndroid`) و فقط با زدنِ «جزئیات»، پوشانده
   *  ([detailsText])، در برگه دیده می‌شود — هرگز روی خودِ کارت.
   *
   *  @param signedIn نشستِ حساب هست؛ `false` ⇒ [Problem.NOT_SIGNED_IN] با دکمهٔ ورود
   *  @param queued تغییرهای در صف (برای متنِ مهمان)
   */
  fun diagnose(
    error: String,
    holding: Boolean,
    dropped: Int,
    needsShop: Boolean,
    server: ServerPulse.State,
    outage: SyncOutage.Kind? = null,
    code: String = "",
    signedIn: Boolean = true,
    queued: Int = 0,
  ): Diagnosis {
    val raw = error.trim()
    val byCode = if (raw.isNotEmpty()) fromCode(code) else null
    //  حکمِ AUTH ِ موتور برای هر ۴۰۱/۴۰۳ است؛ ۴۰۳ ِ کددارِ دیگر (اشتراک، نقش، دستگاهِ بسته) نشست نیست
    val judged = if (raw.isNotEmpty()) fromOutage(outage)?.takeUnless { it == Problem.SIGN_IN && code.isNotBlank() && byCode == null } else null
    val problem = when {
      !signedIn -> Problem.NOT_SIGNED_IN
      holding -> Problem.SERVER_BEHIND
      needsShop || byCode == Problem.NO_SHOP -> Problem.NO_SHOP
      byCode != null -> byCode
      judged != null -> judged
      server == ServerPulse.State.NO_NET && raw.isNotEmpty() -> Problem.OFFLINE
      raw.isNotEmpty() -> classify(raw).let { if (it == Problem.UNKNOWN && plain(raw)) Problem.REFUSED else it }
      dropped > 0 -> Problem.REJECTED
      else -> Problem.NONE
    }
    val detail = raw.takeIf { it.isNotEmpty() && problem != Problem.SERVER_BEHIND && problem != Problem.NOT_SIGNED_IN }
    fun d(title: String, body: String, action: String, tone: V3Tone, fix: Fix = Fix.NONE, det: String? = detail) =
      Diagnosis(problem, title, body, action, tone, det, fix, code.trim())
    return when (problem) {
      Problem.NONE -> d(StrNav.syncAllOnServer, "", "", V3Tone.SUCCESS, det = null)
      Problem.NOT_SIGNED_IN -> d(StrSettings.diagGuestTitle, if (queued > 0) StrSettings.diagGuestBody(queued) else StrSettings.diagGuestBodyEmpty, StrSettings.diagGuestDo, V3Tone.WARNING, Fix.SIGN_IN)
      Problem.OFFLINE -> d(StrSettings.diagOfflineTitle, StrSettings.diagOfflineBody, StrSettings.diagOfflineDo, V3Tone.WARNING)
      Problem.SIGN_IN -> d(StrSettings.diagSignInTitle, StrSettings.diagSignInBody, StrSettings.diagSignInDo, V3Tone.ERROR, Fix.SIGN_IN)
      Problem.SERVER -> d(StrSettings.diagServerTitle, StrSettings.diagServerBody, StrSettings.diagServerDo, if (judged == Problem.SERVER) V3Tone.WARNING else V3Tone.ERROR)
      Problem.UNREACHABLE -> d(StrSettings.diagUnreachableTitle, StrSettings.diagUnreachableBody, StrSettings.diagUnreachableDo, V3Tone.WARNING)
      Problem.RETRYING -> d(StrSettings.diagRetryingTitle, StrSettings.diagRetryingBody, StrSettings.diagRetryingDo, V3Tone.INFO)
      Problem.SERVER_BEHIND -> d(StrSettings.diagBehindTitle, StrNav.serverBehind, StrSettings.diagBehindDo, V3Tone.WARNING, det = null)
      Problem.NO_SHOP -> d(StrSettings.diagNoShopTitle, StrSettings.diagNoShopBody, StrSettings.diagNoShopDo, V3Tone.WARNING, Fix.SHOP)
      Problem.LOCAL -> d(StrSettings.diagLocalTitle, StrSettings.diagLocalBody, StrSettings.diagLocalDo, V3Tone.ERROR)
      Problem.REJECTED -> d(StrSyncPlain.rejectedTitle(dropped), StrNav.droppedChanges(dropped), StrSettings.diagRejectedDo, V3Tone.WARNING, det = null)
      //  پیامِ سادهٔ خودِ سرور (`plain`: بی نشانی، بی کد و عددِ لاتین) همان بدنهٔ کارت است
      Problem.REFUSED -> d(StrSettings.diagRefusedTitle, raw + "\n" + StrSettings.diagUnknownBody, StrSettings.diagRefusedDo, V3Tone.ERROR)
      Problem.UNKNOWN -> d(StrSettings.diagUnknownTitle, StrSettings.diagUnknownBody, StrSettings.diagUnknownDo, V3Tone.ERROR)
    }
  }

  /**
   *  متنِ برگهٔ «جزئیات» (فقط با زدن، `SyncScreen`) — همان چیزی که پشتیبانی لازم دارد: نامِ گره، کد، متنِ خامِ خطا،
   *  صف و ردشده‌ها و آخرین موفق. ⛔ نشانیِ سرور پوشانده (`SupportDiag.clean`: `api.•••`، IP ⇒ `•••`)؛ نسخهٔ سرور، Schema و
   *  راهِ API هرگز (آن‌ها فقط در گزارشِ فرستاده‌شده‌اند، [facts]).
   */
  fun detailsText(d: Diagnosis, queued: Int, dropped: Int, lastOkAt: Long, now: Long): String = buildString {
    append("sync: ").append(d.problem.name.lowercase())
    if (d.code.isNotBlank()) append(" · code: ").append(SupportDiag.clean(d.code, 64))
    append('\n')
    d.detail?.takeIf { it.isNotBlank() }?.let { append(SupportDiag.clean(it, DETAIL_MAX)).append('\n') }
    append("queued: ").append(queued).append(" · dropped: ").append(dropped).append('\n')
    append("lastOk: ").append(if (lastOkAt > 0) lastOkAt.toString() else "-").append('\n')
    append("at: ").append(now)
  }

  private const val DETAIL_MAX = 1200
}
