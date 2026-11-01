package ir.vil3ntec.tohid.data

import java.io.File
import java.lang.ref.WeakReference

/**
 *  نمونه‌های `ShopStore` ِ همین فرایند روی یک فایلِ دفتر — صفحه (`MainActivity`، `desktop/Main.kt`)
 *  و کارگرِ پس‌زمینه (`SyncV1Worker` هر بار نمونهٔ تازه می‌سازد؛ روی کامپیوتر هم در همین فرایند).
 *
 *  هر نوشتنِ موفق ([wrote]) زیرِ قفلِ نوشتنِ دفتر به همتاها می‌رسد و همان دفتر را می‌گیرند — صفحه
 *  ردیف‌های کارگر را می‌بیند و کارگر فروشِ صفحه را. نسخه ([gen]) و مُهرِ فایل ([Stamp]) نشان
 *  می‌دهند دیسک از دفترِ یک نمونه جلوتر است یا نه؛ فقط آن وقت دفتر از نو خوانده می‌شود.
 *
 *  بی `Context` تا آزمون‌پذیر باشد. همهٔ قفل‌های این‌جا برگ‌اند: زیرشان هیچ قفلِ دیگری گرفته نمی‌شود.
 */
internal class LedgerPeers private constructor() {

  /** زمانِ تغییر و اندازهٔ فایل — نوشتنِ بیرون از این فرایند، یا کوچِ رمز (`AtRest.converge`) */
  data class Stamp(val modified: Long, val length: Long) {
    companion object {
      val MISSING = Stamp(0, -1)
      fun of(f: File): Stamp = if (f.isFile) Stamp(f.lastModified(), f.length()) else MISSING
    }
  }

  fun interface Member {
    /** زیرِ قفلِ نوشتنِ نویسنده صدا زده می‌شود: سبک بماند و قفلی نگیرد */
    fun adopt(next: ShopData, gen: Long, stamp: Stamp)
  }

  /** با هر نوشتنِ دفتر در این فرایند یکی جلو می‌رود */
  @Volatile var gen: Long = 0
    private set

  /** با هر جابه‌جاییِ دفتر میانِ حساب‌ها (`stashTo`، `openFrom`) یکی جلو می‌رود */
  @Volatile var epoch: Long = 0
    private set

  private val members = ArrayList<WeakReference<Member>>()

  fun join(m: Member) {
    synchronized(members) {
      members.removeAll { it.get() == null }
      members += WeakReference(m)
    }
  }

  /** [from] همین حالا [next] را نوشت (مُهرِ پس از نوشتن [stamp]). @return نسخهٔ تازه */
  fun wrote(from: Member, next: ShopData, stamp: Stamp): Long {
    val g = synchronized(members) { ++gen }
    for (m in alive()) if (m !== from) m.adopt(next, g, stamp)
    return g
  }

  /** دفترِ حسابِ دیگری جای این فایل نشست: همتاهای پیشین دیگر نمی‌نویسند. @return (نسخه، دوره) */
  fun moved(): Pair<Long, Long> = synchronized(members) { ++gen; ++epoch; gen to epoch }

  private fun alive(): List<Member> = synchronized(members) { members.mapNotNull { it.get() } }

  companion object {
    private val all = HashMap<String, LedgerPeers>()

    fun of(file: File): LedgerPeers = synchronized(all) { all.getOrPut(file.absoluteFile.path) { LedgerPeers() } }
  }
}
