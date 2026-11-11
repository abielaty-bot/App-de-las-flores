package pe.lasflores.controlinterno.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import pe.lasflores.controlinterno.data.IncidentEntity
import pe.lasflores.controlinterno.data.IncidentTypeEntity
import pe.lasflores.controlinterno.domain.IncidentRules

@Composable
fun IncidentHistoryPanel(
    incidents:List<IncidentEntity>,
    onNew:()->Unit,
    onDetails:(String)->Unit
) {
    var query by rememberSaveable{mutableStateOf("")}
    var category by rememberSaveable{mutableStateOf("todos")}
    var status by rememberSaveable{mutableStateOf("todos")}
    var area by rememberSaveable{mutableStateOf("todas")}
    var from by rememberSaveable{mutableStateOf("")}
    var until by rememberSaveable{mutableStateOf("")}
    val validDates= listOf(from,until).all {
        it.isBlank() || runCatching{LocalDate.parse(it).toString()==it}.getOrDefault(false)
    } && (from.isBlank() || until.isBlank() || from<=until)
    val visible=if(!validDates) emptyList() else incidents.filter{case->
        (query.isBlank()||listOf(case.code,case.productOrProcess,case.description,
            case.typeLabelSnapshot,case.reporter,case.detectedArea)
            .any{it.contains(query.trim(),ignoreCase=true)}) &&
        (category=="todos"||case.category==category) &&
        (status=="todos"||case.status==status) &&
        (area=="todas"||case.detectedArea==area) &&
        (from.isBlank()||case.occurredDate>=from) &&
        (until.isBlank()||case.occurredDate<=until)
    }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(14.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {
            Text("Seguimiento de incidentes",
                style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            Text("Filtra registros, revisa acciones, cierres y anulaciones.",
                style=MaterialTheme.typography.bodySmall)
            Button(onClick=onNew){Text("+ Nuevo incidente")}
            OutlinedTextField(query,{query=it.take(100)},
                label={Text("Buscar código, producto o descripción")},
                modifier=Modifier.fillMaxWidth())
            IncidentChoiceRow("Categoría",listOf(
                "todos" to "Todas",
                IncidentRules.PRODUCTO to "Producto",
                IncidentRules.OPERATIVO to "Operativo"
            ),category,{category=it})
            IncidentChoiceRow("Estado",listOf("todos" to "Todos")+
                listOf(IncidentRules.REGISTRADO,IncidentRules.EN_ATENCION,
                    IncidentRules.VERIFICACION,IncidentRules.CERRADO,
                    IncidentRules.ANULADO).map{it to IncidentRules.statusName(it)},
                status,{status=it})
            IncidentChoiceRow("Área de detección",listOf("todas" to "Todas")+
                IncidentRules.AREAS.map{it to it},area,{area=it})
            OutlinedTextField(from,{from=it.take(10)},
                label={Text("Desde · AAAA-MM-DD")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(until,{until=it.take(10)},
                label={Text("Hasta · AAAA-MM-DD")},modifier=Modifier.fillMaxWidth())
            if(!validDates) Text("Corrige el rango de fechas.",
                color=MaterialTheme.colorScheme.error)
            Text(visible.size.toString()+" registros encontrados",
                style=MaterialTheme.typography.bodySmall)
        }
        items(visible,key={it.id}){case->IncidentListCard(case,onDetails)}
        if(visible.isEmpty()&&validDates)item{Text("Sin incidentes para esos filtros.")}
    }
}

@Composable
fun IncidentAnalysisPanel(incidents:List<IncidentEntity>) {
    var month by rememberSaveable{mutableStateOf(LocalDate.now().toString().take(7))}
    val valid=Regex("""\d{4}-(0[1-9]|1[0-2])""").matches(month)
    val selected=if(valid) incidents.filter {
        it.occurredDate.startsWith(month) && it.status!=IncidentRules.ANULADO
    } else emptyList()
    val stats=IncidentRules.stats(selected)
    val byArea=selected.groupingBy{it.detectedArea}.eachCount()
        .toList().sortedWith(compareByDescending<Pair<String,Int>>{it.second}
            .thenBy{it.first})
    val byType=selected.groupingBy{it.typeLabelSnapshot}.eachCount()
        .toList().sortedWith(compareByDescending<Pair<String,Int>>{it.second}
            .thenBy{it.first})
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(14.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {
            Text("Análisis de incidentes",
                style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            Text("Detecciones, problemas frecuentes y casos pendientes.",
                style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(month,{month=it.take(7)},
                label={Text("Mes · AAAA-MM")},modifier=Modifier.fillMaxWidth())
            if(!valid)Text("Mes inválido.",color=MaterialTheme.colorScheme.error)
            Card {
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text("Resumen · "+month,fontWeight=FontWeight.Bold)
                    Text("Casos: "+stats.total+" · Alimentos/bebidas: "+stats.products)
                    Text("Operativos: "+stats.operations+" · Cerrados: "+stats.closed)
                    Text("Pendientes: "+stats.pending)
                    Text("Prioridad alta/crítica pendiente: "+stats.highPriorityOpen)
                }
            }
            Text("Áreas de detección",fontWeight=FontWeight.Bold)
            Text("El área donde se detectó un problema no demuestra su origen o responsabilidad.",
                style=MaterialTheme.typography.bodySmall)
        }
        items(byArea,key={it.first}){ (area,count)->
            Card {Column(Modifier.padding(12.dp)) {
                Text(area,fontWeight=FontWeight.Bold)
                Text(count.toString()+" incidente(s)")
            }}
        }
        item {
            Text("Tipos más frecuentes",fontWeight=FontWeight.Bold)
            Text("Los casos anulados no cuentan en el análisis; siguen en el historial.",
                style=MaterialTheme.typography.bodySmall)
        }
        items(byType,key={it.first}){(label,count)->
            Card {Column(Modifier.padding(12.dp)) {
                Text(label,fontWeight=FontWeight.Bold)
                Text(count.toString()+" caso(s)")
            }}
        }
        if(selected.isEmpty()&&valid)item{
            Text("No hay incidentes vigentes registrados este mes.")
        }
    }
}

@Composable
fun IncidentCatalogPanel(vm:IncidentViewModel,types:List<IncidentTypeEntity>) {
    var category by rememberSaveable{mutableStateOf(IncidentRules.PRODUCTO)}
    var editing by remember{mutableStateOf<IncidentTypeEntity?>(null)}
    var creating by remember{mutableStateOf(false)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(14.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {
            Text("Tipos de incidentes",style=MaterialTheme.typography.headlineSmall,
                fontWeight=FontWeight.Bold)
            Text("Configura los tipos sin alterar los nombres guardados en incidentes anteriores.",
                style=MaterialTheme.typography.bodySmall)
            Button(onClick={creating=true}){Text("+ Nuevo tipo")}
            IncidentChoiceRow("Categoría",listOf(
                IncidentRules.PRODUCTO to "Alimentos y bebidas",
                IncidentRules.OPERATIVO to "Operaciones y servicio"
            ),category,{category=it})
        }
        items(types.filter{it.category==category},key={it.id}){type->
            Card {
                Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(type.label,fontWeight=FontWeight.Bold)
                    Text(if(type.isActive)"Activo" else "Desactivado",
                        style=MaterialTheme.typography.bodySmall)
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        item{OutlinedButton(onClick={editing=type}){Text("Editar")}}
                        item{OutlinedButton(onClick={
                            vm.setTypeActive(type.id,!type.isActive)
                        }){Text(if(type.isActive)"Desactivar" else "Activar")}}
                    }
                }
            }
        }
        if(types.none{it.category==category})item{Text("No hay tipos en esta categoría.")}
    }
    if(creating||editing!=null) IncidentTypeDialog(
        editing,onDismiss={editing=null;creating=false},
        onSave={id,c,label->
            vm.saveType(id,c,label)
            editing=null
            creating=false
        }
    )
}

@Composable
private fun IncidentTypeDialog(type:IncidentTypeEntity?,onDismiss:()->Unit,
    onSave:(String?,String,String)->Unit) {
    var label by remember(type?.id){mutableStateOf(type?.label.orEmpty())}
    var category by remember(type?.id){mutableStateOf(
        type?.category?:IncidentRules.PRODUCTO
    )}
    AlertDialog(onDismissRequest=onDismiss,
        title={Text(if(type==null)"Nuevo tipo de incidente" else "Editar tipo")},
        text={
            Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(label,{label=it.take(120)},
                    label={Text("Nombre del tipo *")},modifier=Modifier.fillMaxWidth())
                IncidentChoiceRow("Categoría",listOf(
                    IncidentRules.PRODUCTO to "Alimentos y bebidas",
                    IncidentRules.OPERATIVO to "Operaciones y servicio"
                ),category,{category=it})
                Text("Los registros históricos conservarán el tipo con el que se guardaron.",
                    style=MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton={
            Button(enabled=label.trim().length in 3..120,
                onClick={onSave(type?.id,category,label)}){Text("Guardar")}
        },
        dismissButton={TextButton(onClick=onDismiss){Text("Cancelar")}}
    )
}
