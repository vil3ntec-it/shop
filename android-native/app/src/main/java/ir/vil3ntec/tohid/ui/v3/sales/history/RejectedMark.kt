package ir.vil3ntec.tohid.ui.v3.sales.history

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrConflictInbox
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.sync.ConflictInbox
import ir.vil3ntec.tohid.data.RejectedDocs
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.shell.ConflictInboxLive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 *  نشانِ «سرور نپذیرفت: <دلیل>» روی فاکتوری که **همین دستگاه** ساخت و سرور رد کرد (نه فقط در لاگ و «حالِ همگام‌سازی»):
 *  ردیفِ تاریخچه و بالای فاکتور. دلیل از دفترِ کنارِ صفِ همین دستگاه (`RejectedDocs`)؛ اگر صاحب/مدیر از صندوقِ سندهای
 *  ردشده بست، حالِ سرور (`GET /shop/conflict-inbox/mine`) جای آن می‌نشیند: «دوباره ثبت کرد» / «اصلاح کرد» / «کنار گذاشت».
 *
 *  ⛔ دستگاهی که هیچ چیزِ ردشده‌ای ندارد هیچ نمی‌پرسد ([ConflictInbox.needsMine])؛ سرورِ کهنه (۴۰۴) ⇒ همان دلیلِ محلی.
 */
object RejectedLive {
  const val MINE_MS = 2 * 60_000L

  val marks = mutableStateOf<Map<String, ConflictInbox.Marker>>(emptyMap())
  @Volatile private var mineAt = 0L
  @Volatile private var mine: List<ConflictInbox.Mine> = emptyList()
  private val lock = Mutex()

  suspend fun refresh(context: Context, force: Boolean = false): Unit = lock.withLock {
    val local = withContext(Dispatchers.IO) { RejectedDocs.local(context) }
    if (!ConflictInbox.needsMine(local)) { marks.value = emptyMap(); return@withLock }
    val t = System.currentTimeMillis()
    if (force || t - mineAt >= MINE_MS) {
      when (val r = ConflictInboxLive.repo(context)?.mine()) {
        is ApiResult.Success -> { mine = r.value; mineAt = t }
        is ApiResult.Failure -> if (!r.error.retryable) { mine = emptyList(); mineAt = t }
        null -> Unit
      }
    }
    marks.value = ConflictInbox.markers(local, mine)
  }

  /** متن و رنگِ نشان */
  fun text(m: ConflictInbox.Marker): Pair<String, V3Tone> = when (m.status) {
    ConflictInbox.RESUBMITTED -> StrConflictInbox.markResubmitted to V3Tone.SUCCESS
    ConflictInbox.FIXED -> StrConflictInbox.markFixed to V3Tone.SUCCESS
    ConflictInbox.DISMISSED -> StrConflictInbox.markDismissed(m.note.ifBlank { "—" }) to V3Tone.WARNING
    else -> (if (m.returnDoc) StrConflictInbox.serverRejectedReturn(StrConflictInbox.reason(m.reason))
      else StrConflictInbox.serverRejected(StrConflictInbox.reason(m.reason))) to V3Tone.ERROR
  }
}

/** تا صفحه جلوی چشم است، هر چند دقیقه نشان‌ها تازه می‌شوند (خواندنِ دیسک؛ شبکه فقط با چیزِ ردشده) */
@Composable
internal fun RejectedWatch(force: Boolean = false) {
  val context = LocalContext.current
  LaunchedEffect(Unit) {
    var first = true
    while (true) {
      runCatching { RejectedLive.refresh(context, force = force && first) }
      first = false
      delay(RejectedLive.MINE_MS / 2)
    }
  }
}

/** یک خطِ کوچک زیرِ ردیفِ تاریخچه */
@Composable
internal fun RejectedLine(saleId: String) {
  val all by RejectedLive.marks
  val m = all[saleId] ?: return
  val (text, tone) = RejectedLive.text(m)
  Text(text, style = V3Type.footnote, color = PhoneV3Roles.tone(PhoneV3.colors, tone).fg, modifier = Modifier.padding(start = 64.dp, end = 16.dp, bottom = 8.dp))
}

/** پیامِ بالای فاکتور */
@Composable
internal fun RejectedBanner(saleId: String) {
  val all by RejectedLive.marks
  val m = all[saleId] ?: return
  val (text, tone) = RejectedLive.text(m)
  V3Message(tone, text, modifier = Modifier.padding(bottom = 8.dp))
}
