package af.tohid.shop.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import af.tohid.shop.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * به‌روزرسانی از داخل برنامه.
 *
 * جریان: بررسی آخرین Release گیت‌هاب → دانلود APK → باز کردن نصب‌کننده اندروید.
 * هیچ کد اجرایی از جای دیگری بارگذاری نمی‌شود؛ فقط بسته‌ی رسمی خودِ برنامه
 * نصب می‌شود و اندروید امضایش را بررسی می‌کند.
 *
 * داده‌ی دکان در نصب روی نسخه‌ی قبلی دست‌نخورده می‌ماند — اندروید فقط وقتی
 * پایگاه‌داده را پاک می‌کند که برنامه حذف و از نو نصب شود.
 */
object UpdateManager {

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val info: Updater.Available) : State
        data class Downloading(val percent: Int) : State
        data class ReadyToInstall(val file: File, val info: Updater.Available) : State
        data class Failed(val message: String) : State
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /** پوشه‌ی موقت دانلود؛ هر بار پاک و از نو ساخته می‌شود. */
    private fun updatesDir(context: Context): File =
        File(context.cacheDir, "updates").apply { mkdirs() }

    suspend fun check(): State = withContext(Dispatchers.IO) {
        try {
            val found = Updater.check(BuildConfig.VERSION_NAME)
            if (found == null) State.UpToDate else State.Available(found)
        } catch (e: Exception) {
            State.Failed(friendly(e))
        }
    }

    /**
     * دانلود فایل نصب.
     * @param onProgress درصد پیشرفت (۰ تا ۱۰۰) — اگر سرور طول فایل را ندهد، ۱- می‌آید.
     */
    suspend fun download(
        context: Context,
        info: Updater.Available,
        onProgress: (Int) -> Unit,
    ): State = withContext(Dispatchers.IO) {
        try {
            val dir = updatesDir(context)
            // فایل‌های قدیمی حذف می‌شوند تا حافظه پر نشود
            dir.listFiles()?.forEach { it.delete() }
            val target = File(dir, "tohid-${info.version}.apk")

            val req = Request.Builder().url(info.apkUrl).build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@withContext State.Failed("دانلود ناموفق بود (${res.code})")
                val body = res.body ?: return@withContext State.Failed("پاسخ خالی از سرور")
                val total = if (body.contentLength() > 0) body.contentLength() else info.sizeBytes

                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var readTotal = 0L
                        var lastPercent = -1
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            readTotal += n
                            if (total > 0) {
                                val pct = ((readTotal * 100) / total).toInt().coerceIn(0, 100)
                                if (pct != lastPercent) { lastPercent = pct; onProgress(pct) }
                            } else onProgress(-1)
                        }
                    }
                }
            }

            if (target.length() <= 0) State.Failed("فایل دانلودشده خالی است")
            else State.ReadyToInstall(target, info)
        } catch (e: Exception) {
            State.Failed(friendly(e))
        }
    }

    /**
     * آیا برنامه اجازه‌ی نصب بسته دارد؟
     * از اندروید ۸ به بعد این اجازه جداگانه و برای هر برنامه است.
     */
    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            context.packageManager.canRequestPackageInstalls()
        else true

    /** باز کردن صفحه‌ی تنظیمات تا کاربر اجازه‌ی نصب بدهد. */
    fun openInstallPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${activity.packageName}"),
        )
        runCatching { activity.startActivity(intent) }
            .onFailure { activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)) }
    }

    /** تحویل فایل به نصب‌کننده‌ی اندروید. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun friendly(e: Exception): String = when {
        e is java.net.UnknownHostException -> "اینترنت در دسترس نیست"
        e is java.net.SocketTimeoutException -> "سرور پاسخ نداد"
        else -> e.message ?: "خطای ناشناخته"
    }
}
