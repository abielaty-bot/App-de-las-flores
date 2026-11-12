package pe.lasflores.controlinterno.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource
import pe.lasflores.controlinterno.R
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import pe.lasflores.controlinterno.domain.UniformRoleRules
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Checkbox
import androidx.compose.ui.Alignment
import pe.lasflores.controlinterno.domain.UniformRules
import pe.lasflores.controlinterno.domain.StaffScheduleRules
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
import pe.lasflores.controlinterno.data.MaterialDeliveryEntity
import pe.lasflores.controlinterno.data.StaffEntity
import pe.lasflores.controlinterno.data.UniformRequirementEntity
import pe.lasflores.controlinterno.domain.STAFF_ROLES
import pe.lasflores.controlinterno.domain.MaterialRules
import java.time.LocalDate

@Composable
fun UniformScreen(people: List<StaffEntity>, vm: UniformViewModel, staffVm: StaffViewModel) {
    var section by rememberSaveable { mutableStateOf("inspecciones") }
    Column(Modifier.fillMaxSize()) {
        SectionControls(
            listOf(
                "inspecciones" to "Inspecciones",
                "requisitos" to "Configurar requisitos",
                "historial" to "Historial",
                "entregas" to "Entregas de materiales"
            ),
            selected = section,
            onSelect = { section = it }
        )
        when(section) {
            "requisitos" -> RequirementsAdmin(vm)
            "historial" -> UniformHistoryAdmin(people, vm)
            "entregas" -> MaterialsAdmin(people, vm)
            else -> UniformInspectionScreen(people, vm, staffVm)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RequirementsAdmin(vm: UniformViewModel) {
    val catalog by vm.catalog.collectAsState()
    val message by vm.saveStatus.collectAsState()
    val busy=message=="Guardando…"
    var editing by remember { mutableStateOf<UniformRequirementEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var proposal by remember { mutableStateOf(false) }
    var viewRequirement by remember { mutableStateOf<UniformRequirementEntity?>(null) }
    var role by rememberSaveable { mutableStateOf("Mozo") }
    var query by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("active") }
    val deletion by vm.requirementDeletion.collectAsState()
    val roleRows=catalog.filter{role=="all"||UniformRoleRules.assigned(it,role)}
    val visible=roleRows.filter{it.label.contains(query,true)&&(status=="all"||(status=="active")==it.isActive)}
    val groups=listOf("Uniforme","Presentación","Implementos","Otro")
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
        item{
            Text("Requisitos de uniformes",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            Text("Un catálogo compartido por puestos. Los controles ya iniciados conservan sus requisitos originales.",style=MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                (listOf("Mozo","Caja","Runner","all")+STAFF_ROLES.filter{it !in setOf("Mozo","Caja","Runner")}).distinct().forEach{r->
                    FilterChip(selected=role==r,onClick={role=r;query="";status="active"},
                        label={Text(if(r=="all")"Todos los puestos" else UniformRoleRules.label(r))})
                }
            }
        }
        item{
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(9.dp)){
                listOf(Triple("Activos",roleRows.count{it.isActive},"success"),Triple("Inactivos",roleRows.count{!it.isActive},"neutral")).forEach{(label,count,kind)->
                    Card(Modifier.weight(1f),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),shape=RoundedCornerShape(13.dp)){
                        Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(2.dp)){
                            Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(count.toString(),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,color=semanticForeground(kind))
                        }
                    }
                }
            }
            if(message!="Guardado automático activo")Text(message,style=MaterialTheme.typography.labelSmall,color=if(message.startsWith("Error"))MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Button(onClick={adding=true},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("+ Agregar requisito")}
            if(role in UniformRoleRules.PROPOSAL_ROLES)OutlinedButton(onClick={proposal=true},enabled=!busy&&catalog.isNotEmpty(),modifier=Modifier.fillMaxWidth()){Text("Aplicar propuesta de "+UniformRoleRules.label(role))}
            if(roleRows.none{it.isActive})Text("Este puesto no tiene requisitos activos.",color=semanticForeground("warning"),style=MaterialTheme.typography.bodySmall)
        }
        item{
            OutlinedTextField(query,{query=it.take(100)},label={Text("Buscar requisito")},singleLine=true,modifier=Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                listOf("all" to "Todos","active" to "Activos","inactive" to "Desactivados").forEach{(v,l)->FilterChip(selected=status==v,onClick={status=v},label={Text(l)})}
            }
        }
        groups.forEach{group->
            val rows=visible.filter{it.category==group}
            if(rows.isNotEmpty())item(key="group-"+group){
                Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(13.dp),
                    colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){
                    Column{
                        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal=13.dp,vertical=10.dp),
                            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            Icon(painterResource(R.drawable.ic_lf_uniformes),null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(20.dp))
                            Text(group,modifier=Modifier.weight(1f),fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
                            Text(rows.size.toString(),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        rows.forEachIndexed{index,req->
                            CompactRequirementRow(req,busy,onView={viewRequirement=req},onEdit={editing=req},
                                onToggle={vm.setRequirementActive(req.id,!req.isActive)},onDelete={vm.inspectRequirementDeletion(req)})
                            if(index<rows.lastIndex)HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
        if(visible.isEmpty())item{Text("No hay requisitos con estos filtros.")}
    }
    viewRequirement?.let{req->
        val current=catalog.firstOrNull{it.id==req.id}?:req
        AlertDialog(onDismissRequest={viewRequirement=null},title={Text(current.label)},
            text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("Grupo: "+current.category)
                Text(if(current.isActive)"Activo" else "Inactivo")
                if(current.minQty>0)Text("Cantidad mínima: "+current.minQty)
                Text("Puestos: "+STAFF_ROLES.filter{UniformRoleRules.assigned(current,it)}.joinToString(", "){UniformRoleRules.label(it)})
                Text("Perfiles: "+UniformRules.PROFILES.filter{current.profilesCsv.contains("|"+it+"|")}.joinToString(", "){StaffScheduleRules.profileLabel(it)})
                Text("Los cambios solo afectan revisiones futuras.",style=MaterialTheme.typography.bodySmall)
            }},
            confirmButton={Button(onClick={viewRequirement=null;editing=current}){Text("Editar")}},
            dismissButton={TextButton(onClick={viewRequirement=null}){Text("Cerrar")}})
    }
    deletion.requirement?.let{req->
        AlertDialog(onDismissRequest=vm::dismissRequirementDeletion,
            title={Text("Eliminar requisito")},
            text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(req.label,fontWeight=FontWeight.Bold)
                Text("El requisito pertenece al catálogo compartido. Eliminarlo lo retira de todos sus puestos y perfiles.")
                when{
                    deletion.checking->Text("Comprobando revisiones y auditoría…")
                    deletion.blocker!=null->Text(deletion.blocker.orEmpty(),color=MaterialTheme.colorScheme.error)
                    else->Text("No tiene registros vinculados. Esta acción elimina solamente el requisito del catálogo y no modifica ninguna revisión anterior.")
                }
            }},
            confirmButton={
                if(deletion.blocker==null)Button(enabled=!deletion.checking&&!busy,onClick=vm::confirmRequirementDeletion){Text("Eliminar definitivamente")}
                else if(req.isActive)Button(enabled=!busy,onClick={vm.setRequirementActive(req.id,false);vm.dismissRequirementDeletion()}){Text("Desactivar")}
            },
            dismissButton={TextButton(onClick=vm::dismissRequirementDeletion){Text(if(deletion.blocker==null)"Cancelar" else "Cerrar")}})
    }
    if(adding||editing!=null)RequirementDialog(editing,if(role=="all")"Mozo" else role,
        onDismiss={adding=false;editing=null},
        onSave={id,label,group,qty,roles,profiles->vm.saveRequirement(id,label,group,qty,roles,profiles);adding=false;editing=null})
    if(proposal)AlertDialog(onDismissRequest={proposal=false},title={Text("Propuesta para "+UniformRoleRules.label(role))},
        text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(7.dp)){
            Text("Agrega o comparte únicamente estos requisitos. Revisa que correspondan al uniforme real del puesto.")
            UniformRoleRules.suggestions(role).forEach{Text("• "+it.label)}
            Text("Se mantienen las personalizaciones, cantidades y estados existentes. Las variantes desactivadas o con perfiles/cantidades especiales se configuran manualmente. No modifica revisiones anteriores.",style=MaterialTheme.typography.bodySmall)
        }},confirmButton={Button(onClick={vm.applyRoleProposal(role);proposal=false}){Text("Aplicar propuesta")}},
        dismissButton={TextButton(onClick={proposal=false}){Text("Cancelar")}})
}

@Composable
private fun CompactRequirementRow(req:UniformRequirementEntity,busy:Boolean,onView:()->Unit,onEdit:()->Unit,onToggle:()->Unit,onDelete:()->Unit){
    var expanded by remember(req.id){mutableStateOf(false)}
    val roles=STAFF_ROLES.filter{UniformRoleRules.assigned(req,it)}
    Row(Modifier.fillMaxWidth().padding(start=13.dp,end=5.dp,top=6.dp,bottom=6.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)){
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
            Text(req.label,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodyLarge)
            Text((if(roles.size==STAFF_ROLES.size)"Todos los puestos" else roles.joinToString(" · "){UniformRoleRules.label(it)})+
                (if(req.minQty>0)" · Mín. "+req.minQty else ""),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(color=semanticBackground(if(req.isActive)"success" else "neutral"),shape=MaterialTheme.shapes.small){
            Text(if(req.isActive)"Activo" else "Inactivo",modifier=Modifier.padding(horizontal=8.dp,vertical=5.dp),
                style=MaterialTheme.typography.labelSmall,color=semanticForeground(if(req.isActive)"success" else "neutral"))
        }
        Box{
            IconButton(onClick={expanded=true},enabled=!busy){
                Icon(painterResource(R.drawable.ic_lf_more),contentDescription="Opciones de "+req.label)
            }
            DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}){
                DropdownMenuItem(text={Text("Ver requisito")},onClick={expanded=false;onView()})
                DropdownMenuItem(text={Text("Editar")},onClick={expanded=false;onEdit()})
                DropdownMenuItem(text={Text(if(req.isActive)"Desactivar" else "Activar")},onClick={expanded=false;onToggle()})
                DropdownMenuItem(text={Text("Eliminar",color=MaterialTheme.colorScheme.error)},onClick={expanded=false;onDelete()})
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RequirementDialog(
    existing: UniformRequirementEntity?,
    defaultRole:String,
    onDismiss: () -> Unit,
    onSave: (String?, String, String, Int, Set<String>,Set<String>) -> Unit
) {
    var label by remember(existing?.id) { mutableStateOf(existing?.label.orEmpty()) }
    var category by remember(existing?.id) { mutableStateOf(existing?.category ?: "Implementos") }
    var qty by remember(existing?.id) { mutableStateOf((existing?.minQty ?: 0).toString()) }
    var roles by remember(existing?.id) {
        mutableStateOf(STAFF_ROLES.filter {
            existing?.rolesCsv?.contains("|" + it + "|") ?: (it == defaultRole)
        }.toSet())
    }
    var profiles by remember(existing?.id){mutableStateOf(UniformRules.PROFILES.filter{existing?.profilesCsv?.contains("|"+it+"|")?:true}.toSet())}
    val groups = listOf("Uniforme", "Presentación", "Implementos", "Otro")
    val validQty = qty.toIntOrNull()
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Nuevo requisito" else "Editar requisito") },
        text = {
            Column(Modifier.heightIn(max = 490.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = label, onValueChange = { label = it.take(100) },
                    label = { Text("Nombre del requisito") }, modifier = Modifier.fillMaxWidth())
                Text("Grupo", fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(7.dp)){
                    groups.forEach { group ->
                        FilterChip(selected = category == group, onClick = { category = group },label = { Text(group) })
                    }
                }
                OutlinedTextField(value = qty, onValueChange = { qty = it.filter(Char::isDigit).take(3) },
                    label = { Text("Cantidad mínima (0 si no aplica)") }, modifier = Modifier.fillMaxWidth())
                Text("Puestos a los que corresponde", fontWeight = FontWeight.Bold)
                Row(verticalAlignment=Alignment.CenterVertically){
                    Checkbox(checked=roles.size==STAFF_ROLES.size,onCheckedChange={roles=if(it)STAFF_ROLES.toSet() else emptySet()})
                    Text("Todos los puestos")
                }
                STAFF_ROLES.forEach { role ->
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Checkbox(checked=role in roles,onCheckedChange={checked->roles=if(checked)roles+role else roles-role})
                        Text(UniformRoleRules.label(role))
                    }
                }
                Text("Perfiles de uniforme",fontWeight=FontWeight.Bold)
                UniformRules.PROFILES.forEach{profile->
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Checkbox(checked=profile in profiles,onCheckedChange={checked->profiles=if(checked)profiles+profile else profiles-profile})
                        Text(StaffScheduleRules.profileLabel(profile))
                    }
                }
                Text("Sin definir: se mantiene el requisito aplicable hasta elegir un perfil.",style=MaterialTheme.typography.bodySmall)
                Text("Los controles ya iniciados conservan su lista original de requisitos.",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(enabled = label.trim().length in 3..100 &&
                validQty != null && validQty in 0..100 && roles.isNotEmpty() && profiles.isNotEmpty(),
                onClick = { onSave(existing?.id, label, category, validQty ?: 0, roles,profiles) }) {
                Text("Guardar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun MaterialsAdmin(people:List<StaffEntity>,vm:UniformViewModel) {
    val deliveries by vm.deliveries.collectAsState()
    val workers=people.filter {it.isActive}
    var adding by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf<MaterialDeliveryEntity?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var personId by rememberSaveable { mutableStateOf("") }
    var materialFilter by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf("") }
    var fromDate by rememberSaveable { mutableStateOf("") }
    var toDate by rememberSaveable { mutableStateOf("") }
    val validRange=runCatching {
        (fromDate.isBlank() || LocalDate.parse(fromDate).toString()==fromDate) &&
        (toDate.isBlank() || LocalDate.parse(toDate).toString()==toDate) &&
        (fromDate.isBlank() || toDate.isBlank() || fromDate <= toDate)
    }.getOrDefault(false)
    val materialNames=deliveries.map {it.material}.distinct().sorted()
    val filtered=deliveries.filter {d->
        validRange && (personId.isBlank()||d.staffId==personId) &&
            (materialFilter.isBlank()||d.material==materialFilter) &&
            (statusFilter.isBlank()||d.status==statusFilter) &&
            (fromDate.isBlank()||d.dateIso>=fromDate) &&
            (toDate.isBlank()||d.dateIso<=toDate) &&
            (query.isBlank()||d.staffNameSnapshot.contains(query,true)||
                d.material.contains(query,true)||d.deliveredBy.contains(query,true))
    }
    val balances=MaterialRules.balances(deliveries).filter { row ->
        (personId.isBlank()||row.staffId==personId) &&
        (materialFilter.isBlank()||row.material==materialFilter) &&
        (query.isBlank()||row.staffName.contains(query,true)||
            row.material.contains(query,true))
    }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(14.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {
            Text("Entrega de materiales",style=MaterialTheme.typography.headlineSmall,
                fontWeight=FontWeight.Bold)
            Text("Control de entregas, devoluciones y reposiciones. El historial no se elimina.",
                style=MaterialTheme.typography.bodySmall)
            Text("Vigentes: "+deliveries.count{it.status=="ENTREGADA"}+
                " · Devueltas: "+deliveries.count{it.status=="DEVUELTA"}+
                " · Anuladas: "+deliveries.count{it.status=="ANULADA"})
            Button(enabled=workers.isNotEmpty(),onClick={adding=true}) {
                Text("+ Registrar entrega")
            }
        }
        item {
            Card {
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    Text("Filtrar historial",fontWeight=FontWeight.Bold)
                    OutlinedTextField(query,{query=it.take(80)},label={Text("Buscar trabajador, material o responsable")},
                        modifier=Modifier.fillMaxWidth(),singleLine=true)
                    Text("Trabajador",fontWeight=FontWeight.Medium)
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        item { FilterChip(selected=personId.isBlank(),onClick={personId=""},
                            label={Text("Todos")}) }
                        items(people,key={it.id}) { person ->
                            FilterChip(selected=personId==person.id,onClick={personId=person.id},
                                label={Text(person.fullName)})
                        }
                    }
                    Text("Material",fontWeight=FontWeight.Medium)
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        item { FilterChip(selected=materialFilter.isBlank(),onClick={materialFilter=""},
                            label={Text("Todos")}) }
                        items(materialNames,key={it}) { label ->
                            FilterChip(selected=materialFilter==label,onClick={materialFilter=label},
                                label={Text(label)})
                        }
                    }
                    Text("Estado",fontWeight=FontWeight.Medium)
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        items(listOf("" to "Todos","ENTREGADA" to "Vigentes",
                            "DEVUELTA" to "Devueltas","ANULADA" to "Anuladas")) { (status,label) ->
                            FilterChip(selected=statusFilter==status,onClick={statusFilter=status},
                                label={Text(label)})
                        }
                    }
                    OutlinedTextField(fromDate,{fromDate=it.take(10)},
                        label={Text("Desde · AAAA-MM-DD")},modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(toDate,{toDate=it.take(10)},
                        label={Text("Hasta · AAAA-MM-DD")},modifier=Modifier.fillMaxWidth())
                    if(!validRange)Text("Corrige el rango de fechas.",
                        color=MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick={
                        query="";personId="";materialFilter="";statusFilter=""
                        fromDate="";toDate=""
                    }) {Text("Limpiar filtros")}
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    Text("Material vigente por trabajador",fontWeight=FontWeight.Bold)
                    Text("Saldo actual de lotes entregados y no devueltos; independiente del filtro de fechas.",
                        style=MaterialTheme.typography.bodySmall)
                    Text(balances.sumOf{it.units}.toString()+" unidades vigentes en "+
                        balances.size+" combinaciones.",color=MaterialTheme.colorScheme.primary)
                    if(balances.isEmpty()) Text("No hay material vigente para estos filtros.")
                    balances.take(80).forEach { row ->
                        Text(row.staffName+" · "+row.material+" · "+row.units+
                            " unidad(es) · "+row.deliveries+" entrega(s)")
                    }
                    if(balances.size>80)Text("Hay más registros; consulta el reporte Excel completo.")
                }
            }
        }
        item { Text("Historial filtrado: "+filtered.size+" entrega(s)",
            fontWeight=FontWeight.Bold) }
        items(filtered,key={it.id}) { item ->
            Card {
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text(item.staffNameSnapshot,fontWeight=FontWeight.Bold)
                    Text(item.material+" · "+item.quantity+" unidad(es) · "+item.dateIso)
                    Text("Motivo: "+item.reason+" · Entregado por: "+item.deliveredBy,
                        style=MaterialTheme.typography.bodySmall)
                    Text(when(item.status) {
                        "ENTREGADA"->"Entrega vigente";"DEVUELTA"->"Devuelta"
                        else->"Anulada"
                    },color=MaterialTheme.colorScheme.primary)
                    if(item.closingNote.isNotBlank())Text("Observación: "+item.closingNote)
                    if(item.status=="ENTREGADA") OutlinedButton(onClick={closing=item}) {
                        Text("Devolución / anulación")
                    }
                }
            }
        }
        if(filtered.isEmpty()&&validRange) item {
            Text("No hay entregas que coincidan con los filtros.")
        }
    }
    if(adding) MaterialForm(workers,deliveries,onDismiss={adding=false},
        onSave={id,date,material,qty,reason,by->
            vm.deliver(id,date,material,qty,reason,by)
            adding=false
        })
    closing?.let { item ->
        MaterialCloseDialog(item,onDismiss={closing=null},onSave={type,note->
            vm.closeDelivery(item.id,type,note)
            closing=null
        })
    }
}

@Composable
private fun MaterialForm(
    workers:List<StaffEntity>,deliveries:List<MaterialDeliveryEntity>,
    onDismiss:()->Unit,
    onSave:(String,String,String,Int,String,String)->Unit
) {
    var staffId by remember { mutableStateOf(workers.firstOrNull()?.id.orEmpty()) }
    var dateIso by remember { mutableStateOf(LocalDate.now().toString()) }
    var material by remember { mutableStateOf("Trapo") }
    var otherMaterial by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var reason by remember { mutableStateOf("Dotación inicial") }
    var otherReason by remember { mutableStateOf("") }
    var actor by remember { mutableStateOf("") }
    var confirmRepeat by remember { mutableStateOf(false) }
    val materials=listOf("Trapo","Mandil","Camisa","Toca","Otro")
    val reasons=listOf("Dotación inicial","Reposición por desgaste",
        "Pérdida","Daño","Cambio de talla","Otro")
    val finalMaterial=if(material=="Otro")otherMaterial.trim() else material
    val finalReason=if(reason=="Otro")otherReason.trim() else reason
    val quantity=qty.toIntOrNull()
    val validDate=MaterialRules.validDay(dateIso)
    val recent=if(validDate && staffId.isNotBlank() && finalMaterial.length>=2)
        MaterialRules.recent(deliveries,staffId,finalMaterial,dateIso)
        else emptyList()
    val valid=staffId.isNotBlank() && validDate && finalMaterial.length in 2..80 &&
        quantity!=null && quantity in 1..100 && finalReason.length in 3..120 &&
        actor.trim().length in 2..120
    fun save() {if(valid)onSave(staffId,dateIso,finalMaterial,quantity?:1,finalReason,actor)}
    AlertDialog(onDismissRequest=onDismiss,
        title={Text("Registrar entrega de material")},
        text={
            Column(Modifier.heightIn(max=530.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Trabajador",fontWeight=FontWeight.Bold)
                LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    items(workers,key={it.id}) { person ->
                        FilterChip(selected=staffId==person.id,onClick={staffId=person.id},
                            label={Text(person.fullName)})
                    }
                }
                OutlinedTextField(dateIso,{dateIso=it.take(10)},
                    label={Text("Fecha de entrega · AAAA-MM-DD")},
                    modifier=Modifier.fillMaxWidth(),singleLine=true)
                if(!validDate)Text("La fecha debe ser válida y no puede ser futura.",
                    color=MaterialTheme.colorScheme.error)
                Text("Material",fontWeight=FontWeight.Bold)
                LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    items(materials) { option ->
                        FilterChip(selected=material==option,onClick={material=option},
                            label={Text(option)})
                    }
                }
                if(material=="Otro")OutlinedTextField(otherMaterial,{
                    otherMaterial=it.take(80)},label={Text("Nombre del material")},
                    modifier=Modifier.fillMaxWidth())
                OutlinedTextField(qty,{qty=it.filter(Char::isDigit).take(3)},
                    label={Text("Cantidad entregada")},modifier=Modifier.fillMaxWidth())
                Text("Motivo",fontWeight=FontWeight.Bold)
                LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    items(reasons){option->
                        FilterChip(selected=reason==option,onClick={reason=option},
                            label={Text(option)})
                    }
                }
                if(reason=="Otro")OutlinedTextField(otherReason,{otherReason=it.take(120)},
                    label={Text("Motivo específico")},modifier=Modifier.fillMaxWidth())
                OutlinedTextField(actor,{actor=it.take(120)},
                    label={Text("Responsable de la entrega")},
                    modifier=Modifier.fillMaxWidth())
                if(recent.isNotEmpty()) {
                    Text("Aviso: "+recent.size+
                        " entrega(s) del mismo material durante los últimos 30 días.",
                        color=MaterialTheme.colorScheme.error)
                    Text("Última: "+recent.first().dateIso+
                        ". Verifica si la reposición corresponde; esto no demuestra mal uso.",
                        style=MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton={
            Button(enabled=valid,onClick={
                if(recent.isNotEmpty())confirmRepeat=true else save()
            }){Text("Guardar entrega")}
        },
        dismissButton={TextButton(onClick=onDismiss){Text("Cancelar")}}
    )
    if(confirmRepeat) AlertDialog(onDismissRequest={confirmRepeat=false},
        title={Text("Confirmar reposición frecuente")},
        text={Text("Se registraron "+recent.size+" entrega(s) de "+finalMaterial+
            " al mismo trabajador durante los 30 días previos. ¿Confirmas la nueva entrega?")},
        confirmButton={
            Button(onClick={confirmRepeat=false;save()}){Text("Registrar de todas formas")}
        },
        dismissButton={TextButton(onClick={confirmRepeat=false}){Text("Revisar")}}
    )
}

@Composable
private fun MaterialCloseDialog(
    item: MaterialDeliveryEntity,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var action by remember { mutableStateOf("DEVUELTA") }
    var note by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(item.material + " · " + item.staffNameSnapshot) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("El registro se conserva en el historial.")
                FilterChip(selected = action == "DEVUELTA",
                    onClick = { action = "DEVUELTA" }, label = { Text("Devuelta") })
                FilterChip(selected = action == "ANULADA",
                    onClick = { action = "ANULADA" }, label = { Text("Anulada") })
                OutlinedTextField(value = note, onValueChange = { note = it.take(300) },
                    label = { Text("Motivo u observación") }, minLines = 2)
            }
        },
        confirmButton = {
            Button(enabled = note.trim().length >= 5,
                onClick = { onSave(action, note) }) { Text("Confirmar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
