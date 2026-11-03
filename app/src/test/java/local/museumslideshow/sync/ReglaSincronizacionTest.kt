package local.museumslideshow.sync

import local.museumslideshow.ajustes.SincronizarCada
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReglaSincronizacionTest {
    private val hora = 3_600_000L
    private val ahora = 1_000 * hora

    @Test
    fun soloAMano_nuncaToca() {
        assertFalse(ReglaSincronizacion.tocaAlArrancar(SincronizarCada.SOLO_A_MANO, null, ahora))
        assertFalse(ReglaSincronizacion.tocaAlArrancar(SincronizarCada.SOLO_A_MANO, ahora - 1000 * hora, ahora))
    }

    @Test
    fun sinNingunaBuena_toca() {
        assertTrue(ReglaSincronizacion.tocaAlArrancar(SincronizarCada.VEINTICUATRO_HORAS, null, ahora))
    }

    @Test
    fun conLaUltimaReciente_noToca() {
        assertFalse(ReglaSincronizacion.tocaAlArrancar(SincronizarCada.SEIS_HORAS, ahora - 5 * hora, ahora))
        assertFalse(ReglaSincronizacion.tocaAlArrancar(SincronizarCada.UNA_HORA, ahora - 10_000, ahora))
    }

    @Test
    fun alCumplirseElPlazo_toca() {
        assertTrue(ReglaSincronizacion.tocaAlArrancar(SincronizarCada.SEIS_HORAS, ahora - 6 * hora, ahora))
        assertTrue(ReglaSincronizacion.tocaAlArrancar(SincronizarCada.UNA_HORA, ahora - 2 * hora, ahora))
        assertFalse(ReglaSincronizacion.tocaAlArrancar(SincronizarCada.VEINTICUATRO_HORAS, ahora - 23 * hora, ahora))
        assertTrue(ReglaSincronizacion.tocaAlArrancar(SincronizarCada.VEINTICUATRO_HORAS, ahora - 25 * hora, ahora))
    }

    @Test
    fun conLaUltimaEnElFuturo_toca() {
        assertTrue(ReglaSincronizacion.tocaAlArrancar(SincronizarCada.SEIS_HORAS, ahora + hora, ahora))
    }
}
