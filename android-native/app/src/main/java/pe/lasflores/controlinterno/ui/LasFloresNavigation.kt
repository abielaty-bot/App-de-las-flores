package pe.lasflores.controlinterno.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.lasflores.controlinterno.R

internal data class AppDestination(
    val id:String,val title:String,@DrawableRes val icon:Int
)

private val main= listOf(
    AppDestination("inicio","Inicio",R.drawable.ic_lf_home),
    AppDestination("personal","Personal",R.drawable.ic_lf_personal),
    AppDestination("briefings","Briefings",R.drawable.ic_lf_briefings),
    AppDestination("uniformes","Uniformes",R.drawable.ic_lf_uniformes)
)
private val secondary= listOf(
    AppDestination("funciones","Funciones",R.drawable.ic_lf_funciones),
    AppDestination("incidentes","Incidentes",R.drawable.ic_lf_incidentes),
    AppDestination("reportes","Reportes",R.drawable.ic_lf_reportes),
    AppDestination("backups","Respaldos",R.drawable.ic_lf_backups),
    AppDestination("settings","Configuración",R.drawable.ic_lf_settings),
    AppDestination("updates","Actualizaciones",R.drawable.ic_lf_updates)
)
private val destinations=main+secondary
private val more=AppDestination("more","Más",R.drawable.ic_lf_more)

/** Portrait: barra inferior de cuatro accesos más "Más".
 * Pantalla horizontal amplia: menú permanente con las nueve áreas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LasFloresAppShell(
    screen:String,
    onNavigate:(String)->Unit,
    content:@Composable ()->Unit
) {
    var showMore by remember{mutableStateOf(false)}
    val title=destinations.firstOrNull{it.id==screen}?.title?:"Las Flores"
    BackHandler(enabled=screen!="inicio"||showMore) {
        if(showMore)showMore=false else onNavigate("inicio")
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide=maxWidth>=760.dp
        Scaffold(
            containerColor=MaterialTheme.colorScheme.background,
            topBar={
                TopAppBar(
                    title={Text(if(screen=="inicio")"LAS FLORES" else title,
                        fontWeight=FontWeight.Bold)},
                    colors=TopAppBarDefaults.topAppBarColors(
                        containerColor=if(wide)MaterialTheme.colorScheme.surface
                            else LasFloresPalette.DeepPlum,
                        titleContentColor=if(wide)MaterialTheme.colorScheme.onSurface
                            else Color.White
                    )
                )
            },
            bottomBar={
                if(!wide) {
                    NavigationBar(
                        containerColor=MaterialTheme.colorScheme.surface,
                        tonalElevation=3.dp
                    ) {
                        (main+more).forEach { destination ->
                            val selected=if(destination.id=="more")
                                screen in secondary.map{it.id} else destination.id==screen
                            NavigationBarItem(
                                selected=selected,
                                onClick={
                                    if(destination.id=="more")showMore=true
                                    else onNavigate(destination.id)
                                },
                                icon={
                                    Icon(painterResource(destination.icon),
                                        contentDescription=null)
                                },
                                label={Text(destination.title)},
                                alwaysShowLabel=true
                            )
                        }
                    }
                }
            }
        ){padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if(wide) SideNavigation(
                    selected=screen,onNavigate=onNavigate
                )
                Column(Modifier.fillMaxSize().weight(1f)) {
                    content()
                }
            }
        }
        if(showMore) {
            ModalBottomSheet(
                onDismissRequest={showMore=false},
                containerColor=MaterialTheme.colorScheme.surface
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal=16.dp)
                    .padding(bottom=22.dp),
                    verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text("Otras secciones",
                        style=MaterialTheme.typography.titleLarge,
                        modifier=Modifier.padding(bottom=7.dp))
                    secondary.forEach { d ->
                        NavigationDrawerItem(
                            label={Text(d.title)},
                            selected=screen==d.id,
                            onClick={showMore=false;onNavigate(d.id)},
                            icon={Icon(painterResource(d.icon),contentDescription=null)}
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SideNavigation(selected:String,onNavigate:(String)->Unit) {
    val dark = LocalLasFloresDark.current
    Column(
        modifier=Modifier.width(213.dp).fillMaxHeight()
            .background(LasFloresPalette.DeepPlum)
            .verticalScroll(rememberScrollState())
            .padding(horizontal=9.dp,vertical=16.dp),
        verticalArrangement=Arrangement.spacedBy(4.dp)
    ) {
        Text("LAS FLORES",color=Color.White,
            fontWeight=FontWeight.Bold,
            style=MaterialTheme.typography.titleLarge,
            modifier=Modifier.padding(start=14.dp,top=7.dp))
        Text("CONTROL INTERNO",
            color=Color(0xFFEAC89B),
            style=MaterialTheme.typography.labelSmall,
            modifier=Modifier.padding(start=14.dp,bottom=16.dp))
        destinations.forEach { d ->
            NavigationDrawerItem(
                label={Text(d.title)},
                selected=selected==d.id,
                onClick={onNavigate(d.id)},
                icon={Icon(painterResource(d.icon),contentDescription=null)},
                colors=NavigationDrawerItemDefaults.colors(
                    selectedContainerColor=if(dark) Color(0xFF513542) else Color(0xFFF0DAC2),
                    selectedTextColor=if(dark) Color.White else LasFloresPalette.DeepPlum,
                    selectedIconColor=if(dark) LasFloresPalette.NightChampagne else LasFloresPalette.DeepPlum,
                    unselectedTextColor=Color.White,
                    unselectedIconColor=if(dark) Color(0xFFDCB98A) else Color(0xFFEAD6D9)
                )
            )
        }
    }
}
