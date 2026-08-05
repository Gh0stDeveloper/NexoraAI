package com.ghostnexora.ai

import android.graphics.Color
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class NexoraActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.rgb(13, 13, 13)),
        )

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
