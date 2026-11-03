package local.museumslideshow.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import local.museumslideshow.R

/** Lo común de las pantallas sencillas: título, texto de abajo, barra de ayuda y Atrás que vuelve. */
@Composable
private fun PantallaSimple(
    titulo: Int,
    pie: Int,
    activa: Boolean,
    alVolver: () -> Unit,
    modifier: Modifier,
    contenido: @Composable () -> Unit,
) {
    Escenario1080 {
        Box(
            modifier
                .fillMaxSize()
                .background(Paleta.Fondo)
                .conTeclas(activa) { tecla -> if (tecla == Tecla.ATRAS) alVolver() },
        ) {
            Column(Modifier.fillMaxSize().padding(start = 120.dp, end = 120.dp, top = 44.dp, bottom = 96.dp)) {
                BasicText(stringResource(titulo), style = estiloTexto(52, Paleta.Texto, 600))
                Box(Modifier.padding(top = 10.dp, bottom = 18.dp)) {
                    BasicText(stringResource(pie), style = estiloTexto(24, Paleta.Tenue, 400), maxLines = 1)
                }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) { contenido() }
            }
            BarraAyuda(listOf(Ayudas.atras(R.string.ayuda_volver)), Modifier.align(Alignment.BottomCenter))
        }
    }
}

