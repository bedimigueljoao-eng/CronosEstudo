package ao.bedi.cronosestudo

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtras(intent)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}

class AlarmService : Service() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate() {
        super.onCreate()

        val alarmUri = RingtoneManager.getDefaultUri(
            RingtoneManager.TYPE_ALARM
        )

        ringtone = RingtoneManager.getRingtone(this, alarmUri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                isLooping = true
            }
        }

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (intent?.action == ACTION_STOP) {
            stopAlarm()
            return START_NOT_STICKY
        }

        val subject = intent?.getStringExtra(EXTRA_SUBJECT)
            ?: "Sessão de estudo"

        val alarmScreenIntent = Intent(
            this,
            AlarmActivity::class.java
        ).apply {
            putExtra(EXTRA_SUBJECT, subject)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            10,
            alarmScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(
            this,
            AlarmService::class.java
        ).apply {
            action = ACTION_STOP
        }

        val stopPendingIntent = PendingIntent.getService(
            this,
            11,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(
            this,
            CHANNEL_ID
        )
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Hora de estudar")
            .setContentText(subject)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(
                android.R.drawable.ic_media_pause,
                "PARAR",
                stopPendingIntent
            )
            .build()

        startForeground(NOTIFICATION_ID, notification)

        if (ringtone?.isPlaying != true) {
            ringtone?.play()
        }

        startVibration()

        startActivity(alarmScreenIntent)

        return START_STICKY
    }

    private fun startVibration() {
        val vibrationPattern = longArrayOf(
            0,
            1_000,
            500,
            1_000,
            500
        )

        vibrator?.vibrate(
            VibrationEffect.createWaveform(
                vibrationPattern,
                1
            )
        )
    }

    private fun stopAlarm() {
        ringtone?.stop()
        vibrator?.cancel()

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        ringtone?.stop()
        vibrator?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "alarm"
        const val NOTIFICATION_ID = 77
        const val ACTION_STOP = "ao.bedi.cronosestudo.STOP_ALARM"
        const val EXTRA_SUBJECT = "subject"
    }
}

class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setShowWhenLocked(true)
        setTurnScreenOn(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        setContent {
            val subject = intent.getStringExtra(
                AlarmService.EXTRA_SUBJECT
            ) ?: "Sessão de estudo"

            AlarmScreen(
                subject = subject,
                onStartStudy = {
                    stopAlarm()

                    startActivity(
                        Intent(
                            this,
                            MainActivity::class.java
                        ).apply {
                            addFlags(
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                            )
                        }
                    )

                    finish()
                },
                onStopAlarm = {
                    stopAlarm()
                    finish()
                },
                onSnooze = {
                    snoozeAlarm(subject)
                    stopAlarm()
                    finish()
                }
            )
        }
    }

    private fun stopAlarm() {
        startService(
            Intent(
                this,
                AlarmService::class.java
            ).apply {
                action = AlarmService.ACTION_STOP
            }
        )
    }

    private fun snoozeAlarm(subject: String) {
        val alarmIntent = Intent(
            this,
            AlarmReceiver::class.java
        ).apply {
            putExtra(
                AlarmService.EXTRA_SUBJECT,
                subject
            )
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            999,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(
            AlarmManager::class.java
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 300_000L,
            pendingIntent
        )
    }
}

@Composable
private fun AlarmScreen(
    subject: String,
    onStartStudy: () -> Unit,
    onStopAlarm: () -> Unit,
    onSnooze: () -> Unit
) {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF10233F),
                            Color(0xFF6557E8)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(130.dp),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.15f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = "Alarme",
                        modifier = Modifier.padding(28.dp),
                        tint = Color.White
                    )
                }

                Text(
                    text = "HORA DE ESTUDAR",
                    modifier = Modifier.padding(top = 24.dp),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = subject,
                    modifier = Modifier.padding(18.dp),
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold
                )

                Button(
                    onClick = onStartStudy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp)
                ) {
                    Text("INICIAR ESTUDO")
                }

                OutlinedButton(
                    onClick = onStopAlarm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 28.dp,
                            vertical = 10.dp
                        ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text("PARAR ALARME")
                }

                TextButton(
                    onClick = onSnooze
                ) {
                    Text(
                        text = "ADIAR 5 MINUTOS",
                        color = Color.White
                    )
                }
            }
        }
    }
}
