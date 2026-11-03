package android.app

import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.View

/** shimِ iOS (گامِ ۹) — `PayDevice.CustomerScreen`؛ بی `Display` (‎android/hardware/display‎) هرگز ساخته نمی‌شود */
internal open class Presentation(outerContext: Context, display: Display) {
  val context: Context = outerContext

  protected open fun onCreate(savedInstanceState: Bundle?) {}

  fun setContentView(view: View) {}

  fun show() {
    onCreate(null)
  }

  fun dismiss() {}
}
