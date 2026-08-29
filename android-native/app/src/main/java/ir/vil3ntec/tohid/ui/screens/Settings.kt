package ir.vil3ntec.tohid.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import ir.vil3ntec.tohid.BuildConfig
import ir.vil3ntec.tohid.data.AccountKeys
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.BackupClock
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.sync.License
import ir.vil3ntec.tohid.sync.SavedLogins
import ir.vil3ntec.tohid.sync.ServerClient
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.sync.Syncer
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import kotlinx.coroutines.launch

/**
 *  تنظیمات.
 *
 *  سه چیز که هرکدام دلیلِ خودش را دارد:
 *    • **پشتیبان** — تمامِ دفتر در یک فایل، با همان قالبی که نسخهٔ وب
 *      می‌سازد و می‌خواند. گوشی گم می‌شود؛ دفترِ دکان نباید با آن برود.
 *    • **تم** — روشن، تاریک، یا هرچه گوشی گفت.
 *    • **حساب و همگام‌سازی** — وصل شدن به سرورِ خودِ کاربر، بدونِ دامنه و
 *      بدونِ سرویسِ بیرونی.
 */
@Composable
fun SettingsScreen(
  store: ShopStore,
  d: ShopData,
  snackbar: SnackbarHostState,
  theme: ThemeChoice,
  onTheme: (ThemeChoice) -> Unit,
  onUpdates: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val prefs = remember { context.getSharedPreferences("tohid", android.content.Context.MODE_PRIVATE) }
  val state = remember { SyncStore(context) }
  val syncer = remember { Syncer(store, state) }

  var storeName by remember { mutableStateOf(prefs.getString("store_name", "") ?: "") }
  var serverUrl by remember { mutableStateOf(state.serverUrl) }
  var identifier by rememberSaveable { mutableStateOf("") }
  var password by rememberSaveable { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var signedIn by remember { mutableStateOf(state.accessToken != null) }
  var licenseStatus by remember { mutableStateOf(syncer.status()) }
  var confirmClear by remember { mutableStateOf(false) }
  var confirmRotate by remember { mutableStateOf(false) }
  var apiKey by remember { mutableStateOf(AccountKeys.apiKey(context)) }
  var staffCode by remember { mutableStateOf(AccountKeys.staffCode(context)) }
  var cameraGranted by remember {
    mutableStateOf(
      androidx.core.content.ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.CAMERA
      ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    )
  }
  // فایل خوانده و سنجیده شده، پیش از آنکه دفتر عوض شود
  var pendingRestore by remember { mutableStateOf<ShopData?>(null) }
  var restoreError by remember { mutableStateOf<String?>(null) }
  var canUndo by remember { mutableStateOf(store.hasSafetyCopy()) }

  fun toast(text: String) {
    scope.launch { snackbar.showSnackbar(text) }
  }

  /* --------------------------- پشتیبان --------------------------- */

  val exportFile = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("application/json")
  ) { uri ->
    if (uri == null) return@rememberLauncherForActivityResult
    scope.launch {
      runCatching {
        context.contentResolver.openOutputStream(uri)!!.use { out ->
          out.write(store.exportBackup(storeName).toByteArray(Charsets.UTF_8))
        }
      }.onSuccess {
        BackupClock.mark(context)
        toast("فایل پشتیبان ساخته شد")
      }
        .onFailure { toast("فایل پشتیبان ساخته نشد: ${it.message ?: "دلیل نامعلوم"}") }
    }
  }

  val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    if (uri == null) return@rememberLauncherForActivityResult
    scope.launch {
      val text = runCatching {
        context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
      }.getOrNull()
      if (text == null) {
        restoreError = "فایل خوانده نشد"
        return@launch
      }
      store.parseBackup(text)
        .onSuccess { pendingRestore = it; restoreError = null }
        .onFailure { restoreError = it.message ?: "فایل پشتیبان معتبر نیست" }
    }
  }

  // اجازهٔ دوربین — همان دکمهٔ «درخواست دسترسی و تست دوربین» نسخهٔ وب
  val askCamera = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted ->
    cameraGranted = granted
    toast(if (granted) "دوربین آماده است — بارکدخوان کار می‌کند" else "بدون اجازهٔ دوربین، بارکدخوان باز نمی‌شود")
  }

  LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
    item {
      Text("تنظیمات", style = MaterialTheme.typography.headlineMedium, color = Shop.colors.text)
      Spacer(Modifier.height(16.dp))

      /* ---------------------------- فروشگاه ---------------------------- */
      SectionTitle("فروشگاه")
      Panel {
        Text(
          "این نام روی سربرگ فاکتور چاپ می‌شود.",
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
          value = storeName,
          onValueChange = {
            storeName = it
            prefs.edit().putString("store_name", it.trim()).apply()
          },
          label = { Text("نام فروشگاه") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
          modifier = Modifier.fillMaxWidth(),
        )
      }

      Spacer(Modifier.height(20.dp))

      /* ------------------------------ تم ------------------------------ */
      SectionTitle("ظاهر")
      Panel {
        listOf(
          ThemeChoice.SYSTEM to "مثل گوشی",
          ThemeChoice.LIGHT to "روشن",
          ThemeChoice.DARK to "تاریک",
        ).forEach { (choice, label) ->
          Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(selected = theme == choice, onClick = { onTheme(choice) })
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
          }
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Shop.colors.border)

        Text(
          "اگر در تنظیمات گوشی «کاهش حرکت» یا حالت ذخیرهٔ باتری روشن باشد، برنامه انیمیشن‌ها را خاموش می‌کند. با این کلید می‌توانید همیشه روشن نگهشان دارید.",
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf("full" to "انیمیشن روشن", "auto" to "مثل تنظیم گوشی").forEach { (value, label) ->
            FilterChip(
              selected = Motion.choice == value,
              onClick = { Motion.set(context, value) },
              label = { Text(label) },
            )
          }
        }
      }

      Spacer(Modifier.height(20.dp))

      /* ----------------------- دوربین بارکدخوان ----------------------- */
      SectionTitle("دوربین بارکدخوان")
      Panel {
        Text(
          "برنامه برای اسکن بارکد محصولات به دسترسی دوربین دستگاه نیاز دارد. با زدن دکمهٔ زیر، اجازهٔ دوربین گرفته می‌شود.",
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
        Spacer(Modifier.height(10.dp))
        InfoLine("وضعیت", if (cameraGranted) "اجازه داده شده" else "هنوز اجازه داده نشده")
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
          onClick = {
            if (cameraGranted) toast("دوربین آماده است — بارکدخوان کار می‌کند")
            else askCamera.launch(android.Manifest.permission.CAMERA)
          },
          modifier = Modifier.fillMaxWidth(),
        ) { Text(if (cameraGranted) "آزمایش دوربین" else "درخواست دسترسی دوربین") }
      }

      Spacer(Modifier.height(20.dp))

      /* ----------------------- کلیدهای حساب ----------------------- */
      SectionTitle("کلیدهای حساب")
      Panel {
        KeyLine("کلید حساب شما", apiKey) {
          copyToClipboard(context, "کلید حساب", apiKey)
          toast("کلید حساب کپی شد")
        }
        Text(
          "این کلید فقط مالِ همین حساب است و هرگز برای کس دیگری تکرار نمی‌شود. هنگام خرید اشتراک، همین را برای فروشنده بفرستید تا اشتراک روی حساب خودتان فعال شود.",
          style = MaterialTheme.typography.labelSmall,
          color = Shop.colors.muted2,
        )

        Spacer(Modifier.height(16.dp))
        KeyLine("کد شاگرد", staffCode) {
          copyToClipboard(context, "کد شاگرد", staffCode)
          toast("کد شاگرد کپی شد")
        }
        Text(
          "این کد را به شاگردهایتان بدهید تا در صفحهٔ ورود بزنند و روی همین دکان بیایند.",
          style = MaterialTheme.typography.labelSmall,
          color = Shop.colors.muted2,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { confirmRotate = true }) { Text("ساخت کد تازه") }
      }

      Spacer(Modifier.height(20.dp))

      /* --------------------------- پشتیبان --------------------------- */
      SectionTitle("پشتیبان‌گیری از اطلاعات")
      InfoLine("آخرین پشتیبان", BackupClock.text(context))
      Panel {
        Text(
          "از تمام اطلاعات فروشگاه (قرض‌داران، محصولات، انبار، فروش‌ها و مصارف) یک فایل پشتیبان بگیرید یا از یک فایل قبلی بازیابی کنید. همین فایل در نسخهٔ وب هم باز می‌شود.",
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
        Spacer(Modifier.height(6.dp))
        Text(
          "${plain(d.products.size)} کالا — ${plain(d.sales.size)} فاکتور — ${plain(d.debtors.size)} قرض‌دار",
          style = MaterialTheme.typography.labelSmall,
          color = Shop.colors.muted2,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
            onClick = { exportFile.launch("tohid-shop-backup-${todayIso()}.json") },
            modifier = Modifier.weight(1f),
          ) { Text("گرفتن پشتیبان") }
          OutlinedButton(
            onClick = { pickFile.launch("application/json") },
            modifier = Modifier.weight(1f),
          ) { Text("بازیابی از فایل") }
        }

        // بعد از هر بازیابی، راهِ برگشت باز می‌ماند: اگر فایل اشتباهی
        // بازیابی شود، کارِ چند ماه رفته است
        if (canUndo) {
          Spacer(Modifier.height(10.dp))
          OutlinedButton(
            onClick = {
              scope.launch {
                store.undoRestore()
                  .onSuccess { canUndo = false; toast("به وضعیت پیش از بازیابی برگشت") }
                  .onFailure { toast("برگرداندن ممکن نشد") }
              }
            },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Shop.colors.warning),
            modifier = Modifier.fillMaxWidth(),
          ) { Text("برگرداندن به پیش از بازیابی") }
        }

        restoreError?.let {
          Spacer(Modifier.height(10.dp))
          Text(it, style = MaterialTheme.typography.labelMedium, color = Shop.colors.danger)
        }
      }

      Spacer(Modifier.height(20.dp))

      /* ----------------------- حساب و همگام‌سازی ----------------------- */
      SectionTitle("حساب و همگام‌سازی")
      Panel {
        Text(
          "به سرور خودتان وصل می‌شود — نه به دامنه لازم دارد نه به سرویس بیرونی. همان نشانی‌ای که پنل سرور نشان می‌دهد.",
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
          value = serverUrl,
          onValueChange = { serverUrl = it; state.serverUrl = it },
          label = { Text("آدرس سرور") },
          placeholder = { Text("http://192.168.1.10:8080") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
          modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))
        OutlinedButton(
          enabled = !busy && serverUrl.isNotBlank(),
          onClick = {
            busy = true
            scope.launch {
              runCatching { ServerClient(serverUrl).health() }
                .onSuccess { toast("سرور جواب داد") }
                .onFailure { toast((it as? ServerClient.ServerError)?.message ?: "سرور جواب نداد") }
              busy = false
            }
          },
          modifier = Modifier.fillMaxWidth(),
        ) { Text("آزمایش اتصال") }

        Spacer(Modifier.height(14.dp))

        if (!signedIn) {
          OutlinedTextField(
            value = identifier,
            onValueChange = { identifier = it },
            label = { Text("ایمیل یا شماره") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(Modifier.height(10.dp))
          OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("رمز عبور") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(Modifier.height(12.dp))
          Button(
            enabled = !busy && serverUrl.isNotBlank() && identifier.isNotBlank() && password.isNotBlank(),
            onClick = {
              busy = true
              scope.launch {
                runCatching { ServerClient(serverUrl).login(identifier.trim(), password) }
                  .onSuccess { session ->
                    state.accessToken = session.accessToken
                    state.refreshToken = session.refreshToken
                    state.accountName = session.name
                    SavedLogins.remember(context, identifier.trim(), session.name)
                    signedIn = true
                    password = ""
                    toast("وارد شدید")
                    runCatching { licenseStatus = syncer.refreshLicense(android.os.Build.MODEL ?: "گوشی") }
                  }
                  .onFailure { toast((it as? ServerClient.ServerError)?.message ?: "ورود ناموفق بود") }
                busy = false
              }
            },
            modifier = Modifier.fillMaxWidth(),
          ) { Text("ورود") }

          Spacer(Modifier.height(6.dp))
          Text(
            "حساب را در پنل سرور بسازید — بخش «توحید».",
            style = MaterialTheme.typography.labelSmall,
            color = Shop.colors.muted2,
          )
        } else {
          InfoLine("حساب", state.accountName.ifBlank { "وارد شده" })
          InfoLine("اشتراک", licenseText(licenseStatus))
          licenseStatus.payload?.let { p ->
            if (p.subscriptionEndsAt > 0) {
              InfoLine("تا تاریخ", formatDate(isoOf(p.subscriptionEndsAt)))
            }
          }
          if (state.lastSyncAt > 0) {
            InfoLine("آخرین همگام‌سازی", formatDate(isoOf(state.lastSyncAt)))
          }

          Spacer(Modifier.height(12.dp))
          Button(
            enabled = !busy,
            onClick = {
              busy = true
              scope.launch {
                runCatching { syncer.run() }
                  .onSuccess {
                    toast("همگام‌سازی شد — ${plain(it.pushed)} فرستاده، ${plain(it.pulled)} گرفته")
                    runCatching { licenseStatus = syncer.refreshLicense(android.os.Build.MODEL ?: "گوشی") }
                  }
                  .onFailure { toast((it as? ServerClient.ServerError)?.message ?: "همگام‌سازی ناموفق بود") }
                busy = false
              }
            },
            modifier = Modifier.fillMaxWidth(),
          ) { Text("همگام‌سازی حالا") }

          Spacer(Modifier.height(8.dp))
          OutlinedButton(
            onClick = {
              state.signOut()
              signedIn = false
              licenseStatus = License.Status(License.State.NONE)
              toast("از حساب خارج شدید — اطلاعات دکان سر جایش است")
            },
            modifier = Modifier.fillMaxWidth(),
          ) { Text("خروج از حساب") }
        }

        if (busy) {
          Spacer(Modifier.height(10.dp))
          LinearProgressIndicator(Modifier.fillMaxWidth(), color = Shop.colors.primary)
        }
      }

      Spacer(Modifier.height(20.dp))

      /* --------------------------- پاک‌سازی --------------------------- */
      SectionTitle("پاک‌سازی")
      Panel {
        Text(
          "تمام قرض‌داران، محصولات، انبار، فروش‌ها و مصارف ثبت‌شده روی این دستگاه برای همیشه حذف می‌شوند. پیش از پاک‌سازی، حتماً یک نسخه پشتیبان بگیرید.",
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
          onClick = { confirmClear = true },
          colors = ButtonDefaults.outlinedButtonColors(contentColor = Shop.colors.danger),
          modifier = Modifier.fillMaxWidth(),
        ) { Text("پاک‌سازی کامل اطلاعات") }
      }

      Spacer(Modifier.height(20.dp))

      /* ------------------------- درباره برنامه ------------------------- */
      SectionTitle("درباره برنامه")
      Panel {
        Text(
          "توحید | مدیریت فروشگاه — برنامهٔ اندروید",
          style = MaterialTheme.typography.bodyMedium,
          color = Shop.colors.text,
        )
        Spacer(Modifier.height(4.dp))
        Text(
          "تمام اطلاعات فقط روی همین گوشی ذخیره می‌شود؛ پس هر چند وقت یک‌بار نسخهٔ پشتیبان بگیرید.",
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
        Spacer(Modifier.height(10.dp))
        InfoLine("نسخهٔ نصب‌شده", BuildConfig.VERSION_NAME)
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onUpdates, modifier = Modifier.fillMaxWidth()) {
          Text("دریافت آخرین نسخه")
        }
      }

      Spacer(Modifier.height(24.dp))
    }
  }

  /* ---------------------------- پنجره‌ها ---------------------------- */

  pendingRestore?.let { incoming ->
    AlertDialog(
      onDismissRequest = { pendingRestore = null },
      containerColor = Shop.colors.surface,
      title = { Text("بازیابی اطلاعات؟", color = Shop.colors.text) },
      text = {
        Column {
          Text(
            "با بازیابی این فایل، تمام اطلاعات فعلی این دستگاه جایگزین می‌شود. یک نسخه از وضعیت فعلی نگه داشته می‌شود تا اگر اشتباه شد، برگردانده شود.",
            style = MaterialTheme.typography.bodySmall,
            color = Shop.colors.muted,
          )
          Spacer(Modifier.height(12.dp))
          RestoreLine("محصولات", d.products.size, incoming.products.size)
          RestoreLine("فاکتورها", d.sales.size, incoming.sales.size)
          RestoreLine("قرض‌داران", d.debtors.size, incoming.debtors.size)
          RestoreLine("مصارف", d.expenses.size, incoming.expenses.size)
          RestoreLine("ورودهای انبار", d.warehouseEntries.size, incoming.warehouseEntries.size)
        }
      },
      confirmButton = {
        TextButton(onClick = {
          val next = incoming
          pendingRestore = null
          scope.launch {
            store.keepSafetyCopy()
            store.save(next)
            canUndo = true
            toast("اطلاعات با موفقیت بازیابی شد")
          }
        }) { Text("بازیابی", color = Shop.colors.danger) }
      },
      dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("انصراف") } },
    )
  }

  if (confirmRotate) {
    AlertDialog(
      onDismissRequest = { confirmRotate = false },
      containerColor = Shop.colors.surface,
      title = { Text("کد شاگرد تازه ساخته شود؟", color = Shop.colors.text) },
      text = {
        Text(
          "کد فعلی از کار می‌افتد و هر شاگردی که با آن وارد شده، دفعهٔ بعد باید کد تازه را بزند.",
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
      },
      confirmButton = {
        TextButton(onClick = {
          confirmRotate = false
          staffCode = AccountKeys.rotateStaffCode(context)
          toast("کد شاگرد تازه ساخته شد")
        }) { Text("بساز") }
      },
      dismissButton = { TextButton(onClick = { confirmRotate = false }) { Text("انصراف") } },
    )
  }

  if (confirmClear) {
    AlertDialog(
      onDismissRequest = { confirmClear = false },
      containerColor = Shop.colors.surface,
      title = { Text("پاک‌سازی کامل اطلاعات؟", color = Shop.colors.text) },
      text = {
        Text(
          "همه چیز روی این دستگاه پاک می‌شود و برنمی‌گردد. اگر پشتیبان نگرفته‌اید، اول پشتیبان بگیرید.",
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
      },
      confirmButton = {
        TextButton(onClick = {
          confirmClear = false
          scope.launch {
            store.save(ShopData())
            toast("تمام اطلاعات پاک شد")
          }
        }) { Text("پاک کن", color = Shop.colors.danger) }
      },
      dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("انصراف") } },
    )
  }
}

/**
 *  یک کلید با دکمهٔ کپی — همان کادرِ `.settings-key` نسخهٔ وب.
 *
 *  خودِ کلید چپ‌به‌راست نوشته می‌شود: در صفحهٔ راست‌به‌راست، دسته‌های
 *  `TSH-…` وارونه دیده می‌شوند و کاربر اشتباه می‌خواندشان.
 */
@Composable
private fun KeyLine(label: String, value: String, onCopy: () -> Unit) {
  Text(label, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
  Spacer(Modifier.height(6.dp))
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .background(Shop.colors.bg)
      .border(1.dp, Shop.colors.border, RoundedCornerShape(10.dp))
      .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
      Text(
        value,
        style = MaterialTheme.typography.labelMedium,
        color = Shop.colors.text,
        textAlign = TextAlign.Start,
        modifier = Modifier.weight(1f),
      )
    }
    TextButton(onClick = onCopy) { Text("کپی") }
  }
  Spacer(Modifier.height(6.dp))
}

private fun copyToClipboard(context: android.content.Context, label: String, value: String) {
  val manager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
    as? android.content.ClipboardManager ?: return
  manager.setPrimaryClip(android.content.ClipData.newPlainText(label, value))
}

/**
 *  یک ردیفِ خلاصهٔ بازیابی: چند تا الان هست، چند تا بعدش می‌شود.
 *
 *  فقط تعدادِ فایل را نشان دادن کافی نیست؛ کاربر باید ببیند چه چیزی را
 *  دارد از دست می‌دهد — «۱۲۰ محصول ← ۳ محصول» حرفِ خودش را می‌زند.
 */
@Composable
private fun RestoreLine(label: String, now: Int, next: Int) {
  Row(
    Modifier.fillMaxWidth().padding(vertical = 3.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
    Text(
      "${plain(now)} ← ${plain(next)}",
      style = MaterialTheme.typography.bodyMedium,
      color = if (next < now) Shop.colors.warning else Shop.colors.text,
    )
  }
}

@Composable
private fun InfoLine(label: String, value: String) {
  Row(
    Modifier.fillMaxWidth().padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
    Text(value, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
  }
}

private fun licenseText(status: License.Status): String = when (status.state) {
  License.State.ACTIVE -> status.payload?.planTitle ?: "فعال"
  License.State.GRACE -> "تمام شده — در مهلت"
  License.State.EXPIRED -> "تمام شده"
  License.State.PENDING -> "هنوز شروع نشده"
  License.State.INVALID -> "مجوز معتبر نیست"
  License.State.NONE -> "بدون اشتراک"
}

private fun isoOf(millis: Long): String {
  val c = java.util.Calendar.getInstance().apply { timeInMillis = millis }
  val y = c.get(java.util.Calendar.YEAR)
  val m = (c.get(java.util.Calendar.MONTH) + 1).toString().padStart(2, '0')
  val d = c.get(java.util.Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
  return "$y-$m-$d"
}
