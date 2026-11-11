package pe.lasflores.controlinterno.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.lasflores.controlinterno.report.ExcelWriter
import pe.lasflores.controlinterno.report.ReportBuilder
import pe.lasflores.controlinterno.report.ReportCounts
import pe.lasflores.controlinterno.report.ReportPeriod
import pe.lasflores.controlinterno.report.ReportRepository
import pe.lasflores.controlinterno.report.ReportSelection
import pe.lasflores.controlinterno.ui.ErrorReportText
import pe.lasflores.controlinterno.ui.LocalErrorReporter

@Composable
fun ReportScreen(repository:ReportRepository,staffVm:StaffViewModel) {
    val people by staffVm.staff.collectAsState()
    val scope=rememberCoroutineScope()
    val context=LocalContext.current
    var periodName by rememberSaveable {mutableStateOf(ReportPeriod.WEEK.name)}
    var dateText by rememberSaveable {mutableStateOf(LocalDate.now().toString())}
    var workerId by rememberSaveable {mutableStateOf<String?>(null)}
    var preview by remember {mutableStateOf<ReportCounts?>(null)}
    var message by remember {mutableStateOf("Selecciona período y trabajador para preparar el reporte.")}
    val reportError = LocalErrorReporter.current
    LaunchedEffect(message) {
        if (ErrorReportText.isError(message)) reportError("Reportes", message)
    }
    var previewWorking by remember {mutableStateOf(false)}
    var exporting by remember {mutableStateOf(false)}
    var pendingExport by remember {mutableStateOf<ReportSelection?>(null)}
    val period=ReportPeriod.valueOf(periodName)
    val anchor=runCatching {LocalDate.parse(dateText)}.getOrNull()
    val selection=anchor?.let {ReportSelection(period,it,workerId)}
    val picker=rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    ) { uri ->
        val selected=pendingExport
        pendingExport=null
        if(uri!=null&&selected!=null) scope.launch {
            exporting=true
            try {
                val total=withContext(Dispatchers.IO) {
                    val snapshot=repository.snapshot()
                    val workbook=ReportBuilder.build(snapshot,selected)
                    val output=context.contentResolver.openOutputStream(uri)
                        ?: error("Android no pudo crear el archivo Excel.")
                    output.use { ExcelWriter().write(workbook,it) }
                    workbook.sumOf {it.rows.size}
                }
                message="Excel guardado correctamente ("+total+" filas de datos). Ya puedes compartirlo con administración."
            } catch(e:Exception) {
                message="Error al exportar: "+ErrorReportText.compact(e.message?:"comprueba el almacenamiento")
            } finally {exporting=false}
        }
    }
    LaunchedEffect(selection) {
        preview=null
        if(selection!=null) {
            previewWorking=true
            try {
                preview=withContext(Dispatchers.IO) {
                    ReportBuilder.counts(repository.snapshot(),selection)
                }
            } catch(e:Exception) {
                message="No se pudo leer el reporte: "+(e.message?:"error de base de datos")
            } finally {previewWorking=false}
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding=PaddingValues(16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Text("Reportes Excel",style=MaterialTheme.typography.headlineSmall,
                fontWeight=FontWeight.Bold)
            Text("Exporta indicadores para administración sin internet. " +
                "Incluye resumen, personal, uniformes, briefings, funciones, evidencias e incidentes.",
                style=MaterialTheme.typography.bodyMedium)
        }
        item {
            Card {
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                    Text("1. Selecciona el período",fontWeight=FontWeight.Bold)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        ReportPeriod.entries.forEach{p->
                            FilterChip(selected=period==p,onClick={periodName=p.name},
                                label={Text(p.label)})
                        }
                    }
                    OutlinedTextField(value=dateText,onValueChange={dateText=it.take(10)},
                        label={Text("Fecha de referencia (AAAA-MM-DD)")},
                        modifier=Modifier.fillMaxWidth(),singleLine=true)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick={
                            if(anchor!=null)dateText=ReportSelection(period,anchor)
                                .shift(-1).anchor.toString()
                        },enabled=anchor!=null){Text("‹ Anterior")}
                        OutlinedButton(onClick={
                            if(anchor!=null)dateText=ReportSelection(period,anchor)
                                .shift(1).anchor.toString()
                        },enabled=anchor!=null){Text("Siguiente ›")}
                        TextButton(onClick={dateText=LocalDate.now().toString()}){Text("Hoy")}
                    }
                    if(selection==null)
                        Text("Introduce una fecha válida.",color=MaterialTheme.colorScheme.error)
                    else {
                        Text("Desde "+selection.start+" hasta "+selection.end,
                            color=MaterialTheme.colorScheme.primary,
                            fontWeight=FontWeight.Bold)
                        Text(if(period==ReportPeriod.WEEK)
                            "Semana de lunes a domingo." else "Período completo seleccionado.",
                            style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text("2. Filtra por trabajador",fontWeight=FontWeight.Bold)
                    Text("Todo el equipo muestra los resultados generales. " +
                        "Al elegir un trabajador, los incidentes se filtran por responsable asignado.",
                        style=MaterialTheme.typography.bodySmall)
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        item{
                            FilterChip(selected=workerId==null,onClick={workerId=null},
                                label={Text("Todo el equipo")})
                        }
                        items(people,key={it.id}){p->
                            FilterChip(selected=workerId==p.id,onClick={workerId=p.id},
                                label={Text(p.fullName)})
                        }
                    }
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("3. Vista previa",fontWeight=FontWeight.Bold)
                    val counts=preview
                    if(previewWorking) Text("Calculando indicadores…")
                    else if(counts==null) Text("Sin vista previa disponible.")
                    else {
                        Text("Uniformes finalizados: "+counts.uniformsFinalized+
                            " · Observados inicialmente: "+counts.uniformsObserved)
                        Text("Briefings registrados: "+counts.briefingRegistered+
                            " · Por informar: "+counts.briefingPendingInformation)
                        Text("Funciones asignadas: "+counts.dutiesAssigned+
                            " · Evaluadas: "+counts.dutiesEvaluated)
                        Text("Cumplió: "+counts.dutiesOk+
                            " · No cumplió: "+counts.dutiesBad+
                            " · Pendientes: "+counts.dutiesPending)
                        Text("Cumplimiento de funciones: "+
                            (counts.dutiesPercent?.toString()?.plus("%")?:"Sin evaluaciones"),
                            fontWeight=FontWeight.Bold,
                            color=MaterialTheme.colorScheme.primary)
                        Text("Incidentes registrados: "+counts.incidentsRegistered+
                            " · Abiertos: "+counts.incidentsOpen)
                        Text("Entregas de materiales: "+counts.deliveriesRegistered+
                            " · Unidades no anuladas: "+counts.deliveryUnits)
                    }
                    Text("Los pendientes no se contabilizan como incumplimientos. " +
                        "Las fotos no se incrustan en el Excel; sus detalles sí aparecen y " +
                        "las imágenes permanecen en la aplicación y el respaldo JSON.",
                        style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Button(onClick={
                if(selection!=null) {
                    pendingExport=selection
                    picker.launch(selection.fileName())
                }
            },enabled=selection!=null&&!previewWorking&&!exporting,
                modifier=Modifier.fillMaxWidth()) {
                Text(if(exporting)"Creando Excel…" else "Guardar reporte Excel (.xlsx)")
            }
            Text(if (ErrorReportText.isError(message)) ErrorReportText.compact(message) else message,modifier=Modifier.padding(top=8.dp),
                color=if(message.startsWith("Error")||message.startsWith("No se"))
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style=MaterialTheme.typography.bodySmall)
        }
    }
}
