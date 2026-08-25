package af.tohid.shop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import af.tohid.shop.ui.TohidRoot
import af.tohid.shop.ui.theme.TohidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TohidTheme {
                // کل برنامه راست‌به‌چپ است
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    TohidRoot()
                }
            }
        }
    }
}
