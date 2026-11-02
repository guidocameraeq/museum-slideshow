package local.museumslideshow.ajustes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreferenciasTest {
    @Test
    fun losValoresPorDefectoSonLosDelSpec() {
        val p = Preferencias()
        assertEquals(Tema.GALERIA_MODERNA, p.tema)
        assertEquals(Modo.AUTOMATICO, p.modo)
        assertEquals(7 * 60, p.horaDiaMin)
        assertEquals(19 * 60, p.horaNocheMin)
        assertNull(p.colorParedClaro)
        assertNull(p.colorParedOscuro)
        assertEquals(MarcoElegido.DEL_TEMA, p.marco)
        assertEquals(Tamano.GRANDE, p.tamano)
        assertEquals(false, p.pantallaCompletaConMarco)
        assertEquals(ExplicacionEnPantalla.CUANDO_ENTRA, p.explicacionEnPantalla)
        assertEquals(NombreSala.SIEMPRE, p.nombreSala)
        assertEquals(Calidad.K4, p.calidad)
        assertEquals(60, p.tiempoPorObraSeg)
        assertEquals(Orden.GALERIA, p.orden)
        assertNull(p.galeria)
        assertEquals(Pared.HASTA_3, p.pared)
        assertEquals(CartelObra.SIEMPRE, p.cartelObra)
        assertEquals(Transicion.FUNDIDO, p.transicion)
        assertEquals(1500, p.duracionTransicionMs)
        assertEquals(SincronizarCada.SEIS_HORAS, p.sincronizarCada)
        assertEquals(EspacioMaximo.UN_GB, p.espacioMaximo)
        assertNull(p.carpetaDrive)
    }

    @Test
    fun modoFijoNoDependeDeLaHora() {
        assertEquals(Modo.CLARO, Preferencias(modo = Modo.CLARO).modoEfectivo(23 * 60))
        assertEquals(Modo.OSCURO, Preferencias(modo = Modo.OSCURO).modoEfectivo(12 * 60))
    }

    @Test
    fun automaticoEsClaroDeDiaYOscuroDeNoche() {
        val p = Preferencias()
        assertEquals(Modo.OSCURO, p.modoEfectivo(6 * 60 + 59))
        assertEquals(Modo.CLARO, p.modoEfectivo(7 * 60))
        assertEquals(Modo.CLARO, p.modoEfectivo(18 * 60 + 59))
        assertEquals(Modo.OSCURO, p.modoEfectivo(19 * 60))
        assertEquals(Modo.OSCURO, p.modoEfectivo(0))
        assertEquals(Modo.OSCURO, p.modoEfectivo(23 * 60 + 59))
    }

    @Test
    fun automaticoConElDiaQueEmpiezaDespuesDeLaNocheDaLaVuelta() {
        val p = Preferencias(horaDiaMin = 20 * 60, horaNocheMin = 6 * 60)   // turno de noche
        assertEquals(Modo.CLARO, p.modoEfectivo(22 * 60))
        assertEquals(Modo.CLARO, p.modoEfectivo(3 * 60))
        assertEquals(Modo.OSCURO, p.modoEfectivo(12 * 60))
    }
}

class PreferenciasDesdeTextoTest {
    private fun aplicar(vararg v: Pair<String, String>) = PreferenciasDesdeTexto.aplicar(Preferencias(), v.toMap())

    @Test
    fun aplicaLosEjemplosDelEncargo() {
        val p = aplicar("tema" to "sala-oscura", "tam" to "maximo", "modo" to "oscuro", "pared" to "2")
        assertEquals(Tema.SALA_OSCURA, p.tema)
        assertEquals(Tamano.MAXIMO, p.tamano)
        assertEquals(Modo.OSCURO, p.modo)
        assertEquals(Pared.HASTA_2, p.pared)
    }

    @Test
    fun todosLosTemasPorId() {
        assertEquals(Tema.GALERIA_MODERNA, aplicar("tema" to "galeria-moderna").tema)
        assertEquals(Tema.INMERSIVO, aplicar("tema" to "inmersivo").tema)
        assertEquals(Tema.LIBRO_DE_ARTE, aplicar("tema" to "libro-de-arte").tema)
    }

    @Test
    fun entiendeMayusculasYTildes() {
        assertEquals(Tamano.MAXIMO, aplicar("tam" to "Máximo").tamano)
        assertEquals(Modo.AUTOMATICO, PreferenciasDesdeTexto.aplicar(Preferencias(modo = Modo.OSCURO), mapOf("modo" to "Automático")).modo)
        assertEquals(Tema.SALA_OSCURA, aplicar("tema" to "Sala_Oscura").tema)
    }

    @Test
    fun tamanosYPantallaCompleta() {
        assertEquals(Tamano.PANTALLA_COMPLETA, aplicar("tam" to "completa").tamano)
        assertEquals(Tamano.NORMAL, aplicar("tam" to "normal").tamano)
        assertEquals(true, aplicar("completa-con-marco" to "si").pantallaCompletaConMarco)
        assertEquals(false, PreferenciasDesdeTexto.aplicar(Preferencias(pantallaCompletaConMarco = true), mapOf("completa-con-marco" to "no")).pantallaCompletaConMarco)
    }

    @Test
    fun marcos() {
        assertEquals(MarcoElegido.DEL_TEMA, aplicar("marco" to "tema").marco)
        assertEquals(MarcoElegido.AL_AZAR, aplicar("marco" to "azar").marco)
        assertEquals(MarcoElegido.DE_LA_GALERIA, aplicar("marco" to "galeria").marco)
        assertEquals(MarcoElegido.FIJO("madera"), aplicar("marco" to "madera").marco)
        assertEquals(MarcoElegido.FIJO("dorado-redondo"), aplicar("marco" to "dorado-redondo").marco)
    }

    @Test
    fun coloresDeParedPorModo() {
        assertEquals("piedra", aplicar("pared-claro" to "piedra").colorParedClaro)
        assertEquals("bordo", aplicar("pared-oscuro" to "bordó").colorParedOscuro)
        assertNull(PreferenciasDesdeTexto.aplicar(Preferencias(colorParedOscuro = "negro"), mapOf("pared-oscuro" to "tema")).colorParedOscuro)
        // un color oscuro no vale como claro
        assertNull(aplicar("pared-claro" to "negro").colorParedClaro)
    }

    @Test
    fun explicacionSalaCartelTransicionOrdenCalidad() {
        assertEquals(ExplicacionEnPantalla.NUNCA, aplicar("expl" to "nunca").explicacionEnPantalla)
        assertEquals(NombreSala.SOLO_CONTADOR, aplicar("sala" to "contador").nombreSala)
        assertEquals(NombreSala.UNOS_SEGUNDOS, aplicar("sala" to "segundos").nombreSala)
        assertEquals(NombreSala.NUNCA, aplicar("sala" to "nunca").nombreSala)
        assertEquals(CartelObra.UNOS_SEGUNDOS, aplicar("cartel" to "segundos").cartelObra)
        assertEquals(Transicion.NINGUNA, aplicar("transicion" to "ninguna").transicion)
        assertEquals(Orden.MEZCLAR, aplicar("orden" to "mezclar").orden)
        assertEquals(Calidad.HD1080, aplicar("calidad" to "1080").calidad)
        assertEquals(Pared.APAGADA, aplicar("pared" to "apagada").pared)
    }

    @Test
    fun numerosDentroDeSuRango() {
        assertEquals(120, aplicar("tiempo" to "120").tiempoPorObraSeg)
        assertEquals(2000, aplicar("duracion" to "2000").duracionTransicionMs)
    }

    @Test
    fun galeriaConservaElIdYTodasLaBorra() {
        assertEquals("Mi Galeria", aplicar("galeria" to " Mi Galeria ").galeria)
        assertNull(PreferenciasDesdeTexto.aplicar(Preferencias(galeria = "x"), mapOf("galeria" to "todas")).galeria)
    }

    @Test
    fun unValorInvalidoSeIgnoraYLoDemasSeAplica() {
        val p = aplicar(
            "tema" to "no-existe", "tam" to "gigante", "tiempo" to "5", "tiempo2" to "1", "duracion" to "9000",
            "marco" to "hierro", "pared" to "7", "modo" to "oscuro",
        )
        assertEquals(Tema.GALERIA_MODERNA, p.tema)
        assertEquals(Tamano.GRANDE, p.tamano)
        assertEquals(60, p.tiempoPorObraSeg)
        assertEquals(1500, p.duracionTransicionMs)
        assertEquals(MarcoElegido.DEL_TEMA, p.marco)
        assertEquals(Pared.HASTA_3, p.pared)
        assertEquals(Modo.OSCURO, p.modo)
    }

    @Test
    fun sinValoresNoCambiaNada() {
        assertEquals(Preferencias(), PreferenciasDesdeTexto.aplicar(Preferencias(), emptyMap()))
    }
}
