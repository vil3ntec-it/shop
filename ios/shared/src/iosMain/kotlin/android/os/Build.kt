package android.os

import platform.UIKit.UIDevice

/*
 *  shimِ iOS ِ `android.os.Build`. `SDK_INT = 0`: هر شاخهٔ «اندروید ≥ X» بسته است، پس کدِ مشترک
 *  هیچ API ِ تازهٔ اندروید را در iOS صدا نمی‌زند و مسیرِ سازگارِ قدیمی را می‌رود.
 */
object Build {
  val MANUFACTURER: String = "Apple"
  val BRAND: String = "Apple"
  val MODEL: String get() = UIDevice.currentDevice.model
  val DEVICE: String get() = UIDevice.currentDevice.model

  object VERSION {
    const val SDK_INT: Int = 0
    val RELEASE: String get() = UIDevice.currentDevice.systemVersion
  }

  object VERSION_CODES {
    const val N = 24
    const val O = 26
    const val P = 28
    const val Q = 29
    const val R = 30
    const val S = 31
    const val TIRAMISU = 33
    const val UPSIDE_DOWN_CAKE = 34
    const val VANILLA_ICE_CREAM = 35
  }
}
