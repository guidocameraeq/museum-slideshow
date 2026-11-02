package local.museumslideshow.pantallas

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import local.museumslideshow.R
import local.museumslideshow.ajustes.Etiqueta
import local.museumslideshow.ajustes.ItemAbrirEstado
import local.museumslideshow.ajustes.ItemAjuste
import local.museumslideshow.ajustes.ItemInfo
import local.museumslideshow.ajustes.Preferencias
import local.museumslideshow.galeria.esTeclaVolumen

@Composable
fun Etiqueta.texto(): String = when (this) {
    is Etiqueta.Res -> stringResource(id, *args.toTypedArray())
    is Etiqueta.Texto -> texto
}

/**
 * Pantalla completa con todos los ajustes. Arriba/Abajo recorren; Izquierda/Derecha (y OK) cambian el valor, que se
 * guarda al instante (`cambiar`). Atrás vuelve (`alVolver`). La entrada Estado abre esa pantalla (`alAbrirEstado`).
 * `activa` = tiene el foco del control (false mientras Estado está encima).
 */
@Composable
fun PantallaAjustes(
    items: List<ItemAjuste>,
    prefs: Preferencias,
    activa: Boolean,
    cambiar: ((Preferencias) -> Preferencias) -> Unit,
    alAbrirEstado: () -> Unit,
    alVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemsActuales by rememberUpdatedState(items)
    val prefsActuales by rememberUpdatedState(prefs)
    val activaActual by rememberUpdatedState(activa)
    var foco by remember { mutableIntStateOf(0) }
    val pedirFoco = remember { FocusRequester() }
    val lista = rememberLazyListState()
    LaunchedEffect(activa) { if (activa) try { pedirFoco.requestFocus() } catch (e: Exception) { /* todavía no está listo */ } }
    LaunchedEffect(foco) {
        val vis = lista.layoutInfo.visibleItemsInfo
        if (vis.isEmpty()) return@LaunchedEffect
        val primero = vis.first().index
        val ultimo = vis.last().index
        if (foco <= primero) lista.scrollToItem((foco - 1).coerceAtLeast(0))
        else if (foco >= ultimo) lista.scrollToItem((foco - (ultimo - primero) + 2).coerceAtLeast(0))
    }

    Escenario1080 {
        Column(
            modifier
                .fillMaxSize()
                .background(Paleta.Fondo)
                .padding(horizontal = 120.dp, vertical = 44.dp)
                .focusRequester(pedirFoco)
                .focusable()
                .onPreviewKeyEvent { ev ->
                    val n = ev.nativeKeyEvent
                    if (!activaActual || esTeclaVolumen(n.keyCode)) return@onPreviewKeyEvent false
                    val tecla = teclaDe(n.keyCode) ?: return@onPreviewKeyEvent false
                    // Las flechas de izquierda/derecha y arriba/abajo repiten si se mantienen; OK y Atrás, una vez.
                    val repite = tecla != Tecla.OK && tecla != Tecla.ATRAS
                    if (n.action == KeyEvent.ACTION_DOWN && (n.repeatCount == 0 || repite)) {
                        val paso = NavegacionLista.paso(foco, tecla, itemsActuales.size) { itemsActuales[it] is ItemAbrirEstado }
                        foco = paso.indice
                        when (val a = paso.accion) {
                            AccionLista.Volver -> alVolver()
                            is AccionLista.Abrir -> alAbrirEstado()
                            is AccionLista.Cambiar -> {
                                val item = itemsActuales[a.indice]
                                if (item.editable) cambiar { p -> item.mover(p, a.dir, a.envolver) }
                            }
                            null -> Unit
                        }
                    }
                    true
                },
        ) {
            BasicText(stringResource(R.string.ajustes_titulo), style = estiloTexto(52, Paleta.Texto, 600))
            Box(Modifier.padding(top = 10.dp, bottom = 18.dp)) {
                BasicText(stringResource(R.string.ajustes_pie), style = estiloTexto(24, Paleta.Tenue, 400), maxLines = 1)
            }
            LazyColumn(state = lista, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(items) { i, item -> FilaAjuste(item, prefs, conFoco = activa && i == foco) }
            }
        }
    }
}

@Composable
private fun FilaAjuste(item: ItemAjuste, prefs: Preferencias, conFoco: Boolean) {
    val habilitado = (item as? ItemInfo)?.habilitado ?: true
    val forma = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(forma)
            .background(if (conFoco) Paleta.PanelClaro else Paleta.Panel)
            .padding(horizontal = 28.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            stringResource(item.nombre),
            modifier = Modifier.width(660.dp),
            style = estiloTexto(32, if (habilitado) Paleta.Texto else Paleta.Tenue, if (conFoco) 600 else 500),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Column(Modifier.weight(1f)) {
            val flechas = conFoco && item.editable
            BasicText(
                (if (flechas) "‹  " else "") + item.valor(prefs).texto() + (if (flechas) "  ›" else ""),
                style = estiloTexto(32, if (!habilitado) Paleta.Tenue else if (conFoco) Paleta.Dorado else Paleta.Texto, 600),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.ayuda?.let { BasicText(stringResource(it), style = estiloTexto(22, Paleta.Tenue, 400), maxLines = 1) }
        }
    }
}
