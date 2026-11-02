package local.museumslideshow.galeria

/** Los números de la galería en pantalla, todos en un solo lugar. */
object ConstantesGaleria {
    // Superficie de la obra: 4K real, o 1080 si "Calidad de la obra" está en 1080.
    const val SUPERFICIE_4K_ANCHO = 3840
    const val SUPERFICIE_4K_ALTO = 2160
    const val SUPERFICIE_1080_ANCHO = 1920
    const val SUPERFICIE_1080_ALTO = 1080

    /** Cartel de la obra con "Unos segundos": cuánto se ve (después se desvanece). */
    const val CARTEL_UNOS_SEGUNDOS_MS = 8000L

    /** Nombre de la sala con "Unos segundos". */
    const val SALA_UNOS_SEGUNDOS_MS = 5000L
    const val DESVANECER_TEXTO_MS = 600

    /** Cuánto hay que mantener OK para que cuente como "mantener" (y no como toque). */
    const val MANTENER_OK_MS = 600L

    /** Una imagen que ya nadie dibuja se recicla recién pasado este tiempo (el dibujo en la GPU es asincrónico). */
    const val RECICLADO_DEMORA_MS = 1500L

    /** Cuántas obras sin medidas (Drive no las dio) se piden a la reserva a la vez para poder medirlas. */
    const val MAX_SIN_MEDIDAS = 10

    /** La pre-carga de la escena siguiente espera esto después de reciclar la anterior (para respetar las 2 pantallas). */
    const val PRECARGA_DEMORA_MS = 500L

    /** Fondo desenfocado de Inmersivo: la copia chica (64 px) se desenfoca una vez al cargarla. */
    const val DESENFOQUE_RADIO_PX = 2
    const val DESENFOQUE_PASADAS = 2

    /** Vista Explicación: letra más chica a la que se baja si ni con la columna ancha entra el texto. */
    const val VISTA_EXPLICACION_MIN_PX = 13

    /** Vista Explicación con columna ancha: el lomo del libro se corre a esta x (del escenario de 1920). */
    const val VISTA_LOMO_X = 736f

    /** Línea que marca la obra elegida en una Pared. */
    const val MARCA_ELEGIDA_ANCHO = 90f
    const val MARCA_ELEGIDA_GROSOR = 3f

    /**
     * true = las obras se decodifican como Bitmap de memoria de video (la mitad de RAM, pero no probado en esta tele).
     * false = ARGB_8888 normal (lo seguro). Si algo se ve negro con true, volver a false.
     */
    const val BITMAP_EN_MEMORIA_DE_VIDEO = true
}
