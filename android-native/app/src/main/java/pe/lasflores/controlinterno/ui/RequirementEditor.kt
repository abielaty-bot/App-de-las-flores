package pe.lasflores.controlinterno.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pe.lasflores.controlinterno.R
import pe.lasflores.controlinterno.data.UniformRequirementEntity
import pe.lasflores.controlinterno.domain.STAFF_ROLES
import pe.lasflores.controlinterno.domain.UniformRoleRules
import pe.lasflores.controlinterno.domain.UniformRules
import pe.lasflores.controlinterno.domain.StaffScheduleRules

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RequirementEditor(existing:UniformRequirementEntity?,defaultRole:String,
    saving:Boolean,error:String,onDismiss:()->Unit,
    onSave:(String?,String,String,Int,Set<String>,Set<String>)->Unit){
    val initialLabel=existing?.label.orEmpty()
    val initialCategory=existing?.category?:"Implementos"
    val initialQty=existing?.minQty?:0
    val initialRoles=STAFF_ROLES.filter{existing?.rolesCsv?.contains("|"+it+"|")?: (it==defaultRole)}.toSet()
    val initialProfiles=UniformRules.PROFILES.filter{existing?.profilesCsv?.contains("|"+it+"|")?:true}.toSet()
    var label by rememberSaveable(existing?.id){mutableStateOf(initialLabel)}
    var category by rememberSaveable(existing?.id){mutableStateOf(initialCategory)}
    var quantityEnabled by rememberSaveable(existing?.id){mutableStateOf(initialQty>0)}
    var qty by rememberSaveable(existing?.id){mutableStateOf(if(initialQty>0)initialQty.toString() else "1")}
    var roleText by rememberSaveable(existing?.id){mutableStateOf(initialRoles.joinToString("|"))}
    var profileText by rememberSaveable(existing?.id){mutableStateOf(initialProfiles.joinToString("|"))}
    val roles=roleText.split("|").filter{it.isNotEmpty()}.toSet()
    val profiles=profileText.split("|").filter{it.isNotEmpty()}.toSet()
    fun setRoles(value:Set<String>){roleText=value.joinToString("|")}
    fun setProfiles(value:Set<String>){profileText=value.joinToString("|")}
    var confirmDiscard by rememberSaveable{mutableStateOf(false)}
    val parsedQty=if(quantityEnabled)qty.toIntOrNull() else 0
    val valid=label.trim().length in 3..100 && parsedQty!=null &&
        (if(quantityEnabled)parsedQty in 1..100 else parsedQty==0) && roles.isNotEmpty() && profiles.isNotEmpty()
    val changed=label!=initialLabel || category!=initialCategory || parsedQty!=initialQty ||
        roles!=initialRoles || profiles!=initialProfiles
    fun requestDismiss(){if(!saving){if(changed)confirmDiscard=true else onDismiss()}}
    val orderedRoles=(listOf("Mozo","Caja","Runner")+STAFF_ROLES).distinct().filter{it in STAFF_ROLES}
    Dialog(onDismissRequest={requestDismiss()},properties=DialogProperties(usePlatformDefaultWidth=false,dismissOnClickOutside=false)){
        Surface(Modifier.fillMaxSize().systemBarsPadding().imePadding(),color=MaterialTheme.colorScheme.background){
            Column(Modifier.fillMaxSize()){
                Surface(color=LasFloresPalette.Burgundy,contentColor=Color.White,shadowElevation=3.dp){
                    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        TextButton(onClick={requestDismiss()},enabled=!saving){Text("‹ Volver",color=Color.White)}
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
                            Text(if(existing==null)"Nuevo requisito" else "Editar requisito",style=MaterialTheme.typography.titleLarge,color=Color.White)
                            Text("LAS FLORES · Control de uniformes",style=MaterialTheme.typography.bodySmall,color=Color(0xFFEAD4C5))
                        }
                        Icon(painterResource(R.drawable.ic_lf_uniformes),null,tint=Color.White,modifier=Modifier.size(26.dp))
                    }
                }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(14.dp),
                    verticalArrangement=Arrangement.spacedBy(12.dp)){
                    RequirementEditorSection("1. Información del requisito",R.drawable.ic_lf_uniformes){
                        OutlinedTextField(value=label,onValueChange={label=it.take(100)},enabled=!saving,
                            label={Text("Nombre del requisito *")},placeholder={Text("Ej. Lapiceros")},singleLine=true,
                            isError=label.isNotBlank()&&label.trim().length<3,modifier=Modifier.fillMaxWidth())
                        Text("Categoría",fontWeight=FontWeight.SemiBold)
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                            listOf("Uniforme","Presentación","Implementos","Otro").forEach{group->
                                FilterChip(selected=category==group,onClick={category=group},enabled=!saving,label={Text(group)})
                            }
                        }
                    }
                    RequirementEditorSection("2. Cantidad mínima",R.drawable.ic_lf_funciones){
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                            Checkbox(checked=quantityEnabled,onCheckedChange={quantityEnabled=it;if(it&&(qty.toIntOrNull() ?: 0) !in 1..100)qty="1"},enabled=!saving)
                            Column(Modifier.weight(1f)){
                                Text("Requiere una cantidad específica",fontWeight=FontWeight.SemiBold)
                                Text("Ejemplo: mínimo 3 lapiceros.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if(quantityEnabled){
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(9.dp),verticalAlignment=Alignment.CenterVertically){
                                OutlinedButton(onClick={qty=((qty.toIntOrNull()?:1)-1).coerceAtLeast(1).toString()},enabled=!saving&&(qty.toIntOrNull()?:1)>1){Text("−")}
                                OutlinedTextField(qty,{qty=it.filter(Char::isDigit).take(3)},enabled=!saving,
                                    label={Text("Cantidad requerida")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),
                                    isError=(qty.toIntOrNull() ?: 0) !in 1..100,modifier=Modifier.weight(1f))
                                OutlinedButton(onClick={qty=((qty.toIntOrNull()?:0)+1).coerceIn(1,100).toString()},enabled=!saving&&(qty.toIntOrNull()?:0)<100){Text("+")}
                            }
                        }else Text("Solo se verifica si cumple o no cumple; sin cantidad mínima.",style=MaterialTheme.typography.bodySmall)
                    }
                    RequirementEditorSection("3. Puestos asignados",R.drawable.ic_lf_personal){
                        Text(roles.size.toString()+" de "+orderedRoles.size+" puestos seleccionados",style=MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment=Alignment.CenterVertically){
                            Checkbox(checked=roles.size==orderedRoles.size,onCheckedChange={setRoles(if(it)orderedRoles.toSet() else emptySet())},enabled=!saving)
                            Text("Todos los puestos",fontWeight=FontWeight.SemiBold)
                        }
                        BoxWithConstraints(Modifier.fillMaxWidth()){
                            val columns=if(maxWidth>=330.dp)2 else 1
                            Column(verticalArrangement=Arrangement.spacedBy(7.dp)){
                                orderedRoles.chunked(columns).forEach{pair->
                                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)){
                                        pair.forEach{role->
                                            RequirementEditorOption(UniformRoleRules.label(role),role in roles,!saving,
                                                {checked->setRoles(if(checked)roles+role else roles-role)},Modifier.weight(1f))
                                        }
                                        repeat(columns-pair.size){Spacer(Modifier.weight(1f))}
                                    }
                                }
                            }
                        }
                        if(roles.isEmpty())Text("Selecciona al menos un puesto.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                    }
                    RequirementEditorSection("4. Perfiles de uniforme",R.drawable.ic_lf_personal){
                        Text("Elige a qué perfiles se aplica.",style=MaterialTheme.typography.bodySmall)
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                            UniformRules.PROFILES.forEach{profile->
                                FilterChip(selected=profile in profiles,onClick={setProfiles(if(profile in profiles)profiles-profile else profiles+profile)},
                                    enabled=!saving,label={Text(StaffScheduleRules.profileLabel(profile))})
                            }
                        }
                        if(profiles.isEmpty())Text("Selecciona al menos un perfil.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                        Text("Sin definir: el requisito sigue aplicando hasta que se asigne un perfil al trabajador.",style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    RequirementEditorSection("Resumen del requisito",R.drawable.ic_lf_reportes){
                        Text(label.trim().ifBlank{"Sin nombre"},style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                        Text(category+" · "+(if(quantityEnabled)"Mínimo "+(parsedQty?.takeIf{it in 1..100}?.toString()?:"—")+" unidades" else "Sin cantidad mínima"))
                        Text("Puestos: "+orderedRoles.filter{it in roles}.joinToString(", "){UniformRoleRules.label(it)}.ifBlank{"Ninguno"},
                            style=MaterialTheme.typography.bodySmall)
                        Text("Perfiles: "+UniformRules.PROFILES.filter{it in profiles}.joinToString(", "){StaffScheduleRules.profileLabel(it)}.ifBlank{"Ninguno"},
                            style=MaterialTheme.typography.bodySmall)
                        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            Icon(painterResource(R.drawable.ic_lf_backups),null,tint=semanticForeground("success"),modifier=Modifier.size(22.dp))
                            Column(Modifier.weight(1f)){
                                Text("Historial protegido",color=semanticForeground("success"),fontWeight=FontWeight.Bold)
                                Text("Los cambios se aplican a nuevas inspecciones; las revisiones iniciadas mantienen su configuración original.",style=MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                Surface(color=MaterialTheme.colorScheme.surface,shadowElevation=5.dp){
                    Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                        if(!valid)Text("Completa el nombre, la cantidad válida (si aplica), los puestos y los perfiles.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically){
                            OutlinedButton(onClick={requestDismiss()},enabled=!saving){Text("Cancelar")}
                            Button(enabled=valid&&!saving,onClick={onSave(existing?.id,label,category,parsedQty?:0,roles,profiles)},
                                modifier=Modifier.weight(1f).heightIn(min=48.dp),
                                colors=ButtonDefaults.buttonColors(containerColor=LasFloresPalette.Burgundy,contentColor=Color.White)){
                                if(saving){CircularProgressIndicator(Modifier.size(17.dp),strokeWidth=2.dp);Spacer(Modifier.width(8.dp))}
                                Text(if(saving)"Guardando…" else if(existing==null)"Crear requisito" else "Guardar cambios",fontWeight=FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
    if(confirmDiscard)AlertDialog(onDismissRequest={confirmDiscard=false},title={Text("¿Descartar cambios?")},
        text={Text("Los cambios de este formulario todavía no se guardaron. El requisito anterior no se modificará.")},
        confirmButton={TextButton(onClick={confirmDiscard=false;onDismiss()}){Text("Descartar",color=MaterialTheme.colorScheme.error)}},
        dismissButton={Button(onClick={confirmDiscard=false}){Text("Seguir editando")}})
}

@Composable
private fun RequirementEditorSection(title:String,res:Int,content:@Composable ColumnScope.()->Unit){
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
        elevation=CardDefaults.cardElevation(defaultElevation=1.dp)){
        Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){
                Icon(painterResource(res),null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(22.dp))
                Text(title,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

@Composable
private fun RequirementEditorOption(title:String,selected:Boolean,enabled:Boolean,onChange:(Boolean)->Unit,modifier:Modifier){
    val shape=RoundedCornerShape(11.dp)
    Row(modifier.background(if(selected)MaterialTheme.colorScheme.primaryContainer.copy(alpha=.45f) else MaterialTheme.colorScheme.surface,shape)
        .border(1.dp,if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,shape)
        .heightIn(min=52.dp).padding(end=8.dp),verticalAlignment=Alignment.CenterVertically){
        Checkbox(checked=selected,onCheckedChange=onChange,enabled=enabled)
        Text(title,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.weight(1f))
    }
}
