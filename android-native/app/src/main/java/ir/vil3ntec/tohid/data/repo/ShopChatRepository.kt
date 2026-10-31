package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.chat.ChatChanges
import ir.vil3ntec.tohid.core.chat.ChatConfig
import ir.vil3ntec.tohid.core.chat.ChatConversation
import ir.vil3ntec.tohid.core.chat.ChatList
import ir.vil3ntec.tohid.core.chat.ChatMessage
import ir.vil3ntec.tohid.core.chat.OutboxItem
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiEndpoints
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.core.net.ApiResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 *  چتِ مشتری ↔ دکان، سمتِ کارمند — `/api/shop/chat` (v3 §۳۳).
 *
 *  shop_id و نقش را سرور از توکن می‌خواند؛ این‌جا هیچ‌کدام فرستاده نمی‌شود.
 *  `send`/`upload` به‌جای `ApiResult` استثنا می‌اندازند، چون صدا زننده‌شان
 *  صفِ ارسال (`ChatQueue.drain`) است و همان استثنا را دسته‌بندی می‌کند.
 */
class ShopChatRepository(private val api: ApiClient) {

  suspend fun config(): ApiResult<ChatConfig> = result { ApiJson.decode<ChatConfig>(api.get(ApiEndpoints.ShopChat.CONFIG)) }

  suspend fun conversations(status: String, q: String = "", limit: Int = 50): ApiResult<ChatList> = result {
    val path = ApiEndpoints.withQuery(
      ApiEndpoints.ShopChat.CONVERSATIONS,
      mapOf("status" to status, "q" to q.trim().takeIf { it.isNotEmpty() }, "limit" to limit),
    )
    ApiJson.decode<ChatList>(api.get(path))
  }

  /** تغییرها پس از `after` (rev)؛ `waitMs` > ۰ یعنی long-poll (سقفِ سرور ۲۵ ثانیه). */
  suspend fun changes(id: String, after: Long, waitMs: Long): ApiResult<ChatChanges> = result {
    val path = ApiEndpoints.withQuery(
      ApiEndpoints.ShopChat.conversation(id) + "/messages",
      mapOf("after" to after, "wait" to waitMs.takeIf { it > 0 }),
    )
    ApiJson.decode<ChatChanges>(api.get(path))
  }

  /** یک پیام از صف — همان clientId ⇒ سرور تکراری نمی‌سازد. */
  suspend fun send(item: OutboxItem): ChatMessage {
    val body = buildJsonObject {
      put("clientId", item.clientId)
      put("kind", item.kind)
      put("body", item.body)
      item.mediaId?.let { put("mediaId", it) }
      item.thumbId?.let { put("thumbId", it) }
      if (!item.meta.isEmpty) put("meta", buildJsonObject {
        item.meta.durationMs?.let { put("durationMs", it) }
        item.meta.width?.let { put("width", it) }
        item.meta.height?.let { put("height", it) }
      })
    }
    val out = api.post(ApiEndpoints.ShopChat.conversation(item.convId) + "/messages", body)
    return ApiJson.decodeAt<ChatMessage>(out, "message") ?: throw ir.vil3ntec.tohid.core.net.ApiFailure.InvalidResponse()
  }

  /** بارگذاریِ رسانه (`role` = main یا thumb) ⇒ شناسهٔ رسانه. همان بایت ⇒ همان شناسه. */
  suspend fun upload(convId: String, bytes: ByteArray, mime: String, role: String, onProgress: ((Long) -> Unit)? = null): String {
    val out = api.postBytes(ApiEndpoints.ShopChat.conversation(convId) + "/media?role=$role", bytes, mime, onProgress = onProgress)
    return ApiJson.text(out, "mediaId").ifEmpty { throw ir.vil3ntec.tohid.core.net.ApiFailure.InvalidResponse() }
  }

  suspend fun media(convId: String, mediaId: String): ApiResult<ByteArray> = result {
    api.getBytes(ApiEndpoints.ShopChat.conversation(convId) + "/media/" + java.net.URLEncoder.encode(mediaId, "UTF-8"))
  }

  suspend fun receipts(id: String, deliveredSeq: Long, readSeq: Long): ApiResult<ChatConversation> = result {
    conv(api.post(ApiEndpoints.ShopChat.conversation(id) + "/receipts", buildJsonObject {
      put("deliveredSeq", deliveredSeq)
      put("readSeq", readSeq)
    }))
  }

  suspend fun setStatus(id: String, status: String): ApiResult<ChatConversation> = result {
    conv(api.put(ApiEndpoints.ShopChat.conversation(id) + "/status", buildJsonObject { put("status", status) }))
  }

  suspend fun setBlock(id: String, on: Boolean): ApiResult<ChatConversation> = result {
    val path = ApiEndpoints.ShopChat.conversation(id) + "/block"
    conv(if (on) api.put(path) else api.delete(path))
  }

  /** @return true اگر همین مورد پیش‌تر گزارش شده بود */
  suspend fun report(id: String, reason: String, note: String, messageId: String?): ApiResult<Boolean> = result {
    val out = api.post(ApiEndpoints.ShopChat.conversation(id) + "/reports", buildJsonObject {
      put("reason", JsonPrimitive(reason))
      if (note.isNotBlank()) put("note", JsonPrimitive(note.trim()))
      if (!messageId.isNullOrBlank()) put("messageId", JsonPrimitive(messageId))
    })
    ApiJson.bool(out, "duplicate")
  }

  private fun conv(body: JsonObject): ChatConversation =
    ApiJson.decodeAt<ChatConversation>(body, "conversation") ?: throw ir.vil3ntec.tohid.core.net.ApiFailure.InvalidResponse()
}
