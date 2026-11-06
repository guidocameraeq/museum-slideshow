package local.museumslideshow.pantallas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import local.museumslideshow.R
import local.museumslideshow.ajustes.ListaAjustes
import local.museumslideshow.ajustes.Tema

/**
 * Lo que el panel muestra. `galeriaPuesta` = posición de la galería puesta en `items`; `restante` se lee al dibujar (la
 * barrita que se vacía no vuelve a armar el panel); `conCierre` = el cierre automático está prendido.
 */
class DatosPanel(
    val estado: EstadoPanel,
    val items: List<ItemGalerias>,
    val galeriaPuesta: Int,
    val tema: Tema,
    val pantallaCompleta: Boolean,
    val explicacion: Boolean,
    val sincronizando: Boolean,
    val mensaje: String?,
    val conCierre: Boolean,
    val restante: () -> Float,
)

private const val ANCHO_PANEL = 680
private val FondoPanel = Color(0xD90E0E10)
private val ColFoco = Color(0xFFF4F1EA)
private val TextoFoco = Color(0xFF1A1A1C)
private val DoradoFoco = Color(0xFF7A5D12)
private val GrupoGris = Color(0xFF7D7A72)

/**
 * La Barra lateral (borrador 7, Propuesta C): un panel a la izquierda, un tercio de la pantalla. No maneja teclas: eso lo
 * hace quien la usa con [NavegacionPanel]. Va en un [Escenario1080] propio.
 */
@Composable
fun PanelLateral(datos: DatosPanel, modifier: Modifier = Modifier) {
    Escenario1080 {
        Box(modifier.width(ANCHO_PANEL.dp).fillMaxHeight().background(FondoPanel)) {
            when (datos.estado.nivel) {
                NivelPanel.MENU -> MenuPanel(datos)
                NivelPanel.GALERIAS -> SublistaGalerias(datos)
                NivelPanel.TEMAS -> SublistaTemas(datos)
            }
            if (datos.conCierre) Barrita(datos.restante, Modifier.align(Alignment.TopStart))
            AyudaPanel(datos.estado.nivel, Modifier.align(Alignment.BottomStart))
        }
    }
}

// ------------------------------------------------------------------------------------------------- Menú

@Composable
private fun MenuPanel(datos: DatosPanel) {
    val foco = datos.estado.foco
    val puesta = datos.items.getOrNull(datos.galeriaPuesta)
    val galeriaNombre = when (puesta) {
        is ItemGalerias.Una -> puesta.titulo
        else -> stringResource(R.string.v_todas)
    }
    val si = stringResource(R.string.v_si)
    val no = stringResource(R.string.v_no)
    Column(Modifier.fillMaxSize().padding(top = 40.dp)) {
        RenglonMenu(
            stringResource(R.string.barra_galeria), galeriaNombre, foco == Renglon.GALERIA.ordinal, entra = true,
            tapa = { TapaChica(if (puesta is ItemGalerias.Una) puesta.id else null, 76, 52) },
        )
        RenglonMenu(stringResource(R.string.barra_tema), stringResource(ListaAjustes.nombreTema(datos.tema)), foco == Renglon.TEMA.ordinal, entra = true)
        RenglonMenu(
            stringResource(R.string.barra_pantalla_completa), if (datos.pantallaCompleta) si else no,
            foco == Renglon.PANTALLA_COMPLETA.ordinal, interruptor = datos.pantallaCompleta,
        )
        RenglonMenu(
            stringResource(R.string.barra_explicacion), if (datos.explicacion) si else no,
            foco == Renglon.EXPLICACION.ordinal, interruptor = datos.explicacion,
        )
        RenglonMenu(stringResource(R.string.barra_ajustes), null, foco == Renglon.AJUSTES.ordinal, entra = true)
        RenglonMenu(stringResource(R.string.barra_sincronizar), null, foco == Renglon.SINCRONIZAR.ordinal)
    }
    val linea = if (datos.sincronizando) stringResource(R.string.estado_sincronizando) else datos.mensaje
    if (linea != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
            BasicText(
                linea,
                modifier = Modifier.padding(start = 48.dp, end = 40.dp, bottom = 108.dp),
                style = estiloTexto(24, Paleta.Dorado, 500),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RenglonMenu(
    titulo: String,
    valor: String?,
    conFoco: Boolean,
    entra: Boolean = false,
    interruptor: Boolean? = null,
    tapa: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .padding(horizontal = 24.dp)
            .padding(bottom = 6.dp)
            .fillMaxWidth()
            .height(112.dp)
            .graphicsLayer { val k = if (conFoco) 1.03f else 1f; scaleX = k; scaleY = k }
            .clip(RoundedCornerShape(16.dp))
            .background(if (conFoco) ColFoco else Color.Transparent)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tapa != null) {
            tapa()
            Spacer(Modifier.width(20.dp))
        }
        BasicText(titulo, style = estiloTexto(34, if (conFoco) TextoFoco else Paleta.Texto, if (conFoco) 600 else 500), maxLines = 1)
        Spacer(Modifier.weight(1f))
        if (valor != null) {
            val texto = if (entra) stringResource(R.string.panel_valor_flecha, valor) else valor
            BasicText(
                texto,
                style = estiloTexto(26, if (conFoco) DoradoFoco else Paleta.Dorado, 500),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(2f, fill = false),
            )
        } else if (entra) {
            BasicText(stringResource(R.string.panel_flecha), style = estiloTexto(26, if (conFoco) DoradoFoco else Paleta.Dorado, 500))
        }
        if (interruptor != null) {
            Spacer(Modifier.width(14.dp))
            Interruptor(interruptor)
        }
    }
}

@Composable
private fun Interruptor(si: Boolean) {
    Box(
        Modifier.size(68.dp, 36.dp).clip(RoundedCornerShape(18.dp)).background(if (si) Paleta.Dorado else Color(0xFF4A4A50)),
    ) {
        Box(
            Modifier
                .padding(start = if (si) 36.dp else 4.dp, top = 4.dp)
                .size(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (si) TextoFoco else Color(0xFFD8D5CD)),
        )
    }
}

/** La tapa de una galería (o la estrella de "Todas" si no hay id), cortada a todo el espacio. */
@Composable
private fun TapaChica(galeriaId: String?, ancho: Int, alto: Int) {
    val forma = RoundedCornerShape(if (ancho > 100) 8.dp else 6.dp)
    if (galeriaId == null) {
        Box(Modifier.size(ancho.dp, alto.dp).clip(forma).background(Paleta.PanelClaro), contentAlignment = Alignment.Center) {
            BasicText(stringResource(R.string.panel_estrella), style = estiloTexto(if (ancho > 100) 40 else 30, Paleta.Texto, 600))
        }
        return
    }
    val img by rememberTapaGaleria(galeriaId, ancho, alto)
    Box(Modifier.size(ancho.dp, alto.dp).clip(forma).background(Color(0xFF333333))) {
        img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

// ------------------------------------------------------------------------------------------------- Galería

@Composable
private fun SublistaGalerias(datos: DatosPanel) {
    val items = datos.items
    val foco = datos.estado.foco
    // Una lista nueva por vez: arranca ya con el foco a la vista (sin recorrer la lista desde arriba).
    val lista = rememberLazyListState(initialFirstVisibleItemIndex = (foco - 2).coerceAtLeast(0))
    val d = LocalDensity.current.density

    LaunchedEffect(foco) {
        val info = lista.layoutInfo
        val fila = info.visibleItemsInfo.firstOrNull { it.index == foco }
        when {
            // Arriba de todo no queda ninguna galería: se muestra el principio de la lista (con "Todas").
            NavegacionPanel.siguienteElegible(items, foco, -1) == null -> lista.animateScrollToItem(0)
            fila == null -> lista.scrollToItem((foco - 2).coerceAtLeast(0))
            else -> {
                val arriba = info.viewportStartOffset + 70 * d
                val abajo = info.viewportEndOffset - 100 * d
                if (fila.offset < arriba) lista.animateScrollBy(fila.offset - arriba)
                else if (fila.offset + fila.size > abajo) lista.animateScrollBy(fila.offset + fila.size - abajo)
            }
        }
    }
    val fuera by remember(items) {
        derivedStateOf {
            val v = lista.layoutInfo.visibleItemsInfo
            if (v.isEmpty()) 0 to 0 else NavegacionPanel.fueraDeVista(items, v.first().index, v.last().index)
        }
    }

    Box(Modifier.fillMaxSize()) {
        BasicText(
            stringResource(R.string.panel_atras_titulo, stringResource(R.string.barra_galeria)),
            modifier = Modifier.padding(start = 44.dp, top = 40.dp),
            style = estiloTexto(28, Paleta.Tenue, 500),
            maxLines = 1,
        )
        LazyColumn(
            state = lista,
            // Una franja propia arriba y abajo para los "▲/▼ N más": la lista nunca pasa por debajo de ellos (antes se encimaban).
            modifier = Modifier.fillMaxSize().padding(top = (96 + ALTO_INDICADOR).dp, bottom = (84 + ALTO_INDICADOR).dp),
        ) {
            itemsIndexed(items) { i, item ->
                when (item) {
                    is ItemGalerias.Encabezado -> EncabezadoGrupo(item.titulo)
                    else -> FilaGaleria(item, conFoco = i == foco, puesta = i == datos.galeriaPuesta)
                }
            }
        }
        if (fuera.first > 0) {
            Indicador(
                pluralStringResource(R.plurals.panel_mas_arriba, fuera.first, fuera.first), arriba = true,
                modifier = Modifier.align(Alignment.TopStart).padding(top = 96.dp),
            )
        }
        if (fuera.second > 0) {
            Indicador(
                pluralStringResource(R.plurals.panel_mas_abajo, fuera.second, fuera.second), arriba = false,
                modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 84.dp),
            )
        }
    }
}

@Composable
private fun Indicador(texto: String, @Suppress("UNUSED_PARAMETER") arriba: Boolean, modifier: Modifier) {
    Box(modifier.fillMaxWidth().height(ALTO_INDICADOR.dp), contentAlignment = Alignment.Center) {
        BasicText(texto, style = estiloTexto(24, Paleta.Tenue, 500))
    }
}

/** Alto de la franja de "▲/▼ N más" (dp): la lista no se dibuja ahí. */
private const val ALTO_INDICADOR = 40

@Composable
private fun EncabezadoGrupo(titulo: String) {
    Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 44.dp), contentAlignment = Alignment.BottomStart) {
        BasicText(
            titulo.uppercase(),
            modifier = Modifier.padding(bottom = 8.dp),
            style = estiloTexto(22, GrupoGris, 600).copy(letterSpacing = 3.sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FilaGaleria(item: ItemGalerias, conFoco: Boolean, puesta: Boolean) {
    val (titulo, obras, id) = when (item) {
        is ItemGalerias.Todas -> Triple(stringResource(R.string.v_todas), item.obras, null)
        is ItemGalerias.Una -> Triple(item.titulo, item.obras, item.id)
        is ItemGalerias.Encabezado -> return
    }
    Row(
        Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .height(104.dp)
            .graphicsLayer { val k = if (conFoco) 1.03f else 1f; scaleX = k; scaleY = k }
            .clip(RoundedCornerShape(16.dp))
            .background(if (conFoco) ColFoco else Color.Transparent)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        TapaChica(id, 116, 78)
        Column(Modifier.weight(1f)) {
            BasicText(
                titulo, style = estiloTexto(32, if (conFoco) TextoFoco else Paleta.Texto, if (conFoco) 600 else 500),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                pluralStringResource(R.plurals.cantidad_obras, obras, obras),
                style = estiloTexto(24, if (conFoco) Color(0xFF55524A) else Paleta.Tenue, 400), maxLines = 1,
            )
        }
        if (puesta) BasicText(stringResource(R.string.panel_tilde), style = estiloTexto(40, if (conFoco) Color(0xFFB08A2E) else Paleta.Dorado, 600))
    }
}

// ------------------------------------------------------------------------------------------------- Tema

@Composable
private fun SublistaTemas(datos: DatosPanel) {
    Box(Modifier.fillMaxSize()) {
        BasicText(
            stringResource(R.string.panel_atras_titulo, stringResource(R.string.barra_tema)),
            modifier = Modifier.padding(start = 44.dp, top = 40.dp),
            style = estiloTexto(28, Paleta.Tenue, 500),
            maxLines = 1,
        )
        Column(Modifier.fillMaxSize().padding(top = 96.dp)) {
            for (t in Tema.values()) {
                val conFoco = datos.estado.foco == t.ordinal
                Row(
                    Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .height(122.dp)
                        .graphicsLayer { val k = if (conFoco) 1.03f else 1f; scaleX = k; scaleY = k }
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (conFoco) ColFoco else Color.Transparent)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    MiniaturaTema(t)
                    BasicText(
                        stringResource(ListaAjustes.nombreTema(t)),
                        modifier = Modifier.weight(1f),
                        style = estiloTexto(32, if (conFoco) TextoFoco else Paleta.Texto, if (conFoco) 600 else 500),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    // La tilde marca el tema guardado de verdad; el foco es solo una prueba.
                    if (t == datos.tema) BasicText(stringResource(R.string.panel_tilde), style = estiloTexto(40, if (conFoco) Color(0xFFB08A2E) else Paleta.Dorado, 600))
                }
            }
        }
    }
}

/** Una "obra" de mentira para las miniaturas: dos tonos, sin imágenes. */
private val ObraMini = Brush.verticalGradient(listOf(Color(0xFFA68762), Color(0xFF55402C)))

/** La miniatura de un tema (168 x 94, formas simples como en el borrador): se dibuja sola y no cambia. */
@Composable
private fun MiniaturaTema(tema: Tema) {
    Canvas(Modifier.size(168.dp, 94.dp).clip(RoundedCornerShape(8.dp))) {
        val k = size.width / 168f
        when (tema) {
            Tema.GALERIA_MODERNA -> {
                drawRect(Color(0xFFE9E5DC))
                rect(k, 46f, 10f, 76f, 52f, ObraMini)
                borde(k, 46f, 10f, 76f, 52f, 4f, Color(0xFF1C1C1C))
                rect(k, 60f, 70f, 48f, 10f, Color.White)
                borde(k, 60f, 70f, 48f, 10f, 1f, Color(0xFFBBBBBB))
            }
            Tema.SALA_OSCURA -> {
                drawRect(Color(0xFF050505))
                rect(k, 46f, 10f, 76f, 52f, ObraMini)
                borde(k, 46f, 10f, 76f, 52f, 3f, Color(0xFF7A5D12))
                borde(k, 48f, 12f, 72f, 48f, 1.5f, Paleta.Dorado)
                rect(k, 60f, 70f, 48f, 6f, Color(0xB3C9A54E))
            }
            Tema.INMERSIVO -> {
                drawRect(Color(0xFF222222))
                rect(k, 0f, 0f, 168f, 94f, ObraMini, alfa = 0.45f)
                rect(k, 12f, 6f, 144f, 82f, ObraMini)
            }
            Tema.LIBRO_DE_ARTE -> {
                drawRect(Color(0xFFF3EEE3))
                rect(k, 10f, 12f, 76f, 70f, ObraMini)
                val linea = Color(0xFF8B857A)
                rect(k, 96f, 18f, 56f, 5f, linea)
                rect(k, 96f, 32f, 44f, 5f, linea)
                rect(k, 96f, 46f, 52f, 5f, linea)
                rect(k, 96f, 60f, 36f, 5f, linea)
            }
        }
    }
}

private fun DrawScope.rect(k: Float, x: Float, y: Float, w: Float, h: Float, color: Color) =
    drawRect(color, Offset(x * k, y * k), Size(w * k, h * k))

private fun DrawScope.rect(k: Float, x: Float, y: Float, w: Float, h: Float, brush: Brush, alfa: Float = 1f) =
    drawRect(brush, Offset(x * k, y * k), Size(w * k, h * k), alpha = alfa)

/** Un borde de `g` de grosor que queda adentro del rectángulo. */
private fun DrawScope.borde(k: Float, x: Float, y: Float, w: Float, h: Float, g: Float, color: Color) =
    drawRect(color, Offset((x + g / 2) * k, (y + g / 2) * k), Size((w - g) * k, (h - g) * k), style = Stroke(g * k))

// ------------------------------------------------------------------------------------------------- Detalles

/** La barrita fina de arriba que se va vaciando hasta que el panel se cierra solo. */
@Composable
private fun Barrita(restante: () -> Float, modifier: Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(10.dp)
            .background(Color(0x1FFFFFFF))
            .drawBehind { drawRect(Paleta.Dorado, size = Size(size.width * restante().coerceIn(0f, 1f), size.height)) },
    )
}

/** Las teclas, abajo del panel. */
@Composable
private fun AyudaPanel(nivel: NivelPanel, modifier: Modifier) {
    val ayudas = when (nivel) {
        NivelPanel.MENU -> listOf(Ayudas.recorrer(), Ayudas.elegir(), Ayudas.atras(R.string.ayuda_cerrar))
        NivelPanel.GALERIAS -> listOf(Ayudas.recorrer(), Ayudas.elegir(), Ayudas.atras(R.string.ayuda_al_menu))
        NivelPanel.TEMAS -> listOf(
            Ayuda(stringResource(R.string.tecla_arriba_abajo), stringResource(R.string.ayuda_probar)),
            Ayuda(stringResource(R.string.tecla_ok), stringResource(R.string.ayuda_quedarme)),
            Ayuda(stringResource(R.string.tecla_atras), stringResource(R.string.ayuda_cancelar)),
        )
    }
    Row(
        modifier.fillMaxWidth().height(84.dp).background(Color(0xD9000000)).padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        for (a in ayudas) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BasicText(
                    a.tecla,
                    modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(Color(0x24FFFFFF)).padding(horizontal = 10.dp, vertical = 3.dp),
                    style = estiloTexto(22, Paleta.Texto, 600).copy(textAlign = TextAlign.Center),
                    maxLines = 1,
                )
                BasicText(a.texto, style = estiloTexto(24, Paleta.Tenue, 400), maxLines = 1)
            }
        }
    }
}
