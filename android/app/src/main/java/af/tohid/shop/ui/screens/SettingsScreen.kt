package af.tohid.shop.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import af.tohid.shop.BuildConfig
import af.tohid.shop.TohidApp
import af.tohid.shop.util.Format
import af.tohid.shop.util.UpdateManager
import kotlinx.coroutines.launch

/**
 * تنظیمات — شامل دکمه‌ی به‌روزرسانی.
 * کاربر همین‌جا نسخه‌ی جدید را از گیت‌هاب می‌گیرد و نصب می‌کند؛
 * نیازی به کامپیوتر یا اندروید استودیو نیست.
 */
@Composable
fun SettingsScreen() {
    val app = TohidApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<UpdateManager.State>(UpdateManager.State.Idle) }

    ScreenScaffold("تنظیمات", "نسخه برنامه و به‌روزرسانی") {

        // ---------- به‌روزرسانی ----------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("به‌روزرسانی برنامه", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold)
                Text("نسخه فعلی: ${Format.toFa(BuildConfig.VERSION_NAME)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                when (val s = state) {
                    is UpdateManager.State.Idle -> {
                        Button(
                            onClick = {
                                state = UpdateManager.State.Checking
                                scope.launch { state = UpdateManager.check() }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("بررسی نسخه جدید") }
                    }

                    is UpdateManager.State.Checking -> {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("در حال بررسی…", style = MaterialTheme.typography.bodySmall)
                    }

                    is UpdateManager.State.UpToDate -> {
                        Text("برنامه شما به‌روز است.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary)
                        OutlinedButton(
                            onClick = { state = UpdateManager.State.Idle },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("بررسی دوباره") }
                    }

                    is UpdateManager.State.Available -> {
                        Text("نسخه ${Format.toFa(s.info.version)} آماده است",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold)
                        if (s.info.notes.isNotBlank()) {
                            Text(s.info.notes.take(400),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    state = UpdateManager.State.Downloading(0)
                                    state = UpdateManager.download(context, s.info) { pct ->
                                        state = UpdateManager.State.Downloading(pct)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("دانلود و نصب") }
                    }

                    is UpdateManager.State.Downloading -> {
                        if (s.percent >= 0) {
                            LinearProgressIndicator(
                                progress = { s.percent / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text("در حال دانلود… ${Format.toFa(s.percent.toString())}٪",
                                style = MaterialTheme.typography.bodySmall)
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text("در حال دانلود…", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    is UpdateManager.State.ReadyToInstall -> {
                        val allowed = UpdateManager.canInstall(context)
                        if (!allowed) {
                            Text(
                                "برای نصب، اجازه‌ی «نصب برنامه‌های ناشناس» لازم است. " +
                                    "دکمه‌ی زیر شما را به همان تنظیم می‌برد.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                            Button(
                                onClick = { (context as? Activity)?.let { UpdateManager.openInstallPermission(it) } },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("باز کردن تنظیمات اجازه نصب") }
                        }
                        Button(
                            onClick = { UpdateManager.install(context, s.file) },
                            enabled = allowed,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("نصب نسخه ${Format.toFa(s.info.version)}") }
                        Text("اطلاعات دکان شما در به‌روزرسانی پاک نمی‌شود.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    is UpdateManager.State.Failed -> {
                        Text(s.message, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error)
                        OutlinedButton(
                            onClick = { state = UpdateManager.State.Idle },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("تلاش دوباره") }
                    }
                }
            }
        }

        // ---------- درباره ----------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("درباره برنامه", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold)
                Text("توحید | مدیریت فروشگاه", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "اطلاعات دکان روی همین دستگاه در یک پایگاه‌داده‌ی واقعی ذخیره می‌شود " +
                        "و با به‌روزرسانی از بین نمی‌رود.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (app.session.shopName().isNotBlank()) {
                    Text("دکان: ${app.session.shopName()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
