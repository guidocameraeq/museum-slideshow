package local.museumslideshow.temas

import local.museumslideshow.ajustes.Modo
import local.museumslideshow.ajustes.Tamano

/** Color de pared (ARGB). `c1` arriba, `c2` abajo; `pagina` es el papel del Libro de arte. */
data class ColorPared(val id: String, val c1: Long, val c2: Long, val pagina: Long)

object CatalogoColores {
    val claros: List<ColorPared> = listOf(
        ColorPared("blanco-galeria", 0xFFF6F5F1L, 0xFFEBE9E3L, 0xFFFBF9F3L),
        ColorPared("gris-calido", 0xFFE9E5DEL, 0xFFD9D4CAL, 0xFFF5EEDDL),
        ColorPared("piedra", 0xFFD9D0C0L, 0xFFC3B9A6L, 0xFFE8DEC6L),
    )

    /** El bordó sale del viejo tema Clásico (borrador 1: degradado #6e2a2c a #361214); la página es de la tabla de reserva. */
    val oscuros: List<ColorPared> = listOf(
        ColorPared("carbon", 0xFF2A2A2CL, 0xFF1A1A1CL, 0xFF1D1C1BL),
        ColorPared("negro", 0xFF141415L, 0xFF050506L, 0xFF0E0E0FL),
        ColorPared("azul-noche", 0xFF1B2744L, 0xFF0C1325L, 0xFF131C30L),
        ColorPared("bordo", 0xFF6E2A2CL, 0xFF361214L, 0xFF2A0F12L),
    )

    /** Claros u oscuros según el modo (AUTOMATICO se trata como claro: pedí el modo efectivo). */
    fun de(modo: Modo): List<ColorPared> = if (modo == Modo.OSCURO) oscuros else claros

    fun porId(id: String): ColorPared? = (claros + oscuros).firstOrNull { it.id == id }
}

/**
 * Un marco del catálogo. `grosor` y `filete` en px del escenario de 1920x1080, por tamaño
 * (PANTALLA_COMPLETA usa los de MAXIMO). `imagenDisponible`: ya está la foto del marco real.
 */
data class MarcoDef(
    val id: String,
    val grosor: Map<Tamano, Int>,
    val filete: Map<Tamano, Int>,
    val redondo: Boolean,
    val imagenDisponible: Boolean,
) {
    fun grosorDe(t: Tamano): Int = grosor[t] ?: 0
    fun fileteDe(t: Tamano): Int = filete[t] ?: 0
}

object CatalogoMarcos {
    private fun porTamano(normal: Int, grande: Int, maximo: Int): Map<Tamano, Int> = mapOf(
        Tamano.NORMAL to normal,
        Tamano.GRANDE to grande,
        Tamano.MAXIMO to maximo,
        Tamano.PANTALLA_COMPLETA to maximo,
    )

    private val CERO = porTamano(0, 0, 0)
    private val GROSOR_DORADO = porTamano(44, 30, 16)

    /** Ninguno tiene foto todavía; "sin marco" no la necesita. */
    val todos: List<MarcoDef> = listOf(
        MarcoDef("fino", porTamano(14, 10, 5), CERO, redondo = false, imagenDisponible = false),
        MarcoDef("filete", porTamano(20, 14, 8), porTamano(4, 3, 2), redondo = false, imagenDisponible = false),
        MarcoDef("madera", porTamano(32, 22, 11), CERO, redondo = false, imagenDisponible = false),
        MarcoDef("dorado", GROSOR_DORADO, CERO, redondo = false, imagenDisponible = false),
        MarcoDef("dorado-redondo", GROSOR_DORADO, CERO, redondo = true, imagenDisponible = false),
        MarcoDef("sin", CERO, CERO, redondo = false, imagenDisponible = true),
    )

    /** Marcos rectangulares con marco de verdad: lo que usa "al azar por obra". */
    val rectangulares: List<String> = listOf("fino", "filete", "madera", "dorado")

    val ids: Set<String> = todos.map { it.id }.toSet()

    fun porId(id: String): MarcoDef? = todos.firstOrNull { it.id == id }

    /**
     * Regla provisoria (SPEC-0, supuesto BAJO): un marco sin foto se dibuja como `fino` (negro fino);
     * `dorado-redondo` sin foto va `sin` marco. Un id desconocido también cae en `fino`.
     * Cuando llegue la foto (`imagenDisponible = true`) el marco se devuelve tal cual.
     */
    fun resolver(id: String): MarcoDef {
        val def = porId(id) ?: return porId("fino")!!.let { resolverSinFoto(it) }
        return if (def.imagenDisponible) def else resolverSinFoto(def)
    }

    private fun resolverSinFoto(def: MarcoDef): MarcoDef =
        if (def.redondo) porId("sin")!! else porId("fino")!!
}
