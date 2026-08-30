package ir.vil3ntec.tohid.admin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.admin.net.AdminApi
import ir.vil3ntec.tohid.admin.net.Session
import ir.vil3ntec.tohid.admin.ui.*
import kotlinx.coroutines.launch

/**
 *  ورودِ مدیر.
 *
 *  اینجا — برخلافِ برنامهٔ مشتری — کادرِ نشانیِ سرور هست و باید باشد:
 *  این برنامه فقط روی گوشیِ خودِ شماست و نشانی را یک بار می‌زنید.
 *  در برنامهٔ مشتری همان کادر یک ضعف بود، چون دستِ همه می‌رسید.
 */
@Composable
fun LoginScreen(session: Session, onIn: () -> Unit) {
  val c = Admin.colors
  val scope = rememberCoroutineScope()

  var server by rememberSaveable { mutableStateOf(session.serverUrl) }
  var username by rememberSaveable { mutableStateOf("") }
  var password by rememberSaveable { mutableStateOf("") }
  var show by rememberSaveable { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  Box(Modifier.fillMaxSize().background(c.bg)) {
    Column(
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .imePadding()
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Spacer(Modifier.height(60.dp))
      Box(
        Modifier.size(84.dp).clip(CircleShape).background(c.primary.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          Icons.Filled.AdminPanelSettings,
          contentDescription = null,
          tint = c.primary,
          modifier = Modifier.size(42.dp),
        )
      }
      Spacer(Modifier.height(18.dp))
      Text("پنل مدیریت توحید", style = MaterialTheme.typography.headlineSmall, color = c.text, fontWeight = FontWeight.Bold)
      Spacer(Modifier.height(6.dp))
      Text(
        "این برنامه فقط برای شماست. با آن اشتراک می‌دهید و حساب می‌بندید.",
        style = MaterialTheme.typography.bodySmall,
        color = c.muted,
        textAlign = TextAlign.Center,
      )

      Spacer(Modifier.height(28.dp))
      Box(Modifier.widthIn(max = 460.dp)) {
        Panel {
          Field(
            value = server,
            onValueChange = { server = it; error = null },
            label = "آدرس سرور — https://…",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
          )
          Spacer(Modifier.height(12.dp))
          Field(
            value = username,
            onValueChange = { username = it; error = null },
            label = "نام کاربری مدیر",
          )
          Spacer(Modifier.height(12.dp))
          Field(
            value = password,
            onValueChange = { password = it; error = null },
            label = "رمز عبور",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visual = if (show) VisualTransformation.None else PasswordVisualTransformation(),
          )
          TextButton(onClick = { show = !show }) {
            Icon(
              if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
              contentDescription = null,
              tint = c.muted,
              modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(if (show) "پنهان کردن رمز" else "نمایش رمز", style = MaterialTheme.typography.labelMedium, color = c.muted)
          }

          ErrorNote(error)

          Spacer(Modifier.height(6.dp))
          PrimaryButton(
            text = "ورود",
            modifier = Modifier.fillMaxWidth(),
            enabled = server.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
            busy = busy,
          ) {
            busy = true; error = null
            val base = server.trim().trimEnd('/')
            scope.launch {
              runCatching { AdminApi(base).login(username.trim(), password) }
                .onSuccess {
                  session.serverUrl = base
                  session.token = it.token
                  session.adminName = it.name
                  session.role = it.role
                  session.expiresAt = it.expiresAt
                  onIn()
                }
                .onFailure { error = (it as? AdminApi.ApiError)?.message ?: "ورود انجام نشد" }
              busy = false
            }
          }
        }
      }

      Spacer(Modifier.height(20.dp))
      Text(
        "نشست مدیر مهلت دارد و بعد از آن دوباره باید وارد شوید.",
        style = MaterialTheme.typography.labelSmall,
        color = c.muted,
        textAlign = TextAlign.Center,
      )
    }
  }
}
