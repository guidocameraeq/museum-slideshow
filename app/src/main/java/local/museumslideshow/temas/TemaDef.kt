package local.museumslideshow.temas

import local.museumslideshow.ajustes.Modo
import local.museumslideshow.ajustes.Tamano
import local.museumslideshow.ajustes.Tema

/** `color-mix(pagina, con parte)`: el fondo de mesa del Libro de arte es el papel mezclado con otro color. */
data class MezclaColor(val con: Long, val parte: Float)

/**
 * Qué es un tema, como datos (no dibuja nada). Un tema nuevo = una línea en [RegistroTemas].
 * `tamanoFijo`: el tema ignora el ajuste Tamaño y usa este (Inmersivo = MAXIMO).
 * `colorPaspartu`, `colorCanal` y `mesaLibro` son los colores propios del tema por modo.
 */
data class TemaDef(
    val id: Tema,
    val admitePared: Boolean,
    val marcoDelTema: String,
    val paredPorDefecto: Map<Modo, String>,
    val conPaspartu: Boolean,
    val sinMarcoSiempre: Boolean,
    val fondoDesenfocado: Boolean,
    val esLibro: Boolean,
    val tipografia: Map<Modo, Tipografia>,
    val tamanoFijo: Tamano? = null,
    /** La pared lleva un foco de luz sobre la obra (Sala oscura): degradado radial en vez del vertical. */
    val focoEnPared: Boolean = false,
    val colorPaspartu: Map<Modo, Long> = emptyMap(),
    val colorCanal: Map<Modo, Long> = emptyMap(),
    val mesaLibro: Map<Modo, MezclaColor> = emptyMap(),
) {
    fun tipografia(modo: Modo): Tipografia =
        tipografia[if (modo == Modo.OSCURO) Modo.OSCURO else Modo.CLARO]!!

    /** El color de pared que corresponde: el elegido (si existe y es del modo) o el del tema. */
    fun colorPared(modo: Modo, elegido: String?): ColorPared {
        val m = if (modo == Modo.OSCURO) Modo.OSCURO else Modo.CLARO
        val lista = CatalogoColores.de(m)
        return lista.firstOrNull { it.id == elegido } ?: lista.first { it.id == paredPorDefecto.getValue(m) }
    }
}

object RegistroTemas {
    val todos: List<TemaDef> = listOf(
        // Galería moderna: negro fino con paspartú; paredes por defecto blanco galería / carbón (WDEF).
        TemaDef(
            id = Tema.GALERIA_MODERNA, admitePared = true, marcoDelTema = "fino",
            paredPorDefecto = mapOf(Modo.CLARO to "blanco-galeria", Modo.OSCURO to "carbon"),
            conPaspartu = true, sinMarcoSiempre = false, fondoDesenfocado = false, esLibro = false,
            tipografia = TipografiasDeTemas.galeriaModerna(),
            colorPaspartu = mapOf(Modo.CLARO to 0xFFFBFAF6L, Modo.OSCURO to 0xFF2A2A2DL),
        ),
        // Sala oscura: negro con filete dorado; paredes por defecto gris cálido / negro.
        TemaDef(
            id = Tema.SALA_OSCURA, admitePared = true, marcoDelTema = "filete",
            paredPorDefecto = mapOf(Modo.CLARO to "gris-calido", Modo.OSCURO to "negro"),
            conPaspartu = false, sinMarcoSiempre = false, fondoDesenfocado = false, esLibro = false,
            tipografia = TipografiasDeTemas.salaOscura(),
            focoEnPared = true,
        ),
        // Inmersivo: sin marco, fondo = la obra desenfocada, siempre tamaño máximo.
        TemaDef(
            id = Tema.INMERSIVO, admitePared = true, marcoDelTema = "sin",
            paredPorDefecto = mapOf(Modo.CLARO to "blanco-galeria", Modo.OSCURO to "carbon"),
            conPaspartu = false, sinMarcoSiempre = true, fondoDesenfocado = true, esLibro = false,
            tipografia = TipografiasDeTemas.inmersivo(),
            tamanoFijo = Tamano.MAXIMO,
        ),
        // Libro de arte: obra a la izquierda, página a la derecha; no hace Pared. El color de pared es el papel.
        TemaDef(
            id = Tema.LIBRO_DE_ARTE, admitePared = false, marcoDelTema = "sin",
            paredPorDefecto = mapOf(Modo.CLARO to "gris-calido", Modo.OSCURO to "carbon"),
            conPaspartu = false, sinMarcoSiempre = true, fondoDesenfocado = false, esLibro = true,
            tipografia = TipografiasDeTemas.libroDeArte(),
            colorCanal = mapOf(Modo.CLARO to 0x335A4119L, Modo.OSCURO to 0xA6000000L),
            mesaLibro = mapOf(Modo.CLARO to MezclaColor(0xFF6B5A3AL, 0.24f), Modo.OSCURO to MezclaColor(0xFF000000L, 0.70f)),
        ),
    )

    fun de(id: Tema): TemaDef = todos.first { it.id == id }
}
