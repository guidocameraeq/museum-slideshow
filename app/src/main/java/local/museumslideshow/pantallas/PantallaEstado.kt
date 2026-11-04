package local.museumslideshow.pantallas

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import local.museumslideshow.BuildConfig
import local.museumslideshow.R
import local.museumslideshow.ajustes.ListaAjustes
import local.museumslideshow.ajustes.Preferencias
import local.museumslideshow.ajustes.estaTildada
import local.museumslideshow.catalogo.CatalogoCargado
import local.museumslideshow.reserva.ErrorReserva
import local.museumslideshow.reserva.EstadoReserva
import local.museumslideshow.catalogo.TipoProblema
import local.museumslideshow.galeria.esTeclaVolumen
import local.museumslideshow.sync.Sincronizacion
import local.museumslideshow.sync.CaminoDrive
import local.museumslideshow.sync.PausaDrive

private enum class Tipo { SECCION, NORMAL, TENUE, AVISO, BIEN, CODIGO, GRUPO }

private class Linea(val texto: String, val tipo: Tipo)

private const val PERMISO_PROTECTOR = "android.permission.WRITE_SECURE_SETTINGS"
private const val AJUSTE_PROTECTOR = "screensaver_components"

/** Lo que el sistema dice del protector: nada se escribe acá, solo se lee. */
internal class DatosProtector(val permisoOtorgado: Boolean, val activo: String?)

internal fun leerProtector(ctx: Context): DatosProtector {
    val permiso = try {
        ctx.checkSelfPermission(PERMISO_PROTECTOR) == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) {
        false
    }
    val activo = try {
        Settings.Secure.getString(ctx.contentResolver, AJUSTE_PROTECTOR)
    } catch (e: Exception) {
        null
    }
    return DatosProtector(permiso, activo?.takeIf { it.isNotBlank() })
}

private fun tamanoCarpeta(f: File): Long =
    if (f.isFile) f.length() else (f.listFiles()?.sumOf { tamanoCarpeta(it) } ?: 0L)

private val ES_AR = Locale("es", "AR")

/** "2 de noviembre, 18:43"; con el año solo si no es el actual. */
internal fun fechaHora(res: android.content.res.Resources, ms: Long): String {
    val d = Date(ms)
    val actual = Calendar.getInstance().get(Calendar.YEAR)
    val anio = Calendar.getInstance().also { it.time = d }.get(Calendar.YEAR)
    val patron = res.getString(if (anio == actual) R.string.formato_fecha else R.string.formato_fecha_anio)
    return res.getString(R.string.formato_fecha_y_hora, SimpleDateFormat(patron, ES_AR).format(d), hora(res, ms))
}

private fun hora(res: android.content.res.Resources, ms: Long): String =
    SimpleDateFormat(res.getString(R.string.formato_hora_corta), ES_AR).format(Date(ms))

/** Tamaño con coma decimal: "21 MB", "1,1 GB". */
internal fun tamano(res: android.content.res.Resources, bytes: Long): String {
    val gb = bytes / (1024.0 * 1024 * 1024)
    return if (gb >= 1.0) res.getString(R.string.formato_tamano_gb, String.format(ES_AR, "%.1f", gb))
    else res.getString(R.string.formato_tamano_mb, Math.round(bytes / (1024.0 * 1024)).toInt())
}

internal fun nombreProtector(ctx: Context, componente: String?): String {
    val res = ctx.resources
    return when {
        componente == null -> res.getString(R.string.est_protector_ninguno)
        componente.contains(ctx.packageName) -> res.getString(R.string.est_protector_nuestro)
        componente.startsWith("com.google.android.apps.tv.dreamx") -> res.getString(R.string.est_protector_ambient)
        else -> componente
    }
}

private fun armarLineas(
    ctx: Context,
    cat: CatalogoCargado?,
    prefs: Preferencias,
    usadoBytes: Long?,
    sincronizando: Boolean,
    reserva: EstadoReserva?,
): List<Linea> {
    val res = ctx.resources
    val l = ArrayList<Linea>()
    val tildadas = cat?.galerias.orEmpty().filter { prefs.estaTildada(it.driveId) }
    val obras = tildadas.sumOf { it.obras.size }

    // --- Sincronización
    l += Linea(res.getString(R.string.est_sec_sync), Tipo.SECCION)
    if (sincronizando) l += Linea(res.getString(R.string.estado_sincronizando), Tipo.AVISO)
    val buena = cat?.ultimaSincronizacionBuena
    l += if (buena != null) Linea(res.getString(R.string.est_ultima_buena, fechaHora(res, buena)), Tipo.NORMAL)
    else Linea(res.getString(R.string.est_ultima_buena_nunca), Tipo.TENUE)
    val pausas = PausaDrive.de(ctx)
    val pausaOriginal = pausas.hastaActivo(camino = CaminoDrive.ORIGINAL)
    val pausaCopia = pausas.hastaActivo(camino = CaminoDrive.COPIA)
    // Los dos caminos en pausa: el aviso de siempre. Uno solo: las obras siguen bajando por el otro (renglÃ³n tranquilo).
    val pausaHasta = if (pausaOriginal != null && pausaCopia != null) minOf(pausaOriginal, pausaCopia) else null
    val intento = cat?.ultimoIntento
    val pIntento = cat?.problemaUltimoIntento
    l += when {
        pausaHasta != null -> Linea(
            res.getString(if (prefs.sincronizarCada.horas == null) R.string.est_pausa_drive_mano else R.string.est_pausa_drive, PausaDrive.horaTexto(pausaHasta)),
            Tipo.AVISO,
        )
        pausaOriginal != null -> Linea(res.getString(R.string.est_pausa_originales, PausaDrive.horaTexto(pausaOriginal)), Tipo.NORMAL)
        pausaCopia != null -> Linea(res.getString(R.string.est_pausa_copias, PausaDrive.horaTexto(pausaCopia)), Tipo.NORMAL)
        intento == null -> Linea(res.getString(R.string.est_intento_nunca), Tipo.TENUE)
        pIntento?.tipo == TipoProblema.ERROR_RED ->
            Linea(res.getString(if (obras > 0) R.string.est_sin_conexion else R.string.est_sin_conexion_sin_obras, hora(res, intento)), Tipo.AVISO)
        pIntento != null -> Linea(TextosProblemas.frase(res, pIntento), Tipo.AVISO)
        cat?.ultimoIntentoOk == true -> Linea(res.getString(R.string.est_intento_ok, fechaHora(res, intento)), Tipo.BIEN)
        else -> Linea(res.getString(R.string.est_intento_con_errores, fechaHora(res, intento)), Tipo.AVISO)
    }

    // --- Obras
    l += Linea(res.getString(R.string.est_sec_obras), Tipo.SECCION)
    if (cat == null || obras == 0) {
        l += Linea(res.getString(R.string.est_sin_obras), Tipo.TENUE)
    } else {
        val g = res.getQuantityString(R.plurals.cantidad_galerias, tildadas.size, tildadas.size)
        val o = res.getQuantityString(R.plurals.cantidad_obras, obras, obras)
        l += Linea(res.getString(R.string.estado_resumen, g, o), Tipo.NORMAL)
        for (gal in tildadas) {
            val n = res.getQuantityString(R.plurals.cantidad_obras, gal.obras.size, gal.obras.size)
            l += Linea(res.getString(R.string.estado_galeria_linea, gal.titulo, n), Tipo.TENUE)
        }
    }

    // --- Reserva de obras
    l += Linea(res.getString(R.string.est_sec_reserva), Tipo.SECCION)
    if (reserva == null || !reserva.conPedido) {
        l += Linea(res.getString(R.string.est_reserva_sin_pedido, reserva?.obrasGuardadas ?: 0), Tipo.TENUE)
    } else {
        l += Linea(res.getString(R.string.est_reserva_guardadas, reserva.obrasGuardadas, tamano(res, reserva.bytes)), Tipo.NORMAL)
        l += Linea(res.getString(R.string.est_reserva_por_delante, reserva.listasPorDelante, reserva.porDelante), Tipo.NORMAL)
        l += Linea(res.getString(R.string.est_reserva_vistas, reserva.vistasGuardadas), Tipo.NORMAL)
        if (reserva.bajando > 0) l += Linea(res.getString(R.string.est_reserva_bajando, reserva.bajando), Tipo.AVISO)
    }
    val sinRed = reserva?.sinInternet == true || cat?.problemaUltimoIntento?.tipo == TipoProblema.ERROR_RED
    if (sinRed && obras > 0) l += Linea(res.getString(R.string.est_reserva_sin_internet), Tipo.AVISO)
    when (reserva?.ultimoError) {
        null -> {}
        ErrorReserva.SIN_INTERNET -> l += Linea(res.getString(R.string.est_reserva_error_internet), Tipo.AVISO)
        ErrorReserva.ESPACIO_LLENO -> l += Linea(res.getString(R.string.est_reserva_error_espacio), Tipo.AVISO)
        ErrorReserva.NO_DECODIFICA -> l += Linea(res.getString(R.string.est_reserva_error_imagen), Tipo.AVISO)
        ErrorReserva.MUY_GRANDE -> l += Linea(res.getString(R.string.est_reserva_error_grande), Tipo.AVISO)
        ErrorReserva.DRIVE -> l += Linea(res.getString(R.string.est_reserva_error_drive, reserva?.detalleError ?: "?"), Tipo.AVISO)
        ErrorReserva.PAUSA, ErrorReserva.PAUSA_ESTA_OBRA -> {}
    }

    // --- Espacio
    l += Linea(res.getString(R.string.est_sec_espacio), Tipo.SECCION)
    val tope = prefs.espacioMaximo.bytes
    if (usadoBytes != null) {
        l += Linea(
            res.getString(R.string.est_espacio_usado, tamano(res, usadoBytes), res.getString(ListaAjustes.etiquetaEspacio(prefs.espacioMaximo))),
            Tipo.NORMAL,
        )
    }
    val lleno = (usadoBytes != null && usadoBytes >= tope) || cat?.problemas?.any { it.tipo == TipoProblema.ESPACIO_LLENO } == true
    if (lleno) l += Linea(res.getString(R.string.est_espacio_lleno), Tipo.AVISO)

    // --- Problemas (agrupados por galería)
    l += Linea(res.getString(R.string.est_sec_problemas), Tipo.SECCION)
    // Con Drive en pausa no se listan los errores de red por archivo: ya lo explica la línea de Sincronización.
    val problemas = cat?.problemas.orEmpty().filter { pausaHasta == null || it.tipo != TipoProblema.ERROR_RED }
    if (problemas.isEmpty()) {
        l += Linea(res.getString(R.string.est_sin_problemas), Tipo.TENUE)
    } else {
        for ((galeria, lista) in TextosProblemas.agrupar(problemas)) {
            l += Linea(galeria.ifBlank { res.getString(R.string.est_grupo_general) }, Tipo.GRUPO)
            for (p in lista) l += Linea("• " + TextosProblemas.frase(res, p), Tipo.NORMAL)
        }
    }

    // --- Protector (solo lectura)
    l += Linea(res.getString(R.string.est_sec_protector), Tipo.SECCION)
    val prot = leerProtector(ctx)
    if (prot.permisoOtorgado) {
        l += Linea(res.getString(R.string.est_permiso_si), Tipo.BIEN)
    } else {
        l += Linea(res.getString(R.string.est_permiso_no), Tipo.AVISO)
        l += Linea(res.getString(R.string.comando_permiso), Tipo.CODIGO)
    }
    val activo = nombreProtector(ctx, prot.activo)
    l += Linea(res.getString(R.string.est_protector_activo, activo), Tipo.NORMAL)

    // --- La app
    l += Linea(res.getString(R.string.est_sec_app), Tipo.SECCION)
    l += Linea(res.getString(R.string.est_version, BuildConfig.VERSION_NAME), Tipo.TENUE)
    return l
}

/** Pantalla completa, solo lectura, con scroll (Arriba/Abajo). Atrás vuelve. */
@Composable
fun PantallaEstado(
    cat: CatalogoCargado?,
    prefs: Preferencias,
    sincronizando: Boolean,
    alVolver: () -> Unit,
    reserva: EstadoReserva? = null,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val usado by produceState<Long?>(null, cat, reserva?.bytes) {
        value = withContext(Dispatchers.IO) { tamanoCarpeta(Sincronizacion.dirBase(ctx)) }
    }
    val lineas = remember(cat, prefs, usado, sincronizando, reserva) { armarLineas(ctx, cat, prefs, usado, sincronizando, reserva) }
    val volver by rememberUpdatedState(alVolver)
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val pedirFoco = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { pedirFoco.requestFocus() } catch (e: Exception) { /* todavía no está listo */ } }

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
                    if (esTeclaVolumen(n.keyCode)) return@onPreviewKeyEvent false
                    val tecla = teclaDe(n.keyCode) ?: return@onPreviewKeyEvent false
                    if (n.action == KeyEvent.ACTION_DOWN) {
                        when (tecla) {
                            Tecla.ATRAS -> if (n.repeatCount == 0) volver()
                            Tecla.ABAJO -> scope.launch { scroll.animateScrollBy(PASO_SCROLL) }
                            Tecla.ARRIBA -> scope.launch { scroll.animateScrollBy(-PASO_SCROLL) }
                            else -> Unit
                        }
                    }
                    true
                },
        ) {
            BasicText(stringResource(R.string.estado_titulo), style = estiloTexto(52, Paleta.Texto, 600))
            Column(Modifier.padding(top = 10.dp, bottom = 18.dp)) {
                BasicText(stringResource(R.string.estado_pie), style = estiloTexto(24, Paleta.Tenue, 400), maxLines = 1)
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (linea in lineas) LineaEstado(linea)
            }
        }
    }
}

private const val PASO_SCROLL = 280f

@Composable
private fun LineaEstado(l: Linea) {
    when (l.tipo) {
        Tipo.SECCION -> BasicText(l.texto, modifier = Modifier.padding(top = 22.dp), style = estiloTexto(34, Paleta.Dorado, 600))
        Tipo.GRUPO -> BasicText(l.texto, modifier = Modifier.padding(top = 8.dp), style = estiloTexto(28, Paleta.Texto, 600))
        Tipo.NORMAL -> BasicText(l.texto, style = estiloTexto(28, Paleta.Texto, 400))
        Tipo.TENUE -> BasicText(l.texto, style = estiloTexto(28, Paleta.Tenue, 400))
        Tipo.AVISO -> BasicText(l.texto, style = estiloTexto(28, Paleta.Aviso, 500))
        Tipo.BIEN -> BasicText(l.texto, style = estiloTexto(28, Paleta.Bien, 500))
        Tipo.CODIGO -> BasicText(
            l.texto,
            modifier = Modifier.background(Paleta.Panel).padding(horizontal = 18.dp, vertical = 10.dp),
            style = estiloTexto(26, Color(0xFFF2E7C4), 500),
        )
    }
}
