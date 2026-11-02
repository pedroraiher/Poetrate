package com.gal

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gal.data.local.MediaStoreRefresher
import javax.inject.Inject
import com.gal.ui.navigation.GalNavHost
import com.gal.ui.screens.settings.SettingsViewModel
import com.gal.ui.theme.GalTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var refresher: MediaStoreRefresher
    private val settingsViewModel: SettingsViewModel by viewModels()


    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()


        setContent {
            val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
            val view = LocalView.current

            // App-wide screenshot blocking (FLAG_SECURE on the main window)
            DisposableEffect(settingsState.blockScreenshots) {
                val blockScreenshots = settingsState.blockScreenshots
                if (blockScreenshots) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                onDispose {
                    if (blockScreenshots) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            // Tapjacking protection: ignore touches when overlaid by another window
            DisposableEffect(settingsState.filterTouches) {
                view.filterTouchesWhenObscured = settingsState.filterTouches
                onDispose { view.filterTouchesWhenObscured = false }
            }

            GalTheme(
                themeMode        = settingsState.themeMode,
                catppuccinAccent = settingsState.catppuccinAccent,
                draculaAccent    = settingsState.draculaAccent,
                ptyxisPalette    = settingsState.ptyxisPalette,
                appFont          = settingsState.appFont,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    var isUnlocked by rememberSaveable { mutableStateOf(false) }

                    // Only handle logic once settings are loaded
                    if (settingsState.isLoaded) {
                        LaunchedEffect(Unit) {
                            if (settingsState.biometricLock && !isUnlocked) {
                                delay(300)
                                authenticate { success ->
                                    if (success) isUnlocked = true
                                }
                            } else if (!settingsState.biometricLock) {
                                isUnlocked = true
                            }
                        }

                        if (isUnlocked || !settingsState.biometricLock) {
                            GalApp()
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize().navigationBarsPadding().statusBarsPadding(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Outlined.Lock,
                                        null,
                                        modifier = Modifier.size(72.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(24.dp))
                                    Text(
                                        "Mídia Privada bloqueada",
                                        style = MaterialTheme.typography.headlineSmall,
                                        modifier = Modifier.padding(bottom = 32.dp)
                                    )
                                    Button(onClick = {
                                        authenticate { success -> if (success) isUnlocked = true }
                                    }) {
                                        Text("Unlock with Biometrics")
                                    }
                                }
                            }
                        }
                    } else {
                        // Loading state
                        Box(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh only this app's private library after captures and edits.
        refresher.bump()
    }

    private fun authenticate(onResult: (Boolean) -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onResult(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onResult(false)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onResult(false)
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Desbloquear Mídia Privada")
            .setSubtitle("Authenticate to view your gallery")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .setConfirmationRequired(false)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}

@Composable
private fun GalApp() {
    GalNavHost()
}
