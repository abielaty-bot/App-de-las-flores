package pe.lasflores.controlinterno.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import pe.lasflores.controlinterno.data.FunctionCheckEntity
import pe.lasflores.controlinterno.data.FunctionEvidenceEntity
import pe.lasflores.controlinterno.data.FunctionEventEntity
import pe.lasflores.controlinterno.data.StaffEntity
import pe.lasflores.controlinterno.domain.FunctionRules

@Composable
fun FunctionMonthlyPanel(
    checks:List<FunctionCheckEntity>,
    evidence:List<FunctionEvidenceEntity>,
    people:List<StaffEntity>
) {
    var month by rememberSaveable {
        mutableStateOf(LocalDate.now().toString().take(7))
    }
    var area by rememberSaveable { mutableStateOf("Todas") }
    var person by rememberSaveable { mutableStateOf("Todos") }
    var order by rememberSaveable { mutableStateOf("Mayor cumplimiento") }
    val valid=Regex("""\d{4}-(0[1-9]|1[0-2])""").matches(month)
    val filtered=if(valid)checks.filter {
        it.voidedAtMillis==null && it.dateIso.startsWith(month) &&
            (area=="Todas" || it.areaSnapshot==area) &&
            (person=="Todos" || it.staffId==person)
    } else emptyList()
    val total=FunctionRules.summarize(filtered,evidence)
    val historicalNames=checks.associate {it.staffId to it.staffNameSnapshot}
    val allPeople=people.filter { it.isActive || it.id in historicalNames }
    val summary=filtered.groupBy { it.staffId }.map { (id,rows) ->
        Triple(id,FunctionRules.summarize(rows,evidence),rows)
    }.sortedWith(compareBy<Triple<String,FunctionRules.Summary,List<FunctionCheckEntity>>> {
        if(order=="Mayor incumplimiento") it.second.rate ?: 101
        else -(it.second.rate ?: -1)
    }.thenByDescending { it.second.evaluated })
    val failed=filtered.filter { it.result==FunctionRules.BAD }
        .groupBy { it.staffId to it.taskId }
        .map { (_,rows) -> rows.first() to rows.size }
        .sortedByDescending { it.second }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(14.dp),
        verticalArrangement=Arrangement.spacedBy(9.dp)) {
        item {
            Text("Cumplimiento mensual",style=MaterialTheme.typography.headlineSmall,
                fontWeight=FontWeight.Bold)
            Text("Porcentaje calculado solo sobre funciones evaluadas. " +
                "Las pendientes y anuladas no entran en el denominador.",
                style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(value=month,onValueChange={month=it.take(7)},
                label={Text("Mes · AAAA-MM")},modifier=Modifier.fillMaxWidth())
            if(!valid) Text("Introduce un mes válido.",color=MaterialTheme.colorScheme.error)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                item { FilterChip(selected=area=="Todas",onClick={area="Todas"},
                    label={Text("Todas las áreas")}) }
                items(FunctionRules.AREAS) {value->
                    FilterChip(selected=area==value,onClick={area=value},label={Text(value)})
                }
            }
            Text("Trabajador",fontWeight=FontWeight.Bold)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                item { FilterChip(selected=person=="Todos",onClick={person="Todos"},
                    label={Text("Todos")}) }
                items(allPeople,key={it.id}) { worker ->
                    FilterChip(selected=person==worker.id,onClick={person=worker.id},
                        label={Text(worker.fullName)})
                }
            }
            Card {
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text("Resumen del mes",fontWeight=FontWeight.Bold)
                    Text("Cumplimiento: "+(total.rate?.toString()?.plus("%") ?: "Sin evaluaciones"))
                    Text("Asignadas: "+total.assigned+" · Evaluadas: "+total.evaluated)
                    Text("Cumplió: "+total.ok+" · No cumplió: "+total.bad)
                    Text("Pendientes: "+total.pending+" · Corrigieron: "+total.corrected)
                    Text("Envío de evidencias declarado: "+total.sentEvidence+
                        " · Sin confirmar: "+total.missingEvidence)
                }
            }
            LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                items(listOf("Mayor cumplimiento","Mayor incumplimiento")) {sort->
                    FilterChip(selected=order==sort,onClick={order=sort},label={Text(sort)})
                }
            }
            Text("Comparación por trabajador",fontWeight=FontWeight.Bold)
            Text("Un 100 % con una sola función evaluada no equivale a 100 % con veinte.",
                style=MaterialTheme.typography.bodySmall)
        }
        items(summary,key={it.first}) { (id,stats,_) ->
            val name=people.firstOrNull { it.id==id }?.fullName ?:
                historicalNames[id] ?: id
            Card {
                Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text(name,fontWeight=FontWeight.Bold)
                    Text("Cumplimiento: "+(stats.rate?.toString()?.plus("%")?:"Sin evaluar"))
                    Text("Asignadas: "+stats.assigned+" · Evaluadas: "+stats.evaluated)
                    Text("Cumplió: "+stats.ok+" · No cumplió: "+stats.bad)
                    Text("Pendientes: "+stats.pending+" · Corregidas: "+stats.corrected)
                    Text("Sin evidencia enviada confirmada: "+stats.missingEvidence,
                        style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {Text("Incumplimientos más frecuentes",fontWeight=FontWeight.Bold)}
        items(failed.take(50),key={it.first.staffId+":"+it.first.taskId}) { (item,count)->
            Card {
                Column(Modifier.padding(13.dp)) {
                    Text(item.staffNameSnapshot,fontWeight=FontWeight.Bold)
                    Text(item.areaSnapshot+" · "+item.taskLabelSnapshot)
                    Text(count.toString()+" incumplimiento(s)")
                }
            }
        }
        if(summary.isEmpty()) item { Text("Sin asignaciones en el mes seleccionado.") }
    }
}

@Composable
fun FunctionHistoryPanel(
    checks:List<FunctionCheckEntity>,
    evidence:List<FunctionEvidenceEntity>,
    events:List<FunctionEventEntity>,
    people:List<StaffEntity>
) {
    var query by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf("") }
    var includeVoids by rememberSaveable { mutableStateOf(true) }
    val staffNames=remember(people) {people.associate {it.id to it.fullName}}
    val visible=checks.filter { row ->
        (query.isBlank() || (staffNames[row.staffId] ?: row.staffNameSnapshot)
            .contains(query,ignoreCase=true) ||
            row.taskLabelSnapshot.contains(query,ignoreCase=true)) &&
        (date.isBlank() || row.dateIso.startsWith(date.trim())) &&
        (includeVoids || row.voidedAtMillis==null)
    }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(14.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {
            Text("Historial de funciones",style=MaterialTheme.typography.headlineSmall,
                fontWeight=FontWeight.Bold)
            Text("Se conservan los registros anulados y los cambios de evaluación.",
                style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(value=query,onValueChange={query=it.take(100)},
                label={Text("Buscar trabajador o función")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(value=date,onValueChange={date=it.take(10)},
                label={Text("Fecha o mes (AAAA-MM-DD)")},modifier=Modifier.fillMaxWidth())
            FilterChip(selected=includeVoids,onClick={includeVoids=!includeVoids},
                label={Text("Incluir anulaciones")})
            Text(visible.size.toString()+" asignaciones históricas")
        }
        items(visible,key={it.id}) { item ->
            val past=events.filter{it.checkId==item.id}
            val photos=evidence.filter{it.checkId==item.id}
            Card {
                Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text(staffNames[item.staffId]?:item.staffNameSnapshot,
                        fontWeight=FontWeight.Bold)
                    Text(item.dateIso+" · "+item.areaSnapshot+" · "+item.taskLabelSnapshot)
                    Text(FunctionRules.resultName(item),
                        color=if(item.result==FunctionRules.BAD)
                            MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary)
                    if(item.note.isNotBlank()) Text(item.note,
                        style=MaterialTheme.typography.bodySmall)
                    if(item.correctedAtMillis!=null)
                        Text("Corrección: "+item.correctionNote,
                            style=MaterialTheme.typography.bodySmall)
                    Text(photos.size.toString()+" foto(s) · "+
                        (if(photos.any{it.groupSentAtMillis!=null})
                            "Envío al grupo declarado" else "Sin envío confirmado"),
                        style=MaterialTheme.typography.bodySmall)
                    if(past.isNotEmpty()) Text("Cambios ("+past.size+")",
                        fontWeight=FontWeight.Bold)
                    past.take(12).forEach { ev->
                        Text("• "+ev.action+
                            (if(ev.actor.isNotBlank())" · "+ev.actor else "")+
                            (if(ev.note.isNotBlank())" · "+ev.note else ""),
                            style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if(visible.isEmpty()) item {Text("No hay registros con estos filtros.")}
    }
}
