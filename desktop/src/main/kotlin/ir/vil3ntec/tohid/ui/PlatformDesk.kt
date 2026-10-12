package ir.vil3ntec.tohid.ui

/** برنامهٔ کامپیوتر — شرح در ‎android-native/.../ui/Platform.kt‎ */
object Platform {
  const val DESKTOP: Boolean = true

  /** «اسناد ⇐ توحید ⇐ پشتیبان» — جایی که کاربر خودش پیدا می‌کند و با فلش کپی می‌کند */
  fun defaultBackupDir(context: android.content.Context): java.io.File? {
    val home = System.getProperty("user.home") ?: return null
    val docs = java.io.File(home, "Documents").takeIf { it.isDirectory } ?: java.io.File(home)
    return java.io.File(docs, "Tohid/پشتیبان")
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
}
