package com.ghostnexora.ai

import android.graphics.Color
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

class NexoraActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.rgb(6, 8, 14)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        splashScreen.setOnExitAnimationListener { provider ->
            provider.iconView.animate()
                .scaleX(1.12f)
                .scaleY(1.12f)
                .setDuration(260L)
                .setInterpolator(DecelerateInterpolator())
                .start()

            provider.view.animate()
                .alpha(0f)
                .setDuration(360L)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { provider.remove() }
                .start()
        }

        setContent { NexoraRoot() }
    }
}
