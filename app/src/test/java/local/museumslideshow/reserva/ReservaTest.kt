package local.museumslideshow.reserva

import local.museumslideshow.acomodo.Datos
import local.museumslideshow.ajustes.Orden
import local.museumslideshow.ajustes.Pared
import local.museumslideshow.ajustes.Preferencias
import local.museumslideshow.ajustes.Tema
import local.museumslideshow.catalogo.Galeria
import local.museumslideshow.galeria.Pase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReservaTest {
    private fun ids(n: Int) = (0 until n).map { "o$it" }

    @Test
    fun laVentanaSonLasProximasNMasLasUltimasDiezVistas() {
        val v = Reserva.ventana(ids(200), posicion = 100, n = 50)
        assertEquals(ids(200).subList(100, 150), v.adelante)
        assertEquals((99 downTo 90).map { "o$it" }, v.vistas)
        assertEquals(60, v.todas.size)
    }

    @Test
    fun alPrincipioLasVistasSonLasDelFinalPorqueElPaseDaLaVuelta() {
        val v = Reserva.ventana(ids(200), posicion = 0, n = 50)
        assertEquals(ids(200).subList(0, 50), v.adelante)
        assertEquals((199 downTo 190).map { "o$it" }, v.vistas)
    }

    @Test
    fun alFinalLasProximasDanLaVueltaAlPrincipio() {
        val v = Reserva.ventana(ids(100), posicion = 95, n = 10)
        assertEquals(listOf("o95", "o96", "o97", "o98", "o99", "o0", "o1", "o2", "o3", "o4"), v.adelante)
        assertEquals((94 downTo 85).map { "o$it" }, v.vistas)
    }

    @Test
    fun siElPaseEsChicoLaVentanaNoRepiteNiPasaDeLoQueHay() {
        val v = Reserva.ventana(ids(8), posicion = 3, n = 50)
        assertEquals(8, v.adelante.size)
        assertTrue(v.vistas.isEmpty())
        assertEquals(8, v.todas.size)
        val w = Reserva.ventana(ids(30), posicion = 0, n = 25)
        assertEquals(25, w.adelante.size)
        assertEquals(5, w.vistas.size) // quedan 5 obras que ni son próximas
        assertEquals(30, w.todas.size)
        assertTrue(Reserva.ventana(emptyList(), 0, 50).todas.isEmpty())
    }

    @Test
    fun noGuardaMasDeNMasDiezCopias() {
        val n = 50
        for (pos in listOf(0, 1, 49, 100, 199)) {
            assertTrue(Reserva.ventana(ids(200), pos, n).todas.size <= n + Reserva.VISTAS)
        }
    }

    @Test
    fun alArrancarSinNadaBajadoBajaLasNProximasEnOrden() {
        val p = Reserva.planificar(ids(200), 0, 50, emptySet())
        assertTrue(p.tanda)
        assertEquals(ids(200).subList(0, 50), p.aBajar)
        assertTrue(p.sobrantes.isEmpty())
        assertEquals(0, p.listasPorDelante)
    }

    @Test
    fun conMasDeLaMitadPorDelanteNoBajaNada() {
        // Estoy en la 10 con 10..59 bajadas: quedan 50 por delante.
        val bajadas = ids(200).subList(10, 60).toSet()
        val p = Reserva.planificar(ids(200), 10, 50, bajadas)
        assertFalse(p.tanda)
        assertTrue(p.aBajar.isEmpty())
        assertEquals(50, p.listasPorDelante)
    }

    @Test
    fun conMitadExactaTodaviaNoBajaYConUnaMenosSi() {
        // N=50: hay bajadas 25 por delante (justo N/2) -> no; 24 -> sí
        val ordenes = ids(200)
        val justo = ordenes.subList(0, 25).toSet()
        val p25 = Reserva.planificar(ordenes, 0, 50, justo)
        assertEquals(25, p25.listasPorDelante)
        assertFalse(p25.tanda)
        val p24 = Reserva.planificar(ordenes, 0, 50, ordenes.subList(0, 24).toSet())
        assertTrue(p24.tanda)
        // y la tanda completa hasta N: baja las 26 que faltan, no solo una
        assertEquals(ordenes.subList(24, 50), p24.aBajar)
    }

    @Test
    fun avanzandoUnaObraPorVezLaTandaLlegaCuandoQuedanMenosDeLaMitad() {
        val orden = ids(200)
        var bajadas = orden.subList(0, 50).toSet()
        var pos = 0
        var tandas = 0
        repeat(200) {
            pos++
            val p = Reserva.planificar(orden, pos, 50, bajadas)
            bajadas = (bajadas - p.sobrantes.toSet()) + p.aBajar
            if (p.tanda) tandas++
            // nunca queda una de las dos próximas sin bajar
            assertTrue(orden[pos % 200] in bajadas && orden[(pos + 1) % 200] in bajadas)
            assertTrue(bajadas.size <= 60)
        }
        // 200 pasos avanzando: una tanda cada ~26 obras
        assertTrue("tandas=$tandas", tandas in 6..9)
    }

    @Test
    fun retrocederConservaLasVistasYBorraLasQueQuedaronLejos() {
        val orden = ids(200)
        // estoy en la 100 con todo bajado: 100..149 por delante y 90..99 vistas
        val bajadas = orden.subList(90, 150).toSet()
        val p = Reserva.planificar(orden, 95, 50, bajadas)
        // al retroceder a la 95: adelante 95..144, vistas 85..94 -> sobran 145..149 y falta la 85..89
        assertEquals((145..149).map { "o$it" }.toSet(), p.sobrantes.toSet())
        assertFalse(p.tanda) // las 50 de adelante están (95..144)
        assertEquals(5, p.vistasGuardadas) // solo 90..94 de las vistas
    }

    @Test
    fun loDestildadoSeBorraPorqueNoEstaEnElPase() {
        val orden = ids(20)
        val bajadas = orden.toSet() + setOf("destildada-1", "destildada-2")
        val p = Reserva.planificar(orden, 0, 50, bajadas)
        assertEquals(setOf("destildada-1", "destildada-2"), p.sobrantes.toSet())
    }

    @Test
    fun unaObraSinMedidasSeBajaAunqueNoEntreAlPaseYNoSeBorra() {
        val p = Reserva.planificar(ids(10), 0, 50, ids(10).toSet() + "sinmedidas-ya", extras = listOf("sinmedidas-ya", "sinmedidas-nueva"))
        assertEquals(listOf("sinmedidas-nueva"), p.aBajar)
        assertTrue("sinmedidas-ya" !in p.sobrantes.toSet())
    }

    @Test
    fun conMezclarLaReservaSigueElOrdenQueVeGuido() {
        fun obra(id: String) = Datos.obra(id, 1000, 600)
        val g = Galeria("g", "G", null, (1..40).map { obra("m$it") })
        val prefs = Preferencias(orden = Orden.MEZCLAR, pared = Pared.APAGADA)
        val tema = Datos.tema(Tema.GALERIA_MODERNA)
        val pase = Pase.armar(listOf(g), prefs, tema, 12345L)!!
        val orden = pase.ordenDeObras().map { it.driveId }
        assertEquals(40, orden.size)
        assertTrue(orden != (1..40).map { "m$it" }) // de verdad mezclado
        // misma semilla = mismo orden para la reserva armada sin la galería
        val otra = Pase.armar(listOf(g), prefs, tema, 12345L)!!.ordenDeObras().map { it.driveId }
        assertEquals(orden, otra)

        // la ventana sigue ese orden, no el del catálogo
        pase.mover(3)
        val v = Reserva.ventana(orden, pase.posicionDeObra(), 5)
        assertEquals(orden.subList(3, 8), v.adelante)
        assertEquals(listOf(orden[2], orden[1], orden[0], orden[39], orden[38], orden[37], orden[36], orden[35], orden[34], orden[33]), v.vistas)
    }

    @Test
    fun elTopeDeEspacioRecortaLoQueSeBaja() {
        val aBajar = ids(10)
        val mb = 1024L * 1024
        assertEquals(10, Reserva.recortarPorEspacio(aBajar, usadoBytes = 0, topeBytes = 100 * mb, estimadoPorObra = 5 * mb).size)
        // quedan 12 MB libres con obras de 5 MB: entran 3 (la tercera completa lo que queda)
        assertEquals(aBajar.take(3), Reserva.recortarPorEspacio(aBajar, 88 * mb, 100 * mb, 5 * mb))
        // lleno o pasado: nada
        assertTrue(Reserva.recortarPorEspacio(aBajar, 100 * mb, 100 * mb, 5 * mb).isEmpty())
        assertTrue(Reserva.recortarPorEspacio(aBajar, 120 * mb, 100 * mb, 5 * mb).isEmpty())
    }

    @Test
    fun losSobrantesSalenOrdenadosDeLaMasVieja() {
        val bajadas = setOf("a", "b", "c", "o0")
        val p = Reserva.planificar(ids(10), 0, 5, bajadas, fechas = mapOf("a" to 300L, "b" to 100L, "c" to 200L, "o0" to 1L))
        assertEquals(listOf("b", "c", "a"), p.sobrantes)
        // sin fecha cuenta como la más vieja
        val q = Reserva.planificar(ids(10), 0, 5, setOf("a", "b"), fechas = mapOf("a" to 5L))
        assertEquals(listOf("b", "a"), q.sobrantes)
    }

    @Test
    fun losSobrantesCuentanComoEspacioQueSePuedeLiberar() {
        val aBajar = ids(10)
        val mb = 1024L * 1024
        // lleno (100 de 100) pero 20 MB son sobrantes: hay lugar para 4 de 5 MB
        assertEquals(aBajar.take(4), Reserva.recortarPorEspacio(aBajar, 100 * mb, 100 * mb, 5 * mb, bytesSobrantes = 20 * mb))
        // sin sobrantes sigue igual
        assertTrue(Reserva.recortarPorEspacio(aBajar, 100 * mb, 100 * mb, 5 * mb, bytesSobrantes = 0).isEmpty())
    }
}
