package af.tohid.shop.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import af.tohid.shop.R
import af.tohid.shop.ui.components.BtnKind
import af.tohid.shop.ui.components.TButton
import af.tohid.shop.ui.theme.T

/**
 * صفحه‌ی نخست برنامه.
 *
 * نشان برنامه بالا می‌آید و کاربر انتخاب می‌کند: وارد حساب شود (تا دکان،
 * شاگردها و اشتراک کار کنند) یا فعلاً بدون حساب ادامه بدهد — در این حالت
 * برنامه کاملاً روی همین گوشی کار می‌کند.
 */
@Composable
fun WelcomeScreen(onLogin: () -> Unit, onSkip: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(T.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Image(
            painter = painterResource(R.drawable.logo_shop),
            contentDescription = stringResource(R.string.logo_desc),
            modifier = Modifier.height(132.dp),
        )
        Spacer(Modifier.height(22.dp))
        Text("shop", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = T.text)
        Spacer(Modifier.height(8.dp))
        Text(
            "مدیریت فروشگاه",
            fontSize = 14.sp, color = T.muted, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Text(
            "با ورود به حساب، دفتر دکان روی سرور شما نگه داشته می‌شود؛ " +
                "شاگردهایتان روی همان دکان کار می‌کنند و اگر گوشی عوض شد " +
                "هیچ چیزی از دست نمی‌رود.",
            fontSize = 13.sp, color = T.muted, lineHeight = 25.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        TButton("ورود یا ساخت حساب", onClick = onLogin, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        TButton(
            "فعلاً بدون حساب ادامه بده",
            kind = BtnKind.Secondary,
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "بعداً هم می‌توانید از «حساب و دکان» وارد شوید.",
            fontSize = 11.5.sp, color = T.muted2, textAlign = TextAlign.Center,
        )
    }
}
