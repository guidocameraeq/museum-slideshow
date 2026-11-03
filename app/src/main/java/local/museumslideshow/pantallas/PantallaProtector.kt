package local.museumslideshow.pantallas

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.drawscope.scale as escalar
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import local.museumslideshow.R
import local.museumslideshow.protector.EstadoProtector
import local.museumslideshow.protector.Interruptor
import local.museumslideshow.protector.ResultadoInterruptor

/** Nombre para mostrar de un protector: el nuestro y el Ambient por su nombre; otro paquete por el nombre de su app; si no, el componente. */
internal fun nombreProtectorLegible(ctx: Context, componente: String?): String {
    val base = nombreProtector(ctx, componente)
    if (componente == null || base != componente) return base
    val paquete = componente.split(',').first().trim().substringBefore('/')
    return try {
        val pm = ctx.packageManager
        pm.getApplicationInfo(paquete, 0).loadLabel(pm).toString().ifBlank { componente }
    } catch (e: Exception) {
        componente
    }
}

private const val ICONO_LUNA = "M20 14.5A8.5 8.5 0 0 1 9.5 4 8.5 8.5 0 1 0 20 14.5z"
private const val ICONO_VOLVER_A = "M9 14L4 9l5-5"
private const val ICONO_VOLVER_B = "M4 9h10a6 6 0 0 1 0 12h-3"
private const val ICONO_PLAY = "M7 4.5v15l12-7.5z"
private const val ICONO_TILDE = "M5 12.5l4.5 4.5L19 7.5"

private enum class Accion { USAR, VOLVER, PROBAR }

/** Un icono de trazo simple (cuadrícula de 24) dibujado con rutas SVG. */
@Composable
private fun Icono(rutas: List<String>, color: Color, tam: Int, grosor: Float = 1.8f) {
    val paths = remember(rutas) { rutas.map { PathParser().parsePathString(it).toPath() } }
    Canvas(Modifier.size(tam.dp)) {
        escalar(size.width / 24f, size.width / 24f, Offset.Zero) {
            for (p in paths) drawPath(p, color, style = Stroke(grosor, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Botón grande del borrador 5: icono + texto; con foco fondo casi blanco; apagado si no se puede usar. */
@Composable
private fun BotonProtector(texto: String, rutas: List<String>, conFoco: Boolean, apagado: Boolean = false) {
    val fondo = when {
        apagado -> Color(0x0FFFFFFF)
        conFoco -> Color(0xFFF4F1EA)
        else -> Color(0x24FFFFFF)
    }
    val color = when {
        apagado -> Color(0xFF6A675F)
        conFoco -> Color(0xFF1A1A1C)
        else -> Paleta.Texto
    }
    val forma = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .width(1000.dp).height(112.dp)
            .then(if (conFoco) Modifier.scale(1.025f).border(6.dp, Color(0x47F4F1EA), forma) else Modifier)
            .clip(forma).background(fondo)
            .padding(horizontal = 34.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icono(rutas, color, 44)
        Spacer(Modifier.width(16.dp))
        BasicText(texto, style = estiloTexto(32, color, if (conFoco) 600 else 500), maxLines = 1)
    }
}

/** Una línea del panel: punto de color + texto (con la parte final en negrita si hay `negrita`). */
@Composable
private fun Dato(punto: Color, prefijo: String, negrita: String? = null, colorTexto: Color = Paleta.Texto, peso: Int = 400) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(punto))
        Spacer(Modifier.width(14.dp))
        val texto = buildAnnotatedString {
            append(prefijo)
            if (negrita != null) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(negrita) }
        }
        BasicText(texto, style = estiloTexto(30, colorTexto, peso))
    }
}

@Composable
private fun PanelDatos(contenido: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Paleta.Panel).padding(horizontal = 34.dp, vertical = 24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(18.dp),
    ) { contenido() }
}

/**
 * La pantalla Protector (borrador 5): Usar como protector, Volver al anterior, Probarlo ahora, y qué dice el sistema.
 * Todo lo que escribe en el sistema lo hace el [Interruptor]; acá solo se lee el estado y se llama. La franja verde "Listo"
 * se muestra siempre que el protector de hoy es el nuestro (es verdad en cualquier visita, no solo al activarlo).
 */
@Composable
fun PantallaProtector(interruptor: Interruptor, activa: Boolean, alVolver: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var estado by remember { mutableStateOf<EstadoProtector?>(null) }
    var aviso by remember { mutableStateOf<Int?>(null) }
    var ocupado by remember { mutableStateOf(false) }
    var foco by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { estado = interruptor.estado() }

    val e = estado
    val acciones: List<Accion> = when {
        e == null -> emptyList()
        e.esElNuestro -> listOf(Accion.VOLVER, Accion.PROBAR)
        e.permisoDado -> listOf(Accion.USAR)
        else -> emptyList()
    }
    val enFoco = acciones.getOrNull(foco.coerceIn(0, (acciones.size - 1).coerceAtLeast(0)))

    fun resultado(r: ResultadoInterruptor) {
        aviso = when (r) {
            is ResultadoInterruptor.Hecho -> null
            ResultadoInterruptor.SinPermiso -> R.string.protector_error_permiso
            ResultadoInterruptor.Fallo -> R.string.protector_error_otro
        }
    }

    fun ejecutar(a: Accion) {
        if (ocupado) return
        ocupado = true
        aviso = null
        scope.launch {
            try {
                when (a) {
                    Accion.USAR -> resultado(interruptor.usar())
                    Accion.VOLVER -> resultado(interruptor.volverAlAnterior())
                    Accion.PROBAR -> if (!interruptor.probarAhora()) aviso = R.string.protector_error_probar
                }
                estado = interruptor.estado()
                foco = 0
            } finally {
                ocupado = false
            }
        }
    }

    Escenario1080 {
        Box(
            modifier
                .fillMaxSize()
                .background(Paleta.Fondo)
                .conTeclas(activa) { tecla ->
                    when (tecla) {
                        Tecla.ATRAS -> alVolver()
                        Tecla.ARRIBA -> if (foco > 0) foco -= 1
                        Tecla.ABAJO -> if (foco < acciones.size - 1) foco += 1
                        Tecla.OK -> enFoco?.let { ejecutar(it) }
                        else -> Unit
                    }
                },
        ) {
            Column(Modifier.fillMaxSize().padding(start = 120.dp, end = 120.dp, top = 44.dp, bottom = 96.dp)) {
                BasicText(stringResource(R.string.menu_protector), style = estiloTexto(52, Paleta.Texto, 600))
                Box(Modifier.padding(top = 10.dp, bottom = 40.dp)) {
                    BasicText(stringResource(R.string.protector_subtitulo), style = estiloTexto(24, Paleta.Tenue, 400), maxLines = 1)
                }
                if (e != null) Cuerpo(e, enFoco, aviso, ctx)
            }
            val ayudas = buildList {
                if (acciones.size > 1) add(Ayudas.recorrer())
                if (acciones.isNotEmpty()) add(Ayudas.elegir())
                add(Ayudas.atras(R.string.ayuda_volver_inicio))
            }
            BarraAyuda(ayudas, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun Cuerpo(e: EstadoProtector, enFoco: Accion?, aviso: Int?, ctx: Context) {
    val hoy = nombreProtectorLegible(ctx, e.activoHoy)
    val espacio = androidx.compose.foundation.layout.Arrangement.spacedBy(28.dp)
    Column(verticalArrangement = espacio) {
        if (e.esElNuestro) {
            Row(
                Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(16.dp))
                    .background(Color(0x248FBF8F)).border(2.dp, Color(0x8C8FBF8F), RoundedCornerShape(16.dp))
                    .padding(horizontal = 34.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icono(listOf(ICONO_TILDE), Paleta.Bien, 40, grosor = 3.2f)
                Spacer(Modifier.width(18.dp))
                BasicText(stringResource(R.string.protector_listo), style = estiloTexto(32, Paleta.Bien, 600), maxLines = 1)
            }
            val anterior = e.anteriorNombre ?: stringResource(R.string.est_protector_ambient)
            BotonProtector(stringResource(R.string.protector_volver, anterior), listOf(ICONO_VOLVER_A, ICONO_VOLVER_B), enFoco == Accion.VOLVER)
            BotonProtector(stringResource(R.string.protector_probar), listOf(ICONO_PLAY), enFoco == Accion.PROBAR)
        } else {
            BotonProtector(stringResource(R.string.protector_usar), listOf(ICONO_LUNA), enFoco == Accion.USAR, apagado = !e.permisoDado)
        }

        if (aviso != null) Dato(Paleta.Aviso, stringResource(aviso), colorTexto = Paleta.Aviso, peso = 500)

        if (!e.esElNuestro && !e.permisoDado) {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
                Dato(Paleta.Aviso, stringResource(R.string.protector_permiso_falta), colorTexto = Paleta.Aviso, peso = 500)
                BasicText(stringResource(R.string.protector_permiso_explica), style = estiloTexto(26, Paleta.Texto, 400))
            }
            BasicText(
                stringResource(R.string.comando_permiso),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF0A0A0B))
                    .border(2.dp, Color(0xFF3A3A40), RoundedCornerShape(12.dp)).padding(horizontal = 30.dp, vertical = 22.dp),
                style = estiloTexto(28, Color(0xFFF2E7C4), 500).copy(fontFamily = FontFamily.Monospace),
            )
        }

        PanelDatos {
            Dato(if (e.esElNuestro) Paleta.Bien else Paleta.Dorado, stringResource(R.string.protector_hoy), hoy)
            if (e.permisoDado && !e.esElNuestro) Dato(Paleta.Bien, stringResource(R.string.est_permiso_si))
            if (e.esElNuestro && e.anteriorNombre != null) {
                Dato(Paleta.Tenue, stringResource(R.string.protector_anterior, e.anteriorNombre), colorTexto = Paleta.Tenue)
            }
        }

        if (!e.esElNuestro && e.permisoDado) {
            BasicText(stringResource(R.string.protector_ajustes_google), style = estiloTexto(26, Paleta.Tenue, 400))
        }
    }
}
