package pe.lasflores.controlinterno.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import pe.lasflores.controlinterno.data.IncidentActionEntity
import pe.lasflores.controlinterno.data.IncidentEntity
import pe.lasflores.controlinterno.data.IncidentTypeEntity
import pe.lasflores.controlinterno.data.OfflineIncidentRepository
import pe.lasflores.controlinterno.data.StaffEntity
import pe.lasflores.controlinterno.domain.IncidentRules

@Composable
fun IncidentScreen(vm:IncidentViewModel,staffVm:StaffViewModel) {
    val incidents by vm.incidents.collectAsState()
    val types by vm.types.collectAsState()
    val actions by vm.actions.collectAsState()
    val people by staffVm.staff.collectAsState()
    val message by vm.message.collectAsState()
    var tab by rememberSaveable {mutableStateOf("registro")}
    var newDialog by remember {mutableStateOf(false)}
    var detailsId by remember {mutableStateOf<String?>(null)}
    var followupId by remember {mutableStateOf<String?>(null)}
    var voidId by remember {mutableStateOf<String?>(null)}
    Column(Modifier.fillMaxSize()) {
        LazyRow(contentPadding=PaddingValues(horizontal=12.dp,vertical=4.dp),
            horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            items(listOf(
                "registro" to "Registro","seguimiento" to "Seguimiento",
                "analisis" to "Análisis","tipos" to "Tipos"
            )){(id,title)->
                FilterChip(selected=tab==id,onClick={tab=id},label={Text(title)})
            }
        }
        Text(message,modifier=Modifier.padding(horizontal=16.dp,vertical=4.dp),
            color=if(message.startsWith("Error"))MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            style=MaterialTheme.typography.labelSmall)
        when(tab) {
            "seguimiento"->IncidentHistoryPanel(incidents,
                onNew={newDialog=true},onDetails={detailsId=it})
            "analisis"->IncidentAnalysisPanel(incidents)
            "tipos"->IncidentCatalogPanel(vm,types)
            else->IncidentDashboard(incidents,
                onNew={newDialog=true},
                onOpen={detailsId=it},
                onHistory={tab="seguimiento"})
        }
    }
    if(newDialog) IncidentNewDialog(types,people,
        onDismiss={newDialog=false},
        onSubmit={vm.create(it);newDialog=false})

    detailsId?.let { id ->
        val record=incidents.firstOrNull {it.id==id}
        if(record!=null) IncidentDetailsDialog(
            record,actions.filter {it.incidentId==id},
            onDismiss={detailsId=null},
            onFollowup={followupId=id; detailsId=null},
            onVoid={voidId=id;detailsId=null})
    }
    followupId?.let {id ->
        val record=incidents.firstOrNull{it.id==id}
        if(record!=null) IncidentFollowupDialog(
            record,people,
            onDismiss={followupId=null},
            onSubmit={vm.followUp(it);followupId=null})
    }
    voidId?.let {id->
        val record=incidents.firstOrNull{it.id==id}
        if(record!=null) IncidentVoidDialog(record,
            onDismiss={voidId=null},
            onSubmit={reason,by->vm.void(id,reason,by);voidId=null})
    }
}

@Composable
fun IncidentChoiceRow(label:String,options:List<Pair<String,String>>,
    selected:String,onSelect:(String)->Unit) {
    Text(label,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.bodyMedium)
    LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        items(options,key={it.first}) {(id,title)->
            FilterChip(selected=selected==id,onClick={onSelect(id)},label={Text(title)})
        }
    }
}

@Composable
private fun IncidentDashboard(
    incidents:List<IncidentEntity>,onNew:()->Unit,
    onOpen:(String)->Unit,onHistory:()->Unit
) {
    val month=LocalDate.now().toString().take(7)
    val current=incidents.filter{it.occurredDate.startsWith(month)}
    val stats=IncidentRules.stats(current)
    val allOpen=incidents.filter(IncidentRules::isOpen)
        .sortedWith(compareByDescending<IncidentEntity>{it.priority=="critica"}
            .thenByDescending{it.priority=="alta"}
            .thenByDescending{it.occurredDate})
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(14.dp),
        verticalArrangement=Arrangement.spacedBy(11.dp)) {
        item {
            Text("Registro de incidentes",style=MaterialTheme.typography.headlineSmall,
                fontWeight=FontWeight.Bold)
            Text("Problemas de alimentos, bebidas y servicio: acciones, responsables y seguimiento.",
                style=MaterialTheme.typography.bodySmall)
        }
        item {
            Card {
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text("Resumen · "+month,fontWeight=FontWeight.Bold)
                    Text("Incidentes este mes: "+stats.total+" · Cerrados: "+stats.closed)
                    Text("Pendientes (todas las fechas): "+allOpen.size)
                    Text("Prioridad alta o crítica pendiente: "+allOpen.count{
                        it.priority in setOf("alta","critica") },
                        color=MaterialTheme.colorScheme.error)
                    Text("Los anulados se conservan en el historial.",
                        style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Button(onClick=onNew,modifier=Modifier.fillMaxWidth()) {
                Text("+ Registrar incidente")
            }
            OutlinedButton(onClick=onHistory,modifier=Modifier.fillMaxWidth()) {
                Text("Ver seguimiento e historial")
            }
        }
        item {
            Text("Casos pendientes",fontWeight=FontWeight.Bold,
                style=MaterialTheme.typography.titleMedium)
            Text("Detectar un problema en un área no demuestra que esa área lo originó.",
                style=MaterialTheme.typography.bodySmall)
        }
        items(allOpen.take(12),key={it.id}) {case ->
            IncidentListCard(case,onOpen)
        }
        if(allOpen.isEmpty())item {Text("No hay incidentes pendientes registrados.")}
    }
}

@Composable
fun IncidentListCard(case:IncidentEntity,onOpen:(String)->Unit) {
    Card {
        Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(case.code+" · "+case.productOrProcess,fontWeight=FontWeight.Bold)
            Text(case.occurredDate+" "+case.occurredTime+" · "+
                IncidentRules.categoryName(case.category),
                style=MaterialTheme.typography.bodySmall)
            Text(case.typeLabelSnapshot+" · Detectado en "+case.detectedArea,
                style=MaterialTheme.typography.bodySmall)
            Text(IncidentRules.statusName(case.status)+" · "+
                IncidentRules.priorityName(case.priority),
                color=if(case.priority in setOf("alta","critica") &&
                    IncidentRules.isOpen(case))MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary)
            OutlinedButton(onClick={onOpen(case.id)}) {Text("Ver y dar seguimiento")}
        }
    }
}

@Composable
private fun IncidentNewDialog(types:List<IncidentTypeEntity>,people:List<StaffEntity>,
    onDismiss:()->Unit,onSubmit:(OfflineIncidentRepository.NewIncident)->Unit) {
    var date by remember {mutableStateOf(LocalDate.now().toString())}
    var time by remember {mutableStateOf(LocalTime.now().format(
        DateTimeFormatter.ofPattern("HH:mm")))}
    var category by remember {mutableStateOf(IncidentRules.PRODUCTO)}
    var detected by remember {mutableStateOf("Cocina")}
    var probable by remember {mutableStateOf("")}
    var product by remember {mutableStateOf("")}
    var typeId by remember {mutableStateOf("")}
    var otherType by remember {mutableStateOf("")}
    var description by remember {mutableStateOf("")}
    var priority by remember {mutableStateOf("media")}
    var reporter by remember {mutableStateOf("")}
    var impact by remember {mutableStateOf("No determinado")}
    var immediate by remember {mutableStateOf("")}
    var staffId by remember {mutableStateOf("")}
    val available=types.filter{it.isActive&&it.category==category}
    val valid=IncidentRules.validDateTime(date,time) &&
        product.trim().length in 2..140 && description.trim().length in 10..1500 &&
        immediate.trim().length in 5..850 && reporter.trim().length in 2..90 &&
        (typeId.isNotBlank()&&available.any{it.id==typeId} ||
            typeId.isBlank()&&otherType.trim().length in 3..120)
    AlertDialog(onDismissRequest=onDismiss,
        title={Text("Nuevo incidente")},
        text={
            Column(Modifier.heightIn(max=560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(9.dp)) {
                Text("Ante un riesgo sanitario, retén el producto y comunica el problema " +
                    "de inmediato. El registro no reemplaza el procedimiento de seguridad.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.error)
                OutlinedTextField(date,{date=it.take(10)},label={Text("Fecha · AAAA-MM-DD")},
                    modifier=Modifier.fillMaxWidth())
                OutlinedTextField(time,{time=it.take(5)},label={Text("Hora · HH:mm")},
                    modifier=Modifier.fillMaxWidth())
                IncidentChoiceRow("Categoría",listOf(
                    IncidentRules.PRODUCTO to "Alimentos y bebidas",
                    IncidentRules.OPERATIVO to "Operaciones y servicio"
                ),category,{category=it;typeId="";otherType=""})
                IncidentChoiceRow("Detectado en",IncidentRules.AREAS.map{it to it},
                    detected,{detected=it})
                IncidentChoiceRow("Origen probable (no confirmado)",
                    listOf("" to "Sin determinar")+IncidentRules.AREAS.map{it to it},
                    probable,{probable=it})
                OutlinedTextField(product,{product=it.take(140)},
                    label={Text("Producto o proceso afectado *")},
                    modifier=Modifier.fillMaxWidth())
                IncidentChoiceRow("Tipo de incidente *",
                    listOf("" to "Otro (especificar)")+
                        available.map{it.id to it.label},typeId,{typeId=it})
                if(typeId.isBlank()) OutlinedTextField(otherType,{
                    otherType=it.take(120)},label={Text("Especifica el tipo *")},
                    modifier=Modifier.fillMaxWidth())
                OutlinedTextField(description,{description=it.take(1500)},
                    label={Text("Hechos observados, sin atribuir culpas *")},
                    minLines=3,modifier=Modifier.fillMaxWidth())
                IncidentChoiceRow("Prioridad",listOf(
                    "baja" to "Baja","media" to "Media",
                    "alta" to "Alta","critica" to "Crítica"
                ),priority,{priority=it})
                OutlinedTextField(reporter,{reporter=it.take(90)},
                    label={Text("Registrado por *")},modifier=Modifier.fillMaxWidth())
                IncidentChoiceRow("Impacto",IncidentRules.IMPACTS.map{it to it},
                    impact,{impact=it})
                OutlinedTextField(immediate,{immediate=it.take(850)},
                    label={Text("Medida inmediata adoptada *")},
                    minLines=2,modifier=Modifier.fillMaxWidth())
                IncidentChoiceRow("Responsable del seguimiento",
                    listOf("" to "Sin asignar")+
                        people.filter {it.isActive}.map{it.id to it.fullName},
                    staffId,{staffId=it})
                Text("El origen definitivo solo se atribuye después de verificarlo.",
                    style=MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton={
            Button(enabled=valid,onClick={
                onSubmit(OfflineIncidentRepository.NewIncident(
                    date,time,category,detected,probable,product,typeId,otherType,
                    description,priority,reporter,impact,immediate,staffId
                ))
            }) {Text("Registrar incidente")}
        },
        dismissButton={TextButton(onClick=onDismiss){Text("Cancelar")}}
    )
}

@Composable
private fun IncidentDetailsDialog(case:IncidentEntity,actions:List<IncidentActionEntity>,
    onDismiss:()->Unit,onFollowup:()->Unit,onVoid:()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,
        title={Text(case.code+" · Detalle")},
        text={
            Column(Modifier.heightIn(max=560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(case.productOrProcess,fontWeight=FontWeight.Bold,
                    style=MaterialTheme.typography.titleMedium)
                Text(IncidentRules.statusName(case.status)+" · "+
                    IncidentRules.priorityName(case.priority),
                    color=if(case.priority in setOf("alta","critica"))
                        MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary)
                Text("Fecha: "+case.occurredDate+" · "+case.occurredTime)
                Text("Categoría: "+IncidentRules.categoryName(case.category))
                Text("Tipo: "+case.typeLabelSnapshot)
                Text("Área de detección: "+case.detectedArea)
                Text("Origen probable: "+(case.originArea.ifBlank{"Sin determinar"}))
                Text("Origen confirmado: "+(case.confirmedOrigin.ifBlank{"No verificado"}))
                Text("Descripción: "+case.description)
                Text("Impacto: "+case.impact)
                Text("Reportado por: "+case.reporter)
                Text("Medida inmediata: "+case.immediateAction)
                Text("Seguimiento: "+case.assignedStaffNameSnapshot.ifBlank{"Sin asignar"})
                if(case.status==IncidentRules.CERRADO)
                    Text("Cerrado por: "+case.closedBy)
                if(case.status==IncidentRules.ANULADO)
                    Text("Motivo de anulación: "+case.voidReason,
                        color=MaterialTheme.colorScheme.error)
                Text("Historial de acciones ("+actions.size+")",
                    fontWeight=FontWeight.Bold)
                actions.sortedByDescending{it.atMillis}.forEach{a->
                    Card {
                        Column(Modifier.padding(10.dp),
                            verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(IncidentRules.actionName(a.kind),
                                fontWeight=FontWeight.Bold)
                            Text(a.description,style=MaterialTheme.typography.bodySmall)
                            Text("Por "+a.actor+" · "+
                                IncidentRules.statusName(a.statusBefore)+" → "+
                                IncidentRules.statusName(a.statusAfter),
                                style=MaterialTheme.typography.bodySmall)
                            if(a.confirmedOrigin.isNotBlank())
                                Text("Origen verificado: "+a.confirmedOrigin,
                                    style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton={
            Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
                if(case.status!=IncidentRules.ANULADO) {
                    Button(onClick=onFollowup) {
                        Text(if(case.status==IncidentRules.CERRADO)
                            "Reabrir caso" else "Registrar seguimiento")
                    }
                    if(IncidentRules.isOpen(case))
                        TextButton(onClick=onVoid){Text("Anular")}
                }
            }
        },
        dismissButton={TextButton(onClick=onDismiss){Text("Cerrar")}}
    )
}

@Composable
private fun IncidentFollowupDialog(
    case:IncidentEntity,staff:List<StaffEntity>,
    onDismiss:()->Unit,onSubmit:(OfflineIncidentRepository.FollowUp)->Unit
) {
    var status by remember(case.id){mutableStateOf(
        if(case.status==IncidentRules.CERRADO)
            IncidentRules.EN_ATENCION else case.status
    )}
    var kind by remember(case.id){mutableStateOf("correccion")}
    var description by remember(case.id){mutableStateOf("")}
    var actor by remember(case.id){mutableStateOf("")}
    var confirmed by remember(case.id){mutableStateOf("")}
    var assignee by remember(case.id){mutableStateOf(case.assignedStaffId)}
    var confirmClose by remember{mutableStateOf(false)}
    val choices=if(case.status==IncidentRules.CERRADO)
        listOf(IncidentRules.REGISTRADO,IncidentRules.EN_ATENCION,IncidentRules.VERIFICACION)
    else listOf(IncidentRules.REGISTRADO,IncidentRules.EN_ATENCION,
        IncidentRules.VERIFICACION,IncidentRules.CERRADO)
    val valid=description.trim().length in 5..1500 && actor.trim().length in 2..90 &&
        (confirmed.isBlank()||kind=="verificacion") &&
        IncidentRules.canClose(case.status,status,kind)
    val data=OfflineIncidentRepository.FollowUp(
        case.id,status,kind,description,actor,confirmed,assignee
    )
    AlertDialog(onDismissRequest=onDismiss,
        title={Text(if(case.status==IncidentRules.CERRADO)
            "Reabrir "+case.code else "Seguimiento "+case.code)},
        text={
            Column(Modifier.heightIn(max=540.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(9.dp)) {
                Text(case.productOrProcess+" · "+IncidentRules.statusName(case.status))
                IncidentChoiceRow("Nuevo estado",choices.map{
                    it to IncidentRules.statusName(it)
                },status,{status=it;if(status==IncidentRules.CERRADO)kind="verificacion"})
                IncidentChoiceRow("Tipo de acción",IncidentRules.ACTIONS.map{
                    it to IncidentRules.actionName(it)
                },kind,{kind=it;if(kind!="verificacion")confirmed=""})
                OutlinedTextField(description,{description=it.take(1500)},
                    label={Text("Medida, resultado o verificación *")},
                    modifier=Modifier.fillMaxWidth(),minLines=3)
                OutlinedTextField(actor,{actor=it.take(90)},
                    label={Text("Ejecutado o verificado por *")},
                    modifier=Modifier.fillMaxWidth())
                if(kind=="verificacion")
                    IncidentChoiceRow("Origen confirmado (solo si se verificó)",
                        listOf("" to "Sin confirmar")+
                            IncidentRules.AREAS.map{it to it},
                        confirmed,{confirmed=it})
                IncidentChoiceRow("Responsable de seguimiento",
                    listOf("" to "Sin asignar")+
                        staff.filter{it.isActive||it.id==case.assignedStaffId}
                            .map{it.id to it.fullName},
                    assignee,{assignee=it})
                if(status==IncidentRules.CERRADO)
                    Text("Cerrar requiere verificación de la solución y responsable identificado.",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.error)
                if(case.status==IncidentRules.CERRADO)
                    Text("La reapertura quedará registrada sin borrar el cierre anterior.",
                        style=MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton={
            Button(enabled=valid,onClick={
                if(status==IncidentRules.CERRADO) confirmClose=true
                else onSubmit(data)
            }){Text(if(status==IncidentRules.CERRADO)"Verificar y cerrar" else "Guardar acción")}
        },
        dismissButton={TextButton(onClick=onDismiss){Text("Cancelar")}}
    )
    if(confirmClose) AlertDialog(onDismissRequest={confirmClose=false},
        title={Text("Confirmar cierre verificado")},
        text={Text("¿Confirmas que la solución fue verificada y que "+actor.trim()+
            " es responsable de esa verificación?")},
        confirmButton={
            Button(onClick={confirmClose=false;onSubmit(data)}){Text("Confirmar cierre")}
        },
        dismissButton={TextButton(onClick={confirmClose=false}){Text("Cancelar")}}
    )
}

@Composable
private fun IncidentVoidDialog(case:IncidentEntity,onDismiss:()->Unit,
    onSubmit:(String,String)->Unit) {
    var reason by remember{mutableStateOf("")}
    var actor by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,
        title={Text("Anular "+case.code)},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("El incidente no se elimina. Su historial seguirá disponible.")
                OutlinedTextField(reason,{reason=it.take(500)},
                    label={Text("Motivo de anulación *")},modifier=Modifier.fillMaxWidth())
                OutlinedTextField(actor,{actor=it.take(90)},
                    label={Text("Responsable *")},modifier=Modifier.fillMaxWidth())
            }
        },
        confirmButton={
            Button(enabled=reason.trim().length>=5 && actor.trim().length>=2,
                onClick={onSubmit(reason,actor)}){Text("Anular caso")}
        },
        dismissButton={TextButton(onClick=onDismiss){Text("Cancelar")}}
    )
}
