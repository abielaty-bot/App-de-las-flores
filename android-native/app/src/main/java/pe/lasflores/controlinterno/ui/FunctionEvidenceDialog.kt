package pe.lasflores.controlinterno.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import pe.lasflores.controlinterno.data.FunctionCheckEntity
import pe.lasflores.controlinterno.data.FunctionEvidenceEntity

/** Comprime y copia la fotografía al almacenamiento de Room (respaldo JSON incluido).
 * Nunca conserva un URI temporal de la galería como única evidencia.
 */
private fun jpegForBackup(context:Context,uri:Uri):String {
    val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it,null,bounds)
    } ?: error("No se pudo leer la fotografía.")
    require(bounds.outWidth in 1..18000 && bounds.outHeight in 1..18000) {
        "Dimensiones de imagen no admitidas."
    }
    var sample=1
    while(bounds.outWidth/sample>1200 || bounds.outHeight/sample>1200) sample*=2
    val options=BitmapFactory.Options().apply { inSampleSize=sample }
    val bitmap=context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it,null,options)
    } ?: error("No se pudo abrir la fotografía seleccionada.")
    val maxSide=maxOf(bitmap.width,bitmap.height)
    val scaled=if(maxSide>1024) Bitmap.createScaledBitmap(bitmap,
        (bitmap.width*1024.0/maxSide).toInt().coerceAtLeast(1),
        (bitmap.height*1024.0/maxSide).toInt().coerceAtLeast(1),true)
    else bitmap
    if(scaled !== bitmap) bitmap.recycle()
    var encoded=ByteArray(0)
    try {
        for(quality in listOf(72,60,48,35,25)) {
            val out=ByteArrayOutputStream()
            check(scaled.compress(Bitmap.CompressFormat.JPEG,quality,out)) {
                "No se pudo comprimir la imagen."
            }
            encoded=out.toByteArray()
            if(encoded.size<=200_000) break
        }
    } finally { scaled.recycle() }
    require(encoded.size<=220_000) {
        "La imagen es demasiado compleja. Prueba una fotografía de menor tamaño."
    }
    return Base64.encodeToString(encoded,Base64.NO_WRAP)
}

/** El usuario elige WhatsApp y su grupo; compartir no confirma una entrega. */
private fun sharePhoto(context:Context,photo:FunctionEvidenceEntity) {
    val folder=File(context.cacheDir,"function-evidence").apply {mkdirs()}
    val image=File(folder,photo.id+".jpg")
    val bytes=Base64.decode(photo.imageBase64,Base64.DEFAULT)
    require(bytes.size in 1..220_000) { "Imagen inválida." }
    image.writeBytes(bytes)
    val uri=FileProvider.getUriForFile(
        context,context.packageName+".updatefiles",image
    )
    val intent=Intent(Intent.ACTION_SEND).apply {
        type="image/jpeg"
        putExtra(Intent.EXTRA_STREAM,uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent,"Compartir evidencia"))
}

@Composable
fun FunctionEvidenceDialog(
    check:FunctionCheckEntity,
    photos:List<FunctionEvidenceEntity>,
    vm:FunctionViewModel,
    onDismiss:()->Unit
) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var caption by remember(check.id){mutableStateOf("")}
    var actor by remember(check.id){mutableStateOf("")}
    var error by remember(check.id){mutableStateOf("")}
    val reportError = LocalErrorReporter.current
    LaunchedEffect(error) {
        if (error.isNotBlank()) reportError("Funciones · Evidencias", error)
    }
    var loading by remember {mutableStateOf(false)}
    var sending by remember {mutableStateOf<String?>(null)}
    var removing by remember {mutableStateOf<String?>(null)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if(uri!=null && caption.trim().length>=3) {
            scope.launch {
                loading=true
                error=""
                try {
                    val base64=withContext(Dispatchers.IO) { jpegForBackup(context,uri) }
                    vm.addPhoto(check.id,caption,base64)
                    caption=""
                } catch(e:Exception) {
                    error=ErrorReportText.compact(e.message ?: "No se pudo adjuntar la foto.")
                } finally {loading=false}
            }
        }
    }

    AlertDialog(onDismissRequest=onDismiss,
        title={Text("Evidencias · "+check.taskLabelSnapshot)},
        text={
            Column(Modifier.heightIn(max=520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(9.dp)) {
                Text(check.staffNameSnapshot+" · "+check.dateIso,
                    style=MaterialTheme.typography.bodySmall)
                Text("Las fotos quedan guardadas en la app. Enviarlas al grupo de WhatsApp " +
                    "se hace por separado; marca el envío solo después de hacerlo.",
                    style=MaterialTheme.typography.bodySmall)
                if(photos.size<2) {
                    OutlinedTextField(value=caption,onValueChange={caption=it.take(300)},
                        label={Text("Descripción de la evidencia")},
                        modifier=Modifier.fillMaxWidth())
                    Button(enabled=caption.trim().length>=3 && !loading,
                        onClick={picker.launch("image/*")}) {
                        Text(if(loading)"Procesando fotografía…" else "Seleccionar fotografía")
                    }
                }
                if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
                Text("Fotografías registradas ("+photos.size+"/2)",
                    fontWeight=FontWeight.Bold)
                photos.forEach { photo ->
                    Card {
                        Column(Modifier.padding(10.dp),
                            verticalArrangement=Arrangement.spacedBy(7.dp)) {
                            val thumb=remember(photo.imageBase64) {
                                runCatching {
                                    val bytes=Base64.decode(photo.imageBase64,Base64.DEFAULT)
                                    BitmapFactory.decodeByteArray(bytes,0,bytes.size)?.asImageBitmap()
                                }.getOrNull()
                            }
                            if(thumb!=null)
                                Image(bitmap=thumb,contentDescription="Evidencia fotográfica",
                                    modifier=Modifier.fillMaxWidth().height(130.dp),
                                    contentScale=ContentScale.Fit)
                            Text(photo.description)
                            Text(if(photo.groupSentAtMillis==null)"Envío al grupo pendiente" else
                                "Envío declarado por "+photo.groupSentBy.orEmpty(),
                                color=if(photo.groupSentAtMillis==null)
                                    MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary,
                                style=MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick={
                                runCatching { sharePhoto(context,photo) }.onFailure {
                                    error=ErrorReportText.compact(it.message?:"No se pudo compartir la fotografía.")
                                }
                            }) { Text("Compartir fotografía") }
                            if(photo.groupSentAtMillis==null) {
                                OutlinedButton(onClick={sending=photo.id}) {
                                    Text("Confirmar envío al grupo")
                                }
                                TextButton(onClick={removing=photo.id}) {Text("Retirar fotografía")}
                            }
                        }
                    }
                }
                if(photos.isEmpty()) Text("Todavía no hay fotografías registradas.")
            }
        },
        confirmButton={TextButton(onClick=onDismiss){Text("Cerrar")}}
    )
    val actionId=sending?:removing
    if(actionId!=null) AlertDialog(
        onDismissRequest={sending=null;removing=null},
        title={Text(if(sending!=null)"Confirmar envío al grupo" else "Retirar evidencia")},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(if(sending!=null)
                    "Confirma únicamente si la fotografía ya fue enviada al grupo de funciones. " +
                    "La aplicación no puede verificar WhatsApp."
                    else "La foto se retirará de la base de datos; quedará el evento de auditoría.")
                OutlinedTextField(value=actor,onValueChange={actor=it.take(100)},
                    label={Text("Nombre del responsable")},modifier=Modifier.fillMaxWidth())
            }
        },
        confirmButton={
            Button(enabled=actor.trim().length>=2,onClick={
                if(sending!=null) vm.markSent(actionId,actor)
                else vm.deletePhoto(actionId,actor)
                sending=null
                removing=null
                actor=""
            }) {Text("Confirmar")}
        },
        dismissButton={TextButton(onClick={sending=null;removing=null}){Text("Cancelar")}}
    )
}
