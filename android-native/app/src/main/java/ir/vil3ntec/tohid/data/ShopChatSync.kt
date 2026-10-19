package ir.vil3ntec.tohid.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import ir.vil3ntec.tohid.core.chat.ChatConfig
import ir.vil3ntec.tohid.core.chat.ChatMessage
import ir.vil3ntec.tohid.core.chat.ChatQueue
import ir.vil3ntec.tohid.core.chat.OutboxItem
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.sync.SyncStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 *  چتِ دکان، بیرون از صفحه (v3 §۳۳؛ CHAT-02/03/11).
 *
 *    • پرچم: `GET /api/shop/chat/config` — ⛔ تا `enabled` نیاید، ردیفِ «بیشتر»
 *      و صفحه دیده نمی‌شوند. آخرین پاسخِ همین حساب نگه داشته می‌شود تا بی
 *      اینترنت هم پیام‌های در صف و گذشته دیده شوند.
 *    • صفِ ارسال (`ChatQueue`) روی دیسک: `files/chat-outbox/queue-<حساب>.json`
 *      و بایت‌های رسانه کنارش. نوشتن اتمی (`DiskWrite`)؛ بستنِ برنامه یا
 *      خاموشیِ گوشی پیام را گم نمی‌کند.
 *    • فرستادن در یک scope ِ جدا از صفحه: بستنِ صفحه ارسالِ نیمه‌کاره را
 *      نمی‌کُشد؛ backoff ِ صف با یک بیدارباش ادامه می‌یابد.
 *
 *  ⛔ متنِ پیام هیچ‌جا لاگ نمی‌شود.
 */
object ShopChatSync {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val draining = ConcurrentHashMap.newKeySet<String>()
  private var wakeJob: Job? = null
  private var queue: ChatQueue? = null
  private var queueUser = ""

  /** آخرین پیکربندیِ دانسته — برای نمایشِ ردیفِ «بیشتر» */
  val config = mutableStateOf<ChatConfig?>(null)

  /** هر تغییرِ صف ⇒ یک واحد بیشتر (صفحه با آن تازه می‌شود) */
  val version = mutableIntStateOf(0)

  /**
   *  برنامه جلوی چشم است؟ اندروید با onResume/onPause ِ `MainActivity`؛
   *  کامپیوتر پنجره‌ای جدا از برنامه ندارد ⇒ همان true.
   */
  val foregroundState = mutableStateOf(true)
  var foreground: Boolean
    get() = foregroundState.value
    set(v) { foregroundState.value = v }

  /** پیامی که از صف رفت — صفحهٔ باز آن را بی انتظارِ long-poll نشان می‌دهد */
  @Volatile var onSent: ((String, ChatMessage) -> Unit)? = null

  private fun user(context: Context): String =
    runCatching { SyncStore(context).accountId }.getOrDefault("").filter { it.isLetterOrDigit() || it == '_' || it == '-' }

  private fun dir(context: Context): File =
    File(context.applicationContext.filesDir, "chat-outbox").apply { if (!exists()) mkdirs() }

  private fun blobFile(context: Context, key: String): File =
    File(dir(context), "blob-" + key.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }.joinToString(""))

  fun putBlob(context: Context, key: String, bytes: ByteArray) = DiskWrite.bytes(blobFile(context, key), bytes)
  fun blob(context: Context, key: String): ByteArray? = blobFile(context, key).takeIf { it.exists() }?.readBytes()
  fun delBlob(context: Context, key: String) { blobFile(context, key).delete() }

  /** صفِ همین حساب؛ بی حساب ⇒ null. */
  @Synchronized
  fun queue(context: Context): ChatQueue? {
    val u = user(context)
    if (u.isEmpty()) return null
    queue?.let { if (u == queueUser) return it }
    val file = File(dir(context), "queue-$u.json")
    val ser = ListSerializer(OutboxItem.serializer())
    val q = ChatQueue(
      load = { file.takeIf { it.exists() }?.readText()?.let { ApiJson.format.decodeFromString(ser, it) } },
      save = { items -> DiskWrite.text(file, ApiJson.format.encodeToString(ser, items)); version.intValue++ },
    )
    queue = q
    queueUser = u
    return q
  }

  private fun prefs(context: Context) = context.getSharedPreferences("tohid-shop-chat", Context.MODE_PRIVATE)

  /** پرچم و مجوزها از سرور؛ بی اینترنت ⇒ آخرین پاسخِ همین حساب. */
  suspend fun refreshConfig(context: Context): ChatConfig? {
    if (!Backend.isReady(context)) { config.value = null; return null }
    val key = "config-" + user(context)
    Backend.shopChat(context).config()
      .onSuccess { c ->
        config.value = c
        prefs(context).edit().putString(key, ApiJson.format.encodeToString(ChatConfig.serializer(), c)).apply()
      }
      .onFailure { e ->
        config.value = if (e.retryable) {
          prefs(context).getString(key, null)?.let { runCatching { ApiJson.format.decodeFromString(ChatConfig.serializer(), it) }.getOrNull() }
        } else null
      }
    if (config.value?.enabled == true) flushAll(context)
    return config.value
  }

  val enabled: Boolean get() = config.value?.enabled == true

  /** یک پیام از صف: اول رسانه (اگر هست و هنوز نرفته)، بعد خودِ پیام با همان clientId. */
  private suspend fun sendItem(context: Context, q: ChatQueue, item: OutboxItem): ChatMessage {
    val repo = Backend.shopChat(context)
    var cur = item
    if (cur.kind != "text" && cur.mediaId == null) {
      val bytes = cur.blobKey?.let { blob(context, it) }
        ?: throw ApiFailure.Invalid("فایلِ این پیام روی این دستگاه نمانده", "local_media_gone")
      val mime = if (cur.kind == "image") "image/jpeg" else "application/octet-stream"
      val id = repo.upload(cur.convId, bytes, mime, "main")
      cur = q.patch(cur.clientId) { it.copy(mediaId = id) } ?: cur
    }
    if (cur.kind == "image" && cur.thumbKey != null && cur.thumbId == null) {
      blob(context, cur.thumbKey!!)?.let { bytes ->
        val id = repo.upload(cur.convId, bytes, "image/jpeg", "thumb")
        cur = q.patch(cur.clientId) { it.copy(thumbId = id) } ?: cur
      }
    }
    return repo.send(cur)
  }

  fun flush(context: Context, convId: String) {
    val app = context.applicationContext
    val q = queue(app) ?: return
    if (!draining.add(convId)) return
    scope.launch {
      try {
        q.drain(
          convId,
          send = { sendItem(app, q, it) },
          onSent = { _, message, keys -> keys.forEach { delBlob(app, it) }; onSent?.invoke(convId, message) },
          onChange = { version.intValue++ },
        )
      } finally {
        draining.remove(convId)
        schedule(app)
      }
    }
  }

  fun flushAll(context: Context) {
    val q = queue(context) ?: return
    q.convs().forEach { flush(context, it) }
  }

  /** نزدیک‌ترین «دوباره» ِ صف — بی آن backoff فقط با بازشدنِ صفحه ادامه می‌یافت. */
  @Synchronized
  private fun schedule(context: Context) {
    val at = queue(context)?.nextWake() ?: return
    wakeJob?.cancel()
    wakeJob = scope.launch {
      delay((at - System.currentTimeMillis()).coerceAtLeast(0) + 50)
      flushAll(context)
    }
  }

  /** اینترنت برگشت یا کاربر «دوباره» زد ⇒ هر «منتظر» همین حالا. */
  fun wake(context: Context) {
    queue(context)?.wake()
    flushAll(context)
  }
}
