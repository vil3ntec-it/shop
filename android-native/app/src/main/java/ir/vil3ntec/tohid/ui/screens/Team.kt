package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.sync.ServerClient
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 *  کارمندانِ دکان — همان «چند کاربر روی یک دکان» که در صفحهٔ اشتراک
 *  فروخته می‌شود.
 *
 *  این صفحه تا امروز وجود نداشت. سرور از اول همه‌ی کارها را می‌توانست —
 *  ساختنِ کد، فهرستِ کارمندان، برداشتنشان — ولی هیچ‌جای برنامه صدایشان
 *  نمی‌زد. یعنی قابلیتی که پولش گرفته می‌شد، در عمل نبود.
 *
 *  کارِ صاحبِ دکان اینجا سه چیز است: کدی بسازد، به شاگردش بدهد، و هر وقت
 *  خواست دسترسی‌اش را ببندد. بقیه‌اش — نقش‌ها و اجازه‌ها — کارِ سرور است.
 */
@Composable
fun TeamScreen(snackbar: SnackbarHostState) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val clipboard = LocalClipboardManager.current
  val state = remember { SyncStore(context) }
  val colors = Shop.colors

  var members by remember { mutableStateOf<List<Member>>(emptyList()) }
  var codes by remember { mutableStateOf<List<StaffCode>>(emptyList()) }
  var maxMembers by remember { mutableStateOf(0) }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  /** کدِ تازه — فقط همین یک بار دیده می‌شود، پس تا بسته نشده روی صفحه می‌ماند */
  var fresh by remember { mutableStateOf<String?>(null) }
  var askNew by remember { mutableStateOf(false) }
  var removing by remember { mutableStateOf<Member?>(null) }

  /** کدِ ثابتِ دکان — همیشه همان است، پس هر بار نشان داده می‌شود */
  var standing by remember { mutableStateOf("") }
  var rotating by remember { mutableStateOf(false) }

  val signedIn = !state.accessToken.isNullOrBlank() && state.serverUrl.isNotBlank()

  suspend fun reload() {
    val token = state.accessToken ?: return
    val client = ServerClient(state.serverUrl)
    runCatching {
      val m = client.members(token)
      members = m["members"]?.jsonArray?.map { row -> Member.of(row.jsonObject) }.orEmpty()
      maxMembers = m["maxMembers"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0

      val c = client.staffCodes(token)
      codes = c["codes"]?.jsonArray?.map { row -> StaffCode.of(row.jsonObject) }.orEmpty()

      //  کدِ ثابت جدا خوانده می‌شود؛ سرورِ قدیمی این مسیر را ندارد و
      //  نبودنش نباید بقیهٔ صفحه را خراب کند
      standing = runCatching { client.standingCode(token) }.getOrDefault("")
      error = null
    }.onFailure {
      error = (it as? ServerClient.ServerError)?.message ?: "فهرست خوانده نشد"
    }
  }

  LaunchedEffect(signedIn) { if (signedIn) reload() }

  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
  ) {
    Spacer(Modifier.height(8.dp))
    Text(
      "کسانی که با شما روی همین دکان کار می‌کنند. هر کدام حساب خودش را دارد و کارهایش در سابقهٔ عملیات به نامِ خودش ثبت می‌شود.",
      style = MaterialTheme.typography.bodySmall,
      color = colors.muted,
    )
    Spacer(Modifier.height(16.dp))

    if (!signedIn) {
      Panel {
        TohidEmptyState(
          icon = Icons.Filled.Groups,
          title = "اول وارد حساب شوید",
          description = "کارمندان روی سرور نگه داشته می‌شوند تا روی هر گوشی یکی باشند. بدون حساب، چیزی برای نشان دادن نیست.",
        )
      }
      return@Column
    }

    error?.let {
      Panel { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.danger) }
      Spacer(Modifier.height(14.dp))
    }

    /* -------------------------- کدِ تازه -------------------------- */
    fresh?.let { code ->
      Column(
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(Radius.md))
          .background(colors.successTint)
          .padding(16.dp)
      ) {
        Text(
          "این کد را به شاگردتان بدهید",
          style = MaterialTheme.typography.labelLarge,
          color = colors.success,
          fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
          "فقط همین یک بار نشان داده می‌شود. بعد از این، فقط می‌شود باطلش کرد.",
          style = MaterialTheme.typography.labelSmall,
          color = colors.muted,
        )
        Spacer(Modifier.height(10.dp))
        // کد لاتین است و در صفحهٔ راست‌به‌چپ وارونه دیده می‌شود
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
          Text(
            code,
            style = MaterialTheme.typography.headlineSmall,
            color = colors.text,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
          )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TohidSecondaryButton(
            text = "رونوشت",
            icon = Icons.Filled.ContentCopy,
            onClick = { clipboard.setText(AnnotatedString(code)) },
            modifier = Modifier.weight(1f),
          )
          TohidSecondaryButton(
            text = "بستن",
            onClick = { fresh = null },
            modifier = Modifier.weight(1f),
          )
        }
      }
      Spacer(Modifier.height(16.dp))
    }

    /* ---------------------- کدِ ثابتِ دکان ---------------------- */
    /*
     *  یک کد، برای همهٔ شاگردها، همیشه همان.
     *
     *  قبلاً برای هر شاگرد یک کدِ یک‌بارمصرف ساخته می‌شد و صاحبِ دکان
     *  باید هر بار می‌آمد اینجا. این کد از شناسهٔ دکان ساخته می‌شود، پس
     *  هر وقت نگاه کنید همان است — نه جایی نوشته شده، نه گم می‌شود.
     */
    if (standing.isNotBlank()) {
      SectionTitle("کد دکان شما")
      Panel {
        Text(
          "این کد را به هر شاگردی که می‌خواهید بدهید. همیشه همین است و عوض نمی‌شود.",
          style = MaterialTheme.typography.bodySmall,
          color = colors.muted,
        )
        Spacer(Modifier.height(10.dp))
        Box(
          Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.primary.copy(alpha = 0.10f))
            .padding(vertical = 14.dp),
          contentAlignment = Alignment.Center,
        ) {
          CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
              standing,
              style = MaterialTheme.typography.titleMedium,
              color = colors.primary,
              fontWeight = FontWeight.Bold,
            )
          }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TohidSecondaryButton(
            text = "کپی کد",
            onClick = {
              val clip = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
              clip.setPrimaryClip(android.content.ClipData.newPlainText("کد دکان", standing))
            },
            modifier = Modifier.weight(1f),
          )
          TohidSecondaryButton(
            text = if (rotating) "…" else "کد تازه",
            enabled = !rotating,
            onClick = {
              val token = state.accessToken
              if (!token.isNullOrBlank()) {
                rotating = true
                scope.launch {
                  runCatching { ServerClient(state.serverUrl).rotateStandingCode(token) }
                    .onSuccess { standing = it }
                    .onFailure { error = (it as? ServerClient.ServerError)?.message ?: "کد عوض نشد" }
                  rotating = false
                }
              }
            },
            modifier = Modifier.weight(1f),
          )
        }
        Text(
          "«کد تازه» فقط وقتی لازم است که کد لو رفته باشد. شاگردهای فعلی بیرون نمی‌روند؛ فقط کد قبلی دیگر کسی را وارد نمی‌کند.",
          style = MaterialTheme.typography.labelSmall,
          color = colors.muted2,
          modifier = Modifier.padding(top = 8.dp),
        )
      }
      Spacer(Modifier.height(18.dp))
    }

    /* -------------------------- کارمندان -------------------------- */
    SectionTitle(
      if (maxMembers > 0) "کارمندان (${plain(members.size)} از ${plain(maxMembers)})"
      else "کارمندان"
    )
    Panel {
      if (members.isEmpty()) {
        EmptyNote("هنوز کسی به دکان نپیوسته است.")
      } else {
        members.forEach { member ->
          MemberRow(member) { removing = member }
        }
      }
    }

    Spacer(Modifier.height(18.dp))

    /* ------------------------ کدهای شاگرد ------------------------ */
    SectionTitle("کدهای پیوستن")
    Panel {
      if (codes.isEmpty()) {
        EmptyNote("کدی ساخته نشده. با دکمهٔ پایین یکی بسازید.")
      } else {
        codes.forEach { code ->
          CodeRow(code) {
            scope.launch {
              val token = state.accessToken ?: return@launch
              runCatching { ServerClient(state.serverUrl).revokeStaffCode(token, code.id) }
                .onSuccess { snackbar.showSnackbar("کد باطل شد"); reload() }
                .onFailure {
                  snackbar.showSnackbar(
                    (it as? ServerClient.ServerError)?.message ?: "کد باطل نشد"
                  )
                }
            }
          }
        }
      }
      Spacer(Modifier.height(12.dp))
      TohidButton(
        text = "ساختن کد تازه",
        icon = Icons.Filled.PersonAdd,
        enabled = !busy,
        onClick = { askNew = true },
        modifier = Modifier.fillMaxWidth(),
      )
    }

    Spacer(Modifier.height(24.dp))
  }

  /* -------------------------- ساختنِ کد -------------------------- */
  if (askNew) {
    NewCodeDialog(
      busy = busy,
      onDismiss = { askNew = false },
      onCreate = { role, uses, days ->
        askNew = false
        busy = true
        scope.launch {
          val token = state.accessToken
          if (token == null) { busy = false; return@launch }
          runCatching {
            ServerClient(state.serverUrl).createStaffCode(token, role, uses, days)
          }.onSuccess { body ->
            fresh = body["code"]?.jsonPrimitive?.content
            reload()
          }.onFailure {
            snackbar.showSnackbar((it as? ServerClient.ServerError)?.message ?: "کد ساخته نشد")
          }
          busy = false
        }
      },
    )
  }

  removing?.let { member ->
    TohidConfirmDialog(
      title = "برداشتنِ ${member.name.ifBlank { "این کارمند" }}",
      message = "دسترسی‌اش به دکان بسته می‌شود. کارهایی که تا حالا ثبت کرده سرِ جایشان می‌مانند.",
      confirmText = "بردار",
      danger = true,
      onDismiss = { removing = null },
      onConfirm = {
        removing = null
        scope.launch {
          val token = state.accessToken ?: return@launch
          runCatching { ServerClient(state.serverUrl).removeMember(token, member.id) }
            .onSuccess { snackbar.showSnackbar("کارمند برداشته شد"); reload() }
            .onFailure {
              snackbar.showSnackbar((it as? ServerClient.ServerError)?.message ?: "برداشته نشد")
            }
        }
      },
    )
  }
}

/* ============================== ردیف‌ها ============================== */

@Composable
private fun MemberRow(member: Member, onRemove: () -> Unit) {
  val colors = Shop.colors
  Row(
    Modifier.fillMaxWidth().padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      Modifier
        .size(38.dp)
        .clip(RoundedCornerShape(13.dp))
        .background(if (member.role == "owner") colors.primaryTint else colors.surface2),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        member.name.trim().take(1).ifBlank { "؟" },
        style = MaterialTheme.typography.titleSmall,
        color = if (member.role == "owner") colors.primary else colors.muted,
        fontWeight = FontWeight.Bold,
      )
    }
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(
        member.name.ifBlank { "بی‌نام" },
        style = MaterialTheme.typography.bodyMedium,
        color = colors.text,
      )
      Text(
        member.contact.ifBlank { roleText(member.role) },
        style = MaterialTheme.typography.labelSmall,
        color = colors.muted2,
      )
    }
    RoleChip(member.role, member.status)
    // صاحبِ دکان را نمی‌شود برداشت — وگرنه دکان بی‌صاحب می‌ماند
    if (member.role != "owner") {
      Spacer(Modifier.width(6.dp))
      Box(
        Modifier
          .size(34.dp)
          .clip(RoundedCornerShape(12.dp))
          .clickable(onClick = onRemove),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          Icons.Filled.Delete,
          contentDescription = "برداشتن",
          tint = colors.danger,
          modifier = Modifier.size(17.dp),
        )
      }
    }
  }
}

@Composable
private fun CodeRow(code: StaffCode, onRevoke: () -> Unit) {
  val colors = Shop.colors
  val spent = code.maxUses > 0 && code.usedCount >= code.maxUses
  val dead = code.status != "active" || spent

  Row(
    Modifier.fillMaxWidth().padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(
          "${code.hint}••••",
          style = MaterialTheme.typography.bodyMedium,
          color = if (dead) colors.muted2 else colors.text,
        )
      }
      Text(
        buildString {
          append(roleText(code.role))
          if (code.maxUses > 0) {
            append(" — ${plain(code.usedCount)} از ${plain(code.maxUses)} بار")
          } else {
            append(" — بی‌شمار")
          }
          if (dead) append(" (باطل)")
        },
        style = MaterialTheme.typography.labelSmall,
        color = colors.muted2,
      )
    }
    if (!dead) {
      Text(
        "باطل کن",
        style = MaterialTheme.typography.labelMedium,
        color = colors.danger,
        modifier = Modifier
          .clip(RoundedCornerShape(10.dp))
          .clickable(onClick = onRevoke)
          .padding(horizontal = 10.dp, vertical = 6.dp),
      )
    }
  }
}

@Composable
private fun RoleChip(role: String, status: String) {
  val colors = Shop.colors
  val tint = when {
    status != "active" -> colors.muted2
    role == "owner" -> colors.primary
    role == "manager" -> colors.accent
    else -> colors.success
  }
  Box(
    Modifier
      .clip(RoundedCornerShape(9.dp))
      .background(tint.copy(alpha = 0.15f))
      .padding(horizontal = 8.dp, vertical = 4.dp)
  ) {
    Text(
      if (status != "active") "بسته" else roleText(role),
      style = MaterialTheme.typography.labelSmall,
      color = tint,
      fontWeight = FontWeight.Bold,
    )
  }
}

private fun roleText(role: String): String = when (role) {
  "owner" -> "صاحب دکان"
  "manager" -> "مدیر"
  else -> "شاگرد"
}

/* ============================== کادرِ ساخت ============================== */

@Composable
private fun NewCodeDialog(
  busy: Boolean,
  onDismiss: () -> Unit,
  onCreate: (role: String, maxUses: Int, days: Int) -> Unit,
) {
  var role by remember { mutableStateOf("staff") }
  var uses by remember { mutableStateOf("1") }
  var days by remember { mutableStateOf("7") }
  val colors = Shop.colors

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = colors.surface,
    title = { Text("کد پیوستن تازه", color = colors.text) },
    text = {
      Column {
        Text(
          "شاگرد این کد را در صفحهٔ ورود می‌زند و به همین دکان می‌پیوندد.",
          style = MaterialTheme.typography.bodySmall,
          color = colors.muted,
        )
        Spacer(Modifier.height(14.dp))
        Text("نقش", style = MaterialTheme.typography.labelMedium, color = colors.muted)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          RoleOption("شاگرد", "فروش و ثبت", role == "staff", Modifier.weight(1f)) { role = "staff" }
          RoleOption("مدیر", "همه‌کاره جز حذف دکان", role == "manager", Modifier.weight(1f)) { role = "manager" }
        }
        Spacer(Modifier.height(14.dp))
        NumberField(uses, { uses = it }, "چند بار قابل استفاده (۰ = بی‌شمار)")
        Spacer(Modifier.height(10.dp))
        NumberField(days, { days = it }, "تا چند روز معتبر (۰ = همیشه)")
      }
    },
    confirmButton = {
      TextButton(
        enabled = !busy,
        onClick = {
          onCreate(role, uses.toIntOrNull() ?: 1, days.toIntOrNull() ?: 0)
        },
      ) { Text("بساز", color = colors.primary) }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("انصراف", color = colors.muted) }
    },
  )
}

@Composable
private fun RoleOption(
  title: String,
  detail: String,
  selected: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  val colors = Shop.colors
  Column(
    modifier
      .clip(RoundedCornerShape(Radius.sm))
      .background(if (selected) colors.primaryTint else colors.surface2)
      .clickable(onClick = onClick)
      .padding(10.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      title,
      style = MaterialTheme.typography.labelLarge,
      color = if (selected) colors.primary else colors.text,
      fontWeight = FontWeight.Bold,
    )
    Text(
      detail,
      style = MaterialTheme.typography.labelSmall,
      color = colors.muted2,
      textAlign = TextAlign.Center,
    )
  }
}

/* ============================== داده‌ها ============================== */

private data class Member(
  val id: String,
  val name: String,
  val contact: String,
  val role: String,
  val status: String,
) {
  companion object {
    fun of(row: JsonObject): Member {
      fun text(key: String) = row[key]?.jsonPrimitive?.content.orEmpty()
      val phone = text("phone")
      val email = text("email")
      return Member(
        id = text("id"),
        name = text("name"),
        contact = phone.ifBlank { email },
        role = text("role").ifBlank { "staff" },
        status = text("status").ifBlank { "active" },
      )
    }
  }
}

private data class StaffCode(
  val id: String,
  val hint: String,
  val role: String,
  val status: String,
  val maxUses: Int,
  val usedCount: Int,
) {
  companion object {
    fun of(row: JsonObject): StaffCode {
      fun text(key: String) = row[key]?.jsonPrimitive?.content.orEmpty()
      fun int(key: String) = text(key).toIntOrNull() ?: 0
      return StaffCode(
        id = text("id"),
        hint = text("hint"),
        role = text("role").ifBlank { "staff" },
        status = text("status").ifBlank { "active" },
        maxUses = int("maxUses"),
        usedCount = int("usedCount"),
      )
    }
  }
}
