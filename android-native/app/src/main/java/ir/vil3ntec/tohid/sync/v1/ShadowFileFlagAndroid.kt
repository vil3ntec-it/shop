package ir.vil3ntec.tohid.sync.v1

import android.content.Context
import ir.vil3ntec.tohid.data.LedgerOwner
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 *  پرچمِ دکانِ `shop.sync_shadow_file` (`live_config` ِ سرور، **پیش‌فرض خاموش**) — سایه و cursor ِ Sync v1 در فایلِ
 *  خودشان (`ShadowStore`) به جای SharedPreferences. RULES: قالبِ تازهٔ دادهٔ محلی فقط پشتِ پرچمِ خاموش
 *  (همان `tohid-ledger-store-v1` ِ وب، #228)؛ خاموش کردن هم امن است: جفتِ فایل در شروعِ بعد در prefs می‌نشیند.
 *
 *  از همان پاسخِ `GET /shop/ledger-merge/config?v=3` ِ `LedgerMergeFlag.kick` می‌آید (کلیدِ `shadowFile`، بی درخواستِ
 *  تازه)؛ سرورِ کهنه (بی کلید) ⇒ خاموش. به دکان بسته است و فقط در شروع خوانده می‌شود (`ShadowStore.ensure`).
 */
object ShadowFileFlag {

  private const val PREFS = "tohid-ledger-merge"

  /** فقط آزمون: جای پرچم را می‌گیرد؛ `null` ⇒ همان پرچمِ ذخیره‌شده */
  @Volatile var override: Boolean? = null

  private fun key(context: Context) = "shadow-file:" + LedgerOwner.currentShop(context).ifBlank { "-" }

  fun enabled(context: Context): Boolean = override ?: runCatching {
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(key(context), false)
  }.getOrDefault(false)

  /** پاسخِ `/config` ⇒ روشن؟ فقط `true` ِ صریح (سرورِ کهنه کلید ندارد ⇒ خاموش) */
  fun decide(body: JsonObject): Boolean = (body["shadowFile"] as? JsonPrimitive)?.booleanOrNull == true

  fun set(context: Context, on: Boolean) {
    val app = context.applicationContext
    app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key(app), on).apply()
  }
}
