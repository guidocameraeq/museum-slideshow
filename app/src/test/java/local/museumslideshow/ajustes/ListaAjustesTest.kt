package local.museumslideshow.ajustes

import local.museumslideshow.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasosTest {
    @Test
    fun tiempoPorObraSiguienteYAnteriorConTope() {
        val t = Pasos.TIEMPO_SEG
        assertEquals(listOf(10, 15, 20, 30, 45, 60, 120, 180, 300, 600), t)
        assertEquals(15, Pasos.mover(10, t, 1, false))
        assertEquals(120, Pasos.mover(60, t, 1, false))
        assertEquals(45, Pasos.mover(60, t, -1, false))
        assertEquals(10, Pasos.mover(10, t, -1, false))     // tope abajo
        assertEquals(600, Pasos.mover(600, t, 1, false))    // tope arriba
    }

    @Test
    fun conOkDaLaVueltaAlLlegarAlFinal() {
        val t = Pasos.TIEMPO_SEG
        assertEquals(10, Pasos.mover(600, t, 1, true))
        assertEquals(600, Pasos.mover(10, t, -1, true))
    }

    @Test
    fun unValorQueNoEsUnPasoVaAlMasCercanoEnEsaDireccion() {
        assertEquals(30, Pasos.mover(25, Pasos.TIEMPO_SEG, 1, false))
        assertEquals(20, Pasos.mover(25, Pasos.TIEMPO_SEG, -1, false))
    }

    @Test
    fun duracionDeMediaEnMedia() {
        assertEquals(listOf(500, 1000, 1500, 2000, 2500, 3000), Pasos.DURACION_MS)
        assertEquals(2000, Pasos.mover(1500, Pasos.DURACION_MS, 1, false))
        assertEquals(3000, Pasos.mover(3000, Pasos.DURACION_MS, 1, false))
        assertEquals(500, Pasos.mover(500, Pasos.DURACION_MS, -1, false))
    }

    @Test
    fun horaDeAMediaHoraConVuelta() {
        assertEquals(7 * 60 + 30, Pasos.moverHora(7 * 60, 1))
        assertEquals(6 * 60 + 30, Pasos.moverHora(7 * 60, -1))
        assertEquals(0, Pasos.moverHora(23 * 60 + 30, 1))
        assertEquals(23 * 60 + 30, Pasos.moverHora(0, -1))
        assertEquals(7 * 60 + 30, Pasos.moverHora(7 * 60 + 10, 1))   // 7:10 -> 7:30
        assertEquals(7 * 60, Pasos.moverHora(7 * 60 + 10, -1))       // 7:10 -> 7:00
    }
}

class ListaAjustesTest {
    private val galerias = listOf(GaleriaOpcion("ID1", "Impresionismo"), GaleriaOpcion("ID2", "Barroco"))
    private val items get() = ListaAjustes.construir(galerias)
    private fun item(id: IdAjuste) = items.first { it.id == id }

    @Test
    fun estanTodosLosAjustesDelSpecEnSuOrden() {
        assertEquals(IdAjuste.values().toList(), items.map { it.id })
        assertEquals(
            listOf(
                IdAjuste.TEMA, IdAjuste.MODO, IdAjuste.HORA_DIA, IdAjuste.HORA_NOCHE, IdAjuste.PARED_CLARO, IdAjuste.PARED_OSCURO,
                IdAjuste.MARCO, IdAjuste.TAMANO, IdAjuste.COMPLETA_CON_MARCO, IdAjuste.EXPLICACION, IdAjuste.SALA, IdAjuste.CALIDAD,
                IdAjuste.TIEMPO, IdAjuste.ORDEN, IdAjuste.GALERIA, IdAjuste.PARED, IdAjuste.CARTEL, IdAjuste.CREDITO, IdAjuste.CIERRE_BARRA, IdAjuste.TRANSICION,
                IdAjuste.DURACION, IdAjuste.SINCRONIZAR, IdAjuste.RESERVA, IdAjuste.ESPACIO, IdAjuste.RITMO, IdAjuste.CAMINO, IdAjuste.CARPETA, IdAjuste.GALERIAS_NUEVAS,
                IdAjuste.PROTECTOR, IdAjuste.ESTADO,
            ),
            items.map { it.id },
        )
    }

    @Test
    fun elValorActualSeMuestraYLaDerechaAvanza() {
        val tema = item(IdAjuste.TEMA)
        assertEquals(Etiqueta.Res(R.string.tema_galeria_moderna), tema.valor(Preferencias()))
        val p = tema.mover(Preferencias(), 1, false)
        assertEquals(Tema.SALA_OSCURA, p.tema)
        assertEquals(Tema.GALERIA_MODERNA, tema.mover(p, -1, false).tema)
    }

    @Test
    fun enLasPuntasSeFrenaPeroConOkDaLaVuelta() {
        val tema = item(IdAjuste.TEMA)
        assertEquals(Tema.GALERIA_MODERNA, tema.mover(Preferencias(), -1, false).tema)
        val ultimo = Preferencias(tema = Tema.LIBRO_DE_ARTE)
        assertEquals(Tema.LIBRO_DE_ARTE, tema.mover(ultimo, 1, false).tema)
        assertEquals(Tema.GALERIA_MODERNA, tema.mover(ultimo, 1, true).tema)
    }

    @Test
    fun tiempoYDuracionUsanLosPasos() {
        assertEquals(Etiqueta.Res(R.string.formato_segundos, listOf(45)), item(IdAjuste.TIEMPO).valor(Preferencias(tiempoPorObraSeg = 45)))
        assertEquals(Etiqueta.Res(R.string.formato_minutos, listOf(2)), item(IdAjuste.TIEMPO).valor(Preferencias(tiempoPorObraSeg = 120)))
        assertEquals(120, item(IdAjuste.TIEMPO).mover(Preferencias(), 1, false).tiempoPorObraSeg)
        assertEquals(Etiqueta.Res(R.string.formato_segundos_texto, listOf("1,5")), item(IdAjuste.DURACION).valor(Preferencias()))
        assertEquals(Etiqueta.Res(R.string.formato_segundos_texto, listOf("2")), item(IdAjuste.DURACION).valor(Preferencias(duracionTransicionMs = 2000)))
        assertEquals(2000, item(IdAjuste.DURACION).mover(Preferencias(), 1, false).duracionTransicionMs)
    }

    @Test
    fun elHorarioVaDeAMediaHoraPorSeparado() {
        val dia = item(IdAjuste.HORA_DIA).mover(Preferencias(), 1, false)
        assertEquals(7 * 60 + 30, dia.horaDiaMin)
        assertEquals(19 * 60, dia.horaNocheMin)
        val noche = item(IdAjuste.HORA_NOCHE).mover(Preferencias(), -1, false)
        assertEquals(18 * 60 + 30, noche.horaNocheMin)
        assertEquals(Etiqueta.Res(R.string.formato_hora, listOf(7, 0)), item(IdAjuste.HORA_DIA).valor(Preferencias()))
    }

    @Test
    fun elMarcoTieneTodasLasOpciones() {
        val marco = item(IdAjuste.MARCO) as ItemLista
        // Del tema, los 6 del catálogo, al azar y el que diga la galería
        assertEquals(1 + 6 + 2, marco.opciones.size)
        assertEquals(Etiqueta.Res(R.string.v_del_tema), marco.valor(Preferencias()))
        var p = Preferencias()
        val vistos = ArrayList<MarcoElegido>()
        repeat(marco.opciones.size - 1) { p = marco.mover(p, 1, false); vistos += p.marco }
        assertEquals(MarcoElegido.DE_LA_GALERIA, vistos.last())
        assertTrue(MarcoElegido.AL_AZAR in vistos)
        assertTrue(MarcoElegido.FIJO("madera") in vistos)
    }

    @Test
    fun losColoresDePared() {
        val claro = item(IdAjuste.PARED_CLARO) as ItemLista
        assertEquals(1 + 3, claro.opciones.size)
        assertEquals("blanco-galeria", claro.mover(Preferencias(), 1, false).colorParedClaro)
        val oscuro = item(IdAjuste.PARED_OSCURO) as ItemLista
        assertEquals(1 + 4, oscuro.opciones.size)
        assertEquals(null, oscuro.mover(Preferencias(colorParedOscuro = "carbon"), -1, false).colorParedOscuro)
    }

    @Test
    fun laGaleriaEsTodasMasLasBajadas() {
        val g = item(IdAjuste.GALERIA) as ItemLista
        assertEquals(3, g.opciones.size)
        assertEquals(Etiqueta.Res(R.string.v_todas), g.valor(Preferencias()))
        val p = g.mover(Preferencias(), 1, false)
        assertEquals("ID1", p.galeria)
        assertEquals(Etiqueta.Texto("Impresionismo"), g.valor(p))
        assertEquals("ID2", g.mover(p, 1, false).galeria)
        // la elección por título también se reconoce
        assertEquals(Etiqueta.Texto("Barroco"), g.valor(Preferencias(galeria = "barroco")))
        // una galería que ya no existe se muestra como Todas
        assertEquals(Etiqueta.Res(R.string.v_todas), g.valor(Preferencias(galeria = "borrada")))
    }

    @Test
    fun elProtectorYLaCarpetaNoSeCambianAcaYElProtectorEstaDeshabilitado() {
        val protector = item(IdAjuste.PROTECTOR) as ItemInfo
        assertFalse(protector.habilitado)
        assertFalse(protector.editable)
        assertEquals(Etiqueta.Res(R.string.protector_proximamente), protector.valor(Preferencias()))
        val carpeta = item(IdAjuste.CARPETA) as ItemInfo
        assertEquals(R.string.carpeta_ayuda, carpeta.ayuda)
        assertEquals(Etiqueta.Res(R.string.carpeta_de_fabrica), carpeta.valor(Preferencias()))
        assertEquals(Etiqueta.Texto("1AbCdE…Mn_p"), carpeta.valor(Preferencias(carpetaDrive = "1AbCdEfGhIjKlMn_p")))
        val prefs = Preferencias()
        assertEquals(prefs, carpeta.mover(prefs, 1, true))
        assertEquals(prefs, protector.mover(prefs, 1, true))
    }

    @Test
    fun sincronizarYEspacio() {
        assertEquals(SincronizarCada.VEINTICUATRO_HORAS, item(IdAjuste.SINCRONIZAR).mover(Preferencias(), 1, false).sincronizarCada)
        assertEquals(SincronizarCada.SOLO_A_MANO, item(IdAjuste.SINCRONIZAR).mover(Preferencias(sincronizarCada = SincronizarCada.VEINTICUATRO_HORAS), 1, false).sincronizarCada)
        assertEquals(EspacioMaximo.DOS_GB, item(IdAjuste.ESPACIO).mover(Preferencias(), 1, false).espacioMaximo)
        assertEquals(EspacioMaximo.MEDIO_GB, item(IdAjuste.ESPACIO).mover(Preferencias(), -1, false).espacioMaximo)
    }

    @Test
    fun siNoAlternaConCualquierFlecha() {
        val i = item(IdAjuste.COMPLETA_CON_MARCO)
        assertTrue(i.mover(Preferencias(), 1, false).pantallaCompletaConMarco)
        assertFalse(i.mover(Preferencias(pantallaCompletaConMarco = true), -1, false).pantallaCompletaConMarco)
    }

    @Test
    fun estadoEsLaUltimaEntrada() {
        assertTrue(items.last() is ItemAbrirEstado)
    }
}

class GruposAjustesTest {
    private val items = ListaAjustes.construir(listOf(GaleriaOpcion("ID1", "Impresionismo")))

    @Test
    fun estanTodosLosAjustesDeAntesEnAlgunGrupoSinRepetir() {
        val repartidos = GrupoAjustes.values().flatMap { it.ids }
        assertEquals("sin repetidos", repartidos.size, repartidos.toSet().size)
        assertEquals(IdAjuste.values().toSet(), (repartidos + GrupoAjustes.APARTE).toSet())
        assertTrue(GrupoAjustes.APARTE.none { it in repartidos })
    }

    @Test
    fun cadaAjusteEstaEnElGrupoCorrecto() {
        assertEquals(
            listOf(
                IdAjuste.TEMA, IdAjuste.MODO, IdAjuste.HORA_DIA, IdAjuste.HORA_NOCHE, IdAjuste.PARED_CLARO, IdAjuste.PARED_OSCURO,
                IdAjuste.MARCO, IdAjuste.TAMANO, IdAjuste.COMPLETA_CON_MARCO, IdAjuste.EXPLICACION, IdAjuste.SALA, IdAjuste.CARTEL, IdAjuste.CREDITO, IdAjuste.CIERRE_BARRA,
            ),
            items.delGrupo(GrupoAjustes.APARIENCIA).map { it.id },
        )
        assertEquals(
            listOf(IdAjuste.TIEMPO, IdAjuste.ORDEN, IdAjuste.GALERIA, IdAjuste.PARED, IdAjuste.TRANSICION, IdAjuste.DURACION, IdAjuste.CALIDAD),
            items.delGrupo(GrupoAjustes.PASE).map { it.id },
        )
        assertEquals(
            listOf(IdAjuste.CARPETA, IdAjuste.SINCRONIZAR, IdAjuste.RESERVA, IdAjuste.ESPACIO, IdAjuste.RITMO, IdAjuste.CAMINO),
            items.delGrupo(GrupoAjustes.CONEXION).map { it.id },
        )
    }

    @Test
    fun losItemsDeUnGrupoSonLosMismosDeLaListaCompleta() {
        // No se duplica la lógica: el grupo reusa los mismos ItemAjuste de ListaAjustes.construir.
        for (g in GrupoAjustes.values()) for (item in items.delGrupo(g)) {
            assertTrue(item === items.first { it.id == item.id })
        }
    }

    @Test
    fun laCarpetaSigueSiendoSoloDeLectura() {
        val carpeta = items.delGrupo(GrupoAjustes.CONEXION).first { it.id == IdAjuste.CARPETA } as ItemInfo
        assertEquals(R.string.carpeta_ayuda, carpeta.ayuda)
        assertFalse(carpeta.editable)
    }
}
