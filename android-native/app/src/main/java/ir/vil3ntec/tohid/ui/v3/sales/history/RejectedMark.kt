package ir.vil3ntec.tohid.ui.v3.sales.history

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrConflictInbox
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.sync.ConflictInbox
import ir.vil3ntec.tohid.core.sync.RejectedSettle
import ir.vil3ntec.tohid.data.RejectedDocs
import ir.vil3ntec.tohid.data.SetAside
import ir.vil3ntec.tohid.data.SetAsideDoc
import ir.vil3ntec.tohid.data.SetAsideSync
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText
import ir.vil3ntec.tohid.ui.v3.V3Section
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.sync.v1.SyncV1Worker
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

  suspend fun refresh(context: Context, force: Boolean = false, settled: Set<String> = emptySet()): Unit = lock.withLock {
    val local = withContext(Dispatchers.IO) { RejectedDocs.local(context, settled) }
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
    //  صاحب «کنار گذاشت»/«اصلاح کرد» و هنوز روی این دفتر ننشسته ⇒ همین حالا یک دورِ همگام‌سازی (`data/SetAside`)
    val ids = local.mapTo(HashSet()) { it.opId }
    if (mine.any { m -> RejectedSettle.actionable(m) && (m.opId in ids || m.settle?.ops?.any { it.id in ids } == true) }) {
      SetAsideSync.due = true
      runCatching { SyncV1Worker.now(context) }
    }
  }

  /** عنوانِ سندِ کنارگذاشته: «فاکتورِ B1-41» / «برگشتِ …» (از ردیف‌های نگه‌داشته) */
  fun setAsideTitle(doc: SetAsideDoc): String {
    val sale = (doc.rows["sales"] as? JsonArray)?.firstNotNullOfOrNull { it as? JsonObject }
    val no = sale?.let { s ->
      val n = (s["invoiceNumber"] as? JsonPrimitive)?.content.orEmpty()
      val series = (s["series"] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
      if (series.isNotEmpty() && n.isNotEmpty()) "$series-$n" else n
    }.orEmpty().ifEmpty { doc.saleId.takeLast(6) }
    return if (doc.table == "returns") StrConflictInbox.returnDoc(no) else StrConflictInbox.saleDoc(no)
  }

  /** مبلغِ سند (فاکتور: مبلغِ نهایی؛ برگشت: جمعِ برگشت) — فقط نمایش، هیچ جمعی از آن ساخته نمی‌شود */
  fun setAsideAmount(doc: SetAsideDoc): Double? {
    fun sum(table: String, field: String): Double? = (doc.rows[table] as? JsonArray)?.mapNotNull { r ->
      ((r as? JsonObject)?.get(field) as? JsonPrimitive)?.content?.toDoubleOrNull()
    }?.takeIf { it.isNotEmpty() }?.sum()
    return if (doc.table == "returns") sum("returns", "amount") else sum("sales", "finalTotal")
  }

  /** «سرور نپذیرفت — کنار گذاشته شد / اصلاح شد: <دلیل>» */
  fun setAsideLine(doc: SetAsideDoc): String {
    val note = doc.note.ifBlank { StrConflictInbox.reason(doc.reason) }
    return if (doc.status == ConflictInbox.FIXED) StrConflictInbox.setAsideFixed(note) else StrConflictInbox.setAsideDismissed(note)
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
internal fun RejectedWatch(force: Boolean = false, data: ShopData? = null) {
  val context = LocalContext.current
  val settled = remember(data?.setAside) { data?.let(SetAside::settledOps).orEmpty() }
  val now by rememberUpdatedState(settled)
  LaunchedEffect(Unit) {
    var first = true
    while (true) {
      runCatching { RejectedLive.refresh(context, force = force && first, settled = now) }
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

/**
 *  «سندهایی که سرور نپذیرفت» — سندهای کنارگذاشتهٔ همین دستگاه (`ShopData.setAside`)، بیرون از هر جمعِ بالا: فقط دیدن،
 *  با همان دلیلِ صاحب. ردیف‌های کامل در خودِ دفتر برای سابقه می‌مانند.
 */
@Composable
internal fun SetAsideSection(docs: List<SetAsideDoc>) {
  if (docs.isEmpty()) return
  val c = PhoneV3.colors
  val warn = PhoneV3Roles.tone(c, V3Tone.WARNING).fg
  V3Section(StrConflictInbox.setAsideTitle, footer = StrConflictInbox.setAsideIntro) {
    docs.sortedByDescending { maxOf(it.resolvedAt, it.settledAt) }.forEach { doc ->
      Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        val amount = RejectedLive.setAsideAmount(doc)?.let { " · " + PhoneSaleText.amount(it) }.orEmpty()
        Text(RejectedLive.setAsideTitle(doc) + amount, style = V3Type.body, color = c.inkMuted, textDecoration = TextDecoration.LineThrough)
        Text(RejectedLive.setAsideLine(doc), style = V3Type.footnote, color = warn)
        if (doc.kept.isNotEmpty()) Text(StrConflictInbox.setAsideKept(plain(doc.kept.size)), style = V3Type.footnote, color = c.inkMuted)
      }
    }
  }
}
