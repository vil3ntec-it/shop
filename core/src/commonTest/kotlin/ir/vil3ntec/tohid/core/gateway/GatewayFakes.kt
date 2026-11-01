package ir.vil3ntec.tohid.core.gateway

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/** کوروتینِ بی کتابخانه: جعلی‌ها هیچ‌وقت واقعاً معلق نمی‌شوند، پس همین‌جا تمام می‌شود */
internal fun <T> runSync(block: suspend () -> T): T {
  var out: Result<T>? = null
  block.startCoroutine(Continuation(EmptyCoroutineContext) { out = it })
  return checkNotNull(out) { "معلق ماند" }.getOrThrow()
}

/** لایهٔ HTTP ِ جعلی: پاسخ‌ها به ترتیب، و هر درخواست ثبت می‌شود */
internal class FakeTransport(vararg replies: GatewayReply) : GatewayTransport {
  val queue = ArrayDeque(replies.toList())
  val calls = ArrayList<String>()
  val bodies = ArrayList<Map<String, Any?>>()
  val keys = ArrayList<String?>()
  var onCall: () -> Unit = {}

  private fun next(): GatewayReply {
    onCall()
    return queue.removeFirstOrNull() ?: GatewayReply(0, message = "تمام")
  }

  override suspend fun get(path: String): GatewayReply { calls += "GET $path"; return next() }

  override suspend fun post(path: String, body: Map<String, Any?>, idempotencyKey: String?): GatewayReply {
    calls += "POST $path"
    bodies += body
    keys += idempotencyKey
    return next()
  }
}

/** درختِ JSON ِ یک قصد، همان شکلِ پاسخِ سرور (عددها `Double`، مثلِ درختِ برنامه) */
internal fun intentJson(
  id: String = "pi_1",
  status: String = "pending",
  amount: Long = 145000,
  expiresAt: Long? = 1_000_000L,
  ref: String = "sale1",
  payment: Map<String, Any?>? = null,
): Map<String, Any?> = linkedMapOf(
  "id" to id, "kind" to "pos_sale", "refId" to ref, "status" to status,
  "amount" to amount.toDouble(), "amountMinor" to amount.toDouble(), "currency" to "AFN",
  "checkoutUrl" to "https://pay.example/c/$id", "qrPayload" to "https://pay.example/c/$id",
  "expiresAt" to expiresAt?.toDouble(), "paidAt" to null, "providerRef" to "",
  "needsReview" to false, "createdAt" to 100_000.0, "somethingNew" to listOf(1, 2),
  "payment" to payment,
)

internal fun ok(body: Map<String, Any?>, status: Int = 200) = GatewayReply(status, body)
