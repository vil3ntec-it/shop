package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.ledger.AppendOnce

/**
 *  رسید و قرضِ دستیِ قرض‌داران (کامپیوتر: پروندهٔ مشتری، F7 و F6؛ گوشی: «قرض‌داران») — تنها درِ ثبتشان.
 *
 *  ⛔ پیش‌تر صفحه `DebtorEngine.addTransaction(دفترِ صفحه)` می‌ساخت و `store.save` می‌کرد: اگر در این فاصله
 *  Pull ِ همگام‌سازی ردیفِ دستگاهِ دیگری را نشانده بود (رسیدِ همان مشتری از گوشی، فروش)، دفترِ کهنهٔ صفحه
 *  روی آن می‌نشست و `record` برایش DELETE به سرور می‌فرستاد. کارگرِ همگام‌سازی (`SyncV1Worker`) نمونهٔ
 *  `ShopStore` ِ خودش را دارد، پس حافظهٔ نمونهٔ صفحه هم تازه نیست. حالا ثبت روی
 *  دفترِ همین لحظهٔ دیسک ساخته می‌شود (`AppendOnce` ِ `core` از راهِ همان `ShopStore.update`، [ledgerNow]) و فقط
 *  ردیفِ تازه و سابقه‌اش افزوده می‌شوند.
 *
 *  هر ثبت یک کلید دارد که صفحه با باز کردنِ ردیف یا پنجرهٔ ورود می‌سازد و **همان شناسهٔ ردیفِ قرض** است؛ دو
 *  Enter یا دو ضربهٔ پشتِ‌سرِهم ⇒ یک ردیف ([Result.Ok] با `again`). قاعده‌های مبلغ، سقف و سررسید همان `DebtorEngine`.
 */
object DebtEntry {

  data class Entry(
    val debtorId: String,
    val kind: DebtorEngine.Kind,
    val amount: Double,
    val date: String,
    val notes: String,
    val dueDate: String? = null,
    val overLimit: Boolean = false,
  )

  sealed interface Result {
    /** ثبت شد؛ [again] ⇒ همین کلید پیش‌تر ثبت شده بود و این بار چیزی نوشته نشد */
    data class Ok(val txId: String, val again: Boolean) : Result

    /** ردِ `DebtorEngine` روی دفترِ همین لحظه (مبلغ، سقف، قرض‌دارِ حذف‌شده) */
    data class Failed(val message: String) : Result

    /** قفلِ نرم یا دیسک — پیامش را `AppRoot` از `ShopStore.blocked` / `diskError` می‌دهد */
    data object NotSaved : Result

    /**
     *  پاسخِ هر دو صفحه: رد ⇒ پیامش؛ [NotSaved] ⇒ هیچ (پیام از `AppRoot`؛ ورود باز می‌ماند تا با همان کلید دوباره
     *  بزند)؛ ثبت ⇒ [onOk]، و [done] فقط بارِ اول — ضربهٔ دوم چیزی ننوشت و پیامِ دوم نمی‌گیرد.
     */
    fun show(toast: (String) -> Unit, done: String, onOk: () -> Unit) {
      when (this) {
        is Failed -> toast(message)
        NotSaved -> Unit
        is Ok -> { onOk(); if (!again) toast(done) }
      }
    }
  }

  /** ردیفِ قرضی با همین کلید در دفتر هست */
  fun has(d: ShopData, key: String): Boolean = d.transactions.any { it.id == key }

  /**
   *  ساختنِ ردیف روی دفترِ [cur] (همان دفترِ همین لحظه که `AppendOnce` می‌دهد) با شناسهٔ [key]. خالص؛ ردِ
   *  `DebtorEngine` ⇒ `null` و پیامش در [fail]. [check] قاعدهٔ بیشترِ فراخوان روی **همین** [cur] (صندوق:
   *  `TillReceiveFlow.check`)؛ پیامِ رد ⇒ همان `null` و [fail]، پیش از ساختنِ ردیف.
   */
  fun change(
    e: Entry, today: String, now: Long, newId: () -> String,
    check: (ShopData) -> String? = { null },
    fail: (String) -> Unit,
  ): (ShopData, String) -> ShopData? =
    { cur, key ->
      val r = check(cur)?.let { DebtorEngine.Result.Failed(it) } ?: DebtorEngine.addTransaction(
        cur, e.debtorId, e.kind, e.amount, e.date, e.notes, today, now, newId,
        dueDate = e.dueDate, overLimit = e.overLimit, txId = key,
      )
      when (r) {
        is DebtorEngine.Result.Ok -> r.data
        is DebtorEngine.Result.Failed -> { fail(r.message); null }
      }
    }

  fun result(out: AppendOnce.Outcome<ShopData>, key: String, failed: String?): Result = when (out) {
    is AppendOnce.Outcome.Wrote -> Result.Ok(key, again = false)
    AppendOnce.Outcome.Already -> Result.Ok(key, again = true)
    AppendOnce.Outcome.Skipped -> Result.Failed(failed ?: NOT_SAVED)
    AppendOnce.Outcome.Unread -> Result.Failed(NOT_REREAD)
    AppendOnce.Outcome.Unsaved -> Result.NotSaved
  }

  /**
   *  دفترِ همین لحظه برای تبدیلِ `ShopStore.update` (زیرِ همان قفلِ موتور): **دیسک**، نه [mem] ِ این نمونه که
   *  پس از Pull ِ کارگر کهنه است. فایل نیست یا دفترِ رمزدارِ خوانده‌نشده (SEC-01) ⇒ همان [mem] (`writeToDisk`
   *  دومی را خودش رد می‌کند)؛ فایل هست ولی خوانده نشد ⇒ `null`: هیچ نوشتنی. همان راهِ `PayFixPref.record` (POS-40).
   */
  internal fun ledgerNow(store: ShopStore, mem: ShopData): ShopData? =
    if (store.unreadable.value != null || !store.hasData()) mem else store.readDiskLocked()

  /**
   *  ثبت روی دفترِ همین لحظهٔ دیسک، یک بار برای [key] (شناسهٔ ردیف). [now] ساعتِ دستگاه از صفحه. [check] — قاعدهٔ
   *  بیشترِ فراخوان روی همان دفترِ همین لحظه، زیرِ همان قفل و پس از «همین کلید هست؟» ([change])
   */
  suspend fun record(store: ShopStore, key: String, e: Entry, today: String, now: Long, check: (ShopData) -> String? = { null }): Result {
    var failed: String? = null
    val out = AppendOnce.append(key, ::has, change(e, today, now, ::auditId, check) { failed = it }) { t ->
      store.update { mem -> t(ledgerNow(store, mem)) }
    }
    return result(out, key, failed)
  }

  const val NOT_SAVED = "ثبت نشد"
  const val NOT_REREAD = "دفترِ روی دیسک دوباره خوانده نشد، پس این ثبت انجام نشد تا چیزی از دفتر رونویسی نشود. دوباره امتحان کنید."
}
