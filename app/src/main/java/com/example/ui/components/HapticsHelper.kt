package com.example.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

object AppHaptics {
    fun triggerClick(context: Context, view: View? = null, composeHaptic: HapticFeedback? = null) {
        var performed = false
        if (view != null) {
            performed = runCatching {
                val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.CONFIRM
                } else {
                    HapticFeedbackConstants.KEYBOARD_TAP
                }
                view.performHapticFeedback(
                    constant,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            }.getOrDefault(false)
        }
        if (!performed && composeHaptic != null) {
            runCatching { composeHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
        }
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(14L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(14L)
                }
            }
        }
    }
}

@Composable
fun rememberClickHaptic(): () -> Unit {
    val context = LocalContext.current
    val view = LocalView.current
    val composeHaptic = LocalHapticFeedback.current
    return remember(context, view, composeHaptic) {
        { AppHaptics.triggerClick(context, view, composeHaptic) }
    }
}
