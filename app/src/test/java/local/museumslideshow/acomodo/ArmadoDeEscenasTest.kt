package local.museumslideshow.acomodo

import local.museumslideshow.ajustes.Pared
import local.museumslideshow.ajustes.Tema
import local.museumslideshow.catalogo.Forma
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArmadoDeEscenasTest {
    private fun v(id: String) = Datos.obra(id, 600, 1000)   // 0,6: vertical
    private fun h(id: String) = Datos.obra(id, 1000, 600)
    private fun ids(e: List<List<local.museumslideshow.catalogo.Obra>>) = e.map { g -> g.map { it.driveId } }
    private val gm = Datos.tema(Tema.GALERIA_MODERNA)

    @Test
    fun agrupaVerticalesConsecutivasHastaTres() {
        val e = ArmadoDeEscenas.armar(listOf(h("h1"), v("v1"), v("v2"), v("v3"), v("v4"), h("h2")), Pared.HASTA_3, gm)
        assertEquals(listOf(listOf("h1"), listOf("v1", "v2", "v3"), listOf("v4"), listOf("h2")), ids(e))
    }

    @Test
    fun hastaDosAgrupaDeADos() {
        val e = ArmadoDeEscenas.armar(listOf(v("v1"), v("v2"), v("v3")), Pared.HASTA_2, gm)
        assertEquals(listOf(listOf("v1", "v2"), listOf("v3")), ids(e))
    }

    @Test
    fun paredApagadaYLibroDeArteVanDeAUna() {
        val obras = listOf(v("v1"), v("v2"), v("v3"))
        assertEquals(3, ArmadoDeEscenas.armar(obras, Pared.APAGADA, gm).size)
        assertEquals(3, ArmadoDeEscenas.armar(obras, Pared.HASTA_3, Datos.tema(Tema.LIBRO_DE_ARTE)).size)
    }

    @Test
    fun laVerticalSolaVaSolaYLaHorizontalCortaLaPared() {
        val e = ArmadoDeEscenas.armar(listOf(v("v1"), h("h"), v("v2")), Pared.HASTA_3, gm)
        assertEquals(listOf(listOf("v1"), listOf("h"), listOf("v2")), ids(e))
    }

    @Test
    fun laRedondaNoCuentaComoVerticalAunqueSeaAngosta() {
        val redonda = Datos.obra("r", 600, 1000, forma = Forma.REDONDA)
        assertFalse(ArmadoDeEscenas.esVertical(redonda))
        val e = ArmadoDeEscenas.armar(listOf(v("v1"), redonda, v("v2")), Pared.HASTA_3, gm)
        assertEquals(3, e.size)
    }

    @Test
    fun ochentaPorCientoJustoNoEsVertical() {
        assertFalse(ArmadoDeEscenas.esVertical(Datos.obra("c", 800, 1000)))
        assertTrue(ArmadoDeEscenas.esVertical(Datos.obra("c", 799, 1000)))
    }

    @Test
    fun conservaElOrdenYNoPierdeObras() {
        val obras = Datos.variadas
        val e = ArmadoDeEscenas.armar(obras, Pared.HASTA_3, gm)
        assertEquals(obras.map { it.driveId }, e.flatten().map { it.driveId })
        assertTrue(e.all { it.size in 1..3 })
    }
}
