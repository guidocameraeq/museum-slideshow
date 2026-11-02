package local.museumslideshow.pantallas

import local.museumslideshow.ajustes.CodecPreferencias
import local.museumslideshow.ajustes.EstadoGrupo
import local.museumslideshow.ajustes.GaleriasNuevas
import local.museumslideshow.ajustes.Preferencias
import local.museumslideshow.ajustes.PreferenciasDesdeTexto
import local.museumslideshow.ajustes.VistaGalerias
import local.museumslideshow.ajustes.GrupoAjustes
import local.museumslideshow.ajustes.IdAjuste
import local.museumslideshow.ajustes.estaTildada
import local.museumslideshow.ajustes.paraEscena
import local.museumslideshow.catalogo.Arbol
import local.museumslideshow.catalogo.CatalogoCargado
import local.museumslideshow.catalogo.FilaLista
import local.museumslideshow.catalogo.Galeria
import local.museumslideshow.catalogo.Grupo
import local.museumslideshow.catalogo.Obra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun galeria(id: String, titulo: String, padre: String?, n: Int = 2) =
    Galeria(id, titulo, null, (1..n).map { Obra("$id-$it", "$id$it.jpg", 100, 100, null) }, padre)

/**
 * Raíz (7 nodos): Barroco, Caravaggio, Dalí, Impresionismo (grupo: Monet, Pissarro, Renoir), Mitología, Renacimiento (grupo:
 * Botticelli, Leonardo (grupo: Bocetos), Rafael), Zurbarán.
 */
private val ARBOL = Arbol(
    grupos = listOf(
        Grupo("IMP", "Impresionismo"), Grupo("REN", "Renacimiento"), Grupo("LEO", "Leonardo", "REN"),
    ),
    galerias = listOf(
        galeria("BAR", "Barroco", null, 5), galeria("CAR", "Caravaggio", null, 3), galeria("MIT", "Mitología", null, 4),
        galeria("ZUR", "Zurbarán", null, 1), galeria("DAL", "Dalí", null, 2),
        galeria("MON", "Monet", "IMP", 6), galeria("PIS", "Pissarro", "IMP", 2), galeria("RNR", "Renoir", "IMP", 3),
        galeria("RAF", "Rafael", "REN", 4), galeria("BOT", "Botticelli", "REN", 2), galeria("BOC", "Bocetos", "LEO", 3),
    ),
)

private fun e(zona: ZonaGalerias, i: Int = 0, pila: List<String> = emptyList(), abiertos: Set<String> = emptySet(), primera: Int = 0) =
    EstadoGalerias(pila, FocoGalerias(zona, i), abiertos, primera)

private fun tarj(est: EstadoGalerias, t: Tecla) = NavegacionGalerias.paso(ARBOL, est, t, VistaGalerias.TARJETAS, emptyList())

private fun lista(est: EstadoGalerias, t: Tecla, p: Preferencias = Preferencias()): PasoGalerias {
    val filas = ARBOL.filasLista(est.abiertos, p)
    return NavegacionGalerias.paso(ARBOL, est, t, VistaGalerias.LISTA, filas)
}

class ArbolParaGaleriasTest {
    @Test
    fun losHijosDeLaRaizSonLosGruposYLasSueltasPorNombre() {
        assertEquals(
            listOf("Barroco", "Caravaggio", "Dalí", "Impresionismo", "Mitología", "Renacimiento", "Zurbarán"),
            ARBOL.hijos(null).map { it.titulo },
        )
        assertEquals(listOf(false, false, false, true, false, true, false), ARBOL.hijos(null).map { it.esGrupo })
    }

    @Test
    fun losHijosDeUnGrupoSonSusGaleriasYSubgrupos() {
        assertEquals(listOf("Botticelli", "Leonardo", "Rafael"), ARBOL.hijos("REN").map { it.titulo })
        assertEquals(listOf("Bocetos"), ARBOL.hijos("LEO").map { it.titulo })
    }

    @Test
    fun unaCarpetaQueEsGaleriaYGrupoApareceUnaSolaVezComoGrupo() {
        val arbol = Arbol(
            grupos = listOf(Grupo("MIX", "Mixta")),
            galerias = listOf(galeria("MIX", "Mixta", null, 2), galeria("HIJA", "Hija", "MIX", 1)),
        )
        val raiz = arbol.hijos(null)
        assertEquals(1, raiz.size)
        assertTrue(raiz[0].esGrupo)
        assertEquals(3, arbol.cantidadObras("MIX"))
    }

    @Test
    fun alternarTildaSiFaltaAlgoYDestildaSiEstabaTodo() {
        var p = Preferencias()
        p = ARBOL.alternar(p, "IMP") // estaba todo tildado (por defecto): destilda todo
        assertEquals(EstadoGrupo.NINGUNA, ARBOL.estadoGrupo("IMP", p))
        p = ARBOL.alternar(p, "IMP")
        assertEquals(EstadoGrupo.TODAS, ARBOL.estadoGrupo("IMP", p))
        val aMedias = ARBOL.tildar(p, "MON", false)
        assertEquals(EstadoGrupo.ALGUNAS, ARBOL.estadoGrupo("IMP", aMedias))
        assertEquals(EstadoGrupo.TODAS, ARBOL.estadoGrupo("IMP", ARBOL.alternar(aMedias, "IMP")))
        // una galería suelta
        assertFalse(ARBOL.alternar(Preferencias(), "BAR").estaTildada("BAR"))
    }

    @Test
    fun elResumenCuentaGaleriasYObrasTildadas() {
        val p = ARBOL.tildar(Preferencias(), "PIS", false)
        val todo = ARBOL.resumen(p)
        assertEquals(11, todo.total)
        assertEquals(10, todo.tildadas)
        assertEquals(33, todo.obras) // 35 en total menos las 2 de Pissarro
        val imp = ARBOL.resumen(p, "IMP")
        assertEquals(3, imp.total)
        assertEquals(2, imp.tildadas)
        assertEquals(9, imp.obras)
    }

    @Test
    fun elMosaicoTomaLasPrimerasCuatroGaleriasConObrasDeAdentroIncluidasLasAnidadas() {
        // Renacimiento: orden del catálogo (Rafael, Botticelli, Bocetos)
        val m = ARBOL.mosaico("REN")
        assertEquals(3, m.size)
        assertEquals(setOf("RAF", "BOT", "BOC"), m.map { it.driveId }.toSet())
        val grande = Arbol(
            grupos = listOf(Grupo("G", "G")),
            galerias = (1..6).map { galeria("G$it", "g$it", "G") } + galeria("VACIA", "vacia", "G", 0),
        )
        assertEquals(listOf("G1", "G2", "G3", "G4"), grande.mosaico("G").map { it.driveId })
        // una galería sin obras no ocupa casilla
        val conVacia = Arbol(listOf(Grupo("G", "G")), listOf(galeria("V", "v", "G", 0), galeria("A", "a", "G")))
        assertEquals(listOf("A"), conVacia.mosaico("G").map { it.driveId })
    }

    @Test
    fun elCaminoVaDeLaRaizAlNodo() {
        assertEquals(listOf("REN", "LEO", "BOC"), ARBOL.camino("BOC"))
        assertEquals(emptyList<String>(), ARBOL.camino("NO-EXISTE"))
    }

    @Test
    fun laListaMuestraLosGruposAbiertosConSuNivel() {
        val cerrada = ARBOL.filasLista(emptySet(), Preferencias())
        assertEquals(7, cerrada.size)
        val abierta = ARBOL.filasLista(setOf("REN", "LEO"), Preferencias())
        val ren = abierta.indexOfFirst { it.id == "REN" }
        assertEquals(listOf("Renacimiento", "Botticelli", "Leonardo", "Bocetos", "Rafael"), abierta.subList(ren, ren + 5).map { it.titulo })
        assertEquals(listOf(0, 1, 1, 2, 1), abierta.subList(ren, ren + 5).map { it.nivel })
        assertEquals(11, abierta.size)
    }

    @Test
    fun cadaFilaDeListaTraeEstadoYTotales() {
        val p = ARBOL.tildar(Preferencias(), "MON", false)
        val imp = ARBOL.filasLista(emptySet(), p).first { it.id == "IMP" }
        assertEquals(EstadoGrupo.ALGUNAS, imp.estado)
        assertEquals(3, imp.galerias)
        assertEquals(11, imp.obras)
        assertTrue(imp.esGrupo)
    }
}

class NavegacionTarjetasTest {
    @Test
    fun laGrillaTieneCuatroColumnasYLasFilasDesparejasNoLlegan() {
        // 7 tarjetas en la raíz: filas de 4 y 3
        val p1 = tarj(e(ZonaGalerias.ITEMS, 0), Tecla.DERECHA)
        assertEquals(FocoGalerias(ZonaGalerias.ITEMS, 1), p1.estado.foco)
        assertEquals(3, tarj(e(ZonaGalerias.ITEMS, 2), Tecla.DERECHA).estado.foco.indice)
        // al borde derecho no pasa a la fila de abajo
        assertEquals(3, tarj(e(ZonaGalerias.ITEMS, 3), Tecla.DERECHA).estado.foco.indice)
        // al borde izquierdo no sube de fila
        assertEquals(4, tarj(e(ZonaGalerias.ITEMS, 4), Tecla.IZQUIERDA).estado.foco.indice)
        // la última fila tiene 3: la derecha de la tarjeta 6 no existe
        assertEquals(6, tarj(e(ZonaGalerias.ITEMS, 6), Tecla.DERECHA).estado.foco.indice)
    }

    @Test
    fun abajoYArribaSaltanDeAcuatro() {
        assertEquals(4, tarj(e(ZonaGalerias.ITEMS, 0), Tecla.ABAJO).estado.foco.indice)
        assertEquals(6, tarj(e(ZonaGalerias.ITEMS, 2), Tecla.ABAJO).estado.foco.indice)
        assertEquals(1, tarj(e(ZonaGalerias.ITEMS, 5), Tecla.ARRIBA).estado.foco.indice)
    }

    @Test
    fun abajoDesdeUnaColumnaQueNoTieneTarjetaDeAbajoCaeEnLaUltima() {
        // la tarjeta 3 es de la columna 4; abajo solo hay 3 tarjetas (4, 5, 6): cae en la 6
        assertEquals(6, tarj(e(ZonaGalerias.ITEMS, 3), Tecla.ABAJO).estado.foco.indice)
    }

    @Test
    fun abajoEnLaUltimaFilaVaAGaleriasNuevasYArribaVuelveALaUltimaTarjeta() {
        val p = tarj(e(ZonaGalerias.ITEMS, 5), Tecla.ABAJO)
        assertEquals(FocoGalerias(ZonaGalerias.NUEVAS, 0), p.estado.foco)
        assertEquals(FocoGalerias(ZonaGalerias.ITEMS, 6), tarj(p.estado, Tecla.ARRIBA).estado.foco)
    }

    @Test
    fun elSelectorEstaArribaDeTodoYAbajoBajaALaPrimeraTarjeta() {
        assertEquals(FocoGalerias(ZonaGalerias.SELECTOR, 0), tarj(e(ZonaGalerias.ITEMS, 2), Tecla.ARRIBA).estado.foco)
        assertEquals(FocoGalerias(ZonaGalerias.ITEMS, 0), tarj(e(ZonaGalerias.SELECTOR, 1), Tecla.ABAJO).estado.foco)
        assertEquals(FocoGalerias(ZonaGalerias.SELECTOR, 1), tarj(e(ZonaGalerias.SELECTOR, 0), Tecla.DERECHA).estado.foco)
        assertEquals(FocoGalerias(ZonaGalerias.SELECTOR, 0), tarj(e(ZonaGalerias.SELECTOR, 1), Tecla.IZQUIERDA).estado.foco)
    }

    @Test
    fun okEnElSelectorPideCambiarLaVista() {
        assertEquals(EfectoGalerias.CambiarVista(VistaGalerias.LISTA), tarj(e(ZonaGalerias.SELECTOR, 1), Tecla.OK).efecto)
        assertEquals(EfectoGalerias.CambiarVista(VistaGalerias.TARJETAS), tarj(e(ZonaGalerias.SELECTOR, 0), Tecla.OK).efecto)
    }

    @Test
    fun galeriasNuevasEstaAlPieConIzquierdaDerechaYOk() {
        val nuevas = e(ZonaGalerias.NUEVAS)
        assertEquals(EfectoGalerias.CambiarNuevas(-1, false), tarj(nuevas, Tecla.IZQUIERDA).efecto)
        assertEquals(EfectoGalerias.CambiarNuevas(1, false), tarj(nuevas, Tecla.DERECHA).efecto)
        assertEquals(EfectoGalerias.CambiarNuevas(1, true), tarj(nuevas, Tecla.OK).efecto)
        assertEquals(nuevas, tarj(nuevas, Tecla.ABAJO).estado)
    }

    @Test
    fun okEnUnaGaleriaLaTildaYEnUnGrupoEntra() {
        // índice 0 = Barroco (galería), 3 = Impresionismo (grupo)
        assertEquals(EfectoGalerias.Alternar("BAR"), tarj(e(ZonaGalerias.ITEMS, 0), Tecla.OK).efecto)
        val entra = tarj(e(ZonaGalerias.ITEMS, 3), Tecla.OK)
        assertNull(entra.efecto)
        assertEquals(listOf("IMP"), entra.estado.pila)
        assertEquals(FocoGalerias(ZonaGalerias.ITEMS, 0), entra.estado.foco)
    }

    @Test
    fun entrarYSalirDeGruposAnidadosUsaLaPilaDeMigas() {
        var s = e(ZonaGalerias.ITEMS, 5) // Renacimiento
        s = tarj(s, Tecla.OK).estado
        assertEquals(listOf("REN"), s.pila)
        // adentro: Botticelli, Leonardo, Rafael -> Leonardo es el índice 1
        s = tarj(s, Tecla.DERECHA).estado
        s = tarj(s, Tecla.OK).estado
        assertEquals(listOf("REN", "LEO"), s.pila)
        // Atrás vuelve a Renacimiento con el foco en Leonardo
        val atras1 = tarj(s, Tecla.ATRAS)
        assertNull(atras1.efecto)
        assertEquals(listOf("REN"), atras1.estado.pila)
        assertEquals(FocoGalerias(ZonaGalerias.ITEMS, 1), atras1.estado.foco)
        // otra vez: a la raíz con el foco en Renacimiento
        val atras2 = tarj(atras1.estado, Tecla.ATRAS)
        assertEquals(emptyList<String>(), atras2.estado.pila)
        assertEquals(FocoGalerias(ZonaGalerias.ITEMS, 5), atras2.estado.foco)
        // y en el primer nivel Atrás sale de la pantalla
        assertEquals(EfectoGalerias.Salir, tarj(atras2.estado, Tecla.ATRAS).efecto)
    }

    @Test
    fun adentroDeUnGrupoArribaDeLaPrimeraFilaEstaTildarTodasYNoHaySelector() {
        val dentro = e(ZonaGalerias.ITEMS, 1, pila = listOf("IMP"))
        val arriba = tarj(dentro, Tecla.ARRIBA).estado
        assertEquals(FocoGalerias(ZonaGalerias.TODAS, 0), arriba.foco)
        assertEquals(arriba, tarj(arriba, Tecla.ARRIBA).estado.copy(primeraFila = arriba.primeraFila))
        assertEquals(EfectoGalerias.Alternar("IMP"), tarj(arriba, Tecla.OK).efecto)
        assertEquals(FocoGalerias(ZonaGalerias.ITEMS, 0), tarj(arriba, Tecla.ABAJO).estado.foco)
    }

    @Test
    fun elScrollSigueAlFocoPorFilas() {
        // 12 tarjetas = 3 filas, se ven 2
        assertEquals(0, NavegacionGalerias.primeraFilaVisible(0, 1, 2, 3))
        assertEquals(1, NavegacionGalerias.primeraFilaVisible(0, 2, 2, 3))
        assertEquals(0, NavegacionGalerias.primeraFilaVisible(1, 0, 2, 3))
        assertEquals(1, NavegacionGalerias.primeraFilaVisible(1, 2, 2, 3))
        // nunca se pasa del final ni baja de cero
        assertEquals(0, NavegacionGalerias.primeraFilaVisible(5, 0, 2, 1))
        assertEquals(1, NavegacionGalerias.primeraFilaVisible(7, 2, 2, 3))
    }

    @Test
    fun bajarPorUnaGrillaLargaMueveElScrollYElFocoSiempreEstaVisible() {
        val muchas = Arbol(emptyList(), (1..14).map { galeria("G$it", "g%02d".format(it), null) })
        var s = EstadoGalerias()
        repeat(3) { s = NavegacionGalerias.paso(muchas, s, Tecla.ABAJO, VistaGalerias.TARJETAS, emptyList()).estado }
        assertEquals(12, s.foco.indice)
        val fila = s.foco.indice / 4
        assertTrue(fila >= s.primeraFila && fila < s.primeraFila + NavegacionGalerias.FILAS_TARJETAS)
        assertEquals(2, s.primeraFila)
        repeat(3) { s = NavegacionGalerias.paso(muchas, s, Tecla.ARRIBA, VistaGalerias.TARJETAS, emptyList()).estado }
        assertEquals(0, s.primeraFila)
    }

    @Test
    fun unaGaleriaVaciaDejaElFocoEnGaleriasNuevas() {
        val vacio = Arbol(emptyList(), emptyList())
        val s = NavegacionGalerias.paso(vacio, EstadoGalerias(), Tecla.ABAJO, VistaGalerias.TARJETAS, emptyList()).estado
        assertEquals(ZonaGalerias.NUEVAS, s.foco.zona)
    }

    @Test
    fun elFocoSeCorrigeSiLaGrillaSeAchico() {
        val n = NavegacionGalerias.normalizar(ARBOL, e(ZonaGalerias.ITEMS, 99), VistaGalerias.TARJETAS, emptyList())
        assertEquals(6, n.foco.indice)
        val sinGrupo = NavegacionGalerias.normalizar(ARBOL, e(ZonaGalerias.ITEMS, 0, pila = listOf("NO-EXISTE")), VistaGalerias.TARJETAS, emptyList())
        assertEquals(emptyList<String>(), sinGrupo.pila)
    }
}

class NavegacionListaGaleriasTest {
    private val sinAbrir = e(ZonaGalerias.ITEMS, 0)

    @Test
    fun derechaAbreElGrupoYIzquierdaLoCierra() {
        // fila 3 = Impresionismo
        val abre = lista(e(ZonaGalerias.ITEMS, 3), Tecla.DERECHA)
        assertEquals(setOf("IMP"), abre.estado.abiertos)
        val cierra = lista(abre.estado, Tecla.IZQUIERDA)
        assertEquals(emptySet<String>(), cierra.estado.abiertos)
        assertEquals(3, cierra.estado.foco.indice)
    }

    @Test
    fun derechaEnUnaGaleriaNoHaceNada() {
        assertEquals(sinAbrir, lista(sinAbrir, Tecla.DERECHA).estado)
    }

    @Test
    fun izquierdaEnUnHijoSaltaAlPadre() {
        val abierto = e(ZonaGalerias.ITEMS, 5, abiertos = setOf("IMP")) // Impresionismo(3), Monet(4), Pissarro(5)...
        val filas = ARBOL.filasLista(abierto.abiertos, Preferencias())
        assertEquals("Pissarro", filas[5].titulo)
        val p = lista(abierto, Tecla.IZQUIERDA)
        assertEquals(3, p.estado.foco.indice)
        assertEquals(setOf("IMP"), p.estado.abiertos)
    }

    @Test
    fun izquierdaEnUnaFilaDeLaRaizNoHaceNada() {
        assertEquals(sinAbrir, lista(sinAbrir, Tecla.IZQUIERDA).estado)
    }

    @Test
    fun okTildaLaFilaYEnUnGrupoPideTildarTodo() {
        assertEquals(EfectoGalerias.Alternar("BAR"), lista(sinAbrir, Tecla.OK).efecto)
        assertEquals(EfectoGalerias.Alternar("IMP"), lista(e(ZonaGalerias.ITEMS, 3), Tecla.OK).efecto)
    }

    @Test
    fun arribaDeLaPrimeraFilaVaAlSelectorYAbajoDeLaUltimaALasNuevas() {
        assertEquals(FocoGalerias(ZonaGalerias.SELECTOR, 1), lista(sinAbrir, Tecla.ARRIBA).estado.foco)
        assertEquals(FocoGalerias(ZonaGalerias.NUEVAS, 0), lista(e(ZonaGalerias.ITEMS, 6), Tecla.ABAJO).estado.foco)
        assertEquals(FocoGalerias(ZonaGalerias.ITEMS, 6), lista(e(ZonaGalerias.NUEVAS), Tecla.ARRIBA).estado.foco)
        assertEquals(FocoGalerias(ZonaGalerias.ITEMS, 0), lista(e(ZonaGalerias.SELECTOR, 1), Tecla.ABAJO).estado.foco)
    }

    @Test
    fun atrasEnLaListaSaleDeLaPantalla() {
        assertEquals(EfectoGalerias.Salir, lista(e(ZonaGalerias.ITEMS, 3, abiertos = setOf("IMP")), Tecla.ATRAS).efecto)
    }

    @Test
    fun cerrarUnGrupoConElFocoMasAbajoNoDejaElFocoFueraDeRango() {
        val abierto = e(ZonaGalerias.ITEMS, 6, abiertos = setOf("IMP", "REN"))
        val n = NavegacionGalerias.normalizar(
            ARBOL, abierto.copy(abiertos = emptySet()), VistaGalerias.LISTA, ARBOL.filasLista(emptySet(), Preferencias()),
        )
        assertTrue(n.foco.indice <= 6)
    }

    @Test
    fun laListaTieneScrollPorFilas() {
        val filas: List<FilaLista> = (0 until 30).map {
            FilaLista("F$it", "f$it", 0, false, false, 1, 0, null, EstadoGrupo.TODAS)
        }
        assertEquals(0, NavegacionGalerias.primeraFilaVisible(0, 10, NavegacionGalerias.FILAS_LISTA, filas.size))
        assertEquals(1, NavegacionGalerias.primeraFilaVisible(0, 11, NavegacionGalerias.FILAS_LISTA, filas.size))
        assertEquals(19, NavegacionGalerias.primeraFilaVisible(0, 29, NavegacionGalerias.FILAS_LISTA, filas.size))
    }
}

class VistaGaleriasAjusteTest {
    @Test
    fun porDefectoEsTarjetas() {
        assertEquals(VistaGalerias.TARJETAS, Preferencias().vistaGalerias)
        assertEquals(VistaGalerias.TARJETAS, CodecPreferencias.decodificar(emptyMap()).vistaGalerias)
    }

    @Test
    fun seGuardaPorNombreYVuelve() {
        val p = Preferencias(vistaGalerias = VistaGalerias.LISTA)
        val m = CodecPreferencias.codificar(p)
        assertEquals("LISTA", m["vista_galerias"])
        assertEquals(p, CodecPreferencias.decodificar(m))
    }

    @Test
    fun unValorRaroVuelveAlDefecto() {
        assertEquals(VistaGalerias.TARJETAS, CodecPreferencias.decodificar(mapOf("vista_galerias" to "MOSAICO")).vistaGalerias)
    }

    @Test
    fun porAdbSeAceptaTarjetasYLista() {
        val p = PreferenciasDesdeTexto.aplicar(Preferencias(), mapOf("vista" to "Lista"))
        assertEquals(VistaGalerias.LISTA, p.vistaGalerias)
        assertEquals(VistaGalerias.TARJETAS, PreferenciasDesdeTexto.aplicar(p, mapOf("vista" to "tarjetas")).vistaGalerias)
        assertEquals(p, PreferenciasDesdeTexto.aplicar(p, mapOf("vista" to "nada")))
    }

    @Test
    fun cambiarLaVistaNoCambiaComoSeVeUnaEscena() {
        val a = Preferencias()
        val b = Preferencias(vistaGalerias = VistaGalerias.LISTA)
        assertEquals(a.paraEscena(), b.paraEscena())
    }

    @Test
    fun galeriasNuevasYaNoEstaAparte() {
        assertFalse(GrupoAjustes.APARTE.contains(IdAjuste.GALERIAS_NUEVAS))
        assertEquals(GaleriasNuevas.TILDADAS, Preferencias().galeriasNuevas)
    }
}

class FondoDeInicioTest {
    private fun obra(id: String, bajada: Boolean) = Obra(id, "$id.jpg", 100, 100, null).conBajada(bajada)
    private fun cat(vararg g: Galeria) = CatalogoCargado(g.toList(), emptyList(), 1L)

    @Test
    fun elFondoEsLaPrimeraObraBajadaParaVerseConSuCopia4K() {
        val g = Galeria("G", "Barroco", null, listOf(obra("G-1", false), obra("G-2", true), obra("G-3", true)))
        val r = ResumenInicio.de(cat(g), Preferencias())
        assertEquals("G-2", r.tapa?.driveId)
        assertEquals("G", r.tapaGaleriaId)
    }

    @Test
    fun siLaGaleriaElegidaNoTieneNingunaBajadaSeUsaUnaDeOtraTildada() {
        val a = Galeria("A", "Barroco", null, listOf(obra("A-1", false)))
        val b = Galeria("B", "Impresionismo", null, listOf(obra("B-1", true)))
        val r = ResumenInicio.de(cat(a, b), Preferencias(galeria = "A"))
        assertEquals("B-1", r.tapa?.driveId)
        assertEquals("B", r.tapaGaleriaId)
    }

    @Test
    fun siNingunaEstaBajadaQuedaLaPrimeraYSuTapaChica() {
        val a = Galeria("A", "Barroco", null, listOf(obra("A-1", false), obra("A-2", false)))
        val r = ResumenInicio.de(cat(a), Preferencias())
        assertEquals("A-1", r.tapa?.driveId)
        assertEquals("A", r.tapaGaleriaId)
    }
}
