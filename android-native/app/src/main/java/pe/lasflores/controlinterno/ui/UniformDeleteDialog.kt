package pe.lasflores.controlinterno.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.lasflores.controlinterno.data.UniformInspectionEntity

@Composable
internal fun UniformDeleteDialog(record:UniformInspectionEntity,name:String,onDismiss:()->Unit,onConfirm:()->Unit){
    var confirmed by remember(record.id){mutableStateOf(false)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Eliminar revisión errónea")},
        text={Column(verticalArrangement=Arrangement.spacedBy(9.dp)){
            Text(name,fontWeight=FontWeight.Bold)
            Text(record.dateIso+" · "+record.roleSnapshot)
            Text("Resultado: "+(record.initialResult?:"Borrador"))
            Text("Se eliminará esta revisión completa, sus respuestas y eventos de auditoría. Dejará de contar en el historial y las estadísticas. No se borrarán otros días ni la ficha del trabajador.")
            Text("Esta acción no se puede deshacer desde Uniformes. Exporta un respaldo antes de continuar.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
            Row(verticalAlignment=Alignment.CenterVertically){
                Checkbox(checked=confirmed,onCheckedChange={confirmed=it})
                Text("Confirmo que registré esta revisión por error.",style=MaterialTheme.typography.bodyMedium)
            }
        }},
        confirmButton={Button(enabled=confirmed,onClick=onConfirm,colors=ButtonDefaults.buttonColors(
            containerColor=MaterialTheme.colorScheme.error,contentColor=MaterialTheme.colorScheme.onError)){
            Text("Eliminar definitivamente")
        }},
        dismissButton={TextButton(onClick=onDismiss){Text("Cancelar")}})
}
