package local.museumslideshow.sync

import java.io.File

data class Medidas(val ancho: Int, val alto: Int)

interface ProcesadorImagen {
    /**
     * Arma la copia para la pantalla en `destino` y la copia chica en `destinoChica`.
     * Devuelve las medidas de la copia, o null si no se pudo decodificar.
     */
    fun procesar(origen: File, destino: File, destinoChica: File): Medidas?

    /** Arma la tapa de una galería: un JPEG de `ladoLargo` px de lado largo (sin agrandar). false si no se pudo. */
    fun tapa(origen: File, destino: File, ladoLargo: Int): Boolean
}
