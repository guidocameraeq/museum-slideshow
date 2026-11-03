package local.museumslideshow.protector

import android.service.dreams.DreamService
import android.util.Log
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import local.museumslideshow.MotorApp
import local.museumslideshow.Registro
import local.museumslideshow.ajustes.estaTildada
import local.museumslideshow.galeria.GaleriaEnPantalla
import local.museumslideshow.galeria.esTeclaVolumen
import local.museumslideshow.pantallas.PantallaSinObras
import local.museumslideshow.pantallas.SinObras

/**
 * El protector de pantalla: la misma galería que la app (con el motor compartido), sin Barra. Interactivo: Izquierda/Derecha,
 * OK (Pantalla completa) y mantener OK (Explicación) funcionan; Atrás cierra lo abierto y, si no hay nada, vuelve a la tele;
 * OK nunca lo cierra; las teclas de volumen nunca se consumen; Home es del sistema.
 *
 * IDENTIDAD FIJA: el nombre de esta clase está en el ajuste del sistema; cambiarlo obliga a reinstalar y elegir el protector de nuevo.
 */
class ProtectorMuseo : DreamService(), LifecycleOwner, SavedStateRegistryOwner {
    private val registro = LifecycleRegistry(this)
    private val estadoGuardado = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registro
    override val savedStateRegistry: SavedStateRegistry get() = estadoGuardado.savedStateRegistry

    private val motor by lazy { MotorApp.de(applicationContext) }

    override fun onCreate() {
        super.onCreate()
        estadoGuardado.performAttach()
        estadoGuardado.performRestore(null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true
        isFullscreen = true
        isScreenBright = true
        Log.i(Registro.TAG, "Protector: arranca")

        registro.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        motor.iniciar()

        val vista = ComposeView(this).apply { setContent { Contenido() } }
        // Compose busca los dueños en la raíz de la ventana, no en su propia vista.
        for (v in listOf(window.decorView, vista)) {
            v.setViewTreeLifecycleOwner(this)
            v.setViewTreeSavedStateRegistryOwner(this)
        }
        setContentView(vista)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        registro.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registro.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        motor.sincronizarSiToca()
    }

    override fun onDreamingStopped() {
        registro.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        registro.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        // Si no llegó a arrancar el sueño, el registro todavía no pasó por STARTED: ON_DESTROY exige bajar de a un escalón.
        if (registro.currentState == Lifecycle.State.STARTED || registro.currentState == Lifecycle.State.RESUMED) {
            registro.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            registro.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
        registro.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDetachedFromWindow()
        Log.i(Registro.TAG, "Protector: termina")
    }

    /**
     * El DreamService de fábrica se come Atrás y despierta la tele antes de que llegue a la ventana: acá Atrás va primero a la
     * galería (para que cierre lo abierto, ej. la vista Explicación) y solo si nadie lo usa se cierra el protector.
     * Las teclas de volumen no se consumen (Volumen Maestro depende de eso).
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (esTeclaVolumen(event.keyCode)) return false
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            val usada = window.superDispatchKeyEvent(event)
            if (!usada && event.action == KeyEvent.ACTION_DOWN) finish()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    @Composable
    private fun Contenido() {
        // Marca esta galería como abierta mientras esté en pantalla: es ella quien le dice a la reserva dónde va el pase.
        DisposableEffect(Unit) {
            motor.galeriaAbierta(this@ProtectorMuseo, true)
            onDispose {
                motor.galeriaAbierta(this@ProtectorMuseo, false)
                motor.pedirReservaSinGaleria()
            }
        }
        val p = motor.preferencias
        if (p == null) {
            Box(Modifier.fillMaxSize().background(Color.Black))
            return
        }
        val cat = motor.catalogo
        val tildadas = cat?.galerias.orEmpty().filter { p.estaTildada(it.driveId) }
        val hayObras = tildadas.sumOf { it.obras.size } > 0
        if (cat != null && hayObras) {
            GaleriaEnPantalla(
                cat, p,
                alSalir = { finish() },
                app = null,   // sin Barra: Abajo no abre nada
                teclasActivas = true,
                reserva = motor.reserva,
            )
        } else {
            PantallaSinObras(
                SinObras.motivo(motor.sincronizando, cat, hayTildadas = tildadas.isNotEmpty()),
                activa = true,
                alVerEstado = null,
                alVolver = { finish() },
            )
        }
    }
}
