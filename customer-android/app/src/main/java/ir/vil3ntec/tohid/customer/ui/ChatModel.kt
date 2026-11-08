package ir.vil3ntec.tohid.customer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.shopper.ChatAct
import ir.vil3ntec.tohid.core.shopper.ChatChanges
import ir.vil3ntec.tohid.core.shopper.ChatConversation
import ir.vil3ntec.tohid.core.shopper.ChatDeleteScope
import ir.vil3ntec.tohid.core.shopper.ChatDeletes
import ir.vil3ntec.tohid.core.shopper.ChatFailure
import ir.vil3ntec.tohid.core.shopper.ChatMessage
import ir.vil3ntec.tohid.core.shopper.ChatOutItem
import ir.vil3ntec.tohid.core.shopper.ChatOutbox
import ir.vil3ntec.tohid.core.shopper.ChatPhoto
import ir.vil3ntec.tohid.core.shopper.ChatReceiptNeed
import ir.vil3ntec.tohid.core.shopper.ChatThread
import ir.vil3ntec.tohid.core.shopper.PhoneTicket
import ir.vil3ntec.tohid.core.shopper.ShopperApi
import ir.vil3ntec.tohid.core.shopper.ShopperError
import ir.vil3ntec.tohid.core.shopper.ShopperKeyValue
import ir.vil3ntec.tohid.core.shopper.ShopperResult
import ir.vil3ntec.tohid.core.shopper.ShopperText
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 *  گفت‌وگو با دکان (CHAT-02/06) — حالِ صفحه، long-poll و صفِ بی‌اینترنت؛ بی android.* (آیفون هم همین را می‌گیرد).
 *  قاعده‌ها در core است (`ChatOutbox`، `ChatThread`)؛ این‌جا فقط ترتیبِ کارها.
 *
 *  ⛔ هویت همان بلیتِ شمارهٔ تأییدشده ([ticket])؛ ۴۰۱ ⇒ [ticketLost] و پیام‌ها در صف می‌مانند تا تأییدِ دوباره.
 *  [pollWaitMs] مهلتِ long-poll ِ سرور (۰ ⇒ فقط یک بار خواندن؛ آزمون).
 */
class ChatModel(
  private val api: ShopperApi,
  private val kv: ShopperKeyValue,
  private val scope: CoroutineScope,
  private val io: CoroutineDispatcher,
  private val clock: () -> Long,
  private val ticket: () -> PhoneTicket?,
  private val ticketLost: () -> Unit,
  private val pollWaitMs: Int = 20_000,
  private val rand: () -> Double = { Random.nextDouble() },
) {
  var thread: Load<ChatThread> by mutableStateOf(Load.Idle)
    private set
  var outbox: List<ChatOutItem> by mutableStateOf(ChatOutbox.load(kv.get(ChatOutbox.KEY)))
    private set
  var draft: String by mutableStateOf("")
  var note: String? by mutableStateOf(null)
  var preparing: Boolean by mutableStateOf(false)
  var needVerify: Boolean by mutableStateOf(false)
    private set
  /** با هر عکسِ تازه‌رسیده بالا می‌رود تا صفحه دوباره بکشد */
  var mediaRev: Int by mutableStateOf(0)
    private set
  /** نوارِ کارهای یک پیام (CHAT-08)؛ null ⇒ بسته */
  var act: ChatAct? by mutableStateOf(null)
    private set
  /** «پاک شد» پس از حذف */
  var deletedNote: String? by mutableStateOf(null)
    private set

  private val photos = HashMap<String, ChatPhoto>()
  private val media = LinkedHashMap<String, ByteArray>()
  private val loading = HashSet<String>()
  private var at: Triple<String, Long?, String>? = null
  private var pollJob: Job? = null
  private var wakeJob: Job? = null
  private var draining = false
  /**
   *  برنامه پیش است (Activity در RESUMED: `onResume`..`onPause`؛ آیفون: `scenePhase == .active`) — با صفحهٔ بازِ گفت‌وگو
   *  یعنی پیام‌ها دیده می‌شوند ⇒ «خواندم»؛ وگرنه فقط «رسید» (CHAT-04). برنامه با پیش آمدن ساخته می‌شود.
   */
  private var foreground = true
  private var asked: ChatReceiptNeed? = null
  private val deletes = ChatDeletes()

  private fun putOutbox(list: List<ChatOutItem>) {
    outbox = list
    runCatching { kv.put(ChatOutbox.KEY, ChatOutbox.dump(list)) }
  }

  private fun current(): ChatThread? = thread.valueOrNull()

  /** گفت‌وگوی همین سفارش ([orderNo]) یا گفت‌وگوی عمومیِ دکان (null) */
  fun open(slug: String, orderNo: Long?, name: String) {
    pollJob?.cancel()
    at = Triple(slug, orderNo, name)
    note = null
    val tk = ticket()
    if (tk == null) {
      needVerify = true
      thread = Load.Idle
      return
    }
    needVerify = false
    thread = Load.Busy
    scope.launch {
      when (val r = withContext(io) { api.chatOpen(slug, tk.ticket, orderNo, name) }) {
        is ShopperResult.Ok -> {
          if (at?.first != slug) return@launch
          thread = Load.Ready(ChatThread(r.value))
          poll(slug, r.value.id)
          drain()
        }
        is ShopperResult.Fail -> if (ChatOutbox.failureOf(r.error) == ChatFailure.VERIFY) lost() else thread = Load.Failed(r.error)
      }
    }
  }

  fun retryOpen() = at?.let { open(it.first, it.second, it.third) }

  /** صفحه بسته شد: long-poll می‌ایستد؛ صف می‌ماند و هر بار که برنامه پیش آمد می‌رود */
  fun leave() {
    pollJob?.cancel()
    at = null
    act = null
    deletedNote = null
  }

  /** برنامه پیش آمد/پس رفت؛ پیش آمدن با صفحهٔ باز ⇒ «خواندم» تا آخرین پیامِ رسیده (همان `visibilitychange` ِ وب) */
  fun visible(on: Boolean) {
    foreground = on
    if (on) receipts()
  }

  private fun poll(slug: String, convId: String) {
    pollJob?.cancel()
    pollJob = scope.launch {
      var fails = 0
      var wait = 0
      while (isActive) {
        val tk = ticket() ?: break
        val cur = current()?.takeIf { it.conversation.id == convId } ?: break
        when (val r = withContext(io) { api.chatChanges(slug, tk.ticket, convId, cur.cursor, wait) }) {
          is ShopperResult.Ok -> {
            fails = 0
            merge(slug, r.value)
            if (r.value.hasMore) continue
          }
          is ShopperResult.Fail -> {
            if (ChatOutbox.failureOf(r.error) == ChatFailure.VERIFY) { lost(); break }
            if (ChatOutbox.failureOf(r.error) == ChatFailure.FAIL) { note = ShopperText.error(r.error); break }
            delay(ChatOutbox.delayMs(++fails, rand()))
          }
        }
        if (pollWaitMs <= 0) break
        wait = pollWaitMs
      }
    }
  }

  private fun merge(slug: String, c: ChatChanges) {
    val cur = current() ?: return
    if (cur.conversation.id != c.conversation.id) return
    val next = cur.merge(c)
    thread = Load.Ready(next)
    //  جوابِ گم‌شده: سرور پیام را دارد ⇒ از صف بیرون، بی ارسالِ دوم
    val arrived = outbox.filter { it.convId == c.conversation.id && next.arrived(it.clientId) }
    if (arrived.isNotEmpty()) putOutbox(outbox.filter { it !in arrived })
    receipts()
  }

  /**
   *  «رسید» همیشه، «خواندم» فقط وقتی صفحه دیده می‌شود (`ChatThread.receiptNeed`، CHAT-04)؛ یکی در راه ⇒ تکرار نه.
   *  پاسخِ سرور (رسیدهای واقعی، فقط جلو) در رشته می‌نشیند.
   */
  private fun receipts() {
    val cur = current() ?: return
    val slug = at?.first ?: return
    val tk = ticket() ?: return
    val need = cur.receiptNeed(foreground, asked) ?: return
    asked = need
    val convId = cur.conversation.id
    scope.launch {
      val r = withContext(io) { api.chatReceipts(slug, tk.ticket, convId, need.deliveredSeq, need.readSeq) }
      if (asked == need) asked = null
      r.valueOrNull()?.let(::conversation)
    }
  }

  /** گفت‌وگوی تازه از سرور (رسید، «مشکل دارم») — همان رشته، رسیدها فقط جلو */
  private fun conversation(c: ChatConversation) {
    current()?.takeIf { it.conversation.id == c.id }?.let { thread = Load.Ready(it.merge(ChatChanges(c, emptyList(), it.cursor))) }
  }

  fun send() {
    val cur = current() ?: return
    val text = draft.trim()
    if (text.isEmpty()) return
    if (!cur.conversation.canSend) {
      note = ShopperText.chatClosed
      return
    }
    val slug = at?.first ?: return
    putOutbox(ChatOutbox.add(outbox, slug, cur.conversation.id, "text", text, clock()).first)
    draft = ""
    drain()
  }

  /** عکسِ آمادهٔ سکو (چرخیده، بی EXIF، فشرده) ⇒ صف؛ بایت‌ها فقط در حافظه */
  fun sendPhoto(photo: ChatPhoto?) {
    preparing = false
    val cur = current() ?: return
    val slug = at?.first ?: return
    if (photo == null) {
      note = ShopperText.chatPhotoBad
      return
    }
    val (list, item) = ChatOutbox.add(outbox, slug, cur.conversation.id, "image", "", clock(), width = photo.width, height = photo.height)
    photos[item.clientId] = photo
    putOutbox(list)
    drain()
  }

  fun retry(clientId: String) {
    putOutbox(ChatOutbox.retry(outbox, clientId))
    drain()
  }

  fun cancel(clientId: String) {
    photos.remove(clientId)
    putOutbox(ChatOutbox.done(outbox, clientId))
  }

  /** برنامه پیش آمد یا اینترنت برگشت: «منتظرِ اینترنت»ها همین حالا */
  fun online() {
    putOutbox(ChatOutbox.online(outbox))
    drain()
  }

  /** شماره دوباره تأیید شد: پیام‌های «منتظرِ تأیید» می‌روند و گفت‌وگوی باز دوباره خوانده می‌شود */
  fun reverified() {
    needVerify = false
    putOutbox(ChatOutbox.reverified(outbox))
    if (at != null && thread !is Load.Ready) retryOpen()
    drain()
  }

  private fun lost() {
    ticketLost()
    needVerify = true
  }

  /** سرِ صفِ هر گفت‌وگو، یکی‌یکی؛ هر دور یا پیامی می‌رود یا حالش عوض می‌شود ⇒ پایان دارد */
  fun drain() {
    if (draining) return
    draining = true
    scope.launch {
      try {
        while (true) {
          val now = clock()
          val item = outbox.map { it.convId }.distinct().firstNotNullOfOrNull { ChatOutbox.next(outbox, it, now) } ?: break
          val tk = ticket()
          if (tk == null) {
            putOutbox(ChatOutbox.failed(outbox, item.clientId, ShopperError(401, "verification_required"), now, rand()))
            needVerify = true
            continue
          }
          putOutbox(ChatOutbox.patch(outbox, item.clientId) { it.copy(state = ChatOutbox.SENDING) })
          val photo = photos[item.clientId]
          if (item.kind == "image" && item.mediaId == null && photo == null) {
            //  بایت‌ها با بسته شدنِ برنامه رفتند ⇒ این پیام دیگر فرستادنی نیست
            putOutbox(ChatOutbox.done(outbox, item.clientId))
            continue
          }
          val up = withContext(io) { upload(item, photo, tk.ticket) }
          if (up is ShopperResult.Fail) { fail(item, up.error); continue }
          val ready = (up as ShopperResult.Ok).value
          if (ready != item) putOutbox(ChatOutbox.patch(outbox, item.clientId) { it.copy(mediaId = ready.mediaId, thumbId = ready.thumbId) })
          when (val r = withContext(io) { api.chatSend(ready.slug, tk.ticket, ready) }) {
            is ShopperResult.Ok -> {
              photos.remove(ready.clientId)
              putOutbox(ChatOutbox.done(outbox, ready.clientId))
              current()?.takeIf { it.conversation.id == ready.convId }?.let { thread = Load.Ready(it.withMine(r.value.message)) }
            }
            is ShopperResult.Fail -> fail(ready, r.error)
          }
        }
      } finally {
        draining = false
      }
      wake()
    }
  }

  /**
   *  عکس: بایت‌های اصلی و پیش‌نمایش یک بار (تکرارِ همان بایت ⇒ همان رسانهٔ سرور، `reused`). فقط درخواست، بی دست
   *  زدن به حالِ صفحه (روی نخِ [io]). پیش‌نمایشِ نرسیده پیام را نمی‌بندد.
   */
  private fun upload(item: ChatOutItem, photo: ChatPhoto?, tk: String): ShopperResult<ChatOutItem> {
    if (item.kind != "image" || item.mediaId != null || photo == null) return ShopperResult.Ok(item)
    val main = when (val r = api.chatUpload(item.slug, tk, item.convId, photo.main, thumb = false)) {
      is ShopperResult.Ok -> r.value.mediaId
      is ShopperResult.Fail -> return r
    }
    val thumb = photo.thumb?.let { (api.chatUpload(item.slug, tk, item.convId, it, thumb = true) as? ShopperResult.Ok)?.value?.mediaId }
    return ShopperResult.Ok(item.copy(mediaId = main, thumbId = thumb))
  }

  private fun fail(item: ChatOutItem, e: ShopperError) {
    putOutbox(ChatOutbox.failed(outbox, item.clientId, e, clock(), rand()))
    if (ChatOutbox.failureOf(e) == ChatFailure.VERIFY) lost()
  }

  private fun wake() {
    wakeJob?.cancel()
    val next = ChatOutbox.wakeAt(outbox) ?: return
    wakeJob = scope.launch {
      delay((next - clock()).coerceAtLeast(250))
      drain()
    }
  }

  fun issue() = convAction { slug, tk, id -> api.chatIssue(slug, tk, id) }

  // ── حذف (CHAT-08): نوار ⇐ پرسشِ تأیید ⇐ یک درخواست؛ قاعده‌ها در `ChatDeletes` ِ core ──

  /** «حذف برای همه» برای این پیام نشان داده شود؟ (خودم، زنده، پیش از `deleteUntil`، رشتهٔ باز) */
  fun canDeleteForAll(m: ChatMessage): Boolean = current()?.let { deletes.canForAll(it, m, clock()) } == true

  fun actions(m: ChatMessage) {
    if (act?.busy == true) return
    deletedNote = null
    act = ChatAct(m.id)
  }

  /** فهرست (null) یا پرسشِ تأییدِ [scope] */
  fun askDelete(scope: ChatDeleteScope?) {
    val a = act ?: return
    if (a.busy) return
    val cur = current() ?: return
    val m = cur.messages.firstOrNull { it.id == a.messageId } ?: run { act = null; return }
    act = deletes.ask(cur, m, scope, clock())
  }

  fun closeActions() {
    if (act?.busy != true) act = null
  }

  /** «بله، پاک کن»: یک درخواست برای هر (پیام، دامنه)؛ دوبار زدن هیچ */
  fun confirmDelete() {
    val a = act ?: return
    val what = a.ask ?: return
    if (a.busy) return
    val cur = current() ?: return
    val slug = at?.first ?: return
    val convId = cur.conversation.id
    val m = cur.messages.firstOrNull { it.id == a.messageId } ?: run { act = null; return }
    if (what == ChatDeleteScope.ALL && !deletes.canForAll(cur, m, clock())) { act = deletes.ask(cur, m, what, clock()); return }
    val tk = ticket() ?: run { act = null; return lost() }
    if (!deletes.begin(m.id, what)) return
    val busy = a.copy(busy = true, note = null)
    act = busy
    scope.launch {
      val r = withContext(io) { api.chatDelete(slug, tk.ticket, convId, m.id, what) }
      val e = r.errorOrNull()
      val back = deletes.end(busy, what, e)
      when {
        e == null -> {
          //  همان پیام با `rev` ِ تازه: «برای من» ⇒ `hidden` (از رشته می‌رود)، «برای همه» ⇒ سنگِ قبر
          r.valueOrNull()?.let { msg -> current()?.takeIf { it.conversation.id == convId }?.let { thread = Load.Ready(it.merge(ChatChanges(it.conversation, listOf(msg), it.cursor))) } }
          if (act?.messageId == m.id) act = null
          val done = if (what == ChatDeleteScope.ALL) ShopperText.chatDeleteAllDone else ShopperText.chatDeleteMeDone
          deletedNote = done
          delay(4_000)
          if (deletedNote == done) deletedNote = null
        }
        ChatOutbox.failureOf(e) == ChatFailure.VERIFY -> { act = null; lost() }
        else -> {
          if (act?.messageId == m.id) act = back
          //  رشته همین حالا بسته شد ⇒ حالِ تازهٔ گفت‌وگو (کادرِ نوشتن و «حذف برای همه» می‌روند)
          if (e.code == "chat_closed") refresh(slug, convId)
        }
      }
    }
  }

  /** یک بار خواندنِ تغییرها بی صبر (long-poll ِ باز ممکن است هنوز منتظر باشد) */
  private fun refresh(slug: String, convId: String) {
    val tk = ticket() ?: return
    val cur = current()?.takeIf { it.conversation.id == convId } ?: return
    scope.launch { withContext(io) { api.chatChanges(slug, tk.ticket, convId, cur.cursor, 0) }.valueOrNull()?.let { merge(slug, it) } }
  }

  private fun convAction(call: (String, String, String) -> ShopperResult<ChatConversation>) {
    val cur = current() ?: return
    val slug = at?.first ?: return
    val tk = ticket() ?: return lost()
    scope.launch {
      when (val r = withContext(io) { call(slug, tk.ticket, cur.conversation.id) }) {
        is ShopperResult.Ok -> current()?.let { thread = Load.Ready(it.copy(conversation = r.value)) }
        is ShopperResult.Fail -> note = ShopperText.error(r.error)
      }
    }
  }

  /** بایت‌های عکسِ یک پیام (حافظهٔ کوچکِ ۲۴ تایی)؛ نیامده ⇒ null و بارگیری در پس‌زمینه */
  fun mediaOf(mediaId: String): ByteArray? {
    media[mediaId]?.let { return it }
    val cur = current() ?: return null
    val slug = at?.first ?: return null
    val tk = ticket() ?: return null
    if (!loading.add(mediaId)) return null
    scope.launch {
      val r = withContext(io) { api.chatMedia(slug, tk.ticket, cur.conversation.id, mediaId) }
      loading.remove(mediaId)
      r.valueOrNull()?.let {
        media[mediaId] = it
        while (media.size > 24) media.remove(media.keys.first())
        mediaRev++
      }
    }
    return null
  }

  /** پیام‌های صفِ همین گفت‌وگو (برای نمایش زیرِ رشته) */
  fun pending(convId: String): List<ChatOutItem> = ChatOutbox.forConv(outbox, convId)

  /** بایت‌های عکسِ پیامی که هنوز در صف است (پیش‌نمایشِ همان دستگاه) */
  fun photoOf(clientId: String): ByteArray? = photos[clientId]?.let { it.thumb ?: it.main }
}
