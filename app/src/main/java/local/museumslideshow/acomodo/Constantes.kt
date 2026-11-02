package local.museumslideshow.acomodo

import local.museumslideshow.ajustes.Tamano

/** Constantes del borrador `docs/diseno/03-temas-v3.html`, en px del escenario de 1920x1080. */
object Constantes {
    const val ANCHO = 1920f
    const val ALTO = 1080f

    /** Margen de seguridad: todo texto y toda obra queda adentro (menos en pantalla completa). */
    val SAFE = Rect(96f, 54f, 1728f, 972f)

    /** Caja máxima (obra + marco) de una obra sola por tamaño. */
    fun cajaSola(t: Tamano): Pair<Float, Float> = when (t) {
        Tamano.NORMAL -> 1000f to 680f
        Tamano.GRANDE -> 1521f to 856f
        else -> 1728f to 972f
    }

    /** Caja máxima de una Pared por tamaño. */
    fun cajaPared(t: Tamano): Pair<Float, Float> = when (t) {
        Tamano.NORMAL -> 1380f to 720f
        Tamano.GRANDE -> 1521f to 856f
        else -> 1728f to 972f
    }

    /** Aire entre obras de una Pared: [de 2, de 3]. */
    fun airePared(t: Tamano, cantidad: Int): Float {
        val i = if (cantidad >= 3) 1 else 0
        return when (t) {
            Tamano.NORMAL -> floatArrayOf(110f, 80f)[i]
            Tamano.GRANDE -> floatArrayOf(64f, 50f)[i]
            else -> 24f
        }
    }

    /** Paspartú (solo Galería moderna, y no con "sin marco"). */
    fun paspartu(t: Tamano): Int = when (t) {
        Tamano.NORMAL -> 64
        Tamano.GRANDE -> 34
        else -> 12
    }

    /** Aire entre la obra y su cartel. */
    const val GAP = 40f

    /** Aire entre la obra y la franja de nombres en una Pared. */
    const val AIRE_NOMBRES = 26f

    /** Ancho mínimo de la columna de una obra de la Pared (para que el nombre respire). */
    const val ANCHO_MIN_COLUMNA = 300f

    /** Ancho (mín, máx) de un cartel por nivel. */
    fun anchoCartel(n: NivelInfo): Pair<Float, Float> = when (n) {
        NivelInfo.COMPLETA -> 340f to 620f
        NivelInfo.FICHA -> 250f to 460f
        NivelInfo.MINIMA -> 210f to 420f
    }

    /** Un cartel de menos de este ancho se dibuja con la letra multiplicada por [ESCALA_CARTEL_CHICO]. */
    const val ANCHO_CARTEL_CHICO = 340f
    const val ESCALA_CARTEL_CHICO = 0.82f

    // Nombre de la sala y contador: esquina de arriba a la izquierda, con aire para decidir si pisa.
    const val SALA_X = 110f
    const val SALA_Y = 76f
    const val SALA_AIRE_H = 10f
    const val SALA_AIRE_V = 8f

    // Libro de arte: el libro ocupa el margen de seguridad. Página izquierda 960, derecha 768.
    val LIBRO = SAFE
    /** Caja de la obra en la página izquierda (con Tamaño GRANDE; NORMAL la achica, MAXIMO la agranda). */
    val LIBRO_OBRA = Rect(140f, 98f, 852f, 884f)
    val LIBRO_OBRA_MAXIMA = Rect(116f, 74f, 900f, 932f)
    const val LIBRO_FACTOR_NORMAL = 0.8f
    val LIBRO_COLUMNA = Rect(1120f, 94f, 648f, 896f)
    const val LIBRO_EXPLICACION_MAX = 26
    const val LIBRO_EXPLICACION_MIN = 17
}
