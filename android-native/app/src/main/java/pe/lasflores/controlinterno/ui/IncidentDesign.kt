package pe.lasflores.controlinterno.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.lasflores.controlinterno.R
import pe.lasflores.controlinterno.data.IncidentEntity
import pe.lasflores.controlinterno.data.IncidentActionEntity
import pe.lasflores.controlinterno.domain.IncidentRules
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun IncidentDesignCard(content:@Composable ColumnScope.()->Unit){
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(17.dp),
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
        elevation=CardDefaults.cardElevation(defaultElevation=2.dp)){
        Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }
}
@Composable
private fun IncidentDesignIcon(res:Int,size:Int=22){
    Icon(painterResource(res),null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(size.dp))
}
@Composable
private fun IncidentHeading(title:String,res:Int){
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
        IncidentDesignIcon(res)
        Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
    }
}
@Composable
private fun IncidentPill(label:String,kind:String){
    val bg=if(kind=="brand")MaterialTheme.colorScheme.primaryContainer else semanticBackground(kind)
    val ink=if(kind=="brand")MaterialTheme.colorScheme.onPrimaryContainer else semanticForeground(kind)
    Text(label,modifier=Modifier.background(bg,RoundedCornerShape(50)).padding(horizontal=10.dp,vertical=6.dp),
        color=ink,style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.SemiBold)
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IncidentPills(case:IncidentEntity){
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
        IncidentPill(IncidentRules.statusName(case.status),when(case.status){
            IncidentRules.CERRADO->"success"
            IncidentRules.ANULADO->"danger"
            IncidentRules.VERIFICACION->"warning"
            else->"brand"
        })
        IncidentPill(IncidentRules.priorityName(case.priority),when(case.priority){
            "alta","critica"->"danger"
            "media"->"warning"
            else->"success"
        })
    }
}
@Composable
private fun IncidentMetric(label:String,value:Int,kind:String,modifier:Modifier,res:Int){
    val ink=if(kind=="neutral")MaterialTheme.colorScheme.onSurface else semanticForeground(kind)
    Column(modifier.background(if(kind=="neutral")MaterialTheme.colorScheme.surfaceContainer else semanticBackground(kind),RoundedCornerShape(12.dp)).padding(10.dp),
        verticalArrangement=Arrangement.spacedBy(4.dp)){
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){
            Icon(painterResource(res),null,tint=ink,modifier=Modifier.size(19.dp))
            Text(value.toString(),style=MaterialTheme.typography.titleLarge,color=ink)
        }
        Text(label,style=MaterialTheme.typography.labelMedium,color=ink)
    }
}
@Composable
internal fun IncidentDesignedListCard(case:IncidentEntity,onOpen:(String)->Unit,selected:Boolean=false){
    val shape=RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxWidth().background(if(selected)MaterialTheme.colorScheme.primaryContainer.copy(alpha=.32f) else MaterialTheme.colorScheme.surface,shape)
        .border(1.dp,if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,shape)
        .clickable(onClick={onOpen(case.id)}).padding(12.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){
        Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.primaryContainer,RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center){
            Icon(painterResource(R.drawable.ic_lf_incidentes),null,tint=MaterialTheme.colorScheme.onPrimaryContainer,modifier=Modifier.size(25.dp))
        }
        BoxWithConstraints(Modifier.weight(1f)){
            val textContent:@Composable ()->Unit={
                Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
                    Text(case.code+" · "+case.productOrProcess,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.bodyLarge)
                    Text(case.occurredDate+" · "+case.occurredTime+" | "+IncidentRules.categoryName(case.category),
                        color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
                }
            }
            if(maxWidth>=410.dp)Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Box(Modifier.weight(1f)){textContent()}
                Box(Modifier.widthIn(max=210.dp)){IncidentPills(case)}
            }else Column(verticalArrangement=Arrangement.spacedBy(5.dp)){textContent();IncidentPills(case)}
        }
        Text("›",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.headlineSmall)
    }
}
@Composable
internal fun IncidentDesignedDashboard(incidents:List<IncidentEntity>,selected:IncidentEntity?,actions:List<IncidentActionEntity>,
    onNew:()->Unit,onOpen:(String)->Unit,onHistory:()->Unit,onClose:()->Unit,onFollowup:()->Unit,onVoid:()->Unit){
    val month=LocalDate.now().toString().take(7)
    val stats=IncidentRules.stats(incidents.filter{it.occurredDate.startsWith(month)})
    val open=incidents.filter(IncidentRules::isOpen).sortedWith(compareByDescending<IncidentEntity>{it.priority=="critica"}
        .thenByDescending{it.priority=="alta"}.thenByDescending{it.occurredDate}.thenByDescending{it.occurredTime})
    val visible=open.take(12)
    val listState=rememberLazyListState()
    LaunchedEffect(selected?.id){if(selected!=null && visible.size>2)listState.animateScrollToItem(3)}
    LazyColumn(Modifier.fillMaxSize(),state=listState,contentPadding=PaddingValues(14.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{
            IncidentDesignCard{
                BoxWithConstraints(Modifier.fillMaxWidth()){
                    val heading:@Composable ()->Unit={
                        Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
                            IncidentHeading("Resumen del mes",R.drawable.ic_lf_reportes)
                            Text(YearMonth.parse(month).format(DateTimeFormatter.ofPattern("MMMM yyyy",Locale("es","PE"))),
                                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    val metrics:@Composable (Modifier)->Unit={m->
                        Row(m,horizontalArrangement=Arrangement.spacedBy(6.dp)){
                            IncidentMetric("Total",stats.total,"neutral",Modifier.weight(1f),R.drawable.ic_lf_incidentes)
                            IncidentMetric("Pendientes",open.size,"warning",Modifier.weight(1f),R.drawable.ic_lf_updates)
                            IncidentMetric("Alta / crítica",open.count{it.priority in setOf("alta","critica")},"danger",Modifier.weight(1f),R.drawable.ic_lf_reportes)
                        }
                    }
                    if(maxWidth>=540.dp)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        Box(Modifier.weight(.85f)){heading()}
                        metrics(Modifier.weight(1.8f))
                    }else Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                        heading()
                        metrics(Modifier.fillMaxWidth())
                    }
                }
                Text("Cerrados este mes: "+stats.closed+" · Pendientes de todas las fechas",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item{
            Button(onClick=onNew,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),
                colors=ButtonDefaults.buttonColors(containerColor=LasFloresPalette.Burgundy,contentColor=Color.White)){
                Text("+ Registrar incidente",fontWeight=FontWeight.Bold)
            }
        }
        item{
            IncidentDesignCard{
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    IncidentDesignIcon(R.drawable.ic_lf_incidentes,25)
                    Column(Modifier.weight(1f)){
                        Text("Casos pendientes",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                        Text("Incidentes que requieren seguimiento.",style=MaterialTheme.typography.bodySmall)
                    }
                    IncidentPill(open.size.toString()+" pendientes","brand")
                }
                if(visible.isEmpty())Text("No hay incidentes pendientes registrados.")
                visible.forEach{case->IncidentDesignedListCard(case,onOpen,selected?.id==case.id)}
                if(open.size>visible.size)Text("Primeros 12 casos por prioridad.",style=MaterialTheme.typography.bodySmall)
                TextButton(onClick=onHistory,modifier=Modifier.align(Alignment.End)){Text("Ver seguimiento e historial")}
            }
        }
        if(selected!=null)item(key="detail-"+selected.id){IncidentDetailCard(selected,actions,onClose,onFollowup,onVoid)}
    }
}
@Composable
private fun IncidentInfo(label:String,value:String,modifier:Modifier=Modifier){
    Column(modifier,verticalArrangement=Arrangement.spacedBy(3.dp)){
        Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium)
    }
}
@Composable
private fun IncidentDetailRow(label:String,value:String,res:Int){
    val shape=RoundedCornerShape(10.dp)
    BoxWithConstraints(Modifier.fillMaxWidth().border(1.dp,MaterialTheme.colorScheme.outlineVariant,shape)){
        if(maxWidth>=400.dp){
            Row(verticalAlignment=Alignment.CenterVertically){
                Row(Modifier.width(160.dp).background(MaterialTheme.colorScheme.surfaceContainer,shape).padding(11.dp),
                    verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)){
                    IncidentDesignIcon(res,19)
                    Text(label,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary)
                }
                Text(value,modifier=Modifier.weight(1f).padding(11.dp),style=MaterialTheme.typography.bodyMedium)
            }
        }else Column(Modifier.padding(11.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)){
                IncidentDesignIcon(res,19)
                Text(label,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.primary)
            }
            Text(value,style=MaterialTheme.typography.bodyMedium)
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IncidentDetailCard(case:IncidentEntity,actions:List<IncidentActionEntity>,
    onDismiss:()->Unit,onFollowup:()->Unit,onVoid:()->Unit){
    IncidentDesignCard{
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer,RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center){
                Icon(painterResource(R.drawable.ic_lf_incidentes),null,tint=MaterialTheme.colorScheme.onPrimaryContainer,modifier=Modifier.size(28.dp))
            }
            BoxWithConstraints(Modifier.weight(1f)){
                if(maxWidth>=410.dp)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
                        Text(case.code,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                        Text(case.productOrProcess,style=MaterialTheme.typography.bodyLarge)
                    }
                    Box(Modifier.widthIn(max=235.dp)){IncidentPills(case)}
                }else Column(verticalArrangement=Arrangement.spacedBy(3.dp)){
                    Text(case.code,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                    Text(case.productOrProcess,style=MaterialTheme.typography.bodyLarge)
                    IncidentPills(case)
                }
            }
        }
        Text(if(IncidentRules.isOpen(case))"Revisa el estado y registra el siguiente paso del caso." else "Consulta el historial y el resultado del caso.",
            modifier=Modifier.fillMaxWidth().background(semanticBackground("warning"),RoundedCornerShape(10.dp)).padding(12.dp),
            style=MaterialTheme.typography.bodyMedium,color=semanticForeground("warning"))
        Column(Modifier.fillMaxWidth().border(1.dp,MaterialTheme.colorScheme.outlineVariant,RoundedCornerShape(13.dp)).padding(13.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)){
            IncidentHeading("Información general",R.drawable.ic_lf_incidentes)
            BoxWithConstraints(Modifier.fillMaxWidth()){
                if(maxWidth>=320.dp){
                    Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
                        Row(horizontalArrangement=Arrangement.spacedBy(14.dp)){
                            IncidentInfo("Fecha",case.occurredDate+" · "+case.occurredTime,Modifier.weight(1f))
                            IncidentInfo("Categoría",IncidentRules.categoryName(case.category),Modifier.weight(1f))
                        }
                        Row(horizontalArrangement=Arrangement.spacedBy(14.dp)){
                            IncidentInfo("Tipo",case.typeLabelSnapshot,Modifier.weight(1f))
                            IncidentInfo("Área de detección",case.detectedArea,Modifier.weight(1f))
                        }
                    }
                }else Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                    IncidentInfo("Fecha",case.occurredDate+" · "+case.occurredTime)
                    IncidentInfo("Categoría",IncidentRules.categoryName(case.category))
                    IncidentInfo("Tipo",case.typeLabelSnapshot)
                    IncidentInfo("Área de detección",case.detectedArea)
                }
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            IncidentInfo("Origen probable",case.originArea.ifBlank{"Sin determinar"})
            IncidentInfo("Origen confirmado",case.confirmedOrigin.ifBlank{"No verificado"})
            Text("El área de detección no necesariamente originó el problema.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IncidentDetailRow("Descripción",case.description,R.drawable.ic_lf_incidentes)
        IncidentDetailRow("Impacto",case.impact,R.drawable.ic_lf_reportes)
        IncidentDetailRow("Reportado por",case.reporter,R.drawable.ic_lf_personal)
        IncidentDetailRow("Medida inmediata",case.immediateAction,R.drawable.ic_lf_funciones)
        IncidentDetailRow("Seguimiento asignado",case.assignedStaffNameSnapshot.ifBlank{"Sin asignar"},R.drawable.ic_lf_personal)
        if(case.status==IncidentRules.CERRADO)IncidentDetailRow("Cerrado por",case.closedBy,R.drawable.ic_lf_personal)
        if(case.status==IncidentRules.ANULADO)IncidentDetailRow("Motivo de anulación",case.voidReason,R.drawable.ic_lf_incidentes)
        Column(Modifier.fillMaxWidth().border(1.dp,MaterialTheme.colorScheme.outlineVariant,RoundedCornerShape(13.dp)).padding(13.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)){
            IncidentHeading("Historial de acciones ("+actions.size+")",R.drawable.ic_lf_updates)
            if(actions.isEmpty())Text("Todavía no hay acciones de seguimiento.",style=MaterialTheme.typography.bodySmall)
            actions.sortedByDescending{it.atMillis}.forEach{a->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.padding(top=8.dp)){
                        Box(Modifier.size(11.dp).background(MaterialTheme.colorScheme.primary,RoundedCornerShape(50)))
                        Box(Modifier.width(2.dp).height(55.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    }
                    Column(Modifier.weight(1f).background(MaterialTheme.colorScheme.surfaceContainer,RoundedCornerShape(12.dp)).padding(12.dp),
                        verticalArrangement=Arrangement.spacedBy(5.dp)){
                        Text(IncidentRules.actionName(a.kind),fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.titleMedium)
                        Text(Instant.ofEpochMilli(a.atMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),style=MaterialTheme.typography.bodySmall)
                        Text(a.description,style=MaterialTheme.typography.bodyMedium)
                        Text("Por "+a.actor+" · "+IncidentRules.statusName(a.statusBefore)+" → "+IncidentRules.statusName(a.statusAfter),
                            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(a.confirmedOrigin.isNotBlank())Text("Origen verificado: "+a.confirmedOrigin,style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(9.dp),verticalArrangement=Arrangement.spacedBy(6.dp),itemVerticalAlignment=Alignment.CenterVertically){
            TextButton(onClick=onDismiss){Text("Cerrar")}
            if(IncidentRules.isOpen(case))OutlinedButton(onClick=onVoid){Text("Anular")}
            if(case.status!=IncidentRules.ANULADO)Button(onClick=onFollowup,colors=ButtonDefaults.buttonColors(containerColor=LasFloresPalette.Burgundy,contentColor=Color.White)){
                Text(if(case.status==IncidentRules.CERRADO)"Reabrir caso" else "+ Registrar seguimiento",fontWeight=FontWeight.Bold)
            }
        }
    }
}
