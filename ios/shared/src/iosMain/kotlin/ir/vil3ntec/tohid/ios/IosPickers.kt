package ir.vil3ntec.tohid.ios

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.IosContext
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.net.toByteArray
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.setValue
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImageOrientation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.darwin.NSObject

/*
 *  پنجره‌های خودِ iOS پشتِ همان قراردادهای اندروید که کدِ مشترک صدا می‌زند (‎androidx/activity/result/contract‎):
 *
 *  | اندروید | آیفون |
 *  |---|---|
 *  | `PickVisualMedia`، `GetContent("image/…")`، `GetMultipleContents` ِ عکس | PHPicker (بی اجازهٔ گالری) |
 *  | `GetContent`/`GetMultipleContents` ِ دیگر | UIDocumentPicker (رونوشت) |
 *  | `TakePicture(uri)` | دوربینِ UIImagePickerController ⇐ JPEG در همان `uri` |
 *  | `CreateDocument(mime)` | پرونده‌ای در cache؛ کدِ مشترک در آن می‌نویسد و با بستنِ جریان، «ذخیره در Files» (UIDocumentPicker ِ صادرات) |
 *  | `ACTION_SEND` (`createChooser`) | برگهٔ اشتراکِ iOS (UIActivityViewController): متن و/یا پرونده |
 *
 *  خروجی همیشه `file://` در `cacheDir` است (همان که `ContentResolver` ِ iOS باز می‌کند). عکس‌ها JPEG ِ
 *  راست‌شده‌اند (HEIC و چرخشِ EXIF ِ آیفون را Skia ِ `BitmapFactory` لازم ندارد بداند). لغو ⇐ همان پاسخِ
 *  «لغو» ِ اندروید (فهرستِ خالی، null، false).
 */
internal object IosPickers {
  private const val JPEG_QUALITY = 0.92

  // ── عکس ─────────────────────────────────────────────────────────────

  fun pickImages(limit: Int, done: (List<Uri>) -> Unit) = IosUi.onMain {
    val config = PHPickerConfiguration()
    config.selectionLimit = limit.toLong()
    config.filter = PHPickerFilter.imagesFilter()
    val picker = PHPickerViewController(configuration = config)
    val delegate = PhotoDelegate(done)
    picker.delegate = delegate
    IosUi.hold(delegate)
    IosUi.present(picker) { IosUi.release(delegate); done(emptyList()) }
  }

  private class PhotoDelegate(private val done: (List<Uri>) -> Unit) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
      picker.dismissViewControllerAnimated(true, completion = null)
      IosUi.release(this)
      val results = didFinishPicking.filterIsInstance<PHPickerResult>()
      if (results.isEmpty()) {
        done(emptyList())
        return
      }
      val out = arrayOfNulls<Uri>(results.size)
      var left = results.size
      results.forEachIndexed { i, r ->
        r.itemProvider.loadDataRepresentationForTypeIdentifier(UTTypeImage.identifier) { data, _ ->
          val uri = data?.let { d -> UIImage.imageWithData(d)?.let { saveJpeg(it, newFile("picked", "jpg")) } }
          IosUi.onMain {
            out[i] = uri
            if (--left == 0) done(out.filterNotNull())
          }
        }
      }
    }
  }

  // ── دوربین ───────────────────────────────────────────────────────────

  fun takePicture(target: Uri, done: (Boolean) -> Unit) = IosUi.onMain {
    val file = target.path?.let { File(it) }
    if (file == null || !UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)) {
      done(false)
      return@onMain
    }
    val picker = UIImagePickerController()
    picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
    val delegate = CameraDelegate(file, done)
    picker.delegate = delegate
    IosUi.hold(delegate)
    IosUi.present(picker) { IosUi.release(delegate); done(false) }
  }

  private class CameraDelegate(private val file: File, private val done: (Boolean) -> Unit) :
    NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
      picker.dismissViewControllerAnimated(true, completion = null)
      IosUi.release(this)
      val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
      done(image != null && saveJpeg(image, file) != null)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
      picker.dismissViewControllerAnimated(true, completion = null)
      IosUi.release(this)
      done(false)
    }
  }

  // ── پرونده ───────────────────────────────────────────────────────────

  fun pickDocuments(mime: String, multiple: Boolean, done: (List<Uri>) -> Unit) {
    if (mime.startsWith("image/")) return pickImages(if (multiple) 0 else 1, done)
    IosUi.onMain {
      val type = mime.takeIf { it.isNotBlank() && !it.contains('*') }?.let { UTType.typeWithMIMEType(it) } ?: UTTypeData
      val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(type), asCopy = true)
      picker.allowsMultipleSelection = multiple
      val delegate = DocDelegate(done)
      picker.delegate = delegate
      IosUi.hold(delegate)
      IosUi.present(picker) { IosUi.release(delegate); done(emptyList()) }
    }
  }

  private class DocDelegate(private val done: (List<Uri>) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
      IosUi.release(this)
      done(didPickDocumentsAtURLs.mapNotNull { (it as? NSURL)?.let(::copyIn) })
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
      IosUi.release(this)
      done(emptyList())
    }
  }

  /** رونوشتِ برگزیده (در Inbox ِ موقتِ iOS) ⇐ cache ِ برنامه، با همان نام (نامِ پرونده را کدِ مشترک نشان می‌دهد) */
  private fun copyIn(url: NSURL): Uri? {
    val from = url.path ?: return null
    val dir = File(IosContext.cacheDir, "picked/" + NSUUID().UUIDString).apply { mkdirs() }
    val to = File(dir, url.lastPathComponent ?: "file")
    return if (NSFileManager.defaultManager.copyItemAtPath(from, toPath = to.path, error = null)) Uri.fromFile(to) else null
  }

  // ── ذخیره (CreateDocument) ───────────────────────────────────────────

  private val exports = HashSet<String>()

  /** جای پرونده‌ای که کدِ مشترک در آن می‌نویسد؛ با بستنِ جریانش «ذخیره در Files» باز می‌شود */
  fun createDocument(name: String): Uri {
    val dir = File(IosContext.cacheDir, "export/" + NSUUID().UUIDString).apply { mkdirs() }
    val file = File(dir, name.ifBlank { "file" }.replace('/', '_'))
    file.writeBytes(ByteArray(0))
    exports += file.path
    return Uri.fromFile(file)
  }

  /** `ContentResolver.openOutputStream` ِ iOS: جریانِ پرونده‌ای که `createDocument` داد */
  fun exportStream(file: File, append: Boolean): FileOutputStream? =
    if (file.path in exports) ExportStream(file, append) else null

  private class ExportStream(private val file: File, append: Boolean) : FileOutputStream(file, append) {
    private var closed = false

    override fun close() {
      super.close()
      if (closed) return
      closed = true
      exports -= file.path
      IosUi.onMain {
        val url = NSURL.fileURLWithPath(file.path)
        IosUi.present(UIDocumentPickerViewController(forExportingURLs = listOf(url), asCopy = true))
      }
    }
  }

  // ── اشتراک (ACTION_SEND) ─────────────────────────────────────────────

  fun share(intent: Intent) {
    val items = ArrayList<Any>()
    (intent.extras[Intent.EXTRA_TEXT] as? CharSequence)?.toString()?.takeIf { it.isNotBlank() }?.let { items += it }
    when (val stream = intent.extras[Intent.EXTRA_STREAM]) {
      is Uri -> stream.path?.let { items += NSURL.fileURLWithPath(it) }
      is List<*> -> stream.forEach { u -> (u as? Uri)?.path?.let { items += NSURL.fileURLWithPath(it) } }
    }
    if (items.isEmpty()) throw ActivityNotFoundException("iOS: ACTION_SEND بی متن و پرونده")
    IosUi.onMain {
      val sheet = UIActivityViewController(activityItems = items, applicationActivities = null)
      (intent.extras[Intent.EXTRA_SUBJECT] as? CharSequence)?.toString()?.let { sheet.setValue(it, forKey = "subject") }
      IosUi.present(sheet)
    }
  }

  // ── کمکی ─────────────────────────────────────────────────────────────

  private fun newFile(kind: String, ext: String): File {
    val dir = File(IosContext.cacheDir, kind).apply { mkdirs() }
    return File(dir, NSUUID().UUIDString + "." + ext)
  }

  /** JPEG ِ راست‌شده (چرخشِ دوربین در خودِ پیکسل‌ها، نه در EXIF) */
  private fun saveJpeg(image: UIImage, file: File): Uri? {
    val upright = upright(image)
    val data: NSData = UIImageJPEGRepresentation(upright, JPEG_QUALITY) ?: return null
    file.parentFile?.mkdirs()
    file.writeBytes(data.toByteArray())
    return Uri.fromFile(file)
  }

  private fun upright(image: UIImage): UIImage {
    if (image.imageOrientation == UIImageOrientation.UIImageOrientationUp) return image
    val (w, h) = image.size.useContents { width to height }
    UIGraphicsBeginImageContextWithOptions(image.size, false, image.scale)
    image.drawInRect(CGRectMake(0.0, 0.0, w, h))
    val out = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    return out ?: image
  }
}
