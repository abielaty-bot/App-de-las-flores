package pe.lasflores.controlinterno

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import pe.lasflores.controlinterno.ui.LasFloresScreen
import pe.lasflores.controlinterno.ui.StaffViewModel

class MainActivity : ComponentActivity() {
    private val staffViewModel: StaffViewModel by viewModels {
        StaffViewModel.Factory((application as LasFloresApp).staffRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LasFloresScreen(staffViewModel) }
    }
}
