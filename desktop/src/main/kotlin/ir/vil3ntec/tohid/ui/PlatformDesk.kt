package ir.vil3ntec.tohid.ui

/** برنامهٔ کامپیوتر — شرح در ‎android-native/.../ui/Platform.kt‎ */
object Platform {
  const val DESKTOP: Boolean = true

  /** «اسناد ⇐ فروشگاه من ⇐ پشتیبان» — جایی که کاربر خودش پیدا می‌کند و با فلش کپی می‌کند */
  fun defaultBackupDir(context: android.content.Context): java.io.File? {
    val home = System.getProperty("user.home") ?: return null
    val docs = java.io.File(home, "Documents").takeIf { it.isDirectory } ?: java.io.File(home)
    return java.io.File(docs, "فروشگاه من/پشتیبان")
  }

  fun pickFolder(start: String?): String? {
    var picked: String? = null
    val run = Runnable {
      val ch = javax.swing.JFileChooser(start ?: System.getProperty("user.home")).apply {
        fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
        dialogTitle = "پوشهٔ پشتیبان را انتخاب کنید"
        applyComponentOrientation(java.awt.ComponentOrientation.RIGHT_TO_LEFT)
      }
      if (ch.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) picked = ch.selectedFile?.absolutePath
    }
    if (java.awt.EventQueue.isDispatchThread()) run.run() else java.awt.EventQueue.invokeAndWait(run)
    return picked
  }

  fun openFolder(path: String): Boolean = runCatching {
    val f = java.io.File(path).apply { mkdirs() }
    java.awt.Desktop.getDesktop().open(f)
    true
  }.getOrDefault(false)

  /**
   *  «ذخیره به‌عنوان» با پنجرهٔ خودِ سیستم، پیش‌فرض در «اسناد»؛ بعد از ذخیره
   *  فایل با برنامهٔ پیش‌فرضش (مثلاً اکسل) باز می‌شود.
   */
  /**
   *  اثرِ انگشتِ همین کامپیوتر برای «شناسهٔ کامپیوتر»ِ کدِ آفلاین.
   *
   *  ویندوز ‎MachineGuid‎ (با نصبِ دوبارهٔ برنامه عوض نمی‌شود)، مک
   *  ‎IOPlatformUUID‎، لینوکس ‎/etc/machine-id‎. هیچ‌کدام نبود ⇒ شناسهٔ
   *  ذخیره‌شدهٔ خودِ برنامه. یک بار خوانده و نگه داشته می‌شود.
   */
  @Volatile private var fingerprint: String? = null

  fun machineFingerprint(context: android.content.Context): String = fingerprint ?: run {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    fun cmd(vararg c: String): String = runCatching {
      val p = ProcessBuilder(*c).redirectErrorStream(true).start()
      val out = p.inputStream.bufferedReader().readText()
      p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
      out
    }.getOrDefault("")
    val id = when {
      os.contains("win") -> Regex("MachineGuid\\s+REG_SZ\\s+([0-9A-Fa-f-]+)")
        .find(cmd("reg", "query", "HKLM\\SOFTWARE\\Microsoft\\Cryptography", "/v", "MachineGuid"))?.groupValues?.get(1)?.let { "win-" + it.lowercase() }
      os.contains("mac") -> Regex("\"IOPlatformUUID\" = \"([^\"]+)\"")
        .find(cmd("ioreg", "-rd1", "-c", "IOPlatformExpertDevice"))?.groupValues?.get(1)?.let { "mac-" + it.lowercase() }
      else -> runCatching { java.io.File("/etc/machine-id").readText().trim() }.getOrNull()?.takeIf { it.isNotBlank() }?.let { "linux-$it" }
    }
    (id ?: ("app-" + ir.vil3ntec.tohid.sync.SyncStore(context).deviceUid)).also { fingerprint = it }
  }

  fun saveFile(suggestedName: String, bytes: ByteArray): String? {
    var picked: java.io.File? = null
    val run = Runnable {
      val home = System.getProperty("user.home") ?: "."
      val docs = java.io.File(home, "Documents").takeIf { it.isDirectory } ?: java.io.File(home)
      val ch = javax.swing.JFileChooser(docs).apply {
        dialogTitle = "ذخیره"
        selectedFile = java.io.File(docs, suggestedName.replace(Regex("[\\\\/:*?\"<>|]"), " "))
        applyComponentOrientation(java.awt.ComponentOrientation.RIGHT_TO_LEFT)
      }
      if (ch.showSaveDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) picked = ch.selectedFile
    }
    if (java.awt.EventQueue.isDispatchThread()) run.run() else java.awt.EventQueue.invokeAndWait(run)
    val f = picked ?: return null
    return runCatching {
      f.writeBytes(bytes)
      runCatching { java.awt.Desktop.getDesktop().open(f) }
      f.absolutePath
    }.getOrNull()
  }
}
