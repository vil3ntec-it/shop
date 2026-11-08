package ir.vil3ntec.tohid.platform

/**
 *  PERF-LEDGER — دو ابزارِ JVM برای میان‌بُرهای دفترِ بزرگ (`sync/v1/LedgerTrees`، `sync/v1/ShadowText`، `SyncCommit`؛
 *  `docs/PERF-LEDGER-fa.md`). آیفون هر دو را از shimهای `ios/shared/.../java/` دارد (`SoftReference`، `IdentityHashMap`).
 */

/**
 *  کشی که زیرِ فشارِ حافظه رها می‌شود (`java.lang.ref.SoftReference`): دفترِ بزرگ روی گوشیِ کم‌حافظه پیش از کمبودِ
 *  حافظه کش را می‌دهد و کار همان راهِ بی کش را می‌رود — کُندتر، نه غلط.
 */
class SoftRef<T : Any>(value: T) {
  private val ref = java.lang.ref.SoftReference(value)
  fun get(): T? = ref.get()
}

/** نگاشت بر پایهٔ **همان شیء** (`===`)، نه برابری */
fun <K, V> identityMap(expected: Int): MutableMap<K, V> = java.util.IdentityHashMap(expected)
