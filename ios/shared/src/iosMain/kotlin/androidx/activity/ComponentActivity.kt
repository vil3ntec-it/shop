package androidx.activity

/**
 *  shimِ iOS (گامِ ۹) — رده‌ای که `FragmentActivity` از آن می‌آید (مثلِ اندروید)؛ `PayDevice.CustomerScreen` با
 *  `as? ComponentActivity` آن را می‌خواهد. ریشهٔ آیفون (`IosActivity`) هم همین است.
 */
open class ComponentActivity : android.app.Activity()
