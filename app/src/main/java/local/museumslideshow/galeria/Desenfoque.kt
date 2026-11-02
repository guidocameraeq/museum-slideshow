package local.museumslideshow.galeria

/** Desenfoque por cajas para la copia chica de Inmersivo (64 px: cuesta nada). Sin Android, así se prueba. */
object Desenfoque {
    /** `pixeles` en ARGB; devuelve otro arreglo. Repite `pasadas` veces un promedio horizontal y uno vertical de `radio` px. */
    fun cajas(pixeles: IntArray, ancho: Int, alto: Int, radio: Int, pasadas: Int): IntArray {
        require(pixeles.size == ancho * alto) { "Medidas que no coinciden" }
        var actual = pixeles.copyOf()
        if (radio <= 0) return actual
        repeat(pasadas) {
            actual = pasada(actual, ancho, alto, radio, horizontal = true)
            actual = pasada(actual, ancho, alto, radio, horizontal = false)
        }
        return actual
    }

    private fun pasada(src: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean): IntArray {
        val out = IntArray(src.size)
        val n = if (horizontal) w else h
        val lineas = if (horizontal) h else w
        for (l in 0 until lineas) {
            for (i in 0 until n) {
                var a = 0; var rr = 0; var g = 0; var b = 0
                var cuenta = 0
                for (d in -r..r) {
                    val j = (i + d).coerceIn(0, n - 1)
                    val p = if (horizontal) src[l * w + j] else src[j * w + l]
                    a += p ushr 24; rr += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
                    cuenta++
                }
                val px = ((a / cuenta) shl 24) or ((rr / cuenta) shl 16) or ((g / cuenta) shl 8) or (b / cuenta)
                if (horizontal) out[l * w + i] = px else out[i * w + l] = px
            }
        }
        return out
    }
}
