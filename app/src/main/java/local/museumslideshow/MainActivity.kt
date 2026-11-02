package local.museumslideshow

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import local.museumslideshow.ajustes.Preferencias
import local.museumslideshow.ajustes.PreferenciasDesdeTexto
import local.museumslideshow.catalogo.Catalogo
import local.museumslideshow.catalogo.CatalogoCargado
import local.museumslideshow.galeria.GaleriaEnPantalla
import local.museumslideshow.galeria.SaltoEscena
import local.museumslideshow.sync.Sincronizacion
import local.museumslideshow.sync.SincronizarWorker

class MainActivity : ComponentActivity() {
    private var catalogo by mutableStateOf<CatalogoCargado?>(null)
    private var sincronizando by mutableStateOf(false)
    private var preferencias by mutableStateOf(Preferencias())
    private var salto by mutableStateOf<SaltoEscena?>(null)

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(Registro.TAG, "Arranca la app (versión ${BuildConfig.VERSION_NAME})")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        aplicarIntent(intent)
        try {
            SincronizarWorker.programar(applicationContext)
        } catch (e: Exception) {
            Log.w(Registro.TAG, "No se pudo programar la sincronización (${e.javaClass.simpleName})")
        }
        setContent {
            val cat = catalogo
            val obras = cat?.galerias?.sumOf { it.obras.size } ?: 0
            if (cat != null && obras > 0) {
                GaleriaEnPantalla(cat, preferencias, alSalir = { finish() }, salto = salto)
            } else {
                PantallaMensaje(cat, sincronizando)
            }
        }

        lifecycleScope.launch {
            recargar()
            sincronizando = true
            try {
                withContext(Dispatchers.IO) { Sincronizacion.ejecutar(applicationContext) }
            } finally {
                sincronizando = false
            }
            // Recarga el catálogo sin cortar la escena: la galería conserva la obra actual si sigue existiendo.
            recargar()
        }
    }

    /** Para probar por adb sin reiniciar: `am start -n local.museumslideshow/.MainActivity --es tema sala-oscura`. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        aplicarIntent(intent)
    }

    /**
     * Los extras del intent son ajustes de prueba (mismas claves que [PreferenciasDesdeTexto]) y se suman a los
     * actuales; `reset` los vuelve a los valores por defecto antes. `escena` = índice (desde 0) donde arrancar.
     */
    private fun aplicarIntent(i: Intent?) {
        val extras = i?.extras ?: return
        val texto = HashMap<String, String>()
        for (k in extras.keySet()) {
            @Suppress("DEPRECATION")
            extras.get(k)?.toString()?.let { texto[k] = it }
        }
        if (texto.isEmpty()) return
        val base = if (texto.containsKey("reset")) Preferencias() else preferencias
        preferencias = PreferenciasDesdeTexto.aplicar(base, texto)
        Log.i(Registro.TAG, "Preferencias efectivas: $preferencias")
        texto["escena"]?.toIntOrNull()?.let {
            salto = SaltoEscena(it)
            Log.i(Registro.TAG, "Salto a la escena $it")
        }
    }

    private suspend fun recargar() {
        catalogo = withContext(Dispatchers.IO) { Catalogo.cargar(Sincronizacion.dirBase(applicationContext)) }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun PantallaMensaje(cat: CatalogoCargado?, sincronizando: Boolean) {
    val res = LocalContext.current.resources
    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            color = Color.White,
            fontSize = 48.sp,
            textAlign = TextAlign.Center,
        )
        val obras = cat?.galerias?.sumOf { it.obras.size } ?: 0
        if (cat == null || obras == 0) {
            Linea(stringResource(R.string.estado_sin_obras), 24, Color(0xFFB0B0B0))
        } else {
            val g = res.getQuantityString(R.plurals.cantidad_galerias, cat.galerias.size, cat.galerias.size)
            val o = res.getQuantityString(R.plurals.cantidad_obras, obras, obras)
            Linea(res.getString(R.string.estado_resumen, g, o), 28, Color.White)
            for (gal in cat.galerias) {
                val n = res.getQuantityString(R.plurals.cantidad_obras, gal.obras.size, gal.obras.size)
                Linea(res.getString(R.string.estado_galeria_linea, gal.titulo, n), 22, Color(0xFFB0B0B0))
            }
        }
        if (cat != null && cat.problemas.isNotEmpty()) {
            val n = cat.problemas.size
            Linea(res.getQuantityString(R.plurals.cantidad_problemas, n, n), 22, Color(0xFFE0A050))
        }
        if (sincronizando) {
            Linea(stringResource(R.string.estado_sincronizando), 22, Color(0xFF80B0E0))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun Linea(texto: String, tamano: Int, color: Color) {
    Text(
        text = texto,
        color = color,
        fontSize = tamano.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 12.dp),
    )
}
