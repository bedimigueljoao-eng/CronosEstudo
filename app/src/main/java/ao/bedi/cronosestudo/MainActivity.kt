package ao.bedi.cronosestudo

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as C
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*

data class StudySession(val id:Long=System.currentTimeMillis(), val subject:String, val hour:Int, val minute:Int, val duration:Int, val colorIndex:Int=0, val done:Boolean=false)

class MainActivity: ComponentActivity() {
    private val notificationPermission=registerForActivityResult(ActivityResultContracts.RequestPermission()){}
    override fun onCreate(savedInstanceState:Bundle?){ super.onCreate(savedInstanceState); if(Build.VERSION.SDK_INT>=33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS); createChannel(); setContent{ App(this) } }
    private fun createChannel(){ if(Build.VERSION.SDK_INT>=26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("study","Lembretes de estudo",NotificationManager.IMPORTANCE_HIGH).apply{description="Alarmes das sessões";enableVibration(true)}) }
}

class AlarmReceiver:BroadcastReceiver(){ override fun onReceive(c:Context,i:Intent){
    val title=i.getStringExtra("subject")?:"Sessão de estudo"
    val open=PendingIntent.getActivity(c,0,Intent(c,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
    val n=Notification.Builder(c,"study").setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Hora de estudar").setContentText(title).setContentIntent(open).setAutoCancel(true).build()
    c.getSystemService(NotificationManager::class.java).notify(i.getLongExtra("id",1).toInt(),n)
}}

private val colors=listOf(C(0xFF6D5DFB),C(0xFF00A896),C(0xFFF59E0B),C(0xFFEF476F),C(0xFF0284C7))

fun load(c:Context):MutableList<StudySession>{ val a=JSONArray(c.getSharedPreferences("db",0).getString("sessions","[]")); return MutableList(a.length()){k->a.getJSONObject(k).let{StudySession(it.getLong("id"),it.getString("s"),it.getInt("h"),it.getInt("m"),it.getInt("d"),it.getInt("c"),it.getBoolean("ok"))}} }
fun save(c:Context,x:List<StudySession>){val a=JSONArray();x.forEach{a.put(JSONObject().put("id",it.id).put("s",it.subject).put("h",it.hour).put("m",it.minute).put("d",it.duration).put("c",it.colorIndex).put("ok",it.done))};c.getSharedPreferences("db",0).edit().putString("sessions",a.toString()).apply()}
fun alarm(c:Context,s:StudySession){ val am=c.getSystemService(AlarmManager::class.java);if(Build.VERSION.SDK_INT>=31&&!am.canScheduleExactAlarms()){c.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return}; val t=LocalDateTime.of(LocalDate.now(),LocalTime.of(s.hour,s.minute)).let{if(it.isBefore(LocalDateTime.now()))it.plusDays(1) else it}.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();val i=Intent(c,AlarmReceiver::class.java).putExtra("subject",s.subject).putExtra("id",s.id);val p=PendingIntent.getBroadcast(c,s.id.toInt(),i,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,t,p)}

@Composable fun App(c:Context){
 var tab by remember{mutableIntStateOf(0)};var sessions by remember{mutableStateOf(load(c))};var add by remember{mutableStateOf(false)}
 fun update(n:List<StudySession>){sessions=n.toMutableList();save(c,sessions)}
 MaterialTheme(colorScheme=lightColorScheme(primary=C(0xFF6D5DFB),secondary=C(0xFF00A896),background=C(0xFFF6F7FB))){Scaffold(containerColor=C(0xFFF6F7FB),bottomBar={NavigationBar{listOf(Icons.Default.Home to "Hoje",Icons.Default.DateRange to "Agenda",Icons.Default.Timer to "Cronómetro",Icons.Default.BarChart to "Análise",Icons.Default.Settings to "Definições").forEachIndexed{i,p->NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(p.first,p.second)},label={Text(p.second, fontSize=10.sp)})}}},floatingActionButton={if(tab<2)FloatingActionButton(onClick={add=true}){Icon(Icons.Default.Add,"Adicionar")}}){pad->Box(Modifier.padding(pad)){when(tab){0->Today(sessions,{update(it)},{add=true});1->Agenda(sessions,{update(it)});2->TimerPage{subject,mins->update(sessions+StudySession(subject=subject,hour=LocalTime.now().hour,minute=LocalTime.now().minute,duration=mins,done=true))};3->Analysis(sessions);else->SettingsPage()}};if(add)AddDialog({add=false}){s->update((sessions+s).sortedWith(compareBy({it.hour},{it.minute})));alarm(c,s);add=false}}
}}}

@Composable fun Header(title:String,subtitle:String){Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(C(0xFF111827),C(0xFF4338CA))),RoundedCornerShape(bottomStart=28.dp,bottomEnd=28.dp)).padding(24.dp)){Column{Text(title,C.White,28.sp,FontWeight.Bold);Text(subtitle,C(0xFFD1D5DB))}}}
@Composable fun Today(x:List<StudySession>,update:(List<StudySession>)->Unit,add:()->Unit){Column{Header("Olá, Bedi 👋",LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM",Locale("pt","PT"))));val done=x.filter{it.done}.sumOf{it.duration};val total=x.sumOf{it.duration};Card(Modifier.padding(16.dp).fillMaxWidth(),shape=RoundedCornerShape(22.dp)){Column(Modifier.padding(18.dp)){Text("Progresso de hoje",fontWeight=FontWeight.Bold);Spacer(Modifier.height(10.dp));LinearProgressIndicator(if(total==0)0f else done.toFloat()/total,Modifier.fillMaxWidth());Text("${done/60}h ${done%60}min de ${total/60}h ${total%60}min",modifier=Modifier.padding(top=8.dp))}};SessionList(x,update)} }
@Composable fun Agenda(x:List<StudySession>,update:(List<StudySession>)->Unit){Column{Header("Agenda","Organize as suas sessões");SessionList(x,update)}}
@Composable fun SessionList(x:List<StudySession>,update:(List<StudySession>)->Unit){if(x.isEmpty())Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("Nenhuma sessão planeada.")}else LazyColumn(Modifier.padding(horizontal=16.dp)){itemsIndexed(x,key={_,s->s.id}){i,s->Card(Modifier.fillMaxWidth().padding(vertical=6.dp),shape=RoundedCornerShape(18.dp)){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(10.dp,48.dp).background(colors[s.colorIndex%colors.size],RoundedCornerShape(8.dp)));Column(Modifier.padding(start=12.dp).weight(1f)){Text(s.subject,fontWeight=FontWeight.Bold);Text("%02d:%02d • %d min".format(s.hour,s.minute,s.duration),color=C.Gray)};IconButton(onClick={update(x.map{if(it.id==s.id)it.copy(done=!it.done)else it})}){Icon(if(s.done)Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,"Concluir",tint=if(s.done)C(0xFF00A896) else C.Gray)};IconButton(onClick={if(i>0){val n=x.toMutableList();Collections.swap(n,i,i-1);update(n)}}){Icon(Icons.Default.KeyboardArrowUp,"Subir")};IconButton(onClick={update(x.filterNot{it.id==s.id})}){Icon(Icons.Default.Delete,"Eliminar",tint=C(0xFFEF476F))}}}}}}
@Composable fun AddDialog(close:()->Unit,ok:(StudySession)->Unit){var name by remember{mutableStateOf("")};var time by remember{mutableStateOf("18:00")};var dur by remember{mutableStateOf("60")};AlertDialog(onDismissRequest=close,title={Text("Nova sessão")},text={Column{OutlinedTextField(name,{name=it},label={Text("Disciplina")});OutlinedTextField(time,{time=it},label={Text("Horário (HH:mm)")});OutlinedTextField(dur,{dur=it.filter(Char::isDigit)},label={Text("Duração em minutos")})}},confirmButton={Button(onClick={runCatching{val p=time.split(":");if(name.isNotBlank())ok(StudySession(subject=name,hour=p[0].toInt().coerceIn(0,23),minute=p[1].toInt().coerceIn(0,59),duration=(dur.toIntOrNull()?:60).coerceIn(1,720),colorIndex=(System.currentTimeMillis()%colors.size).toInt()))}}){Text("Guardar")}},dismissButton={TextButton(onClick=close){Text("Cancelar")}})}
@Composable fun TimerPage(done:(String,Int)->Unit){var running by remember{mutableStateOf(false)};var sec by remember{mutableLongStateOf(0)};var subject by remember{mutableStateOf("Estudo livre")};LaunchedEffect(running){while(running){delay(1000);sec++}};Column{Header("Cronómetro","Foco sem distrações");Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally){OutlinedTextField(subject,{subject=it},label={Text("Disciplina")});Text("%02d:%02d:%02d".format(sec/3600,(sec/60)%60,sec%60),fontSize=46.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(28.dp));Button(onClick={running=!running},Modifier.fillMaxWidth()){Icon(if(running)Icons.Default.Pause else Icons.Default.PlayArrow,null);Text(if(running)" Pausar" else " Iniciar")};OutlinedButton(onClick={if(sec>0){done(subject,(sec/60).toInt().coerceAtLeast(1));sec=0;running=false}},Modifier.fillMaxWidth().padding(top=10.dp)){Text("Terminar e guardar")}}}}
@Composable fun Analysis(x:List<StudySession>){val done=x.filter{it.done};val groups=done.groupBy{it.subject}.mapValues{it.value.sumOf{v->v.duration}}.toList().sortedByDescending{it.second};Column{Header("Análise","Acompanhe a sua evolução");LazyColumn(Modifier.padding(16.dp)){item{Row{Stat("Hoje","${done.sumOf{it.duration}/60}h ${done.sumOf{it.duration}%60}m",Modifier.weight(1f));Spacer(Modifier.width(10.dp));Stat("Conclusão","${if(x.isEmpty())0 else done.size*100/x.size}%",Modifier.weight(1f))};Text("Por disciplina",fontWeight=FontWeight.Bold,fontSize=18.sp,modifier=Modifier.padding(vertical=18.dp))};itemsIndexed(groups){_,g->Card(Modifier.fillMaxWidth().padding(vertical=5.dp)){Column(Modifier.padding(14.dp)){Row{Text(g.first,Modifier.weight(1f),fontWeight=FontWeight.SemiBold);Text("${g.second/60}h ${g.second%60}m")};LinearProgressIndicator(if(done.sumOf{it.duration}==0)0f else g.second.toFloat()/done.sumOf{it.duration},Modifier.fillMaxWidth().padding(top=8.dp))}}}}}
@Composable fun Stat(a:String,b:String,m:Modifier){Card(m,colors=CardDefaults.cardColors(containerColor=C.White)){Column(Modifier.padding(16.dp)){Text(a,color=C.Gray);Text(b,fontSize=24.sp,fontWeight=FontWeight.Bold)}}}
@Composable fun SettingsPage(){Column{Header("Definições","Personalize a experiência");Card(Modifier.padding(16.dp).fillMaxWidth()){Column(Modifier.padding(18.dp)){Text("CronosEstudo",fontWeight=FontWeight.Bold);Text("Versão 0.1.0");Text("Os dados ficam guardados offline no dispositivo.",modifier=Modifier.padding(top=12.dp),color=C.Gray)}}}}
