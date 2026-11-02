package local.museumslideshow.pantallas

import local.museumslideshow.ajustes.MarcoElegido
import local.museumslideshow.ajustes.Modo
import local.museumslideshow.ajustes.Preferencias
import local.museumslideshow.ajustes.Tamano
import local.museumslideshow.ajustes.Tema
import local.museumslideshow.catalogo.CatalogoCargado
import local.museumslideshow.catalogo.Galeria
import local.museumslideshow.catalogo.Obra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavegacionInicioTest {
    private fun paso(f: Int, t: Tecla) = NavegacionInicio.paso(f, t)

    @Test
    fun abajoDesdeElPrincipalVaAlPrimerBotonYArribaVuelve() {
        assertEquals(1, paso(0, Tecla.ABAJO).foco)
        assertEquals(0, paso(1, Tecla.ARRIBA).foco)
        assertEquals(0, paso(2, Tecla.ARRIBA).foco)
        assertEquals(0, paso(0, Tecla.ARRIBA).foco)
    }

    @Test
    fun losSeisBotonesVanEnDosColumnas() {
        assertEquals(3, paso(1, Tecla.ABAJO).foco)
        assertEquals(4, paso(2, Tecla.ABAJO).foco)
        assertEquals(6, paso(4, Tecla.ABAJO).foco)
        assertEquals(1, paso(3, Tecla.ARRIBA).foco)
        assertEquals(4, paso(6, Tecla.ARRIBA).foco)
        // en la última fila no hay más abajo
        assertEquals(5, paso(5, Tecla.ABAJO).foco)
        assertEquals(6, paso(6, Tecla.ABAJO).foco)
    }

    @Test
    fun izquierdaYDerechaCambianDeColumnaSinSalirseDeLaFila() {
        assertEquals(2, paso(1, Tecla.DERECHA).foco)
        assertEquals(1, paso(2, Tecla.IZQUIERDA).foco)
        assertEquals(6, paso(5, Tecla.DERECHA).foco)
        assertEquals(2, paso(2, Tecla.DERECHA).foco)    // ya está a la derecha
        assertEquals(1, paso(1, Tecla.IZQUIERDA).foco)  // ya está a la izquierda
        assertEquals(0, paso(0, Tecla.DERECHA).foco)    // el principal ocupa todo el ancho
        assertEquals(0, paso(0, Tecla.IZQUIERDA).foco)
    }

    @Test
    fun okAbreElBotonConFocoYAtrasSale() {
        val destinos = DestinoInicio.values()
        for (f in 0..6) assertEquals(destinos[f], paso(f, Tecla.OK).abrir)
        assertEquals(DestinoInicio.VER_GALERIA, paso(0, Tecla.OK).abrir)
        assertEquals(DestinoInicio.ESTADO, paso(6, Tecla.OK).abrir)
        assertTrue(paso(3, Tecla.ATRAS).salir)
        assertNull(paso(3, Tecla.ATRAS).abrir)
        assertFalse(paso(3, Tecla.OK).salir)
    }

    @Test
    fun unFocoFueraDeRangoSeCorrige() {
        assertEquals(DestinoInicio.ESTADO, paso(99, Tecla.OK).abrir)
        assertEquals(DestinoInicio.VER_GALERIA, paso(-4, Tecla.OK).abrir)
    }
}

class PantallaAppExtraTest {
    @Test
    fun elExtraPantallaAbreCadaUnaDirecto() {
        for (p in PantallaApp.values()) assertEquals(p, PantallaApp.deIntent(mapOf("pantalla" to p.id)))
        assertEquals(PantallaApp.APARIENCIA, PantallaApp.deIntent(mapOf("pantalla" to " Apariencia ")))
        assertEquals(PantallaApp.CONEXION, PantallaApp.deIntent(mapOf("pantalla" to "Conexión")))
    }

    @Test
    fun sinExtrasEsInicioYUnaPantallaInventadaNoCambiaNada() {
        assertEquals(PantallaApp.INICIO, PantallaApp.deIntent(emptyMap()))
        assertNull(PantallaApp.deIntent(mapOf("pantalla" to "inventada")))
    }

    @Test
    fun losOtrosExtrasNoCambianDePantallaPeroEscenaAbreLaGaleria() {
        assertNull(PantallaApp.deIntent(mapOf("tema" to "sala-oscura")))
        assertEquals(PantallaApp.GALERIA, PantallaApp.deIntent(mapOf("escena" to "3", "tema" to "inmersivo")))
        // si piden pantalla y escena a la vez, manda la pantalla
        assertEquals(PantallaApp.ESTADO, PantallaApp.deIntent(mapOf("escena" to "3", "pantalla" to "estado")))
    }

    @Test
    fun estanLasSieteDelEncargo() {
        val ids = PantallaApp.values().map { it.id }
        assertTrue(ids.containsAll(listOf("inicio", "galeria", "apariencia", "pase", "conexion", "protector", "estado")))
    }
}

class ResumenInicioTest {
    private fun obra(id: String) = Obra(id, "$id.jpg", 100, 100, null)
    private fun galeria(id: String, titulo: String, n: Int) = Galeria(id, titulo, null, (1..n).map { obra("$id-$it") })
    private fun cat(vararg g: Galeria) = CatalogoCargado(g.toList(), emptyList(), 123L)

    @Test
    fun sinCatalogoTodoEnCero() {
        val r = ResumenInicio.de(null, Preferencias())
        assertEquals(0, r.obras)
        assertEquals(0, r.obrasDelPase)
        assertNull(r.tapa)
        assertNull(r.galeriaElegida)
    }

    @Test
    fun conTodasLaTapaEsLaPrimeraObraDelCatalogoYLosNumerosSuman() {
        val r = ResumenInicio.de(cat(galeria("A", "Barroco", 3), galeria("B", "Impresionismo", 5)), Preferencias())
        assertEquals("A-1", r.tapa?.driveId)
        assertNull(r.galeriaElegida)
        assertEquals(8, r.obrasDelPase)
        assertEquals(2, r.galerias)
        assertEquals(8, r.obras)
        assertEquals(0, r.obrasListas) // todavía sin reserva
        assertEquals(8, r.obrasListasDe)
        assertEquals(123L, r.ultimaSincronizacion)
    }

    @Test
    fun conUnaGaleriaElegidaLaTapaYElSubtituloSonDeEsa() {
        val c = cat(galeria("A", "Barroco", 3), galeria("B", "Impresionismo", 5))
        val r = ResumenInicio.de(c, Preferencias(galeria = "B"))
        assertEquals("Impresionismo", r.galeriaElegida)
        assertEquals(5, r.obrasDelPase)
        assertEquals("B-1", r.tapa?.driveId)
        assertEquals(8, r.obras)
        // también por título, como acepta el pase
        assertEquals("A-1", ResumenInicio.de(c, Preferencias(galeria = "barroco")).tapa?.driveId)
    }

    @Test
    fun unaGaleriaQueNoExisteCuentaComoTodas() {
        val r = ResumenInicio.de(cat(galeria("A", "Barroco", 3), galeria("B", "Impresionismo", 5)), Preferencias(galeria = "borrada"))
        assertNull(r.galeriaElegida)
        assertEquals(8, r.obrasDelPase)
    }

    @Test
    fun laElegidaSinObrasBajadasUsaLaPrimeraDelCatalogoDeFondo() {
        val r = ResumenInicio.de(cat(galeria("A", "Barroco", 3), galeria("B", "Vacia", 0)), Preferencias(galeria = "B"))
        assertEquals("Vacia", r.galeriaElegida)
        assertEquals(0, r.obrasDelPase)
        assertEquals("A-1", r.tapa?.driveId)
    }
}

class VistaPreviaTest {
    @Test
    fun laParedSigueAlColorElegidoSegunElModo() {
        val claro = Preferencias(modo = Modo.CLARO, colorParedClaro = "piedra", colorParedOscuro = "bordo")
        assertEquals("piedra", DatosVistaPrevia.de(claro, 12 * 60).idColor)
        val oscuro = claro.copy(modo = Modo.OSCURO)
        assertEquals("bordo", DatosVistaPrevia.de(oscuro, 12 * 60).idColor)
        // Automático: de día claro, de noche oscuro
        val auto = claro.copy(modo = Modo.AUTOMATICO)
        assertEquals("piedra", DatosVistaPrevia.de(auto, 12 * 60).idColor)
        assertEquals("bordo", DatosVistaPrevia.de(auto, 23 * 60).idColor)
    }

    @Test
    fun sinColorElegidoUsaElDelTema() {
        assertEquals("blanco-galeria", DatosVistaPrevia.de(Preferencias(modo = Modo.CLARO), 0).idColor)
        assertEquals("negro", DatosVistaPrevia.de(Preferencias(tema = Tema.SALA_OSCURA, modo = Modo.OSCURO), 0).idColor)
    }

    @Test
    fun elTamanoYElMarcoSeVen() {
        assertEquals(0.7f, DatosVistaPrevia.de(Preferencias(tamano = Tamano.NORMAL), 0).escala)
        assertEquals(1f, DatosVistaPrevia.de(Preferencias(tamano = Tamano.MAXIMO), 0).escala)
        assertEquals(1f, DatosVistaPrevia.de(Preferencias(tema = Tema.INMERSIVO, tamano = Tamano.NORMAL), 0).escala)   // el tema manda
        assertEquals("fino", DatosVistaPrevia.de(Preferencias(), 0).marco)
        assertEquals("sin", DatosVistaPrevia.de(Preferencias(marco = MarcoElegido.FIJO("sin")), 0).marco)
        assertEquals("sin", DatosVistaPrevia.de(Preferencias(tema = Tema.INMERSIVO), 0).marco)
    }
}

class ResumenInicioConReservaTest {
    private fun obra(id: String) = Obra(id, "$id.jpg", 100, 100, null)
    private fun galeria(id: String, titulo: String, n: Int) = Galeria(id, titulo, null, (1..n).map { obra("$id-$it") })
    private fun cat(vararg g: Galeria) = CatalogoCargado(g.toList(), emptyList(), 123L)

    @Test
    fun soloCuentanLasGaleriasTildadas() {
        val c = cat(galeria("A", "Barroco", 3), galeria("B", "Impresionismo", 5))
        val r = ResumenInicio.de(c, Preferencias(seleccionGalerias = mapOf("B" to false)))
        assertEquals(1, r.galerias)
        assertEquals(3, r.obras)
        assertEquals(3, r.obrasDelPase)
        assertEquals("A-1", r.tapa?.driveId)
        assertEquals("A", r.tapaGaleriaId)
    }

    @Test
    fun conLaReservaMuestraLasListasDeLasProximas() {
        val c = cat(galeria("A", "Barroco", 80))
        val estado = local.museumslideshow.reserva.EstadoReserva(listasPorDelante = 38, porDelante = 50, vistasGuardadas = 10, conPedido = true)
        val r = ResumenInicio.de(c, Preferencias(), estado)
        assertEquals(38, r.obrasListas)
        assertEquals(50, r.obrasListasDe)
    }

    @Test
    fun sinPedidoTodaviaUsaLoQueHayGuardadoYElAjuste() {
        val c = cat(galeria("A", "Barroco", 80))
        val estado = local.museumslideshow.reserva.EstadoReserva(obrasGuardadas = 12)
        val r = ResumenInicio.de(c, Preferencias(reservaObras = 20), estado)
        assertEquals(12, r.obrasListas)
        assertEquals(20, r.obrasListasDe)
    }

    @Test
    fun todasDestildadasDejaInicioEnCero() {
        val c = cat(galeria("A", "Barroco", 3))
        val r = ResumenInicio.de(c, Preferencias(galeriasNuevas = local.museumslideshow.ajustes.GaleriasNuevas.DESTILDADAS))
        assertEquals(0, r.galerias)
        assertEquals(0, r.obras)
        assertNull(r.tapa)
    }
}
