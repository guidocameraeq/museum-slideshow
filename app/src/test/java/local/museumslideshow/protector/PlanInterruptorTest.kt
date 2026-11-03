package local.museumslideshow.protector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanInterruptorTest {
    private val paquete = "local.museumslideshow"
    private val nuestro = "local.museumslideshow/local.museumslideshow.protector.ProtectorMuseo"
    private val ambient = "com.google.android.apps.tv.dreamx/.service.Backdrop"

    @Test
    fun usarGuardaElAnteriorYEscribeElNuestro() {
        val plan = PlanInterruptor.usar(ambient, nuestro, paquete)
        assertEquals(nuestro, plan.escribir)
        assertTrue(plan.guardarAnterior)
        assertEquals(ambient, plan.anterior)
    }

    @Test
    fun usarDosVecesNoPisaLoGuardado() {
        val plan = PlanInterruptor.usar(nuestro, nuestro, paquete)
        assertEquals(nuestro, plan.escribir)
        assertFalse(plan.guardarAnterior)
    }

    @Test
    fun usarConElNuestroEnLaFormaCortaTampocoPisa() {
        val plan = PlanInterruptor.usar("local.museumslideshow/.protector.ProtectorMuseo", nuestro, paquete)
        assertFalse(plan.guardarAnterior)
    }

    @Test
    fun usarConValorNuloOVacioLoGuardaComoVacio() {
        for (actual in listOf(null, "", "   ")) {
            val plan = PlanInterruptor.usar(actual, nuestro, paquete)
            assertTrue(plan.guardarAnterior)
            assertEquals(actual.orEmpty(), plan.anterior)
        }
    }

    @Test
    fun volverSinGuardadoPoneElAmbient() {
        assertEquals(ambient, PlanInterruptor.volver(null, paquete))
    }

    @Test
    fun volverConGuardadoVacioPoneElAmbient() {
        assertEquals(ambient, PlanInterruptor.volver("", paquete))
    }

    @Test
    fun volverRestauraExactamenteUnAnteriorDistintoDelAmbient() {
        val otro = "com.otra.app/.OtroProtector"
        assertEquals(otro, PlanInterruptor.volver(otro, paquete))
    }

    @Test
    fun volverRestauraUnaListaConComasTalCual() {
        val lista = "com.uno/.A,com.dos/.B"
        assertEquals(lista, PlanInterruptor.volver(lista, paquete))
    }

    @Test
    fun volverNuncaDejaElNuestroPuesto() {
        assertEquals(ambient, PlanInterruptor.volver(nuestro, paquete))
        assertEquals(ambient, PlanInterruptor.volver("com.uno/.A,$nuestro", paquete))
    }

    @Test
    fun esNuestroMiraCadaEntradaDeLaLista() {
        assertTrue(PlanInterruptor.esNuestro("com.uno/.A,$nuestro", paquete))
        assertTrue(PlanInterruptor.esNuestro("com.uno/.A, $nuestro", paquete))
        assertFalse(PlanInterruptor.esNuestro("com.uno/.A,com.dos/.B", paquete))
        assertFalse(PlanInterruptor.esNuestro(null, paquete))
        assertFalse(PlanInterruptor.esNuestro("", paquete))
        // un paquete que solo empieza igual no cuenta
        assertFalse(PlanInterruptor.esNuestro("local.museumslideshow2/.X", paquete))
    }

    @Test
    fun usarVolverUsarVolverDejaElOriginal() {
        var guardado: String? = null
        var sistema: String? = ambient
        repeat(2) {
            val u = PlanInterruptor.usar(sistema, nuestro, paquete)
            if (u.guardarAnterior) guardado = u.anterior
            sistema = u.escribir
            // tocar Usar de nuevo no cambia lo guardado
            val u2 = PlanInterruptor.usar(sistema, nuestro, paquete)
            if (u2.guardarAnterior) guardado = u2.anterior
            sistema = PlanInterruptor.volver(guardado, paquete)
            guardado = null
            assertEquals(ambient, sistema)
        }
    }
}
