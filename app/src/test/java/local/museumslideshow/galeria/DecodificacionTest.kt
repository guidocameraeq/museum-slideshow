package local.museumslideshow.galeria

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DecodificacionTest {
    @Test
    fun muestreoEsPotenciaDeDosQueNoBajaDelDestino() {
        assertEquals(1, Decodificacion.muestreo(3840, 2160, 3840, 2160))
        assertEquals(2, Decodificacion.muestreo(3840, 2160, 1920, 1080))
        assertEquals(2, Decodificacion.muestreo(3840, 2160, 1920, 1000))
        assertEquals(1, Decodificacion.muestreo(3840, 2160, 1925, 1080))    // a la mitad bajaría de 1925
        assertEquals(4, Decodificacion.muestreo(3840, 2160, 960, 540))
        assertEquals(8, Decodificacion.muestreo(3840, 2160, 480, 270))
    }

    @Test
    fun siLaObraEsMasChicaQueElLugarSeAbreEntera() {
        assertEquals(1, Decodificacion.muestreo(1600, 1245, 3000, 2000))
    }

    @Test
    fun enLaOtraMedidaTambienNoBaja() {
        // ancho alcanza para 4 pero el alto pide 2
        assertEquals(2, Decodificacion.muestreo(3840, 2160, 900, 1000))
    }

    @Test
    fun ladosRedondeanParaArriba() {
        assertEquals(1920, Decodificacion.lado(3840, 2))
        assertEquals(1001, Decodificacion.lado(2001, 2))
    }

    @Test
    fun unaObraSolaNoPasaDelPresupuestoDeUnaPantalla4K() {
        val pantalla = 3840L * 2160
        val p = listOf(PedidoDecodificacion(3840, 2160, 3840, 2160))
        assertEquals(listOf(1), Decodificacion.planificar(p, pantalla))
    }

    @Test
    fun tresVerticalesGrandesSeAchicanHastaEntrarEnUnaPantalla() {
        val pantalla = 3840L * 2160
        // tres obras de 1620x2160 (verticales) en lugares de ~960x1380: cada una sola pesa 3,5 Mpx, juntas 10,5 > 8,3
        val p = List(3) { PedidoDecodificacion(1620, 2160, 960, 1380) }
        val m = Decodificacion.planificar(p, pantalla)
        val total = p.indices.sumOf { Decodificacion.pixeles(p[it], m[it]) }
        assertTrue("total $total", total <= pantalla)
        assertTrue(m.all { it >= 1 })
        // solo se agrandó lo necesario: no todas
        assertTrue(m.any { it == 1 })
    }

    @Test
    fun conCalidad1080ElPresupuestoEsUnaPantallaDe1080() {
        val pantalla = 1920L * 1080
        val p = listOf(PedidoDecodificacion(3840, 2160, 1920, 1080))
        val m = Decodificacion.planificar(p, pantalla)
        assertEquals(listOf(2), m)
        assertTrue(Decodificacion.pixeles(p[0], m[0]) <= pantalla)
    }

    @Test
    fun siNadaEntraTerminaIgual() {
        val p = listOf(PedidoDecodificacion(3840, 2160, 3840, 2160))
        val m = Decodificacion.planificar(p, 10)
        assertEquals(64, m[0])   // tope del muestreo
    }

    @Test
    fun bytesDeUnaPantalla4K() {
        assertEquals(33_177_600L, Decodificacion.bytes(3840, 2160))
    }
}
