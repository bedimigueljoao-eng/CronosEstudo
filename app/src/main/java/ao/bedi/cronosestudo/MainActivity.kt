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
import androidx.compose.foundation.clickable
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

data class Discipline(
    val id: String,
    val name: String,
    val colorIndex: Int = 0,
    val archived: Boolean = false
)

data class StudyLog(
    val id: Long = System.currentTimeMillis(),
    val disciplineName: String,
    val slotId: Long?,
    val date: String,
    val status: String,
    val minutesStudied: Int
)

// Cada StudySession representa um ScheduleSlot independente.
// O ID identifica somente uma programação, nunca a disciplina inteira.
data class StudySession(
    val id: Long = System.currentTimeMillis(),
    val subject: String,
    val start: String,
    val end: String,
    val weekdays: List<Int>,
    val alarm: Boolean = true,
    val leadMinutes: Int = 10,
    val studiedMinutes: Int = 0,
    val status: String = "PENDENTE"
)

private val Blue = Color(0xFF1684EA)
private val Navy = Color(0xFF10233F)
private val Bg = Color(0xFFF4F7FC)
private val Green = Color(0xFF10B981)
private val Pink = Color(0xFFFF426D)
private val Purple = Color(0xFF7C5CE7)
private val Orange = Color(0xFFFF8A3D)
private val Yellow = Color(0xFFF9C21A)
private val subjects = emptyList<String>()
private val subjectColors = listOf(Blue, Green, Orange, Pink, Purple, Yellow)
private val dayNames = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")

class MainActivity : ComponentActivity() {
    private val askNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createAlarmChannel()
        if (Build.VERSION.SDK_INT >= 33) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { CronosApp(this) }
    }

    private fun createAlarmChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                "alarm",
                "Alarmes de estudo",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}

private fun loadSessions(context: Context): List<StudySession> = runCatching {
    val raw = context.getSharedPreferences("db_v11", Context.MODE_PRIVATE)
        .getString("sessions", "[]") ?: "[]"
    val array = JSONArray(raw)
    List(array.length()) { index ->
        val item = array.getJSONObject(index)
        val daysArray = item.getJSONArray("days")
        StudySession(
            id = item.getLong("id"),
            subject = item.getString("subject"),
            start = item.getString("start"),
            end = item.getString("end"),
            weekdays = List(daysArray.length()) { daysArray.getInt(it) },
            alarm = item.getBoolean("alarm"),
            leadMinutes = item.getInt("lead"),
            studiedMinutes = item.getInt("studied"),
            status = item.getString("status")
        )
    }
}.getOrDefault(emptyList())

private fun saveSessions(context: Context, sessions: List<StudySession>) {
    val array = JSONArray()
    sessions.forEach { session ->
        val days = JSONArray()
        session.weekdays.forEach { days.put(it) }
        array.put(
            JSONObject()
                .put("id", session.id)
                .put("subject", session.subject)
                .put("start", session.start)
                .put("end", session.end)
                .put("days", days)
                .put("alarm", session.alarm)
                .put("lead", session.leadMinutes)
                .put("studied", session.studiedMinutes)
                .put("status", session.status)
        )
    }
    context.getSharedPreferences("db_v11", Context.MODE_PRIVATE)
        .edit().putString("sessions", array.toString()).apply()
}

private fun nextOccurrence(session: StudySession): ZonedDateTime? {
    if (session.weekdays.isEmpty()) return null
    val now = ZonedDateTime.now()
    val time = LocalTime.parse(session.start)
    for (offset in 0..7) {
        val date = now.toLocalDate().plusDays(offset.toLong())
        val isoDay = date.dayOfWeek.value
        val candidate = ZonedDateTime.of(date, time, now.zone)
            .minusMinutes(session.leadMinutes.toLong())
        if (isoDay in session.weekdays && candidate.isAfter(now)) return candidate
    }
    return null
}

private fun scheduleAlarm(context: Context, session: StudySession) {
    val settings = context.getSharedPreferences("settings_v14", Context.MODE_PRIVATE)
    if (!session.alarm || !settings.getBoolean("alarms", true)) return
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    if (Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return
    }
    val occurrence = nextOccurrence(session) ?: return
    val intent = Intent(context, AlarmReceiver::class.java)
        .putExtra("subject", session.subject)
    val pending = PendingIntent.getBroadcast(
        context,
        session.id.toInt(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    alarmManager.setAlarmClock(
        AlarmManager.AlarmClockInfo(occurrence.toInstant().toEpochMilli(), pending),
        pending
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CronosApp(context: Context) {
    var tab by remember { mutableIntStateOf(0) }
    var sessions by remember { mutableStateOf(loadSessions(context)) }
    var showNew by remember { mutableStateOf(false) }
    var selectedSession by remember { mutableStateOf<StudySession?>(null) }
    var timerSubject by remember { mutableStateOf("") }
    var timerRequested by remember { mutableStateOf(false) }

    fun update(newData: List<StudySession>) {
        sessions = newData
        saveSessions(context, newData)
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Blue,
            secondary = Pink,
            background = Bg
        )
    ) {
        Scaffold(
            containerColor = Bg,
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    val destinations = listOf(
                        Triple("Hoje", Icons.Default.Home, 0),
                        Triple("Agenda", Icons.Default.CalendarMonth, 1),
                        Triple("Cronómetro", Icons.Default.Timer, 2),
                        Triple("Análise", Icons.Default.BarChart, 3),
                        Triple("Definições", Icons.Default.Settings, 4)
                    )
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
                    FloatingActionButton(
                        onClick = { showNew = true },
                        containerColor = Pink
                    ) {
                        Icon(Icons.Default.Add, "Adicionar sessão", tint = Color.White)
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (tab) {
                    0 -> HomeScreen(
                        sessions = sessions,
                        onSessionClick = { selectedSession = it },
                        onStart = { session ->
                            timerSubject = session.subject
                            timerRequested = true
                            tab = 2
                        }
                    )
                    1 -> AgendaScreen(sessions) { selectedSession = it }
                    2 -> TimerScreen(
                        subjects = sessions.map { it.subject }.filter { it.isNotBlank() }.distinct(),
                        initialSubject = timerSubject,
                        autoStart = timerRequested,
                        onAutoStartConsumed = { timerRequested = false },
                        onFinished = { subject, minutes ->
                            val match = sessions.lastOrNull { it.subject == subject }
                            if (match != null) {
                                update(sessions.map {
                                    if (it.id == match.id) it.copy(
                                        studiedMinutes = it.studiedMinutes + minutes,
                                        status = "ESTUDADO"
                                    ) else it
                                })
                            } else {
                                update(sessions + StudySession(
                                    subject = subject,
                                    start = LocalTime.now().withSecond(0).withNano(0).toString(),
                                    end = LocalTime.now().plusMinutes(minutes.toLong()).withSecond(0).withNano(0).toString(),
                                    weekdays = listOf(LocalDate.now().dayOfWeek.value),
                                    alarm = false,
                                    studiedMinutes = minutes,
                                    status = "ESTUDADO"
                                ))
                            }
                        }
                    )
                    3 -> AnalysisScreen(sessions)
                    else -> SettingsScreen(context)
                }
            }
        }

        if (showNew) {
            NewSessionDialog(
                onDismiss = { showNew = false },
                onSave = { newSessions ->
                    update(sessions + newSessions)
                    newSessions.forEach { scheduleAlarm(context, it) }
                    showNew = false
                }
            )
        }

        selectedSession?.let { session ->
            SessionActionsDialog(
                session = session,
                onDismiss = { selectedSession = null },
                onStartStudy = {
                    timerSubject = session.subject
                    timerRequested = true
                    selectedSession = null
                    tab = 2
                },
                onStudied = { minutes ->
                    update(sessions.map {
                        if (it.id == session.id) it.copy(
                            studiedMinutes = it.studiedMinutes + minutes,
                            status = "ESTUDADO"
                        ) else it
                    })
                    selectedSession = null
                },
                onAddSchedule = { day, start, end ->
                    val added = StudySession(
                        subject = session.subject,
                        start = start,
                        end = end,
                        weekdays = listOf(day),
                        alarm = session.alarm,
                        leadMinutes = session.leadMinutes
                    )
                    update(sessions + added)
                    scheduleAlarm(context, added)
                    selectedSession = null
                },
                onEdit = { day, start, end ->
                    val changed = session.copy(weekdays = listOf(day), start = start, end = end)
                    update(sessions.map { if (it.id == session.id) changed else it })
                    scheduleAlarm(context, changed)
                    selectedSession = null
                },
                onPostpone = {
                    val now = LocalTime.parse(session.start).plusMinutes(30)
                    val end = LocalTime.parse(session.end).plusMinutes(30)
                    val changed = session.copy(start = now.toString(), end = end.toString())
                    update(sessions.map { if (it.id == session.id) changed else it })
                    scheduleAlarm(context, changed)
                    selectedSession = null
                },
                onDelete = {
                    val remaining = sessions.filterNot { it.id == session.id }
                    val history = if (session.studiedMinutes > 0) {
                        listOf(session.copy(id = System.currentTimeMillis(), weekdays = emptyList(), alarm = false, status = "HISTORICO"))
                    } else emptyList()
                    update(remaining + history)
                    selectedSession = null
                }
            )
        }
    }
}

@Composable
private fun GradientHeader() {
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(Color(0xFF1558C9), Color(0xFF8558EF))),
                RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
            )
            .padding(22.dp)
    ) {
        Column {
            Text("Olá!", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("Vamos estudar hoje? 💪", color = Color.White, fontSize = 18.sp)
            Text(
                LocalDate.now().format(
                    DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM", Locale("pt", "PT"))
                ),
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                "⚙ Disciplina hoje, resultados amanhã",
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun HomeScreen(
    sessions: List<StudySession>,
    onSessionClick: (StudySession) -> Unit,
    onStart: (StudySession) -> Unit
) {
    val today = LocalDate.now().dayOfWeek.value
    val todaySessions = sessions.filter { today in it.weekdays }.sortedBy { it.start }
    val studied = todaySessions.sumOf { it.studiedMinutes }
    val planned = todaySessions.sumOf { durationMinutes(it.start, it.end) }
    val next = todaySessions.firstOrNull {
        runCatching { LocalTime.parse(it.end).isAfter(LocalTime.now()) }.getOrDefault(false)
    }

    LazyColumn {
        item { GradientHeader() }
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row {
                        Text("Progresso do dia", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("${if (planned == 0) 0 else studied * 100 / planned}%", fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressRing(studied, planned)
                        Column(Modifier.padding(start = 16.dp)) {
                            LegendRow(Green, "Estudado", minutesLabel(studied))
                            LegendRow(Color(0xFFCBD5E1), "Restante", minutesLabel((planned - studied).coerceAtLeast(0)))
                            LegendRow(Color(0xFFE2E8F0), "Meta do dia", minutesLabel(planned))
                        }
                    }
                }
            }
        }
        if (next != null) {
            item {
                Text("Próxima sessão", Modifier.padding(horizontal = 18.dp, vertical = 8.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SubjectIcon(next.subject)
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(next.subject, fontWeight = FontWeight.Bold)
                                Text("${next.start} - ${next.end} (${minutesLabel(durationMinutes(next.start, next.end))})")
                            }
                        }
                        Button(
                            onClick = { onStart(next) },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        ) {
                            Icon(Icons.Default.PlayCircle, null)
                            Text("  Iniciar")
                        }
                    }
                }
            }
        }
        item {
            Text("Suas sessões hoje", Modifier.padding(horizontal = 18.dp, vertical = 12.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        items(todaySessions, key = { it.id }) { session ->
            SessionCard(session) { onSessionClick(session) }
        }
        item { Spacer(Modifier.height(90.dp)) }
    }
}

@Composable
private fun ProgressRing(studied: Int, planned: Int) {
    val progress = if (planned <= 0) 0f else (studied.toFloat() / planned).coerceIn(0f, 1f)
    Box(Modifier.size(130.dp).padding(8.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawArc(Color(0xFFE2E8F0), -90f, 360f, false, style = Stroke(14f, cap = StrokeCap.Round))
            drawArc(Green, -90f, 360f * progress, false, style = Stroke(14f, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(minutesLabel(studied), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("de ${minutesLabel(planned)}", fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun LegendRow(color: Color, title: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(title, Modifier.padding(start = 8.dp).weight(1f), fontSize = 12.sp)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SubjectIcon(subject: String) {
    val index = subjects.indexOf(subject).let { if (it < 0) 0 else it }
    Surface(shape = RoundedCornerShape(12.dp), color = subjectColors[index].copy(alpha = 0.16f)) {
        Icon(Icons.Default.MenuBook, null, tint = subjectColors[index], modifier = Modifier.padding(12.dp).size(26.dp))
    }
}

@Composable
private fun SessionCard(session: StudySession, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            SubjectIcon(session.subject)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text("${session.start} - ${session.end}", fontWeight = FontWeight.SemiBold)
                Text(session.subject, fontWeight = FontWeight.Bold)
                if (session.studiedMinutes > 0) Text("Estudado: ${minutesLabel(session.studiedMinutes)}", color = Green, fontSize = 12.sp)
            }
            Icon(
                if (session.status == "ESTUDADO") Icons.Default.CheckCircle else Icons.Default.ChevronRight,
                null,
                tint = if (session.status == "ESTUDADO") Green else Blue
            )
        }
    }
}

@Composable
private fun PageTitle(title: String) {
    Row(Modifier.fillMaxWidth().background(Color.White).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Navy, modifier = Modifier.weight(1f))
        Icon(Icons.Default.Settings, null, tint = Navy)
    }
}

@Composable
private fun AgendaScreen(sessions: List<StudySession>, onClick: (StudySession) -> Unit) {
    var mode by remember { mutableStateOf("Semana") }
    var weekOffset by remember { mutableIntStateOf(0) }
    val monday = LocalDate.now().minusDays((LocalDate.now().dayOfWeek.value - 1).toLong()).plusWeeks(weekOffset.toLong())
    val sunday = monday.plusDays(6)
    val dateLabel = "${monday.dayOfMonth} – ${sunday.dayOfMonth} de ${sunday.month.getDisplayName(java.time.format.TextStyle.FULL, Locale("pt", "PT"))} de ${sunday.year}"

    Column(Modifier.fillMaxSize().background(Bg)) {
        PageTitle("Agenda")
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Dia", "Semana", "Mês").forEach { item ->
                Button(
                    onClick = { mode = item },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (mode == item) Blue else Color(0xFFE9EEF6),
                        contentColor = if (mode == item) Color.White else Navy
                    ),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) { Text(item, fontSize = 12.sp) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { weekOffset-- }) { Icon(Icons.Default.ChevronLeft, "Semana anterior") }
            Text(dateLabel, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            IconButton(onClick = { weekOffset++ }) { Icon(Icons.Default.ChevronRight, "Semana seguinte") }
        }
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 38.dp)) {
                    (1..7).forEach { day ->
                        val date = monday.plusDays((day - 1).toLong())
                        Column(Modifier.weight(1f).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(dayNames[day - 1], fontSize = 10.sp, color = Color.Gray)
                            Text(date.dayOfMonth.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().height(390.dp)) {
                    Column(Modifier.width(38.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                        listOf("08:00", "10:00", "12:00", "14:00", "16:00", "18:00", "20:00").forEach {
                            Text(it, fontSize = 9.sp, color = Color.Gray)
                        }
                    }
                    Row(Modifier.weight(1f).fillMaxHeight()) {
                        (1..7).forEach { day ->
                            val daySessions = sessions.filter { day in it.weekdays }.sortedBy { it.start }
                            Box(
                                Modifier.weight(1f).fillMaxHeight()
                                    .background(if (day == LocalDate.now().dayOfWeek.value) Blue.copy(alpha = 0.05f) else Color.Transparent)
                            ) {
                                Canvas(Modifier.fillMaxSize()) {
                                    repeat(7) { index ->
                                        val y = size.height * index / 6f
                                        drawLine(Color(0xFFE7ECF3), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1f)
                                    }
                                    drawLine(Color(0xFFE7ECF3), androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(0f, size.height), 1f)
                                }
                                daySessions.forEachIndexed { index, session ->
                                    val startHour = runCatching { LocalTime.parse(session.start).hour + LocalTime.parse(session.start).minute / 60f }.getOrDefault(8f)
                                    val minutes = durationMinutes(session.start, session.end)
                                    val top = (((startHour - 8f) / 12f) * 390f).coerceIn(0f, 360f)
                                    val height = ((minutes / 720f) * 390f).coerceAtLeast(34f)
                                    val colorIndex = kotlin.math.abs(session.subject.hashCode()) % subjectColors.size
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)
                                            .offset(y = top.dp).height(height.dp)
                                            .clickable { onClick(session) },
                                        colors = CardDefaults.cardColors(containerColor = subjectColors[colorIndex].copy(alpha = 0.70f)),
                                        shape = RoundedCornerShape(5.dp)
                                    ) {
                                        Text(session.subject, fontSize = 9.sp, modifier = Modifier.padding(4.dp), color = Navy, maxLines = 2)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Text("Disciplinas", Modifier.padding(start = 18.dp, top = 12.dp), fontWeight = FontWeight.Bold)
        val unique = sessions.map { it.subject }.distinct()
        LazyColumn(Modifier.padding(horizontal = 16.dp)) {
            items(unique.chunked(2)) { pair ->
                Row(Modifier.fillMaxWidth()) {
                    pair.forEach { name ->
                        val colorIndex = kotlin.math.abs(name.hashCode()) % subjectColors.size
                        Row(Modifier.weight(1f).padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).background(subjectColors[colorIndex], CircleShape))
                            Text(name, Modifier.padding(start = 8.dp), fontSize = 12.sp)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun NewSessionDialog(onDismiss: () -> Unit, onSave: (List<StudySession>) -> Unit) {
    data class ScheduleDraft(val day: Int, val start: String, val end: String)
    var subject by remember { mutableStateOf("") }
    var day by remember { mutableIntStateOf(LocalDate.now().dayOfWeek.value) }
    var startTime by remember { mutableStateOf("18:00") }
    var endTime by remember { mutableStateOf("19:00") }
    var schedules by remember { mutableStateOf(emptyList<ScheduleDraft>()) }
    var alarm by remember { mutableStateOf(true) }
    var lead by remember { mutableStateOf("10") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova disciplina e horários", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nome da disciplina") },
                        placeholder = { Text("Ex.: Contabilidade") }
                    )
                }
                item { Text("Adicionar horário", fontWeight = FontWeight.SemiBold) }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        (1..7).forEach { option ->
                            FilterChip(
                                selected = day == option,
                                onClick = { day = option },
                                label = { Text(dayNames[option - 1].take(1)) }
                            )
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(startTime, { startTime = it }, Modifier.weight(1f), label = { Text("Início") })
                        OutlinedTextField(endTime, { endTime = it }, Modifier.weight(1f), label = { Text("Fim") })
                    }
                }
                item {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                val a = LocalTime.parse(startTime)
                                val b = LocalTime.parse(endTime)
                                require(b.isAfter(a))
                                schedules = schedules + ScheduleDraft(day, a.toString(), b.toString())
                                error = ""
                            }.onFailure { error = "Horário inválido." }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("+ Adicionar este horário") }
                }
                items(schedules) { schedule ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F5FC))) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${dayNames[schedule.day - 1]}  ${schedule.start} - ${schedule.end}", Modifier.weight(1f))
                            IconButton(onClick = { schedules = schedules - schedule }) {
                                Icon(Icons.Default.Delete, "Remover", tint = Pink)
                            }
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, null)
                        Text("Ativar alarme", Modifier.padding(start = 8.dp).weight(1f))
                        Switch(alarm, { alarm = it })
                    }
                }
                item {
                    OutlinedTextField(
                        value = lead,
                        onValueChange = { lead = it.filter(Char::isDigit) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Aviso antecipado em minutos") }
                    )
                }
                if (error.isNotEmpty()) item { Text(error, color = Pink) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (subject.isBlank() || schedules.isEmpty()) {
                    error = "Indique a disciplina e adicione pelo menos um horário."
                } else {
                    val reminder = lead.toIntOrNull()?.coerceIn(0, 120) ?: 10
                    onSave(schedules.map { schedule ->
                        StudySession(
                            subject = subject.trim(),
                            start = schedule.start,
                            end = schedule.end,
                            weekdays = listOf(schedule.day),
                            alarm = alarm,
                            leadMinutes = reminder
                        )
                    })
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun SessionActionsDialog(
    session: StudySession,
    onDismiss: () -> Unit,
    onStartStudy: () -> Unit,
    onStudied: (Int) -> Unit,
    onAddSchedule: (Int, String, String) -> Unit,
    onEdit: (Int, String, String) -> Unit,
    onPostpone: () -> Unit,
    onDelete: () -> Unit
) {
    var view by remember { mutableStateOf("MENU") }
    var hours by remember { mutableStateOf("0") }
    var minutes by remember { mutableStateOf("30") }
    var day by remember { mutableIntStateOf(session.weekdays.firstOrNull() ?: 1) }
    var startTime by remember { mutableStateOf(session.start) }
    var endTime by remember { mutableStateOf(session.end) }
    var error by remember { mutableStateOf("") }

    fun validateAndRun(action: (Int, String, String) -> Unit) {
        runCatching {
            val start = LocalTime.parse(startTime)
            val end = LocalTime.parse(endTime)
            require(end.isAfter(start))
            action(day, start.toString(), end.toString())
        }.onFailure { error = "Verifique o dia e os horários." }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(session.subject, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Programação selecionada: ${dayNames[(session.weekdays.firstOrNull() ?: 1) - 1]} • ${session.start} - ${session.end}")
                when (view) {
                    "MENU" -> {
                        Button(onClick = onStartStudy, modifier = Modifier.fillMaxWidth()) { Text("Iniciar estudo") }
                        OutlinedButton(onClick = { view = "STUDIED" }, modifier = Modifier.fillMaxWidth()) { Text("Marcar como estudada") }
                        OutlinedButton(onClick = { view = "ADD"; day = 1 }, modifier = Modifier.fillMaxWidth()) { Text("+ Adicionar outro dia/horário") }
                        OutlinedButton(onClick = { view = "EDIT" }, modifier = Modifier.fillMaxWidth()) { Text("Editar esta programação") }
                        OutlinedButton(onClick = onPostpone, modifier = Modifier.fillMaxWidth()) { Text("Adiar esta programação 30 min") }
                        TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Eliminar apenas esta programação", color = Pink) }
                    }
                    "STUDIED" -> {
                        Text("Quanto tempo estudou?", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(hours, { hours = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Horas") })
                            OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Minutos") })
                        }
                        Button(onClick = {
                            val total = (hours.toIntOrNull() ?: 0) * 60 + (minutes.toIntOrNull() ?: 0)
                            if (total > 0) onStudied(total)
                        }, modifier = Modifier.fillMaxWidth()) { Text("Guardar tempo estudado") }
                    }
                    "ADD", "EDIT" -> {
                        Text(if (view == "ADD") "Adicionar outro dia e horário" else "Editar somente a programação selecionada", fontWeight = FontWeight.SemiBold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            (1..7).forEach { option ->
                                FilterChip(
                                    selected = day == option,
                                    onClick = { day = option },
                                    label = { Text(dayNames[option - 1].take(1)) }
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(startTime, { startTime = it }, Modifier.weight(1f), label = { Text("Início") })
                            OutlinedTextField(endTime, { endTime = it }, Modifier.weight(1f), label = { Text("Fim") })
                        }
                        if (error.isNotEmpty()) Text(error, color = Pink)
                        Button(
                            onClick = { validateAndRun(if (view == "ADD") onAddSchedule else onEdit) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (view == "ADD") "Guardar novo dia/horário" else "Guardar alterações") }
                        if (view == "ADD") {
                            Text("A programação selecionada será mantida. Este dia e horário serão adicionados como uma nova programação independente da mesma disciplina.", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }
        },
        confirmButton = { },
        dismissButton = {
            TextButton(onClick = { if (view == "MENU") onDismiss() else view = "MENU" }) {
                Text(if (view == "MENU") "Fechar" else "Voltar")
            }
        }
    )
}

@Composable
private fun TimerScreen(
    subjects: List<String>,
    initialSubject: String,
    autoStart: Boolean,
    onAutoStartConsumed: () -> Unit,
    onFinished: (String, Int) -> Unit
) {
    var subject by remember(initialSubject) { mutableStateOf(initialSubject) }
    var expanded by remember { mutableStateOf(false) }
    var seconds by remember { mutableLongStateOf(0L) }
    var running by remember { mutableStateOf(false) }

    LaunchedEffect(autoStart) {
        if (autoStart) {
            running = true
            onAutoStartConsumed()
        }
    }
    LaunchedEffect(running) {
        while (running) {
            delay(1000)
            seconds++
        }
    }

    Column {
        PageTitle("Cronómetro")
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Disciplina a estudar") },
                placeholder = { Text("Escreva ou selecione") }
            )
            if (subjects.isNotEmpty()) {
                Box {
                    TextButton(onClick = { expanded = true }) { Text("Selecionar disciplina existente") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        subjects.forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = { subject = option; expanded = false })
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(245.dp)) {
                drawArc(Color(0xFFE2E8F0), -90f, 360f, false, style = Stroke(16f))
                drawArc(Pink, -90f, ((seconds % 3600) / 10f), false, style = Stroke(16f, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%02d:%02d:%02d".format(seconds / 3600, (seconds / 60) % 60, seconds % 60), fontSize = 40.sp, fontWeight = FontWeight.Bold)
                Text(if (running) "A estudar" else "Pronto para estudar")
                Text(subject, color = Blue, fontWeight = FontWeight.SemiBold)
            }
        }
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { running = !running },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Pink)
            ) {
                Icon(if (running) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                Text(if (running) " Pausar" else " Iniciar")
            }
            OutlinedButton(
                onClick = {
                    if (seconds > 0) {
                        onFinished(subject, (seconds / 60).toInt().coerceAtLeast(1))
                        seconds = 0
                        running = false
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("Terminar") }
        }
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { seconds += 300 }, modifier = Modifier.weight(1f)) { Text("Adicionar 5 min") }
            OutlinedButton(onClick = { seconds = 0; running = false }, modifier = Modifier.weight(1f)) { Text("Cancelar") }
        }
        Text(
            "“Pequenos esforços diários produzem grandes resultados.”",
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            color = Navy,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun AnalysisScreen(sessions: List<StudySession>) {
    var mode by remember { mutableStateOf("Semana") }
    val total = sessions.sumOf { it.studiedMinutes }
    val grouped = sessions.groupBy { it.subject }
        .mapValues { entry -> entry.value.sumOf { it.studiedMinutes } }
        .filterValues { it > 0 }
        .toList().sortedByDescending { it.second }
    val byDay = (1..7).map { day -> sessions.filter { day in it.weekdays }.sumOf { it.studiedMinutes } }
    val maxDay = (byDay.maxOrNull() ?: 1).coerceAtLeast(1)

    LazyColumn(Modifier.fillMaxSize().background(Bg)) {
        item { PageTitle("Análise") }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Dia", "Semana", "Mês").forEach { item ->
                    Button(
                        onClick = { mode = item },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (mode == item) Blue else Color(0xFFE9EEF6),
                            contentColor = if (mode == item) Color.White else Navy
                        ),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) { Text(item, fontSize = 12.sp) }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ChevronLeft, null)
                Text("Semana atual", Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.ChevronRight, null)
            }
        }
        item {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Total estudado", minutesLabel(total), Modifier.weight(1f))
                StatCard("Média diária", minutesLabel(total / 7), Modifier.weight(1f))
            }
        }
        item { Text("Horas por disciplina", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontWeight = FontWeight.Bold) }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            if (total == 0) {
                                drawArc(Color(0xFFE2E8F0), -90f, 360f, false, style = Stroke(28f))
                            } else {
                                var angle = -90f
                                grouped.forEachIndexed { index, item ->
                                    val sweep = item.second * 360f / total
                                    drawArc(subjectColors[index % subjectColors.size], angle, sweep, false, style = Stroke(28f))
                                    angle += sweep
                                }
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(minutesLabel(total), fontWeight = FontWeight.Bold)
                            Text("total", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        grouped.forEachIndexed { index, item ->
                            val percent = if (total == 0) 0 else item.second * 100 / total
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).background(subjectColors[index % subjectColors.size], CircleShape))
                                Text(item.first, Modifier.padding(start = 6.dp).weight(1f), fontSize = 11.sp)
                                Text("${minutesLabel(item.second)} ($percent%)", fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
        item { Text("Evolução da semana", Modifier.padding(horizontal = 16.dp, vertical = 14.dp), fontWeight = FontWeight.Bold) }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.height(190.dp).padding(16.dp), verticalAlignment = Alignment.Bottom) {
                    byDay.forEachIndexed { index, minutes ->
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            Box(
                                Modifier.width(22.dp)
                                    .fillMaxHeight((minutes.toFloat() / maxDay).coerceIn(0.04f, 1f))
                                    .background(Blue.copy(alpha = 0.85f), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            )
                            Text(dayNames[index], fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(90.dp)) }
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
private fun SettingsScreen(context: Context) {
    val prefs = context.getSharedPreferences("settings_v14", Context.MODE_PRIVATE)
    var alarms by remember { mutableStateOf(prefs.getBoolean("alarms", true)) }
    var sound by remember { mutableStateOf(prefs.getBoolean("sound", true)) }
    var vibration by remember { mutableStateOf(prefs.getBoolean("vibration", true)) }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
    var message by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize().background(Bg)) {
        item { PageTitle("Definições") }
        item {
            Text("Aparência", Modifier.padding(start = 18.dp, top = 12.dp), fontWeight = FontWeight.Bold)
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                SettingSwitch("Tema escuro", dark) { dark = it; prefs.edit().putBoolean("dark", it).apply() }
                SettingValue("Cor principal", "Azul")
                SettingValue("Idioma", "Português")
            }
        }
        item {
            Text("Notificações", Modifier.padding(start = 18.dp, top = 8.dp), fontWeight = FontWeight.Bold)
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                SettingSwitch("Alarmes de estudo", alarms) { alarms = it; prefs.edit().putBoolean("alarms", it).apply() }
                SettingSwitch("Som", sound) { sound = it; prefs.edit().putBoolean("sound", it).apply() }
                SettingSwitch("Vibração", vibration) { vibration = it; prefs.edit().putBoolean("vibration", it).apply() }
                TextButton(onClick = {
                    if (Build.VERSION.SDK_INT >= 34) context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).setData(android.net.Uri.parse("package:${context.packageName}")))
                    else message = "O alarme em ecrã inteiro já é gerido pelo sistema."
                }, modifier = Modifier.fillMaxWidth()) { Text("Autorizar alarme em ecrã inteiro") }
            }
        }
        item {
            Text("Dados", Modifier.padding(start = 18.dp, top = 8.dp), fontWeight = FontWeight.Bold)
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                TextButton(onClick = { message = "Cópia local preparada. Os dados permanecem no dispositivo." }, modifier = Modifier.fillMaxWidth()) { Text("Criar cópia de segurança") }
                TextButton(onClick = { message = "Exportação registada para uma próxima integração de ficheiros." }, modifier = Modifier.fillMaxWidth()) { Text("Exportar dados") }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                SettingValue("CronosEstudo", "v1.4.0")
            }
        }
        if (message.isNotEmpty()) item { Text(message, Modifier.padding(16.dp), color = Blue) }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(label, Modifier.weight(1f))
        Text(value, color = Color.Gray)
    }
}

private fun durationMinutes(start: String, end: String): Int = runCatching {
    Duration.between(LocalTime.parse(start), LocalTime.parse(end)).toMinutes().toInt().coerceAtLeast(0)
}.getOrDefault(0)

private fun minutesLabel(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return if (hours > 0) "${hours}h ${mins}min" else "${mins}min"
}
