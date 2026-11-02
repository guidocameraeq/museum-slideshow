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

/**
 * Protector: el interruptor "Usar como protector" todavía no anda (llega en la sesión 4) y se muestra apagado, junto con lo
 * que el sistema dice hoy del permiso y del protector activo. Solo lee: nada se escribe en el sistema.
 */
@Composable
fun PantallaProtector(activa: Boolean, alVolver: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val datos = leerProtector(ctx)
    PantallaSimple(R.string.menu_protector, R.string.pie_protector, activa, alVolver, modifier) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Paleta.Panel).padding(horizontal = 28.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(stringResource(R.string.aj_protector), Modifier.width(560.dp), style = estiloTexto(30, Paleta.Tenue, 500), maxLines = 1)
            BasicText(stringResource(R.string.protector_proximamente), style = estiloTexto(30, Paleta.Tenue, 600), maxLines = 1)
        }
        Box(Modifier.padding(top = 18.dp)) {
            if (datos.permisoOtorgado) {
                BasicText(stringResource(R.string.est_permiso_si), style = estiloTexto(28, Paleta.Bien, 500))
            } else {
                BasicText(stringResource(R.string.est_permiso_no), style = estiloTexto(28, Paleta.Aviso, 500))
            }
        }
        if (!datos.permisoOtorgado) {
            BasicText(
                stringResource(R.string.comando_permiso),
                modifier = Modifier.background(Paleta.Panel).padding(horizontal = 18.dp, vertical = 10.dp),
                style = estiloTexto(26, Color(0xFFF2E7C4), 500),
            )
        }
        BasicText(
            stringResource(R.string.est_protector_activo, nombreProtector(ctx, datos.activo)),
            style = estiloTexto(28, Paleta.Texto, 400),
        )
    }
}

/** Galerías: elegir cuáles se muestran llega en la próxima parte del menú. Por ahora solo avisa. */
@Composable
fun PantallaGalerias(activa: Boolean, alVolver: () -> Unit, modifier: Modifier = Modifier) {
    PantallaSimple(R.string.menu_galerias, R.string.pie_galerias, activa, alVolver, modifier) {
        BasicText(stringResource(R.string.galerias_proximamente), style = estiloTexto(32, Paleta.Tenue, 400))
    }
}
