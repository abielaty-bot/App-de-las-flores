package pe.lasflores.controlinterno.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pe.lasflores.controlinterno.R
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
import java.time.Instant
import java.time.ZoneId
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
    var detailsId by rememberSaveable {mutableStateOf<String?>(null)}
    var followupId by remember {mutableStateOf<String?>(null)}
    var voidId by remember {mutableStateOf<String?>(null)}
    val selected=incidents.firstOrNull{it.id==detailsId}
    BackHandler(enabled=detailsId!=null && !newDialog && followupId==null && voidId==null){detailsId=null}
    Column(Modifier.fillMaxSize()) {
        LazyRow(contentPadding=PaddingValues(horizontal=12.dp,vertical=4.dp),
            horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            items(listOf(
                "registro" to "Registro","seguimiento" to "Seguimiento",
                "analisis" to "Análisis","tipos" to "Tipos"
            )){(id,title)->
                FilterChip(selected=tab==id,onClick={tab=id;detailsId=null},label={Text(title,fontWeight=FontWeight.SemiBold)},leadingIcon={
                    Icon(painterResource(when(id){
                        "registro"->R.drawable.ic_lf_incidentes
                        "seguimiento"->R.drawable.ic_lf_updates
                        "analisis"->R.drawable.ic_lf_reportes
                        else->R.drawable.ic_lf_funciones
                    }),null,modifier=Modifier.size(19.dp))
                },colors=FilterChipDefaults.filterChipColors(selectedContainerColor=LasFloresPalette.Burgundy,
                    selectedLabelColor=Color.White,selectedLeadingIconColor=Color.White),shape=RoundedCornerShape(13.dp))
            }
        }
        if(message!="Guardado automático activo")Text(if (ErrorReportText.isError(message)) ErrorReportText.compact(message) else message,modifier=Modifier.padding(horizontal=16.dp,vertical=4.dp),
            color=if(message.startsWith("Error"))MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            style=MaterialTheme.typography.labelSmall)
        when(tab) {
            "seguimiento"->if(selected==null) IncidentHistoryPanel(incidents,
                onNew={newDialog=true},onDetails={detailsId=it})
            else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)){
                IncidentDetailCard(selected,actions.filter{it.incidentId==selected.id},
                    onDismiss={detailsId=null},onFollowup={followupId=selected.id},onVoid={voidId=selected.id})
            }
            "analisis"->IncidentAnalysisPanel(incidents)
            "tipos"->IncidentCatalogPanel(vm,types)
            else->IncidentDesignedDashboard(incidents,selected,actions.filter{it.incidentId==detailsId},
                onNew={newDialog=true},onOpen={detailsId=it},onHistory={tab="seguimiento";detailsId=null},
                onClose={detailsId=null},onFollowup={followupId=detailsId},onVoid={voidId=detailsId})
        }
    }
    if(newDialog) IncidentNewDialog(types,people,
        onDismiss={newDialog=false},
        onSubmit={vm.create(it);newDialog=false})

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
fun IncidentListCard(case:IncidentEntity,onOpen:(String)->Unit){
    IncidentDesignedListCard(case,onOpen)
}

@Composable
private fun IncidentForm(title:String,subtitle:String,valid:Boolean,onDismiss:()->Unit,onSubmit:()->Unit,
    submitLabel:String,content:@Composable androidx.compose.foundation.layout.ColumnScope.()->Unit){
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.fillMaxSize().systemBarsPadding().imePadding(),color=MaterialTheme.colorScheme.background){
            Column(Modifier.fillMaxSize()){
                Surface(color=MaterialTheme.colorScheme.surface,shadowElevation=2.dp){
                    Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically){
                        TextButton(onClick=onDismiss){Text("‹ Volver")}
                        Column(Modifier.weight(1f)){
                            Text(title,style=MaterialTheme.typography.titleLarge)
                            Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement=Arrangement.spacedBy(11.dp),content=content)
                Surface(color=MaterialTheme.colorScheme.surface,shadowElevation=5.dp){
                    Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically){
                        OutlinedButton(onClick=onDismiss){Text("Cancelar")}
                        Button(enabled=valid,onClick=onSubmit,modifier=Modifier.weight(1f).heightIn(min=48.dp)){
                            Text(submitLabel,fontWeight=FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IncidentFormSection(title:String,description:String){
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer,RoundedCornerShape(10.dp)).padding(12.dp),
        verticalArrangement=Arrangement.spacedBy(3.dp)){
        Text(title,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
        Text(description,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
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
    IncidentForm("Nuevo incidente","Completa los campos obligatorios (*)",valid,onDismiss,
        onSubmit={onSubmit(OfflineIncidentRepository.NewIncident(date,time,category,detected,probable,product,typeId,otherType,
            description,priority,reporter,impact,immediate,staffId))},submitLabel="Registrar incidente") {
                Text("Ante un riesgo sanitario, retén el producto y comunica el problema " +
                    "de inmediato. El registro no reemplaza el procedimiento de seguridad.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.error)
                IncidentFormSection("1. ¿Qué ocurrió?","Identifica el problema y cuándo ocurrió.")
                OutlinedTextField(date,{date=it.take(10)},label={Text("Fecha · AAAA-MM-DD")},
                    modifier=Modifier.fillMaxWidth())
                OutlinedTextField(time,{time=it.take(5)},label={Text("Hora · HH:mm")},
                    modifier=Modifier.fillMaxWidth())
                IncidentChoiceRow("Categoría",listOf(
                    IncidentRules.PRODUCTO to "Alimentos y bebidas",
                    IncidentRules.OPERATIVO to "Operaciones y servicio"
                ),category,{category=it;typeId="";otherType=""})
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
                IncidentFormSection("2. Impacto y detección","Dónde se detectó y cómo afectó al cliente.")
                IncidentChoiceRow("Detectado en",IncidentRules.AREAS.map{it to it},detected,{detected=it})
                IncidentChoiceRow("Origen probable (no confirmado)",listOf("" to "Sin determinar")+IncidentRules.AREAS.map{it to it},probable,{probable=it})
                IncidentChoiceRow("Prioridad",listOf(
                    "baja" to "Baja","media" to "Media",
                    "alta" to "Alta","critica" to "Crítica"
                ),priority,{priority=it})
                IncidentChoiceRow("Impacto",IncidentRules.IMPACTS.map{it to it},
                    impact,{impact=it})
                IncidentFormSection("3. Acción inmediata y responsable","Registra lo que hiciste y quién dará seguimiento.")
                OutlinedTextField(reporter,{reporter=it.take(90)},label={Text("Registrado por *")},modifier=Modifier.fillMaxWidth())
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
    IncidentForm(if(case.status==IncidentRules.CERRADO)"Reabrir "+case.code else "Seguimiento "+case.code,
        case.productOrProcess,valid,onDismiss,
        onSubmit={if(status==IncidentRules.CERRADO)confirmClose=true else onSubmit(data)},
        submitLabel=if(status==IncidentRules.CERRADO)"Verificar y cerrar" else "Guardar acción") {
                Text(case.productOrProcess+" · "+IncidentRules.statusName(case.status))
                IncidentFormSection("1. Estado y acción","Selecciona el siguiente paso del caso.")
                IncidentChoiceRow("Nuevo estado",choices.map{
                    it to IncidentRules.statusName(it)
                },status,{status=it;if(status==IncidentRules.CERRADO)kind="verificacion"})
                IncidentChoiceRow("Tipo de acción",IncidentRules.ACTIONS.map{
                    it to IncidentRules.actionName(it)
                },kind,{kind=it;if(kind!="verificacion")confirmed=""})
                IncidentFormSection("2. Resultado y responsable","Describe lo que se hizo y quién lo verificó.")
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
