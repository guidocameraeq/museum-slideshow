package local.museumslideshow.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import local.museumslideshow.R
import local.museumslideshow.actualizacion.Actualizador
import local.museumslideshow.actualizacion.EstadoActualizacion
import local.museumslideshow.ajustes.ItemAjuste
import local.museumslideshow.ajustes.Preferencias

/** Las filas que se pueden elegir en Actualizaciones (las dos de arriba son solo informativas). */
private enum class FilaAct { ACTUALIZAR, BUSCAR_SOLA, BUSCAR_AHORA }

/**
 * Actualizaciones (borrador 8): la versión instalada y la última de GitHub; si hay una nueva, "Actualizar" (o "Dar el
 * permiso" la primera vez); el ajuste "Buscar actualizaciones" y "Buscar ahora". Abajo, qué trae la nueva o en qué anda.
 */
@Composable
fun PantallaActualizaciones(
    act: Actualizador,
    prefs: Preferencias,
    ajusteBuscar: ItemAjuste,
    cambiar: ((Preferencias) -> Preferencias) -> Unit,
    alVolver: () -> Unit,
) {
    val ctx = LocalContext.current
    val res = ctx.resources
    val nueva = act.nueva
    val estado = act.estado
    val filas = buildList {
        if (nueva != null) add(FilaAct.ACTUALIZAR)
        add(FilaAct.BUSCAR_SOLA)
        add(FilaAct.BUSCAR_AHORA)
    }
    val filasActuales by rememberUpdatedState(filas)
    val cambiarActual by rememberUpdatedState(cambiar)
    val volverActual by rememberUpdatedState(alVolver)
    var foco by remember { mutableIntStateOf(0) }
    val f = foco.coerceIn(0, filas.lastIndex)

    Escenario1080 {
        Box(
            Modifier
                .fillMaxSize()
                .background(Paleta.Fondo)
                .conTeclas(activa = true) { tecla ->
                    val lista = filasActuales
                    val paso = NavegacionLista.paso(foco, tecla, lista.size) { lista.getOrNull(it) != FilaAct.BUSCAR_SOLA }
                    foco = paso.indice
                    when (val a = paso.accion) {
                        AccionLista.Volver -> volverActual()
                        is AccionLista.Abrir -> when (lista.getOrNull(a.indice)) {
                            FilaAct.ACTUALIZAR -> act.alApretarActualizar(ctx)
                            FilaAct.BUSCAR_AHORA -> act.buscar()
                            else -> Unit
                        }
                        is AccionLista.Cambiar ->
                            if (lista.getOrNull(a.indice) == FilaAct.BUSCAR_SOLA) cambiarActual { p -> ajusteBuscar.mover(p, a.dir, a.envolver) }
                        null -> Unit
                    }
                },
        ) {
            Column(Modifier.fillMaxSize().padding(start = 120.dp, end = 120.dp, top = 44.dp, bottom = 96.dp)) {
                BasicText(stringResource(R.string.menu_actualizaciones), style = estiloTexto(52, Paleta.Texto, 600))
                Box(Modifier.padding(top = 10.dp, bottom = 18.dp)) {
                    BasicText(stringResource(R.string.pie_actualizaciones), style = estiloTexto(24, Paleta.Tenue, 400), maxLines = 1)
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Fila(stringResource(R.string.act_instalada), act.instalada, Paleta.Texto, conFoco = false)
                    val ultimaTexto = when {
                        estado is EstadoActualizacion.Buscando -> res.getString(R.string.act_buscando)
                        nueva != null -> res.getString(R.string.act_nueva, nueva.version)
                        act.ultima != null -> res.getString(R.string.act_al_dia, act.ultima!!.version)
                        act.ultimaBusquedaMs != null -> res.getString(R.string.act_ninguna_publicada)
                        else -> res.getString(R.string.act_sin_buscar)
                    }
                    Fila(stringResource(R.string.act_ultima), ultimaTexto, if (nueva != null) Paleta.Dorado else Paleta.Texto, conFoco = false)
                    for ((i, fila) in filas.withIndex()) {
                        val conFoco = i == f
                        when (fila) {
                            FilaAct.ACTUALIZAR -> {
                                val permiso = estado is EstadoActualizacion.FaltaPermiso
                                Fila(
                                    if (permiso) stringResource(R.string.act_dar_permiso) else res.getString(R.string.act_actualizar_a, nueva!!.version),
                                    stringResource(if (permiso) R.string.act_ok_abrir_ajustes else R.string.act_ok_instalar),
                                    Paleta.Texto, conFoco,
                                )
                            }
                            FilaAct.BUSCAR_SOLA -> Fila(
                                stringResource(R.string.aj_buscar_actualizaciones),
                                (if (conFoco) "‹  " else "") + ajusteBuscar.valor(prefs).texto() + (if (conFoco) "  ›" else ""),
                                Paleta.Texto, conFoco,
                            )
                            FilaAct.BUSCAR_AHORA -> Fila(
                                stringResource(R.string.act_buscar_ahora),
                                act.ultimaBusquedaMs?.let { res.getString(R.string.act_ultima_vez, fechaHora(res, it)) }
                                    ?: res.getString(R.string.act_sin_buscar),
                                Paleta.Tenue, conFoco,
                            )
                        }
                    }
                }
                CajaEstado(estado, nueva?.notas.orEmpty(), nueva?.version)
            }
            val ayudas = buildList {
                add(Ayudas.recorrer())
                when (filas.getOrNull(f)) {
                    FilaAct.BUSCAR_SOLA -> add(Ayudas.cambiar())
                    FilaAct.ACTUALIZAR -> add(Ayuda(stringResource(R.string.tecla_ok), stringResource(R.string.act_ayuda_actualizar)))
                    else -> add(Ayuda(stringResource(R.string.tecla_ok), stringResource(R.string.act_ayuda_buscar)))
                }
                add(Ayudas.atras(R.string.ayuda_volver))
            }
            BarraAyuda(ayudas, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun Fila(nombre: String, valor: String, colorValor: Color, conFoco: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (conFoco) Paleta.PanelClaro else Paleta.Panel)
            .padding(horizontal = 28.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            nombre,
            modifier = Modifier.width(560.dp),
            style = estiloTexto(30, Paleta.Texto, if (conFoco) 600 else 500),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        BasicText(
            valor,
            modifier = Modifier.weight(1f),
            style = estiloTexto(30, if (conFoco) Paleta.Dorado else colorValor, 600),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** La caja de abajo: qué trae la nueva, el avance de la bajada, el permiso que falta o lo que salió mal. */
@Composable
private fun CajaEstado(estado: EstadoActualizacion, notas: String, version: String?) {
    val res = LocalContext.current.resources
    val (titulo, texto) = when (estado) {
        is EstadoActualizacion.Bajando -> res.getString(R.string.act_bajando, estado.version.version, estado.porcentaje) to null
        is EstadoActualizacion.FaltaPermiso -> res.getString(R.string.act_falta_permiso_titulo) to
            res.getString(if (estado.sinPantalla) R.string.act_falta_permiso_a_mano else R.string.act_falta_permiso_texto)
        is EstadoActualizacion.Instalando -> res.getString(R.string.act_instalando_titulo, estado.version.version) to
            res.getString(R.string.act_instalando_texto)
        is EstadoActualizacion.Fallo -> res.getString(R.string.act_fallo_titulo) to res.getString(R.string.act_fallo_texto)
        EstadoActualizacion.SinConexion -> res.getString(R.string.act_sin_conexion_titulo) to res.getString(R.string.act_sin_conexion_texto)
        else -> if (version != null && notas.isNotBlank()) res.getString(R.string.act_que_trae, version) to notas else return
    }
    Column(
        Modifier.padding(top = 26.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Paleta.Panel)
            .padding(horizontal = 32.dp, vertical = 26.dp),
    ) {
        BasicText(titulo, style = estiloTexto(34, Paleta.Texto, 600))
        if (estado is EstadoActualizacion.Bajando) {
            Row(Modifier.padding(top = 18.dp).fillMaxWidth().height(26.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFF0A0A0B))) {
                val pct = estado.porcentaje.coerceIn(0, 100)
                if (pct > 0) Box(Modifier.weight(pct.toFloat()).fillMaxHeight().background(Brush.horizontalGradient(listOf(Color(0xFF9A7C36), Paleta.Dorado))))
                if (pct < 100) Box(Modifier.weight((100 - pct).toFloat()).fillMaxHeight())
            }
        }
        if (texto != null) {
            Box(Modifier.padding(top = 10.dp)) {
                BasicText(texto, style = estiloTexto(26, Paleta.Tenue, 400), maxLines = 6, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
