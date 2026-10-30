package ao.bedi.cronosestudo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class Discipline(val id: Long = System.currentTimeMillis(), val name: String, val color: Long)
data class ScheduleSlot(val id: Long = System.nanoTime(), val disciplineId: Long, val weekday: Int, val start: String, val end: String)
data class StudyLog(val id: Long = System.nanoTime(), val disciplineId: Long, val date: String, val minutes: Int)

private val Blue = Color(0xFF087DF0)
private val Navy = Color(0xFF112544)
private val Bg = Color(0xFFF5F8FD)
private val Red = Color(0xFFFF416C)
private val palette = listOf(0xFF1684EAL, 0xFFFF426DL, 0xFFF9B51AL, 0xFF10B981L, 0xFF7C5CE7L, 0xFFFF7A32L)
private val dayNames = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Blue, background = Bg)) {
                CronosApp()
            }
        }
    }
}

@Composable
private fun CronosApp() {
    var tab by remember { mutableIntStateOf(0) }
    var disciplines by remember { mutableStateOf(emptyList<Discipline>()) }
    var slots by remember { mutableStateOf(emptyList<ScheduleSlot>()) }
    var logs by remember { mutableStateOf(emptyList<StudyLog>()) }
    var selectedDiscipline by remember { mutableStateOf<Discipline?>(null) }
    var showDisciplineDialog by remember { mutableStateOf(false) }
    var selectedSlot by remember { mutableStateOf<ScheduleSlot?>(null) }

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                val destinations = listOf(
                    Triple("Disciplinas", Icons.Default.School, 0),
                    Triple("Agenda", Icons.Default.CalendarMonth, 1),
                    Triple("Cronómetro", Icons.Default.Timer, 2),
                    Triple("Análise", Icons.Default.BarChart, 3),
                    Triple("Definições", Icons.Default.Settings, 4)
                )
                destinations.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item.third,
                        onClick = { tab = item.third; selectedDiscipline = null },
                        icon = { Icon(item.second, null) },
                        label = { Text(item.first, fontSize = 9.sp) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == 0 && selectedDiscipline == null) {
                FloatingActionButton(onClick = { showDisciplineDialog = true }, containerColor = Blue) {
                    Icon(Icons.Default.Add, null, tint = Color.White)
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> if (selectedDiscipline == null) {
                    DisciplinesScreen(disciplines, slots, logs) { selectedDiscipline = it }
                } else {
                    DisciplineDetailScreen(
                        discipline = selectedDiscipline!!,
                        slots = slots.filter { it.disciplineId == selectedDiscipline!!.id },
                        logs = logs.filter { it.disciplineId == selectedDiscipline!!.id },
                        onBack = { selectedDiscipline = null },
                        onAddSlot = { slots = slots + it },
                        onDeleteSlot = { target -> slots = slots.filterNot { it.id == target.id } },
                        onDeleteDiscipline = { target ->
                            disciplines = disciplines.filterNot { it.id == target.id }
                            slots = slots.filterNot { it.disciplineId == target.id }
                            logs = logs.filterNot { it.disciplineId == target.id }
                            selectedDiscipline = null
                        }
                    )
                }
                1 -> AgendaScreen(disciplines, slots) { selectedSlot = it }
                2 -> TimerScreen(disciplines) { id, minutes ->
                    logs = logs + StudyLog(disciplineId = id, date = LocalDate.now().toString(), minutes = minutes)
                }
                3 -> AnalysisScreen(disciplines, logs)
                else -> SettingsScreen()
            }
        }
    }

    if (showDisciplineDialog) {
        DisciplineDialog(
            onDismiss = { showDisciplineDialog = false },
            onSave = { name ->
                disciplines = disciplines + Discipline(name = name, color = palette[disciplines.size % palette.size])
                showDisciplineDialog = false
            }
        )
    }

    selectedSlot?.let { slot ->
        val discipline = disciplines.firstOrNull { it.id == slot.disciplineId }
        SlotActionsDialog(
            slot = slot,
            disciplineName = discipline?.name ?: "Disciplina",
            onDismiss = { selectedSlot = null },
            onDelete = {
                slots = slots.filterNot { it.id == slot.id }
                selectedSlot = null
            },
            onStudied = { minutes ->
                logs = logs + StudyLog(disciplineId = slot.disciplineId, date = LocalDate.now().toString(), minutes = minutes)
                selectedSlot = null
            }
        )
    }
}

@Composable
private fun TopBar(title: String, onBack: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().background(Color.White).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
        Text(title, Modifier.weight(1f), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Navy)
        Icon(Icons.Default.Search, null, tint = Navy)
    }
}

@Composable
private fun DisciplinesScreen(ds: List<Discipline>, slots: List<ScheduleSlot>, logs: List<StudyLog>, onOpen: (Discipline) -> Unit) {
    Column { TopBar("Disciplinas"); LazyColumn { items(ds, key = { it.id }) { d ->
        Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp).clickable { onOpen(d) }, colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(52.dp), shape = RoundedCornerShape(14.dp), color = Color(d.color)) { Icon(Icons.Default.School, null, tint = Color.White, modifier = Modifier.padding(12.dp)) }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(d.name, fontWeight = FontWeight.Bold)
                    Text("${timeLabel(logs.filter { it.disciplineId == d.id }.sumOf { it.minutes })} estudados", fontSize = 12.sp)
                    Text("${slots.count { it.disciplineId == d.id }} programações", fontSize = 12.sp)
                }
                Icon(Icons.Default.ChevronRight, null)
            }
        }
    } } }
}

@Composable
private fun DisciplineDetailScreen(discipline: Discipline, slots: List<ScheduleSlot>, logs: List<StudyLog>, onBack: () -> Unit, onAddSlot: (ScheduleSlot) -> Unit, onDeleteSlot: (ScheduleSlot) -> Unit, onDeleteDiscipline: (Discipline) -> Unit) {
    var showSchedule by remember { mutableStateOf(false) }
    Column {
        TopBar(discipline.name, onBack)
        Card(Modifier.fillMaxWidth().padding(14.dp), colors = CardDefaults.cardColors(Color(discipline.color))) {
            Column(Modifier.padding(18.dp)) { Text(discipline.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold); Text("${timeLabel(logs.sumOf { it.minutes })} estudados", color = Color.White) }
        }
        Text("Programações", Modifier.padding(16.dp, 8.dp), fontWeight = FontWeight.Bold)
        LazyColumn(Modifier.weight(1f)) { items(slots, key = { it.id }) { slot ->
            Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp), colors = CardDefaults.cardColors(Color.White)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Alarm, null, tint = Color(discipline.color))
                    Column(Modifier.padding(start = 12.dp).weight(1f)) { Text(dayNames[slot.weekday - 1], fontWeight = FontWeight.Bold); Text("${slot.start} - ${slot.end}") }
                    IconButton(onClick = { onDeleteSlot(slot) }) { Icon(Icons.Default.Delete, null, tint = Red) }
                }
            }
        } }
        OutlinedButton(onClick = { showSchedule = true }, modifier = Modifier.fillMaxWidth().padding(14.dp)) { Icon(Icons.Default.Add, null); Text(" Adicionar programação") }
        Button(onClick = { onDeleteDiscipline(discipline) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFE9EE), contentColor = Red)) { Text("Eliminar disciplina") }
    }
    if (showSchedule) ScheduleDialog(discipline, { showSchedule = false }) { onAddSlot(it); showSchedule = false }
}

@Composable
private fun AgendaScreen(ds: List<Discipline>, slots: List<ScheduleSlot>, onOpen: (ScheduleSlot) -> Unit) {
    var mode by remember { mutableIntStateOf(1) }
    var weekOffset by remember { mutableIntStateOf(0) }
    val monday = LocalDate.now().minusDays((LocalDate.now().dayOfWeek.value - 1).toLong()).plusWeeks(weekOffset.toLong())
    Column {
        TopBar("Agenda")
        Row(Modifier.fillMaxWidth().padding(12.dp)) { listOf("Dia", "Semana", "Mês").forEachIndexed { index, title ->
            Button(onClick = { mode = index }, modifier = Modifier.weight(1f).padding(2.dp), colors = ButtonDefaults.buttonColors(containerColor = if (mode == index) Blue else Color(0xFFE8EEF7), contentColor = if (mode == index) Color.White else Navy)) { Text(title) }
        } }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { weekOffset-- }) { Icon(Icons.Default.ChevronLeft, null) }
            Text("${monday.dayOfMonth} – ${monday.plusDays(6).format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy"))}", Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            IconButton(onClick = { weekOffset++ }) { Icon(Icons.Default.ChevronRight, null) }
        }
        if (mode == 1) WeekView(ds, slots, monday, onOpen) else DayView(ds, slots, LocalDate.now().dayOfWeek.value, onOpen)
    }
}

@Composable
private fun WeekView(ds: List<Discipline>, slots: List<ScheduleSlot>, monday: LocalDate, onOpen: (ScheduleSlot) -> Unit) {
    Row(Modifier.fillMaxSize().padding(8.dp)) {
        (1..7).forEach { day ->
            Column(Modifier.weight(1f)) {
                Text(dayNames[day - 1], Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 10.sp)
                Text(monday.plusDays((day - 1).toLong()).dayOfMonth.toString(), Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                slots.filter { it.weekday == day }.sortedBy { it.start }.forEach { slot ->
                    val d = ds.firstOrNull { it.id == slot.disciplineId }
                    Card(Modifier.fillMaxWidth().padding(2.dp).clickable { onOpen(slot) }, colors = CardDefaults.cardColors(Color(d?.color ?: palette.first()).copy(alpha = .22f))) {
                        Text("${(d?.name ?: "").take(5)}\n${slot.start}", fontSize = 9.sp, modifier = Modifier.padding(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayView(ds: List<Discipline>, slots: List<ScheduleSlot>, day: Int, onOpen: (ScheduleSlot) -> Unit) {
    LazyColumn { items(slots.filter { it.weekday == day }.sortedBy { it.start }) { slot ->
        val d = ds.firstOrNull { it.id == slot.disciplineId }
        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).clickable { onOpen(slot) }, colors = CardDefaults.cardColors(Color.White)) {
            Row(Modifier.padding(14.dp)) { Text("${slot.start}\n${slot.end}", Modifier.width(80.dp)); Text(d?.name ?: "Disciplina", fontWeight = FontWeight.Bold) }
        }
    } }
}

@Composable
private fun TimerScreen(ds: List<Discipline>, onFinish: (Long, Int) -> Unit) {
    var selected by remember(ds) { mutableStateOf(ds.firstOrNull()) }
    var seconds by remember { mutableLongStateOf(0) }
    var running by remember { mutableStateOf(false) }
    LaunchedEffect(running) { while (running) { delay(1000); seconds++ } }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TopBar("Cronómetro")
        Text(selected?.name ?: "Selecione uma disciplina", Modifier.padding(20.dp), fontWeight = FontWeight.Bold)
        Text("%02d:%02d:%02d".format(seconds / 3600, (seconds / 60) % 60, seconds % 60), fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Navy)
        Row(Modifier.padding(20.dp)) {
            Button(onClick = { running = !running }) { Text(if (running) "Pausar" else "Iniciar") }
            Spacer(Modifier.width(10.dp))
            OutlinedButton(onClick = { selected?.let { onFinish(it.id, (seconds / 60).toInt().coerceAtLeast(1)) }; seconds = 0; running = false }) { Text("Terminar") }
        }
        LazyColumn { items(ds) { d -> TextButton(onClick = { selected = d }) { Text(d.name) } } }
    }
}

@Composable
private fun AnalysisScreen(ds: List<Discipline>, logs: List<StudyLog>) {
    val total = logs.sumOf { it.minutes }
    Column {
        TopBar("Análise")
        Row { StatCard("Tempo total", timeLabel(total), Modifier.weight(1f)); StatCard("Média diária", timeLabel(total / 7), Modifier.weight(1f)) }
        Text("Tempo por disciplina", Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
        LazyColumn { items(ds) { d ->
            val minutes = logs.filter { it.disciplineId == d.id }.sumOf { it.minutes }
            ListItem(headlineContent = { Text(d.name) }, supportingContent = { LinearProgressIndicator(progress = { if (total == 0) 0f else minutes.toFloat() / total }) }, trailingContent = { Text(timeLabel(minutes)) })
        } }
    }
}

@Composable private fun StatCard(title: String, value: String, modifier: Modifier) { Card(modifier.padding(8.dp), colors = CardDefaults.cardColors(Color.White)) { Column(Modifier.padding(16.dp)) { Text(title, color = Color.Gray); Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold) } } }
@Composable private fun SettingsScreen() { Column { TopBar("Definições"); listOf("Alarmes de estudo", "Som", "Vibração", "Tema escuro").forEach { label -> var checked by remember { mutableStateOf(true) }; Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(checked, { checked = it }) } }; ListItem(headlineContent = { Text("CronosEstudo") }, trailingContent = { Text("v1.7.1") }) } }

@Composable
private fun DisciplineDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Nova disciplina") }, text = { OutlinedTextField(name, { name = it }, label = { Text("Nome da disciplina") }) }, confirmButton = { Button(onClick = { if (name.isNotBlank()) onSave(name.trim()) }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun ScheduleDialog(discipline: Discipline, onDismiss: () -> Unit, onSave: (ScheduleSlot) -> Unit) {
    var day by remember { mutableIntStateOf(1) }; var start by remember { mutableStateOf("18:00") }; var end by remember { mutableStateOf("19:00") }; var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Adicionar programação") }, text = { Column { Row { (1..7).forEach { index -> FilterChip(selected = day == index, onClick = { day = index }, label = { Text(dayNames[index - 1].take(1)) }, modifier = Modifier.weight(1f)) } }; OutlinedTextField(start, { start = it }, label = { Text("Início") }); OutlinedTextField(end, { end = it }, label = { Text("Fim") }); if (error.isNotEmpty()) Text(error, color = Red) } }, confirmButton = { Button(onClick = { runCatching { val a = LocalTime.parse(start); val b = LocalTime.parse(end); require(b.isAfter(a)); onSave(ScheduleSlot(disciplineId = discipline.id, weekday = day, start = a.toString(), end = b.toString())) }.onFailure { error = "Horário inválido." } }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun SlotActionsDialog(slot: ScheduleSlot, disciplineName: String, onDismiss: () -> Unit, onDelete: () -> Unit, onStudied: (Int) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(disciplineName) }, text = { Column { Text("${dayNames[slot.weekday - 1]} • ${slot.start} - ${slot.end}"); Button(onClick = { onStudied(60) }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("Marcar 1 hora como estudada") }; TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Eliminar apenas esta programação", color = Red) } } }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } })
}

private fun timeLabel(minutes: Int): String = if (minutes >= 60) "${minutes / 60}h ${minutes % 60}min" else "${minutes}min"
