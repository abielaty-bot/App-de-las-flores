package pe.lasflores.controlinterno.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import pe.lasflores.controlinterno.data.UniformInspectionEntity
import pe.lasflores.controlinterno.domain.UniformDaySnapshot
import pe.lasflores.controlinterno.domain.StaffScheduleRules
import pe.lasflores.controlinterno.domain.UniformRequirement
import pe.lasflores.controlinterno.domain.UniformRules

private data class WorkerInspection(val staff:StaffEntity,val inspection:UniformInspectionEntity?,
    val requirements:List<UniformRequirement>,val answers:Map<String,UniformAnswerEntity>,
    val status:String,val scheduled:Boolean) {
    val applicableCount get()=requirements.count{it.applicable && answers[it.id]?.status!=UniformRules.NA}
    val reviewed get()=requirements.count{it.applicable && UniformRules.evaluated(answers[it.id]?.status)}
    val exempt get()=requirements.count{!it.applicable || answers[it.id]?.status==UniformRules.NA}
    val ok get()=requirements.count{it.applicable && answers[it.id]?.status==UniformRules.OK}
    val bad get()=requirements.count{it.applicable && answers[it.id]?.status==UniformRules.BAD}
    val profile get()=inspection?.uniformProfileSnapshot?:staff.uniformProfile
    val complete get()=reviewed==applicableCount && applicableCount>0
    val pending get()=inspection?.workflow!="FINALIZED"
}

@Composable
fun UniformInspectionScreen(people: List<StaffEntity>, vm: UniformViewModel, staffVm: StaffViewModel) {
    val dateIso by vm.dateIso.collectAsState()
    val snapshot by vm.day.collectAsState()
    val saved by vm.saveStatus.collectAsState()
    val validSnapshot = if (snapshot.dateIso == dateIso) snapshot else UniformDaySnapshot(dateIso)
    val catalog by vm.catalog.collectAsState()
    val isNoReview = validSnapshot.activity?.status == "NO_REALIZADA"
    var expandedId by rememberSaveable(dateIso) { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    var hideExempt by rememberSaveable { mutableStateOf(false) }
    var dialog by remember(dateIso) { mutableStateOf("") }
    var dialogStaff by remember(dateIso) { mutableStateOf("") }
    var dialogRequirement by remember(dateIso) { mutableStateOf("") }
    var verifier by rememberSaveable { mutableStateOf("") }
    var requiredQty by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }
    val vacations by staffVm.vacations.collectAsState()
    val overrides by staffVm.overrides.collectAsState()
    val busy=saved=="Guardando…" || snapshot.dateIso!=dateIso
    val rows=people.mapNotNull{staff->
        val inspection=validSnapshot.inspectionFor(staff.id)
        val scheduled=StaffScheduleRules.isScheduled(StaffScheduleRules.status(staff,dateIso,vacations,overrides))
        if(!scheduled && inspection==null)return@mapNotNull null
        val requirements=if(inspection!=null)validSnapshot.requirementsFor(inspection.id)
            else catalog.filter{it.appliesTo(staff.role)}.map{
                UniformRequirement(it.id,it.label,it.category,it.minQty,it.appliesToProfile(staff.uniformProfile))
            }
        val answers=if(inspection==null)emptyMap() else validSnapshot.answers.filter{it.inspectionId==inspection.id}.associateBy{it.requirementId}
        WorkerInspection(staff,inspection,requirements,answers,inspection?.let(validSnapshot::statusFor)?:"Sin revisar",scheduled)
    }
    val visible=rows.filter{row->
        row.staff.fullName.contains(query,true) && when(filter){
            "pending"->row.pending
            "observed"->row.inspection?.initialResult==UniformRules.OBSERVADO
            "ok"->row.inspection?.initialResult==UniformRules.CONFORME
            else->true
        }
    }
    fun selectNext(row:WorkerInspection){
        val index=visible.indexOfFirst{it.staff.id==row.staff.id}
        expandedId=visible.drop(index+1).firstOrNull{it.pending}?.staff?.id
            ?:visible.getOrNull(index+1)?.staff?.id.orEmpty()
    }
    val showChecklist:@Composable (WorkerInspection)->Unit = { row ->
        UniformChecklist(row,hideExempt,busy||isNoReview,
            onAnswer={req,value->
                dialogStaff=row.staff.id;dialogRequirement=req.id
                when {
                    value==UniformRules.NA && row.answers[req.id]?.status!=UniformRules.NA -> {reason="";dialog="NA"}
                    value==UniformRules.OK && req.minQty>0 && row.answers[req.id]?.status!=UniformRules.OK -> {requiredQty="";dialog="QUANTITY"}
                    else -> vm.choose(row.staff.id,req.id,value,
                        if(value==UniformRules.OK && req.minQty>0)row.answers[req.id]?.quantity else null)
                }
            },
            onCorrect={req->dialogStaff=row.staff.id;dialogRequirement=req.id;verifier="";reason="";dialog="CORRECT"},
            onRectify={req->dialogStaff=row.staff.id;dialogRequirement=req.id;verifier="";reason="";dialog="RECTIFY"},
            onFinalize={dialogStaff=row.staff.id;dialog="FINALIZE"},
            onNext={selectNext(row)},onClose={expandedId=""})
    }
    val controls:@Composable ()->Unit = {
        Column(verticalArrangement=Arrangement.spacedBy(7.dp)){
            Text("Control de uniformes",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            DayControls(dateIso,{vm.changeDay(-1)},{vm.changeDay(1)},vm::today,vm::setDate)
            Text(if(ErrorReportText.isError(saved))ErrorReportText.compact(saved) else saved,
                color=if(saved.startsWith("Error"))MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style=MaterialTheme.typography.labelSmall)
            Text("Programados: "+rows.count{it.scheduled}+" · Revisiones finalizadas: "+rows.count{!it.pending}+
                " · Pendientes: "+rows.count{it.scheduled&&it.pending},style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(value=query,onValueChange={query=it.take(100)},singleLine=true,
                label={Text("Buscar trabajador")},modifier=Modifier.fillMaxWidth())
            LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                items(listOf("all" to "Todos","pending" to "Pendientes","observed" to "Observados","ok" to "Conformes")){(code,label)->
                    FilterChip(selected=filter==code,onClick={filter=code},label={Text(label)})
                }
            }
            FilterChip(selected=hideExempt,onClick={hideExempt=!hideExempt},label={Text(if(hideExempt)"No aplica ocultos" else "Ocultar No aplica")})
            if(isNoReview){
                Text("No se realizó revisión · "+validSnapshot.activity?.reason.orEmpty())
                OutlinedButton(onClick=vm::reopenDay,enabled=!busy){Text("Reabrir jornada")}
            }else if(validSnapshot.inspections.isEmpty()){
                TextButton(onClick={reason="";dialog="NO_REVIEW"},enabled=!busy){Text("Hoy no se realizó revisión")}
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()){
        val split=maxWidth>=820.dp && maxWidth>maxHeight
        if(split){
            Row(Modifier.fillMaxSize().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                LazyColumn(Modifier.width(310.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    item{controls()}
                    items(visible,key={it.staff.id}){row->
                        WorkerSummary(row,expandedId==row.staff.id,!isNoReview){expandedId=row.staff.id}
                    }
                    if(visible.isEmpty())item{Text("No hay trabajadores con este filtro.")}
                }
                val selected=visible.firstOrNull{it.staff.id==expandedId}?:visible.firstOrNull()
                LazyColumn(Modifier.weight(1f).fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    if(selected!=null)item{showChecklist(selected)}
                    else item{Text("Selecciona un trabajador.")}
                }
            }
        }else{
            LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(12.dp),
                verticalArrangement=Arrangement.spacedBy(8.dp)){
                item{controls()}
                items(visible,key={it.staff.id}){row->
                    Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
                        WorkerSummary(row,expandedId==row.staff.id,!isNoReview){
                            expandedId=if(expandedId==row.staff.id)"" else row.staff.id
                        }
                        if(expandedId==row.staff.id && !isNoReview)showChecklist(row)
                    }
                }
                if(visible.isEmpty())item{Text("No hay trabajadores con este filtro.")}
            }
        }
    }
    if (dialog.isNotEmpty()) {
        val action = dialog
        AlertDialog(onDismissRequest = { dialog = "" },
            title = { Text(when(action) {
                "NO_REVIEW" -> "No se realizó revisión"
                "QUANTITY" -> "Verificar cantidad mínima"
                "FINALIZE" -> "Finalizar inspección"
                "CORRECT" -> "Verificar corrección"
                "NA" -> "Justificar No aplica"
                else -> "Rectificar error de marcación"
            }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when(action) {
                        "NO_REVIEW" -> Text("No podrás declararlo si ya existen inspecciones iniciadas.")
                        "QUANTITY" -> Text("Introduce la cantidad comprobada antes de marcar Cumple.")
                        "FINALIZE" -> Text("Se guardará el resultado inicial. Después no podrás desmarcar las faltas sin dejar constancia.")
                        "CORRECT" -> Text("El trabajador solucionó una falta real. El resultado inicial continuará siendo Observado.")
                        "RECTIFY" -> Text("El requisito estaba mal marcado. Esta rectificación conservará un evento de auditoría.")
                        "NA" -> Text("Este requisito normalmente corresponde. Indica por qué no aplica en esta inspección. Quedará en auditoría y no contará como cumplimiento.")
                    }
                    if (action == "QUANTITY") {
                        OutlinedTextField(value = requiredQty, onValueChange = {
                            requiredQty = it.filter(Char::isDigit).take(3)
                        }, label = { Text("Cantidad encontrada") }, singleLine = true)
                    }
                    if (action == "CORRECT" || action == "RECTIFY") {
                        OutlinedTextField(value = verifier, onValueChange = { verifier = it.take(90) },
                            label = { Text("Verificado por") }, singleLine = true)
                    }
                    if (action == "NO_REVIEW" || action == "RECTIFY" || action=="NA") {
                        OutlinedTextField(value = reason, onValueChange = { reason = it.take(250) },
                            label = { Text("Motivo") }, minLines = 2)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    when(action) {
                        "NO_REVIEW" -> vm.markNotPerformed(reason)
                        "QUANTITY" -> vm.choose(dialogStaff, dialogRequirement, UniformRules.OK, requiredQty.toIntOrNull())
                        "FINALIZE" -> vm.finalize(dialogStaff)
                        "CORRECT" -> vm.correct(dialogStaff, dialogRequirement, verifier)
                        "RECTIFY" -> vm.rectify(dialogStaff, dialogRequirement, verifier, reason)
                        "NA" -> vm.choose(dialogStaff,dialogRequirement,UniformRules.NA,exemptionReason=reason)
                    }
                    dialog = ""
                }, enabled = when(action) {
                    "NO_REVIEW" -> reason.trim().length >= 3
                    "QUANTITY" -> (requiredQty.toIntOrNull() ?: 0) in 1..100
                    "CORRECT" -> verifier.trim().length >= 2
                    "RECTIFY" -> verifier.trim().length >= 2 && reason.trim().length >= 5
                    "NA" -> reason.trim().length >= 5
                    else -> true
                }) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Cancelar") } }
        )
    }
}


@Composable
private fun statusColor(status:String?):Color = semanticForeground(when(status){
    "Conforme","Cumple"->"success"
    "Observado","No cumple"->"danger"
    "Corregido","Rectificado"->"corrected"
    "Corrección parcial"->"warning"
    else->"neutral"
})

@Composable
private fun WorkerSummary(row:WorkerInspection,selected:Boolean,enabled:Boolean,onSelect:()->Unit){
    val shape=RoundedCornerShape(14.dp)
    Card(Modifier.fillMaxWidth().then(if(selected)Modifier.border(1.5.dp,MaterialTheme.colorScheme.primary,shape)else Modifier),
        shape=shape,colors=CardDefaults.cardColors(containerColor=if(selected)MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)){
        Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)){
                Text(row.staff.fullName,fontWeight=FontWeight.Bold)
                Text(row.staff.role+" · "+row.reviewed+"/"+row.applicableCount+" revisados",style=MaterialTheme.typography.bodySmall)
                Text(row.status,color=statusColor(row.status),style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.SemiBold)
                if(row.exempt>0)Text(row.exempt.toString()+" no aplica",style=MaterialTheme.typography.labelSmall)
                if(!row.scheduled)Text("Registro histórico",style=MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(onClick=onSelect,enabled=enabled){Text(if(selected)"Ocultar" else "Requisitos")}
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UniformChecklist(row:WorkerInspection,hideExempt:Boolean,busy:Boolean,
    onAnswer:(UniformRequirement,String)->Unit,onCorrect:(UniformRequirement)->Unit,
    onRectify:(UniformRequirement)->Unit,onFinalize:()->Unit,onNext:()->Unit,onClose:()->Unit){
    val finalized=row.inspection?.workflow=="FINALIZED"
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Text(row.staff.fullName,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                    Text(row.staff.role+" · "+row.status,style=MaterialTheme.typography.bodySmall)
                    Text("Perfil: "+StaffScheduleRules.profileLabel(row.profile),style=MaterialTheme.typography.labelMedium,
                        color=MaterialTheme.colorScheme.tertiary)
                }
                TextButton(onClick=onClose){Text("Cerrar")}
            }
            FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Text(row.ok.toString()+" cumplen",color=semanticForeground("success"),style=MaterialTheme.typography.labelMedium)
                Text(row.bad.toString()+" incumplen",color=semanticForeground("danger"),style=MaterialTheme.typography.labelMedium)
                Text(row.exempt.toString()+" no aplica",style=MaterialTheme.typography.labelMedium)
            }
            Text("Revisados: "+row.reviewed+"/"+row.applicableCount+
                if(row.reviewed>0)" · Cumplimiento evaluado: "+(100*row.ok/row.reviewed)+"%" else " · Sin evaluaciones",
                style=MaterialTheme.typography.bodySmall)
            if(row.profile=="SIN_DEFINIR")Text("Selecciona el perfil en Personal. Los requisitos ya congelados no cambian.",
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.tertiary)
            HorizontalDivider()
            row.requirements.filter{!hideExempt || (it.applicable && row.answers[it.id]?.status!=UniformRules.NA)}.forEach{req->
                RequirementBox(req,row.answers[req.id],finalized,busy||(!row.scheduled&&!finalized),
                    onAnswer={onAnswer(req,it)},onCorrect={onCorrect(req)},onRectify={onRectify(req)})
            }
            if(row.requirements.isEmpty())Text("No hay requisitos configurados para este puesto.")
            if(hideExempt && row.exempt>0)Text(row.exempt.toString()+" requisitos No aplica ocultos.",style=MaterialTheme.typography.bodySmall)
            if(row.inspection?.workflow=="DRAFT"){
                Button(onClick=onFinalize,enabled=row.complete&&!busy,modifier=Modifier.fillMaxWidth()){
                    Text(if(row.complete)"Finalizar revisión" else if(row.applicableCount==0)"Sin requisitos evaluables" else "Faltan "+(row.applicableCount-row.reviewed)+" requisitos")
                }
            }
            Text("No aplica no es cumplimiento ni incumplimiento. Los requisitos automáticos y las excepciones quedan separados del resultado.",style=MaterialTheme.typography.bodySmall)
            if(finalized)Text("Resultado inicial cerrado; se conserva al corregir o rectificar.",style=MaterialTheme.typography.bodySmall)
            else Text("Toca de nuevo una respuesta para desmarcarla. Guardado automático.",style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick=onNext,modifier=Modifier.fillMaxWidth()){Text("Siguiente trabajador")}
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RequirementBox(requirement:UniformRequirement,answer:UniformAnswerEntity?,
    finalized:Boolean,busy:Boolean,onAnswer:(String)->Unit,onCorrect:()->Unit,onRectify:()->Unit){
    val auto=!requirement.applicable
    val status=when{
        auto || answer?.status==UniformRules.NA -> "No aplica"
        answer==null -> "Sin revisar"
        answer.status==UniformRules.OK -> "Cumple"
        answer.correctedAtMillis!=null -> "Corregido"
        else -> "No cumple"
    }
    val category=when(status){"Cumple"->"success";"No cumple"->"danger";"Corregido"->"corrected";else->"neutral"}
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(10.dp),
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)){
        Column(Modifier.padding(horizontal=10.dp,vertical=5.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){
                Column(Modifier.weight(1f)){
                    Text(requirement.label,style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.SemiBold)
                    Text((if(requirement.minQty>0)"Mínimo: "+requirement.minQty+" · " else "")+
                        (if(auto)"No aplica al perfil" else status),
                        style=MaterialTheme.typography.labelSmall,color=statusColor(status))
                }
                if(auto){
                    FilterChip(selected=true,onClick={},enabled=false,label={Text("✓ No aplica")})
                }else{
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)){
                        listOf(UniformRules.OK to "Sí",UniformRules.BAD to "No",UniformRules.NA to "No aplica").forEach{(code,label)->
                            val semantic=when(code){UniformRules.OK->"success";UniformRules.BAD->"danger";else->"neutral"}
                            FilterChip(selected=answer?.status==code,onClick={onAnswer(code)},enabled=!finalized&&!busy,
                                label={Text(label)},colors=FilterChipDefaults.filterChipColors(
                                    selectedContainerColor=semanticBackground(semantic),selectedLabelColor=semanticForeground(semantic)))
                        }
                    }
                }
            }
            if(finalized && answer?.status==UniformRules.BAD && answer.correctedAtMillis==null){
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    OutlinedButton(onClick=onCorrect,enabled=!busy){Text("Verificar corrección")}
                    TextButton(onClick=onRectify,enabled=!busy){Text("Rectificar error")}
                }
            }else if(answer?.correctedBy!=null)Text("Verificado por "+answer.correctedBy,style=MaterialTheme.typography.labelSmall,color=semanticForeground("success"))
        }
    }
}
