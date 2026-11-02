package local.museumslideshow.ajustes

/** Los valores que recorre la pantalla de Ajustes en los ajustes numéricos, y cómo se pasa de uno al siguiente (lógica pura). */
object Pasos {
    /** Tiempo por obra: 10, 15, 20, 30, 45 s, 1, 2, 3, 5 y 10 min. */
    val TIEMPO_SEG = listOf(10, 15, 20, 30, 45, 60, 120, 180, 300, 600)

    /** Duración de la transición: 0,5 a 3 s de a 0,5. */
    val DURACION_MS = listOf(500, 1000, 1500, 2000, 2500, 3000)

    /** Horario del Automático: de a 30 minutos. */
    const val PASO_HORA_MIN = 30
    const val MINUTOS_DEL_DIA = 24 * 60

    /**
     * El paso siguiente (`dir` > 0) o anterior (`dir` < 0) a `valor` en `pasos` (ordenada de menor a mayor). Si `valor` no
     * es un paso (por ejemplo 25 s puesto por adb) va al más cercano en esa dirección. Con `envolver` falso se frena en
     * las puntas; con `envolver` verdadero salta al otro extremo.
     */
    fun mover(valor: Int, pasos: List<Int>, dir: Int, envolver: Boolean): Int {
        if (dir == 0 || pasos.isEmpty()) return valor
        return if (dir > 0) {
            pasos.firstOrNull { it > valor } ?: if (envolver) pasos.first() else pasos.last()
        } else {
            pasos.lastOrNull { it < valor } ?: if (envolver) pasos.last() else pasos.first()
        }
    }

    /** Una hora del día (minutos desde la medianoche) movida de a 30 minutos, dando la vuelta a las 24 h. */
    fun moverHora(minutos: Int, dir: Int): Int {
        if (dir == 0) return minutos
        val ajustado = Math.floorMod(minutos, MINUTOS_DEL_DIA)
        val paso = if (dir > 0) {
            (ajustado / PASO_HORA_MIN + 1) * PASO_HORA_MIN
        } else {
            val k = ajustado / PASO_HORA_MIN
            (if (ajustado % PASO_HORA_MIN == 0) k - 1 else k) * PASO_HORA_MIN
        }
        return Math.floorMod(paso, MINUTOS_DEL_DIA)
    }
}
