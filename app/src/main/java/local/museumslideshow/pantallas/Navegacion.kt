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
