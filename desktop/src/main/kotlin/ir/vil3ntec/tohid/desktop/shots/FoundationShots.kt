package ir.vil3ntec.tohid.desktop.shots

import ir.vil3ntec.tohid.ui.v3.gallery.KitGallery
import ir.vil3ntec.tohid.ui.v3.gallery.KitPart
import ir.vil3ntec.tohid.ui.v3.modes.ModeChooser

/**
 *  عکس‌های پایه: کیت در همهٔ حالت‌ها، «بیشتر» از روی ماژول‌ها، صفحهٔ «امنیت» که
 *  مستقیم از «بیشتر» باز می‌شود، هشدارها، انتخاب‌گرِ حالت و ماژولِ نمونه.
 */
object FoundationShots : GroupShots {
  override val group = "foundation"
  override val shots = listOf(
    Shot("kit-basics", content = { KitGallery(KitPart.BASICS) }),
    Shot("kit-forms", content = { KitGallery(KitPart.FORMS) }),
    Shot("kit-lists", content = { KitGallery(KitPart.LISTS) }),
    Shot("kit-states", content = { KitGallery(KitPart.STATES) }),
    Shot("more", route = "more"),
    Shot("settings-security", route = "settings/security"),
    Shot("updates", route = "app/updates"),
    Shot("mode-chooser", content = { ModeChooser {} }),
    Shot("example", route = "example"),
  )
}
