package ao.bedi.cronosestudo
import android.app.*
import android.content.*
import android.os.*
import androidx.activity.ComponentActivity
class AlarmReceiver:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){if(Build.VERSION.SDK_INT>=26)c.startForegroundService(Intent(c,AlarmService::class.java).putExtras(i))}}
class AlarmService:Service(){override fun onBind(i:Intent?)=null;override fun onStartCommand(i:Intent?,f:Int,id:Int):Int{val n=Notification.Builder(this,"alarm").setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Hora de estudar").setContentText(i?.getStringExtra("subject")?:"Sessão de estudo").build();startForeground(77,n);return START_NOT_STICKY}}
class AlarmActivity:ComponentActivity()
