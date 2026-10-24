package ir.vil3ntec.tohid.sync.v1

import android.content.Context
import ir.vil3ntec.tohid.core.config.AppConfig
import ir.vil3ntec.tohid.core.sync.ReplayBook
import ir.vil3ntec.tohid.data.LedgerArchive
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.sync.v3.SyncChoice
import ir.vil3ntec.tohid.sync.v3.SyncV3Runner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlin.math.min
import kotlin.math.pow

/**
 *  موتورِ Sync v1 — همان کاری که `license/sync-engine.js` در وب می‌کند.
 *
 *      ShopStore.save()  ⇒  record()  ⇒  تفاضل ⇒ صف ⇒ Push
 *      سوکتِ «changed» یا کارِ دوره‌ای  ⇒  Pull ⇒ اعمال روی دفتر
 *
 *  ── قاعده‌هایی که نباید بشکنند ──────────────────────────────────
 *  ⛔ **opی که سرور نپذیرفته از صف بیرون نمی‌رود.** فقط `applied` و
 *     `duplicate`. `rejected` بیرون می‌رود ولی با دلیل در دفترِ کنار.
 *  ⛔ **۴۲۶ یعنی نگه‌دار، نه دور بریز** (بندِ ۲۰.۶).
 *  ⛔ **حالِ هر حساب جداست.** بی این، سایهٔ حسابِ قبلی روی یک گوشیِ
 *     مشترک همهٔ ردیف‌هایش را «تغییرِ تازه» می‌دید.
 *  ⚠️ **اعمالِ opهای رسیده باید سایه را هم جلو ببرد**، وگرنه تفاضلِ
 *     بعدی همان‌ها را به سرور پس می‌فرستد — حلقهٔ بی‌پایان.
 */
class SyncV1Engine private constructor(context: Context) {

  private val app = context.applicationContext
  private val json = Json { ignoreUnknownKeys = true; isLenient = true }
  private val store = SyncStore(app)

  /** کلیدِ حساب — نشستِ نبوده هم کلیدِ خودش را دارد. */
  private var accountKey: String = keyOf()
  private var state = SyncStateV1(app, accountKey)
  private var queue = OpQueue(app, accountKey)

  /**
   *  سه تکهٔ حال (صف، سایه، cursor) برای commitِ ژورنالی — F0-05، شرح سرِ
   *  `SyncCommit`. همیشه حال و صفِ **همین حالا**ی حساب را می‌دهد.
   */
  private val parts = object : SyncCommit.Parts {
    override fun shadow(): String = state.shadow
    override fun commit(shadow: String, cursor: Long?) {
      if (!state.commitShadow(shadow, cursor)) throw java.io.IOException("سایهٔ همگام‌سازی روی دیسک ننشست")
    }
    override fun queued(): Set<String> = queue.all().mapTo(HashSet()) { it.opId }
    override fun push(ops: List<SyncOp>) = queue.push(ops)
  }
  private var commit = commitFor(accountKey)

  private fun commitFor(key: String) =
    SyncCommit(SyncCommit.Journal(java.io.File(app.filesDir, "sync-journal-${OpQueue.fileKey(key)}.json")), parts)

  /** §۹.۵: سرور از پشتیبان برگشت — شرح سرِ `V1Restore` و `core/sync/RestoreReplay.kt` */
  private var restore = restoreFor(accountKey)

  private fun restoreFor(key: String) =
    V1Restore(state, queue, ReplayBook.inDir(app.filesDir, "sync-replay-${OpQueue.fileKey(key)}"), commit) {
      LedgerArchive.archivedIds(app)
    }

  private val gate = Mutex()
  private val _status = MutableStateFlow(snapshotStatus())
  val status: StateFlow<SyncStatus> = _status.asStateFlow()

  @Volatile private var busy = false
  @Volatile var liveConnected: Boolean = false
    internal set
  @Volatile private var attempt = 0

  /**
   *  کلیدِ حساب — شناسهٔ کاربر اگر معلوم باشد، وگرنه نامِ حساب، وگرنه
   *  خودِ دستگاه. هر سه پایدارند و هیچ‌کدام بینِ دو حساب مشترک نیست.
   */
  private fun keyOf(): String =
    SyncStateV1.currentAccount(app)
      .ifBlank { store.accountName }
      .ifBlank { store.deviceUid }

  /** پس از ورود یا خروج صدا زده می‌شود — کلیدها عوض می‌شوند. */
  fun rebind() {
    val fresh = keyOf()
    if (fresh == accountKey) return
    accountKey = fresh
    state = SyncStateV1(app, accountKey)
    queue = OpQueue(app, accountKey)
    commit = commitFor(accountKey)
    restore = restoreFor(accountKey)
    publish()
  }

  /**
   *  بازیابیِ commitِ نیمه‌مانده — هنگامِ شروع، با دفتری که همین حالا از
   *  دیسک خوانده شد (`ShopStore.load`). شرح سرِ `SyncCommit`.
   */
  @Synchronized
  fun recover(ledger: ShopData) {
    //  Sync v3 پشتِ تنظیمِ هر دستگاه (پیش‌فرض v1) — شرح سرِ `sync/v3/SyncV3Runner.kt`
    if (SyncChoice.isV3(app)) { SyncV3Runner.of(app).recover(ledger); return }
    //  A00: مهرِ سایه پیش از بازیابی خوانده می‌شود؛ سایهٔ بی مهرِ ساختِ پیشین یک بار و پیش از
    //  نخستین تفاضل درمان، و opهای آن ساخت در صف نشان می‌شوند (شرح سرِ `SyncCommit.start`)
    val start = commit.start(ledger) { upTo -> state.legacyOpsUpTo = upTo }
    if (start.outcome == SyncCommit.Outcome.MISMATCH) {
      //  نباید پیش بیاید؛ اگر آمد، دیده شود
      state.lastError = "ژورنالِ همگام‌سازی با سایه نخواند"
    }
    if (start.outcome != SyncCommit.Outcome.NONE) publish()
  }

  /* ==========================================================
     ۱) هر تغییرِ دفتر ⇒ op
     ========================================================== */

  /**
   *  تنها درِ ساختنِ op. `ShopStore.save()` این را صدا می‌زند و هیچ
   *  نوشتنِ دیگری در دفتر نیست.
   *
   *  @return چند op ساخته شد
   */
  @Synchronized
  fun record(data: ShopData): Int {
    if (SyncChoice.isV3(app)) return SyncV3Runner.of(app).record(data)
    /*
     *  نخستین بار روی این دستگاه (سایه‌ای نیست): دفترِ امروز «تغییر» نیست،
     *  حالتِ پایه است. اگر همین‌جا تفاضل می‌گرفتیم، کلِ دفتر یک‌جا به سرور
     *  می‌رفت — دقیقاً همان کاری که قانونِ طلایی منع کرده. فرستادنِ دفترِ
     *  موجود کارِ `baseline()` است و فقط یک بار.
     *
     *  ⛔ صف و سایه با هم (F0-05): اگر میانِ این دو بسته شود، ذخیرهٔ بعدی
     *  همان تغییرها را با op_idِ تازه می‌فرستاد و `$inc` دو بار جمع می‌شد.
     *  شرح سرِ `SyncCommit`.
     */
    val made = commit.record(data)
    publish()
    return made
  }

  /**
   *  بایگانیِ دفتر: ردیف‌هایی که به بایگانی می‌روند از سایهٔ همین حساب هم
   *  بیرون می‌آیند، زیرِ همان قفلِ `record()` — شرح سرِ `SyncCommit.forget`.
   *  شکست پرتاب می‌شود تا `LedgerArchive.compact` دفتر را کوتاه نکند.
   */
  @Synchronized
  fun forgetArchived(gone: Map<String, Collection<String>>): Int =
    if (SyncChoice.isV3(app)) SyncV3Runner.of(app).forgetArchived(gone) else commit.forget(gone)

  /**
   *  دفترِ امروز را یک‌جا به سرور می‌دهد — فقط برای دستگاهِ نخست.
   *
   *  ⚠️ تنها جایی است که «کلِ دفتر» می‌رود و عمدی است: حسابی که تازه
   *  ساخته شده روی سرور هیچ ردیفی ندارد. دستگاهِ دوم همین را از
   *  `snapshot` می‌گیرد، نه از این راه.
   */
  fun baseline(data: ShopData): Int {
    val made = commit.baseline(data)
    publish()
    return made
  }

  /* ==========================================================
     ۲) یک دورِ کامل: Push تا ته، بعد Pull
     ========================================================== */

  /**
   *  @param read  دفترِ همین حالا
   *  @param write دفترِ تازه پس از اعمالِ opهای رسیده
   */
  suspend fun runOnce(read: () -> ShopData, write: (ShopData) -> Unit): Result<Unit> =
    gate.withLock {
      if (!Backend.isReady(app)) { publish(); return@withLock Result.success(Unit) }
      if (!Backend.isOnline(app)) { publish(); return@withLock Result.success(Unit) }
      busy = true
      publish()
      try {
        //  ژورنالِ نیمه‌مانده پیش از هر Push/Pull (F0-05)
        if (commit.pending()) synchronized(this) { commit.recover(read()) }
        val client = SyncV1Client(Backend.api(app))
        val device = store.deviceUid

        //  §۹.۵: سرور از پشتیبان برگشته؟ (هر دور) opهای گم‌شده — خودِ دستگاه و گرفته‌شده‌ها — به ترتیبِ سرور جلوی صف
        val epoch = try {
          client.epoch()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
          throw cancelled
        } catch (_: Throwable) {
          null
        }
        synchronized(this) { restore.check(epoch) }

        //  صف را تا ته خالی می‌کنیم، دسته‌دسته
        try {
          pushQueue(client, device)
        } catch (upgrade: SyncV1Client.UpgradeRequired) {
          return@withLock Result.failure(upgrade)
        }

        /*
         *  §۹.۵: سرور برگشته و صف خالی شد ⇒ دفتر از Snapshot از نو، ردیف‌های تنهای همین دستگاه
         *  (جز آنچه سرور حذف‌شده می‌داند) می‌مانند و «درج» می‌روند. تا آن وقت Pull نه — opِ
         *  دوباره‌فرستادهٔ دستگاهِ دیگر روی دفترِ کهنه دو بار می‌نشست.
         */
        if (state.restorePending && queue.size() == 0) {
          val snapshot = client.snapshot()
          val ask = synchronized(this) { restore.localOnly(snapshot, read()) }
          val gone = if (ask.isEmpty()) emptySet() else client.deleted(ask)
          val built = synchronized(this) {
            if (commit.pending()) commit.recover(read())
            try {
              restore.rebuild(snapshot, read(), gone, write)
            } catch (e: Throwable) {
              //  نیمه‌کاره در همین اجرا: همان‌جا با دفترِ همین حالا تمام یا برگردانده می‌شود
              //  (همان کارِ `absorbRemote`) — ژورنالِ ساختنِ دوباره هیچ‌گاه بی‌صدا پاک نمی‌شود
              runCatching { commit.recover(read()) }
              throw e
            }
          }
          if (built) {
            state.snapshotAt = System.currentTimeMillis()
            //  ردیف‌های تنها (اگر بود) همین حالا بروند
            try {
              pushQueue(client, device)
            } catch (upgrade: SyncV1Client.UpgradeRequired) {
              return@withLock Result.failure(upgrade)
            }
          }
        }

        //  Pull — تا وقتی سرور بگوید چیزِ دیگری هست
        var rounds = 0
        while (!state.restorePending && rounds < 50) {
          rounds++
          val got = client.pull(device, state.cursor)
          //  §۹.۵: opهای دیگران با شماره‌شان — **پیش از** نشاندن — برای دوباره فرستادن پس از بازیابی
          if (got.seen.isNotEmpty()) restore.seen(got.seen)
          if (got.ops.isNotEmpty()) absorbRemote(got.ops, read, write, got.cursor)
          state.cursor = got.cursor
          state.lastPullAt = System.currentTimeMillis()
          if (got.serverSchema > 0) state.serverSchema = got.serverSchema
          if (!got.hasMore) break
        }

        attempt = 0
        state.lastError = ""
        state.lastOkAt = System.currentTimeMillis()
        Result.success(Unit)
      } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
      } catch (error: Throwable) {
        attempt++
        state.lastError = error.message ?: "خطای نامشخص"
        Result.failure(error)
      } finally {
        busy = false
        publish()
      }
    }

  /**
   *  فرستادنِ صف تا ته، دسته‌دسته — همان حلقهٔ همیشگیِ `runOnce`، جدا تا دفترِ رمزدارِ
   *  خوانده‌نشده هم بتواند صفش را بفرستد (`pushPending`، `rebuildFromServer`). صدازننده `gate` را دارد.
   *
   *  ⛔ §۹.۵: پذیرفته‌شده‌ها با server_seq کنارِ صف (`V1Restore.accepted`) **پیش از** بیرون رفتن
   *  از آن — تا اگر سرور از پشتیبان برگشت، با همان op_id دوباره بروند؛ ردِ opِ دوباره‌فرستاده بی‌صدا.
   *
   *  @throws SyncV1Client.UpgradeRequired سرور عقب‌تر است: هیچ opی پاک نمی‌شود
   */
  private suspend fun pushQueue(client: SyncV1Client, device: String) {
    var guard = 0
    while (queue.size() > 0 && guard < 500) {
      guard++
      val batch = queue.batch()
      if (batch.isEmpty()) break
      //  A00: opهای ساختِ پیش از A00 بی `caps` می‌روند تا تورِ سرور null ِ کهنه‌شان را بپالاید
      val legacy = state.legacyOpsUpTo
      if (legacy.isNotEmpty() && queue.all().none { LedgerDiff.isLegacyOp(it.opId, legacy) }) state.legacyOpsUpTo = ""
      //  §۹.۵: opِ دوباره‌فرستاده (شاید از ساختِ دیگری و پیش از A00) هم بی `caps` — بدترین اثرِ تور «کهنه ماندن» است
      val caps = LedgerDiff.capsFor(batch.map { it.opId }, state.legacyOpsUpTo) && batch.none { restore.isReplay(it.opId) }
      val out = try {
        client.push(device, batch, queue.size(), caps = caps)
      } catch (upgrade: SyncV1Client.UpgradeRequired) {
        //  سرور عقب‌تر است: هیچ opی پاک نمی‌شود
        state.holding = true
        state.lastError = upgrade.reason
        publish()
        throw upgrade
      }
      state.holding = false
      state.serverSchema = out.serverSchema
      state.upgradeAvailable = out.upgradeAvailable

      val accepted = out.results.filter { it.accepted }.map { it.opId }
      val rejected = out.results.filter { !it.accepted }
      if (accepted.isNotEmpty()) {
        restore.accepted(batch, out.results)
        queue.ack(accepted)
      }
      //  ردِ opِ دوباره‌فرستاده (پیش‌تر پذیرفته یا گرفته شده بود) ردشده‌ای نیست که کاربر ببیند
      val (replayed, refused) = rejected.partition { restore.isReplay(it.opId) }
      if (replayed.isNotEmpty()) queue.ack(replayed.map { it.opId })
      if (refused.isNotEmpty()) {
        //  ردشده‌ها از صف بیرون می‌روند — وگرنه صف تا ابد قفل
        //  می‌کند — ولی بی‌صدا نه.
        val ids = refused.map { it.opId }.toHashSet()
        queue.drop(batch.filter { it.opId in ids }, "rejected")
        queue.ack(ids)
      }
      state.lastPushAt = System.currentTimeMillis()
    }
  }

  /* ==========================================================
     SEC-01 — دفترِ رمزداری که این دستگاه باز نمی‌کند (بازبینیِ #331)
     ========================================================== */

  /**
   *  فقط فرستادنِ صف — بی Pull، بی تفاضل، بی نشاندن.
   *
   *  دفترِ رمزدار باز نشد (کلید نیست یا گذرا در دسترس نیست) ولی صفِ opها رمزدار نیست
   *  (`AtRestScope.syncState`): تغییرهایی که پیش از آن ثبت شده بودند باید به سرور برسند —
   *  هم برای خودشان، هم چون «بازسازی از سرور» فقط با صفِ خالی انجام می‌شود. pull نه، چون
   *  نشاندنِ opها روی دفترِ خالیِ حافظه یعنی رونویسیِ دفترِ واقعی.
   *
   *  @return چند op هنوز در صف مانده
   */
  suspend fun pushPending(): Result<Int> = gate.withLock {
    if (!Backend.isReady(app) || !Backend.isOnline(app)) { publish(); return@withLock Result.success(queue.size()) }
    busy = true
    publish()
    try {
      //  opهای ژورنالِ «ثبت» که هنوز به صف نرسیده بودند (همان `op_id`)
      synchronized(this) { commit.queueRecorded() }
      pushQueue(SyncV1Client(Backend.api(app)), store.deviceUid)
      Result.success(queue.size())
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
      throw cancelled
    } catch (error: Throwable) {
      attempt++
      state.lastError = error.message ?: "خطای نامشخص"
      Result.failure(error)
    } finally {
      busy = false
      publish()
    }
  }

  /**
   *  «بازسازی از سرور»: صف اول، بعد Snapshot؛ کناری ⇐ سایه و cursor ⇐ جابه‌جایی — شرح سرِ `SyncRebuild`.
   *
   *  @param base از آن فقط بخش‌های بیرون از همگام‌سازی (تنظیمات) برمی‌دارد
   *  @param files کناری و جابه‌جاییِ دفتر (`ShopStore`)؛ زیرِ قفلِ همین موتور صدا زده می‌شوند
   *  @throws Exception شبکه یا دیسک؛ تا پیش از `commitShadow` هیچ چیزی عوض نشده است
   */
  internal suspend fun rebuildFromServer(base: ShopData, files: SyncRebuild.Staging): SyncRebuild.Outcome = gate.withLock {
    busy = true
    publish()
    try {
      synchronized(this) { commit.queueRecorded() }
      val client = SyncV1Client(Backend.api(app))
      val device = store.deviceUid
      //  §۹.۵: سرور از پشتیبان برگشته؟ opهای گم‌شده (خودی و گرفته‌شده، دفترچه رمزدار نیست) پیش از
      //  Snapshot جلوی صف ⇒ دفترِ بازسازی‌شده آن‌ها را هم دارد
      val epoch = try {
        client.epoch()
      } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
      } catch (_: Throwable) {
        null
      }
      synchronized(this) { restore.check(epoch) }
      val out = SyncRebuild(object : SyncRebuild.Io {
        override fun queued(): Int = queue.size()
        override suspend fun push() = pushQueue(client, device)
        override fun shadow(): String = state.shadow
        override suspend fun snapshot(): JsonObject = client.snapshot()
        override fun stage(next: ShopData, shadowSha: String) = synchronized(this@SyncV1Engine) { files.stage(next, shadowSha) }
        override fun commitShadow(shadow: String, cursor: Long) {
          if (!state.commitShadow(shadow, cursor)) throw java.io.IOException("سایهٔ همگام‌سازی روی دیسک ننشست")
        }
        override fun swap() = synchronized(this@SyncV1Engine) { files.swap() }
      }).run(base)
      if (out is SyncRebuild.Outcome.Done) {
        val now = System.currentTimeMillis()
        //  دفتر حالا همان سرور است (با opهای دوباره‌رفته) ⇒ ساختنِ دوبارهٔ §۹.۵ دیگر لازم نیست
        if (state.restorePending) state.commitRestore(state.epoch, false)
        state.snapshotAt = now
        state.reconciledAt = now
        state.lastOkAt = now
        state.lastError = ""
        attempt = 0
      }
      out
    } finally {
      busy = false
      publish()
    }
  }

  /**
   *  آیا این حساب روی این دستگاه هرگز با سرور هم‌تراز شده؟ اگر شده، بازیابی بی سرور ممکن نیست:
   *  ردیفی که در پشتیبان هست و سرور ندارد شاید حذف‌شده باشد و `prepare` زنده‌اش می‌کرد.
   */
  fun everSynced(): Boolean = state.cursor > 0 || state.reconciledAt > 0 || state.snapshotAt > 0

  /** اثرِ سایهٔ همین حالا — برای `SyncRebuild.atLoad` */
  fun shadowSha(): String = SyncRebuild.sha(state.shadow)

  /**
   *  بی سرور و **هرگز همگام‌نشده**: دفترِ بازیابی‌شده خودش سایه می‌شود ⇐ هیچ op؛ cursor صفر و
   *  «هم‌ترازی» از نو. کناری و نشان پیش از سایه، جابه‌جایی پس از آن — همان ترتیبِ بازسازی.
   */
  @Synchronized
  internal fun resetTo(ledger: ShopData, files: SyncRebuild.Staging) {
    val shadow = SyncRebuild.shadowFor(ledger)
    files.stage(ledger, SyncRebuild.sha(shadow))
    if (!state.commitShadow(shadow, 0L)) throw java.io.IOException("سایهٔ همگام‌سازی روی دیسک ننشست")
    state.reconciledAt = 0L
    state.snapshotAt = 0L
    files.swap()
    publish()
  }

  /**
   *  هم‌ترازیِ یک‌باره با سرور — پیش از نخستین دورِ این حساب روی این دستگاه.
   *
   *  ── چرا ───────────────────────────────────────────────────────────
   *  `firstSync` در اندروید هرگز صدا زده نمی‌شد. یعنی ردیف‌هایی که پیش از
   *  آمدنِ v1 روی گوشی بودند هرگز op نشدند و فقط موتورِ قدیم آن‌ها را
   *  می‌برد. حالا که موتورِ قدیم دیگر به دفتر دست نمی‌زند (دو موتور روی
   *  یک دفتر، `$inc`ها را دو بار می‌شمرد)، این‌جا یک بار جبران می‌شود:
   *
   *   • دفترِ خالی ⇒ همان Snapshot (گوشیِ نو).
   *   • دفترِ پر ⇒ فقط ردیف‌هایی که **سرور ندارد** به‌صورتِ INSERT می‌روند.
   *     ردیفی که سرور دارد دست نمی‌خورد؛ فرستادنِ دوباره‌اش مقدارِ تازهٔ
   *     دستگاهِ دیگر را با مقدارِ کهنهٔ این‌جا می‌پوشاند.
   *   • اگر این دستگاه هنوز هیچ opی نگرفته (`cursor == 0`)، cursor سرِ
   *     Snapshot می‌نشیند و ردیف‌هایی که فقط سرور دارد به دفتر اضافه
   *     می‌شوند — وگرنه pull از صفر همهٔ تاریخچه را روی دفتری می‌ریخت که
   *     همان‌ها را از راهِ قدیم دارد و `$inc`ها دو بار جمع می‌شدند.
   *  ──────────────────────────────────────────────────────────────────
   */
  suspend fun prepare(read: () -> ShopData, write: (ShopData) -> Unit) {
    if (state.reconciledAt > 0) return
    gate.withLock {
      if (state.reconciledAt > 0) return@withLock
      val body = SyncV1Client(Backend.api(app)).snapshot()
      val server = LedgerDiff.fromSnapshot(body, JsonObject(emptyMap()))
      val serverCursor = (body["cursor"] as? kotlinx.serialization.json.JsonPrimitive)
        ?.content?.toLongOrNull() ?: 0L
      synchronized(this) {
        val local = LedgerDiff.toJson(read())
        val localRows = LedgerDiff.COLLECTIONS.sumOf { (local[it] as? JsonArray)?.size ?: 0 }
        if (localRows == 0) {
          val next = LedgerDiff.fromSnapshot(body, local)
          write(LedgerDiff.fromJson(next))
          writeShadow(next)
          state.cursor = serverCursor
          state.snapshotAt = System.currentTimeMillis()
        } else {
          val missing = LedgerDiff.diff(server, local).filter { it.type == SyncOp.INSERT }
          if (missing.isNotEmpty()) queue.push(missing)
          if (state.cursor == 0L) {
            val next = LedgerDiff.unionRows(local, server)
            write(LedgerDiff.fromJson(next))
            val shadow = readShadow()
            writeShadow(if (shadow != null) LedgerDiff.unionRows(shadow, server) else next)
            state.cursor = serverCursor
          }
        }
        state.reconciledAt = System.currentTimeMillis()
      }
    }
    publish()
  }

  /**
   *  نشاندنِ opهای رسیده — زیرِ **همان قفلِ** `record()`.
   *
   *  پیش‌تر «بخوان ← اعمال کن ← بنویس ← سایه» فقط زیرِ `gate` بود و
   *  `record()` قفلِ دیگری داشت. فروشی که وسطِ این چهار گام ثبت می‌شد،
   *  با نوشتنِ دفترِ کهنه از روی گوشی پاک می‌شد و سایه هم «فرستاده شد»
   *  ثبتش می‌کرد. حالا `ShopStore` هم نوشتنِ خودش را زیرِ همین قفل
   *  (`synchronized(engine)`) انجام می‌دهد.
   *
   *  سایه هم به‌جای «کلِ دفترِ امروز»، خودِ سایه به‌اضافهٔ همین opهاست؛
   *  تغییرِ محلی‌ای که هنوز op نشده، با این کار گم نمی‌شود.
   */
  @Synchronized
  internal fun absorbRemote(
    ops: List<SyncOp>,
    read: () -> ShopData,
    write: (ShopData) -> Unit,
    cursor: Long? = null,
  ) {
    /*
     *  ⛔ دفتر، سایه و cursor با هم (F0-05): اگر دفتر نوشته شود و سایه و
     *  cursor نه، همان opها دوباره گرفته و روی دفتر جمع می‌شدند و تفاضلِ
     *  بعدی آن‌ها را «تغییرِ من» به سرور پس می‌فرستاد.
     */
    try {
      commit.absorb(ops, read(), cursor, write)
    } catch (e: Throwable) {
      //  نیمه‌کاره در همین اجرا (نه بسته شدن): همان‌جا با دفترِ همین حالا
      //  تمام یا برگردانده می‌شود، پیش از آنکه ذخیرهٔ محلی‌ای برسد
      runCatching { commit.recover(read()) }
      throw e
    }
  }

  /* ==========================================================
     ۳) Snapshot — گوشیِ نو، یک بار
     ========================================================== */

  suspend fun restoreFromServer(read: () -> ShopData, write: suspend (ShopData) -> Unit): Int {
    val client = SyncV1Client(Backend.api(app))
    //  §۹.۵: دورانِ پیش از Snapshot — دفتر حالا همان سرور است، پس همان دوران و دیگر «در انتظار» نه
    val epoch = try {
      client.epoch()
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
      throw cancelled
    } catch (_: Throwable) {
      null
    }
    val body = client.snapshot()
    val next = LedgerDiff.fromSnapshot(body, LedgerDiff.toJson(read()))
    write(LedgerDiff.fromJson(next))
    writeShadow(next)
    state.cursor = (body["cursor"] as? kotlinx.serialization.json.JsonPrimitive)
      ?.content?.toLongOrNull() ?: 0L
    state.snapshotAt = System.currentTimeMillis()
    state.lastOkAt = System.currentTimeMillis()
    state.lastError = ""
    if (epoch != null) state.commitRestore(epoch.id, false)
    publish()
    return (body["rows"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: 0
  }

  /**
   *  نخستین همگام‌سازیِ این دستگاه با این حساب.
   *
   *  دفترِ خالی ⇒ Snapshot (گوشیِ نو). دفترِ پر ⇒ یک بار فرستادنِ همان
   *  دفتر، چون این گوشی تا امروز آفلاین کار می‌کرده.
   */
  suspend fun firstSync(read: () -> ShopData, write: (ShopData) -> Unit) {
    if (state.snapshotAt > 0 || state.cursor > 0) return
    val data = read()
    val tree = LedgerDiff.toJson(data)
    val rows: Int = LedgerDiff.COLLECTIONS.sumOf { key ->
      (tree[key] as? kotlinx.serialization.json.JsonArray)?.size ?: 0
    }
    if (rows == 0) { restoreFromServer(read) { write(it) }; return }
    /*
     *  ⚠️ **اول فرستادن، بعد گرفتن** — و این ترتیب عمدی است. اگر اول
     *  pull می‌کردیم، سایه روی «دفترِ ادغام‌شده» می‌نشست و ردیف‌های
     *  محلی — که سرور آن‌ها را ندارد — دیگر «تغییر» شمرده نمی‌شدند و
     *  هیچ‌وقت نمی‌رفتند.
     */
    baseline(data)
    runOnce(read, write)
  }

  /* ==========================================================
     ۴) وضعیت و عقب‌نشینی
     ========================================================== */

  /** عقب‌نشینیِ نمایی: ۲، ۴، ۸ … تا پنج دقیقه، بی‌نهایت تلاش. */
  fun backoffMs(): Long {
    val ms = min(MAX_BACKOFF_MS.toDouble(), MIN_BACKOFF_MS * 2.0.pow(attempt)).toLong()
    return ms
  }

  fun snapshotStatus(): SyncStatus {
    //  Sync v3 پشتِ تنظیمِ هر دستگاه (پیش‌فرض v1): همان نوار و قفلِ خروج، از صفِ v3
    if (SyncChoice.isV3(app)) SyncV3Runner.of(app).asV1Status()?.let { return it }
    val queued = runCatching { queue.size() }.getOrDefault(0)
    return SyncStatus(
      dot = SyncDot.of(
        error = state.lastError.isNotBlank(),
        online = Backend.isOnline(app),
        signedIn = Backend.tokens(app).signedIn,
        configured = AppConfig.isConfigured(app),
        queued = queued,
        busy = busy,
      ),
      queued = queued,
      dropped = runCatching { queue.droppedCount() }.getOrDefault(0),
      cursor = state.cursor,
      lastOkAt = state.lastOkAt,
      //  §۹.۵: هشدارِ «دفترچه همهٔ پنجره را نداشت» — نوشته، ولی چراغ را قرمز نمی‌کند
      lastError = state.lastError.ifBlank { notice() },
      holding = state.holding,
      upgradeAvailable = state.upgradeAvailable,
      schemaVersion = LedgerDiff.SCHEMA_VERSION,
      serverSchema = state.serverSchema,
      live = liveConnected,
    )
  }

  internal fun publish() { _status.value = snapshotStatus() }

  /** §۹.۵: پس از بازیابیِ سروری که دفترچه همهٔ پنجره‌اش را نداشت، چند روز دیده شود */
  private fun notice(): String {
    val at = runCatching { state.restoreNoticeAt }.getOrDefault(0L)
    return if (at > 0 && System.currentTimeMillis() - at < RESTORE_NOTICE_MS) RESTORE_NOTICE else ""
  }

  fun queueSize(): Int = queue.size()
  fun droppedOps(): List<JsonObject> = queue.dropped()
  fun errorReports(): Boolean = state.errorReports
  fun setErrorReports(on: Boolean) { state.errorReports = on }
  fun deviceId(): String = store.deviceUid

  private fun readShadow(): JsonObject? {
    val raw = state.shadow
    if (raw.isBlank()) return null
    return runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
  }

  /**
   *  سایهٔ Snapshot (`prepare`، `restoreFromServer`) — ⛔ A00: از درِ مدلِ همین ساخت
   *  (`LedgerDiff.modelTree`)، مثلِ `SyncV3Engine.shape`. ردیفِ خامِ سرور کلیدهایی دارد که
   *  دفترِ این ساخت نمی‌تواند نگه دارد؛ اگر در سایه می‌ماندند، پس از به‌روزرسانی به ساختی
   *  که آن‌ها را می‌شناسد نخستین ذخیرهٔ بی‌ربط همه را `null` می‌فرستاد.
   */
  private fun writeShadow(tree: JsonObject) {
    state.shadow = LedgerDiff.shadowOf(LedgerDiff.modelTree(tree)).toString()
  }

  companion object {
    const val MIN_BACKOFF_MS = 2_000L
    const val MAX_BACKOFF_MS = 5 * 60_000L

    /** §۹.۵: هشدارِ پوششِ دفترچه، سه روز */
    const val RESTORE_NOTICE_MS = 3 * 24 * 60 * 60_000L
    const val RESTORE_NOTICE =
      "سرور از پشتیبان برگشت و این دستگاه همهٔ کارهای پس از پشتیبان را نگه نداشته بود؛ " +
        "فروش‌ها و ردیف‌های تازهٔ همین دستگاه فرستاده شدند، ولی ویرایش‌های پیش از آن را یک بار با دفتر بسنجید."

    @Volatile private var instance: SyncV1Engine? = null

    fun of(context: Context): SyncV1Engine =
      instance ?: synchronized(this) {
        instance ?: SyncV1Engine(context).also { instance = it }
      }
  }
}
