package ir.vil3ntec.tohid.ui.v3.products

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrNewProduct
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Space
import ir.vil3ntec.tohid.ui.v3.v3Pressable

/**
 *  کادرِ کشویِ جمع‌وجور (خواستهٔ صاحب‌کار ۱۴۰۵/۰۸/۲۴: «دسته و واحد و هر فهرستِ انتخابی کادرِ کشویی»): همان قابِ
 *  `V3TextField` (برچسب بالا، ستارهٔ «لازم»، خطای درجا زیرش) با مقدارِ برگزیده یا «انتخاب کنید» و فلشِ پایین. زدن ⇐ فهرستِ
 *  گزینه‌ها زیرِ همان کادر (برگزیده تیک دارد)، و آخرین ردیف «+ [addLabel]» ⇐ [onAdd] (برگهٔ کوچکِ نامِ تازه).
 *  گزینهٔ برگزیده‌ای که در فهرست نیست (دستهٔ تازهٔ همین فرم) آخرِ فهرست می‌آید تا گم نشود.
 *  ⏳ کادرِ کشوییِ مشترکِ کیت هنوز نیست (`claude/e-rows-63w` پیدا نشد) ⇒ فعلاً در همین بسته؛ با آمدنِ آن جایگزین شود.
 */
@Composable
internal fun ChoiceDropdown(
  label: String,
  value: String,
  options: List<String>,
  onPick: (String) -> Unit,
  modifier: Modifier = Modifier,
  required: Boolean = false,
  error: String? = null,
  addLabel: String? = null,
  onAdd: (() -> Unit)? = null,
  /** نوشتهٔ گزینهٔ «هیچ» (مثلاً «هیچ‌کدام» ِ تأمین‌کننده) ⇒ ردیفِ نخست با مقدارِ خالی */
  noneLabel: String? = null,
  /** نامِ نمایشیِ یک گزینه (شناسه ⇒ نام)؛ پیش‌فرض خودِ گزینه */
  display: (String) -> String = { it },
  /** فهرست بسته شد (با انتخاب یا بی آن) — همان «ترکِ کادر» ِ کادرِ نوشتنی: از این پس خطایش دیده شود */
  onClosed: () -> Unit = {},
) {
  val c = PhoneV3.colors
  var open by remember { mutableStateOf(false) }
  fun close() { if (open) { open = false; onClosed() } }
  val shape = RoundedCornerShape(V3Space.controlRadius)
  val ring = when {
    error != null -> PhoneV3Roles.errorText(c)
    open -> PhoneV3Roles.focus(c)
    else -> PhoneV3Roles.inputBorder(c)
  }
  val all = options + listOfNotNull(value.takeIf { it.isNotBlank() && it !in options })
  val shown = value.takeIf { it.isNotBlank() }?.let(display) ?: noneLabel?.takeIf { value.isBlank() }
  Column(modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)) {
      Text(label, style = v3Text(13, FontWeight.SemiBold, 18), color = if (open) PhoneV3Roles.brandText(c) else c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (required) Text(" *", style = v3Text(13, FontWeight.SemiBold, 18), color = PhoneV3Roles.errorText(c))
    }
    Box {
      Row(
        Modifier
          .fillMaxWidth()
          .heightIn(min = V3Space.control)
          .clip(shape)
          .background(PhoneV3Roles.inputFill(c))
          .border(if (open || error != null) 2.dp else 1.dp, ring, shape)
          .v3Pressable(label = label, role = Role.DropdownList, onClick = { open = true })
          .semantics {
            contentDescription = label
            stateDescription = shown ?: StrNewProduct.choose
            if (error != null) error(error)
          }
          .padding(start = 14.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          shown ?: StrNewProduct.choose,
          style = v3Text(16, FontWeight.Medium, 22),
          color = if (shown == null) c.inkMuted.copy(alpha = 0.75f) else c.ink,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f).padding(vertical = 12.dp),
        )
        Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = c.icon, modifier = Modifier.size(20.dp))
      }
      DropdownMenu(
        expanded = open,
        onDismissRequest = ::close,
        containerColor = c.card,
        shape = RoundedCornerShape(V3Space.cardRadius),
        modifier = Modifier.widthIn(min = 180.dp),
      ) {
        if (noneLabel != null) Choice(noneLabel, value.isBlank()) { close(); onPick("") }
        all.forEach { o -> Choice(display(o), o == value) { close(); onPick(o) } }
        if (addLabel != null && onAdd != null) {
          if (all.isNotEmpty() || noneLabel != null) HorizontalDivider(color = c.hairline)
          DropdownMenuItem(
            text = { Text(addLabel, style = v3Text(15, FontWeight.SemiBold, 20), color = PhoneV3Roles.brandText(c)) },
            leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null, tint = PhoneV3Roles.brandText(c)) },
            onClick = { close(); onAdd() },
          )
        }
      }
    }
    if (error != null) {
      Row(Modifier.padding(start = 2.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = PhoneV3Roles.errorText(c), modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(error, style = v3Text(12, FontWeight.Medium, 17), color = PhoneV3Roles.errorText(c))
      }
    }
  }
}

/** یک گزینهٔ فهرست: برگزیده با تیک و نوشتهٔ پررنگ */
@Composable
private fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
  val c = PhoneV3.colors
  DropdownMenuItem(
    text = {
      Text(
        text,
        style = v3Text(15, if (selected) FontWeight.SemiBold else FontWeight.Medium, 20),
        color = if (selected) PhoneV3Roles.brandText(c) else c.ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    },
    trailingIcon = if (selected) {
      { Icon(Icons.Outlined.Check, contentDescription = StrNewProduct.selected, tint = PhoneV3Roles.brandText(c)) }
    } else null,
    onClick = onClick,
    modifier = Modifier.semantics { this.selected = selected },
  )
}
