package local.museumslideshow.galeria

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeclasTest {
    private val libre = EstadoControl(enPared = false, vistaExplicacion = false)
    private val pared = EstadoControl(enPared = true, vistaExplicacion = false)
    private val vista = EstadoControl(enPared = false, vistaExplicacion = true)

    @Test
    fun izquierdaYDerechaNavegan() {
        assertEquals(Accion.ANTERIOR, accionPara(KeyEvent.KEYCODE_DPAD_LEFT, false, libre))
        assertEquals(Accion.SIGUIENTE, accionPara(KeyEvent.KEYCODE_DPAD_RIGHT, false, libre))
    }

    @Test
    fun okEsPantallaCompletaYMantenerOkEsExplicacion() {
        for (k in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            assertEquals(Accion.PANTALLA_COMPLETA, accionPara(k, false, libre))
            assertEquals(Accion.EXPLICACION, accionPara(k, true, pared))
        }
    }

    @Test
    fun enLaVistaExplicacionOkCierra() {
        assertEquals(Accion.ATRAS, accionPara(KeyEvent.KEYCODE_DPAD_CENTER, false, vista))
        assertEquals(Accion.ATRAS, accionPara(KeyEvent.KEYCODE_DPAD_CENTER, true, vista))
        assertEquals(Accion.ATRAS, accionPara(KeyEvent.KEYCODE_BACK, false, vista))
    }

    @Test
    fun arribaYAbajoEnUnaParedEligenYFueraDeEllaAbajoAbreLaBarra() {
        assertEquals(Accion.ELEGIR_ANTERIOR, accionPara(KeyEvent.KEYCODE_DPAD_UP, false, pared))
        assertEquals(Accion.ELEGIR_SIGUIENTE, accionPara(KeyEvent.KEYCODE_DPAD_DOWN, false, pared))
        assertEquals(Accion.BARRA, accionPara(KeyEvent.KEYCODE_DPAD_DOWN, false, libre))
        assertEquals(Accion.NINGUNA, accionPara(KeyEvent.KEYCODE_DPAD_UP, false, libre))
    }

    @Test
    fun playPausaYAtras() {
        assertEquals(Accion.PAUSAR_O_SEGUIR, accionPara(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, false, libre))
        assertEquals(Accion.SEGUIR, accionPara(KeyEvent.KEYCODE_MEDIA_PLAY, false, libre))
        assertEquals(Accion.PAUSAR, accionPara(KeyEvent.KEYCODE_MEDIA_PAUSE, false, libre))
        assertEquals(Accion.ATRAS, accionPara(KeyEvent.KEYCODE_BACK, false, libre))
    }

    @Test
    fun elVolumenNuncaEsDeLaGaleria() {
        for (k in listOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE)) {
            for (largo in listOf(false, true)) {
                for (e in listOf(libre, pared, vista)) assertEquals(Accion.NINGUNA, accionPara(k, largo, e))
            }
        }
    }

    // ---- mantener OK

    @Test
    fun unToqueCortoEsToque() {
        val d = DetectorMantener(600)
        assertFalse(d.alBajar(0, 1000, 1000))
        assertTrue(d.alSoltar())
    }

    @Test
    fun mantenerSeDisparaUnaSolaVezYNoDejaToque() {
        val d = DetectorMantener(600)
        assertFalse(d.alBajar(0, 1000, 1000))
        assertFalse(d.alBajar(1, 1000, 1400))      // primer repetido, todavía poco
        assertTrue(d.alBajar(2, 1000, 1650))       // pasó el umbral: mantener
        assertFalse(d.alBajar(3, 1000, 1700))      // ya disparó: no repite
        assertFalse(d.alSoltar())                  // y al soltar no cuenta como toque
        // la siguiente vez vuelve a andar
        assertFalse(d.alBajar(0, 5000, 5000))
        assertTrue(d.alSoltar())
    }

    @Test
    fun laPulsacionLargaDelSistemaCuentaAunqueNoPaseElUmbral() {
        val d = DetectorMantener(600)
        assertFalse(d.alBajar(0, 1000, 1000))
        assertTrue(d.alBajar(1, 1000, 1000, largoDelSistema = true))   // como el `input keyevent --longpress` de adb
        assertFalse(d.alSoltar())
    }

    // ---- el manejo completo

    private fun armar(acciones: MutableList<Accion>, estado: EstadoControl = libre) =
        ControlGaleria({ estado }, { acciones += it }, DetectorMantener(600))

    @Test
    fun controlToqueDeOkYMantenerOk() {
        val acciones = mutableListOf<Accion>()
        val c = armar(acciones)
        // toque
        assertTrue(c.alTecla(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0, 100, 100))
        assertTrue(c.alTecla(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0, 100, 180))
        assertEquals(listOf(Accion.PANTALLA_COMPLETA), acciones)
        // mantener: dispara al pasar el umbral, no al soltar, y no dispara además el toque
        assertTrue(c.alTecla(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0, 1000, 1000))
        assertTrue(c.alTecla(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 1, 1000, 1500))
        assertEquals(1, acciones.size)
        assertTrue(c.alTecla(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 2, 1000, 1650))
        assertEquals(listOf(Accion.PANTALLA_COMPLETA, Accion.EXPLICACION), acciones)
        assertTrue(c.alTecla(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0, 1000, 1800))
        assertEquals(2, acciones.size)
    }

    @Test
    fun controlNoConsumeElVolumenNiLoQueNoUsa() {
        val acciones = mutableListOf<Accion>()
        val c = armar(acciones)
        assertFalse(c.alTecla(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, 0, 0, 0))
        assertFalse(c.alTecla(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP, 0, 0, 0))
        assertFalse(c.alTecla(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, 0, 0, 0))
        assertFalse(c.alTecla(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE, 0, 0, 0))
        assertFalse(c.alTecla(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP, 0, 0, 0))     // Arriba fuera de una Pared
        assertFalse(c.alTecla(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 0, 0, 0))
        assertTrue(acciones.isEmpty())
    }

    @Test
    fun controlLasFlechasNoRepitenAlMantenerlas() {
        val acciones = mutableListOf<Accion>()
        val c = armar(acciones)
        assertTrue(c.alTecla(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 0, 0, 0))
        assertTrue(c.alTecla(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 1, 0, 600))
        assertTrue(c.alTecla(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0, 0, 700))
        assertEquals(listOf(Accion.SIGUIENTE), acciones)
    }
}
