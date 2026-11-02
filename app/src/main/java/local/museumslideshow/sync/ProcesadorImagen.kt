package local.museumslideshow.sync

import java.io.File

data class Medidas(val ancho: Int, val alto: Int)

interface ProcesadorImagen {
    /**
     * Arma la copia para la pantalla en `destino` y la copia chica en `destinoChica`.
     * Devuelve las medidas de la copia, o null si no se pudo decodificar.
     */
    fun procesar(origen: File, destino: File, destinoChica: File): Medidas?
}
