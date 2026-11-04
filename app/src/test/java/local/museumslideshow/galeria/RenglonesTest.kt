package local.museumslideshow.galeria

import local.museumslideshow.acomodo.Datos
import local.museumslideshow.acomodo.NivelInfo
import local.museumslideshow.temas.Rol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RenglonesTest {
    private val fm = Formatos(
        autorConVida = { a, v -> "$a ($v)" },
        autorYAnio = { a, y -> "$a, $y" },
        tecnicaYMedidas = { t, m -> "$t · $m" },
    )
    private val aviso = "Mantener OK: leer sobre la obra"
    private val completa = Datos.ficha()

    private fun roles(l: List<Renglon>) = l.map { it.rol }

    @Test
    fun minimaEsTituloYAutorConAnio() {
        val r = Renglones.cartel(completa, NivelInfo.MINIMA, false, aviso, fm, true)
        assertEquals(listOf(Rol.TITULO, Rol.AUTOR), roles(r))
        assertEquals("Claude Monet, 1872", r[1].texto)
    }

    @Test
    fun fichaTraeAutorConVidaAnioMovimientoDatosYCredito() {
        val r = Renglones.cartel(completa, NivelInfo.FICHA, false, aviso, fm, true)
        assertEquals(
            listOf(Rol.TITULO, Rol.AUTOR, Rol.ANIO, Rol.MOVIMIENTO, Rol.DATOS, Rol.DATOS, Rol.CREDITO),
            roles(r),
        )
        assertEquals("Claude Monet (1840–1926)", r[1].texto)
        assertEquals("Óleo sobre lienzo · 48 × 63 cm", r[4].texto)
        assertEquals("Museo Marmottan, París", r[5].texto)
        assertEquals(2f, r[5].margenSup!!, 0f)       // la ubicación va pegada a técnica · medidas
        assertNull(r[4].margenSup)
    }

    @Test
    fun completaSumaLaExplicacionAntesDelCredito() {
        val r = Renglones.cartel(completa, NivelInfo.COMPLETA, false, aviso, fm, true)
        assertEquals(Rol.EXPLICACION, r[r.size - 2].rol)
        assertEquals(Rol.CREDITO, r.last().rol)
        assertEquals(Datos.EXPLICACION_CORTA, r[r.size - 2].texto)
    }

    @Test
    fun elAvisoDeOkVaAlFinalSoloSiSePide() {
        val con = Renglones.cartel(completa, NivelInfo.FICHA, true, aviso, fm, true)
        assertEquals(Rol.AVISO_OK, con.last().rol)
        assertEquals(aviso, con.last().texto)
        assertFalse(Renglones.cartel(completa, NivelInfo.FICHA, false, aviso, fm, true).any { it.rol == Rol.AVISO_OK })
        assertEquals(Rol.AVISO_OK, Renglones.cartel(completa, NivelInfo.MINIMA, true, aviso, fm, true).last().rol)
    }

    @Test
    fun losCamposQueFaltanNoDejanHuecos() {
        val pelada = completa.copy(
            vidaAutor = null, anio = "  ", movimiento = null, tecnica = null, medidas = "48 × 63 cm",
            ubicacion = null, explicacion = null, credito = null,
        )
        val r = Renglones.cartel(pelada, NivelInfo.COMPLETA, false, aviso, fm, true)
        assertEquals(listOf(Rol.TITULO, Rol.AUTOR, Rol.DATOS), roles(r))
        assertEquals("Claude Monet", r[1].texto)       // sin vida: solo el autor
        assertEquals("48 × 63 cm", r[2].texto)          // solo las medidas, sin " · "
        assertNull(r[2].margenSup)
    }

    @Test
    fun laUbicacionSolaTomaElAireDeUnPrimerDato() {
        val f = completa.copy(tecnica = null, medidas = null)
        val r = Renglones.cartel(f, NivelInfo.FICHA, false, aviso, fm, true)
        val ubi = r.first { it.rol == Rol.DATOS }
        assertNull(ubi.margenSup)
    }

    @Test
    fun sinTituloNiAutorNiAnioElMinimoQuedaVacio() {
        val f = completa.copy(titulo = null, autor = null, anio = null)
        assertTrue(Renglones.cartel(f, NivelInfo.MINIMA, false, aviso, fm, true).isEmpty())
        assertEquals("1872", Renglones.autorYAnio(completa.copy(autor = null), fm))
        assertEquals("Claude Monet", Renglones.autorYAnio(completa.copy(anio = null), fm))
    }

    @Test
    fun nombreYChipLlevanTituloYAutorConAnio() {
        val n = Renglones.nombre(completa, fm)
        assertEquals(listOf(Rol.NOMBRE_TITULO, Rol.NOMBRE_AUTOR), roles(n))
        assertEquals("Claude Monet, 1872", n[1].texto)
        assertEquals(listOf(Rol.CHIP_TITULO, Rol.CHIP_AUTOR), roles(Renglones.chip(completa, fm)))
    }

    @Test
    fun libroCabeceraYCredito() {
        val r = Renglones.libroCabecera(completa, fm)
        assertEquals(
            listOf(Rol.LIBRO_TITULO, Rol.LIBRO_AUTOR, Rol.LIBRO_DATO, Rol.LIBRO_MOVIMIENTO, Rol.LIBRO_DATO, Rol.LIBRO_DATO),
            roles(r),
        )
        assertEquals(12f, r[0].margenSup!!, 0f)
        assertEquals(6f, r[0].margenInf, 0f)
        assertEquals(6f, r[4].margenSup!!, 0f)       // técnica · medidas lleva 6 de aire
        assertEquals(0f, r[5].margenSup!!, 0f)
        assertEquals("Dominio público", Renglones.libroCredito(completa, true)!!.texto)
        assertNull(Renglones.libroCredito(completa.copy(credito = null), true))
    }

    @Test
    fun sinCreditoNoHayRenglonDeCreditoEnElCartelNiEnElLibro() {
        for (n in NivelInfo.values()) {
            assertFalse(Renglones.cartel(completa, n, true, aviso, fm, false).any { it.rol == Rol.CREDITO })
        }
        assertTrue(Renglones.cartel(completa, NivelInfo.FICHA, false, aviso, fm, true).any { it.rol == Rol.CREDITO })
        assertNull(Renglones.libroCredito(completa, false))
    }
}
