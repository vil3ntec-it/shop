package ir.vil3ntec.tohid.ui.v3.settings.sections

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.BuildConfig
import ir.vil3ntec.tohid.core.backup.RecoveryPhraseFlow
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrBackupSafety
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.data.AuditEntry
import ir.vil3ntec.tohid.data.AutoBackup
import ir.vil3ntec.tohid.data.BackupBundle
import ir.vil3ntec.tohid.data.BackupClock
import ir.vil3ntec.tohid.data.BackupMirror
import ir.vil3ntec.tohid.data.BackupRestore
import ir.vil3ntec.tohid.data.BackupSeal
import ir.vil3ntec.tohid.data.ReportCsv
import ir.vil3ntec.tohid.data.SnapshotSeal
import ir.vil3ntec.tohid.data.LedgerArchive
import ir.vil3ntec.tohid.data.OwnerExport
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.auditId
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.BackupRepository
import ir.vil3ntec.tohid.print.ReportPrint
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.v3.V3Toasts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** یادداشتِ سابقهٔ عملیات برای خروجیِ کامل — دادهٔ دفتر است، با زبانِ صفحه عوض نمی‌شود */
private const val AUDIT_EXPORT_XLSX = "خروجی کامل داده — اکسل"
private const val AUDIT_EXPORT_JSON = "خروجی کامل داده — JSON"

/**
 *  کارهای صفحهٔ «پشتیبان و بازگردانی» — **همان** کارهای پیشینِ تنظیمات، بی منطقِ
 *  تازه: پشتیبانِ ZIP (`BackupBundle`) و فقط‌دفتر، خروجیِ کاملِ مالک (`OwnerExport`،
 *  OPS-08، با ردیفِ سابقه)، بازیابی (نسخهٔ ایمنی، عکس‌ها، `applyRestore`)، نسخه‌های
 *  خودکار، پشتیبانِ ابری (`AutoBackup.push`)، پوشه (`BackupMirror`)، فشرده‌سازی
 *  (`LedgerArchive`)، برگرداندن و پاک‌سازیِ کامل. صفحه فقط حالت را می‌کشد.
 */
@Stable
internal class BackupController(
  private val context: Context,
  private val store: ShopStore,
  private val scope: CoroutineScope,
  private val toasts: V3Toasts,
) {
  /** فایلِ خوانده و سنجیده‌شده، پیش از آنکه دفتر عوض شود (برگهٔ تأیید) */
  var pendingRestore by mutableStateOf<ShopData?>(null)
    private set
  var restorePhotos by mutableIntStateOf(0)
    private set
  var restoreError by mutableStateOf<String?>(null)
    private set
  var canUndo by mutableStateOf(store.hasSafetyCopy())
    private set
  var cloud by mutableStateOf<BackupRepository.Listing?>(null)
    private set
  var cloudBusy by mutableStateOf(false)
    private set
  var cloudReload by mutableIntStateOf(0)
    private set
  var mirrorTick by mutableIntStateOf(0)
    private set
  var mirrorBusy by mutableStateOf(false)
    private set
  var mirrorOn by mutableStateOf(BackupMirror.enabled(context))
    private set

  //  OPS-02: «رمزِ نسخه‌های خودکار» (`SnapshotSeal`، پیش‌فرض خاموش) و برگهٔ «عبارتِ بازیابی» (`RecoveryPhraseFlow`)
  var sealOn by mutableStateOf(SnapshotSeal.on(context))
    private set
  var sealKeyReady by mutableStateOf(sealOn && SnapshotSeal.keyReady(context))
    private set
  var phrase by mutableStateOf<RecoveryPhraseFlow?>(null)
    private set
  var phraseStep by mutableStateOf(RecoveryPhraseFlow.Step.INTRO)
    private set
  var phraseError by mutableStateOf<String?>(null)
    private set
  var making by mutableStateOf(false)
    private set
  var askSealOff by mutableStateOf(false)
    private set
  /** نسخهٔ رمزدار با عبارت که کلیدش این‌جا نیست — برگهٔ پرسیدنِ عبارت */
  var unlock by mutableStateOf<AutoBackup.Snapshot?>(null)
    private set
  /** پشتیبانِ رمزدار (دستی/پوشه، `BackupSeal`) که کلیدش این‌جا نیست — همان برگه */
  var unlockFile by mutableStateOf<Uri?>(null)
    private set
  /** OPS-02: «پشتیبانِ دستی و پوشه هم رمزدار» (`BackupSeal`، پیش‌فرض خاموش) */
  var backupSealOn by mutableStateOf(BackupSeal.on(context))
    private set
  var unlockError by mutableStateOf<String?>(null)
    private set

  private val storeName: String
    get() = context.getSharedPreferences("tohid", Context.MODE_PRIVATE).getString("store_name", "") ?: ""

  private fun failText(e: Throwable) = e.message ?: Str.unknownReason

  /** پشتیبانِ کامل — دفتر و عکس‌ها در یک ZIP (بیرون از رشتهٔ اصلی) */
  fun writeFull(uri: Uri) = scope.launch {
    runCatching {
      BackupRestore.writeFull(context, store, storeName) { context.contentResolver.openOutputStream(uri) ?: error(StrSettings.pathNotWritable) }
    }.onSuccess { photos ->
      BackupClock.mark(context)
      toasts.show(if (photos > 0) StrSettings.backupMadeWithPhotos(photos) else StrSettings.backupMade)
    }.onFailure { toasts.show(StrSettings.backupFailed(failText(it))) }
  }

  /** فقط دفتر — همان فایلی که نسخهٔ وب می‌خواند؛ رمزِ پشتیبانِ دستی روشن ⇒ همان پاکتِ عبارت (`BackupRestore.writeLedger`) */
  fun writeLedger(uri: Uri) = scope.launch {
    runCatching {
      BackupRestore.writeLedger(context, store, storeName) { context.contentResolver.openOutputStream(uri) ?: error(StrSettings.pathNotWritable) }
    }.onSuccess { BackupClock.mark(context); toasts.show(StrSettings.ledgerFileMade) }
      .onFailure { toasts.show(StrSettings.fileFailed(failText(it))) }
  }

  /**
   *  OPS-02: پیش از باز کردنِ انتخابگرِ فایل — رمزِ پشتیبانِ دستی روشن و کلید باز نشد یا پشتیبان برای حافظه بزرگ است ⇒ پیامِ
   *  روشن و انتخابگر باز نمی‌شود (وگرنه اندروید فایلِ خالیِ `….sealed` را می‌ساخت و بعد پشتیبان شکست می‌خورد).
   */
  fun sealBlocked(photos: Boolean): Boolean {
    val why = BackupSeal.blocked(context, photos) ?: return false
    toasts.show(why)
    return true
  }

  /** خروجیِ کاملِ داده برای مالک — فقط مالک/مدیر، با ثبت در سابقهٔ عملیات (۲۱.۲) */
  fun ownerExport(uri: Uri, excel: Boolean) {
    if (ShopRole.isStaff(context)) { toasts.show(StrSettings.exportOwnerOnly); return }
    scope.launch {
      runCatching {
        withContext(Dispatchers.IO) {
          val meta = OwnerExport.Meta(System.currentTimeMillis(), if (Platform.DESKTOP) "desktop" else "android", BuildConfig.VERSION_NAME, storeName)
          val archive = LedgerArchive.readArchive(context)
          val out = context.contentResolver.openOutputStream(uri) ?: error(StrSettings.pathNotWritable)
          out.use {
            if (excel) OwnerExport.writeXlsx(store.data.value, meta, archive, it)
            else OwnerExport.writeJson(store.data.value, meta, archive, it)
          }
        }
      }.onSuccess {
        val cur = store.data.value
        store.save(
          cur,
          cur.copy(
            auditLog = cur.auditLog + AuditEntry(
              id = auditId(), type = "data_export", date = todayIso(),
              notes = if (excel) AUDIT_EXPORT_XLSX else AUDIT_EXPORT_JSON, createdAt = System.currentTimeMillis(),
            ),
          ),
        )
        toasts.show(if (excel) StrSettings.excelExportMade else StrSettings.jsonExportMade)
      }.onFailure { toasts.show(StrSettings.exportFailed(failText(it))) }
    }
  }

  /**
   *  بازیابی هر سه شکل را می‌شناسد: ZIPِ کامل، JSONِ ساده و پشتیبانِ رمزدار با عبارت (`BackupSeal`).
   *  @param typed عبارتِ بازیابی برای پشتیبانِ رمزداری که کلیدش این‌جا نیست ([unlockFile])
   */
  fun readFile(uri: Uri, typed: String? = null) = scope.launch {
    withContext(Dispatchers.IO) {
      runCatching {
        val input = context.contentResolver.openInputStream(uri) ?: error(StrSettings.fileNotOpened)
        input.use { BackupRestore.open(context, store, it, typed).getOrThrow() }
      }
    }.onSuccess { unlockFile = null; unlockError = null; pendingRestore = it.data; restorePhotos = it.photos; restoreError = null }
      .onFailure { e ->
        BackupBundle.dropStaging(context)
        when {
          typed == null && BackupSeal.needsPhrase(e) -> { unlockFile = uri; unlockError = null; restoreError = null }
          //  عبارتِ نادرست ⇒ برگه باز می‌ماند؛ هر خطای دیگرِ پس از باز شدن (ZIP ِ ناقص، دکانِ دیگر…) ⇒ پیامِ خودش
          typed != null && e.message == StrBackupSafety.wrongPhraseFile -> unlockError = StrBackupSafety.wrongPhraseFile
          else -> { unlockFile = null; unlockError = null; restoreError = e.message ?: StrSettings.invalidBackup }
        }
      }
  }

  /** @param typed عبارتِ بازیابی برای نسخهٔ رمزداری که کلیدش این‌جا نیست ([unlock]) */
  fun readSnapshot(snap: AutoBackup.Snapshot, typed: String? = null) = scope.launch {
    withContext(Dispatchers.IO) { BackupRestore.openSnapshot(context, store, snap.file, typed) }
      .onSuccess { unlock = null; unlockError = null; pendingRestore = it; restorePhotos = 0; restoreError = null }
      .onFailure { e ->
        when {
          typed == null && BackupRestore.needsPhrase(e, snap.file) -> { unlock = snap; unlockError = null; restoreError = null }
          typed != null -> unlockError = StrBackupSafety.wrongPhrase
          else -> restoreError = StrSettings.snapshotUnreadable
        }
      }
  }

  fun cancelUnlock() { unlock = null; unlockFile = null; unlockError = null }

  /** همان برگهٔ عبارت برای نسخهٔ خودکار یا پشتیبانِ رمزدار */
  fun submitUnlock(typed: String) {
    unlock?.let { readSnapshot(it, typed); return }
    unlockFile?.let { readFile(it, typed) }
  }

  fun cancelRestore() {
    BackupBundle.dropStaging(context)
    pendingRestore = null
    restorePhotos = 0
  }

  fun confirmRestore() {
    val next = pendingRestore ?: return
    pendingRestore = null
    scope.launch {
      //  ⚠️ بازیابیِ پشتیبان دادهٔ خودِ کاربر است؛ قفلِ نرمِ پایانِ اشتراک گروگانش نمی‌گیرد. OPS-02 (`BackupRestore.confirm`):
      //  عکس‌ها و تنظیمات فقط پس از نشستنِ دفتر؛ ردشده («اول وارد شوید»، صفِ نرفته…) ⇒ پیامِ خودش، نه «روی حافظه نوشته نشد»
      val r = BackupRestore.confirm(context, store, next)
      if (!r.ok) { toasts.show(r.message.ifBlank { StrSettings.restoreDiskFail }); return@launch }
      canUndo = true
      restorePhotos = 0
      val done = if (r.photos > 0) StrSettings.restoredWithPhotos(r.photos) else StrSettings.restored
      toasts.show(if (r.message.isEmpty()) done else "$done\n${r.message}")
    }
  }

  /** پشتیبانِ ابری — فهرستِ همین دکان (شناسه از توکن)، بی‌صدا */
  suspend fun loadCloud(signedIn: Boolean) {
    if (!signedIn || !Backend.isReady(context)) { cloud = null; return }
    Backend.backups(context).list().onSuccess { cloud = it }.onFailure { cloud = null }
  }

  fun pushCloud(signedIn: Boolean) {
    if (cloudBusy) return
    if (!signedIn) { toasts.show(StrSettings.signInFirst); return }
    scope.launch {
      cloudBusy = true
      val sent = AutoBackup.push(context, manual = true)
      cloudBusy = false
      cloudReload++
      toasts.show(if (sent) StrSettings.cloudSent else StrSettings.cloudNotSent)
    }
  }

  /** پشتیبانِ کامل در پوشه + سرور با هم (`BackupMirror`) */
  fun mirrorNow(signedIn: Boolean) {
    if (mirrorBusy) return
    mirrorBusy = true
    scope.launch {
      val local = withContext(Dispatchers.IO) { BackupMirror.write(context) }
      val sent = if (signedIn) AutoBackup.push(context, manual = true) else false
      mirrorBusy = false
      mirrorTick++; cloudReload++
      toasts.show(
        local.fold({ StrSettings.savedToFolder }, { StrSettings.folder(it.message ?: Str.unknownReason) }) +
          if (signedIn) " · " + (if (sent) StrSettings.alsoOnServer else StrSettings.serverNotSent) else "",
      )
    }
  }

  fun setMirror(on: Boolean) { mirrorOn = on; BackupMirror.setEnabled(context, on) }

  fun pickFolder(current: String) {
    Platform.pickFolder(current)?.let { BackupMirror.setFolder(context, it); mirrorTick++ }
  }

  fun openFolder(path: String) { if (!Platform.openFolder(path)) toasts.show(StrSettings.folderNotOpened) }

  /** فشرده کردنِ دفتر — یک‌طرفه و دستی (شرحش سرِ `LedgerArchive`) */
  fun compact(d: ShopData) = scope.launch {
    withContext(Dispatchers.IO) { LedgerArchive.compact(context, d) }
      .onSuccess { store.save(d, it.data); toasts.show(StrSettings.archived(it.moved.total)) }
      .onFailure { toasts.show(StrSettings.compactFailed(failText(it))) }
  }

  fun undoRestore() = scope.launch {
    store.undoRestore()
      .onSuccess { canUndo = false; toasts.show(StrSettings.undone) }
      .onFailure { toasts.show(StrSettings.undoFailed) }
  }

  /* ---------- OPS-02: رمزِ نسخه‌های خودکار و «عبارتِ بازیابی» ---------- */

  /** روشن ⇒ برگهٔ عبارت (فقط پس از تأیید روشن می‌شود)؛ خاموش ⇒ پرسیدن */
  fun toggleSeal(on: Boolean) { if (on) startPhrase() else askSealOff = true }

  fun sealOff() {
    askSealOff = false
    SnapshotSeal.setOn(context, false)
    //  کلیدِ پشتیبانِ دستی/پوشه همان کلیدِ عبارت است ⇒ با هم خاموش
    BackupSeal.setOn(context, false)
    backupSealOn = false
    sealOn = false
    sealKeyReady = false
    toasts.show(StrBackupSafety.sealedOff)
  }

  fun dismissSealOff() { askSealOff = false }

  /** «پشتیبانِ دستی و پوشه هم رمزدار» — روشن فقط با کلیدِ عبارتِ همین دستگاه */
  fun setBackupSeal(on: Boolean) {
    val ok = BackupSeal.setOn(context, on)
    backupSealOn = BackupSeal.on(context)
    if (on && !ok) toasts.show(StrBackupSafety.backupSealKeyMissing)
  }

  fun startPhrase() { phrase = RecoveryPhraseFlow(); phraseError = null; syncPhrase() }
  fun showPhrase() { phrase?.start(); syncPhrase() }
  fun phraseWritten() { phrase?.written(); syncPhrase() }
  fun phraseBack() { phrase?.back(); phraseError = null; syncPhrase() }
  fun clearPhraseError() { phraseError = null }
  fun cancelPhrase() { phrase?.cancel(); phrase = null; syncPhrase() }
  private fun syncPhrase() { phraseStep = phrase?.step ?: RecoveryPhraseFlow.Step.INTRO }

  /** چاپِ عبارت (فقط در گامِ نمایش) — همان چاپِ گزارش، A4 */
  fun printPhrase() {
    val rows = phrase?.printRows(storeName, StrBackupSafety.printDate(todayIso()), StrBackupSafety.printKeep, StrBackupSafety.printHow).orEmpty()
    if (rows.isNotEmpty()) runCatching { ReportPrint.print(context, ReportCsv.Sheet("recovery-phrase", rows), StrBackupSafety.phraseTitle) }
  }

  /** تأییدِ نوشتنی ⇒ کلید (PBKDF2، بیرون از رشتهٔ صفحه) ⇒ روشن */
  fun confirmPhrase(typed: String) {
    val f = phrase ?: return
    when (f.confirm(typed)) {
      RecoveryPhraseFlow.Check.OK -> {
        val canonical = f.takeConfirmed() ?: return
        making = true
        scope.launch {
          val ok = withContext(Dispatchers.Default) {
            runCatching { SnapshotSeal.adopt(context, canonical) }.isSuccess && SnapshotSeal.setOn(context, true)
          }
          making = false
          phrase = null
          syncPhrase()
          sealOn = SnapshotSeal.on(context)
          sealKeyReady = sealOn && SnapshotSeal.keyReady(context)
          toasts.show(if (ok) StrBackupSafety.sealedOn else StrBackupSafety.keyFailed)
        }
      }
      RecoveryPhraseFlow.Check.TYPO -> phraseError = StrBackupSafety.typo
      RecoveryPhraseFlow.Check.MISMATCH -> phraseError = StrBackupSafety.mismatch
      RecoveryPhraseFlow.Check.NOT_ASKED -> Unit
    }
    syncPhrase()
  }

  fun wipe() = scope.launch {
    store.replaceAll(ShopData())
    toasts.show(StrSettings.wiped)
  }
}

/** راه‌اندازهای پرونده — بی‌قید و شرط ساخته می‌شوند (ترتیبِ فراخوانیِ Compose) */
internal class BackupLaunchers(
  val full: () -> Unit,
  val ledger: () -> Unit,
  val excel: () -> Unit,
  val json: () -> Unit,
  val restore: () -> Unit,
)

@Composable
internal fun rememberBackupController(store: ShopStore, toasts: V3Toasts, signedIn: Boolean): Pair<BackupController, BackupLaunchers> {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val ctrl = remember(store) { BackupController(context, store, scope, toasts) }
  LaunchedEffect(signedIn, ctrl.cloudReload) { ctrl.loadCloud(signedIn) }
  val full = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(ctrl::writeFull) }
  //  OPS-02: پشتیبانِ رمزدار ZIP نیست (`BackupSeal.SEALED_EXT`) — نوعِ دیگر تا اندروید «.zip» نچسباند
  val fullSealed = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { it?.let(ctrl::writeFull) }
  val ledger = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(ctrl::writeLedger) }
  val ledgerSealed = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { it?.let(ctrl::writeLedger) }
  val json = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { u -> ctrl.ownerExport(u, excel = false) } }
  val excel = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) {
    it?.let { u -> ctrl.ownerExport(u, excel = true) }
  }
  val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { u -> ctrl.readFile(u) } }
  val launchers = BackupLaunchers(
    full = {
      when {
        ctrl.sealBlocked(photos = true) -> Unit
        ctrl.backupSealOn -> fullSealed.launch("tohid-backup-${todayIso()}${BackupSeal.SEALED_EXT}")
        else -> full.launch("tohid-backup-${todayIso()}.zip")
      }
    },
    ledger = {
      when {
        ctrl.sealBlocked(photos = false) -> Unit
        ctrl.backupSealOn -> ledgerSealed.launch("tohid-shop-backup-${todayIso()}.json${BackupSeal.SEALED_SUFFIX}")
        else -> ledger.launch("tohid-shop-backup-${todayIso()}.json")
      }
    },
    excel = { excel.launch("tohid-export-${todayIso()}.xlsx") },
    json = { json.launch("tohid-export-${todayIso()}.json") },
    restore = { pick.launch("*/*") },
  )
  return ctrl to launchers
}
