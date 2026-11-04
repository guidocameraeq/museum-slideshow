package local.museumslideshow.acomodo

import local.museumslideshow.acomodo.Datos.pedido
import local.museumslideshow.ajustes.CartelObra
import local.museumslideshow.ajustes.ExplicacionEnPantalla
import local.museumslideshow.ajustes.MarcoElegido
import local.museumslideshow.ajustes.Modo
import local.museumslideshow.ajustes.NombreSala
import local.museumslideshow.ajustes.Preferencias
import local.museumslideshow.ajustes.Tamano
import local.museumslideshow.ajustes.Tema
import local.museumslideshow.catalogo.Forma
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AcomodadorTest {
    private val med = MedidorFalso()
    private val S = Constantes.SAFE

    private fun acomodar(p: PedidoEscena) = Acomodador.acomodar(p, med)
    private inline fun <reified T : Texto> Disposicion.uno(): T = textos.filterIsInstance<T>().single()
    private fun Disposicion.cartel(): Texto.Cartel = uno()

    // ------------------------------------------------------------ una obra sola

    @Test
    fun horizontalEnGrandeLlevaElCartelAlCostadoYNoPisaLaObra() {
        val d = acomodar(pedido(Datos.monet))
        val o = d.obras.single().exterior
        val c = d.cartel()
        assertTrue(c.rect.x >= o.derecha || c.rect.derecha <= o.x)
        assertFalse(c.rect.pisa(o))
        assertEquals(NivelInfo.COMPLETA, d.nivel)
        assertFalse(d.explicacionNoEntra)
    }

    @Test
    fun muyAnchaLlevaElCartelAbajo() {
        val d = acomodar(pedido(Datos.panorama))
        val o = d.obras.single().exterior
        val c = d.cartel()
        assertTrue("cartel abajo de la obra", c.rect.y >= o.abajo)
        assertTrue(c.rect.abajo <= S.abajo + 0.5f)
    }

    @Test
    fun verticalLlevaElCartelAlCostadoCentradoEnAlturaConLaObra() {
        val d = acomodar(pedido(Datos.gioconda))
        val o = d.obras.single().exterior
        val c = d.cartel()
        assertTrue(c.rect.x >= o.derecha || c.rect.derecha <= o.x)
        assertEquals(o.centroY, c.rect.centroY, 1f)
    }

    @Test
    fun elCartelAltoQueLaObraQuedaCentradoPeroDentroDelMargen() {
        // Cartel de 900 px contra una obra baja: se centra pero no sale del margen de seguridad.
        val larga = Datos.obra("larga", 4000, 1000, Datos.ficha(explicacion = "x".repeat(2200)))
        val d = acomodar(pedido(larga, prefs = Preferencias(tamano = Tamano.NORMAL)))
        d.textos.filterIsInstance<Texto.Cartel>().forEach {
            assertTrue(it.rect.y >= S.y - 0.5f && it.rect.abajo <= S.abajo + 0.5f)
        }
    }

    @Test
    fun explicacionQueNoEntraBajaAFichaConAvisoOk() {
        val d = acomodar(pedido(Datos.obra("m", 1600, 1245, Datos.ficha(explicacion = Datos.EXPLICACION_LARGA))))
        val c = d.cartel()
        assertEquals(NivelInfo.FICHA, c.nivel)
        assertTrue("aviso de OK porque hay explicación que no entró", c.avisoOk)
        assertFalse("no la pidió: no hay que abrir la vista", d.explicacionNoEntra)
    }

    @Test
    fun conExplicacionNuncaMuestraLaFichaConAvisoYForzarlaLaMuestra() {
        val prefs = Preferencias(explicacionEnPantalla = ExplicacionEnPantalla.NUNCA)
        val sola = acomodar(pedido(Datos.monet, prefs = prefs))
        assertEquals(NivelInfo.FICHA, sola.cartel().nivel)
        assertTrue(sola.cartel().avisoOk)
        val forzada = acomodar(pedido(Datos.monet, prefs = prefs, forzarExplicacion = true))
        assertEquals(NivelInfo.COMPLETA, forzada.cartel().nivel)
        assertFalse(forzada.explicacionNoEntra)
    }

    @Test
    fun forzarLaExplicacionQueNoEntraAvisaQueNoEntra() {
        val o = Datos.obra("m", 1600, 1245, Datos.ficha(explicacion = Datos.EXPLICACION_LARGA))
        val d = acomodar(pedido(o, forzarExplicacion = true))
        assertTrue(d.explicacionNoEntra)
        assertTrue(d.nivel != NivelInfo.COMPLETA)
    }

    @Test
    fun forzarSinExplicacionNoAvisaNada() {
        val o = Datos.obra("m", 1600, 1245, Datos.ficha(explicacion = null))
        val d = acomodar(pedido(o, forzarExplicacion = true))
        assertFalse(d.explicacionNoEntra)
        assertFalse(d.cartel().avisoOk)
    }

    @Test
    fun sinFichaNoHayCartelNiHuecos() {
        val d = acomodar(pedido(Datos.sinFicha, nombreSala = null))
        assertTrue(d.textos.none { it is Texto.Cartel || it is Texto.Chip || it is Texto.Nombre })
        assertNull(d.nivel)
        val o = d.obras.single().exterior
        assertEquals(960f, o.centroX, 1f)
        assertEquals(540f, o.centroY, 1f)
    }

    @Test
    fun cartelNuncaNoDibujaCartelPeroLaObraRespetaSuTamano() {
        val con = acomodar(pedido(Datos.monet))
        val sin = acomodar(pedido(Datos.monet, prefs = Preferencias(cartelObra = CartelObra.NUNCA)))
        assertTrue(sin.textos.none { it is Texto.Cartel || it is Texto.Chip })
        assertEquals(con.obras.single().interior.ancho, sin.obras.single().interior.ancho, 0.5f)
        assertEquals(con.obras.single().interior.alto, sin.obras.single().interior.alto, 0.5f)
    }

    @Test
    fun inmersivoSinLugarUsaElCuadritoFlotanteDentroDeLaObra() {
        val d = acomodar(pedido(Datos.ancha16x9, tema = Tema.INMERSIVO, modo = Modo.OSCURO))
        val o = d.obras.single().exterior
        val chip = d.uno<Texto.Chip>()
        assertTrue(chip.rect.x >= o.x && chip.rect.derecha <= o.derecha)
        assertTrue(chip.rect.y >= o.y && chip.rect.abajo <= o.abajo)
        assertEquals(NivelInfo.MINIMA, d.nivel)
        assertTrue(d.textos.none { it is Texto.Cartel })
    }

    @Test
    fun inmersivoSiempreUsaTamanoMaximoYSinMarco() {
        val d = acomodar(pedido(Datos.gioconda, tema = Tema.INMERSIVO, prefs = Preferencias(tamano = Tamano.NORMAL)))
        val o = d.obras.single()
        assertEquals("sin", o.marco.id)
        assertEquals(972f, o.exterior.alto, 1f)
    }

    @Test
    fun redondaTieneInteriorCuadradoYMarcoDeRedondaAunqueElijanOtro() {
        val d = acomodar(pedido(Datos.tondo, prefs = Preferencias(marco = MarcoElegido.FIJO("madera"))))
        val o = d.obras.single()
        assertEquals(Forma.REDONDA, o.forma)
        assertEquals(o.interior.ancho, o.interior.alto, 0.5f)
        assertEquals("sin", o.marco.id) // dorado-redondo todavía no tiene foto
    }

    @Test
    fun ovaladaRespetaSuProporcion() {
        val d = acomodar(pedido(Datos.ovalo))
        val i = d.obras.single().interior
        assertEquals(1500f / 2000f, i.ancho / i.alto, 0.01f)
    }

    // ------------------------------------------------------------ marcos

    @Test
    fun elMarcoDelTemaEsElFinoEnGaleriaModernaConPaspartu() {
        val o = acomodar(pedido(Datos.monet, prefs = Preferencias(tamano = Tamano.GRANDE))).obras.single()
        assertEquals("fino", o.marco.id)
        assertEquals(10f, o.grosorMarco, 0.01f)
        assertEquals(34f, o.paspartu, 0.01f)
        assertEquals(o.interior.ancho + 2 * (10f + 34f), o.exterior.ancho, 0.5f)
    }

    @Test
    fun salaOscuraSinPaspartuYConFilete() {
        val o = acomodar(pedido(Datos.monet, tema = Tema.SALA_OSCURA, modo = Modo.OSCURO)).obras.single()
        assertEquals("fino", o.marco.id) // el filete no tiene foto: se resuelve a fino
        assertEquals(0f, o.paspartu, 0.01f)
    }

    @Test
    fun marcoSinNoLlevaPaspartu() {
        val o = acomodar(pedido(Datos.monet, prefs = Preferencias(marco = MarcoElegido.FIJO("sin")))).obras.single()
        assertEquals(0f, o.paspartu, 0.01f)
        assertEquals(0f, o.grosorMarco, 0.01f)
    }

    @Test
    fun marcoAlAzarEsEstableParaCadaObraYVariaEntreObras() {
        val tema = Datos.tema(Tema.GALERIA_MODERNA)
        val prefs = Preferencias(marco = MarcoElegido.AL_AZAR)
        val obras = (1..40).map { Datos.obra("obra-$it", 100, 100) }
        val a = obras.map { Acomodador.idMarco(it, tema, prefs) }
        val b = obras.map { Acomodador.idMarco(it, tema, prefs) }
        assertEquals(a, b)
        assertTrue(a.toSet().size >= 3)
        assertTrue(a.all { it in local.museumslideshow.temas.CatalogoMarcos.rectangulares })
    }

    @Test
    fun marcoDeLaGaleriaUsaElDeLaFichaSiExisteYSinoElDelTema() {
        val tema = Datos.tema(Tema.SALA_OSCURA)
        val prefs = Preferencias(marco = MarcoElegido.DE_LA_GALERIA)
        assertEquals("madera", Acomodador.idMarco(Datos.obra("a", 10, 10, Datos.ficha(marco = "madera")), tema, prefs))
        assertEquals("filete", Acomodador.idMarco(Datos.obra("a", 10, 10, Datos.ficha(marco = "inventado")), tema, prefs))
        assertEquals("filete", Acomodador.idMarco(Datos.obra("a", 10, 10, Datos.ficha(marco = null)), tema, prefs))
        assertEquals("filete", Acomodador.idMarco(Datos.obra("a", 10, 10, null), tema, prefs))
    }

    // ------------------------------------------------------------ pantalla completa

    private fun assertProporcion(r: Rect, ancho: Int, alto: Int) {
        // la imagen llena la pantalla en un eje y en el otro difiere menos de 1 px
        assertEquals(ancho.toFloat() / alto, r.ancho / r.alto, ancho.toFloat() / alto * 0.01f)
    }

    @Test
    fun pantallaCompletaSinMarcoEsLaObraLoMasGrandePosibleSinTextos() {
        val d = acomodar(pedido(Datos.gioconda, pantallaCompleta = true))
        val o = d.obras.single()
        assertTrue(d.textos.isEmpty())
        assertEquals(o.interior, o.exterior)
        assertEquals(1080f, o.interior.alto, 1f)
        assertProporcion(o.interior, 7479, 11146)
        assertEquals(960f, o.exterior.centroX, 1f)
    }

    @Test
    fun pantallaCompletaConMarcoUsaGrosoresDeMaximoYConservaLaProporcion() {
        val prefs = Preferencias(pantallaCompletaConMarco = true)
        val o = acomodar(pedido(Datos.monet, prefs = prefs, pantallaCompleta = true)).obras.single()
        assertEquals(5f, o.grosorMarco, 0.01f)
        assertEquals(12f, o.paspartu, 0.01f)
        assertTrue(o.exterior.ancho <= 1920.5f && o.exterior.alto <= 1080.5f)
        assertTrue(abs(o.exterior.ancho - 1920f) < 1.5f || abs(o.exterior.alto - 1080f) < 1.5f)
        assertProporcion(o.interior, 1600, 1245)
    }

    @Test
    fun pantallaCompletaDeUnaObraAnchaLlenaElAncho() {
        val o = acomodar(pedido(Datos.panorama, pantallaCompleta = true)).obras.single()
        assertEquals(1920f, o.interior.ancho, 1f)
        assertProporcion(o.interior, 6000, 2000)
    }

    @Test
    fun tamanoPantallaCompletaYOkVuelveAGrandeConCartel() {
        val prefs = Preferencias(tamano = Tamano.PANTALLA_COMPLETA)
        val completa = acomodar(pedido(Datos.monet, prefs = prefs))
        assertTrue(completa.textos.isEmpty())
        val vuelve = acomodar(pedido(Datos.monet, prefs = prefs, pantallaCompleta = true))
        assertNotNull(vuelve.textos.filterIsInstance<Texto.Cartel>().firstOrNull())
        assertEquals(10f, vuelve.obras.single().grosorMarco, 0.01f) // Grande
    }

    @Test
    fun enUnaParedPantallaCompletaMuestraSolaLaObraElegida() {
        val obras = listOf(Datos.gioconda, Datos.armino, Datos.arnolfini)
        val d = acomodar(pedido(obras, elegida = 1, pantallaCompleta = true))
        assertEquals(1, d.obras.size)
        assertEquals("armino", d.obras.single().obra.driveId)
        assertTrue(d.textos.isEmpty())
    }

    // ------------------------------------------------------------ pared

    @Test
    fun paredDeDosYTresQuedaDentroDeLaCajaConUnNombreCentradoBajoCadaObra() {
        for (obras in listOf(listOf(Datos.caminante, Datos.arnolfini), listOf(Datos.gioconda, Datos.armino, Datos.arnolfini))) {
            for (t in listOf(Tamano.NORMAL, Tamano.GRANDE, Tamano.MAXIMO)) {
                val d = acomodar(pedido(obras, prefs = Preferencias(tamano = t)))
                assertEquals(obras.size, d.obras.size)
                val nombres = d.textos.filterIsInstance<Texto.Nombre>()
                assertEquals(obras.size, nombres.size)
                val (cw, ch) = Constantes.cajaPared(t)
                val minX = d.obras.minOf { it.exterior.x }
                val maxX = d.obras.maxOf { it.exterior.derecha }
                val minY = d.obras.minOf { it.exterior.y }
                val maxY = (d.obras.map { it.exterior.abajo } + nombres.map { it.rect.abajo }).max()
                assertTrue("ancho $t ${maxX - minX} <= $cw", maxX - minX <= cw + 1f)
                assertTrue("alto $t ${maxY - minY} <= $ch", maxY - minY <= ch + 1f)
                d.obras.forEach { assertTrue(it.exterior.x >= S.x - 0.5f && it.exterior.derecha <= S.derecha + 0.5f) }
                nombres.forEach { n ->
                    val o = d.obras[n.obraIndice].exterior
                    assertEquals(o.centroX, n.rect.centroX, 1.5f)
                    assertTrue(n.rect.y >= o.abajo - 0.5f)
                }
            }
        }
    }

    @Test
    fun paredConObraSinFichaNoDejaHuecoDeNombreSiNingunaTiene() {
        val d = acomodar(pedido(listOf(Datos.obra("a", 100, 150, null), Datos.obra("b", 100, 150, null))))
        assertTrue(d.textos.none { it is Texto.Nombre })
        assertEquals(2, d.obras.size)
    }

    @Test
    fun paredConCartelNuncaNoLlevaNombres() {
        val d = acomodar(pedido(listOf(Datos.gioconda, Datos.armino), prefs = Preferencias(cartelObra = CartelObra.NUNCA)))
        assertTrue(d.textos.none { it is Texto.Nombre })
    }

    @Test
    fun paredDeObrasRedondasYRectangularesRespetaProporciones() {
        val d = acomodar(pedido(listOf(Datos.tondo, Datos.arnolfini, Datos.ovalo)))
        val (t, a, o) = d.obras
        assertEquals(t.interior.ancho, t.interior.alto, 0.5f)
        assertProporcion(a.interior, 4386, 6000)
        assertProporcion(o.interior, 1500, 2000)
    }

    @Test
    fun paredPideLaExplicacionNoEntraSiLaObraElegidaLaTiene() {
        val d = acomodar(pedido(listOf(Datos.gioconda, Datos.armino), forzarExplicacion = true))
        assertTrue(d.explicacionNoEntra)
    }

    // ------------------------------------------------------------ sala y contador

    @Test
    fun laSalaVaArribaALaIzquierdaSiNoPisaNada() {
        val d = acomodar(pedido(Datos.gioconda, nombreSala = "Renacimiento · 3 / 24"))
        val s = d.uno<Texto.Sala>()
        assertEquals(110f, s.rect.x, 0.01f)
        assertEquals(76f, s.rect.y, 0.01f)
        assertTrue(d.textos.none { it is Texto.Contador })
    }

    @Test
    fun siLaSalaPisaLaObraQuedaUnContadorEnUnRinconLibre() {
        val d = acomodar(pedido(Datos.tresPorDos, prefs = Preferencias(tamano = Tamano.MAXIMO)))
        assertTrue("la obra llega hasta la sala", d.obras.single().exterior.pisa(Rect(100f, 66f, 300f, 40f)))
        assertTrue(d.textos.none { it is Texto.Sala })
        val c = d.uno<Texto.Contador>()
        d.obras.forEach { assertFalse(c.rect.pisa(it.exterior)) }
        d.textos.filter { it !is Texto.Contador }.forEach { assertFalse(c.rect.pisa(it.rect)) }
        assertTrue(c.rect.x >= S.x && c.rect.derecha <= S.derecha && c.rect.y >= S.y && c.rect.abajo <= S.abajo)
    }

    @Test
    fun soloContadorMuestraSoloElContadorYNuncaNoMuestraNada() {
        val soloC = acomodar(pedido(Datos.gioconda, prefs = Preferencias(nombreSala = NombreSala.SOLO_CONTADOR)))
        assertTrue(soloC.textos.none { it is Texto.Sala })
        soloC.uno<Texto.Contador>()
        val nunca = acomodar(pedido(Datos.gioconda, prefs = Preferencias(nombreSala = NombreSala.NUNCA)))
        assertTrue(nunca.textos.none { it is Texto.Sala || it is Texto.Contador })
    }

    @Test
    fun sinNombreDeSalaQuedaElContador() {
        val d = acomodar(pedido(Datos.gioconda, nombreSala = null))
        d.uno<Texto.Contador>()
    }

    @Test
    fun unosSegundosDaElMismoLugarQueSiempre() {
        val a = acomodar(pedido(Datos.gioconda, prefs = Preferencias(nombreSala = NombreSala.UNOS_SEGUNDOS)))
        val b = acomodar(pedido(Datos.gioconda))
        assertEquals(b.textos, a.textos)
    }

    // ------------------------------------------------------------ libro de arte

    private fun libro(o: local.museumslideshow.catalogo.Obra, prefs: Preferencias = Preferencias(), forzar: Boolean = false) =
        acomodar(pedido(o, tema = Tema.LIBRO_DE_ARTE, prefs = prefs, forzarExplicacion = forzar))

    private fun obraConExplicacion(chars: Int) =
        Datos.obra("libro", 7479, 11146, Datos.ficha(explicacion = "x".repeat(chars)))

    @Test
    fun libroConExplicacionCortaLaMuestraAlTamanoMaximo() {
        val d = libro(obraConExplicacion(300))
        assertEquals(26f, d.libro!!.tamanoExplicacionPx!!, 0.01f)
        assertFalse(d.libro!!.avisoOk)
        assertEquals(NivelInfo.COMPLETA, d.nivel)
        assertTrue(d.textos.isEmpty())
    }

    @Test
    fun libroBajaLaLetraDe26A17HastaQueEntra() {
        val d = libro(obraConExplicacion(800))
        val s = d.libro!!.tamanoExplicacionPx!!
        assertTrue("letra entre 17 y 25 (fue $s)", s in 17f..25f)
        val m = med.medirPaginaLibro(d.obras.single().obra.ficha!!, null, true, s, false, Constantes.LIBRO_COLUMNA.ancho, Modo.CLARO, false)
        assertTrue(m.alto <= Constantes.LIBRO_COLUMNA.alto)
        val mas = med.medirPaginaLibro(d.obras.single().obra.ficha!!, null, true, s + 1, false, Constantes.LIBRO_COLUMNA.ancho, Modo.CLARO, false)
        assertTrue("a un punto más no entraba", mas.alto > Constantes.LIBRO_COLUMNA.alto)
    }

    @Test
    fun libroQueNoEntraNiAl17NoMuestraLaExplicacionYAvisaConOk() {
        val d = libro(obraConExplicacion(6000))
        assertNull(d.libro!!.tamanoExplicacionPx)
        assertTrue(d.libro!!.avisoOk)
        assertEquals(NivelInfo.FICHA, d.nivel)
        assertFalse(d.explicacionNoEntra)
        assertTrue(libro(obraConExplicacion(6000), forzar = true).explicacionNoEntra)
    }

    @Test
    fun libroConExplicacionNuncaSoloLaFichaHastaQueLaPiden() {
        val prefs = Preferencias(explicacionEnPantalla = ExplicacionEnPantalla.NUNCA)
        val d = libro(obraConExplicacion(300), prefs)
        assertNull(d.libro!!.tamanoExplicacionPx)
        assertTrue(d.libro!!.avisoOk)
        assertEquals(26f, libro(obraConExplicacion(300), prefs, forzar = true).libro!!.tamanoExplicacionPx!!, 0.01f)
    }

    @Test
    fun libroPoneLaObraEnSuPaginaSinMarcoYElNombreDeLaSalaEnElCorrido() {
        val d = libro(Datos.gioconda)
        val o = d.obras.single()
        val area = d.libro!!.obraArea
        assertEquals("sin", o.marco.id)
        assertEquals(o.interior, o.exterior)
        assertTrue(o.interior.x >= area.x - 0.5f && o.interior.derecha <= area.derecha + 0.5f)
        assertTrue(o.interior.y >= area.y - 0.5f && o.interior.abajo <= area.abajo + 0.5f)
        assertEquals("Impresionismo · 3 / 24", d.libro!!.corrido)
        assertEquals("3 / 24", libro(Datos.gioconda, Preferencias(nombreSala = NombreSala.SOLO_CONTADOR)).libro!!.corrido)
        assertNull(libro(Datos.gioconda, Preferencias(nombreSala = NombreSala.NUNCA)).libro!!.corrido)
    }

    @Test
    fun libroEnPantallaCompletaMuestraSoloLaObra() {
        val d = acomodar(pedido(Datos.gioconda, tema = Tema.LIBRO_DE_ARTE, pantallaCompleta = true))
        assertNull(d.libro)
        assertTrue(d.textos.isEmpty())
    }

    @Test
    fun libroConTamanoMaximoAgrandaLaObraDentroDeSuMitad() {
        val normal = libro(Datos.gioconda, Preferencias(tamano = Tamano.NORMAL)).obras.single().interior.alto
        val grande = libro(Datos.gioconda, Preferencias(tamano = Tamano.GRANDE)).obras.single().interior.alto
        val maximo = libro(Datos.gioconda, Preferencias(tamano = Tamano.MAXIMO)).obras.single().interior.alto
        assertTrue(normal < grande && grande < maximo)
    }

    @Test
    fun libroConVariasObrasMuestraSoloLaElegida() {
        val d = acomodar(pedido(listOf(Datos.gioconda, Datos.armino), tema = Tema.LIBRO_DE_ARTE, elegida = 1))
        assertEquals("armino", d.obras.single().obra.driveId)
    }

    @Test
    fun elAcomodadorLePasaAlMedidorSiSeMuestraElCredito() {
        val sin = MedidorFalso()
        Acomodador.acomodar(pedido(Datos.monet), sin)
        assertTrue(sin.pedidosCredito.isNotEmpty() && sin.pedidosCredito.none { it })
        val con = MedidorFalso()
        Acomodador.acomodar(pedido(Datos.monet, prefs = Preferencias(mostrarCredito = true)), con)
        assertTrue(con.pedidosCredito.isNotEmpty() && con.pedidosCredito.all { it })
        val libroSin = MedidorFalso()
        Acomodador.acomodar(pedido(obraConExplicacion(300), tema = Tema.LIBRO_DE_ARTE), libroSin)
        assertTrue(libroSin.pedidosCredito.isNotEmpty() && libroSin.pedidosCredito.none { it })
    }
}
