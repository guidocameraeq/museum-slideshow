package local.museumslideshow.sync

import java.io.File
import java.nio.file.Files
import local.museumslideshow.ajustes.CaminoObras
import local.museumslideshow.ajustes.CodecPreferencias
import local.museumslideshow.ajustes.Preferencias
import local.museumslideshow.ajustes.PreferenciasDesdeTexto
import local.museumslideshow.catalogo.Catalogo
import local.museumslideshow.reserva.DescargadorDrive
import local.museumslideshow.reserva.ErrorReserva
import local.museumslideshow.reserva.ObraRef
import local.museumslideshow.reserva.ResultadoBajada
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

private const val MIN = 60_000L

class UrlCopiaTest {
    private val base = "https://lh3.googleusercontent.com/drive-storage/AJQWtoken123"

    @Test
    fun cambiaElTamanoDelFinal() {
        assertEquals("$base=s3840", UrlCopia.armar("$base=s220", 3840))
        assertEquals("$base=s1920", UrlCopia.armar("$base=s220", 1920))
        assertEquals("$base=s480", UrlCopia.armar("$base=s220", 480))
    }

    @Test
    fun conParametrosLosRespeta() {
        assertEquals("$base=s3840?authuser=0", UrlCopia.armar("$base=s220?authuser=0", 3840))
        assertEquals("$base=s3840", UrlCopia.armar("$base=s220-c", 3840)) // sin recorte
        assertEquals("$base=s3840", UrlCopia.armar("$base=w220-h160", 3840))
    }

    @Test
    fun sinTamanoSeLoAgrega() {
        assertEquals("$base=s3840", UrlCopia.armar(base, 3840))
        assertEquals("$base=s3840?a=b", UrlCopia.armar("$base?a=b", 3840))
    }

    @Test
    fun soloAceptaLinksSegurosDeGoogle() {
        assertNull(UrlCopia.armar("http://lh3.googleusercontent.com/x=s220", 3840))
        assertNull(UrlCopia.armar("https://example.com/x=s220", 3840))
        assertNull(UrlCopia.armar("https://googleusercontent.com.malo.com/x=s220", 3840))
        assertNull(UrlCopia.armar("", 3840))
        assertNull(UrlCopia.armar("$base=s220", 0))
    }
}

/** Un Drive de mentira con dos caminos que se pueden romper por separado. */
private class ApiDosCaminos : DriveApi {
    val llamadas = ArrayList<String>()
    var original: (String) -> Unit = {}
    var copia: (String) -> Unit = {}
    override fun listar(carpetaId: String) = emptyList<ArchivoDrive>()
    override fun bajar(archivoId: String, destino: File, maxBytes: Long) {
        llamadas += "original:$archivoId"; original(archivoId); destino.writeText("ORIG")
    }
    override fun bajarCopia(archivoId: String, lado: Int, destino: File, maxBytes: Long) {
        llamadas += "copia:$archivoId:$lado"; copia(archivoId); destino.writeText("COPIA")
    }
}

class BajadorConRespaldoTest {
    private lateinit var destino: File

    @Before
    fun preparar() { destino = File(Files.createTempDirectory("camino").toFile(), "x.orig") }

    @Test
    fun conCopiaPrimeroBajaLaCopiaYNoTocaElOriginal() {
        val api = ApiDosCaminos()
        val via = BajadorConRespaldo(api) { CaminoObras.COPIA_PRIMERO }.bajar("A", 3840, destino, 100)
        assertEquals(ViaBajada(CaminoDrive.COPIA, 3840), via)
        assertEquals("copia s3840", via.etiqueta)
        assertEquals(listOf("copia:A:3840"), api.llamadas)
        assertEquals("COPIA", destino.readText())
    }

    @Test
    fun conOriginalPrimeroBajaElOriginalYNoTocaLaCopia() {
        val api = ApiDosCaminos()
        val via = BajadorConRespaldo(api) { CaminoObras.ORIGINAL_PRIMERO }.bajar("A", 3840, destino, 100)
        assertEquals("original", via.etiqueta)
        assertEquals(listOf("original:A"), api.llamadas)
    }

    @Test
    fun siLaCopiaDa404OEsUnaRespuestaQueNoEsImagenSeUsaElOriginal() {
        for (codigo in listOf(404, 200, 403)) {
            val api = ApiDosCaminos().also { it.copia = { id -> throw ErrorDrive(codigo, id) } }
            val via = BajadorConRespaldo(api) { CaminoObras.COPIA_PRIMERO }.bajar("A", 1920, destino, 100)
            assertEquals(CaminoDrive.ORIGINAL, via.camino)
            assertEquals(listOf("copia:A:1920", "original:A"), api.llamadas)
            assertEquals("ORIG", destino.readText())
        }
    }

    @Test
    fun siElOriginalFallaConOriginalPrimeroSeUsaLaCopia() {
        val api = ApiDosCaminos().also { it.original = { id -> throw ErrorDrive(404, id) } }
        val via = BajadorConRespaldo(api) { CaminoObras.ORIGINAL_PRIMERO }.bajar("A", 3840, destino, 100)
        assertEquals(CaminoDrive.COPIA, via.camino)
        assertEquals(listOf("original:A", "copia:A:3840"), api.llamadas)
    }

    @Test
    fun sinInternetNoProbaElOtroCamino() {
        val api = ApiDosCaminos().also { it.copia = { id -> throw ErrorDrive(null, id) } }
        try { BajadorConRespaldo(api).bajar("A", 3840, destino, 100); fail() } catch (e: ErrorDrive) { assertNull(e.codigoHttp) }
        assertEquals(listOf("copia:A:3840"), api.llamadas)
    }

    @Test
    fun siFallanLosDosSeVeElErrorDelOriginal() {
        val api = ApiDosCaminos().also {
            it.copia = { id -> throw ErrorDrive(404, id) }
            it.original = { id -> throw ErrorDrive(500, id) }
        }
        try { BajadorConRespaldo(api).bajar("A", 3840, destino, 100); fail() } catch (e: ErrorDrive) { assertEquals(500, e.codigoHttp) }
    }

    @Test
    fun siUnaImagenExcedeElTopeNoSeProbaElOtroCamino() {
        val api = ApiDosCaminos().also { it.original = { id -> throw ArchivoExcedeTope(id) } }
        try { BajadorConRespaldo(api) { CaminoObras.ORIGINAL_PRIMERO }.bajar("A", 3840, destino, 100); fail() } catch (e: ArchivoExcedeTope) {}
        assertEquals(listOf("original:A"), api.llamadas)
    }
}

class PausaPorCaminoTest {
    private lateinit var carpeta: File
    private lateinit var pausa: PausaDrive
    private var reloj = 9_000_000L
    private val registro = ArrayList<String>()
    private lateinit var base: ApiDosCaminos
    private lateinit var envuelta: DriveApiConPausa
    private lateinit var destino: File

    @Before
    fun preparar() {
        carpeta = Files.createTempDirectory("camino-pausa").toFile()
        reloj = 9_000_000L
        pausa = PausaDrive(File(carpeta, "p.txt")) { reloj }
        base = ApiDosCaminos()
        registro.clear()
        envuelta = DriveApiConPausa(base, pausa, registrar = { registro += it }, ahora = { reloj })
        destino = File(carpeta, "x.orig")
    }

    private fun bloqueo(id: String): Nothing = throw ErrorDrive(403, id, MotivoPausa.CONSULTAS_AUTOMATICAS)

    @Test
    fun siGoogleBloqueaElOriginalSePausaSoloElOriginalYLaCopiaSigue() {
        base.original = { bloqueo(it) }
        val b = BajadorConRespaldo(envuelta) { CaminoObras.ORIGINAL_PRIMERO }
        val via = b.bajar("A", 3840, destino, 100)
        assertEquals(CaminoDrive.COPIA, via.camino)
        assertEquals(reloj + 30 * MIN, pausa.hastaActivo(reloj, CaminoDrive.ORIGINAL))
        assertNull(pausa.hastaActivo(reloj, CaminoDrive.COPIA))
        // Las siguientes obras ni intentan el original.
        b.bajar("B", 3840, destino, 100)
        assertEquals(listOf("original:A", "copia:A:3840", "copia:B:3840"), base.llamadas)
        assertTrue(registro.single().startsWith("Drive pidió pausa hasta "))
    }

    @Test
    fun siGoogleBloqueaLaCopiaSePausaSoloLaCopiaYSeUsaElOriginal() {
        base.copia = { bloqueo(it) }
        val b = BajadorConRespaldo(envuelta) { CaminoObras.COPIA_PRIMERO }
        assertEquals(CaminoDrive.ORIGINAL, b.bajar("A", 3840, destino, 100).camino)
        assertEquals(reloj + 30 * MIN, pausa.hastaActivo(reloj, CaminoDrive.COPIA))
        assertNull(pausa.hastaActivo(reloj, CaminoDrive.ORIGINAL))
        assertTrue(registro.single().startsWith("Drive pidió pausa de la copia hasta "))
        b.bajar("B", 3840, destino, 100)
        assertEquals(listOf("copia:A:3840", "original:A", "original:B"), base.llamadas)
    }

    @Test
    fun el429DeLaCopiaTambienPausaLaCopia() {
        base.copia = { throw ErrorDrive(429, it, MotivoPausa.DEMASIADOS_PEDIDOS) }
        BajadorConRespaldo(envuelta).bajar("A", 3840, destino, 100)
        assertEquals(reloj + 30 * MIN, pausa.hastaActivo(reloj, CaminoDrive.COPIA))
    }

    @Test
    fun unaObraSinCopiaConElOriginalEnPausaEsperaSinSerError() {
        pausa.registrar(reloj, CaminoDrive.ORIGINAL)
        base.copia = { throw ErrorDrive(404, it) } // sin thumbnailLink
        try {
            BajadorConRespaldo(envuelta).bajar("A", 3840, destino, 100); fail()
        } catch (e: PausaDriveException) {
            assertEquals(CaminoDrive.ORIGINAL, e.camino)
            assertFalse("el otro camino existe: solo espera esta obra", e.todosEnPausa)
        }
        assertEquals("el original en pausa no salió a Drive", listOf("copia:A:3840"), base.llamadas)
    }

    @Test
    fun conLosDosCaminosEnPausaNoSaleNingunPedido() {
        pausa.registrar(reloj, CaminoDrive.ORIGINAL)
        pausa.registrar(reloj, CaminoDrive.COPIA)
        assertTrue(pausa.todosEnPausa(reloj))
        try {
            BajadorConRespaldo(envuelta).bajar("A", 3840, destino, 100); fail()
        } catch (e: PausaDriveException) {
            assertTrue(e.todosEnPausa)
        }
        try { envuelta.listar("C"); fail() } catch (e: PausaDriveException) { assertEquals(CaminoDrive.ORIGINAL, e.camino) }
        assertTrue(base.llamadas.isEmpty())
    }

    @Test
    fun elListadoYLosJsonSonOriginalYLaPausaDeLaCopiaNoLosFrena() {
        pausa.registrar(reloj, CaminoDrive.COPIA)
        envuelta.listar("C")
        envuelta.bajar("J", destino, 100)
        assertEquals(listOf("original:J"), base.llamadas)
    }

    @Test
    fun unaPausaDelOriginalCortaElListadoYElJsonPeroNoLaCopia() {
        pausa.registrar(reloj, CaminoDrive.ORIGINAL)
        try { envuelta.listar("C"); fail() } catch (e: PausaDriveException) {}
        try { envuelta.bajar("J", destino, 100); fail() } catch (e: PausaDriveException) {}
        envuelta.bajarCopia("A", 480, destino, 100)
        assertEquals(listOf("copia:A:480"), base.llamadas)
    }

    @Test
    fun unaCopiaBajadaBienOlvidaLaEsperaDeLaCopia() {
        val h = pausa.registrar(reloj, CaminoDrive.COPIA)
        reloj = h + 1
        pausa.registrar(reloj, CaminoDrive.COPIA) // 60 min
        reloj += 61 * MIN
        envuelta.bajarCopia("A", 3840, destino, 100)
        assertEquals(30 * MIN, pausa.registrar(reloj, CaminoDrive.COPIA) - reloj)
    }

    @Test
    fun laReservaMarcaPausaSoloSiNoHayNingunCaminoYPausaEstaObraSiFaltaElOtro() {
        val dir = Files.createTempDirectory("desc-camino").toFile()
        val descargador = DescargadorDrive(envuelta, dir, ProcesadorFalso())
        // Solo el original en pausa y la obra sin copia -> espera esa obra.
        pausa.registrar(reloj, CaminoDrive.ORIGINAL)
        base.copia = { throw ErrorDrive(404, it) }
        assertEquals(ResultadoBajada.Falla(ErrorReserva.PAUSA_ESTA_OBRA, null), descargador.bajar(ObraRef("O1", "a.jpg")))
        // Con copia: baja y lo dice.
        base.copia = {}
        val r = descargador.bajar(ObraRef("O2", "b.jpg"))
        assertTrue(r is ResultadoBajada.Lista && r.via == "copia s3840")
        assertTrue(Catalogo.archivoObra(dir, "O2").isFile)
        // Los dos en pausa -> PAUSA (se corta la tanda).
        pausa.registrar(reloj, CaminoDrive.COPIA)
        assertEquals(ResultadoBajada.Falla(ErrorReserva.PAUSA, null), descargador.bajar(ObraRef("O3", "c.jpg")))
    }

    @Test
    fun conCalidad1080ElDescargadorPideLaCopiaDe1920() {
        val dir = Files.createTempDirectory("desc-1080").toFile()
        val r = DescargadorDrive(envuelta, dir, ProcesadorFalso(), lado = { UrlCopia.LADO_1080 }).bajar(ObraRef("O1", "a.jpg"))
        assertTrue(r is ResultadoBajada.Lista && r.via == "copia s1920")
        assertEquals(listOf("copia:O1:1920"), base.llamadas)
    }
}

class PausaDriveArchivoTest {
    private lateinit var archivo: File
    private var reloj = 1_000_000L

    @Before
    fun preparar() {
        archivo = File(Files.createTempDirectory("pausa-arch").toFile(), "pausa-drive.txt")
        reloj = 1_000_000L
    }

    private fun pausa() = PausaDrive(archivo) { reloj }

    @Test
    fun elArchivoViejoDeUnSoloCaminoSeLeeComoOriginal() {
        archivo.writeText("${reloj + 10 * MIN};${60 * MIN}")
        val p = pausa()
        assertEquals(reloj + 10 * MIN, p.hastaActivo(reloj, CaminoDrive.ORIGINAL))
        assertNull(p.hastaActivo(reloj, CaminoDrive.COPIA))
        assertFalse(p.todosEnPausa(reloj))
        // La espera vieja se conserva: la próxima del original es de 2 h.
        reloj += 11 * MIN
        assertEquals(120 * MIN, p.registrar(reloj, CaminoDrive.ORIGINAL) - reloj)
        // Y el archivo ya queda en el formato nuevo, sin perder nada.
        val otra = pausa()
        assertEquals(p.hastaActivo(reloj, CaminoDrive.ORIGINAL), otra.hastaActivo(reloj, CaminoDrive.ORIGINAL))
        assertTrue(archivo.readText().contains("original="))
    }

    @Test
    fun loDeUnCaminoNoPisaAlOtroNiSeMezclaAlGuardar() {
        val p = pausa()
        val o = p.registrar(reloj, CaminoDrive.ORIGINAL)
        reloj += MIN
        val c = p.registrar(reloj, CaminoDrive.COPIA)
        val otra = pausa()
        assertEquals(o, otra.hastaActivo(reloj, CaminoDrive.ORIGINAL))
        assertEquals(c, otra.hastaActivo(reloj, CaminoDrive.COPIA))
        assertTrue(otra.todosEnPausa(reloj))
        p.reiniciar(CaminoDrive.ORIGINAL)
        assertNull(p.hastaActivo(reloj, CaminoDrive.ORIGINAL))
        assertEquals(c, pausa().hastaActivo(reloj, CaminoDrive.COPIA))
    }

    @Test
    fun unArchivoNuevoRotoNoPausaNada() {
        archivo.writeText("original=basura\ncopia=;x\nmarciano=1;2")
        val p = pausa()
        assertNull(p.hastaActivo(reloj, CaminoDrive.ORIGINAL))
        assertNull(p.hastaActivo(reloj, CaminoDrive.COPIA))
    }
}

class AjusteCaminoObrasTest {
    @Test
    fun porDefectoLaCopiaPrimeroYSeGuardaYSeCambiaPorAdb() {
        assertEquals(CaminoObras.COPIA_PRIMERO, Preferencias().caminoObras)
        val guardado = CodecPreferencias.codificar(Preferencias(caminoObras = CaminoObras.ORIGINAL_PRIMERO))
        assertEquals("ORIGINAL_PRIMERO", guardado["camino_obras"])
        assertEquals(CaminoObras.ORIGINAL_PRIMERO, CodecPreferencias.decodificar(guardado).caminoObras)
        assertEquals(CaminoObras.COPIA_PRIMERO, CodecPreferencias.decodificar(mapOf("camino_obras" to "XX")).caminoObras)
        assertEquals(CaminoObras.COPIA_PRIMERO, CodecPreferencias.decodificar(emptyMap()).caminoObras)
        val o = PreferenciasDesdeTexto.aplicar(Preferencias(), mapOf("camino" to "original"))
        assertEquals(CaminoObras.ORIGINAL_PRIMERO, o.caminoObras)
        assertEquals(CaminoObras.COPIA_PRIMERO, PreferenciasDesdeTexto.aplicar(o, mapOf("camino" to "copia")).caminoObras)
        assertEquals(o, PreferenciasDesdeTexto.aplicar(o, mapOf("camino" to "otra-cosa")))
    }
}
