package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.crm.CustomerAccess
import ir.vil3ntec.tohid.core.crm.CustomerAccessOutbox
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.data.repo.Backend

/**
 *  CRM-15 (د۱۸) — مرزِ سکوی صفِ دیدنِ حسابدارِ بی‌اینترنت (`core/crm/CustomerAccessOutbox`): صفِ هر حساب@دکان در
 *  SharedPreferences ِ `tohid-customer-access` و فرستادنِ آن پس از هر دورِ `SyncV1Worker`.
 *  ⛔ پرچمِ خاموشِ سرور ⇒ ۴۰۴ ⇒ صف پاک (همان امروز). خطای شبکه/سرور ⇒ صف می‌ماند و دور را نمی‌گیرد.
 */
object CustomerAccessOutboxAndroid {
  private const val PREFS = "tohid-customer-access"
  private val lock = Any()

  private fun key(context: Context): String =
    "q." + runCatching { ir.vil3ntec.tohid.sync.v1.SyncV1Engine.of(context).currentKey() }.getOrDefault("")

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun queued(context: Context): List<CustomerAccessOutbox.Note> =
    CustomerAccessOutbox.decode(prefs(context).getString(key(context), null).orEmpty())

  private fun save(context: Context, q: List<CustomerAccessOutbox.Note>) {
    prefs(context).edit().putString(key(context), CustomerAccessOutbox.encode(q)).apply()
  }

  /** دیدنِ بی‌اینترنت در صف؛ `true` یعنی در صف هست (تازه یا یکی‌شده با همان دیدنِ پنجره) */
  fun add(context: Context, customerId: String, kind: CustomerAccess.Kind, now: Long): Boolean = synchronized(lock) {
    val q = queued(context)
    val id = "n" + java.lang.Long.toString(now, 36) + java.lang.Long.toString(System.nanoTime() and 0xFFFFFF, 36)
    val next = CustomerAccessOutbox.add(q, CustomerAccessOutbox.Note(id, customerId, kind, now), now)
    if (next != q) save(context, next)
    next.any { it.customerId == customerId.trim() && it.kind == kind }
  }

  /** پس از دورِ همگام‌سازی: دسته‌دسته تا ته؛ ۴۰۴ ⇒ صف پاک */
  suspend fun flush(context: Context): Int {
    if (TrainingMode.on || !Backend.isReady(context)) return 0
    var sent = 0
    repeat(CustomerAccessOutbox.CAP / CustomerAccessOutbox.BATCH + 1) {
      val q = synchronized(lock) { queued(context) }
      val batch = CustomerAccessOutbox.batch(q)
      if (batch.isEmpty()) return sent
      val res = try {
        Backend.api(context).post(CustomerAccessOutbox.PATH, CustomerAccessOutbox.body(batch))
      } catch (_: ApiFailure.NotFound) {
        synchronized(lock) { save(context, emptyList()) }
        return sent
      }
      synchronized(lock) {
        val now = queued(context)
        val left = CustomerAccessOutbox.settle(now, res)
        if (left.size == now.size) return sent   //  پاسخِ بی نتیجه ⇒ دورِ بعد
        save(context, left)
        sent += now.size - left.size
      }
    }
    return sent
  }
}
