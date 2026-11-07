package ir.vil3ntec.tohid.customer.ios

import ir.vil3ntec.tohid.core.shopper.ChatPhoto
import ir.vil3ntec.tohid.core.shopper.ShopperPhoto
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIRectFill
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 *  عکسِ گفت‌وگو روی آیفون (CHAT-05/06) — همتای `PhotoAndroid.kt`: PHPicker (بی اجازهٔ گالری)، بایت‌های عکس، و
 *  کشیدنِ دوباره روی زمینهٔ **سفید** با UIKit (که جهتِ EXIF را خودش راست می‌کند) تا ≤ ۱۶۰۰px و پیش‌نمایش ≤ ۳۲۰px؛
 *  JPEG ِ تازه از روی پیکسل ⇒ EXIF (مکان، دستگاه، زمان) نمی‌رود. کیفیت پله‌پله تا زیرِ سقفِ سرور (`ShopperPhoto`).
 */
object PhotoIos {
  private val held = HashSet<NSObject>()

  fun pick(done: (ChatPhoto?) -> Unit) {
    val config = PHPickerConfiguration()
    config.selectionLimit = 1L
    config.filter = PHPickerFilter.imagesFilter()
    val picker = PHPickerViewController(configuration = config)
    val delegate = Delegate(done)
    picker.delegate = delegate
    held += delegate
    val host = top() ?: run { held -= delegate; return done(null) }
    host.presentViewController(picker, animated = true, completion = null)
  }

  private fun top(): UIViewController? {
    val app = UIApplication.sharedApplication
    val window = app.keyWindow ?: app.windows.firstOrNull() as? UIWindow
    var vc = window?.rootViewController ?: return null
    while (true) vc = vc.presentedViewController ?: break
    return vc
  }

  private class Delegate(private val done: (ChatPhoto?) -> Unit) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
      picker.dismissViewControllerAnimated(true, completion = null)
      held -= this
      val r = didFinishPicking.firstOrNull() as? PHPickerResult ?: return done(null)
      r.itemProvider.loadDataRepresentationForTypeIdentifier(UTTypeImage.identifier) { data, _ ->
        val image = data?.let { UIImage.imageWithData(it) }
        dispatch_async(dispatch_get_main_queue()) { done(image?.let { prepare(it) }) }
      }
    }
  }

  /** روی رشتهٔ اصلی (UIKit) */
  fun prepare(image: UIImage): ChatPhoto? {
    val (w0, h0) = image.size.useContents { width.toInt() to height.toInt() }
    if (w0 <= 0 || h0 <= 0) return null
    val (w, h) = ShopperPhoto.fit(w0, h0, ShopperPhoto.MAIN_PX)
    val main = redraw(image, w, h) ?: return null
    val mainJpeg = jpeg(main, ShopperPhoto.MAIN_QUALITY, ShopperPhoto.MAIN_MAX_BYTES) ?: return null
    val (tw, th) = ShopperPhoto.fit(w, h, ShopperPhoto.THUMB_PX)
    val thumb = redraw(main, tw, th)?.let { jpeg(it, ShopperPhoto.THUMB_QUALITY, ShopperPhoto.THUMB_MAX_BYTES) }
    return ChatPhoto(mainJpeg, thumb, w, h)
  }

  /** جهتِ درست (UIKit `drawInRect`)، زمینهٔ سفید زیرِ شفافیت، و ابعادِ تازه با مقیاسِ ۱ (پیکسلِ واقعی) */
  private fun redraw(image: UIImage, w: Int, h: Int): UIImage? {
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(w.toDouble(), h.toDouble()), true, 1.0)
    UIColor.whiteColor.setFill()
    UIRectFill(CGRectMake(0.0, 0.0, w.toDouble(), h.toDouble()))
    image.drawInRect(CGRectMake(0.0, 0.0, w.toDouble(), h.toDouble()))
    val out = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    return out
  }

  private fun jpeg(image: UIImage, start: Int, max: Int): ByteArray? {
    for (q in ShopperPhoto.qualities(start)) {
      val bytes = UIImageJPEGRepresentation(image, q / 100.0)?.toByteArray() ?: continue
      if (bytes.size <= max) return bytes
    }
    return null
  }
}
