package ao.bedi.cronosestudo

import android.app.*
import android.content.*
import android.os.*
import androidx.activity.ComponentActivity

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val service = Intent(context, AlarmService::class.java).putExtras(intent)
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service) else context.startService(service)
    }
}

class AlarmService : Service() {
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = Notification.Builder(this, "alarm")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Hora de estudar")
            .setContentText(intent?.getStringExtra("subject") ?: "Sessão de estudo")
            .build()
        startForeground(77, notification)
        return START_NOT_STICKY
    }
}

class AlarmActivity : ComponentActivity()
