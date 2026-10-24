package ir.vil3ntec.tohid.ui.v3.online.chat

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import ir.vil3ntec.tohid.core.chat.ChatBackoff
import ir.vil3ntec.tohid.core.chat.ChatConfig
import ir.vil3ntec.tohid.core.chat.ChatConversation
import ir.vil3ntec.tohid.core.chat.ChatIds
import ir.vil3ntec.tohid.core.chat.ChatMessage
import ir.vil3ntec.tohid.core.chat.ChatMeta
import ir.vil3ntec.tohid.core.chat.ChatThread
import ir.vil3ntec.tohid.core.chat.OutboxItem
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.data.ChatMedia
import ir.vil3ntec.tohid.data.ShopChatSync
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.ui.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 *  یک گفت‌وگو — همان کارِ `ChatThreadPane` ِ پیشین بیرون از نمایش (v3 §۳۳):
 *  long-poll با `after=<rev>` و backoff، «رسید» وقتی پیام به دستگاه آمد و «خوانده» فقط جلوی چشم
 *  (CHAT-04)، صفِ دیسکیِ `ShopChatSync` با clientId (بی اینترنت نوشته می‌شود و یک بار می‌رود)، عکسِ
 *  کوچک و بی EXIF (`ChatMedia`)، صدا، گزارش، بستن/باز کردن و مسدودی. صفحه فقط حالت می‌کشد.
 */
@Stable
internal class ChatThreadController(
  private val context: Context,
  val convId: String,
  initial: ChatConversation?,
  private val cfg: ChatConfig,
  private val onConversation: (ChatConversation) -> Unit,
) {
  private val repo = Backend.shopChat(context)
  val queue = ShopChatSync.queue(context)
  private val thread = ChatThread().also { it.conversation = initial }

  /** با هر تغییرِ رشته یکی بالا می‌رود (فهرست دوباره کشیده می‌شود) */
  var tick by mutableIntStateOf(0)
    private set
  /** نوارِ بالای گفت‌وگو: متن و «بد است؟» (خطا ⇐ قرمز) */
  var banner by mutableStateOf<Pair<String, Boolean>?>(null)
  val media = mutableStateMapOf<String, ImageBitmap>()
  val full = mutableStateMapOf<String, Boolean>()

  val conversation: ChatConversation? get() = thread.conversation

  fun messages(): List<ChatMessage> = thread.list()

  /** پیام‌های صفِ این دستگاه که هنوز در رشتهٔ سرور نیامده‌اند */
  fun local(server: List<ChatMessage>): List<OutboxItem> {
    val have = server.mapNotNull { it.clientId }.toSet()
    return queue?.forConv(convId)?.filter { it.clientId !in have }.orEmpty()
  }

  val canWrite: Boolean get() = cfg.permissions.reply && (conversation?.canSend != false)

  /** پیامی که از صف رفت بی انتظارِ long-poll دیده می‌شود */
  fun onSent(id: String, m: ChatMessage) { if (id == convId && thread.add(m)) tick++ }

  private fun changed(c: ChatConversation) { thread.conversation = c; onConversation(c); tick++ }

  suspend fun receipts() {
    val need = thread.receiptNeed(ShopChatSync.foreground) ?: return
    repo.receipts(convId, need.deliveredSeq, need.readSeq).onSuccess(::changed)
  }

  /** حلقهٔ long-poll — تا مسیر باز است؛ «گفت‌وگو خاموش/پیدا نشد» ⇐ پایان با نوارِ خطا */
  suspend fun run() {
    var first = true
    var fails = 0
    ShopChatSync.flush(context, convId)
    while (coroutineContext.isActive) {
      val res = repo.changes(convId, thread.cursor, if (first) 0 else LONG_POLL_MS)
      val err = (res as? ApiResult.Failure)?.error
      if (err == null) {
        val ch = res.valueOrNull() ?: continue
        first = false
        fails = 0
        if (thread.apply(ch)) tick++
        queue?.reconcile(convId, ch.messages.filter { it.mine }.mapNotNull { it.clientId })?.forEach { ShopChatSync.delBlob(context, it) }
        thread.conversation?.let(onConversation)
        banner = null
        receipts()
        continue
      }
      if (err is ApiFailure.NotFound) {
        if (err.code == "chat_disabled") ShopChatSync.refreshConfig(context)
        banner = err.userMessage to true
        return
      }
      fails++
      banner = (if (err.retryable) StrOnline.ctOfflineThread else err.userMessage) to !err.retryable
      delay(ChatBackoff.delayMs(fails))
    }
  }

  suspend fun loadImage(key: String, fetch: suspend () -> ByteArray?) {
    if (media.containsKey(key)) return
    val bytes = fetch() ?: return
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { media[key] = it.asImageBitmap() }
  }

  suspend fun thumb(m: ChatMessage) {
    val key = m.media?.let { it.thumbId ?: it.id } ?: return
    loadImage(key) { repo.media(convId, key).valueOrNull() }
  }

  suspend fun localThumb(x: OutboxItem) {
    val k = x.thumbKey ?: x.blobKey ?: return
    loadImage("local-$k") { ShopChatSync.blob(context, k) }
  }

  /** عکسِ کامل فقط با زدن (کم‌مصرف، §۳۳.۲ بند ۶) */
  suspend fun openFull(m: ChatMessage) {
    val id = m.media?.id ?: return
    full[m.id] = true
    loadImage(id) { repo.media(convId, id).onFailure { e -> banner = e.userMessage to true }.valueOrNull() }
  }

  fun imageOf(m: ChatMessage): ImageBitmap? {
    val ref = m.media ?: return null
    val thumb = ref.thumbId ?: ref.id
    return (if (full[m.id] == true) media[ref.id] else null) ?: media[thumb]
  }

  fun imageOf(x: OutboxItem): ImageBitmap? = (x.thumbKey ?: x.blobKey)?.let { media["local-$it"] }

  suspend fun audio(m: ChatMessage) {
    val id = m.media?.id ?: return
    repo.media(convId, id)
      .onSuccess { bytes -> playOrSave(context, id, bytes)?.let { banner = it to true } }
      .onFailure { banner = it.userMessage to true }
  }

  fun sendText(body: String) {
    val b = body.trim()
    if (b.isEmpty()) return
    queue?.add(convId, kind = "text", body = b)
    ShopChatSync.flush(context, convId)
  }

  /** عکس روی همین دستگاه کوچک و چرخیده و بی EXIF می‌شود، بعد در صف می‌رود; @return true اگر رفت */
  suspend fun sendImage(uri: Uri, caption: String): Boolean {
    banner = StrOnline.ctPreparingPhoto to false
    val prepared = withContext(Dispatchers.Default) { ChatMedia.prepareImage(context, uri, cfg.limits) }
    var ok = false
    prepared.onSuccess { p ->
      val q = queue ?: return@onSuccess
      val id = ChatIds.next(System.currentTimeMillis())
      ShopChatSync.putBlob(context, "$id-main", p.main)
      ShopChatSync.putBlob(context, "$id-thumb", p.thumb)
      q.add(
        convId, kind = "image", body = caption.trim(), meta = ChatMeta(width = p.width, height = p.height),
        blobKey = "$id-main", thumbKey = "$id-thumb", size = p.main.size.toLong(), clientId = id,
      )
      banner = StrOnline.ctPhotoReady(StrOnline.kb(p.main.size.toLong())) to false
      ShopChatSync.flush(context, convId)
      ok = true
    }.onFailure { banner = (it.message ?: StrOnline.ctPhotoFailed) to true }
    return ok
  }

  fun sendVoice(bytes: ByteArray, ms: Long) {
    val q = queue ?: return
    val id = ChatIds.next(System.currentTimeMillis())
    ShopChatSync.putBlob(context, "$id-main", bytes)
    q.add(convId, kind = "audio", meta = ChatMeta(durationMs = maxOf(1L, ms)), blobKey = "$id-main", size = bytes.size.toLong(), clientId = id)
    ShopChatSync.flush(context, convId)
  }

  fun retry(x: OutboxItem) { queue?.retry(x.clientId); ShopChatSync.flush(context, convId) }

  fun cancel(x: OutboxItem) { queue?.cancel(x.clientId)?.forEach { ShopChatSync.delBlob(context, it) } }

  suspend fun toggleStatus() {
    val c = conversation ?: return
    repo.setStatus(convId, if (c.status == "open") "closed" else "open").onSuccess(::changed).onFailure { banner = it.userMessage to true }
  }

  suspend fun toggleBlock() {
    val c = conversation ?: return
    repo.setBlock(convId, !c.blockedCustomer).onSuccess(::changed).onFailure { banner = it.userMessage to true }
  }

  /** @param messageId null ⇒ کلِ گفت‌وگو; @return true اگر رسید */
  suspend fun report(reason: String, note: String, messageId: String?): Boolean {
    var ok = false
    repo.report(convId, reason, note, messageId)
      .onSuccess { dup -> ok = true; banner = (if (dup) StrOnline.ctReportDup else StrOnline.ctReportSent) to false }
      .onFailure { banner = it.userMessage to true }
    return ok
  }

  companion object {
    private const val LONG_POLL_MS = 20_000L

    /** صدا روی گوشی پخش می‌شود؛ کامپیوتر رمزگشای صدای پیام (ogg/aac) را ندارد ⇒ «ذخیرهٔ فایل» */
    private var player: ChatMedia.Player? = null

    /** @return پیامِ خطا برای نمایش، یا null */
    private fun playOrSave(context: Context, id: String, bytes: ByteArray): String? {
      if (ChatMedia.voiceSupported) {
        val p = player ?: ChatMedia.Player(context.applicationContext).also { player = it }
        return runCatching { p.play(id, bytes) {} }.exceptionOrNull()?.let { StrOnline.ctVoiceNotPlayed }
      }
      val ext = when {
        bytes.size > 4 && String(bytes, 0, 4, Charsets.ISO_8859_1) == "OggS" -> "ogg"
        bytes.size > 4 && bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte() -> "webm"
        else -> "m4a"
      }
      Platform.saveFile("voice-$id.$ext", bytes)
      return null
    }
  }
}
