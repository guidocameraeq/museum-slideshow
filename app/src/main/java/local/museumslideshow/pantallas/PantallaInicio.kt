package local.museumslideshow.pantallas

import android.content.res.Resources
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import local.museumslideshow.R
import local.museumslideshow.ajustes.Preferencias
import local.museumslideshow.catalogo.CatalogoCargado

/**
 * Inicio ("Inicio A" del borrador 4): la obra de la galería actual de fondo con un velo a la izquierda, el nombre de la
 * app, tres datos chicos, el botón principal "Ver la galería" y seis botones. `foco` lo guarda quien la usa (para
 * volver al mismo botón al regresar de otra pantalla). Atrás sale de la app (`alSalir`).
 */
@Composable
fun PantallaInicio(
    cat: CatalogoCargado?,
    prefs: Preferencias,
    foco: Int,
    alCambiarFoco: (Int) -> Unit,
    alAbrir: (DestinoInicio) -> Unit,
    alSalir: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val res = LocalContext.current.resources
    val resumen = ResumenInicio.de(cat, prefs)
    val fondo by rememberTapa(resumen.tapa, 1920)

    Escenario1080 {
        Box(
            modifier
                .fillMaxSize()
                .background(Paleta.Fondo)
                .conTeclas(activa = true) { tecla ->
                    val paso = NavegacionInicio.paso(foco, tecla)
                    if (paso.foco != foco) alCambiarFoco(paso.foco)
                    paso.abrir?.let(alAbrir)
                    if (paso.salir) alSalir()
                },
        ) {
            fondo?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            // El velo de la izquierda deja leer el menú y la obra se ve a la derecha.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f to Color(0xF70E0E10),
                        0.38f to Color(0xE60E0E10),
                        0.70f to Color(0x590E0E10),
                        1f to Color(0x260E0E10),
                    ),
                ),
            )

            BasicText(
                stringResource(R.string.inicio_sobre),
                modifier = Modifier.offset(120.dp, 92.dp),
                style = estiloTexto(26, Paleta.Tenue, 500).copy(letterSpacing = 5.sp),
            )
            BasicText(
                stringResource(R.string.inicio_titulo),
                modifier = Modifier.offset(116.dp, 134.dp),
                style = estiloTexto(92, Paleta.Texto, 600).copy(letterSpacing = (-1.5).sp, lineHeight = 94.sp),
            )

            val datos = datosDe(res, resumen)
            datos.forEachIndexed { i, d ->
                Dato(d.texto, d.bien, Modifier.offset(120.dp, (352 + i * 46).dp))
            }

            val galeriaTexto = resumen.galeriaElegida ?: stringResource(R.string.inicio_todas_galerias)
            val subtitulo = if (resumen.obrasDelPase == 0) stringResource(R.string.inicio_sin_obras)
            else stringResource(
                R.string.inicio_ver_sub, galeriaTexto,
                pluralStringResource(R.plurals.cantidad_obras, resumen.obrasDelPase, resumen.obrasDelPase),
            )
            BotonPrincipal(stringResource(R.string.inicio_ver), subtitulo, foco == 0, Modifier.offset(120.dp, 508.dp))

            val textos = listOf(
                R.string.menu_galerias, R.string.menu_apariencia, R.string.menu_pase,
                R.string.menu_conexion, R.string.menu_protector, R.string.menu_estado,
            )
            textos.forEachIndexed { i, t ->
                BotonMenu(
                    stringResource(t), foco == i + 1,
                    Modifier.offset((120 + (i % 2) * 420).dp, (652 + (i / 2) * 104).dp),
                )
            }

            val ficha = resumen.tapa?.ficha
            val tituloObra = ficha?.titulo
            if (fondo != null && tituloObra != null) {
                val autor = listOfNotNull(ficha.autor, ficha.anio).joinToString(" · ")
                Column(
                    Modifier.align(Alignment.BottomEnd).padding(end = 90.dp, bottom = 116.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    BasicText(
                        tituloObra,
                        style = estiloTexto(24, Color(0xD9FFFFFF), 400).copy(textAlign = TextAlign.End),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (autor.isNotEmpty()) {
                        BasicText(
                            autor,
                            style = estiloTexto(24, Color(0xB3FFFFFF), 400).copy(textAlign = TextAlign.End),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            BarraAyuda(
                listOf(Ayudas.moverse(), Ayudas.elegir(), Ayudas.atras(R.string.ayuda_salir_app)),
                Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

private class DatoInicio(val texto: String, val bien: Boolean)

/** Los tres datos chicos: galerías y obras, obras listas y última sincronización. */
private fun datosDe(res: Resources, r: ResumenInicio): List<DatoInicio> {
    val g = res.getQuantityString(R.plurals.cantidad_galerias, r.galerias, r.galerias)
    val o = res.getQuantityString(R.plurals.cantidad_obras, r.obras, r.obras)
    val sync = r.ultimaSincronizacion?.let { res.getString(R.string.inicio_sync, fechaHora(res, it)) }
        ?: res.getString(R.string.inicio_sync_nunca)
    return listOf(
        DatoInicio(res.getString(R.string.estado_resumen, g, o), false),
        DatoInicio(res.getString(R.string.inicio_obras_listas, r.obrasListas), r.obrasListas > 0),
        DatoInicio(sync, false),
    )
}

@Composable
private fun Dato(texto: String, bien: Boolean, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(if (bien) Paleta.Bien else Paleta.Dorado))
        Spacer(Modifier.width(12.dp))
        BasicText(texto, style = estiloTexto(26, Paleta.Texto, 400), maxLines = 1)
    }
}

/** El foco: fondo casi blanco con letra oscura, un poco más grande y con halo. */
private val FondoFoco = Color(0xFFF4F1EA)
private val TextoFoco = Color(0xFF1A1A1C)
private val HaloFoco = Color(0x48F4F1EA)

@Composable
private fun BotonPrincipal(texto: String, subtitulo: String, conFoco: Boolean, modifier: Modifier) {
    val forma = RoundedCornerShape(16.dp)
    val colorTexto = if (conFoco) TextoFoco else Color(0xFF1A1608)
    Row(
        modifier
            .width(820.dp).height(124.dp)
            .scale(if (conFoco) 1.035f else 1f)
            .then(if (conFoco) Modifier.border(6.dp, HaloFoco, forma) else Modifier)
            .clip(forma)
            .background(if (conFoco) FondoFoco else Paleta.Dorado)
            .padding(horizontal = 30.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(44.dp)) {
            val p = Path().apply {
                moveTo(size.width * 7f / 24f, size.height * 4.5f / 24f)
                lineTo(size.width * 7f / 24f, size.height * 19.5f / 24f)
                lineTo(size.width * 19f / 24f, size.height * 12f / 24f)
                close()
            }
            drawPath(p, colorTexto)
        }
        Spacer(Modifier.width(16.dp))
        BasicText(texto, style = estiloTexto(32, colorTexto, 600), maxLines = 1)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            BasicText(
                subtitulo,
                style = estiloTexto(22, colorTexto.copy(alpha = 0.75f), 400),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BotonMenu(texto: String, conFoco: Boolean, modifier: Modifier) {
    val forma = RoundedCornerShape(16.dp)
    Row(
        modifier
            .width(400.dp).height(84.dp)
            .scale(if (conFoco) 1.035f else 1f)
            .then(if (conFoco) Modifier.border(6.dp, HaloFoco, forma) else Modifier)
            .clip(forma)
            .background(if (conFoco) FondoFoco else Color(0x24FFFFFF))
            .padding(horizontal = 30.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            texto,
            style = estiloTexto(32, if (conFoco) TextoFoco else Paleta.Texto, if (conFoco) 600 else 500),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
