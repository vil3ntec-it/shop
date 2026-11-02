package ir.vil3ntec.tohid.ios

import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.popoverPresentationController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/*
 *  نمایشِ پنجره‌های خودِ iOS (انتخابِ عکس/پرونده، دوربین، اشتراک، ذخیره در Files) روی ریشهٔ Compose — همان کارِ
 *  «Activity ِ دیگر» ِ اندروید. همه روی رشتهٔ اصلی. نماینده‌ها (delegate) در UIKit ضعیف نگه داشته می‌شوند، پس
 *  `hold` آن‌ها را تا پایانِ پنجره نگه می‌دارد.
 */
internal object IosUi {
  private val held = HashSet<NSObject>()

  fun onMain(block: () -> Unit) = dispatch_async(dispatch_get_main_queue()) { block() }

  /** بالاترین UIViewController ِ نمایان (روی پنجرهٔ کلید)؛ نبودش (برنامه هنوز صفحه‌ای ندارد) ⇐ null */
  fun top(): UIViewController? {
    val app = UIApplication.sharedApplication
    val window = app.keyWindow ?: app.windows.firstOrNull { (it as? UIWindow)?.isKeyWindow() == true } as? UIWindow
      ?: app.windows.firstOrNull() as? UIWindow
    var vc = window?.rootViewController ?: return null
    while (true) vc = vc.presentedViewController?.takeIf { !it.isBeingDismissed() } ?: break
    return vc
  }

  /** نمایش؛ روی iPad برگهٔ اشتراک لنگر می‌خواهد ⇐ میانهٔ صفحه */
  fun present(vc: UIViewController, onFail: () -> Unit = {}) = onMain {
    val host = top()
    if (host == null) {
      onFail()
      return@onMain
    }
    vc.popoverPresentationController?.let { pop ->
      pop.sourceView = host.view
      val (w, h) = host.view.bounds.useContents { size.width to size.height }
      pop.sourceRect = CGRectMake(w / 2, h / 2, 1.0, 1.0)
    }
    host.presentViewController(vc, animated = true, completion = null)
  }

  fun hold(o: NSObject) { held += o }

  fun release(o: NSObject) { held -= o }
}
