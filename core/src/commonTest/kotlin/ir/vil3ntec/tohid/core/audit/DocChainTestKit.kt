package ir.vil3ntec.tohid.core.audit

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/** دیسکِ حافظه با همان قراردادِ [DocChainDisk] (دُمِ نیمه‌کاره، بایتِ UTF-8) — برای آزمون‌ها */
class MemoryDocDisk : DocChainDisk {
  var state: String? = null
  val files = HashMap<Long, StringBuilder>()
  var stateWrites = 0

  override fun readState(): String? = state
  override fun writeState(text: String) { state = text; stateWrites++ }
  override fun size(epoch: Long): Long = (files[epoch]?.toString() ?: "").encodeToByteArray().size.toLong()

  override fun readFrom(epoch: Long, from: Long): List<String> {
    val bytes = (files[epoch]?.toString() ?: "").encodeToByteArray()
    if (from >= bytes.size) return emptyList()
    return bytes.copyOfRange(from.toInt(), bytes.size).decodeToString().split('\n').dropLast(1)
  }

  override fun append(epoch: Long, lines: List<String>) {
    val sb = files.getOrPut(epoch) { StringBuilder() }
    sb.setLength(sb.lastIndexOf("\n") + 1)
    lines.forEach { sb.append(it).append('\n') }
  }

  fun lines(epoch: Long): List<String> = readFrom(epoch, 0)
}

/** سرورِ ساختگی: پاسخ‌ها از پیش، درخواست‌ها ثبت */
class FakeChainServer : DocChainServer {
  var status: DocChainStatus? = DocChainStatus(enabled = true)
  /** null ⇐ «آفلاین»؛ پیش‌فرض: هرچه آمد تأیید می‌شود */
  var reply: ((DocChainReport) -> DocChainReply?)? = { r -> DocChainReply(true, listOf(DocChainAck(r.series, r.epoch, r.seq, r.head))) }
  val statusCalls = ArrayList<Int>()
  val reports = ArrayList<DocChainReport>()

  override suspend fun status(): DocChainStatus? { statusCalls += 1; return status }
  override suspend fun report(r: DocChainReport): DocChainReply? { reports += r; return reply?.invoke(r) }
}

/** دویدنِ تابعِ suspend بی کتابخانهٔ coroutines (هیچ‌جا واقعاً معلق نمی‌شود) */
fun <T> runSync(block: suspend () -> T): T {
  var out: Result<T>? = null
  block.startCoroutine(Continuation(EmptyCoroutineContext) { out = it })
  return out!!.getOrThrow()
}

fun sale(id: String, no: Long, series: String = "B1", total: Long = 1000, customer: String? = null) = SaleFacts(
  id = id, series = series, no = no, postedAt = 1_761_800_000_000 + no, customerId = customer, currency = "AFN",
  lines = listOf(SaleFacts.Line("p1", 1500, total, total)), subtotal = total, discount = 0, total = total,
  paid = total, method = "cash",
)
