package ao.bedi.cronosestudo

import android.Manifest
import android.app.*
import android.content.*
import android.os.*
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Session(val id:Long=System.currentTimeMillis(),val subject:String,val date:String,val start:String,val end:String,val minutes:Int,val alarm:Boolean=true,val lead:Int=0,val done:Boolean=false)
private val Blue=Color(0xFF1684EA); private val Navy=Color(0xFF10233F); private val Bg=Color(0xFFF5F7FC); private val Green=Color(0xFF0DBA73); private val Pink=Color(0xFFFF426D)

class MainActivity:ComponentActivity(){
 private val ask=registerForActivityResult(ActivityResultContracts.RequestPermission()){}
 override fun onCreate(b:Bundle?){super.onCreate(b); channels();if(Build.VERSION.SDK_INT>=33)ask.launch(Manifest.permission.POST_NOTIFICATIONS);setContent{App(this)}}
 private fun channels(){if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("alarm","Alarmes de estudo",NotificationManager.IMPORTANCE_HIGH).apply{setSound(null,null);enableVibration(true);lockscreenVisibility=Notification.VISIBILITY_PUBLIC})}
}

fun load(c:Context):List<Session> = runCatching{val a=JSONArray(c.getSharedPreferences("db",0).getString("s","[]"));List(a.length()){i->a.getJSONObject(i).let{Session(it.getLong("id"),it.getString("sub"),it.getString("date"),it.getString("start"),it.getString("end"),it.getInt("min"),it.getBoolean("alarm"),it.getInt("lead"),it.getBoolean("done"))}}}.getOrDefault(emptyList())
fun save(c:Context,x:List<Session>){val a=JSONArray();x.forEach{a.put(JSONObject().put("id",it.id).put("sub",it.subject).put("date",it.date).put("start",it.start).put("end",it.end).put("min",it.minutes).put("alarm",it.alarm).put("lead",it.lead).put("done",it.done))};c.getSharedPreferences("db",0).edit().putString("s",a.toString()).apply()}
fun schedule(c:Context,s:Session){if(!s.alarm)return;val am=c.getSystemService(AlarmManager::class.java);if(Build.VERSION.SDK_INT>=31&&!am.canScheduleExactAlarms()){c.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));return};val d=LocalDate.parse(s.date);val t=LocalTime.parse(s.start);val at=LocalDateTime.of(d,t).minusMinutes(s.lead.toLong()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();val i=Intent(c,AlarmReceiver::class.java).putExtra("subject",s.subject).putExtra("id",s.id);val p=PendingIntent.getBroadcast(c,s.id.toInt(),i,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);am.setAlarmClock(AlarmManager.AlarmClockInfo(at,p),p)}

@Composable fun App(c:Context){var tab by remember{mutableIntStateOf(0)};var data by remember{mutableStateOf(load(c))};var add by remember{mutableStateOf(false)};fun upd(v:List<Session>){data=v;save(c,v)}
 MaterialTheme(colorScheme=lightColorScheme(primary=Blue,secondary=Pink,background=Bg)){Scaffold(containerColor=Bg,bottomBar={NavigationBar(containerColor=Color.White){listOf("Hoje" to Icons.Default.Home,"Agenda" to Icons.Default.CalendarMonth,"Cronómetro" to Icons.Default.Timer,"Análise" to Icons.Default.BarChart,"Definições" to Icons.Default.Settings).forEachIndexed{i,p->NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(p.second,p.first)},label={Text(p.first,fontSize=9.sp)})}}},floatingActionButton={if(tab<2)FloatingActionButton({add=true},containerColor=Pink){Icon(Icons.Default.Add,null,tint=Color.White)}}){p->Box(Modifier.padding(p)){when(tab){0->Home(data,upd);1->Agenda(data);2->Timer();3->Analysis(data);else->Settings(c)}}};if(add)NewSession({add=false}){s->upd(data+s);schedule(c,s);add=false}}
}
@Composable fun Hero(title:String,sub:String){Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF1558C9),Color(0xFF7957F4))),RoundedCornerShape(bottomStart=28.dp,bottomEnd=28.dp)).padding(22.dp)){Column{Text(title,Color.White,30.sp,FontWeight.Bold);Text(sub,Color.White,17.sp)}}}
@Composable fun Home(x:List<Session>,upd:(List<Session>)->Unit){val today=LocalDate.now().toString();val list=x.filter{it.date==today};val done=list.filter{it.done}.sumOf{it.minutes};val total=list.sumOf{it.minutes};Column{Hero("Olá!","Vamos estudar hoje? 💪");Card(Modifier.padding(12.dp).fillMaxWidth(),shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(16.dp)){Row(verticalAlignment=Alignment.CenterVertically){ProgressRing(done,total);Column(Modifier.padding(start=18.dp)){Text("Progresso do dia",fontWeight=FontWeight.Bold);Text("Estudado   ${done/60}h ${done%60}min");Text("Meta do dia   ${total/60}h ${total%60}min")}}};};Text("Suas sessões hoje",Modifier.padding(horizontal=18.dp,vertical=8.dp),fontWeight=FontWeight.Bold,fontSize=18.sp);SessionList(list,upd,x)}}
@Composable fun ProgressRing(done:Int,total:Int){val f=if(total==0)0f else done.toFloat()/total;Box(Modifier.size(120.dp),contentAlignment=Alignment.Center){Canvas(Modifier.fillMaxSize()){drawArc(Color(0xFFDCE5EF),-90f,360f,false,style=Stroke(12f,cap=StrokeCap.Round));drawArc(Green,-90f,360*f,false,style=Stroke(12f,cap=StrokeCap.Round))};Text("${done/60}h ${done%60}m",fontWeight=FontWeight.Bold)}}
@Composable fun SessionList(show:List<Session>,upd:(List<Session>)->Unit,all:List<Session>){LazyColumn(Modifier.padding(horizontal=12.dp)){items(show,key={it.id}){s->Card(Modifier.fillMaxWidth().padding(vertical=5.dp),shape=RoundedCornerShape(16.dp)){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.MenuBook,null,tint=Blue,modifier=Modifier.size(38.dp));Column(Modifier.weight(1f).padding(start=10.dp)){Text(s.subject,fontWeight=FontWeight.Bold);Text("${s.start} - ${s.end}")};IconButton({upd(all.map{if(it.id==s.id)it.copy(done=!it.done)else it})}){Icon(if(s.done)Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,null,tint=if(s.done)Green else Blue)};IconButton({upd(all.filterNot{it.id==s.id})}){Icon(Icons.Default.Delete,null,tint=Pink)}}}}}}
@Composable fun Agenda(x:List<Session>){Column{Top("Agenda");Row(Modifier.padding(12.dp).fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){listOf("Dia","Semana","Mês").forEach{FilterChip(it=="Semana",{}, {Text(it)})}};Text("Esta semana",Modifier.padding(16.dp),fontWeight=FontWeight.Bold);LazyColumn(Modifier.padding(horizontal=12.dp)){items(x.sortedBy{it.date+it.start}){s->Card(Modifier.fillMaxWidth().padding(5.dp)){Row(Modifier.padding(14.dp)){Text(s.date.takeLast(5),color=Blue,fontWeight=FontWeight.Bold);Column(Modifier.padding(start=16.dp)){Text(s.subject,fontWeight=FontWeight.Bold);Text("${s.start} - ${s.end}")}}}}}}}
@Composable fun Top(t:String){Box(Modifier.fillMaxWidth().background(Color.White).padding(22.dp)){Text(t,fontSize=26.sp,fontWeight=FontWeight.Bold,color=Navy)}}
@Composable fun NewSession(close:()->Unit,ok:(Session)->Unit){var sub by remember{mutableStateOf("Matemática")};var date by remember{mutableStateOf(LocalDate.now().toString())};var start by remember{mutableStateOf("18:00")};var end by remember{mutableStateOf("19:30")};var lead by remember{mutableStateOf("10")};var active by remember{mutableStateOf(true)};var err by remember{mutableStateOf("")};AlertDialog(onDismissRequest=close,title={Text("Nova sessão")},text={LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){item{OutlinedTextField(sub,{sub=it},label={Text("Disciplina")})};item{OutlinedTextField(date,{date=it},label={Text("Data (AAAA-MM-DD)")})};item{Row{OutlinedTextField(start,{start=it},Modifier.weight(1f),label={Text("Início")});Spacer(Modifier.width(6.dp));OutlinedTextField(end,{end=it},Modifier.weight(1f),label={Text("Fim")})}};item{Row(verticalAlignment=Alignment.CenterVertically){Text("Ativar alarme",Modifier.weight(1f));Switch(active,{active=it})}};item{OutlinedTextField(lead,{lead=it.filter(Char::isDigit)},label={Text("Aviso antecipado (min)")})};item{Text("Som contínuo e vibração até Parar, Estudar ou Adiar.",fontSize=12.sp,color=Color.Gray)};if(err.isNotEmpty())item{Text(err,color=Pink)}}},confirmButton={Button({runCatching{val a=LocalTime.parse(start);val b=LocalTime.parse(end);val d=LocalDate.parse(date);val m=Duration.between(a,b).toMinutes().toInt();require(sub.isNotBlank()&&m>0);ok(Session(subject=sub,date=d.toString(),start=a.toString(),end=b.toString(),minutes=m,alarm=active,lead=lead.toIntOrNull()?:0))}.onFailure{err="Verifique disciplina, data e horários."}}){Text("Guardar")}},dismissButton={TextButton(close){Text("Cancelar")}})}
@Composable fun Timer(){var sec by remember{mutableLongStateOf(0)};var run by remember{mutableStateOf(false)};LaunchedEffect(run){while(run){delay(1000);sec++}};Column{Top("Cronómetro");Box(Modifier.fillMaxWidth().padding(24.dp),contentAlignment=Alignment.Center){Canvas(Modifier.size(245.dp)){drawArc(Color(0xFFE2E8F0),-90f,360f,false,style=Stroke(16f));drawArc(Pink,-90f,(sec%3600)/10f,false,style=Stroke(16f,cap=StrokeCap.Round))};Column(horizontalAlignment=Alignment.CenterHorizontally){Text("%02d:%02d:%02d".format(sec/3600,(sec/60)%60,sec%60),fontSize=40.sp,fontWeight=FontWeight.Bold);Text("A estudar");Text("Matemática",color=Blue)}};Row(Modifier.padding(20.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){Button({run=!run},Modifier.weight(1f),colors=ButtonDefaults.buttonColors(containerColor=Pink)){Icon(if(run)Icons.Default.Pause else Icons.Default.PlayArrow,null);Text(if(run)" Pausar" else " Iniciar")};OutlinedButton({sec=0;run=false},Modifier.weight(1f)){Text("Terminar")}}}}
@Composable fun Analysis(x:List<Session>){val done=x.filter{it.done};val mins=done.sumOf{it.minutes};val g=done.groupBy{it.subject}.mapValues{it.value.sumOf{q->q.minutes}};Column{Top("Análise");Row(Modifier.padding(12.dp)){Stat("Total estudado","${mins/60}h ${mins%60}min",Modifier.weight(1f));Spacer(Modifier.width(10.dp));Stat("Sessões","${done.size}",Modifier.weight(1f))};Text("Horas por disciplina",Modifier.padding(16.dp),fontWeight=FontWeight.Bold,fontSize=18.sp);LazyColumn(Modifier.padding(horizontal=12.dp)){items(g.toList()){p->Card(Modifier.fillMaxWidth().padding(5.dp)){Column(Modifier.padding(14.dp)){Row{Text(p.first,Modifier.weight(1f));Text("${p.second/60}h ${p.second%60}min")};LinearProgressIndicator({if(mins==0)0f else p.second.toFloat()/mins},Modifier.fillMaxWidth().padding(top=8.dp))}}}}}}
@Composable fun Stat(a:String,b:String,m:Modifier){Card(m){Column(Modifier.padding(16.dp)){Text(a,color=Color.Gray);Text(b,fontSize=22.sp,fontWeight=FontWeight.Bold)}}}
@Composable fun Settings(c:Context){Column{Top("Definições");Section("Aparência",listOf("Tema" to "Claro","Cor principal" to "Azul","Idioma" to "Português"));Section("Notificações",listOf("Alarmes de estudo" to "Ativo","Som" to "Ativo","Vibração" to "Ativa"));Section("Sobre",listOf("CronosEstudo" to "v1.0.0"));Button({if(Build.VERSION.SDK_INT>=34)c.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).setData(android.net.Uri.parse("package:${c.packageName}")))},Modifier.padding(16.dp).fillMaxWidth()){Text("Autorizar alarme em ecrã inteiro")}}}
@Composable fun Section(t:String,rows:List<Pair<String,String>>){Text(t,Modifier.padding(start=18.dp,top=14.dp),fontWeight=FontWeight.Bold);Card(Modifier.padding(horizontal=12.dp,vertical=6.dp).fillMaxWidth()){Column{rows.forEach{Row(Modifier.padding(16.dp)){Text(it.first,Modifier.weight(1f));Text(it.second,color=Color.Gray)}}}}}
