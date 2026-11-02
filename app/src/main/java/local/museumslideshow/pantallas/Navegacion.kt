package local.museumslideshow.pantallas

import android.view.KeyEvent

/** Las teclas del control que usan la Barra, Ajustes y Estado. */
enum class Tecla { ARRIBA, ABAJO, IZQUIERDA, DERECHA, OK, ATRAS }

/** Traduce un código de tecla de Android. Las de volumen (y cualquier otra) dan null: nunca se consumen. */
fun teclaDe(keyCode: Int): Tecla? = when (keyCode) {
    KeyEvent.KEYCODE_DPAD_UP -> Tecla.ARRIBA
    KeyEvent.KEYCODE_DPAD_DOWN -> Tecla.ABAJO
    KeyEvent.KEYCODE_DPAD_LEFT -> Tecla.IZQUIERDA
    KeyEvent.KEYCODE_DPAD_RIGHT -> Tecla.DERECHA
    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> Tecla.OK
    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> Tecla.ATRAS
    else -> null
}

// ------------------------------------------------------------------------------------------------- La Barra

/** Dónde está el foco de la Barra: en qué fila y en qué botón de esa fila. */
data class FocoBarra(val fila: Int, val columna: Int)

sealed interface EventoBarra {
    /** Atrás, o Arriba desde la primera fila. */
    object Cerrar : EventoBarra

    /** OK sobre un botón. */
    data class Elegir(val fila: Int, val columna: Int) : EventoBarra
}

data class PasoBarra(val foco: FocoBarra, val evento: EventoBarra? = null)

/** La navegación de la Barra como función pura (sin Android ni Compose). */
object NavegacionBarra {
    /**
     * `tamanos[i]` = cuántos botones tiene la fila i. `seleccion[i]` = cuál es el valor actual de esa fila (o -1 si no
     * tiene): al bajar o subir de fila el foco cae en el valor actual de la fila de llegada.
     */
    fun paso(foco: FocoBarra, tecla: Tecla, tamanos: List<Int>, seleccion: List<Int>): PasoBarra {
        if (tamanos.isEmpty()) return PasoBarra(foco, if (tecla == Tecla.ATRAS) EventoBarra.Cerrar else null)
        val fila = foco.fila.coerceIn(0, tamanos.lastIndex)
        val columna = foco.columna.coerceIn(0, (tamanos[fila] - 1).coerceAtLeast(0))
        val actual = FocoBarra(fila, columna)
        return when (tecla) {
            Tecla.ATRAS -> PasoBarra(actual, EventoBarra.Cerrar)
            Tecla.ARRIBA -> if (fila == 0) PasoBarra(actual, EventoBarra.Cerrar) else PasoBarra(aFila(fila - 1, columna, tamanos, seleccion))
            Tecla.ABAJO -> if (fila == tamanos.lastIndex) PasoBarra(actual) else PasoBarra(aFila(fila + 1, columna, tamanos, seleccion))
            Tecla.IZQUIERDA -> PasoBarra(FocoBarra(fila, (columna - 1).coerceAtLeast(0)))
            Tecla.DERECHA -> PasoBarra(FocoBarra(fila, (columna + 1).coerceAtMost((tamanos[fila] - 1).coerceAtLeast(0))))
            Tecla.OK -> PasoBarra(actual, if (tamanos[fila] > 0) EventoBarra.Elegir(fila, columna) else null)
        }
    }

    private fun aFila(fila: Int, columnaAntes: Int, tamanos: List<Int>, seleccion: List<Int>): FocoBarra {
        val max = (tamanos[fila] - 1).coerceAtLeast(0)
        val sel = seleccion.getOrElse(fila) { -1 }
        return FocoBarra(fila, if (sel in 0..max) sel else columnaAntes.coerceIn(0, max))
    }
}

// ------------------------------------------------------------------------------------------------- Ajustes

sealed interface AccionLista {
    /** Atrás: sale de la pantalla. */
    object Volver : AccionLista

    /** Izquierda / Derecha / OK sobre un ajuste que se cambia. */
    data class Cambiar(val indice: Int, val dir: Int, val envolver: Boolean) : AccionLista

    /** OK sobre una entrada que abre otra pantalla (Estado). */
    data class Abrir(val indice: Int) : AccionLista
}

data class PasoLista(val indice: Int, val accion: AccionLista? = null)

/** La navegación de la lista de Ajustes como función pura. */
object NavegacionLista {
    /** `abre(i)` = la fila i abre otra pantalla con OK. Arriba y Abajo frenan en las puntas. */
    fun paso(indice: Int, tecla: Tecla, cantidad: Int, abre: (Int) -> Boolean): PasoLista {
        if (cantidad <= 0) return PasoLista(0, if (tecla == Tecla.ATRAS) AccionLista.Volver else null)
        val i = indice.coerceIn(0, cantidad - 1)
        return when (tecla) {
            Tecla.ATRAS -> PasoLista(i, AccionLista.Volver)
            Tecla.ARRIBA -> PasoLista((i - 1).coerceAtLeast(0))
            Tecla.ABAJO -> PasoLista((i + 1).coerceAtMost(cantidad - 1))
            Tecla.IZQUIERDA -> PasoLista(i, AccionLista.Cambiar(i, -1, false))
            Tecla.DERECHA -> PasoLista(i, AccionLista.Cambiar(i, 1, false))
            Tecla.OK -> PasoLista(i, if (abre(i)) AccionLista.Abrir(i) else AccionLista.Cambiar(i, 1, true))
        }
    }
}
