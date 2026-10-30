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
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class AlarmReceiver:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){val service=Intent(c,AlarmService::class.java).putExtras(i);if(Build.VERSION.SDK_INT>=26)c.startForegroundService(service)else c.startService(service)}}
class AlarmService:Service(){private var ring:Ringtone?=null;private var vib:Vibrator?=null;override fun onCreate(){super.onCreate();ring=RingtoneManager.getRingtone(this,RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)).apply{audioAttributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build();if(Build.VERSION.SDK_INT>=28)isLooping=true};vib=if(Build.VERSION.SDK_INT>=31)getSystemService(VibratorManager::class.java).defaultVibrator else @Suppress("DEPRECATION")(getSystemService(VIBRATOR_SERVICE) as Vibrator)};override fun onStartCommand(i:Intent?,f:Int,id:Int):Int{if(i?.action=="STOP"){stopAll();return START_NOT_STICKY};val subject=i?.getStringExtra("subject")?:"Hora de estudar";val full=PendingIntent.getActivity(this,10,Intent(this,AlarmActivity::class.java).putExtra("subject",subject),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);val stop=PendingIntent.getService(this,11,Intent(this,AlarmService::class.java).setAction("STOP"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);val n=Notification.Builder(this,"alarm").setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Hora de estudar").setContentText(subject).setCategory(Notification.CATEGORY_ALARM).setOngoing(true).setFullScreenIntent(full,true).addAction(android.R.drawable.ic_media_pause,"PARAR",stop).build();startForeground(77,n);ring?.play();vib?.vibrate(VibrationEffect.createWaveform(longArrayOf(0,800,400),0));return START_STICKY};private fun stopAll(){ring?.stop();vib?.cancel();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()};override fun onDestroy(){ring?.stop();vib?.cancel();super.onDestroy()};override fun onBind(i:Intent?)=null}
class AlarmActivity:ComponentActivity(){override fun onCreate(b:Bundle?){super.onCreate(b);setShowWhenLocked(true);setTurnScreenOn(true);val subject=intent.getStringExtra("subject")?:"Sessão de estudo";setContent{MaterialTheme{Box(Modifier.fillMaxSize().background(Color(0xFF10233F)),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Text("HORA DE ESTUDAR",color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Bold);Text(subject,color=Color.White,fontSize=32.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(20.dp));Button({stopAlarm();startActivity(Intent(this@AlarmActivity,MainActivity::class.java));finish()},Modifier.fillMaxWidth().padding(horizontal=28.dp)){Text("INICIAR ESTUDO")};OutlinedButton({stopAlarm();finish()},Modifier.fillMaxWidth().padding(28.dp),colors=ButtonDefaults.outlinedButtonColors(contentColor=Color.White)){Text("PARAR ALARME")}}}}}};private fun stopAlarm(){startService(Intent(this,AlarmService::class.java).setAction("STOP"))}}
