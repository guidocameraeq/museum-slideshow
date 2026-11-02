package local.museumslideshow.pantallas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import local.museumslideshow.R
import local.museumslideshow.ajustes.EstadoGrupo
import local.museumslideshow.ajustes.GaleriasNuevas
import local.museumslideshow.ajustes.Preferencias
import local.museumslideshow.ajustes.VistaGalerias
import local.museumslideshow.ajustes.estaTildada
import local.museumslideshow.catalogo.Arbol
import local.museumslideshow.catalogo.CatalogoCargado
import local.museumslideshow.catalogo.FilaLista
import local.museumslideshow.catalogo.NodoArbol

private val ColFoco = Color(0xFFF4F1EA)
private val ColTextoFoco = Color(0xFF1A1A1C)
private val ColHalo = Color(0x48F4F1EA)
private val ColDoradoFoco = Color(0xFFB08A2E)
private val ColTextoTenueFoco = Color(0xFF55524B)

/** Las medidas de la grilla (el borrador 4: tarjetas de 390 x 322 cada 430 x 364). */
private object Medidas {
    const val TARJETA_ANCHO = 390
    const val TARJETA_ALTO = 322
    const val PASO_X = 430
    const val PASO_Y = 364
    const val IMAGEN_ALTO = 236
    const val MARGEN = 120
    const val NUEVAS_Y = 896
    const val NUEVAS_ALTO = 72
    const val FILA_LISTA_Y = 138
    const val FILA_LISTA_PASO = 66
    const val FILA_LISTA_ALTO = 58
    /** Cuánto se ve de la grilla: hasta arriba de "Galerías nuevas". */
    const val GRILLA_FIN = 890
    const val GRILLA_MARGEN = 26
}

private val SinColor = ColorMatrix().apply { setToSaturation(0f) }

/**
 * Galerías (borrador 4): elegir cuáles entran al pase. Vista Tarjetas (grilla de 4 con la tapa de cada galería y mosaico
 * para los grupos) o vista Lista (el árbol con tildes). Todo se guarda al instante (`cambiar`). Atrás sube un nivel y,
 * en el primero, vuelve (`alVolver`).
 */
@Composable
fun PantallaGalerias(
    cat: CatalogoCargado?,
    prefs: Preferencias,
    cambiar: ((Preferencias) -> Preferencias) -> Unit,
    activa: Boolean,
    alVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val arbol = remember(cat) { if (cat != null) Arbol(cat) else Arbol(emptyList(), emptyList()) }
    var estado by remember { mutableStateOf(EstadoGalerias()) }
    val vista = prefs.vistaGalerias
    val filas = if (vista == VistaGalerias.LISTA) arbol.filasLista(estado.abiertos, prefs) else emptyList()
    val e = NavegacionGalerias.normalizar(arbol, estado, vista, filas)

    Escenario1080 {
        Box(
            modifier
                .fillMaxSize()
                .background(Paleta.Fondo)
                .conTeclas(activa) { tecla ->
                    val vistaAhora = prefs.vistaGalerias
                    val filasAhora = if (vistaAhora == VistaGalerias.LISTA) arbol.filasLista(estado.abiertos, prefs) else emptyList()
                    val paso = NavegacionGalerias.paso(arbol, estado, tecla, vistaAhora, filasAhora)
                    estado = paso.estado
                    when (val ef = paso.efecto) {
                        null -> Unit
                        EfectoGalerias.Salir -> alVolver()
                        is EfectoGalerias.CambiarVista -> cambiar { it.copy(vistaGalerias = ef.vista) }
                        is EfectoGalerias.Alternar -> cambiar { arbol.alternar(it, ef.id) }
                        is EfectoGalerias.CambiarNuevas -> cambiar { it.copy(galeriasNuevas = siguienteNuevas(it.galeriasNuevas, ef.dir, ef.envolver)) }
                    }
                },
        ) {
            val grupoId = NavegacionGalerias.grupoActual(e)
            val enGrupo = grupoId != null && vista == VistaGalerias.TARJETAS
            Encabezado(arbol, prefs, e, vista, enGrupo)
            if (enGrupo) {
                val todas = arbol.estadoGrupo(grupoId!!, prefs) == EstadoGrupo.TODAS
                BotonTildarTodas(
                    stringResource(if (todas) R.string.galerias_destildar_todas else R.string.galerias_tildar_todas),
                    e.foco.zona == ZonaGalerias.TODAS,
                    Modifier.offset(Medidas.MARGEN.dp, 106.dp),
                )
            }

            val vacio = if (vista == VistaGalerias.TARJETAS) NavegacionGalerias.tarjetas(arbol, e).isEmpty() else filas.isEmpty()
            if (vacio) {
                BasicText(
                    stringResource(R.string.galerias_vacio),
                    modifier = Modifier.offset(Medidas.MARGEN.dp, 300.dp),
                    style = estiloTexto(32, Paleta.Tenue, 400),
                )
            } else if (vista == VistaGalerias.TARJETAS) {
                Grilla(arbol, prefs, e, enGrupo)
            } else {
                ListaArbol(filas, e)
            }

            FilaNuevas(prefs.galeriasNuevas, e.foco.zona == ZonaGalerias.NUEVAS)
            BarraAyuda(ayudas(vista, enGrupo), Modifier.align(Alignment.BottomCenter))
        }
    }
}

private fun siguienteNuevas(actual: GaleriasNuevas, dir: Int, envolver: Boolean): GaleriasNuevas {
    val v = GaleriasNuevas.values()
    val n = actual.ordinal + dir
    return v[if (envolver) Math.floorMod(n, v.size) else n.coerceIn(0, v.lastIndex)]
}

@Composable
private fun ayudas(vista: VistaGalerias, enGrupo: Boolean): List<Ayuda> = when {
    vista == VistaGalerias.LISTA -> listOf(
        Ayuda(stringResource(R.string.tecla_ok), stringResource(R.string.ayuda_tildar)),
        Ayuda(stringResource(R.string.tecla_der), stringResource(R.string.ayuda_abrir_grupo)),
        Ayuda(stringResource(R.string.tecla_izq), stringResource(R.string.ayuda_cerrar_grupo)),
        Ayudas.atras(R.string.ayuda_volver),
    )
    else -> listOf(
        Ayuda(stringResource(R.string.tecla_ok), stringResource(R.string.ayuda_tildar_o_abrir)),
        Ayudas.moverse(),
        Ayudas.atras(R.string.ayuda_volver),
    )
}

// ------------------------------------------------------------------------------------------------- Encabezado

@Composable
private fun Encabezado(arbol: Arbol, prefs: Preferencias, e: EstadoGalerias, vista: VistaGalerias, enGrupo: Boolean) {
    val titulo = stringResource(R.string.menu_galerias)
    val separador = stringResource(R.string.galerias_miga_separador)
    Row(Modifier.offset(Medidas.MARGEN.dp, 40.dp), verticalAlignment = Alignment.CenterVertically) {
        if (enGrupo) {
            BasicText(titulo, style = estiloTexto(52, Paleta.Tenue, 500), maxLines = 1)
            e.pila.forEachIndexed { i, id ->
                BasicText(separador, style = estiloTexto(52, Paleta.Tenue, 500), maxLines = 1)
                val ultimo = i == e.pila.lastIndex
                BasicText(
                    arbol.titulo(id) ?: "",
                    modifier = Modifier.width(if (ultimo) 760.dp else 330.dp),
                    style = estiloTexto(52, if (ultimo) Paleta.Texto else Paleta.Tenue, if (ultimo) 600 else 500),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            BasicText(titulo, style = estiloTexto(52, Paleta.Texto, 600), maxLines = 1)
        }
    }
    if (!enGrupo) {
        Selector(
            vista,
            if (e.foco.zona == ZonaGalerias.SELECTOR) e.foco.indice else -1,
            Modifier.offset(440.dp, 46.dp),
        )
    }
    val grupoId = NavegacionGalerias.grupoActual(e)
    val r = arbol.resumen(prefs, if (enGrupo) grupoId else null)
    val obras = pluralStringResource(R.plurals.cantidad_obras, r.obras, r.obras)
    val resumen = if (enGrupo) {
        stringResource(R.string.galerias_resumen_grupo, r.tildadas, r.total, obras)
    } else {
        stringResource(R.string.estado_resumen, pluralStringResource(R.plurals.galerias_tildadas, r.tildadas, r.tildadas), obras)
    }
    Box(Modifier.fillMaxWidth().padding(end = Medidas.MARGEN.dp, top = 54.dp), contentAlignment = Alignment.TopEnd) {
        BasicText(resumen, style = estiloTexto(34, Paleta.Dorado, 600).copy(textAlign = TextAlign.End), maxLines = 1)
    }
}

/** Tarjetas | Lista. `foco` = cuál tiene el foco (-1 ninguno); el elegido lleva borde dorado. */
@Composable
private fun Selector(vista: VistaGalerias, foco: Int, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Pastilla(stringResource(R.string.vista_tarjetas), vista == VistaGalerias.TARJETAS, foco == 0)
        Pastilla(stringResource(R.string.vista_lista), vista == VistaGalerias.LISTA, foco == 1)
    }
}

@Composable
private fun Pastilla(texto: String, elegida: Boolean, conFoco: Boolean) {
    val forma = RoundedCornerShape(30.dp)
    Box(
        Modifier
            .clip(forma)
            .background(if (conFoco) ColFoco else Color(0x1FFFFFFF))
            .then(if (elegida) Modifier.border(3.dp, if (conFoco) ColDoradoFoco else Paleta.Dorado, forma) else Modifier)
            .padding(horizontal = 26.dp, vertical = 8.dp),
    ) {
        BasicText(
            texto,
            style = estiloTexto(28, if (conFoco) ColTextoFoco else if (elegida) Paleta.Texto else Paleta.Tenue, 600),
            maxLines = 1,
        )
    }
}

@Composable
private fun BotonTildarTodas(texto: String, conFoco: Boolean, modifier: Modifier) {
    val forma = RoundedCornerShape(16.dp)
    Box(
        modifier
            .height(56.dp)
            .then(if (conFoco) Modifier.border(5.dp, ColHalo, forma) else Modifier)
            .clip(forma)
            .background(if (conFoco) ColFoco else Color(0x24FFFFFF))
            .padding(horizontal = 34.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(texto, style = estiloTexto(30, if (conFoco) ColTextoFoco else Paleta.Texto, 600), maxLines = 1)
    }
}

// ------------------------------------------------------------------------------------------------- Tarjetas

@Composable
private fun Grilla(arbol: Arbol, prefs: Preferencias, e: EstadoGalerias, enGrupo: Boolean) {
    val nodos = NavegacionGalerias.tarjetas(arbol, e)
    val origenY = if (enGrupo) 176 else 148
    val filasTotal = (nodos.size + NavegacionGalerias.COLUMNAS - 1) / NavegacionGalerias.COLUMNAS
    val primera = NavegacionGalerias.primeraFilaVisible(
        e.primeraFila, if (e.foco.zona == ZonaGalerias.ITEMS) e.foco.indice / NavegacionGalerias.COLUMNAS else e.primeraFila,
        NavegacionGalerias.FILAS_TARJETAS, filasTotal,
    )
    val arriba = origenY - Medidas.GRILLA_MARGEN
    // Se arman las filas que se ven y una más (que queda recortada): así su tapa ya está cargada al bajar.
    Box(Modifier.offset(0.dp, arriba.dp).fillMaxWidth().height((Medidas.GRILLA_FIN - arriba).dp).clip(androidx.compose.ui.graphics.RectangleShape)) {
        for (fila in primera..minOf(filasTotal - 1, primera + NavegacionGalerias.FILAS_TARJETAS)) {
            for (col in 0 until NavegacionGalerias.COLUMNAS) {
                val i = fila * NavegacionGalerias.COLUMNAS + col
                if (i >= nodos.size) break
                Tarjeta(
                    arbol, prefs, nodos[i],
                    conFoco = e.foco.zona == ZonaGalerias.ITEMS && e.foco.indice == i,
                    modifier = Modifier.offset(
                        (Medidas.MARGEN + col * Medidas.PASO_X).dp,
                        (Medidas.GRILLA_MARGEN + (fila - primera) * Medidas.PASO_Y).dp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun Tarjeta(arbol: Arbol, prefs: Preferencias, nodo: NodoArbol, conFoco: Boolean, modifier: Modifier) {
    val forma = RoundedCornerShape(20.dp)
    val estado = arbol.estadoGrupo(nodo.id, prefs)
    val apagada = estado == EstadoGrupo.NINGUNA && !nodo.esGrupo
    Box(
        modifier
            .size(Medidas.TARJETA_ANCHO.dp, Medidas.TARJETA_ALTO.dp)
            .scale(if (conFoco) 1.045f else 1f)
            .drawBehind {
                if (conFoco) {
                    drawRoundRect(ColHalo, Offset(-16.dp.toPx(), -16.dp.toPx()), Size(size.width + 32.dp.toPx(), size.height + 32.dp.toPx()), CornerRadius(34.dp.toPx()))
                }
            }
            .clip(forma)
            .background(Paleta.Panel),
    ) {
        if (nodo.esGrupo) {
            Mosaico(arbol, prefs, nodo.id)
            EtiquetaGrupo(estado, Modifier.offset(14.dp, 14.dp))
        } else {
            ImagenTapa(nodo.id, Medidas.TARJETA_ANCHO, Medidas.IMAGEN_ALTO, apagada, Modifier.fillMaxWidth().height(Medidas.IMAGEN_ALTO.dp))
        }
        if (estado != EstadoGrupo.NINGUNA) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 14.dp).size(58.dp).clip(CircleShape).background(Color(0xB30E0E10)),
                contentAlignment = Alignment.Center,
            ) { Tilde(estado, conFoco = false) }
        }
        val obras = arbol.cantidadObras(nodo.id)
        val textoObras = pluralStringResource(R.plurals.cantidad_obras, obras, obras)
        val sub = if (nodo.esGrupo) {
            val g = arbol.galeriasDe(nodo.id).size
            stringResource(R.string.galerias_sub_grupo, pluralStringResource(R.plurals.cantidad_galerias, g, g), textoObras)
        } else textoObras
        Column(Modifier.offset(22.dp, 246.dp).width((Medidas.TARJETA_ANCHO - 44).dp)) {
            BasicText(
                nodo.titulo,
                style = estiloTexto(32, if (conFoco) Color.White else if (apagada) Paleta.Tenue else Paleta.Texto, 600),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            BasicText(sub, style = estiloTexto(25, Paleta.Tenue, 400), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (conFoco) Box(Modifier.fillMaxSize().border(8.dp, ColFoco, forma))
    }
}

/** La tapa de una galería, cortada a todo el espacio. `apagada` = gris y oscura (la galería está destildada). */
@Composable
private fun ImagenTapa(galeriaId: String?, ancho: Int, alto: Int, apagada: Boolean, modifier: Modifier) {
    val img by rememberTapaGaleria(galeriaId, ancho, alto)
    Box(modifier.background(Color(0xFF2A2A2E))) {
        img?.let {
            Image(
                it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                colorFilter = if (apagada) ColorFilter.colorMatrix(SinColor) else null,
            )
        }
        if (apagada) Box(Modifier.fillMaxSize().background(Color(0x9E0E0E10)))
    }
}

/** El mosaico 2 x 2 de un grupo: las tapas de sus primeras galerías; lo que sobra queda en fondo oscuro. */
@Composable
private fun Mosaico(arbol: Arbol, prefs: Preferencias, grupoId: String) {
    val tapas = remember(arbol, grupoId) { arbol.mosaico(grupoId) }
    val ancho = (Medidas.TARJETA_ANCHO - 4) / 2
    val alto = (Medidas.IMAGEN_ALTO - 4) / 2
    Column(
        Modifier.fillMaxWidth().height(Medidas.IMAGEN_ALTO.dp).background(Paleta.Fondo),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (fila in 0..1) {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (col in 0..1) {
                    val g = tapas.getOrNull(fila * 2 + col)
                    if (g != null) {
                        ImagenTapa(g.driveId, ancho, alto, !prefs.estaTildada(g.driveId), Modifier.weight(1f).fillMaxSize())
                    } else {
                        Box(Modifier.weight(1f).fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun EtiquetaGrupo(estado: EstadoGrupo, modifier: Modifier) {
    val texto = stringResource(
        when (estado) {
            EstadoGrupo.TODAS -> R.string.galerias_grupo_todas
            EstadoGrupo.ALGUNAS -> R.string.galerias_grupo_algunas
            EstadoGrupo.NINGUNA -> R.string.galerias_grupo_ninguna
        },
    )
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xC70E0E10)).padding(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(30.dp)) {
            val w = size.width / 24f
            val p = Path().apply {
                moveTo(3 * w, 7 * w)
                quadraticTo(3 * w, 5 * w, 5 * w, 5 * w)
                lineTo(9 * w, 5 * w)
                lineTo(11 * w, 7.5f * w)
                lineTo(19 * w, 7.5f * w)
                quadraticTo(21 * w, 7.5f * w, 21 * w, 9.5f * w)
                lineTo(21 * w, 17 * w)
                quadraticTo(21 * w, 19 * w, 19 * w, 19 * w)
                lineTo(5 * w, 19 * w)
                quadraticTo(3 * w, 19 * w, 3 * w, 17 * w)
                close()
            }
            drawPath(p, Paleta.Dorado, style = Stroke(2f * w, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Spacer(Modifier.width(10.dp))
        BasicText(texto, style = estiloTexto(24, Paleta.Texto, 600), maxLines = 1)
    }
}

// ------------------------------------------------------------------------------------------------- Lista

@Composable
private fun ListaArbol(filas: List<FilaLista>, e: EstadoGalerias) {
    val primera = NavegacionGalerias.primeraFilaVisible(
        e.primeraFila, if (e.foco.zona == ZonaGalerias.ITEMS) e.foco.indice else e.primeraFila, NavegacionGalerias.FILAS_LISTA, filas.size,
    )
    val hasta = minOf(filas.size - 1, primera + NavegacionGalerias.FILAS_LISTA - 1)
    for (i in primera..hasta) {
        FilaDeLista(
            filas[i], e.foco.zona == ZonaGalerias.ITEMS && e.foco.indice == i,
            Modifier.offset(Medidas.MARGEN.dp, (Medidas.FILA_LISTA_Y + (i - primera) * Medidas.FILA_LISTA_PASO).dp),
        )
    }
}

@Composable
private fun FilaDeLista(f: FilaLista, conFoco: Boolean, modifier: Modifier) {
    val forma = RoundedCornerShape(14.dp)
    val apagada = f.estado == EstadoGrupo.NINGUNA
    val colorNombre = when {
        conFoco && apagada -> ColTextoTenueFoco
        conFoco -> ColTextoFoco
        apagada -> Paleta.Tenue
        else -> Paleta.Texto
    }
    val colorNumero = if (conFoco) ColTextoTenueFoco else Paleta.Tenue
    Row(
        modifier
            .width((1920 - 2 * Medidas.MARGEN).dp).height(Medidas.FILA_LISTA_ALTO.dp)
            .then(if (conFoco) Modifier.border(5.dp, ColHalo, forma) else Modifier)
            .clip(forma)
            .background(if (conFoco) ColFoco else Paleta.Panel)
            .padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width((64 * f.nivel).dp))
        Box(Modifier.size(34.dp)) { if (f.esGrupo) Flecha(f.abierto, colorNombre) }
        Spacer(Modifier.width(14.dp))
        Tilde(f.estado, conFoco)
        Spacer(Modifier.width(24.dp))
        BasicText(
            f.titulo,
            modifier = Modifier.weight(1f),
            style = estiloTexto(34, colorNombre, if (f.esGrupo) 600 else if (f.nivel > 0) 400 else 500),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (f.esGrupo) {
            BasicText(
                pluralStringResource(R.plurals.cantidad_galerias, f.galerias, f.galerias),
                style = estiloTexto(28, colorNumero.copy(alpha = 0.8f), 400), maxLines = 1,
            )
            Spacer(Modifier.width(22.dp))
        }
        BasicText(pluralStringResource(R.plurals.cantidad_obras, f.obras, f.obras), style = estiloTexto(28, colorNumero, 400), maxLines = 1)
    }
}

/** La flecha de un grupo: hacia abajo si está abierto, hacia la derecha si está cerrado. */
@Composable
private fun Flecha(abierto: Boolean, color: Color) {
    Canvas(Modifier.size(34.dp)) {
        val w = size.width / 24f
        val p = Path().apply {
            if (abierto) { moveTo(6 * w, 9 * w); lineTo(12 * w, 15 * w); lineTo(18 * w, 9 * w) }
            else { moveTo(9 * w, 6 * w); lineTo(15 * w, 12 * w); lineTo(9 * w, 18 * w) }
        }
        drawPath(p, color, style = Stroke(3.2f * w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// ------------------------------------------------------------------------------------------------- Piezas comunes

/** El tilde: todo (dorado con la marca), a medias (borde dorado con una raya) o ninguno (borde tenue). */
@Composable
private fun Tilde(estado: EstadoGrupo, conFoco: Boolean) {
    val dorado = if (conFoco) ColDoradoFoco else Paleta.Dorado
    val vacio = if (conFoco) Color(0xFF7A766D) else Paleta.Tenue
    Canvas(Modifier.size(44.dp)) {
        val lado = size.width
        val radio = CornerRadius(11.dp.toPx())
        val borde = 3.dp.toPx()
        when (estado) {
            EstadoGrupo.TODAS -> {
                drawRoundRect(dorado, Offset.Zero, size, radio)
                val p = Path().apply {
                    moveTo(lado * 0.27f, lado * 0.52f)
                    lineTo(lado * 0.43f, lado * 0.68f)
                    lineTo(lado * 0.74f, lado * 0.32f)
                }
                drawPath(p, Color(0xFF1A1608), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            EstadoGrupo.ALGUNAS -> {
                drawRoundRect(dorado, Offset(borde / 2, borde / 2), Size(lado - borde, lado - borde), radio, style = Stroke(borde))
                drawRoundRect(dorado, Offset(lado * 0.22f, lado * 0.44f), Size(lado * 0.56f, lado * 0.14f), CornerRadius(3.dp.toPx()))
            }
            EstadoGrupo.NINGUNA -> drawRoundRect(vacio, Offset(borde / 2, borde / 2), Size(lado - borde, lado - borde), radio, style = Stroke(borde))
        }
    }
}

/** "Galerías nuevas ‹ Tildadas ›": qué pasa con lo que el curador sube después. */
@Composable
private fun FilaNuevas(valor: GaleriasNuevas, conFoco: Boolean) {
    val forma = RoundedCornerShape(14.dp)
    val texto = stringResource(if (valor == GaleriasNuevas.TILDADAS) R.string.v_tildadas else R.string.v_destildadas)
    Row(
        Modifier
            .offset(Medidas.MARGEN.dp, Medidas.NUEVAS_Y.dp)
            .width((1920 - 2 * Medidas.MARGEN).dp).height(Medidas.NUEVAS_ALTO.dp)
            .then(if (conFoco) Modifier.border(5.dp, ColHalo, forma) else Modifier)
            .clip(forma)
            .background(if (conFoco) ColFoco else Paleta.Panel)
            .padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            stringResource(R.string.aj_galerias_nuevas),
            modifier = Modifier.weight(1f),
            style = estiloTexto(32, if (conFoco) ColTextoFoco else Paleta.Texto, if (conFoco) 600 else 500),
            maxLines = 1,
        )
        BasicText(
            "‹  $texto  ›",
            style = estiloTexto(32, if (conFoco) ColTextoFoco else Paleta.Tenue, 600),
            maxLines = 1,
        )
    }
}
