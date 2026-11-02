package local.museumslideshow.galeria

import local.museumslideshow.acomodo.Datos
import local.museumslideshow.ajustes.Orden
import local.museumslideshow.ajustes.Pared
import local.museumslideshow.ajustes.Preferencias
import local.museumslideshow.ajustes.Tema
import local.museumslideshow.catalogo.Galeria
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaseTest {
    private fun h(id: String) = Datos.obra(id, 1000, 600)
    private fun v(id: String) = Datos.obra(id, 600, 1000)
    private val gm = Datos.tema(Tema.GALERIA_MODERNA)

    private fun galeria(id: String, titulo: String, vararg o: local.museumslideshow.catalogo.Obra) =
        Galeria(id, titulo, null, o.toList())

    private val a = galeria("ga", "Impresionismo", h("a1"), h("a2"), h("a3"))
    private val b = galeria("gb", "Retratos", v("b1"), v("b2"), v("b3"), h("b4"))

    private fun pase(prefs: Preferencias = Preferencias(), semilla: Long = 1L, obra: String? = null, indice: Int? = null) =
        Pase.armar(listOf(a, b), prefs, gm, semilla, obra, indice)!!

    @Test
    fun todasLasGaleriasEnElOrdenDelCatalogoYContadorPorGaleria() {
        val p = pase()
        // a: 3 escenas sueltas; b: una Pared de 3 verticales + una horizontal = 2 escenas
        assertEquals(5, p.escenas.size)
        assertEquals(listOf("ga", "ga", "ga", "gb", "gb"), p.escenas.map { it.galeriaId })
        assertEquals(listOf(1, 2, 3, 1, 2), p.escenas.map { it.posicion })
        assertEquals(listOf(3, 3, 3, 2, 2), p.escenas.map { it.total })
        assertEquals(listOf("b1", "b2", "b3"), p.escenas[3].obras.map { it.driveId })
        assertEquals("Retratos", p.escenas[3].galeriaTitulo)
    }

    @Test
    fun unaGaleriaElegidaPorIdOPorTitulo() {
        val porId = pase(Preferencias(galeria = "gb"))
        assertEquals(setOf("gb"), porId.escenas.map { it.galeriaId }.toSet())
        val porTitulo = pase(Preferencias(galeria = "impresionismo"))
        assertEquals(setOf("ga"), porTitulo.escenas.map { it.galeriaId }.toSet())
        // una que no existe cae en Todas
        assertEquals(5, pase(Preferencias(galeria = "no-existe")).escenas.size)
    }

    @Test
    fun avanzarYRetrocederDanLaVuelta() {
        val p = pase()
        assertEquals(0, p.indice)
        p.mover(-1)
        assertEquals(4, p.indice)
        p.mover(1)
        assertEquals(0, p.indice)
        p.mover(7)
        assertEquals(2, p.indice)
        assertEquals(3, p.indiceRelativo(1))
        assertEquals(2, p.indice)   // indiceRelativo no se mueve
    }

    @Test
    fun mezclarEsEstablePorSesionYCambiaConOtraSemilla() {
        val muchas = galeria("gm", "Muchas", *Array(30) { h("o$it") })
        val prefs = Preferencias(orden = Orden.MEZCLAR, pared = Pared.APAGADA)
        fun ids(sem: Long) = Pase.armar(listOf(muchas), prefs, gm, sem)!!.escenas.map { it.obras[0].driveId }
        assertEquals(ids(7L), ids(7L))
        assertNotEquals(ids(7L), ids(8L))
        assertEquals(muchas.obras.map { it.driveId }.toSet(), ids(7L).toSet())
        // sin mezclar queda el orden de la galería
        val igual = Pase.armar(listOf(muchas), Preferencias(pared = Pared.APAGADA), gm, 7L)!!.escenas.map { it.obras[0].driveId }
        assertEquals(muchas.obras.map { it.driveId }, igual)
    }

    @Test
    fun rearmarConservaLaObraActualOElIndiceSolicitado() {
        val p = pase(obra = "a3")
        assertEquals(2, p.indice)
        assertEquals(1, pase(obra = "a3", indice = 1).indice)         // el índice pedido manda
        assertEquals(3, pase(obra = "b2").indice)                       // b2 está en la Pared de la escena 3
        assertEquals(0, pase(obra = "ya-no-existe").indice)
    }

    @Test
    fun sinObrasNoHayPase() {
        assertNull(Pase.armar(emptyList(), Preferencias(), gm, 1L))
        assertNull(Pase.armar(listOf(galeria("v", "Vacía")), Preferencias(), gm, 1L))
        assertNotNull(Pase.armar(listOf(a), Preferencias(), gm, 1L))
    }

    @Test
    fun pausaYReloj() {
        val p = pase()
        assertFalse(p.pausado)
        assertTrue(p.alternarPausa())
        assertTrue(p.pausado)
        p.seguir()
        assertFalse(p.pausado)
        p.pausar()
        assertTrue(p.pausado)
        // el reloj: en pausa nunca avanza; sin pausa avanza al cumplirse el tiempo
        assertFalse(Pase.debeAvanzar(999_999, 60_000, true))
        assertFalse(Pase.debeAvanzar(59_999, 60_000, false))
        assertTrue(Pase.debeAvanzar(60_000, 60_000, false))
    }
}
