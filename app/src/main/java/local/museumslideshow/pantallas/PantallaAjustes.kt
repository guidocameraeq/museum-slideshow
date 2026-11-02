package local.museumslideshow.pantallas

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Calendar
import local.museumslideshow.R
import local.museumslideshow.ajustes.Etiqueta
import local.museumslideshow.ajustes.GrupoAjustes
import local.museumslideshow.ajustes.IdAjuste
import local.museumslideshow.ajustes.ItemAjuste
import local.museumslideshow.ajustes.ItemInfo
import local.museumslideshow.ajustes.ListaAjustes
import local.museumslideshow.ajustes.Preferencias
import local.museumslideshow.catalogo.Obra
import local.museumslideshow.reserva.EstadoReserva

@Composable
fun Etiqueta.texto(): String = when (this) {
    is Etiqueta.Res -> stringResource(id, *args.toTypedArray())
    is Etiqueta.Texto -> texto
}

/** Lo que Conexión muestra de la sincronización: si está en curso, el resultado de la última y cuándo fue la última buena. */
class DatosSync(val sincronizando: Boolean, val mensaje: String?, val ultimaBuena: Long?)

/**
 * Una de las pantallas de ajustes agrupados (Apariencia, Pase o Conexión y espacio). Arriba/Abajo recorren;
 * Izquierda/Derecha (y OK) cambian el valor, que se guarda al instante (`cambiar`). Atrás vuelve (`alVolver`).
 * Apariencia lleva una vista previa a la derecha (`obraPrevia` = la obra que se dibuja); Conexión suma al final el
 * botón "Sincronizar ahora" (`sync` no es null solo ahí). `activa` = tiene el foco del control.
 */
@Composable
fun PantallaAjustesGrupo(
    grupo: GrupoAjustes,
    items: List<ItemAjuste>,
    prefs: Preferencias,
    activa: Boolean,
    cambiar: ((Preferencias) -> Preferencias) -> Unit,
    alVolver: () -> Unit,
    modifier: Modifier = Modifier,
    obraPrevia: Obra? = null,
    galeriaPrevia: String? = null,
    reserva: EstadoReserva? = null,
    sync: DatosSync? = null,
    alSincronizar: () -> Unit = {},
) {
    val itemsActuales by rememberUpdatedState(items)
    val syncActual by rememberUpdatedState(alSincronizar)
    val cambiarActual by rememberUpdatedState(cambiar)
    val volverActual by rememberUpdatedState(alVolver)
    val filaSincronizar = if (sync != null) items.size else -1
    val cantidad = items.size + (if (sync != null) 1 else 0)
    var foco by remember { mutableIntStateOf(0) }
    val lista = rememberLazyListState()
    LaunchedEffect(foco) {
        val vis = lista.layoutInfo.visibleItemsInfo
        if (vis.isEmpty()) return@LaunchedEffect
        val primero = vis.first().index
        val ultimo = vis.last().index
        if (foco <= primero) lista.scrollToItem((foco - 1).coerceAtLeast(0))
        else if (foco >= ultimo) lista.scrollToItem((foco - (ultimo - primero) + 2).coerceAtLeast(0))
    }
    val compacta = grupo == GrupoAjustes.APARIENCIA

    Escenario1080 {
        Box(
            modifier
                .fillMaxSize()
                .background(Paleta.Fondo)
                .conTeclas(activa) { tecla ->
                    val paso = NavegacionLista.paso(foco, tecla, cantidad) { it == filaSincronizar }
                    foco = paso.indice
                    when (val a = paso.accion) {
                        AccionLista.Volver -> volverActual()
                        is AccionLista.Abrir -> syncActual()
                        is AccionLista.Cambiar -> {
                            val item = itemsActuales.getOrNull(a.indice)
                            if (item != null && item.editable) cambiarActual { p -> item.mover(p, a.dir, a.envolver) }
                        }
                        null -> Unit
                    }
                },
        ) {
            Column(Modifier.fillMaxSize().padding(start = 120.dp, end = 120.dp, top = 44.dp, bottom = 96.dp)) {
                BasicText(stringResource(titulo(grupo)), style = estiloTexto(52, Paleta.Texto, 600))
                Box(Modifier.padding(top = 10.dp, bottom = 18.dp)) {
                    BasicText(stringResource(pie(grupo)), style = estiloTexto(24, Paleta.Tenue, 400), maxLines = 1)
                }
                LazyColumn(
                    state = lista,
                    modifier = Modifier.weight(1f).then(if (compacta) Modifier.width(1070.dp) else Modifier.fillMaxWidth()),
                    verticalArrangement = Arrangement.spacedBy(if (compacta) 6.dp else 12.dp),
                ) {
                    itemsIndexed(items) { i, item ->
                        FilaAjuste(item, prefs, conFoco = activa && i == foco, compacta = compacta)
                    }
                    if (sync != null) {
                        item { FilaSincronizar(sync, conFoco = activa && foco == filaSincronizar) }
                    }
                }
            }
            if (grupo == GrupoAjustes.APARIENCIA) {
                VistaPrevia(
                    prefs, items, obraPrevia, galeriaPrevia, ayudaDe(items.getOrNull(foco)?.id),
                    Modifier.align(Alignment.TopEnd).padding(end = 120.dp, top = 158.dp),
                )
            }
            if (grupo == GrupoAjustes.CONEXION) {
                BarraReserva(reserva, prefs.reservaObras, Modifier.align(Alignment.BottomCenter).padding(start = 120.dp, end = 120.dp, bottom = 110.dp))
            }
            val ayudas = buildList {
                add(Ayudas.recorrer())
                if (foco == filaSincronizar) add(Ayuda(stringResource(R.string.tecla_ok), stringResource(R.string.ayuda_sincronizar)))
                else add(Ayudas.cambiar())
                add(Ayudas.atras(R.string.ayuda_volver))
            }
            BarraAyuda(ayudas, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@StringRes private fun titulo(g: GrupoAjustes): Int = when (g) {
    GrupoAjustes.APARIENCIA -> R.string.menu_apariencia
    GrupoAjustes.PASE -> R.string.menu_pase
    GrupoAjustes.CONEXION -> R.string.menu_conexion
}

@StringRes private fun pie(g: GrupoAjustes): Int = when (g) {
    GrupoAjustes.APARIENCIA -> R.string.pie_apariencia
    GrupoAjustes.PASE -> R.string.pie_pase
    GrupoAjustes.CONEXION -> R.string.pie_conexion
}

/** El texto de ayuda que acompaña a cada ajuste de Apariencia en la vista previa. */
@StringRes private fun ayudaDe(id: IdAjuste?): Int? = when (id) {
    IdAjuste.TEMA -> R.string.ayuda_tema
    IdAjuste.MODO -> R.string.ayuda_modo
    IdAjuste.HORA_DIA -> R.string.ayuda_hora_dia
    IdAjuste.HORA_NOCHE -> R.string.ayuda_hora_noche
    IdAjuste.PARED_CLARO -> R.string.ayuda_pared_claro
    IdAjuste.PARED_OSCURO -> R.string.ayuda_pared_oscuro
    IdAjuste.MARCO -> R.string.ayuda_marco
    IdAjuste.TAMANO -> R.string.ayuda_tamano
    IdAjuste.COMPLETA_CON_MARCO -> R.string.ayuda_completa_con_marco
    IdAjuste.EXPLICACION -> R.string.ayuda_explicacion
    IdAjuste.SALA -> R.string.ayuda_sala
    IdAjuste.CARTEL -> R.string.ayuda_cartel
    else -> null
}

@Composable
private fun FilaAjuste(item: ItemAjuste, prefs: Preferencias, conFoco: Boolean, compacta: Boolean) {
    val habilitado = (item as? ItemInfo)?.habilitado ?: true
    val forma = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(forma)
            .background(if (conFoco) Paleta.PanelClaro else Paleta.Panel)
            .padding(horizontal = 28.dp, vertical = if (compacta) 12.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            stringResource(item.nombre),
            modifier = Modifier.width(if (compacta) 470.dp else 560.dp),
            style = estiloTexto(30, if (habilitado) Paleta.Texto else Paleta.Tenue, if (conFoco) 600 else 500),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Column(Modifier.weight(1f)) {
            val flechas = conFoco && item.editable
            BasicText(
                (if (flechas) "‹  " else "") + item.valor(prefs).texto() + (if (flechas) "  ›" else ""),
                style = estiloTexto(30, if (!habilitado) Paleta.Tenue else if (conFoco) Paleta.Dorado else Paleta.Texto, 600),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!compacta) item.ayuda?.let { BasicText(stringResource(it), style = estiloTexto(22, Paleta.Tenue, 400), maxLines = 1) }
        }
    }
}

/** El botón "Sincronizar ahora" de Conexión, con el resultado en una línea abajo. */
@Composable
private fun FilaSincronizar(sync: DatosSync, conFoco: Boolean) {
    val res = LocalContext.current.resources
    val linea = when {
        sync.sincronizando -> res.getString(R.string.estado_sincronizando)
        sync.mensaje != null -> sync.mensaje
        sync.ultimaBuena != null -> res.getString(R.string.conexion_ultima_vez, fechaHora(res, sync.ultimaBuena))
        else -> res.getString(R.string.conexion_nunca)
    }
    val forma = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(forma)
            .background(if (conFoco) Paleta.PanelClaro else Paleta.Panel)
            .padding(horizontal = 28.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            stringResource(R.string.barra_sincronizar),
            modifier = Modifier.width(560.dp),
            style = estiloTexto(30, Paleta.Texto, if (conFoco) 600 else 500),
            maxLines = 1,
        )
        BasicText(
            linea,
            modifier = Modifier.weight(1f),
            style = estiloTexto(26, if (conFoco) Paleta.Dorado else Paleta.Tenue, 500),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** La vista previa chiquita: la pared del color elegido con una obra con su marco, y qué hace el ajuste enfocado. */
@Composable
private fun VistaPrevia(prefs: Preferencias, items: List<ItemAjuste>, obra: Obra?, galeriaId: String?, @StringRes ayuda: Int?, modifier: Modifier) {
    val ahora = Calendar.getInstance()
    val d = DatosVistaPrevia.de(prefs, ahora.get(Calendar.HOUR_OF_DAY) * 60 + ahora.get(Calendar.MINUTE))
    val imagen by rememberTapa(obra, galeriaId, 520)
    val tema = items.firstOrNull { it.id == IdAjuste.TEMA }?.valor(prefs)?.texto().orEmpty()
    val tamano = items.firstOrNull { it.id == IdAjuste.TAMANO }?.valor(prefs)?.texto().orEmpty()
    val pared = ListaAjustes.nombreColor(d.idColor)?.let { stringResource(it) } ?: d.idColor
    Column(modifier.width(540.dp)) {
        Box(
            Modifier
                .fillMaxWidth().height(340.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(Color(d.paredArriba), Color(d.paredAbajo)))),
            contentAlignment = Alignment.Center,
        ) {
            val forma = RoundedCornerShape(0.dp)
            Box(
                Modifier
                    .width((364 * d.escala).dp).height((280 * d.escala).dp)
                    .then(if (d.marco == "sin") Modifier else Modifier.border(12.dp, Color(0xFF0B0B0B), forma))
                    .background(Color(0xFF2A2A2E)),
            ) {
                imagen?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            }
        }
        Column(Modifier.padding(top = 18.dp)) {
            BasicText(stringResource(R.string.vista_previa), style = estiloTexto(24, Paleta.Texto, 600))
            BasicText(
                stringResource(R.string.vista_previa_texto, tema, pared.lowercase(), tamano.lowercase()),
                style = estiloTexto(24, Paleta.Tenue, 400),
            )
            if (ayuda != null) {
                Box(Modifier.padding(top = 8.dp)) {
                    BasicText(stringResource(ayuda), style = estiloTexto(24, Paleta.Tenue.copy(alpha = 0.8f), 400))
                }
            }
        }
    }
}

/**
 * La barrita de la Reserva de obras (Conexión y espacio): cuántas de las próximas ya están bajadas (dorado) y cuántas de las
 * ya vistas se guardan para volver (gris), sobre el total que se guarda como máximo (N + las 10 vistas).
 */
@Composable
private fun BarraReserva(reserva: EstadoReserva?, ajusteN: Int, modifier: Modifier) {
    val res = LocalContext.current.resources
    val conPedido = reserva?.conPedido == true
    val de = if (conPedido) reserva!!.porDelante else ajusteN
    val listas = if (conPedido) reserva!!.listasPorDelante else minOf(reserva?.obrasGuardadas ?: 0, de)
    val vistas = if (conPedido) reserva!!.vistasGuardadas else 0
    val total = (de + local.museumslideshow.reserva.Reserva.VISTAS).coerceAtLeast(1)
    val bytes = reserva?.bytes ?: 0L
    Column(
        modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)).background(Paleta.Panel).padding(horizontal = 30.dp, vertical = 22.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            BasicText(stringResource(R.string.aj_reserva), style = estiloTexto(34, Paleta.Texto, 600))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                BasicText(
                    res.getString(R.string.reserva_listas, listas, de, tamano(res, bytes)),
                    style = estiloTexto(34, Paleta.Dorado, 600), maxLines = 1,
                )
            }
        }
        Row(
            Modifier.padding(top = 20.dp).fillMaxWidth().height(30.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFF0A0A0B)),
        ) {
            if (listas > 0) Box(Modifier.weight(listas.toFloat()).fillMaxHeight().background(Brush.horizontalGradient(listOf(Color(0xFF9A7C36), Paleta.Dorado))))
            if (vistas > 0) Box(Modifier.weight(vistas.toFloat()).fillMaxHeight().background(Color(0xFF5D5A52)))
            val resto = total - listas - vistas
            if (resto > 0) Box(Modifier.weight(resto.toFloat()).fillMaxHeight())
        }
        Row(Modifier.padding(top = 20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Cuadradito(Paleta.Dorado)
            BasicText(stringResource(R.string.reserva_leyenda_proximas), Modifier.padding(start = 10.dp), style = estiloTexto(24, Paleta.Tenue, 400), maxLines = 1)
            Box(Modifier.width(44.dp))
            Cuadradito(Color(0xFF5D5A52))
            BasicText(
                res.getString(R.string.reserva_leyenda_vistas, local.museumslideshow.reserva.Reserva.VISTAS),
                Modifier.padding(start = 10.dp), style = estiloTexto(24, Paleta.Tenue, 400), maxLines = 1,
            )
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                BasicText(stringResource(R.string.reserva_leyenda_internet), style = estiloTexto(24, Paleta.Tenue, 400), maxLines = 1)
            }
        }
    }
}

@Composable
private fun Cuadradito(color: Color) {
    Box(Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(color))
}
