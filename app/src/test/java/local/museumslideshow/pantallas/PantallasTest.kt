package local.museumslideshow.pantallas

import android.view.KeyEvent
import local.museumslideshow.catalogo.CatalogoCargado
import local.museumslideshow.catalogo.Problema
import local.museumslideshow.catalogo.TipoProblema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TeclasPantallasTest {
    @Test
    fun traduceLasTeclasDelControl() {
        assertEquals(Tecla.ARRIBA, teclaDe(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(Tecla.ABAJO, teclaDe(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(Tecla.IZQUIERDA, teclaDe(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(Tecla.DERECHA, teclaDe(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(Tecla.OK, teclaDe(KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(Tecla.ATRAS, teclaDe(KeyEvent.KEYCODE_BACK))
    }

    @Test
    fun elVolumenNuncaSeTraduceYAsiNuncaSeConsume() {
        assertNull(teclaDe(KeyEvent.KEYCODE_VOLUME_UP))
        assertNull(teclaDe(KeyEvent.KEYCODE_VOLUME_DOWN))
        assertNull(teclaDe(KeyEvent.KEYCODE_VOLUME_MUTE))
    }
}

class NavegacionBarraTest {
    // Galería (Todas + 2), Tema (4), Acciones (4)
    private val tamanos = listOf(3, 4, 4)
    private val seleccion = listOf(1, 2, -1)

    private fun paso(f: FocoBarra, t: Tecla) = NavegacionBarra.paso(f, t, tamanos, seleccion)

    @Test
    fun abajoYArribaCambianDeFilaYCaenEnElValorActual() {
        val p = paso(FocoBarra(0, 0), Tecla.ABAJO)
        assertEquals(FocoBarra(1, 2), p.foco)      // el tema actual (índice 2)
        assertNull(p.evento)
        assertEquals(FocoBarra(0, 1), paso(FocoBarra(1, 3), Tecla.ARRIBA).foco)   // la galería actual (índice 1)
    }

    @Test
    fun abajoEnLasAccionesNoSale() {
        assertEquals(FocoBarra(2, 1), paso(FocoBarra(2, 1), Tecla.ABAJO).foco)
    }

    @Test
    fun enLaFilaDeAccionesElColumnaSeConserva() {
        assertEquals(FocoBarra(2, 1), paso(FocoBarra(1, 1), Tecla.ABAJO).foco.copy(columna = 1))
    }

    @Test
    fun arribaDesdeLaPrimeraFilaCierra() {
        assertEquals(EventoBarra.Cerrar, paso(FocoBarra(0, 1), Tecla.ARRIBA).evento)
    }

    @Test
    fun atrasCierraDesdeCualquierLado() {
        assertEquals(EventoBarra.Cerrar, paso(FocoBarra(2, 3), Tecla.ATRAS).evento)
    }

    @Test
    fun izquierdaYDerechaFrenanEnLasPuntas() {
        assertEquals(FocoBarra(0, 0), paso(FocoBarra(0, 0), Tecla.IZQUIERDA).foco)
        assertEquals(FocoBarra(0, 2), paso(FocoBarra(0, 2), Tecla.DERECHA).foco)
        assertEquals(FocoBarra(1, 3), paso(FocoBarra(1, 2), Tecla.DERECHA).foco)
        assertEquals(FocoBarra(1, 1), paso(FocoBarra(1, 2), Tecla.IZQUIERDA).foco)
    }

    @Test
    fun okEligeLoQueTieneElFoco() {
        assertEquals(EventoBarra.Elegir(1, 3), paso(FocoBarra(1, 3), Tecla.OK).evento)
    }

    @Test
    fun unFocoFueraDeRangoSeCorrige() {
        assertEquals(FocoBarra(0, 2), paso(FocoBarra(0, 9), Tecla.OK).foco)
        assertEquals(EventoBarra.Cerrar, NavegacionBarra.paso(FocoBarra(0, 0), Tecla.ATRAS, emptyList(), emptyList()).evento)
    }
}

class NavegacionListaTest {
    private fun paso(i: Int, t: Tecla, n: Int = 5, abre: (Int) -> Boolean = { it == 4 }) = NavegacionLista.paso(i, t, n, abre)

    @Test
    fun arribaYAbajoRecorrenYFrenanEnLasPuntas() {
        assertEquals(1, paso(0, Tecla.ABAJO).indice)
        assertEquals(0, paso(0, Tecla.ARRIBA).indice)
        assertEquals(4, paso(4, Tecla.ABAJO).indice)
        assertEquals(3, paso(4, Tecla.ARRIBA).indice)
    }

    @Test
    fun izquierdaYDerechaCambianElValorSinEnvolver() {
        assertEquals(AccionLista.Cambiar(2, -1, false), paso(2, Tecla.IZQUIERDA).accion)
        assertEquals(AccionLista.Cambiar(2, 1, false), paso(2, Tecla.DERECHA).accion)
    }

    @Test
    fun okAvanzaElValorEnvolviendoOAbreLaPantalla() {
        assertEquals(AccionLista.Cambiar(1, 1, true), paso(1, Tecla.OK).accion)
        assertEquals(AccionLista.Abrir(4), paso(4, Tecla.OK).accion)
    }

    @Test
    fun atrasVuelve() {
        assertEquals(AccionLista.Volver, paso(3, Tecla.ATRAS).accion)
        assertEquals(AccionLista.Volver, NavegacionLista.paso(0, Tecla.ATRAS, 0) { false }.accion)
    }
}

class TextosProblemasTest {
    private fun problema(t: TipoProblema, galeria: String = "G", archivo: String? = "a.jpg", detalle: String? = "x") =
        Problema(galeria, archivo, t, detalle)

    @Test
    fun cadaTipoDeProblemaTieneSuFraseEnStringsXml() {
        for (t in TipoProblema.values()) {
            val clave = TextosProblemas.clave(problema(t))
            assertNotEquals("Falta la frase de $t", 0, clave)
        }
    }

    @Test
    fun lasFrasesDeProblemasDistintosNoSeRepiten() {
        val claves = TipoProblema.values().map { TextosProblemas.clave(problema(it)) }
        assertEquals(claves.size, claves.toSet().size)
    }

    @Test
    fun elErrorDeRedTieneTresVersionesYLaFaltaDeClaveDos() {
        val raiz = TextosProblemas.clave(problema(TipoProblema.ERROR_RED, galeria = "", archivo = null))
        val carpeta = TextosProblemas.clave(problema(TipoProblema.ERROR_RED, archivo = null))
        val archivo = TextosProblemas.clave(problema(TipoProblema.ERROR_RED))
        assertEquals(3, setOf(raiz, carpeta, archivo).size)
        val sinCarpeta = TextosProblemas.clave(problema(TipoProblema.SIN_CLAVE, galeria = "", archivo = null, detalle = "carpeta"))
        val sinClave = TextosProblemas.clave(problema(TipoProblema.SIN_CLAVE, galeria = "", archivo = null, detalle = "clave"))
        assertNotEquals(sinCarpeta, sinClave)
    }

    @Test
    fun agrupaPorGaleriaYLaGeneralVaPrimera() {
        val ps = listOf(
            problema(TipoProblema.ARCHIVO_FALTANTE, galeria = "B"),
            problema(TipoProblema.ERROR_RED, galeria = "", archivo = null),
            problema(TipoProblema.JSON_INVALIDO, galeria = "A"),
            problema(TipoProblema.NO_ES_IMAGEN, galeria = "B"),
        )
        val g = TextosProblemas.agrupar(ps)
        assertEquals(listOf("", "B", "A"), g.map { it.first })
        assertEquals(2, g.first { it.first == "B" }.second.size)
    }
}

class SinObrasTest {
    private fun cat(problemaIntento: Problema? = null, problemas: List<Problema> = emptyList(), intento: Long? = 1L) =
        CatalogoCargado(emptyList(), problemas, null, intento, intento?.let { problemaIntento == null }, problemaIntento)

    @Test
    fun sincronizandoGanaSobreTodo() {
        assertEquals(MotivoSinObras.SINCRONIZANDO, SinObras.motivo(true, cat()))
        assertEquals(MotivoSinObras.SINCRONIZANDO, SinObras.motivo(false, null))
        assertEquals(MotivoSinObras.SINCRONIZANDO, SinObras.motivo(false, cat(intento = null)))
    }

    @Test
    fun sinInternetYSinClaveYCarpetaVacia() {
        val red = Problema("", null, TipoProblema.ERROR_RED, "red")
        assertEquals(MotivoSinObras.SIN_CONEXION, SinObras.motivo(false, cat(problemaIntento = red)))
        assertEquals(MotivoSinObras.SIN_CONEXION, SinObras.motivo(false, cat(problemas = listOf(Problema("G", null, TipoProblema.ERROR_RED, "503")))))
        assertEquals(MotivoSinObras.SIN_CLAVE, SinObras.motivo(false, cat(problemaIntento = Problema("", null, TipoProblema.SIN_CLAVE, "clave"))))
        assertEquals(MotivoSinObras.CARPETA_VACIA, SinObras.motivo(false, cat()))
        assertTrue(MotivoSinObras.values().size == 4)
    }
}
