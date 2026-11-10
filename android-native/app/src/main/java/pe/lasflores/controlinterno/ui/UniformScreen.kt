package pe.lasflores.controlinterno.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.lasflores.controlinterno.data.StaffEntity
import pe.lasflores.controlinterno.data.UniformAnswerEntity
import pe.lasflores.controlinterno.domain.UniformDaySnapshot
import pe.lasflores.controlinterno.domain.UniformRequirement
import pe.lasflores.controlinterno.domain.UniformRules

private val neutral = Color(0xFFF5F7F5)
private val positive = Color(0xFFE7F4EC)
private val negative = Color(0xFFFBEAE8)
private val correctedColor = Color(0xFFE8F1FC)

@Composable
fun UniformScreen(people: List<StaffEntity>, vm: UniformViewModel) {
    val dateIso by vm.dateIso.collectAsState()
    val snapshot by vm.day.collectAsState()
    val saved by vm.saveStatus.collectAsState()
    val validSnapshot = if (snapshot.dateIso == dateIso) snapshot else UniformDaySnapshot(dateIso)
    val isNoReview = validSnapshot.activity?.status == "NO_REALIZADA"
    var expandedId by rememberSaveable { mutableStateOf("") }
    var dialog by remember { mutableStateOf("") }
    var dialogStaff by remember { mutableStateOf("") }
    var dialogRequirement by remember { mutableStateOf("") }
    var verifier by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }
    val activePeople = people.filter { it.isActive }

    LazyColumn(modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Control de uniformes", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text("Inspección por puesto · Borradores guardados automáticamente",
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.changeDay(-1) }) { Text("‹") }
                Text(dateIso, fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = { vm.changeDay(1) }) { Text("›") }
                TextButton(onClick = vm::today) { Text("Hoy") }
            }
            Text(saved, color = if (saved.startsWith("Error")) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = if (isNoReview) neutral else Color.White)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (isNoReview) "No se realizó revisión" else "Estado del día: sin cierre general",
                        fontWeight = FontWeight.SemiBold)
                    if (isNoReview) Text(validSnapshot.activity?.reason.orEmpty(),
                        style = MaterialTheme.typography.bodySmall)
                    else Text("Si hoy no hubo supervisión, regístralo; no se considerará incumplimiento.",
                        style = MaterialTheme.typography.bodySmall)
                    if (isNoReview) {
                        OutlinedButton(onClick = vm::reopenDay) { Text("Reabrir jornada") }
                    } else {
                        OutlinedButton(onClick = {
                            reason = ""; dialog = "NO_REVIEW"
                        }) { Text("Hoy no se realizó revisión") }
                    }
                }
            }
        }
        item {
            Text("Trabajadores activos (${activePeople.size})",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("La programación y los descansos se integrarán en otra etapa. No se registran faltas automáticamente.",
                style = MaterialTheme.typography.bodySmall)
        }
        items(activePeople, key = { it.id }) { staff ->
            val inspection = validSnapshot.inspectionFor(staff.id)
            val requirements = UniformRules.forRole(inspection?.roleSnapshot ?: staff.role)
            val reviewed = inspection?.let { record ->
                requirements.count { validSnapshot.answerFor(record.id, it.id) != null }
            } ?: 0
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(staff.fullName, fontWeight = FontWeight.Bold)
                            Text("${staff.role} · $reviewed/${requirements.size} revisados",
                                style = MaterialTheme.typography.bodySmall)
                            Text(inspection?.let(validSnapshot::statusFor) ?: "Sin revisar",
                                color = statusColor(inspection?.let(validSnapshot::statusFor)),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(onClick = { expandedId = if (expandedId == staff.id) "" else staff.id },
                            enabled = !isNoReview) {
                            Text(if (expandedId == staff.id) "Ocultar" else "Requisitos")
                        }
                    }
                    if (expandedId == staff.id && !isNoReview) {
                        HorizontalDivider()
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val columns = if (maxWidth >= 620.dp) 2 else 1
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                requirements.chunked(columns).forEach { rowItems ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        rowItems.forEach { requirement ->
                                            val answer = inspection?.let { validSnapshot.answerFor(it.id, requirement.id) }
                                            RequirementBox(requirement, answer, inspection?.workflow == "FINALIZED",
                                                onAnswer = { value -> vm.choose(staff.id, requirement.id, value) },
                                                onCorrect = {
                                                    dialogStaff = staff.id; dialogRequirement = requirement.id
                                                    verifier = ""; reason = ""; dialog = "CORRECT"
                                                },
                                                onRectify = {
                                                    dialogStaff = staff.id; dialogRequirement = requirement.id
                                                    verifier = ""; reason = ""; dialog = "RECTIFY"
                                                },
                                                modifier = Modifier.weight(1f))
                                        }
                                        if (rowItems.size < columns) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                        if (inspection?.workflow == "DRAFT") {
                            val canFinish = reviewed == requirements.size && requirements.isNotEmpty()
                            Button(onClick = { dialogStaff = staff.id; dialog = "FINALIZE" },
                                enabled = canFinish,
                                modifier = Modifier.fillMaxWidth()) {
                                Text(if (canFinish) "Finalizar revisión" else "Faltan ${requirements.size - reviewed} requisitos")
                            }
                        }
                        Text(if (inspection?.workflow == "FINALIZED")
                            "Resultado inicial cerrado. Las faltas posteriores se registran como correcciones o rectificaciones."
                        else "Toca de nuevo un botón marcado para desmarcarlo. El borrador se guarda al instante.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (activePeople.isEmpty()) item { Text("Añade trabajadores desde Personal para empezar.") }
    }

    if (dialog.isNotEmpty()) {
        val action = dialog
        AlertDialog(onDismissRequest = { dialog = "" },
            title = { Text(when(action) {
                "NO_REVIEW" -> "No se realizó revisión"
                "FINALIZE" -> "Finalizar inspección"
                "CORRECT" -> "Verificar corrección"
                else -> "Rectificar error de marcación"
            }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when(action) {
                        "NO_REVIEW" -> Text("No podrás declararlo si ya existen inspecciones iniciadas.")
                        "FINALIZE" -> Text("Se guardará el resultado inicial. Después no podrás desmarcar las faltas sin dejar constancia.")
                        "CORRECT" -> Text("El trabajador solucionó una falta real. El resultado inicial continuará siendo Observado.")
                        "RECTIFY" -> Text("El requisito estaba mal marcado. Esta rectificación conservará un evento de auditoría.")
                    }
                    if (action == "CORRECT" || action == "RECTIFY") {
                        OutlinedTextField(value = verifier, onValueChange = { verifier = it.take(90) },
                            label = { Text("Verificado por") }, singleLine = true)
                    }
                    if (action == "NO_REVIEW" || action == "RECTIFY") {
                        OutlinedTextField(value = reason, onValueChange = { reason = it.take(250) },
                            label = { Text("Motivo") }, minLines = 2)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    when(action) {
                        "NO_REVIEW" -> vm.markNotPerformed(reason)
                        "FINALIZE" -> vm.finalize(dialogStaff)
                        "CORRECT" -> vm.correct(dialogStaff, dialogRequirement, verifier)
                        "RECTIFY" -> vm.rectify(dialogStaff, dialogRequirement, verifier, reason)
                    }
                    dialog = ""
                }, enabled = when(action) {
                    "NO_REVIEW" -> reason.trim().length >= 3
                    "CORRECT" -> verifier.trim().length >= 2
                    "RECTIFY" -> verifier.trim().length >= 2 && reason.trim().length >= 5
                    else -> true
                }) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Cancelar") } }
        )
    }
}

private fun statusColor(status: String?): Color = when(status) {
    "Conforme" -> Color(0xFF18744D)
    "Observado" -> Color(0xFFB43D36)
    "Corregido" -> Color(0xFF296FA8)
    "Corrección parcial" -> Color(0xFF8A6817)
    else -> Color(0xFF63766B)
}

@Composable
private fun RequirementBox(
    requirement: UniformRequirement,
    answer: UniformAnswerEntity?,
    finalized: Boolean,
    onAnswer: (String) -> Unit,
    onCorrect: () -> Unit,
    onRectify: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = when {
        answer == null -> "Sin revisar"
        answer.status == UniformRules.OK -> "Cumple"
        answer.correctedAtMillis != null -> "Corregido"
        else -> "No cumple"
    }
    val background = when(status) {
        "Cumple" -> positive
        "No cumple" -> negative
        "Corregido" -> correctedColor
        else -> neutral
    }
    Card(modifier = modifier, shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = background)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(requirement.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text("${requirement.group} · $status", style = MaterialTheme.typography.labelSmall,
                color = statusColor(status))
            if (!finalized) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { onAnswer(UniformRules.OK) }, modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp)) { Text("✓ Cumple", style = MaterialTheme.typography.labelSmall) }
                    OutlinedButton(onClick = { onAnswer(UniformRules.BAD) }, modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp)) { Text("× No cumple", style = MaterialTheme.typography.labelSmall) }
                }
            } else if (answer?.status == UniformRules.BAD && answer.correctedAtMillis == null) {
                OutlinedButton(onClick = onCorrect, modifier = Modifier.fillMaxWidth()) { Text("Verificar corrección") }
                TextButton(onClick = onRectify) { Text("Desmarcar error") }
            } else if (answer?.correctedBy != null) {
                Text("Verificado por ${answer.correctedBy}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
