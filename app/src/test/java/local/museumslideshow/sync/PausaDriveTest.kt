package local.museumslideshow.sync

import java.io.File
import java.nio.file.Files
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

class ClasificadorRespuestaDriveTest {
    private fun c(codigo: Int, cuerpo: String?) = ClasificadorRespuestaDrive.clasificar(codigo, cuerpo)

    @Test
    fun el429SiemprePideUnaPausa() {
        assertEquals(MotivoPausa.DEMASIADOS_PEDIDOS, c(429, null))
        assertEquals(MotivoPausa.DEMASIADOS_PEDIDOS, c(429, "{}"))
    }

    @Test
    fun el403ConLaPaginaDeConsultasAutomaticasPideUnaPausa() {
        val html = "<!DOCTYPE html><html><body><b>Sorry...</b> your computer or network may be sending automated queries.</body></html>"
        assertEquals(MotivoPausa.CONSULTAS_AUTOMATICAS, c(403, html))
        assertEquals(MotivoPausa.CONSULTAS_AUTOMATICAS, c(403, "  <html><body>Sorry</body></html>"))
        assertEquals(MotivoPausa.CONSULTAS_AUTOMATICAS, c(403, "<html>Unusual traffic from automated queries</html>"))
    }

    @Test
    fun el403ConJsonDeCuotaPideUnaPausa() {
        fun json(razon: String) = """{"error":{"code":403,"message":"x","errors":[{"domain":"usageLimits","reason":"$razon"}]}}"""
        assertEquals(MotivoPausa.LIMITE_DE_PEDIDOS, c(403, json("rateLimitExceeded")))
        assertEquals(MotivoPausa.LIMITE_DE_PEDIDOS, c(403, json("userRateLimitExceeded")))
        assertEquals(MotivoPausa.CUOTA, c(403, json("dailyLimitExceeded")))
        assertEquals(MotivoPausa.CUOTA, c(403, json("quotaExceeded")))
        assertEquals(MotivoPausa.CUOTA_DE_DESCARGA, c(403, json("downloadQuotaExceeded")))
    }

    @Test
    fun unPermisoDeVerdadSigueSiendoUnProblemaNormal() {
        fun json(razon: String) = """{"error":{"code":403,"errors":[{"reason":"$razon"}]}}"""
        assertNull(c(403, json("forbidden")))
        assertNull(c(403, json("insufficientFilePermissions")))
        assertNull(c(403, json("cannotDownloadFile")))
        assertNull(c(403, null))
        assertNull(c(403, ""))
        assertNull(c(403, "Prohibido"))
        assertNull(c(403, "<html>No tenés permiso</html>"))
    }

    @Test
    fun otrosCodigosNoPidenPausa() {
        assertNull(c(404, "<html>Sorry</html>"))
        assertNull(c(500, null))
        assertNull(c(503, """{"error":{"errors":[{"reason":"rateLimitExceeded"}]}}"""))
    }
}

class PausaDriveTest {
    private lateinit var archivo: File
    private var reloj = 1_000_000L

    @Before
    fun preparar() {
        archivo = File(Files.createTempDirectory("pausa-test").toFile(), "pausa.txt")
        reloj = 1_000_000L
    }

    private fun pausa() = PausaDrive(archivo) { reloj }

    @Test
    fun sinPausaNoHayNada() {
        assertNull(pausa().hastaActivo())
    }

    @Test
    fun laPrimeraEsperaEs30MinutosYSeDuplicaEnPausasSeguidasConTopeDe6Horas() {
        val p = pausa()
        val esperas = ArrayList<Long>()
        repeat(8) {
            val hasta = p.registrar(reloj)
            esperas += hasta - reloj
            reloj = hasta + 1 // vence la pausa y vuelve a pasar lo mismo
        }
        assertEquals(listOf(30 * MIN, 60 * MIN, 120 * MIN, 240 * MIN, 360 * MIN, 360 * MIN, 360 * MIN, 360 * MIN), esperas)
    }

    @Test
    fun conPausaVigenteNoSeAlargaNiSeDuplica() {
        val p = pausa()
        val a = p.registrar(reloj)
        reloj += 5 * MIN
        assertEquals(a, p.registrar(reloj))
        assertEquals(a, p.hastaActivo())
        reloj = a
        assertNull("al vencer ya no está activa", p.hastaActivo())
    }

    @Test
    fun unaSincronizacionBuenaVuelveALos30Minutos() {
        val p = pausa()
        reloj = p.registrar(reloj) + 1
        reloj = p.registrar(reloj) + 1 // 60 min
        p.reiniciar()
        assertNull(p.hastaActivo())
        assertEquals(30 * MIN, p.registrar(reloj) - reloj)
    }

    @Test
    fun laPausaSobreviveAUnReinicioDelProceso() {
        val hasta = pausa().registrar(reloj)
        val otra = pausa() // instancia nueva leyendo el mismo archivo
        assertEquals(hasta, otra.hastaActivo())
        reloj = hasta + 1
        assertEquals("la espera también se recuerda", 60 * MIN, otra.registrar(reloj) - reloj)
    }

    @Test
    fun unArchivoRotoEsComoNoTenerPausa() {
        archivo.writeText("basura;más basura")
        assertNull(pausa().hastaActivo())
        assertEquals(30 * MIN, pausa().registrar(reloj) - reloj)
    }
}

private class ApiQueFalla(var respuesta: () -> Nothing) : DriveApi {
    var pedidos = 0
    override fun listar(carpetaId: String): List<ArchivoDrive> { pedidos++; respuesta() }
    override fun bajar(archivoId: String, destino: File, maxBytes: Long) { pedidos++; respuesta() }
    override fun bajarCopia(archivoId: String, lado: Int, destino: File, maxBytes: Long) { pedidos++; respuesta() }
}

class DriveApiConPausaTest {
    private lateinit var pausa: PausaDrive
    private var reloj = 5_000_000L
    private val registro = ArrayList<String>()
    private val avisos = ArrayList<Long>()
    private val esperas = ArrayList<Long>()

    @Before
    fun preparar() {
        pausa = PausaDrive(File(Files.createTempDirectory("api-pausa").toFile(), "p.txt")) { reloj }
        reloj = 5_000_000L
        registro.clear(); avisos.clear(); esperas.clear()
    }

    private fun envuelta(api: DriveApi, ritmo: Long = 0, turno: TurnoDescargas = TurnoDescargas()) =
        DriveApiConPausa(api, pausa, { ritmo }, { registro += it }, { avisos += it }, { reloj }, { esperas += it; reloj += it }, turno)

    @Test
    fun alRecibirLaPausaLaAnotaLaRegistraYAvisaParaElReintento() {
        val api = ApiQueFalla { throw ErrorDrive(403, "X1", MotivoPausa.CONSULTAS_AUTOMATICAS) }
        val e = try { envuelta(api).bajar("X1", File("x"), 10); null } catch (e: PausaDriveException) { e }
        assertEquals(reloj + 30 * MIN, e!!.hastaMs)
        assertEquals(MotivoPausa.CONSULTAS_AUTOMATICAS, e.motivo)
        assertEquals(listOf(reloj + 30 * MIN), avisos)
        assertEquals(1, registro.size)
        assertTrue(registro[0], registro[0].startsWith("Drive pidió pausa hasta ") && registro[0].endsWith("(motivo: consultas_automaticas)"))
        assertFalse(registro[0].contains("key="))
    }

    @Test
    fun conLaPausaVigenteNoSaleNiUnPedido() {
        val api = ApiQueFalla { throw ErrorDrive(403, "X1", MotivoPausa.DEMASIADOS_PEDIDOS) }
        val d = envuelta(api)
        try { d.listar("C") } catch (e: PausaDriveException) {}
        assertEquals(1, api.pedidos)
        repeat(3) {
            try { d.listar("C"); fail() } catch (e: PausaDriveException) {}
            try { d.bajar("A", File("x"), 1); fail() } catch (e: PausaDriveException) {}
        }
        assertEquals("ni un pedido más", 1, api.pedidos)
        assertEquals(1, registro.size)
    }

    @Test
    fun unErrorNormalPasaTalCualSinPausa() {
        val api = ApiQueFalla { throw ErrorDrive(403, "X1") }
        try { envuelta(api).bajar("X1", File("x"), 1); fail() } catch (e: ErrorDrive) { assertEquals(403, e.codigoHttp) }
        assertNull(pausa.hastaActivo())
        assertTrue(registro.isEmpty())
    }

    @Test
    fun alVencerLaPausaVuelveAPedir() {
        val api = ApiQueFalla { throw ErrorDrive(429, "X", MotivoPausa.DEMASIADOS_PEDIDOS) }
        val d = envuelta(api)
        try { d.listar("C") } catch (e: PausaDriveException) {}
        reloj += 31 * MIN
        try { d.listar("C") } catch (e: PausaDriveException) {}
        assertEquals(2, api.pedidos)
    }

    @Test
    fun bajaDeAUnaConUnRespiroEntreDescargas() {
        val bajadas = ArrayList<String>()
        val api = object : DriveApi {
            override fun listar(carpetaId: String) = emptyList<ArchivoDrive>()
            override fun bajar(archivoId: String, destino: File, maxBytes: Long) { bajadas += archivoId }
        }
        val d = envuelta(api, ritmo = 2000, turno = TurnoDescargas().also { it.ultimaTerminada = reloj })
        d.bajar("A", File("x"), 1)
        d.bajar("B", File("x"), 1)
        assertEquals(listOf("A", "B"), bajadas)
        assertEquals("espera 2 s antes de cada una (la anterior acaba de terminar)", listOf(2000L, 2000L), esperas)
    }

    @Test
    fun conRitmoNormalNoEspera() {
        val api = object : DriveApi {
            override fun listar(carpetaId: String) = emptyList<ArchivoDrive>()
            override fun bajar(archivoId: String, destino: File, maxBytes: Long) {}
        }
        val d = envuelta(api, ritmo = 0)
        d.bajar("A", File("x"), 1); d.bajar("B", File("x"), 1)
        assertTrue(esperas.isEmpty())
    }

    @Test
    fun laReservaNoBajaYLaPausaNoEsUnErrorDeInternetNiDeDrive() {
        val dir = Files.createTempDirectory("desc").toFile()
        val api = ApiQueFalla { throw ErrorDrive(403, "X", MotivoPausa.CONSULTAS_AUTOMATICAS) }
        val descargador = DescargadorDrive(envuelta(api), dir, ProcesadorFalso())
        val r = descargador.bajar(ObraRef("OBRA1", "a.jpg"))
        assertEquals(ResultadoBajada.Falla(ErrorReserva.PAUSA, null), r)
        val r2 = descargador.bajar(ObraRef("OBRA2", "b.jpg"))
        assertEquals(ResultadoBajada.Falla(ErrorReserva.PAUSA, null), r2)
        assertEquals("el segundo ni llegó a Drive (la primera probó copia y original)", 2, api.pedidos)
        assertFalse(Catalogo.archivoObra(dir, "OBRA1").exists())
    }
}

class SincronizacionEnPausaTest {
    private lateinit var base: File
    private lateinit var pausa: PausaDrive
    private var reloj = 7_000_000L

    @Before
    fun preparar() {
        val padre = Files.createTempDirectory("sync-pausa").toFile()
        base = File(padre, "galerias")
        pausa = PausaDrive(File(padre, "pausa.txt")) { reloj }
        reloj = 7_000_000L
    }

    private fun carpeta(id: String, nombre: String) = ArchivoDrive(id, nombre, ArchivoDrive.MIME_CARPETA, null, null, null)
    private fun json(id: String) = ArchivoDrive(id, "galeria.json", "application/json", "m$id", "t", 50)

    @Test
    fun alPrimerPedidoEnPausaCortaSinAnotarProblemasPorArchivo() {
        val falsa = ApiFalsa()
        falsa.carpetas["RAIZ"] = (1..23).map { carpeta("G$it", "Galería $it") }
        for (i in 1..23) {
            falsa.carpetas["G$i"] = listOf(json("J$i"), ArchivoDrive("A$i", "a.jpg", "image/jpeg", "ma$i", "t", 500, 40, 30))
            falsa.contenido["J$i"] = """{"formato":1,"titulo":"G$i","obras":[{"archivo":"a.jpg"}]}""".toByteArray()
            falsa.contenido["A$i"] = ByteArray(10)
        }
        // Drive acepta el listado (24 pedidos) pero la primera descarga recibe la página de "automated queries".
        var pedidos = 0
        val api = object : DriveApi {
            override fun listar(carpetaId: String): List<ArchivoDrive> { pedidos++; return falsa.listar(carpetaId) }
            override fun bajar(archivoId: String, destino: File, maxBytes: Long) {
                pedidos++
                throw ErrorDrive(403, archivoId, MotivoPausa.CONSULTAS_AUTOMATICAS)
            }
        }
        val s = Sincronizador(DriveApiConPausa(api, pausa, ahora = { reloj }), base, ProcesadorFalso()) { reloj }
        val r = s.sincronizar("RAIZ")
        assertFalse(r.ok)
        assertEquals(reloj + 30 * MIN, r.pausaHastaMs)
        assertTrue("sin problemas por archivo", r.problemas.isEmpty())
        assertEquals("24 listados + 1 descarga que dio la pausa, ni uno más", 24 + 1, pedidos)
        assertEquals(0, (File(base, "tmp").listFiles()?.size ?: 0))
        val c = Catalogo.cargar(base)
        assertTrue("el índice no se tocó", c.problemas.isEmpty() && c.ultimoIntento == null)

        // "Sincronizar ahora" durante la pausa: no pide nada.
        val antes = pedidos
        val r2 = s.sincronizar("RAIZ")
        assertEquals(r.pausaHastaMs, r2.pausaHastaMs)
        assertEquals(antes, pedidos)
        assertTrue(r2.problemas.isEmpty())
    }

    @Test
    fun sinPausaElResultadoNoTrae() {
        val falsa = ApiFalsa()
        falsa.carpetas["RAIZ"] = emptyList()
        val r = Sincronizador(DriveApiConPausa(falsa, pausa, ahora = { reloj }), base, ProcesadorFalso()) { reloj }.sincronizar("RAIZ")
        assertNull(r.pausaHastaMs)
    }
}
