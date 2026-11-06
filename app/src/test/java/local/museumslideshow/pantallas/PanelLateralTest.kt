package local.museumslideshow.pantallas

import local.museumslideshow.ajustes.Tema
import local.museumslideshow.catalogo.Galeria
import local.museumslideshow.catalogo.Obra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun galeria(id: String, titulo: String, padre: String? = null, obras: Int = 3) =
    Galeria(id, titulo, null, List(obras) { Obra("$id-$it", "$it.jpg", 10, 10, null) }, padreId = padre)

class ListaGaleriasPanelTest {
    private val titulos = mapOf("P" to "Pintura", "E" to "Escultura")
    private fun armar(vararg g: Galeria) = ListaGaleriasPanel.armar(g.toList()) { titulos[it] }

    @Test
    fun todasVaPrimeroConLaSumaDeObras() {
        val items = armar(galeria("a", "A", obras = 2), galeria("b", "B", obras = 5))
        assertEquals(ItemGalerias.Todas(7), items.first())
    }

    @Test
    fun lasSueltasVanSinEncabezadoYLosGruposJuntosBajoSuNombre() {
        val items = armar(
            galeria("g1", "Barroco", "P"), galeria("s1", "Suelta"), galeria("g2", "Rococó", "P"), galeria("e1", "Bronce", "E"),
        )
        assertEquals(
            listOf("Todas", "Suelta", "#Pintura", "Barroco", "Rococó", "#Escultura", "Bronce"),
            items.map {
                when (it) {
                    is ItemGalerias.Todas -> "Todas"
                    is ItemGalerias.Encabezado -> "#" + it.titulo
                    is ItemGalerias.Una -> it.titulo
                }
            },
        )
    }

    @Test
    fun unaGaleriaConPadreDesconocidoQuedaSuelta() {
        val items = armar(galeria("x", "X", "NO-ESTA"))
        assertEquals(2, items.size)
        assertTrue(items[1] is ItemGalerias.Una)
    }

    @Test
    fun losEncabezadosNoSeElijen() {
        assertFalse(ItemGalerias.Encabezado("x").elegible)
        assertTrue(ItemGalerias.Todas(1).elegible)
    }

    @Test
    fun laPosicionElegidaSaleDelIdODelTituloYSinNadaEsTodas() {
        val items = armar(galeria("g1", "Barroco", "P"), galeria("g2", "Rococó", "P"))
        // 0 Todas, 1 Encabezado, 2 Barroco, 3 Rococó
        assertEquals(0, ListaGaleriasPanel.posicionElegida(items, null))
        assertEquals(3, ListaGaleriasPanel.posicionElegida(items, "g2"))
        assertEquals(2, ListaGaleriasPanel.posicionElegida(items, "barroco"))
        assertEquals(0, ListaGaleriasPanel.posicionElegida(items, "ya-no-existe"))
    }
}

class NavegacionPanelTest {
    // 0 Todas, 1 Suelta, 2 #Pintura, 3 Barroco, 4 Rococó, 5 #Escultura, 6 Bronce
    private val items: List<ItemGalerias> = listOf(
        ItemGalerias.Todas(10), ItemGalerias.Una("s1", "Suelta", 2), ItemGalerias.Encabezado("Pintura"),
        ItemGalerias.Una("g1", "Barroco", 3), ItemGalerias.Una("g2", "Rococó", 3), ItemGalerias.Encabezado("Escultura"),
        ItemGalerias.Una("e1", "Bronce", 2),
    )

    private fun ctx(elegida: Int = 0, tema: Tema = Tema.GALERIA_MODERNA) = ContextoPanel(items, elegida, tema)
    private fun paso(e: EstadoPanel, t: Tecla, c: ContextoPanel = ctx()) = NavegacionPanel.paso(e, t, c)
    private fun menu(r: Renglon) = EstadoPanel(NivelPanel.MENU, r.ordinal)

    // ---- menú

    @Test
    fun enElMenuArribaYAbajoMuevenYDanLaVuelta() {
        assertEquals(1, paso(menu(Renglon.GALERIA), Tecla.ABAJO).estado.foco)
        assertEquals(Renglon.SINCRONIZAR.ordinal, paso(menu(Renglon.GALERIA), Tecla.ARRIBA).estado.foco)
        assertEquals(0, paso(menu(Renglon.SINCRONIZAR), Tecla.ABAJO).estado.foco)
    }

    @Test
    fun atrasEnElMenuCierra() {
        val p = paso(menu(Renglon.EXPLICACION), Tecla.ATRAS)
        assertEquals(EventoPanel.Cerrar, p.evento)
        assertEquals(EstadoPanel(), p.estado)
    }

    @Test
    fun okEnLosInterruptoresLosCambiaSinSalirDelMenu() {
        val a = paso(menu(Renglon.PANTALLA_COMPLETA), Tecla.OK)
        assertEquals(EventoPanel.AlternarPantallaCompleta, a.evento)
        assertEquals(menu(Renglon.PANTALLA_COMPLETA), a.estado)
        assertEquals(EventoPanel.AlternarExplicacion, paso(menu(Renglon.EXPLICACION), Tecla.OK).evento)
    }

    @Test
    fun ajustesYSincronizarSonEventos() {
        assertEquals(EventoPanel.AbrirAjustes, paso(menu(Renglon.AJUSTES), Tecla.OK).evento)
        assertEquals(EventoPanel.AbrirAjustes, paso(menu(Renglon.AJUSTES), Tecla.DERECHA).evento)
        assertEquals(EventoPanel.Sincronizar, paso(menu(Renglon.SINCRONIZAR), Tecla.OK).evento)
        assertNull(paso(menu(Renglon.SINCRONIZAR), Tecla.DERECHA).evento)
    }

    @Test
    fun izquierdaYDerechaEnLosInterruptoresNoHacenNada() {
        assertEquals(PasoPanel(menu(Renglon.EXPLICACION)), paso(menu(Renglon.EXPLICACION), Tecla.DERECHA))
        assertEquals(PasoPanel(menu(Renglon.EXPLICACION)), paso(menu(Renglon.EXPLICACION), Tecla.IZQUIERDA))
    }

    // ---- entrar y salir de las sublistas

    @Test
    fun okODerechaEntranALaGaleriaConElFocoEnLaPuesta() {
        assertEquals(EstadoPanel(NivelPanel.GALERIAS, 4), paso(menu(Renglon.GALERIA), Tecla.OK, ctx(elegida = 4)).estado)
        assertEquals(EstadoPanel(NivelPanel.GALERIAS, 3), paso(menu(Renglon.GALERIA), Tecla.DERECHA, ctx(elegida = 3)).estado)
    }

    @Test
    fun alEntrarALaGaleriaSiLaPuestaEraUnEncabezadoElFocoCaeEnLaProxima() {
        assertEquals(3, NavegacionPanel.entrar(NivelPanel.GALERIAS, ctx(elegida = 2)).foco)
    }

    @Test
    fun alEntrarAlTemaElFocoCaeEnElGuardado() {
        val p = paso(menu(Renglon.TEMA), Tecla.OK, ctx(tema = Tema.INMERSIVO))
        assertEquals(EstadoPanel(NivelPanel.TEMAS, Tema.INMERSIVO.ordinal), p.estado)
        assertEquals(Tema.INMERSIVO, p.estado.temaEnPrueba)
    }

    @Test
    fun atrasEIzquierdaVuelvenAlMenuEnElRenglonDeDondeSeEntro() {
        for (t in listOf(Tecla.ATRAS, Tecla.IZQUIERDA)) {
            assertEquals(EstadoPanel(NivelPanel.MENU, Renglon.GALERIA.ordinal), paso(EstadoPanel(NivelPanel.GALERIAS, 4), t).estado)
            assertEquals(EstadoPanel(NivelPanel.MENU, Renglon.TEMA.ordinal), paso(EstadoPanel(NivelPanel.TEMAS, 2), t).estado)
            assertNull(paso(EstadoPanel(NivelPanel.TEMAS, 2), t).evento)
        }
    }

    // ---- galerías

    @Test
    fun elFocoSaltaLosEncabezadosEnAmbosSentidos() {
        assertEquals(3, paso(EstadoPanel(NivelPanel.GALERIAS, 1), Tecla.ABAJO).estado.foco)    // salta "Pintura"
        assertEquals(1, paso(EstadoPanel(NivelPanel.GALERIAS, 3), Tecla.ARRIBA).estado.foco)
        assertEquals(6, paso(EstadoPanel(NivelPanel.GALERIAS, 4), Tecla.ABAJO).estado.foco)    // salta "Escultura"
    }

    @Test
    fun enLaListaDeGaleriasArribaYAbajoFrenanEnLasPuntas() {
        assertEquals(0, paso(EstadoPanel(NivelPanel.GALERIAS, 0), Tecla.ARRIBA).estado.foco)
        assertEquals(6, paso(EstadoPanel(NivelPanel.GALERIAS, 6), Tecla.ABAJO).estado.foco)
    }

    @Test
    fun okEligeLaGaleriaYCierraTodo() {
        val p = paso(EstadoPanel(NivelPanel.GALERIAS, 4), Tecla.OK)
        assertEquals(EventoPanel.ElegirGaleria("g2"), p.evento)
        assertEquals(EstadoPanel(), p.estado)
        assertEquals(EventoPanel.ElegirGaleria(null), paso(EstadoPanel(NivelPanel.GALERIAS, 0), Tecla.OK).evento)
    }

    @Test
    fun unFocoQueQuedoEnUnEncabezadoOFueraDeRangoSeCorrige() {
        assertEquals(3, NavegacionPanel.normalizar(EstadoPanel(NivelPanel.GALERIAS, 2), ctx()).foco)
        assertEquals(6, NavegacionPanel.normalizar(EstadoPanel(NivelPanel.GALERIAS, 99), ctx()).foco)
        assertEquals(0, NavegacionPanel.normalizar(EstadoPanel(NivelPanel.GALERIAS, 3), ContextoPanel(emptyList(), 0, Tema.INMERSIVO)).foco)
        assertEquals(Renglon.SINCRONIZAR.ordinal, NavegacionPanel.normalizar(EstadoPanel(NivelPanel.MENU, 40), ctx()).foco)
    }

    @Test
    fun cuantasGaleriasQuedanFueraDeVistaNoCuentaEncabezados() {
        assertEquals(0 to 3, NavegacionPanel.fueraDeVista(items, 0, 2))    // abajo: Barroco, Rococó y Bronce (el encabezado no cuenta)
        assertEquals(2 to 1, NavegacionPanel.fueraDeVista(items, 2, 5))    // arriba: Todas y Suelta; abajo: Bronce
        assertEquals(0 to 0, NavegacionPanel.fueraDeVista(items, 0, 6))
    }

    // ---- temas

    @Test
    fun elTemaEnPruebaEsElQueTieneElFocoSoloEnLaSublistaTema() {
        assertEquals(Tema.SALA_OSCURA, EstadoPanel(NivelPanel.TEMAS, 1).temaEnPrueba)
        assertNull(EstadoPanel(NivelPanel.MENU, 1).temaEnPrueba)
        assertNull(EstadoPanel(NivelPanel.GALERIAS, 1).temaEnPrueba)
    }

    @Test
    fun moverElFocoPorLosTemasCambiaLaPruebaYFrenaEnLasPuntas() {
        var e = EstadoPanel(NivelPanel.TEMAS, 0)
        e = paso(e, Tecla.ABAJO).estado
        assertEquals(Tema.SALA_OSCURA, e.temaEnPrueba)
        repeat(5) { e = paso(e, Tecla.ABAJO).estado }
        assertEquals(Tema.LIBRO_DE_ARTE, e.temaEnPrueba)
        assertEquals(Tema.GALERIA_MODERNA, paso(EstadoPanel(NivelPanel.TEMAS, 0), Tecla.ARRIBA).estado.temaEnPrueba)
    }

    @Test
    fun okGuardaElTemaProbadoYCierraTodo() {
        val p = paso(EstadoPanel(NivelPanel.TEMAS, 2), Tecla.OK)
        assertEquals(EventoPanel.ElegirTema(Tema.INMERSIVO), p.evento)
        assertEquals(EstadoPanel(), p.estado)
        assertNull(p.estado.temaEnPrueba)
    }

    @Test
    fun atrasDescartaLaPrueba() {
        val p = paso(EstadoPanel(NivelPanel.TEMAS, 3), Tecla.ATRAS)
        assertNull(p.evento)
        assertNull(p.estado.temaEnPrueba)   // ya no está en la sublista: la galería vuelve al tema guardado
    }
}

class CierreAutomaticoTest {
    private var ahora = 1_000L
    private fun cierre(limite: Long?) = CierreAutomatico(limite) { ahora }

    @Test
    fun seVenceAlPasarElLimite() {
        val c = cierre(10_000)
        ahora += 9_999
        assertFalse(c.vencido())
        ahora += 1
        assertTrue(c.vencido())
    }

    @Test
    fun cualquierTeclaReiniciaLaCuenta() {
        val c = cierre(10_000)
        ahora += 8_000
        c.reiniciar()
        ahora += 8_000
        assertFalse(c.vencido())
        ahora += 2_000
        assertTrue(c.vencido())
    }

    @Test
    fun laBarritaSeVaVaciandoDeUnoACero() {
        val c = cierre(10_000)
        assertEquals(1f, c.restante()!!, 0.001f)
        ahora += 2_500
        assertEquals(0.75f, c.restante()!!, 0.001f)
        ahora += 50_000
        assertEquals(0f, c.restante()!!, 0.001f)
    }

    @Test
    fun conNuncaNoSeVenceNiHayBarrita() {
        val c = cierre(null)
        ahora += 100_000_000
        assertFalse(c.vencido())
        assertNull(c.restante())
    }
}
