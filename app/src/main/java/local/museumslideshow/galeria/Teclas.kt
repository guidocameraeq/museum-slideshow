package local.museumslideshow.galeria

import android.view.KeyEvent

/** Lo que puede pedir el control remoto a la galería (igual en la app y en el protector). */
enum class Accion {
    NINGUNA, ANTERIOR, SIGUIENTE, PANTALLA_COMPLETA, EXPLICACION,
    ELEGIR_ANTERIOR, ELEGIR_SIGUIENTE, BARRA, PAUSAR_O_SEGUIR, PAUSAR, SEGUIR, ATRAS,
}

/** Lo que hace falta saber para traducir una tecla. */
data class EstadoControl(val enPared: Boolean, val vistaExplicacion: Boolean)

fun esTeclaOk(keyCode: Int): Boolean =
    keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER

fun esTeclaVolumen(keyCode: Int): Boolean =
    keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE

/**
 * Traduce una tecla a una acción. `largo` = OK mantenido. Las teclas de volumen dan NINGUNA (nunca se consumen).
 * Con la vista Explicación abierta, OK (toque o largo) la cierra, igual que Atrás.
 */
fun accionPara(keyCode: Int, largo: Boolean, estado: EstadoControl): Accion = when {
    esTeclaVolumen(keyCode) -> Accion.NINGUNA
    esTeclaOk(keyCode) -> when {
        estado.vistaExplicacion -> Accion.ATRAS
        largo -> Accion.EXPLICACION
        else -> Accion.PANTALLA_COMPLETA
    }
    keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> Accion.ANTERIOR
    keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> Accion.SIGUIENTE
    keyCode == KeyEvent.KEYCODE_DPAD_UP -> if (estado.enPared) Accion.ELEGIR_ANTERIOR else Accion.NINGUNA
    keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> if (estado.enPared) Accion.ELEGIR_SIGUIENTE else Accion.BARRA
    keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> Accion.PAUSAR_O_SEGUIR
    keyCode == KeyEvent.KEYCODE_MEDIA_PLAY -> Accion.SEGUIR
    keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE -> Accion.PAUSAR
    keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE -> Accion.ATRAS
    else -> Accion.NINGUNA
}

/**
 * Distingue el toque de "mantener". El sistema marca el primer repetido como "pulsación larga" (`isLongPress`; también lo hace `adb shell input keyevent --longpress`) y eso cuenta como mantener. Se alimenta con los eventos de la tecla (los repetidos del sistema llegan
 * con `repeatCount` > 0); el mantener se dispara una sola vez, apenas pasa el umbral, sin esperar a que se suelte.
 */
class DetectorMantener(private val umbralMs: Long = ConstantesGaleria.MANTENER_OK_MS) {
    private var activo = false
    private var disparado = false

    /** Evento de tecla abajo. Devuelve true UNA vez, cuando se cumple el mantener. */
    fun alBajar(repeatCount: Int, downTimeMs: Long, eventTimeMs: Long, largoDelSistema: Boolean = false): Boolean {
        if (repeatCount == 0) {
            activo = true
            disparado = false
            return false
        }
        activo = true
        if (!disparado && (largoDelSistema || eventTimeMs - downTimeMs >= umbralMs)) {
            disparado = true
            return true
        }
        return false
    }

    /** Evento de tecla arriba. Devuelve true si fue un toque (no se llegó a disparar el mantener). */
    fun alSoltar(): Boolean {
        val toque = activo && !disparado
        activo = false
        disparado = false
        return toque
    }
}

/**
 * El manejo de teclas de la galería, sin depender de la Activity ni de Compose: se le pasan los datos del evento
 * y devuelve si lo consumió. Las teclas de volumen y las que no usa devuelven false (siguen al sistema).
 */
class ControlGaleria(
    private val estado: () -> EstadoControl,
    private val despachar: (Accion) -> Unit,
    private val detector: DetectorMantener = DetectorMantener(),
) {
    fun alTecla(
        action: Int, keyCode: Int, repeatCount: Int, downTimeMs: Long, eventTimeMs: Long, largoDelSistema: Boolean = false,
    ): Boolean {
        if (esTeclaVolumen(keyCode)) return false
        if (esTeclaOk(keyCode)) {
            when (action) {
                KeyEvent.ACTION_DOWN -> if (detector.alBajar(repeatCount, downTimeMs, eventTimeMs, largoDelSistema)) {
                    despachar(accionPara(keyCode, true, estado()))
                }
                KeyEvent.ACTION_UP -> if (detector.alSoltar()) {
                    despachar(accionPara(keyCode, false, estado()))
                }
            }
            return true
        }
        val accion = accionPara(keyCode, false, estado())
        if (accion == Accion.NINGUNA) return false
        // Solo actúa el primer "abajo" (mantener apretada una flecha no repite); el "arriba" se consume igual.
        if (action == KeyEvent.ACTION_DOWN && repeatCount == 0) despachar(accion)
        return true
    }
}
