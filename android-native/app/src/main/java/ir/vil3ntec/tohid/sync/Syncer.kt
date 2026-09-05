package ir.vil3ntec.tohid.sync

import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.SyncRepository
import kotlinx.serialization.json.JsonArray

/**
 *  یک دورِ کاملِ همگام‌سازی.
 *
 *  ترتیبش مهم است و عمداً همان ترتیبِ نسخهٔ وب است:
 *    ۱) تغییراتِ خودم را می‌فرستم و همان‌جا سایه را تازه می‌کنم
 *    ۲) تغییراتِ دیگران را صفحه‌به‌صفحه می‌گیرم و ادغام می‌کنم
 *    ۳) سایه را دوباره ثبت می‌کنم
 *
 *  گامِ سه بی‌اهمیت به نظر می‌رسد ولی نیست: بدونِ آن، داده‌ای که تازه از
 *  سرور گرفته‌ام دفعهٔ بعد به‌عنوانِ «تغییرِ محلی» دوباره فرستاده می‌شود و
 *  همگام‌سازی هیچ‌وقت آرام نمی‌گیرد.
 */
class Syncer(
  private val store: ShopStore,
  private val state: SyncStore,
  private val context: android.content.Context,
) {

  /** لایهٔ شبکه از نقطهٔ اتصال می‌آید؛ اینجا نه نشانی ساخته می‌شود نه توکن */
  private val api by lazy { Backend.sync(context) }

  data class Outcome(
    val pushed: Int,
    val pulled: Int,
    val revision: Long,
    /** تغییرهایی که سرور قبول نکرد و نسخهٔ او جایشان نشست */
    val rejected: List<SyncRepository.Conflict> = emptyList(),
  )

  /**
   *  چند تغییرِ محلی هنوز نرفته — پیش از فرستادن خبر داده می‌شود.
   *
   *  `AutoSync` با همین عدد نقطهٔ بالای صفحه را زرد می‌کند و می‌گوید
   *  «۳ مورد در انتظار». بدون این، کاربر تا وقتی چیزی نمی‌ترکید
   *  نمی‌فهمید کارش روی گوشی مانده.
   */
  var onCollected: (Int) -> Unit = {}

  suspend fun run(): Outcome {
    if (!Backend.tokens(context).signedIn) throw ApiFailure.SessionExpired()
    val device = state.deviceUid

    // ۱) فرستادن
    val outgoing = SyncEngine.collect(store.data.value, state.shadow, System.currentTimeMillis())
    onCollected(outgoing.changes.size)
    var rejected: List<SyncRepository.Conflict> = emptyList()
    if (outgoing.changes.isNotEmpty()) {
      val result = api.push(device, outgoing.changes, outgoing.settings)
      state.shadow = SyncEngine.snapshot(store.data.value)

      /*
       *  تغییری که سرور رد کرد، بی‌صدا گم نمی‌شود.
       *
       *  تا دیروز `conflicts` خوانده نمی‌شد. یعنی اگر شریکِ شما همان
       *  فاکتور را زودتر عوض کرده بود، ویرایشِ شما رد می‌شد، سایه
       *  «فرستاده شد» ثبت می‌کرد، و کارِ شما بی‌هیچ پیامی ناپدید
       *  می‌شد — بدتر: چون rev رکورد عوض نشده بود، در `pull` بعدی هم
       *  نمی‌آمد و دو طرف تا ابد ناهمگام می‌ماندند.
       *
       *  حالا سرور نسخهٔ خودش را همراه تعارض می‌فرستد و همان‌جا جای
       *  نسخهٔ محلی می‌نشیند. داورِ نهایی سرور است — همان قاعده‌ای که
       *  برای بقیهٔ تغییرها هم هست — و کاربر می‌بیند چه چیزی اعمال نشد.
       */
      rejected = result.conflicts
      if (rejected.isNotEmpty()) applyServerVersions(rejected)
    }

    // ۲) گرفتن — صفحه‌به‌صفحه، با سقفی که حلقهٔ بی‌پایان نسازد
    var since = state.revision
    var pulled = 0
    var guard = 0
    while (guard++ < 50) {
      val page = api.pull(since, device)
      if (page.changes.isNotEmpty() || page.settings != null) {
        val merged = SyncEngine.merge(store.data.value, page.changes, page.settings)
        if (merged.touched > 0) store.save(merged.data)
        pulled += merged.touched
      }
      since = page.rev
      if (!page.hasMore) break
    }

    // ۳) آنچه تازه رسید نباید دفعهٔ بعد به‌عنوان تغییرِ محلی برگردد
    state.shadow = SyncEngine.snapshot(store.data.value)
    state.revision = since
    state.lastSyncAt = System.currentTimeMillis()

    onCollected(0)
    return Outcome(
      pushed = outgoing.changes.size,
      pulled = pulled,
      revision = since,
      rejected = rejected,
    )
  }

  /** نسخهٔ سرور را جای رکوردهای ردشده می‌نشاند */
  private suspend fun applyServerVersions(conflicts: List<SyncRepository.Conflict>) {
    val incoming = kotlinx.serialization.json.buildJsonArray {
      conflicts.forEach { conflict ->
        //  رکوردی که سرور نفرستاده، چیزی برای نشاندن ندارد؛ دست نمی‌خورد
        if (!conflict.deleted && conflict.record == null) return@forEach
        add(
          kotlinx.serialization.json.buildJsonObject {
            put("collection", kotlinx.serialization.json.JsonPrimitive(conflict.collection))
            put("id", kotlinx.serialization.json.JsonPrimitive(conflict.id))
            put("deleted", kotlinx.serialization.json.JsonPrimitive(conflict.deleted))
            conflict.record?.let { put("data", it) }
          }
        )
      }
    }
    if (incoming.size == 0) return
    val merged = SyncEngine.merge(store.data.value, incoming, null)
    if (merged.touched > 0) store.save(merged.data)
    state.shadow = SyncEngine.snapshot(store.data.value)
  }

  /** گرفتن یا تازه‌کردنِ مجوزِ اشتراک */
  suspend fun refreshLicense(deviceName: String): License.Status {
    if (!Backend.tokens(context).signedIn) return License.Status(License.State.NONE, reason = "no_account")

    //  کلیدِ سنجاق‌شده از سرور پرسیده نمی‌شود: سرور — یا هر کسی که خود
    //  را جای سرور جا بزند — نباید بتواند کلیدِ سنجشِ امضا را عوض کند
    if (!state.publicKeyPinned && state.publicKey.isNullOrBlank()) {
      state.publicKey = runCatching { api.publicKey() }.getOrNull()
    }

    val body = api.license(state.deviceUid, deviceName)
    val license = body["license"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }

    if (!license.isNullOrBlank()) {
      // مجوز پیش از ذخیره بررسی می‌شود تا مجوزِ خراب ذخیره نشود
      val key = state.publicKey
      if (key != null &&
        License.verify(license, key, state.deviceUid, state.accountId) is License.Verdict.Valid
      ) {
        state.license = license
      }
    } else {
      /*
       *  سرور صریحاً گفت اشتراکی نیست — پس مجوزِ قدیمی همان‌جا برداشته
       *  می‌شود.
       *
       *  تا دیروز فقط وقتی چیزی نوشته می‌شد که مجوزی آمده باشد. یعنی
       *  اگر اشتراکی وسطِ دوره لغو می‌شد، مجوزِ قبلی تا روزِ انقضای
       *  خودش روی گوشی کار می‌کرد. حالا همان لحظه قفل می‌شود.
       *
       *  فقط با پاسخِ خودِ سرور — نه با خطای شبکه: اگر درخواست شکست
       *  بخورد، این تابع اصلاً به اینجا نمی‌رسد و مجوزِ آفلاین سرِ جایش
       *  می‌ماند تا خودش تمام شود.
       */
      val reason = body["reason"]
        ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
        .orEmpty()
      if (reason == "no_subscription" || reason == "no_shop") state.license = null
    }
    return status()
  }

  /** وضعیتِ اشتراک — با همان نگهبان‌هایی که `LicenseGuard` دارد */
  fun status(): License.Status = LicenseGuard.status(context, state)

  private fun JsonArray.isNotEmpty(): Boolean = size > 0
}
