package local.museumslideshow.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import local.museumslideshow.R
import local.museumslideshow.ajustes.ListaAjustes
import local.museumslideshow.ajustes.Tema

/** Lo que la Barra muestra. `galerias` = (id, título) de las bajadas; `galeriaElegida` = índice en esa lista, o -1 = Todas. */
class DatosBarra(
    val galerias: List<Pair<String, String>>,
    val galeriaElegida: Int,
    val tema: Tema,
    val pantallaCompleta: Boolean,
    val explicacion: Boolean,
    val sincronizando: Boolean,
    val mensaje: String?,
) {
    /** Cuántos botones tiene cada fila: Galería (Todas + las bajadas), Tema (4) y las acciones (4). */
    val tamanos: List<Int> get() = listOf(1 + galerias.size, Tema.values().size, ACCIONES)

    /** Cuál es el valor actual de cada fila (para dónde cae el foco al cambiar de fila). */
    val seleccion: List<Int> get() = listOf(galeriaElegida + 1, tema.ordinal, -1)

    companion object {
        const val ACCIONES = 4
        const val FILA_GALERIA = 0
        const val FILA_TEMA = 1
        const val FILA_ACCIONES = 2
        const val ACC_PANTALLA_COMPLETA = 0
        const val ACC_EXPLICACION = 1
        const val ACC_AJUSTES = 2
        const val ACC_SINCRONIZAR = 3
    }
}

/**
 * La Barra: un panel abajo de todo, translúcido, que no tapa más de un tercio de la pantalla. Las flechas mueven el
 * foco (`foco`), OK elige. No maneja teclas: eso lo hace quien la usa con [NavegacionBarra].
 */
@Composable
fun Barra(datos: DatosBarra, foco: FocoBarra, modifier: Modifier = Modifier) {
    Escenario1080 {
        Column(
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Paleta.Barra)
                .padding(horizontal = 40.dp, vertical = 26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FilaChips(
                etiqueta = stringResource(R.string.barra_galeria),
                nombres = listOf(stringResource(R.string.v_todas)) + datos.galerias.map { it.second },
                elegido = datos.galeriaElegida + 1,
                foco = if (foco.fila == DatosBarra.FILA_GALERIA) foco.columna else -1,
            )
            FilaChips(
                etiqueta = stringResource(R.string.barra_tema),
                nombres = Tema.values().map { stringResource(ListaAjustes.nombreTema(it)) },
                elegido = datos.tema.ordinal,
                foco = if (foco.fila == DatosBarra.FILA_TEMA) foco.columna else -1,
            )
            val f = if (foco.fila == DatosBarra.FILA_ACCIONES) foco.columna else -1
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.width(ETIQUETA_ANCHO.dp))
                Boton(
                    stringResource(
                        if (datos.pantallaCompleta) R.string.barra_interruptor_si else R.string.barra_interruptor_no,
                        stringResource(R.string.barra_pantalla_completa),
                    ),
                    conFoco = f == DatosBarra.ACC_PANTALLA_COMPLETA, marcado = datos.pantallaCompleta,
                )
                Boton(
                    stringResource(
                        if (datos.explicacion) R.string.barra_interruptor_si else R.string.barra_interruptor_no,
                        stringResource(R.string.barra_explicacion),
                    ),
                    conFoco = f == DatosBarra.ACC_EXPLICACION, marcado = datos.explicacion,
                )
                Boton(stringResource(R.string.barra_ajustes), conFoco = f == DatosBarra.ACC_AJUSTES)
                Boton(stringResource(R.string.barra_sincronizar), conFoco = f == DatosBarra.ACC_SINCRONIZAR)
                val linea = if (datos.sincronizando) stringResource(R.string.estado_sincronizando) else datos.mensaje
                if (linea != null) {
                    BasicText(
                        linea,
                        modifier = Modifier.weight(1f).padding(start = 10.dp),
                        style = estiloTexto(26, Paleta.Dorado, 500),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private const val ETIQUETA_ANCHO = 150

@Composable
private fun FilaChips(etiqueta: String, nombres: List<String>, elegido: Int, foco: Int) {
    val estado = rememberLazyListState()
    // Con muchas galerías la fila se desplaza para que el foco siempre se vea.
    LaunchedEffect(foco) {
        if (foco >= 0) {
            val vis = estado.layoutInfo.visibleItemsInfo
            if (vis.isNotEmpty() && (foco <= vis.first().index || foco >= vis.last().index)) {
                estado.animateScrollToItem((foco - 1).coerceAtLeast(0))
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(etiqueta, modifier = Modifier.width(ETIQUETA_ANCHO.dp), style = estiloTexto(26, Paleta.Tenue, 500), maxLines = 1)
        LazyRow(state = estado, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            itemsIndexed(nombres) { i, n -> Boton(n, conFoco = i == foco, marcado = i == elegido) }
        }
    }
}
