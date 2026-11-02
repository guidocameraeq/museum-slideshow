package local.museumslideshow.galeria

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesenfoqueTest {
    @Test
    fun unaImagenLisaQuedaIgual() {
        val px = IntArray(8 * 6) { 0xFF336699.toInt() }
        val r = Desenfoque.cajas(px, 8, 6, 2, 2)
        assertTrue(r.all { it == 0xFF336699.toInt() })
    }

    @Test
    fun unPuntoBlancoSeReparteYBajaDeIntensidad() {
        val w = 9
        val h = 9
        val px = IntArray(w * h) { 0xFF000000.toInt() }
        px[4 * w + 4] = 0xFFFFFFFF.toInt()
        val r = Desenfoque.cajas(px, w, h, 1, 1)
        val centro = r[4 * w + 4] and 0xFF
        val vecino = r[4 * w + 5] and 0xFF
        assertTrue(centro < 255 && centro > 0)
        assertTrue(vecino > 0)
        assertTrue(centro >= vecino)
        assertEquals(0xFF, r[0] ushr 24)     // el alfa queda opaco
        assertEquals(0, r[0] and 0xFF)         // lejos no llegó nada
    }

    @Test
    fun radioCeroDevuelveUnaCopia() {
        val px = intArrayOf(1, 2, 3, 4)
        val r = Desenfoque.cajas(px, 2, 2, 0, 3)
        assertTrue(px.contentEquals(r))
        assertTrue(px !== r)
    }
}
