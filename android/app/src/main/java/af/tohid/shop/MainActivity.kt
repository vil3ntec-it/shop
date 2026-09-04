package af.tohid.shop

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.webkit.ServiceWorkerClientCompat
import androidx.webkit.ServiceWorkerControllerCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewFeature
import af.tohid.shop.util.UpdateManager
import kotlinx.coroutines.launch
import java.io.File

/**
 * پوسته‌ی برنامه.
 *
 * خودِ برنامه همان چیزی است که در سایت می‌بینید — همان index.html و همان
 * فایل‌های license/ و sounds/ که داخل بسته گذاشته شده‌اند. پس ظاهر، رنگ،
 * انیمیشن و رفتار مو‌به‌مو یکی است و دو نسخه از هم جدا نمی‌افتند.
 *
 * چیزی از اینترنت بارگذاری نمی‌شود؛ همه‌چیز از داخل خود برنامه می‌آید و
 * بدون اینترنت هم کامل کار می‌کند.
 */
class MainActivity : ComponentActivity() {

    private lateinit var web: WebView
    private lateinit var assetLoader: WebViewAssetLoader

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingCamera: PermissionRequest? = null
    /*
     *  اجازه‌ی لوکیشن، وقتی خودِ صفحه آن را می‌خواهد.
     *
     *  صفحه لوکیشنِ دکان را همان اولِ کار می‌گیرد و به سرور می‌فرستد —
     *  حتی پیش از ثبت‌نام. داخلِ WebView این کار دو اجازه لازم دارد: یکی
     *  اجازه‌ی خودِ اندروید و یکی اجازه‌ای که WebView از میزبانش می‌گیرد.
     *  اینجا هر دو به هم وصل می‌شوند.
     */
    private var pendingGeoOrigin: String? = null
    private var pendingGeoCallback: android.webkit.GeolocationPermissions.Callback? = null
    private var lastBackPress = 0L

    /** انتخاب عکس محصول — فقط از گالری، بدون دوربین. */
    private val pickFile = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val cb = fileCallback
        fileCallback = null
        if (cb == null) return@registerForActivityResult
        val data = result.data
        val uris = when {
            result.resultCode != RESULT_OK -> null
            data?.clipData != null -> Array(data.clipData!!.itemCount) { i ->
                data.clipData!!.getItemAt(i).uri
            }
            data?.data != null -> arrayOf(data.data!!)
            else -> null
        }
        cb.onReceiveValue(uris)
    }

    /** اجازه‌ی دوربین برای اسکن بارکد. */
    private val askCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val request = pendingCamera
        pendingCamera = null
        if (request == null) return@registerForActivityResult
        if (granted) request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
        else {
            request.deny()
            toast("برای اسکن بارکد، اجازه‌ی دوربین لازم است.")
        }
    }

    /** اجازه‌ی لوکیشن برای صفحه. */
    private val askLocation = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val origin = pendingGeoOrigin
        val callback = pendingGeoCallback
        pendingGeoOrigin = null
        pendingGeoCallback = null
        if (origin == null || callback == null) return@registerForActivityResult
        //  «نه» گفتن هیچ بخشی از برنامه را نمی‌بندد؛ فقط لوکیشن ثبت نمی‌شود،
        //  پس پیام و اصراری هم در کار نیست
        callback.invoke(origin, granted.values.any { it }, false)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        assetLoader = WebViewAssetLoader.Builder()
            .setDomain(DOMAIN)
            .addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        // سرویس‌ورکر هم مثل سایت کار کند تا رفتار یکی باشد
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE)) {
            ServiceWorkerControllerCompat.getInstance().setServiceWorkerClient(
                object : ServiceWorkerClientCompat() {
                    override fun shouldInterceptRequest(
                        request: WebResourceRequest,
                    ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
                }
            )
        }

        web = WebView(this)
        val root = FrameLayout(this)
        root.addView(
            web,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        )
        setContentView(root)

        // نوار وضعیت و دکمه‌های پایین گوشی روی برنامه نیفتند
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }

        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            // صدای اسکن باید بدون دست زدن دوباره پخش شود
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = false
            useWideViewPort = false
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            allowFileAccess = false
            allowContentAccess = false
            //  بدون این، `navigator.geolocation` داخلِ اپ همیشه خطا می‌دهد
            setGeolocationEnabled(true)
            // برنامه از این نشانه می‌فهمد که داخل اپ اجرا می‌شود
            userAgentString = "$userAgentString ShopAndroid/${BuildConfig.VERSION_NAME}"
        }
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        web.setBackgroundColor(ContextCompat.getColor(this, R.color.app_background))
        web.overScrollMode = WebView.OVER_SCROLL_NEVER

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView, request: WebResourceRequest,
            ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(
                view: WebView, request: WebResourceRequest,
            ): Boolean {
                val url = request.url
                // صفحه‌های خود برنامه داخل برنامه باز می‌شوند
                if (url.host == DOMAIN) return false
                // بقیه (واتساپ، تماس، لینک بیرونی) به برنامه‌ی مربوطه می‌روند
                return openOutside(url)
            }

            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(DOWNLOAD_BRIDGE, null)
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                val wantsCamera = request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                if (!wantsCamera) { request.deny(); return }
                runOnUiThread {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
                    } else {
                        pendingCamera = request
                        askCamera.launch(Manifest.permission.CAMERA)
                    }
                }
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: android.webkit.GeolocationPermissions.Callback,
            ) {
                runOnUiThread {
                    val granted = ContextCompat.checkSelfPermission(
                        this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(
                            this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        callback.invoke(origin, true, false)
                    } else {
                        pendingGeoOrigin = origin
                        pendingGeoCallback = callback
                        askLocation.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                            )
                        )
                    }
                }
            }

            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams,
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                // فقط گالری؛ دوربین عمداً پیشنهاد نمی‌شود
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                }
                return try {
                    pickFile.launch(Intent.createChooser(intent, "انتخاب عکس"))
                    true
                } catch (e: ActivityNotFoundException) {
                    fileCallback = null
                    callback.onReceiveValue(null)
                    false
                }
            }
        }

        web.addJavascriptInterface(SaveBridge(), "ShopAndroid")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // اول اجازه بده خود برنامه پنجره‌ی باز را ببندد
                web.evaluateJavascript(BACK_HANDLER) { handled ->
                    if (handled == "true") return@evaluateJavascript
                    if (web.canGoBack()) { web.goBack(); return@evaluateJavascript }
                    val now = System.currentTimeMillis()
                    if (now - lastBackPress < 2000) finish()
                    else {
                        lastBackPress = now
                        toast("برای بستن برنامه، دوباره برگشت را بزنید.")
                    }
                }
            }
        })

        if (savedInstanceState == null) {
            web.loadUrl("https://$DOMAIN/index.html")
        } else {
            web.restoreState(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    private fun openOutside(url: Uri): Boolean = try {
        startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        toast("برنامه‌ای برای باز کردن این لینک پیدا نشد.")
        true
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    /**
     * ذخیره‌ی فایل پشتیبان در پوشه‌ی Downloads.
     *
     * در مرورگر، «گرفتن پشتیبان» یک فایل دانلود می‌کند. داخل WebView این
     * کار خودبه‌خود انجام نمی‌شود، پس همان فایل از اینجا در Downloads
     * نوشته می‌شود تا رفتار دقیقاً همان باشد.
     */
    private inner class SaveBridge {

        /**
         * بررسی و نصب نسخه‌ی تازه.
         * برنامه از دکمه‌ی «به‌روزرسانی» در بخش تنظیمات همین را صدا می‌زند.
         */
        @JavascriptInterface
        fun checkUpdate() {
            runOnUiThread {
                toast("در حال بررسی نسخه‌ی تازه…")
                lifecycleScope.launch {
                    when (val state = UpdateManager.check()) {
                        is UpdateManager.State.UpToDate -> toast("برنامه به‌روز است.")
                        is UpdateManager.State.Failed -> toast(state.message)
                        is UpdateManager.State.Available -> {
                            toast("نسخه‌ی ${state.info.version} پیدا شد، در حال دانلود…")
                            val done = UpdateManager.download(this@MainActivity, state.info) { }
                            when (done) {
                                is UpdateManager.State.ReadyToInstall -> {
                                    if (!UpdateManager.canInstall(this@MainActivity)) {
                                        toast("برای نصب، اجازه‌ی «نصب برنامه‌های ناشناس» لازم است.")
                                        UpdateManager.openInstallPermission(this@MainActivity)
                                    } else {
                                        UpdateManager.install(this@MainActivity, done.file)
                                    }
                                }
                                is UpdateManager.State.Failed -> toast(done.message)
                                else -> Unit
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }

        /** نسخه‌ی نصب‌شده — برنامه آن را در تنظیمات نشان می‌دهد. */
        @JavascriptInterface
        fun appVersion(): String = BuildConfig.VERSION_NAME
        @JavascriptInterface
        fun saveFile(base64: String, fileName: String, mimeType: String) {
            val bytes = try {
                Base64.decode(base64, Base64.DEFAULT)
            } catch (e: IllegalArgumentException) {
                runOnUiThread { toast("فایل ذخیره نشد.") }
                return
            }
            val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "shop-backup.json" }
            val mime = mimeType.ifBlank { "application/octet-stream" }

            val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveWithMediaStore(safeName, mime, bytes)
            } else {
                saveToPublicDownloads(safeName, bytes)
            }
            runOnUiThread {
                toast(if (ok) "در پوشه‌ی Downloads ذخیره شد: $safeName" else "فایل ذخیره نشد.")
            }
        }

        private fun saveWithMediaStore(name: String, mime: String, bytes: ByteArray): Boolean = try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri == null) false
            else {
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            }
        } catch (e: Exception) {
            false
        }

        private fun saveToPublicDownloads(name: String, bytes: ByteArray): Boolean = try {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            File(dir, name).outputStream().use { it.write(bytes) }
            true
        } catch (e: Exception) {
            // اگر اجازه‌ی نوشتن نبود، داخل پوشه‌ی خود برنامه ذخیره می‌شود
            try {
                File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name)
                    .outputStream().use { it.write(bytes) }
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    private companion object {
        const val DOMAIN = "appassets.androidplatform.net"

        /**
         * دانلودهای برنامه (پشتیبان و خروجی CSV) از نوع blob هستند و
         * WebView خودش آن‌ها را ذخیره نمی‌کند. این تکه، همان کلیک را
         * می‌گیرد، محتوا را می‌خواند و به سمت اندروید می‌فرستد.
         */
        const val DOWNLOAD_BRIDGE = """
            (function(){
              if (window.__shopDownloadBridge) return;
              window.__shopDownloadBridge = true;

              function send(href, name){
                fetch(href).then(function(r){ return r.blob(); }).then(function(blob){
                  var reader = new FileReader();
                  reader.onload = function(){
                    var s = String(reader.result);
                    var comma = s.indexOf(',');
                    var b64 = comma >= 0 ? s.slice(comma + 1) : s;
                    ShopAndroid.saveFile(b64, name || 'shop-file', blob.type || '');
                  };
                  reader.readAsDataURL(blob);
                }).catch(function(){});
              }

              function isLocal(href){ return /^(blob:|data:)/.test(href || ''); }

              // کلیک معمولی روی لینک دانلود
              document.addEventListener('click', function(e){
                var a = e.target && e.target.closest ? e.target.closest('a[download]') : null;
                if (!a || !isLocal(a.href)) return;
                e.preventDefault();
                send(a.href, a.getAttribute('download'));
              }, true);

              // کلیکی که خود برنامه با کد می‌زند (گرفتن پشتیبان و خروجی CSV)
              var nativeClick = HTMLAnchorElement.prototype.click;
              HTMLAnchorElement.prototype.click = function(){
                var name = this.getAttribute && this.getAttribute('download');
                if (name && isLocal(this.href)) { send(this.href, name); return; }
                return nativeClick.apply(this, arguments);
              };
            })();
        """

        /** اگر پنجره‌ای باز است، دکمه‌ی برگشت همان را ببندد، نه برنامه را. */
        /** اگر پنجره‌ای باز است، دکمه‌ی برگشت همان را ببندد، نه برنامه را. */
        const val BACK_HANDLER = """
            (function(){
              // صفحه‌ی ورود که از داخل برنامه باز شده — برگشت آن را ببندد
              var authClose = document.getElementById('auth-close');
              if (authClose && !authClose.hidden) { authClose.click(); return true; }
              // پنجره‌های برنامه با کلاس open باز می‌شوند
              var open = document.querySelector('.modal-scrim.open, .sheet.open, .drawer.open');
              if (open) {
                if (open.id && typeof window.closeModal === 'function') {
                  window.closeModal(open.id);
                  return true;
                }
                open.classList.remove('open');
                return true;
              }
              // اگر داخل صفحه‌ی فرعی هستیم، به صفحه‌ی قبلی برگردیم
              var back = document.querySelector('.page.active .account-back');
              if (back) { back.click(); return true; }
              return false;
            })();
        """
    }
}
