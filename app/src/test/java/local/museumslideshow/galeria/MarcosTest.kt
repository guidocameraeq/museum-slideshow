package local.museumslideshow.galeria

import local.museumslideshow.temas.CatalogoMarcos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarcosTest {
    @Test
    fun elLadoMasGruesoMideElGrosorYLosDemasVanEnProporcion() {
        val b = Bordes(100, 50, 100, 200)
        val out = FloatArray(4)
        b.escalar(40f, out)
        assertEquals(20f, out[0], 0.001f)
        assertEquals(10f, out[1], 0.001f)
        assertEquals(20f, out[2], 0.001f)
        assertEquals(40f, out[3], 0.001f)
    }

    @Test
    fun elMismoFactorParaLosCuatroLadosConservaLasProporciones() {
        val b = Bordes(110, 107, 111, 116)
        val out = FloatArray(4)
        b.escalar(11f, out)
        assertEquals(11f, out.max(), 0.001f)
        assertEquals(110f / 116f, out[0] / out[3], 0.001f)
        assertEquals(107f / 111f, out[1] / out[2], 0.001f)
    }

    @Test
    fun bordesEnCeroNoDibujanNiDividenPorCero() {
        val out = floatArrayOf(9f, 9f, 9f, 9f)
        Bordes(0, 0, 0, 0).escalar(10f, out)
        assertTrue(out.all { it == 0f })
    }

    @Test
    fun cadaMarcoRectangularConFotoTieneSusBordes() {
        for (m in CatalogoMarcos.todos.filter { it.imagenDisponible && it.id != "sin" && !it.redondo }) {
            val b = Marcos.BORDES_DE_FOTO[m.id]
            assertTrue("${m.id} sin bordes", b != null && b.maximo > 0)
        }
    }
}
