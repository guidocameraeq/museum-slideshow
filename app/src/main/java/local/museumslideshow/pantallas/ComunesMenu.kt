package local.museumslideshow.pantallas

import android.graphics.BitmapFactory
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import local.museumslideshow.R
import local.museumslideshow.catalogo.Catalogo
import local.museumslideshow.catalogo.Obra
import local.museumslideshow.galeria.Decodificacion
import local.museumslideshow.galeria.esTeclaVolumen
import local.museumslideshow.sync.Sincronizacion

/**
 * Pide el foco del control cuando la pantalla está `activa` y entrega cada flecha/OK/Atrás a `alTecla`. Las flechas se
 * repiten si se mantienen; OK y Atrás, una vez. Las teclas de volumen (y cualquier otra) nunca se consumen.
 */
@Composable
fun Modifier.conTeclas(activa: Boolean, alTecla: (Tecla) -> Unit): Modifier {
    val activaActual by rememberUpdatedState(activa)
    val destino by rememberUpdatedState(alTecla)
    val pedirFoco = remember { FocusRequester() }
    LaunchedEffect(activa) { if (activa) try { pedirFoco.requestFocus() } catch (e: Exception) { /* todavía no está listo */ } }
    return this
        .focusRequester(pedirFoco)
        .focusable()
        .onPreviewKeyEvent { ev ->
            val n = ev.nativeKeyEvent
            if (!activaActual || esTeclaVolumen(n.keyCode)) return@onPreviewKeyEvent false
            val tecla = teclaDe(n.keyCode) ?: return@onPreviewKeyEvent false
            val repite = tecla != Tecla.OK && tecla != Tecla.ATRAS
            if (n.action == KeyEvent.ACTION_DOWN && (n.repeatCount == 0 || repite)) destino(tecla)
            true
        }
}

/** Una indicación de la barra de ayuda: la tecla y qué hace. */
class Ayuda(val tecla: String, val texto: String)

/** La barra de ayuda de teclas, abajo de todo (borrador 4: negro al 55%, 84 de alto). Va dentro de [Escenario1080]. */
@Composable
fun BarraAyuda(ayudas: List<Ayuda>, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(84.dp).background(Color(0x8C000000)).padding(horizontal = 120.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ayudas.forEachIndexed { i, a ->
            if (i > 0) Spacer(Modifier.width(46.dp))
            BasicText(
                a.tecla,
                modifier = Modifier
                    .widthIn(min = 54.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0x24FFFFFF))
                    .padding(horizontal = 12.dp, vertical = 3.dp),
                style = estiloTexto(24, Paleta.Texto, 600).copy(textAlign = TextAlign.Center),
                maxLines = 1,
            )
            Spacer(Modifier.width(12.dp))
            BasicText(a.texto, style = estiloTexto(26, Paleta.Tenue, 400), maxLines = 1)
        }
    }
}

/** Las ayudas de teclas más comunes, con sus textos de strings.xml. */
object Ayudas {
    @Composable fun moverse() = Ayuda(stringResource(R.string.tecla_flechas), stringResource(R.string.ayuda_moverse))
    @Composable fun recorrer() = Ayuda(stringResource(R.string.tecla_arriba_abajo), stringResource(R.string.ayuda_recorrer))
    @Composable fun cambiar() = Ayuda(stringResource(R.string.tecla_izq_der), stringResource(R.string.ayuda_cambiar))
    @Composable fun elegir() = Ayuda(stringResource(R.string.tecla_ok), stringResource(R.string.ayuda_elegir))
    @Composable fun atras(texto: Int) = Ayuda(stringResource(R.string.tecla_atras), stringResource(texto))
}

/**
 * La imagen de una obra para mostrar como tapa o vista previa: abre la copia guardada sin pasarse del `ancho` pedido
 * (primero las medidas, después `inSampleSize`), en un hilo de fondo. null mientras carga o si no hay obra.
 */
@Composable
fun rememberTapa(obra: Obra?, ancho: Int): State<ImageBitmap?> {
    val ctx = LocalContext.current
    return produceState<ImageBitmap?>(null, obra?.driveId, ancho) {
        value = if (obra == null) null else withContext(Dispatchers.IO) {
            try {
                val f = Catalogo.archivoObra(Sincronizacion.dirBase(ctx), obra.driveId)
                if (!f.isFile) return@withContext null
                val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.absolutePath, medidas)
                if (medidas.outWidth <= 0 || medidas.outHeight <= 0) return@withContext null
                val m = Decodificacion.muestreo(medidas.outWidth, medidas.outHeight, ancho, ancho * 9 / 16)
                BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = m })?.asImageBitmap()
            } catch (e: Exception) {
                null
            } catch (e: OutOfMemoryError) {
                null
            }
        }
    }
}
