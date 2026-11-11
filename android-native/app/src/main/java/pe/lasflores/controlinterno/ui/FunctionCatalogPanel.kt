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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.lasflores.controlinterno.data.FunctionTaskEntity
import pe.lasflores.controlinterno.domain.FunctionRules

@Composable
fun FunctionCatalogPanel(vm:FunctionViewModel,tasks:List<FunctionTaskEntity>) {
    var area by rememberSaveable{mutableStateOf(FunctionRules.AREAS.first())}
    var editing by remember{mutableStateOf<FunctionTaskEntity?>(null)}
    var creating by remember{mutableStateOf(false)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(14.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {
            Text("Catálogo de funciones",style=MaterialTheme.typography.headlineSmall,
                fontWeight=FontWeight.Bold)
            Text("Crea funciones diarias o mensuales, edita sus nombres y desactiva las que ya no uses.",
                style=MaterialTheme.typography.bodySmall)
            Text("Las asignaciones anteriores conservan su descripción original.",
                style=MaterialTheme.typography.bodySmall)
            Button(onClick={creating=true}){Text("+ Nueva función")}
            LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                items(FunctionRules.AREAS) { a ->
                    FilterChip(selected=area==a,onClick={area=a},label={Text(a)})
                }
            }
        }
        items(tasks.filter{it.area==area},key={it.id}) {task ->
            Card {
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(task.label,fontWeight=FontWeight.Bold)
                    Text((if(task.periodicity=="MENSUAL")"Mensual" else "Diaria")+
                        " · "+(if(task.isActive)"Activa" else "Desactivada"),
                        style=MaterialTheme.typography.bodySmall)
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        item {
                            OutlinedButton(onClick={editing=task}){Text("Editar")}
                        }
                        item {
                            OutlinedButton(onClick={vm.setTaskActive(task.id,!task.isActive)}) {
                                Text(if(task.isActive)"Desactivar" else "Activar")
                            }
                        }
                    }
                }
            }
        }
        if(tasks.none{it.area==area})item{Text("Sin funciones en esta área.")}
    }
    if(creating || editing!=null) FunctionTaskDialog(
        task=editing,
        onDismiss={creating=false;editing=null},
        onSave={id,title,a,frequency->
            vm.saveTask(id,title,a,frequency)
            creating=false
            editing=null
        }
    )
}

@Composable
private fun FunctionTaskDialog(
    task:FunctionTaskEntity?,
    onDismiss:()->Unit,
    onSave:(String?,String,String,String)->Unit
) {
    var title by remember(task?.id){mutableStateOf(task?.label.orEmpty())}
    var area by remember(task?.id){mutableStateOf(task?.area?:FunctionRules.AREAS.first())}
    var periodicity by remember(task?.id){mutableStateOf(task?.periodicity?:"DIARIA")}
    AlertDialog(onDismissRequest=onDismiss,
        title={Text(if(task==null)"Nueva función" else "Editar función")},
        text={
            Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(value=title,onValueChange={title=it.take(100)},
                    label={Text("Nombre de la función")},modifier=Modifier.fillMaxWidth())
                Text("Oficina o área",fontWeight=FontWeight.Bold)
                FunctionRules.AREAS.forEach{a->
                    FilterChip(selected=area==a,onClick={area=a},label={Text(a)})
                }
                Text("Frecuencia",fontWeight=FontWeight.Bold)
                FilterChip(selected=periodicity=="DIARIA",
                    onClick={periodicity="DIARIA"},label={Text("Diaria")})
                FilterChip(selected=periodicity=="MENSUAL",
                    onClick={periodicity="MENSUAL"},label={Text("Mensual")})
                Text("El cambio no modificará las evaluaciones ya guardadas.",
                    style=MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton={
            Button(enabled=FunctionRules.validTaskLabel(title),
                onClick={onSave(task?.id,title,area,periodicity)}){Text("Guardar")}
        },
        dismissButton={TextButton(onClick=onDismiss){Text("Cancelar")}}
    )
}
