package ao.bedi.cronosestudo

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class StudySession(
    val id: Long = System.currentTimeMillis(),
    val subject: String,
    val hour: Int,
    val minute: Int,
    val duration: Int,
    val done: Boolean = false
)

class MainActivity : ComponentActivity() {
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= 33) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { CronosApp(this) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                "study",
                "Lembretes de estudo",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { enableVibration(true) }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val subject = intent.getStringExtra("subject") ?: "Sessão de estudo"
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, "study")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Hora de estudar")
            .setContentText(subject)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(intent.getLongExtra("id", 1L).toInt(), notification)
    }
}

private val Purple = Color(0xFF635BFF)
private val Navy = Color(0xFF111827)
private val Background = Color(0xFFF5F7FB)
private val Green = Color(0xFF10B981)

private fun loadSessions(context: Context): List<StudySession> {
    return runCatching {
        val raw = context.getSharedPreferences("db", Context.MODE_PRIVATE)
            .getString("sessions", "[]") ?: "[]"
        val array = JSONArray(raw)
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            StudySession(
                id = item.getLong("id"),
                subject = item.getString("subject"),
                hour = item.getInt("hour"),
                minute = item.getInt("minute"),
                duration = item.getInt("duration"),
                done = item.getBoolean("done")
            )
        }
    }.getOrDefault(emptyList())
}

private fun saveSessions(context: Context, sessions: List<StudySession>) {
    val array = JSONArray()
    sessions.forEach { session ->
        array.put(
            JSONObject()
                .put("id", session.id)
                .put("subject", session.subject)
                .put("hour", session.hour)
                .put("minute", session.minute)
                .put("duration", session.duration)
                .put("done", session.done)
        )
    }
    context.getSharedPreferences("db", Context.MODE_PRIVATE)
        .edit().putString("sessions", array.toString()).apply()
}

private fun scheduleAlarm(context: Context, session: StudySession) {
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    if (Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return
    }

    var dateTime = LocalDateTime.of(
        LocalDate.now(),
        LocalTime.of(session.hour, session.minute)
    )
    if (dateTime.isBefore(LocalDateTime.now())) dateTime = dateTime.plusDays(1)

    val intent = Intent(context, AlarmReceiver::class.java)
        .putExtra("subject", session.subject)
        .putExtra("id", session.id)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        session.id.toInt(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val trigger = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pendingIntent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CronosApp(context: Context) {
    var tab by remember { mutableIntStateOf(0) }
    var sessions by remember { mutableStateOf(loadSessions(context)) }
    var showDialog by remember { mutableStateOf(false) }

    fun updateSessions(newList: List<StudySession>) {
        sessions = newList
        saveSessions(context, newList)
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Purple,
            secondary = Green,
            background = Background
        )
    ) {
        Scaffold(
            containerColor = Background,
            bottomBar = {
                val destinations = listOf(
                    Triple("Hoje", Icons.Default.Home, 0),
                    Triple("Agenda", Icons.Default.DateRange, 1),
                    Triple("Cronómetro", Icons.Default.Timer, 2),
                    Triple("Análise", Icons.Default.BarChart, 3),
                    Triple("Definições", Icons.Default.Settings, 4)
                )
                NavigationBar {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = tab == destination.third,
                            onClick = { tab = destination.third },
                            icon = { Icon(destination.second, destination.first) },
                            label = { Text(destination.first, fontSize = 9.sp) }
                        )
                    }
                }
            },
            floatingActionButton = {
                if (tab == 0 || tab == 1) {
                    FloatingActionButton(onClick = { showDialog = true }) {
                        Icon(Icons.Default.Add, "Adicionar sessão")
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (tab) {
                    0 -> TodayScreen(sessions, ::updateSessions)
                    1 -> AgendaScreen(sessions, ::updateSessions)
                    2 -> TimerScreen { subject, minutes ->
                        updateSessions(
                            sessions + StudySession(
                                subject = subject,
                                hour = LocalTime.now().hour,
                                minute = LocalTime.now().minute,
                                duration = minutes,
                                done = true
                            )
                        )
                    }
                    3 -> AnalysisScreen(sessions)
                    else -> SettingsScreen()
                }
            }
        }

        if (showDialog) {
            AddSessionDialog(
                onDismiss = { showDialog = false },
                onSave = { session ->
                    val updated = (sessions + session).sortedWith(
                        compareBy<StudySession> { it.hour }.thenBy { it.minute }
                    )
                    updateSessions(updated)
                    scheduleAlarm(context, session)
                    showDialog = false
                }
            )
        }
    }
}

@Composable
private fun AppHeader(title: String, subtitle: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(Navy, Purple)),
                RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
            )
            .padding(24.dp)
    ) {
        Column {
            Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color(0xFFDDE2F1))
        }
    }
}

@Composable
private fun TodayScreen(
    sessions: List<StudySession>,
    update: (List<StudySession>) -> Unit
) {
    val studied = sessions.filter { it.done }.sumOf { it.duration }
    val planned = sessions.sumOf { it.duration }
    Column {
        AppHeader(
            "Olá, Bedi!",
            LocalDate.now().format(
                DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM", Locale("pt", "PT"))
            )
        )
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("Progresso de hoje", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { if (planned == 0) 0f else studied.toFloat() / planned },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "$studied de $planned minutos concluídos",
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        SessionList(sessions, update)
    }
}

@Composable
private fun AgendaScreen(
    sessions: List<StudySession>,
    update: (List<StudySession>) -> Unit
) {
    Column {
        AppHeader("Agenda", "Organize as suas sessões de estudo")
        SessionList(sessions, update)
    }
}

@Composable
private fun SessionList(
    sessions: List<StudySession>,
    update: (List<StudySession>) -> Unit
) {
    if (sessions.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nenhuma sessão planeada.")
        }
        return
    }

    LazyColumn(Modifier.padding(horizontal = 16.dp)) {
        items(sessions, key = { it.id }) { session ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(width = 8.dp, height = 48.dp)
                            .background(if (session.done) Green else Purple, RoundedCornerShape(8.dp))
                    )
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(session.subject, fontWeight = FontWeight.Bold)
                        Text(
                            "%02d:%02d • %d min".format(
                                session.hour,
                                session.minute,
                                session.duration
                            ),
                            color = Color.Gray
                        )
                    }
                    IconButton(onClick = {
                        update(sessions.map {
                            if (it.id == session.id) it.copy(done = !it.done) else it
                        })
                    }) {
                        Icon(
                            if (session.done) Icons.Default.CheckCircle
                            else Icons.Default.RadioButtonUnchecked,
                            "Concluir",
                            tint = if (session.done) Green else Color.Gray
                        )
                    }
                    IconButton(onClick = {
                        update(sessions.filterNot { it.id == session.id })
                    }) {
                        Icon(Icons.Default.Delete, "Eliminar", tint = Color(0xFFEF476F))
                    }
                }
            }
        }
    }
}

@Composable
private fun AddSessionDialog(
    onDismiss: () -> Unit,
    onSave: (StudySession) -> Unit
) {
    var subject by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("18:00") }
    var duration by remember { mutableStateOf("60") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova sessão") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Disciplina") }
                )
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("Horário (HH:mm)") }
                )
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it.filter(Char::isDigit) },
                    label = { Text("Duração em minutos") }
                )
                if (error.isNotEmpty()) Text(error, color = Color(0xFFB91C1C))
            }
        },
        confirmButton = {
            Button(onClick = {
                val parts = time.split(":")
                val hour = parts.getOrNull(0)?.toIntOrNull()
                val minute = parts.getOrNull(1)?.toIntOrNull()
                val minutes = duration.toIntOrNull()
                if (subject.isBlank() || hour !in 0..23 || minute !in 0..59 || minutes == null) {
                    error = "Preencha os dados corretamente."
                } else {
                    onSave(
                        StudySession(
                            subject = subject.trim(),
                            hour = hour!!,
                            minute = minute!!,
                            duration = minutes.coerceIn(1, 720)
                        )
                    )
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun TimerScreen(onFinish: (String, Int) -> Unit) {
    var running by remember { mutableStateOf(false) }
    var seconds by remember { mutableLongStateOf(0L) }
    var subject by remember { mutableStateOf("Estudo livre") }

    LaunchedEffect(running) {
        while (running) {
            delay(1000)
            seconds++
        }
    }

    Column {
        AppHeader("Cronómetro", "Foco sem distrações")
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("Disciplina") }
            )
            Text(
                "%02d:%02d:%02d".format(
                    seconds / 3600,
                    (seconds / 60) % 60,
                    seconds % 60
                ),
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(28.dp)
            )
            Button(
                onClick = { running = !running },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Text(if (running) " Pausar" else " Iniciar")
            }
            OutlinedButton(
                onClick = {
                    if (seconds > 0) {
                        onFinish(subject.ifBlank { "Estudo livre" }, (seconds / 60).toInt().coerceAtLeast(1))
                        seconds = 0
                        running = false
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            ) { Text("Terminar e guardar") }
        }
    }
}

@Composable
private fun AnalysisScreen(sessions: List<StudySession>) {
    val completed = sessions.filter { it.done }
    val totalMinutes = completed.sumOf { it.duration }
    val bySubject = completed.groupBy { it.subject }
        .mapValues { entry -> entry.value.sumOf { it.duration } }
        .toList().sortedByDescending { it.second }

    Column {
        AppHeader("Análise", "Acompanhe a sua evolução")
        LazyColumn(Modifier.padding(16.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("Tempo estudado", "${totalMinutes / 60}h ${totalMinutes % 60}m", Modifier.weight(1f))
                    StatCard(
                        "Conclusão",
                        "${if (sessions.isEmpty()) 0 else completed.size * 100 / sessions.size}%",
                        Modifier.weight(1f)
                    )
                }
                Text(
                    "Horas por disciplina",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 18.dp)
                )
            }
            items(bySubject) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(Modifier.padding(16.dp)) {
                        Text(item.first, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text("${item.second / 60}h ${item.second % 60}m")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Color.Gray)
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SettingsScreen() {
    Column {
        AppHeader("Definições", "Personalize a sua experiência")
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("CronosEstudo", fontWeight = FontWeight.Bold)
                Text("Versão 0.1.1")
                Text(
                    "Os dados ficam guardados offline no dispositivo.",
                    modifier = Modifier.padding(top = 12.dp),
                    color = Color.Gray
                )
            }
        }
    }
}
