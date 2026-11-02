package local.museumslideshow.ajustes

import local.museumslideshow.catalogo.Arbol
import local.museumslideshow.catalogo.Galeria
import local.museumslideshow.catalogo.Grupo
import local.museumslideshow.catalogo.Obra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TildesTest {
    private fun galeria(id: String, titulo: String, padre: String?, n: Int = 2) =
        Galeria(id, titulo, null, (1..n).map { Obra("$id-$it", "$id$it.jpg", 100, 100, null) }, padre)

    /** Renacimiento > (Rafael, Leonardo > Bocetos); Barroco suelta; Mixta (galería y grupo) > Hija. */
    private val arbol = Arbol(
        grupos = listOf(Grupo("REN", "Renacimiento"), Grupo("LEO", "Leonardo", "REN"), Grupo("MIX", "Mixta")),
        galerias = listOf(
            galeria("RAF", "Rafael", "REN"), galeria("BOC", "Bocetos", "LEO", 3), galeria("BAR", "Barroco", null),
            galeria("MIX", "Mixta", null), galeria("HIJA", "Hija", "MIX"),
        ),
    )

    @Test
    fun unaGaleriaQueNoFiguraTomaElValorDeGaleriasNuevas() {
        assertTrue(Preferencias().estaTildada("X"))
        assertFalse(Preferencias(galeriasNuevas = GaleriasNuevas.DESTILDADAS).estaTildada("X"))
        val p = Preferencias(galeriasNuevas = GaleriasNuevas.DESTILDADAS, seleccionGalerias = mapOf("X" to true))
        assertTrue(p.estaTildada("X"))
        assertFalse(p.estaTildada("Y"))
        // lo que está en el mapa manda aunque cambie el valor para las nuevas
        val q = Preferencias(galeriasNuevas = GaleriasNuevas.TILDADAS, seleccionGalerias = mapOf("X" to false))
        assertFalse(q.estaTildada("X"))
        assertTrue(q.estaTildada("Y"))
    }

    @Test
    fun tildarUnaGaleriaSoloCambiaEsa() {
        val p = arbol.tildar(Preferencias(), "RAF", false)
        assertFalse(p.estaTildada("RAF"))
        assertTrue(p.estaTildada("BOC"))
        assertEquals(mapOf("RAF" to false), p.seleccionGalerias)
    }

    @Test
    fun tildarUnGrupoTildaTodoLoDeAdentroIncluidasLasAnidadas() {
        val p = arbol.tildar(Preferencias(), "REN", false)
        assertFalse(p.estaTildada("RAF"))
        assertFalse(p.estaTildada("BOC"))
        assertTrue(p.estaTildada("BAR"))
        assertTrue(p.estaTildada("MIX"))
        val q = arbol.tildar(p, "REN", true)
        assertTrue(q.estaTildada("RAF") && q.estaTildada("BOC"))
    }

    @Test
    fun unaCarpetaQueEsGaleriaYGrupoTildaSuPropiaGaleriaYLasDeAdentro() {
        val p = arbol.tildar(Preferencias(), "MIX", false)
        assertFalse(p.estaTildada("MIX"))
        assertFalse(p.estaTildada("HIJA"))
        // tildar solo la hija no toca a la madre
        val q = arbol.tildar(p, "HIJA", true)
        assertTrue(q.estaTildada("HIJA"))
        assertFalse(q.estaTildada("MIX"))
    }

    @Test
    fun estadoDeUnGrupoTodasAlgunasNinguna() {
        val todas = Preferencias()
        assertEquals(EstadoGrupo.TODAS, arbol.estadoGrupo("REN", todas))
        assertEquals(EstadoGrupo.ALGUNAS, arbol.estadoGrupo("REN", arbol.tildar(todas, "BOC", false)))
        assertEquals(EstadoGrupo.NINGUNA, arbol.estadoGrupo("REN", arbol.tildar(todas, "REN", false)))
        // una galería sola cuenta como un grupo de una
        assertEquals(EstadoGrupo.TODAS, arbol.estadoGrupo("BAR", todas))
        assertEquals(EstadoGrupo.NINGUNA, arbol.estadoGrupo("BAR", arbol.tildar(todas, "BAR", false)))
        // el estado respeta "Galerías nuevas" para lo que no figura
        assertEquals(EstadoGrupo.NINGUNA, arbol.estadoGrupo("REN", Preferencias(galeriasNuevas = GaleriasNuevas.DESTILDADAS)))
        val mezcla = Preferencias(galeriasNuevas = GaleriasNuevas.DESTILDADAS, seleccionGalerias = mapOf("RAF" to true))
        assertEquals(EstadoGrupo.ALGUNAS, arbol.estadoGrupo("REN", mezcla))
    }

    @Test
    fun cuantasObrasTieneUnNodo() {
        assertEquals(5, arbol.cantidadObras("REN"))
        assertEquals(3, arbol.cantidadObras("LEO"))
        assertEquals(2, arbol.cantidadObras("BAR"))
        assertEquals(4, arbol.cantidadObras("MIX"))
    }

    @Test
    fun resolverPorIdONombreSinMayusculasNiTildes() {
        assertEquals(listOf("REN"), arbol.resolver("REN"))
        assertEquals(listOf("RAF"), arbol.resolver("rafael"))
        assertEquals(listOf("LEO"), arbol.resolver("  LEONARDO "))
        assertEquals(listOf("BAR"), arbol.resolver("BARROCO"))
        assertTrue(arbol.resolver("nada").isEmpty())
        val conTilde = Arbol(emptyList(), listOf(galeria("A", "Pintura Española", null)))
        assertEquals(listOf("A"), conTilde.resolver("pintura espanola"))
    }

    @Test
    fun siUnNombreCoincideConUnGrupoYUnaGaleriaConElMismoIdSeDevuelveUnaVez() {
        assertEquals(listOf("MIX"), arbol.resolver("mixta"))
    }
}

class CodecAjustesNuevosTest {
    @Test
    fun laSeleccionIdaYVuelta() {
        val sel = mapOf("A-1" to true, "b_2" to false)
        assertEquals(sel, CodecPreferencias.decodificarSeleccion(CodecPreferencias.codificarSeleccion(sel)))
        val p = Preferencias(seleccionGalerias = sel, galeriasNuevas = GaleriasNuevas.DESTILDADAS, reservaObras = 200)
        assertEquals(p, CodecPreferencias.decodificar(CodecPreferencias.codificar(p)))
    }

    @Test
    fun lasClavesNuevasFaltantesVuelvenAlDefecto() {
        val p = CodecPreferencias.decodificar(emptyMap())
        assertEquals(emptyMap<String, Boolean>(), p.seleccionGalerias)
        assertEquals(GaleriasNuevas.TILDADAS, p.galeriasNuevas)
        assertEquals(50, p.reservaObras)
    }

    @Test
    fun valoresRarosSeIgnoranSinRomper() {
        assertEquals(mapOf("OK" to true), CodecPreferencias.decodificarSeleccion("OK:1,mal:x,:1,../a:1,a:b:c,sin-dos-puntos,"))
        assertEquals(emptyMap<String, Boolean>(), CodecPreferencias.decodificarSeleccion(null))
        assertEquals(emptyMap<String, Boolean>(), CodecPreferencias.decodificarSeleccion("   "))
        val p = CodecPreferencias.decodificar(mapOf("galerias_nuevas" to "NADA", "reserva" to "-4"))
        assertEquals(GaleriasNuevas.TILDADAS, p.galeriasNuevas)
        assertEquals(50, p.reservaObras)
        assertEquals(50, CodecPreferencias.decodificar(mapOf("reserva" to "cinco")).reservaObras)
    }

    @Test
    fun sinSeleccionNoHayClaveYElDefectoSeGuarda() {
        val m = CodecPreferencias.codificar(Preferencias())
        assertFalse("galerias_seleccion" in m)
        assertEquals("TILDADAS", m["galerias_nuevas"])
        assertEquals("50", m["reserva"])
    }

    @Test
    fun porAdbNuevasYReserva() {
        val p = PreferenciasDesdeTexto.aplicar(Preferencias(), mapOf("nuevas" to "Destildadas", "reserva" to "5"))
        assertEquals(GaleriasNuevas.DESTILDADAS, p.galeriasNuevas)
        assertEquals(5, p.reservaObras)
        assertEquals(GaleriasNuevas.TILDADAS, PreferenciasDesdeTexto.aplicar(p, mapOf("nuevas" to "tildadas")).galeriasNuevas)
        // inválidos se ignoran
        val q = PreferenciasDesdeTexto.aplicar(p, mapOf("nuevas" to "quizas", "reserva" to "0"))
        assertEquals(p, q)
    }

    @Test
    fun laReservaDeLaPantallaRecorre20_50_100_200() {
        assertEquals(listOf(20, 50, 100, 200), Pasos.RESERVA_OBRAS)
        assertEquals(100, Pasos.mover(50, Pasos.RESERVA_OBRAS, 1, false))
        assertEquals(20, Pasos.mover(50, Pasos.RESERVA_OBRAS, -1, false))
        assertEquals(20, Pasos.mover(5, Pasos.RESERVA_OBRAS, 1, false)) // un 5 puesto por adb salta al primer paso
    }
}
