package ir.vil3ntec.tohid.data

import android.content.Context

/**
 *  e-paymethods — کلیدِ `tohid-policy:pay_v2` ِ همین دستگاه برای پیش‌فرضِ «ترکیبی و شیفت» (`PayV2Default`، منطقِ خالص).
 *  فقط از شروعِ برنامه (`ShopStore.adoptPayDefault`). انتخابِ صریح (کلید هست) دست نمی‌خورد؛ `ADOPT` ⇒ `true` نوشته می‌شود و
 *  از آن پس مثلِ انتخابِ صریح است؛ `WAIT` ⇒ هیچ (شروعِ بعدی دوباره).
 */
object PayV2Pref {

  /** کلیدِ `pay_v2` روی همین دستگاه هست (کاربر انتخاب کرد، یا پیش‌فرض پیش‌تر پذیرفته شد) */
  fun chosen(context: Context): Boolean = ShopPolicy.prefs(context).contains(ShopPolicy.PAY_V2)

  fun adopt(context: Context, d: ShopData, now: Long): PayV2Default.Step {
    val step = PayV2Default.step(chosen(context), d, now)
    if (step == PayV2Default.Step.ADOPT) ShopPolicy.prefs(context).edit().putBoolean(ShopPolicy.PAY_V2, true).commit()
    return step
  }
}
