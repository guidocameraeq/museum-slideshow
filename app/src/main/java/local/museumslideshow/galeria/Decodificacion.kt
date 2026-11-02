package local.museumslideshow.galeria

/** Una obra a decodificar: medidas de la copia guardada y tamaño (en px de la superficie) que va a ocupar. */
data class PedidoDecodificacion(val origenAncho: Int, val origenAlto: Int, val destinoAncho: Int, val destinoAlto: Int)

/** Cuentas puras de cómo abrir cada obra sin pasarse del presupuesto de memoria. */
object Decodificacion {
    private const val MUESTREO_MAXIMO = 64

    /** Lado de la imagen decodificada con `inSampleSize` = m (BitmapFactory redondea para arriba). */
    fun lado(origen: Int, m: Int): Int = (origen + m - 1) / m

    fun pixeles(p: PedidoDecodificacion, m: Int): Long = lado(p.origenAncho, m).toLong() * lado(p.origenAlto, m)

    /** El `inSampleSize` (potencia de 2) más grande que no baja del tamaño de destino en ninguna de las dos medidas. */
    fun muestreo(origenAncho: Int, origenAlto: Int, destinoAncho: Int, destinoAlto: Int): Int {
        var m = 1
        while (m < MUESTREO_MAXIMO &&
            origenAncho / (m * 2) >= destinoAncho && origenAlto / (m * 2) >= destinoAlto
        ) m *= 2
        return m
    }

    /**
     * El `inSampleSize` de cada obra de una escena. Parte del que no baja del tamaño de su lugar y, si entre todas
     * se pasan del presupuesto (en px: una pantalla de la superficie), agranda el muestreo de la más pesada
     * hasta que entren. Una obra sola nunca se pasa: la copia guardada ya entra en una pantalla 4K.
     */
    fun planificar(pedidos: List<PedidoDecodificacion>, presupuestoPx: Long): List<Int> {
        val m = pedidos.map { muestreo(it.origenAncho, it.origenAlto, it.destinoAncho, it.destinoAlto) }.toIntArray()
        while (true) {
            val total = pedidos.indices.sumOf { pixeles(pedidos[it], m[it]) }
            if (total <= presupuestoPx) break
            var peor = -1
            var peorPx = 0L
            for (i in pedidos.indices) {
                if (m[i] >= MUESTREO_MAXIMO) continue
                val px = pixeles(pedidos[i], m[i])
                if (px > peorPx) { peor = i; peorPx = px }
            }
            if (peor < 0) break
            m[peor] *= 2
        }
        return m.toList()
    }

    /** Bytes de un bitmap ARGB_8888 de esas medidas. */
    fun bytes(ancho: Int, alto: Int): Long = ancho.toLong() * alto * 4
}
