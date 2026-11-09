package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.net.ApiEndpoints
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.data.repo.Backend

/**
 *  پس از ورود و گرفتنِ دفتر از سرور: تنظیماتِ دکان (`tohid-policy`، `tohid-receipt`، `tohid-modules` — `ShopSettingsBundle`)
 *  و عکس‌های کالا از **تازه‌ترین پشتیبانِ ابریِ** همین حساب برمی‌گردند — یک بار برای هر حساب@دکان روی این دستگاه.
 *
 *  ⛔ فقط آنچه این دستگاه هنوز ندارد: کلیدِ تنظیم (`ShopSettingsBundle.apply(onlyMissing = true)` از راهِ `commitPhotos`)،
 *  عکسِ نبوده (`commitPhotos(onlyMissing = true)`) و نگه‌داشته‌ها فقط روی دستگاهِ بی فاکتورِ باز و فقط از پشتیبانِ همین دستگاه
 *  (`DeviceDocsBundle.commitHeld(sameMachineOnly)`). دفترِ درونِ پشتیبان **نشانده نمی‌شود** — دفتر از سرور آمده و تازه‌تر است.
 *  نبودِ پشتیبان، شبکه یا فایلِ ناخوانا ⇒ هیچ کاری (بارِ بعد دوباره، جز «پشتیبانی نیست» که یک بار بس است).
 */
object CloudSettingsRestore {

  private const val PREFS = "tohid-settings-restore"

  suspend fun once(context: Context, store: ShopStore): Int {
    val app = context.applicationContext
    val user = LedgerOwner.currentUser(app)
    if (user.isBlank() || store.training || !Backend.isReady(app)) return 0
    val mark = "done:$user@${LedgerOwner.currentShop(app)}"
    val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    if (prefs.getBoolean(mark, false)) return 0
    val items = (Backend.backups(app).list() as? ApiResult.Success)?.value?.items ?: return 0
    val newest = items.maxByOrNull { it.createdAt }
    if (newest == null) { prefs.edit().putBoolean(mark, true).apply(); return 0 }
    val bytes = runCatching { Backend.api(app).getBytes(ApiEndpoints.Backups.one(newest.id)) }.getOrNull() ?: return 0
    if (BackupBundle.read(app, bytes.inputStream(), store::parseBackup).isFailure) {
      BackupBundle.dropStaging(app)
      return 0
    }
    val photos = BackupBundle.commitPhotos(app, onlyMissing = true)
    //  DATA-SAFETY §۸: نگه‌داشته‌ها و سبدِ جاری — فقط روی دستگاهِ بی فاکتورِ باز، همان دکان، نه آن‌که در دفترِ سرور فروش رفته،
    //  و فقط پشتیبانی که همین دستگاه نوشت (این راه خودکار است؛ گوشیِ دیگرِ همان حساب شاید هنوز همان‌ها را باز دارد)
    DeviceDocsBundle.commitHeld(app, store.data.value, sameMachineOnly = true)
    prefs.edit().putBoolean(mark, true).apply()
    return photos
  }
}
