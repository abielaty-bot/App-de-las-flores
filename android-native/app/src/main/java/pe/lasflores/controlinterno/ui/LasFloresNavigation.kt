package pe.lasflores.controlinterno.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pe.lasflores.controlinterno.R

internal data class AppDestination(
    val id:String,val title:String,@DrawableRes val icon:Int
)

private val destinations=listOf(
    AppDestination("inicio","Inicio",R.drawable.ic_lf_home),
    AppDestination("personal","Personal",R.drawable.ic_lf_personal),
    AppDestination("briefings","Briefings",R.drawable.ic_lf_briefings),
    AppDestination("uniformes","Uniformes",R.drawable.ic_lf_uniformes),
    AppDestination("celulares","Celulares",R.drawable.ic_lf_phone),
    AppDestination("operativo","Control operativo",R.drawable.ic_lf_funciones),
    AppDestination("funciones","Funciones",R.drawable.ic_lf_funciones),
    AppDestination("incidentes","Incidentes",R.drawable.ic_lf_incidentes),
    AppDestination("reportes","Reportes",R.drawable.ic_lf_reportes),
    AppDestination("notifications","Notificaciones",R.drawable.ic_lf_bell),
    AppDestination("backups","Respaldos",R.drawable.ic_lf_backups),
    AppDestination("settings","Configuración",R.drawable.ic_lf_settings),
    AppDestination("updates","Actualizaciones",R.drawable.ic_lf_updates)
)

/** Cajón modal: cerrado no ocupa anchura; el contenido no se desmonta al abrirlo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LasFloresAppShell(screen:String,onNavigate:(String)->Unit,unreadNotifications:Int=0,content:@Composable ()->Unit) {
    val drawer=rememberDrawerState(DrawerValue.Closed)
    val scope=rememberCoroutineScope()
    val title=destinations.firstOrNull{it.id==screen}?.title?:"Las Flores"
    val dark=LocalLasFloresDark.current
    BackHandler(enabled=drawer.isOpen || screen!="inicio") {
        if(drawer.isOpen)scope.launch{drawer.close()} else onNavigate("inicio")
    }
    BoxWithConstraints(Modifier.fillMaxSize()){
        val drawerWidth=minOf(340.dp,maxWidth*0.68f).coerceAtLeast(minOf(240.dp,maxWidth))
        ModalNavigationDrawer(drawerState=drawer,gesturesEnabled=true,
            drawerContent={
                ModalDrawerSheet(modifier=Modifier.width(drawerWidth),drawerContainerColor=LasFloresPalette.DeepPlum,
                    drawerContentColor=Color.White) {
                    Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState())
                        .padding(horizontal=12.dp,vertical=14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text("LAS FLORES",color=Color.White,fontWeight=FontWeight.Bold,
                            style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(start=14.dp,top=7.dp))
                        Text("CONTROL INTERNO",color=LasFloresPalette.NightChampagne,
                            style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(start=14.dp,bottom=15.dp))
                        TextButton(onClick={scope.launch{drawer.close()}}){Text("Cerrar menú",color=LasFloresPalette.NightChampagne)}
                        destinations.forEach{d->
                            NavigationDrawerItem(label={Text(d.title)},selected=screen==d.id,
                                onClick={onNavigate(d.id);scope.launch{drawer.close()}},
                                icon={Icon(painterResource(d.icon),contentDescription=null)},
                                colors=NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor=if(dark)Color(0xFF513542) else Color(0xFFF0DAC2),
                                    selectedTextColor=if(dark)Color.White else LasFloresPalette.DeepPlum,
                                    selectedIconColor=if(dark)LasFloresPalette.NightChampagne else LasFloresPalette.DeepPlum,
                                    unselectedTextColor=Color.White,unselectedIconColor=LasFloresPalette.NightChampagne))
                        }
                    }
                }
            }) {
            Scaffold(containerColor=MaterialTheme.colorScheme.background,
                topBar={Column{
                    if(screen=="incidentes")Row(Modifier.fillMaxWidth().background(LasFloresPalette.Burgundy)
                        .padding(horizontal=16.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        Image(painterResource(R.drawable.las_flores_tulip),contentDescription=null,modifier=Modifier.size(29.dp),contentScale=ContentScale.Fit)
                        Text("Las Flores",color=Color.White,style=MaterialTheme.typography.titleLarge)
                    }
                    TopAppBar(title={Text(if(screen=="inicio")"LAS FLORES" else title,fontWeight=FontWeight.Bold)},
                    navigationIcon={
                        IconButton(onClick={scope.launch{if(drawer.isOpen)drawer.close() else drawer.open()}},
                            modifier=Modifier.semantics{contentDescription="Abrir menú de navegación"}){
                            val ink=MaterialTheme.colorScheme.onSurface
                            Canvas(Modifier.size(24.dp)) {
                                val w=size.width;val h=size.height
                                drawLine(ink,Offset(w*0.12f,h*0.27f),Offset(w*0.88f,h*0.27f),strokeWidth=2.dp.toPx())
                                drawLine(ink,Offset(w*0.12f,h*0.55f),Offset(w*0.68f,h*0.55f),strokeWidth=2.dp.toPx())
                                drawLine(ink,Offset(w*0.12f,h*0.82f),Offset(w*0.88f,h*0.82f),strokeWidth=2.dp.toPx())
                            }
                        }
                    },actions={
                        IconButton(onClick={onNavigate("notifications")},modifier=Modifier.semantics{contentDescription="Notificaciones: "+unreadNotifications+" sin leer"}){
                            Icon(painterResource(R.drawable.ic_lf_bell),contentDescription=null,tint=MaterialTheme.colorScheme.tertiary)
                        }
                        if(unreadNotifications>0)Text(unreadNotifications.coerceAtMost(99).toString(),style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(end=8.dp))
                    },colors=TopAppBarDefaults.topAppBarColors(
                        containerColor=MaterialTheme.colorScheme.surface,titleContentColor=MaterialTheme.colorScheme.onSurface))
                    }
                }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)){content()}
            }
        }
    }
}
