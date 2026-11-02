package local.museumslideshow.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import local.museumslideshow.R

/** Colores de la Barra, Ajustes y Estado (sobrios, del borrador de diseño: oscuro con acento dorado). */
object Paleta {
    val Fondo = Color(0xFF0E0E10)
    val Panel = Color(0xFF1F1F22)
    val PanelClaro = Color(0xFF34343A)
    val Texto = Color(0xFFE8E6E1)
    val Tenue = Color(0xFFA19D95)
    val Dorado = Color(0xFFC9A54E)
    val Aviso = Color(0xFFE0A050)
    val Bien = Color(0xFF8FBF8F)

    /** La Barra: negro al ~72%. */
    val Barra = Color(0xB8000000)
}

/** Inter (variable): un `Font` por peso que se usa. */
@OptIn(ExperimentalTextApi::class)
val Inter: FontFamily = FontFamily(
    listOf(400, 500, 600).map { w ->
        Font(resId = R.font.inter, weight = FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

fun estiloTexto(tam: Int, color: Color = Paleta.Texto, peso: Int = 400) =
    TextStyle(fontFamily = Inter, fontSize = tam.sp, fontWeight = FontWeight(peso), color = color)

/**
 * Hace que 1 dp sea 1 px de una pantalla de 1920 de ancho (como el resto de la galería): se diseña en 1920x1080
 * sea cual sea la densidad que declare la tele.
 */
@Composable
fun Escenario1080(contenido: @Composable () -> Unit) {
    val ancho = LocalContext.current.resources.displayMetrics.widthPixels
    val d = if (ancho > 0) ancho / 1920f else 1f
    CompositionLocalProvider(LocalDensity provides Density(d, 1f), content = contenido)
}

/** Un botón de la Barra o de las pantallas: con foco lleva borde dorado y fondo claro; `marcado` = el valor actual. */
@Composable
fun Boton(texto: String, conFoco: Boolean, marcado: Boolean = false, habilitado: Boolean = true, modifier: Modifier = Modifier) {
    val forma = RoundedCornerShape(14.dp)
    // El foco es un fondo casi blanco con letra oscura; lo elegido es un borde dorado y un punto dorado.
    val fondo = if (conFoco) Color(0xFFF4F1EA) else Color(0x33FFFFFF)
    val colorTexto = when {
        conFoco -> Color(0xFF1A1A1C)
        habilitado -> Paleta.Texto
        else -> Paleta.Tenue
    }
    Row(
        modifier
            .clip(forma)
            .background(fondo)
            .then(if (marcado) Modifier.border(3.dp, Paleta.Dorado, forma) else Modifier)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (marcado) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(if (conFoco) Color(0xFFB08A2E) else Paleta.Dorado))
            Spacer(Modifier.width(12.dp))
        }
        BasicText(
            texto,
            style = estiloTexto(28, colorTexto, if (marcado || conFoco) 600 else 500),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
