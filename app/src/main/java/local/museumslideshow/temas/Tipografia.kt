package local.museumslideshow.temas

/** SERIF = EB Garamond, SANS = Inter. */
enum class Familia { SERIF, SANS }

/** Cada pieza de texto que dibuja un tema. Los LIBRO_* son los de la página del Libro de arte. */
enum class Rol {
    TITULO, AUTOR, ANIO, MOVIMIENTO, DATOS, EXPLICACION, CREDITO, AVISO_OK,
    NOMBRE_TITULO, NOMBRE_AUTOR, CHIP_TITULO, CHIP_AUTOR, SALA, CONTADOR,
    LIBRO_CORRIDO, LIBRO_TITULO, LIBRO_AUTOR, LIBRO_DATO, LIBRO_MOVIMIENTO,
    LIBRO_EXPLICACION, LIBRO_CREDITO, LIBRO_AVISO_OK, LIBRO_REGLA,
}

/**
 * Receta de un texto, en px del escenario de 1920x1080 (sale del CSS del borrador 3).
 * `espaciado` en em; `interlineadoPx` ya multiplicado; `margenSuperiorPx` es el aire sobre el renglón;
 * `fondo`/`rellenoH`/`rellenoV` son para textos con cajita (sala de Inmersivo).
 * Para LIBRO_REGLA: `tamanoPx` es el grosor de la línea y `color` ya trae su transparencia.
 */
data class EstiloTexto(
    val familia: Familia,
    val tamanoPx: Float,
    val peso: Int,
    val italica: Boolean,
    val interlineadoPx: Float,
    val color: Long,
    val mayusculas: Boolean = false,
    val espaciado: Float = 0f,
    val margenSuperiorPx: Float = 0f,
    val fondo: Long? = null,
    val rellenoH: Float = 0f,
    val rellenoV: Float = 0f,
)

/**
 * La tipografía de un tema en un modo. `fondoCartel` = fondo del cartel si el tema lo tiene (ninguno hoy);
 * el cuadrito flotante siempre lleva `fondoChip` con relleno `rellenoChipH` x `rellenoChipV`.
 * Los carteles chicos (ancho < 340 px) multiplican todos los tamaños por `ESCALA_CARTEL_CHICO` (ver Constantes).
 */
data class Tipografia(
    val estilos: Map<Rol, EstiloTexto>,
    val fondoCartel: Long? = null,
    val fondoChip: Long = 0x9E0A0A0AL,
    val rellenoChipH: Float = 18f,
    val rellenoChipV: Float = 12f,
) {
    operator fun get(rol: Rol): EstiloTexto =
        estilos[rol] ?: error("Esta tipografía no define el rol $rol")

    fun tiene(rol: Rol): Boolean = rol in estilos
}
