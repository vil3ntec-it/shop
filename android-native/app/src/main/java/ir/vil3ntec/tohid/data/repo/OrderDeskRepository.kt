package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 *  میزِ سفارش — `/api/shop/orders/desk` (ORD-04/08/09؛ `server/src/routes/order-desk.js`). پرچمِ خاموش ⇒
 *  `GET` فقط `{enabled:false}` و صفحه چیزی نشان نمی‌دهد. هر نوشتن یک `eventId` ِ تازه دارد (تکرارِ همان ⇒ همان پاسخ).
 */
class OrderDeskRepository(private val api: ApiClient) {

  data class Desk(
    val enabled: Boolean = false,
    val busyUntil: Long? = null,
    val pausedUntil: Long? = null,
    val pauseReason: String = "",
    val pauseSource: String = "",
    val autoAcceptOn: Boolean = false,
    val autoAcceptMaxOpen: Int = 5,
    val autoAcceptReadyMinutes: Int = 15,
    val autoPauseOn: Boolean = false,
    val autoPauseMaxOpen: Int = 10,
    val autoPauseOldestMinutes: Int = 30,
    val autoPauseMinutes: Int = 30,
    val version: Long = 0,
    val queueOpen: Int = 0,
    val serverTime: Long = 0,
    val canManage: Boolean = false,
    val canSettings: Boolean = false,
  )

  private fun obj(o: JsonObject?, key: String): JsonObject? = o?.get(key) as? JsonObject

  /** پاسخِ `GET /` یا `state` ِ هر نوشتن ⇒ میز؛ `perms` و `serverTime` از همان `GET` */
  fun parse(o: JsonObject, base: Desk = Desk(enabled = true)): Desk {
    val busy = obj(o, "busy")
    val pause = obj(o, "pause")
    val aa = obj(o, "autoAccept")
    val ap = obj(o, "autoPause")
    val perms = obj(o, "permissions")
    return base.copy(
      enabled = o["enabled"]?.let { CourierWire.bool(o, "enabled") } ?: base.enabled,
      busyUntil = busy?.let { CourierWire.long(it, "until") },
      pausedUntil = pause?.let { CourierWire.long(it, "until") },
      pauseReason = pause?.let { CourierWire.str(it, "reason") }.orEmpty(),
      pauseSource = pause?.let { CourierWire.str(it, "source") }.orEmpty(),
      autoAcceptOn = aa?.let { CourierWire.bool(it, "enabled") } ?: base.autoAcceptOn,
      autoAcceptMaxOpen = aa?.let { CourierWire.long(it, "maxOpen") }?.toInt() ?: base.autoAcceptMaxOpen,
      autoAcceptReadyMinutes = aa?.let { CourierWire.long(it, "readyMinutes") }?.toInt() ?: base.autoAcceptReadyMinutes,
      autoPauseOn = ap?.let { CourierWire.bool(it, "enabled") } ?: base.autoPauseOn,
      autoPauseMaxOpen = ap?.let { CourierWire.long(it, "maxOpen") }?.toInt() ?: base.autoPauseMaxOpen,
      autoPauseOldestMinutes = ap?.let { CourierWire.long(it, "oldestMinutes") }?.toInt() ?: base.autoPauseOldestMinutes,
      autoPauseMinutes = ap?.let { CourierWire.long(it, "minutes") }?.toInt() ?: base.autoPauseMinutes,
      version = CourierWire.long(o, "version") ?: base.version,
      queueOpen = obj(o, "queue")?.let { CourierWire.long(it, "open") }?.toInt() ?: base.queueOpen,
      serverTime = CourierWire.long(o, "serverTime") ?: base.serverTime,
      canManage = perms?.let { CourierWire.bool(it, "manage") } ?: base.canManage,
      canSettings = perms?.let { CourierWire.bool(it, "settings") } ?: base.canSettings,
    )
  }

  suspend fun state(): ApiResult<Desk> = result {
    val o = api.get(ROOT)
    if (!CourierWire.bool(o, "enabled")) Desk(enabled = false) else parse(o)
  }

  private suspend fun write(path: String, body: JsonObject, base: Desk): Desk {
    val withId = JsonObject(body + ("eventId" to JsonPrimitive(OrdersRepository.newKey())))
    val out = api.post("$ROOT$path", withId)
    return parse(obj(out, "state") ?: JsonObject(emptyMap()), base)
  }

  /** «شلوغم» برای `minutes` دقیقه (۰ = پایان) */
  suspend fun busy(base: Desk, minutes: Int): ApiResult<Desk> = result { write("/busy", buildJsonObject { put("minutes", minutes) }, base) }

  suspend fun pause(base: Desk, minutes: Int, reason: String, note: String): ApiResult<Desk> = result {
    write("/pause", buildJsonObject { put("minutes", minutes); put("reason", reason); if (note.isNotBlank()) put("note", note.trim()) }, base)
  }

  suspend fun resume(base: Desk): ApiResult<Desk> = result { write("/resume", JsonObject(emptyMap()), base) }

  /** قبول و توقفِ خودکار با نسخهٔ دیده‌شده (کهنه ⇒ ۴۰۹ `version_conflict`) */
  suspend fun settings(base: Desk, next: Desk): ApiResult<Desk> = result {
    val body = buildJsonObject {
      put("expectedVersion", base.version)
      put("autoAccept", buildJsonObject {
        put("enabled", next.autoAcceptOn); put("maxOpen", next.autoAcceptMaxOpen); put("readyMinutes", next.autoAcceptReadyMinutes)
      })
      put("autoPause", buildJsonObject {
        put("enabled", next.autoPauseOn); put("maxOpen", next.autoPauseMaxOpen)
        put("oldestMinutes", next.autoPauseOldestMinutes); put("minutes", next.autoPauseMinutes)
      })
      put("eventId", OrdersRepository.newKey())
    }
    val out = api.put("$ROOT/settings", body)
    parse(obj(out, "state") ?: JsonObject(emptyMap()), base)
  }

  companion object {
    const val ROOT = "/shop/orders/desk"
  }
}
