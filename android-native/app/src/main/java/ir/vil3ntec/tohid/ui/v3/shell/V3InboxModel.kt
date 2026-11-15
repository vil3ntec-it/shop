package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.runtime.mutableStateOf
import ir.vil3ntec.tohid.core.sync.V3Inbox
import ir.vil3ntec.tohid.core.sync.V3InboxClient
import ir.vil3ntec.tohid.core.sync.V3InboxOutbox
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 *  SYN-07 / S28 — حالِ صفحهٔ «تعارض‌های همگام‌سازی» (بی هیچ چیزِ پلتفرمی؛ مرزِ سکو `V3InboxAndroid.kt`): فهرستِ باز و
 *  بسته از سرور، صفِ تصمیم‌ها و پیامِ پس از هر تصمیم.
 *
 *  ⛔ [on] فقط وقتی سرور کلیدِ `inbox` داد (پرچمِ `shop.sync_v3_inbox`)؛ وگرنه فهرست خالی و تا [offMs] ِ بعد پرسیده نمی‌شود.
 *  ⛔ هر تصمیم اول در صف (دیسک) و بعد فرستاده می‌شود ([decide])؛ بی‌اینترنت در صف می‌ماند و ردیف «در صف» نشان داده می‌شود.
 *  ⛔ سرور تنها داور است: ردیف فقط با پاسخِ سرور بسته می‌شود؛ «پیش‌تر بسته شده بود» ⇒ فهرست از نو.
 */
class V3InboxModel(
  private val client: V3InboxClient,
  /** شناسهٔ v3 ِ همین دستگاه (برای «مالِ من / مالِ دیگری») */
  val me: String,
  private val lock: Mutex,
  private val now: () -> Long,
  private val offMs: Long = 10 * 60_000L,
  /** کمترین فاصلهٔ دو پرسش بی [refresh] ِ `force` (نوار و صفحه با هم، و ساعتِ آزمون که جلو نمی‌رود) */
  private val gapMs: Long = 30_000L,
) {
  /** پیامِ یک تعارض پس از فرستادن */
  data class Note(val fate: V3InboxOutbox.Fate, val duplicate: Boolean = false, val code: String = "")

  val open = mutableStateOf<List<V3Inbox.Conflict>>(emptyList())
  val closed = mutableStateOf<List<V3Inbox.Conflict>?>(null)
  val on = mutableStateOf(false)
  val merge = mutableStateOf(false)
  val failed = mutableStateOf(false)
  val queued = mutableStateOf<List<V3InboxOutbox.Entry>>(emptyList())
  val notes = mutableStateOf<Map<String, Note>>(emptyMap())
  @Volatile private var offUntil = 0L
  @Volatile private var listedAt = 0L
  @Volatile private var listed = false

  /** شمارِ نوارِ پوسته: بازهایی که هنوز تصمیمشان در صف نیست */
  fun waiting(): Int = open.value.count { c -> V3InboxOutbox.pending(queued.value, c.id) == null }

  fun pending(id: String): V3InboxOutbox.Entry? = V3InboxOutbox.pending(queued.value, id)

  /** صف را بفرست و فهرستِ باز را از نو بگیر؛ [force] پنجرهٔ «خاموش» را نادیده می‌گیرد */
  suspend fun refresh(force: Boolean = false) {
    val t = now()
    if (!force && (t < offUntil || (listed && t - listedAt < gapMs))) return
    listed = true; listedAt = t
    send()
    val l = client.list()
    val page = l.page
    when {
      page != null && page.on -> {
        open.value = page.items.filter { it.open }; merge.value = page.merge; on.value = true; failed.value = false
      }
      //  پرچمِ خاموش (بی `inbox`)، v3 ِ خاموشِ سرور (۴۰۴) یا نقشِ بی اجازه (۴۰۳) ⇒ هیچ چیزی، تا پنجرهٔ بعد
      page != null || l.status == 403 || l.status == 404 -> { clear(); offUntil = now() + offMs }
      else -> failed.value = true
    }
    queued.value = client.queued()
  }

  /** بسته‌ها (`status=all`) */
  suspend fun loadClosed() {
    val page = client.list(all = true).page
    closed.value = page?.takeIf { it.on }?.items?.filterNot { it.open }.orEmpty()
  }

  /** تصمیم ⇒ صف (دیسک) ⇒ فرستادن؛ بی‌اینترنت در صف می‌ماند */
  suspend fun decide(d: V3Inbox.Decision) {
    lock.withLock { client.decide(d) }
    queued.value = client.queued()
    send()
  }

  private suspend fun send() {
    val sent = lock.withLock { client.flush() }
    if (sent.isEmpty()) return
    val n = HashMap(notes.value)
    var reload = false
    for (s in sent) {
      if (s.fate == V3InboxOutbox.Fate.RETRY) continue
      n[s.entry.conflictId] = Note(s.fate, s.duplicate, s.code)
      when (s.fate) {
        V3InboxOutbox.Fate.DONE -> {
          open.value = open.value.filterNot { it.id == s.entry.conflictId }
          s.conflict?.let { c -> closed.value = closed.value?.let { listOf(c) + it.filterNot { x -> x.id == c.id } } }
        }
        V3InboxOutbox.Fate.CLOSED -> { open.value = open.value.filterNot { it.id == s.entry.conflictId }; reload = true }
        else -> Unit
      }
    }
    notes.value = n
    queued.value = client.queued()
    if (reload) closed.value = null
  }

  private fun clear() {
    open.value = emptyList(); closed.value = null; on.value = false; merge.value = false; failed.value = false
  }
}
