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

class PaseConReservaTest {
    private fun h(id: String) = Datos.obra(id, 1000, 600)
    private val gm = Datos.tema(Tema.GALERIA_MODERNA)
    private val g = Galeria("g", "Galería", null, (1..6).map { h("o$it") })
    private val prefs = Preferencias(pared = Pared.APAGADA)

    private fun pase(p: Preferencias = prefs, galerias: List<Galeria> = listOf(g)) = Pase.armar(galerias, p, gm, 1L)!!

    private fun lista(vararg ids: String): (EscenaDef) -> Boolean = { e -> e.obras.all { it.driveId in ids } }

    @Test
    fun elPaseSeArmaConTodasLasObrasAunqueNoEstenBajadas() {
        val p = pase()
        assertEquals(6, p.escenas.size)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), p.escenas.map { it.posicion })
    }

    @Test
    fun avanzarSalteaLasNoBajadasSinPerderElContador() {
        val p = pase()
        val l = lista("o1", "o2", "o5")
        p.moverSaltando(1, l)
        assertEquals("o2", p.actual.obras[0].driveId)
        p.moverSaltando(1, l)
        assertEquals("o5", p.actual.obras[0].driveId)
        assertEquals(5, p.actual.posicion) // sigue siendo la quinta de la galería
        assertEquals(6, p.actual.total)
        p.moverSaltando(1, l)
        assertEquals("o1", p.actual.obras[0].driveId) // da la vuelta
        assertEquals(1, p.actual.posicion)
    }

    @Test
    fun retrocederTambienSaltea() {
        val p = pase()
        val l = lista("o1", "o2", "o5")
        p.moverSaltando(-1, l)
        assertEquals("o5", p.actual.obras[0].driveId)
        p.moverSaltando(-1, l)
        assertEquals("o2", p.actual.obras[0].driveId)
    }

    @Test
    fun siNoHayNingunaListaNoSeMueveYAvisa() {
        val p = pase()
        val ninguna = lista()
        assertEquals(0, p.indiceSaltando(1, ninguna))
        assertFalse(p.asegurarLista(ninguna))
        assertEquals(0, p.indice)
    }

    @Test
    fun siSoloLaActualEstaListaSeQuedaEnEsa() {
        val p = pase()
        val l = lista("o1")
        p.moverSaltando(3, l)
        assertEquals(0, p.indice)
    }

    @Test
    fun alArrancarSiLaPrimeraNoEstaBajadaPasaALaPrimeraQueSi() {
        val p = pase()
        assertTrue(p.asegurarLista(lista("o4", "o6")))
        assertEquals("o4", p.actual.obras[0].driveId)
        assertEquals(4, p.actual.posicion)
        // y si la actual ya está lista no se mueve
        assertTrue(p.asegurarLista(lista("o4")))
        assertEquals(3, p.indice)
    }

    @Test
    fun conMezclarYElSaltoSigueElOrdenMezclado() {
        val p = pase(prefs.copy(orden = Orden.MEZCLAR))
        val orden = p.ordenDeObras().map { it.driveId }
        val l = lista(orden[0], orden[2], orden[4])
        p.moverSaltando(1, l)
        assertEquals(orden[2], p.actual.obras[0].driveId)
        assertEquals(2, p.posicionDeObra())
    }

    @Test
    fun soloEntranAlPaseLasGaleriasTildadas() {
        val b = Galeria("gb", "Otra", null, listOf(h("b1"), h("b2")))
        val sinB = prefs.copy(seleccionGalerias = mapOf("gb" to false))
        val p = pase(sinB, listOf(g, b))
        assertEquals(setOf("g"), p.escenas.map { it.galeriaId }.toSet())
        // con "Galerías nuevas" destildadas, solo las que figuran tildadas
        val soloB = prefs.copy(galeriasNuevas = local.museumslideshow.ajustes.GaleriasNuevas.DESTILDADAS, seleccionGalerias = mapOf("gb" to true))
        assertEquals(setOf("gb"), pase(soloB, listOf(g, b)).escenas.map { it.galeriaId }.toSet())
        // "Todas" = todas las tildadas; sin ninguna tildada no hay pase
        val ninguna = prefs.copy(galeriasNuevas = local.museumslideshow.ajustes.GaleriasNuevas.DESTILDADAS)
        assertNull(Pase.armar(listOf(g, b), ninguna, gm, 1L))
        // una galería elegida en la Barra que está destildada cae en Todas las tildadas
        val elegidaDestildada = sinB.copy(galeria = "gb")
        assertEquals(setOf("g"), pase(elegidaDestildada, listOf(g, b)).escenas.map { it.galeriaId }.toSet())
    }

    @Test
    fun lasObrasSinMedidasNoEntranHastaTenerlas() {
        val sin = local.museumslideshow.catalogo.Obra("sm", "sm.jpg", 0, 0, null).conBajada(false)
        val gal = Galeria("g", "G", null, listOf(h("a"), sin, h("b")))
        val p = pase(galerias = listOf(gal))
        assertEquals(listOf("a", "b"), p.escenas.map { it.obras[0].driveId })
        assertEquals(listOf(2, 2), p.escenas.map { it.total })
        assertEquals(listOf("sm"), Pase.obrasSinMedidas(listOf(gal), prefs).map { it.driveId })
        // si todas están sin medidas no hay pase todavía
        assertNull(Pase.armar(listOf(Galeria("g", "G", null, listOf(sin))), prefs, gm, 1L))
    }

    @Test
    fun laPosicionYElOrdenParaLaReservaSumanLasObrasDeUnaPared() {
        fun v(id: String) = Datos.obra(id, 600, 1000)
        val gal = Galeria("g", "G", null, listOf(v("v1"), v("v2"), v("v3"), h("h1")))
        val p = Pase.armar(listOf(gal), Preferencias(), gm, 1L)!!
        assertEquals(listOf("v1", "v2", "v3", "h1"), p.ordenDeObras().map { it.driveId })
        assertEquals(0, p.posicionDeObra())
        p.mover(1)
        assertEquals(3, p.posicionDeObra()) // la segunda escena arranca después de las 3 de la Pared
    }
}
