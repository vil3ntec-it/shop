package java.lang.ref

/**
 *  shimِ iOS ِ `SoftReference` (PERF-LEDGER: کشِ سایه و درختِ دفتر در `SyncCommit`/`LedgerTrees`). Kotlin/Native مرجعِ
 *  «نرم» ندارد؛ این‌جا تا `clear` نگه می‌دارد — همان رفتارِ JVM وقتی حافظه کم نیست. کشِ پاک‌شده هرگز نتیجه را عوض نمی‌کند.
 */
class SoftReference<T : Any>(referent: T?) {
  private var ref: T? = referent
  fun get(): T? = ref
  fun clear() { ref = null }
}
