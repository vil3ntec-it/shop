package androidx.fragment.app

/**
 *  shimِ iOS — `context as? FragmentActivity` (قفل با Face ID/Touch ID، ‎data/FingerprintIos‎): `LocalContext` ِ
 *  ریشهٔ آیفون همان `ir.vil3ntec.tohid.ios.IosActivity` است، مثلِ اندروید که `LocalContext` همان Activity است.
 */
open class FragmentActivity : android.app.Activity()
