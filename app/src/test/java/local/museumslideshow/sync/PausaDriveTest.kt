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
        try { d.bajar("A", File("x"), 1) } catch (e: PausaDriveException) {}
        assertEquals(1, api.pedidos)
        repeat(3) {
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
        // La pausa de las descargas no corta: se lista todo, se escribe el índice y las fichas quedan esperando.
        assertTrue(r.ok)
        assertEquals(reloj + 30 * MIN, r.pausaHastaMs)
        assertEquals(23, r.fichasEsperando)
        assertTrue("sin problemas por archivo", r.problemas.isEmpty())
        assertEquals("24 listados + 1 descarga que dio la pausa, ni uno más", 24 + 1, pedidos)
        assertEquals(0, (File(base, "tmp").listFiles()?.size ?: 0))
        val c = Catalogo.cargar(base)
        assertEquals(23, c.galerias.size)
        assertEquals(reloj, c.ultimoIntento)

        // "Sincronizar ahora" durante la pausa: lista de nuevo, pero no pide ninguna descarga.
        val antes = pedidos
        val r2 = s.sincronizar("RAIZ")
        assertEquals(r.pausaHastaMs, r2.pausaHastaMs)
        assertEquals(antes + 24, pedidos)
        assertEquals(23, r2.fichasEsperando)
    }

    // --- El listado tiene su propia pausa

    private fun escenarioDe(vararg ids: Int): ApiFalsa {
        val falsa = ApiFalsa()
        falsa.carpetas["RAIZ"] = ids.map { carpeta("G$it", "Galería $it") }
        for (i in ids) {
            falsa.carpetas["G$i"] = listOf(json("J$i"), ArchivoDrive("A$i", "a.jpg", "image/jpeg", "ma$i", "t", 500, 40, 30))
            falsa.contenido["J$i"] = """{"formato":1,"titulo":"Titulo $i","obras":[{"archivo":"a.jpg"}]}""".toByteArray()
            falsa.contenido["A$i"] = ByteArray(10)
        }
        return falsa
    }

    private fun sincronizador(api: DriveApi) = Sincronizador(DriveApiConPausa(api, pausa, ahora = { reloj }), base, ProcesadorFalso()) { reloj }

    @Test
    fun conLosOriginalesEnPausaIgualListaAnotaGaleriasNuevasYEscribeElIndice() {
        pausa.registrar(reloj, CaminoDrive.ORIGINAL)
        val falsa = escenarioDe(1)
        val r = sincronizador(falsa).sincronizar("RAIZ")
        assertTrue(r.ok)
        assertEquals(1, r.fichasEsperando)
        assertEquals(reloj + 30 * MIN, r.pausaHastaMs)
        assertTrue(falsa.bajadas.isEmpty()) // ni una descarga original
        val c = Catalogo.cargar(base)
        assertEquals(1, c.galerias.size)
        assertEquals(1, c.galerias[0].obras.size) // la obra nueva ya figura
        assertEquals(reloj, c.ultimoIntento)
    }

    @Test
    fun unaFichaCambiadaQueRecibePausaConservaLaViejaYSigueConLasDemasGalerias() {
        val falsa = escenarioDe(1, 2)
        sincronizador(falsa).sincronizar("RAIZ")
        assertEquals("Titulo 1", Catalogo.cargar(base).galerias[0].titulo)

        // En Drive cambian las dos fichas y las galerias nueva; las descargas del original quedan en pausa.
        falsa.carpetas["G1"] = listOf(
            ArchivoDrive("J1", "galeria.json", "application/json", "NUEVO1", "t", 50),
            ArchivoDrive("A1", "a.jpg", "image/jpeg", "ma1", "t", 500, 40, 30),
        )
        falsa.carpetas["G2"] = listOf(
            ArchivoDrive("J2", "galeria.json", "application/json", "NUEVO2", "t", 50),
            ArchivoDrive("A2", "a.jpg", "image/jpeg", "ma2", "t", 500, 40, 30),
            ArchivoDrive("A2b", "b.jpg", "image/jpeg", "mb2", "t", 500, 40, 30),
        )
        falsa.contenido["A2b"] = ByteArray(10)
        falsa.bajadas.clear()
        reloj += 1000
        val hasta = pausa.registrar(reloj, CaminoDrive.ORIGINAL)
        val r = sincronizador(falsa).sincronizar("RAIZ")
        assertTrue(r.ok)
        assertEquals(2, r.fichasEsperando)
        assertEquals(hasta, r.pausaHastaMs)
        assertTrue(File(base, "json/J1.json").isFile && File(base, "json/J2.json").isFile) // quedaron las viejas
        val c = Catalogo.cargar(base)
        assertEquals("Titulo 1", c.galerias[0].titulo)
        assertEquals("la galería 2 siguió: ya figura la obra nueva", 2, c.galerias[1].obras.size)
        assertEquals(reloj, c.ultimaSincronizacionBuena)

        // Cuando Google deja, se bajan las dos fichas (el índice se acordó del md5 viejo).
        reloj = hasta + 1
        val r2 = sincronizador(falsa).sincronizar("RAIZ")
        assertEquals(0, r2.fichasEsperando)
        assertNull(r2.pausaHastaMs)
        assertTrue(falsa.bajadas.containsAll(listOf("J1", "J2")))
    }

    @Test
    fun unaFichaConElMismoMd5NoSeBajaAunqueNoHayaPausa() {
        val falsa = escenarioDe(1)
        sincronizador(falsa).sincronizar("RAIZ")
        falsa.bajadas.clear()
        val r = sincronizador(falsa).sincronizar("RAIZ")
        assertFalse("J1" in falsa.bajadas)
        assertEquals(0, r.fichasEsperando)
    }

    @Test
    fun conLosOriginalesEnPausaUnaFichaConElMismoMd5NiSiquieraEsperaNada() {
        val falsa = escenarioDe(1)
        sincronizador(falsa).sincronizar("RAIZ")
        pausa.registrar(reloj, CaminoDrive.ORIGINAL)
        val r = sincronizador(falsa).sincronizar("RAIZ")
        assertTrue(r.ok)
        assertEquals(0, r.fichasEsperando)
        assertNull(r.pausaHastaMs)
    }

    @Test
    fun laPausaAlListarLaRaizCortaComoAntesYNoTocaElIndice() {
        val falsa = escenarioDe(1)
        sincronizador(falsa).sincronizar("RAIZ")
        val antes = Catalogo.cargar(base)
        pausa.registrar(reloj, CaminoDrive.LISTADO)
        reloj += 1000
        val r = sincronizador(falsa).sincronizar("RAIZ")
        assertFalse(r.ok)
        assertEquals(pausa.hastaActivo(camino = CaminoDrive.LISTADO), r.pausaHastaMs)
        assertEquals(0, r.fichasEsperando)
        val c = Catalogo.cargar(base)
        assertEquals(antes.ultimoIntento, c.ultimoIntento)
        assertEquals(1, c.galerias.size)
    }

    @Test
    fun laPausaAlListarUnaSubcarpetaConservaEseSubarbol() {
        val falsa = escenarioDe(1, 2)
        sincronizador(falsa).sincronizar("RAIZ")
        val api = object : DriveApi {
            override fun listar(carpetaId: String): List<ArchivoDrive> {
                if (carpetaId == "G2") throw ErrorDrive(403, carpetaId, MotivoPausa.CONSULTAS_AUTOMATICAS)
                return falsa.listar(carpetaId)
            }
            override fun bajar(archivoId: String, destino: File, maxBytes: Long) = falsa.bajar(archivoId, destino, maxBytes)
        }
        reloj += 1000
        val r = sincronizador(api).sincronizar("RAIZ")
        assertFalse("no cuenta como sincronización buena", r.ok)
        assertEquals(reloj + 30 * MIN, r.pausaHastaMs)
        assertEquals(2, Catalogo.cargar(base).galerias.size)
    }

    // --- Archivo de pausas

    @Test
    fun laLineaListadoSeGuardaSeLeeYElFormatoViejoSigueAndando() {
        val archivoPausa = File(Files.createTempDirectory("pausa-listado").toFile(), "p.txt")
        val p = PausaDrive(archivoPausa) { reloj }
        val hasta = p.registrar(reloj, CaminoDrive.LISTADO)
        assertTrue(archivoPausa.readLines().any { it == "listado=$hasta;${30 * MIN}" })
        val otra = PausaDrive(archivoPausa) { reloj }
        assertEquals(hasta, otra.hastaActivo(camino = CaminoDrive.LISTADO))
        assertNull(otra.hastaActivo(camino = CaminoDrive.ORIGINAL))

        archivoPausa.writeText("${reloj + 5 * MIN};${30 * MIN}") // formato de un solo camino
        val vieja = PausaDrive(archivoPausa) { reloj }
        assertEquals(reloj + 5 * MIN, vieja.hastaActivo(camino = CaminoDrive.ORIGINAL))
        assertNull(vieja.hastaActivo(camino = CaminoDrive.LISTADO))

        archivoPausa.writeText("original=${reloj + 5 * MIN};1\ncopia=0;0") // sin línea de listado
        assertNull(PausaDrive(archivoPausa) { reloj }.hastaActivo(camino = CaminoDrive.LISTADO))
    }

    @Test
    fun todosEnPausaIgnoraElListado() {
        pausa.registrar(reloj, CaminoDrive.LISTADO)
        pausa.registrar(reloj, CaminoDrive.ORIGINAL)
        assertFalse(pausa.todosEnPausa(reloj))
        pausa.registrar(reloj, CaminoDrive.COPIA)
        assertTrue(pausa.todosEnPausa(reloj))
        val sinListado = PausaDrive(File(Files.createTempDirectory("p2").toFile(), "p.txt")) { reloj }
        sinListado.registrar(reloj, CaminoDrive.ORIGINAL)
        sinListado.registrar(reloj, CaminoDrive.COPIA)
        assertTrue(sinListado.todosEnPausa(reloj))
    }

    @Test
    fun sinPausaElResultadoNoTrae() {
        val falsa = ApiFalsa()
        falsa.carpetas["RAIZ"] = emptyList()
        val r = Sincronizador(DriveApiConPausa(falsa, pausa, ahora = { reloj }), base, ProcesadorFalso()) { reloj }.sincronizar("RAIZ")
        assertNull(r.pausaHastaMs)
    }
}
