package af.tohid.shop.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import af.tohid.shop.ui.theme.Radius
import af.tohid.shop.ui.theme.T

/* ================================================================== */
/*  بازخورد فشار                                                       */
/* ================================================================== */

/**
 * هر چیز قابل لمس، هنگام فشار کمی کوچک می‌شود و رها که شد برمی‌گردد.
 * همان حسی که نسخه‌ی وب با `transform:scale(.96)` می‌دهد.
 */
@Composable
fun Modifier.pressable(
    enabled: Boolean = true,
    scaleDown: Float = 0.96f,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) scaleDown else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
        label = "press",
    )
    return this
        .scale(scale)
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}

/* ================================================================== */
/*  کارت پایه — معادل .panel و .stat-card در نسخه‌ی وب                 */
/* ================================================================== */

@Composable
fun TCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier
        .clip(RoundedCornerShape(Radius.lg))
        .background(T.bg)
        .border(1.dp, T.border, RoundedCornerShape(Radius.lg))
    Column(
        modifier = if (onClick != null) base.pressable(scaleDown = 0.985f) { onClick() } else base,
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

/** پنل با تیتر و لینک «مشاهده همه» — معادل .panel + .panel-head */
@Composable
fun TPanel(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    TCard(modifier, padding = 18.dp) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = T.text)
            if (actionLabel != null && onAction != null) {
                Text(
                    actionLabel,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = T.primary,
                    modifier = Modifier.clickable { onAction() },
                )
            }
        }
        content()
    }
}

/* ================================================================== */
/*  کارت آمار — معادل .stat-card                                       */
/* ================================================================== */

enum class Tone { Blue, Green, Orange, Red }

@Composable
fun toneBg(tone: Tone): Color = when (tone) {
    Tone.Blue -> T.primaryTint
    Tone.Green -> T.successTint
    Tone.Orange -> T.warningTint
    Tone.Red -> T.dangerTint
}

@Composable
fun toneFg(tone: Tone): Color = when (tone) {
    Tone.Blue -> T.primary
    Tone.Green -> T.success
    Tone.Orange -> T.warning
    Tone.Red -> T.danger
}

@Composable
fun StatCard(
    label: String,
    value: String,
    icon: ImageVector,
    tone: Tone = Tone.Blue,
    suffix: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    TCard(modifier, padding = 16.dp, onClick = onClick) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = T.muted)
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(toneBg(tone)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = toneFg(tone), modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = T.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (suffix != null) {
                Spacer(Modifier.width(4.dp))
                Text(
                    suffix,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = T.muted,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
    }
}

/** یک ردیف از کارت‌های آمار — معادل .stat-grid (دو ستون روی گوشی). */
@Composable
fun StatRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/* ================================================================== */
/*  حالت خالی — معادل .empty-wrap                                      */
/* ================================================================== */

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    ctaLabel: String? = null,
    onCta: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 56.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(Radius.lg))
                .background(T.surface)
                .border(1.dp, T.border, RoundedCornerShape(Radius.lg)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = T.muted2, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = T.text)
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            fontSize = 12.5.sp,
            color = T.muted,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
            modifier = Modifier.widthIn(max = 260.dp),
        )
        if (ctaLabel != null && onCta != null) {
            Spacer(Modifier.height(18.dp))
            TButton(ctaLabel, onClick = onCta)
        }
    }
}

/* ================================================================== */
/*  دکمه‌ها — معادل .btn                                               */
/* ================================================================== */

enum class BtnKind { Primary, Secondary, Danger, Ghost }

@Composable
fun TButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: BtnKind = BtnKind.Primary,
    small: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val bg = when (kind) {
        BtnKind.Primary -> T.primary
        BtnKind.Secondary -> T.surface
        BtnKind.Danger -> T.danger
        BtnKind.Ghost -> Color.Transparent
    }
    val fg = when (kind) {
        BtnKind.Primary, BtnKind.Danger -> Color.White
        BtnKind.Secondary -> T.text
        BtnKind.Ghost -> T.primary
    }
    val borderColor = if (kind == BtnKind.Secondary) T.border else Color.Transparent
    val alpha = if (enabled) 1f else 0.45f

    Row(
        modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(bg.copy(alpha = bg.alpha * alpha))
            .border(1.dp, borderColor, RoundedCornerShape(Radius.sm))
            .pressable(enabled = enabled) { onClick() }
            .padding(
                horizontal = if (small) 12.dp else 18.dp,
                vertical = if (small) 8.dp else 11.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg.copy(alpha = alpha), modifier = Modifier.size(16.dp))
        }
        Text(
            label,
            fontSize = if (small) 12.sp else 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg.copy(alpha = alpha),
            maxLines = 1,
        )
    }
}

/* ================================================================== */
/*  فیلد ورودی — معادل .form-input                                     */
/* ================================================================== */

@Composable
fun TField(
    label: String?,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    numeric: Boolean = false,
    password: Boolean = false,
    error: String? = null,
    singleLine: Boolean = true,
    minHeight: Dp = 44.dp,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        if (label != null) {
            Text(
                label,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = T.muted,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        val borderColor = if (error != null) T.danger else T.border
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .clip(RoundedCornerShape(Radius.sm))
                .background(T.surface)
                .border(1.dp, borderColor, RoundedCornerShape(Radius.sm))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, fontSize = 13.5.sp, color = T.muted2)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = singleLine,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = T.text,
                        fontSize = 13.5.sp,
                    ),
                    cursorBrush = SolidColor(T.primary),
                    visualTransformation =
                        if (password) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when {
                            password -> KeyboardType.Password
                            numeric -> KeyboardType.Number
                            else -> KeyboardType.Text
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
        if (error != null) {
            Text(error, fontSize = 11.5.sp, color = T.danger, modifier = Modifier.padding(top = 5.dp))
        }
    }
}

/** نوار جستجو — معادل ورودی جستجوی صفحات فهرست */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "جستجو…",
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .background(T.surface)
            .border(1.dp, T.border, RoundedCornerShape(Radius.sm))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = T.muted2, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, fontSize = 13.sp, color = T.muted2)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = T.text, fontSize = 13.sp),
                cursorBrush = SolidColor(T.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "پاک کردن",
                tint = T.muted,
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onValueChange("") },
            )
        }
    }
}

/* ================================================================== */
/*  چیپ فیلتر — معادل .seg-btn / فیلترهای بالای فهرست                  */
/* ================================================================== */

@Composable
fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(if (selected) T.primary else T.surface)
            .border(1.dp, if (selected) T.primary else T.border, RoundedCornerShape(Radius.sm))
            .pressable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else T.text,
            maxLines = 1,
        )
    }
}

/** برچسب وضعیت — معادل .sale-status-badge و برچسب‌های موجودی */
@Composable
fun Badge(text: String, tone: Tone) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(toneBg(tone))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = toneFg(tone), maxLines = 1)
    }
}

/* ================================================================== */
/*  سرتیتر صفحه — معادل .page-toolbar                                  */
/* ================================================================== */

@Composable
fun PageToolbar(
    title: String,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = T.text)
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.5.sp, color = T.muted, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            actions()
        }
    }
}

/** ردیف کلید/مقدار — در جمع‌بندی فاکتور و گزارش‌ها */
@Composable
fun SummaryRow(label: String, value: String, bold: Boolean = false, valueColor: Color? = null) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = if (bold) 14.sp else 13.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = if (bold) T.text else T.muted,
        )
        Text(
            value,
            fontSize = if (bold) 15.sp else 13.sp,
            fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = valueColor ?: T.text,
        )
    }
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(T.border))
}

/* ================================================================== */
/*  گفتگوی تأیید                                                       */
/* ================================================================== */

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "تأیید",
    danger: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = T.bg,
        shape = RoundedCornerShape(Radius.lg),
        title = { Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = T.text) },
        text = { Text(message, fontSize = 13.sp, color = T.muted, lineHeight = 24.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (danger) T.danger else T.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف", color = T.muted, fontSize = 13.sp)
            }
        },
    )
}

/* ================================================================== */
/*  بدنه‌ی صفحه با اسکرول                                              */
/* ================================================================== */

@Composable
fun ScreenBody(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(T.surface)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        content = content,
    )
}

/* ================================================================== */
/*  برگه‌ی فرم (افزودن/ویرایش) — معادل مودال نسخه‌ی وب                 */
/* ================================================================== */

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FormSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = T.bg,
        contentColor = T.text,
        shape = RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.lg),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(width = 38.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(T.border)
                )
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(top = 8.dp, bottom = 28.dp),
        ) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = T.text)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

/** دو دکمه‌ی پایین فرم — معادل .modal-actions */
@Composable
fun FormActions(
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    deleteLabel: String? = null,
    onDelete: (() -> Unit)? = null,
) {
    Column(Modifier.padding(top = 18.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TButton("انصراف", onCancel, Modifier.weight(1f), kind = BtnKind.Secondary)
            TButton(confirmLabel, onConfirm, Modifier.weight(1f))
        }
        if (deleteLabel != null && onDelete != null) {
            Spacer(Modifier.height(10.dp))
            TButton(deleteLabel, onDelete, Modifier.fillMaxWidth(), kind = BtnKind.Danger)
        }
    }
}

/** پیام کوتاه بالای فهرست — برای خطا یا تأیید عملیات. */
@Composable
fun Notice(text: String, tone: Tone) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(toneBg(tone))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 12.5.sp, color = T.text, lineHeight = 23.sp)
    }
}
