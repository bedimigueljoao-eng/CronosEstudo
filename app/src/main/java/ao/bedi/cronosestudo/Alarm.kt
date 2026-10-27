package ao.bedi.cronosestudo

import android.app.*
import android.content.*
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class AlarmReceiver:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){val s=Intent(c,AlarmService::class.java).putExtras(i);if(Build.VERSION.SDK_INT>=26)c.startForegroundService(s)else c.startService(s)}}
class AlarmService:Service(){private var ring:Ringtone?=null;private var vib:Vibrator?=null
 override fun onCreate(){super.onCreate();val uri=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);ring=RingtoneManager.getRingtone(this,uri).apply{audioAttributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build();if(Build.VERSION.SDK_INT>=28)isLooping=true};vib=if(Build.VERSION.SDK_INT>=31)getSystemService(VibratorManager::class.java).defaultVibrator else @Suppress("DEPRECATION")(getSystemService(VIBRATOR_SERVICE) as Vibrator)}
 override fun onStartCommand(i:Intent?,f:Int,id:Int):Int{if(i?.action=="STOP"){stopNow();return START_NOT_STICKY};val sub=i?.getStringExtra("subject")?:"Hora de estudar";val full=PendingIntent.getActivity(this,10,Intent(this,AlarmActivity::class.java).putExtra("subject",sub),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);val stop=PendingIntent.getService(this,11,Intent(this,AlarmService::class.java).setAction("STOP"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);val n=Notification.Builder(this,"alarm").setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Hora de estudar").setContentText(sub).setCategory(Notification.CATEGORY_ALARM).setOngoing(true).setFullScreenIntent(full,true).addAction(android.R.drawable.ic_media_pause,"PARAR",stop).build();startForeground(77,n);ring?.play();vib?.vibrate(VibrationEffect.createWaveform(longArrayOf(0,800,400),0));startActivity(Intent(this,AlarmActivity::class.java).putExtra("subject",sub).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP));return START_STICKY}
 private fun stopNow(){ring?.stop();vib?.cancel();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()};override fun onDestroy(){ring?.stop();vib?.cancel();super.onDestroy()};override fun onBind(i:Intent?)=null}
class AlarmActivity:ComponentActivity(){override fun onCreate(b:Bundle?){super.onCreate(b);setShowWhenLocked(true);setTurnScreenOn(true);setContent{val sub=intent.getStringExtra("subject")?:"Sessão de estudo";MaterialTheme{Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF10233F),Color(0xFF6557E8)))),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Surface(shape=CircleShape,color=Color.White.copy(alpha=.15f),modifier=Modifier.size(130.dp)){Icon(Icons.Default.Alarm,null,tint=Color.White,modifier=Modifier.padding(28.dp))};Text("HORA DE ESTUDAR",Color.White,18.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=24.dp));Text(sub,Color.White,34.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(18.dp));Button({stopAlarm();startActivity(Intent(this@AlarmActivity,MainActivity::class.java));finish()},Modifier.fillMaxWidth().padding(horizontal=28.dp)){Text("INICIAR ESTUDO")};OutlinedButton({stopAlarm();finish()},Modifier.fillMaxWidth().padding(horizontal=28.dp,vertical=10.dp),colors=ButtonDefaults.outlinedButtonColors(contentColor=Color.White)){Text("PARAR ALARME")};TextButton({snooze(sub);stopAlarm();finish()}){Text("ADIAR 5 MINUTOS",color=Color.White)}}}}}}
 private fun stopAlarm(){startService(Intent(this,AlarmService::class.java).setAction("STOP"))}
 private fun snooze(sub:String){val i=Intent(this,AlarmReceiver::class.java).putExtra("subject",sub);val p=PendingIntent.getBroadcast(this,999,i,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);getSystemService(AlarmManager::class.java).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,System.currentTimeMillis()+300000,p)} }
