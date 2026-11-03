package android.hardware.display

import android.view.Display

/*
 *  shimِ iOS (گامِ ۹) — نمایشگرِ دومِ مشتری (`PayDevice.CustomerScreen`، `Presentation` ِ اندروید). `getSystemService(DISPLAY_SERVICE)`
 *  ِ آیفون null است و این رده هرگز ساخته نمی‌شود؛ صفحهٔ دومِ مشتری روی آیفون نیست (همان `PayDeviceIos` ِ پیشین).
 */
internal class DisplayManager private constructor() {
  fun getDisplays(category: String): Array<Display> = emptyArray()

  companion object {
    const val DISPLAY_CATEGORY_PRESENTATION = "android.hardware.display.category.PRESENTATION"
  }
}
