package ir.vil3ntec.tohid.data

import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 *  دفترِ حافظهٔ یک نمونهٔ `ShopStore` در برابرِ دیسک و همتاها — ذخیرهٔ کهنه.
 *
 *  ⛔ صفحه (`MainActivity`، `desktop/Main.kt`) و کارگرِ همگام‌سازی (`SyncV1Worker`، هر بار نمونهٔ
 *  تازه) هر کدام `ShopStore` ِ خودشان را دارند. پیش‌تر دفترِ صفحه پس از Pull ِ کارگر تازه نمی‌شد و
 *  ذخیرهٔ بعدیِ صفحه دفترِ کهنه را کامل می‌نوشت: `record` برای هر ردیفی که کارگر آورده بود DELETE و
 *  برای هر ویرایشِ دستگاهِ دیگر برگرداندن به سرور می‌فرستاد. و برعکس: Pull ِ کارگر روی دفترِ کهنهٔ
 *  خودش فروشی را که صفحه در این فاصله ثبت کرد از دیسک پاک می‌کرد.
 *
 *  این‌جا: (۱) هر نوشتن به همتاها می‌رسد ([published] ⇐ `LedgerPeers`) و همان دفتر را می‌گیرند؛
 *  (۲) پیش از هر نوشتن، زیرِ قفلِ نوشتن، نسخه و مُهرِ دیسک سنجیده می‌شود و فقط اگر دیسک جلوتر است
 *  دفتر از نو خوانده می‌شود ([freshLocked])؛ (۳) دفترهایی که پیش از جایگزینی از بیرون روی صفحه بودند
 *  پایهٔ ادغامِ سه‌طرفهٔ `ShopStore.save` می‌مانند ([baseOf] ⇐ `LedgerMerge`).
 *
 *  بی `Context` تا آزمون‌پذیر باشد. «Locked» یعنی فراخوان قفلِ نوشتنِ دفتر (`SyncV1Engine`) را دارد.
 */
internal class LedgerFollow(
  private val file: File,
  private val data: MutableStateFlow<ShopData>,
  /** دفترِ رمزدارِ خوانده‌نشده (SEC-01): نه همتا می‌گیرد، نه از نو خوانده می‌شود — راهش بازسازی است */
  private val unreadable: () -> Boolean,
  /** دفترِ دیسک؛ `null` اگر خوانده نشد (رمز، دیسک، JSON) */
  private val readDisk: () -> ShopData?,
  /** پیش از اصلاح: پذیرفته ولی بی اثر (آزمون‌ها کامپایل شوند و سرخ) */
  @Suppress("unused") private val enabled: () -> Boolean = { true },
) {
  private val peers = LedgerPeers.of(file)

  /** نسخه، مُهر و دورهٔ دیسک که دفترِ حافظه با آن خوانده یا نوشته شد */
  @Volatile private var seenGen = -1L
  @Volatile private var seenStamp = LedgerPeers.Stamp.MISSING
  @Volatile private var seenEpoch = -1L

  /** تا `load` تمام نشده نوشتنِ همتا گرفته نمی‌شود — خودِ `load` تازه‌ترین را می‌خواند */
  @Volatile private var following = false

  /** حداکثر [EARLIER] دفترِ پیش از جایگزینی از بیرون (همتا، دیسک، دفترِ ادغام‌شده، بازیابی) */
  private val earlier = ArrayDeque<ShopData>()

  private val member = LedgerPeers.Member { next, gen, stamp -> adopt(next, gen, stamp) }

  init {
    peers.join(member)
  }

  /** نشانِ دیسک **پیش از** خواندنِ `load` — نوشتنی که میانِ خواندن برسد در [loadedLocked] دیده می‌شود */
  class Mark internal constructor(internal val gen: Long, internal val epoch: Long, internal val stamp: LedgerPeers.Stamp)

  fun mark(): Mark = Mark(peers.gen, peers.epoch, LedgerPeers.Stamp.of(file))

  /** پایانِ `load`: از این‌جا همتاها دنبال می‌شوند؛ همتایی که میانِ خواندن نوشت ⇒ همین حالا از نو */
  fun loadedLocked(m: Mark) {
    seenEpoch = m.epoch
    if (!unreadable() && peers.gen != m.gen && peers.epoch == m.epoch) {
      reloadLocked()
    } else {
      seenGen = m.gen
      seenStamp = m.stamp
    }
    following = true
  }

  /** دفترِ حافظه از بیرون عوض شد؛ دفترِ پیشین پایهٔ ممکنِ صفحه می‌ماند */
  fun replace(next: ShopData) {
    remember(data.value)
    data.value = next
  }

  fun remember(d: ShopData) {
    synchronized(earlier) {
      if (earlier.lastOrNull() !== d) earlier.addLast(d)
      while (earlier.size > EARLIER) earlier.removeFirst()
    }
  }

  /** دفترِ حسابِ دیگر، یا خالی: هیچ پایهٔ کهنه‌ای نمی‌ماند */
  fun reset(d: ShopData) {
    synchronized(earlier) { earlier.clear() }
    data.value = d
  }

  /** دفتری که صفحه [next] را با آن ساخت (شرح سرِ `LedgerMerge.baseOf`) */
  fun baseOf(next: ShopData): ShopData? = LedgerMerge.baseOf(next, data.value, synchronized(earlier) { earlier.toList() })

  /** همتا همین حالا نوشت — زیرِ قفلِ نوشتنِ همان همتا */
  private fun adopt(next: ShopData, gen: Long, stamp: LedgerPeers.Stamp) {
    if (!following || unreadable() || seenEpoch != peers.epoch) return
    replace(next)
    seenGen = gen
    seenStamp = stamp
  }

  /** همین نمونه [next] را روی دیسک نشاند: همتاها همان را می‌گیرند */
  fun publishedLocked(next: ShopData) {
    val stamp = LedgerPeers.Stamp.of(file)
    seenGen = peers.wrote(member, next, stamp)
    seenStamp = stamp
  }

  /** دفترِ حافظه همان دیسکِ همین لحظه است (خوانده شد بی آن‌که همتاها خبر شوند) */
  fun syncedLocked() {
    seenGen = peers.gen
    seenStamp = LedgerPeers.Stamp.of(file)
  }

  /** دفترِ حسابِ دیگری جای این فایل ننشسته */
  fun sameAccount(): Boolean = seenEpoch == peers.epoch

  /**
   *  دفترِ همین لحظهٔ دیسک. فقط وقتی دیسک جلوتر است (نسخهٔ این فرایند یا مُهرِ فایل عوض شده) از نو
   *  خوانده می‌شود.
   *
   *  @return `null` ⇒ نباید نوشت: دیسک جلوتر است و خوانده نشد، یا دفترِ حسابِ دیگری جای این نشسته
   */
  fun freshLocked(): ShopData? {
    //  دفترِ خوانده‌نشده هیچ نوشتنی نمی‌گیرد؛ پیامش را `ShopStore.writeToDisk` می‌دهد
    if (unreadable()) return data.value
    if (!sameAccount()) return null
    if (seenGen == peers.gen && LedgerPeers.Stamp.of(file) == seenStamp) return data.value
    return reloadLocked()
  }

  private fun reloadLocked(): ShopData? {
    val gen = peers.gen
    val stamp = LedgerPeers.Stamp.of(file)
    //  فایل نیست: چیزی جلوتر از دفترِ حافظه نیست (همان رفتارِ همیشه)
    if (stamp == LedgerPeers.Stamp.MISSING) {
      seenGen = gen
      seenStamp = stamp
      return data.value
    }
    val next = readDisk() ?: return null
    replace(next)
    seenGen = gen
    seenStamp = stamp
    return next
  }

  /**
   *  دفتر به حسابِ دیگری رفت یا از آن آمد (`stashTo`، `openFrom`): همتاهای پیشین — کارگرِ نیمه‌راه —
   *  دیگر روی این فایل نمی‌نویسند. [stop] ⇒ تا `load` ِ بعدی همتا گرفته نمی‌شود.
   */
  fun movedLocked(stop: Boolean, next: ShopData) {
    val (gen, epoch) = peers.moved()
    seenGen = gen
    seenEpoch = epoch
    seenStamp = LedgerPeers.Stamp.of(file)
    if (stop) following = false
    reset(next)
  }

  private companion object {
    /** چند دفترِ پیشین پایهٔ ممکنِ صفحه می‌مانند — هر کدام شاید یک دفترِ کامل در حافظه */
    const val EARLIER = 2
  }
}
