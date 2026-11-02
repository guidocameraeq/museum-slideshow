package local.museumslideshow.sync

import java.io.File
import java.nio.file.Files
import local.museumslideshow.catalogo.Catalogo
import local.museumslideshow.catalogo.TipoProblema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val CARPETA = "RAIZ"
private const val MIME_JPG = "image/jpeg"

class ApiFalsa : DriveApi {
    override var disponible = true
    val carpetas = HashMap<String, List<ArchivoDrive>>()
    val contenido = HashMap<String, ByteArray>()
    val falla = HashSet<String>() // ids de carpeta o archivo que fallan
    var bajadas = ArrayList<String>()
    var listadas = ArrayList<String>()

    override fun listar(carpetaId: String): List<ArchivoDrive> {
        listadas += carpetaId
        if (carpetaId in falla) throw ErrorDrive(503, carpetaId)
        return carpetas[carpetaId] ?: emptyList()
    }

    override fun bajar(archivoId: String, destino: File, maxBytes: Long) {
        if (archivoId in falla) throw ErrorDrive(500, archivoId)
        val b = contenido[archivoId] ?: throw ErrorDrive(404, archivoId)
        if (b.size > maxBytes) throw ArchivoExcedeTope(archivoId)
        bajadas += archivoId
        destino.writeBytes(b)
    }
}

class ProcesadorFalso(private val bytesCopia: Int = 100) : ProcesadorImagen {
    override fun procesar(origen: File, destino: File, destinoChica: File): Medidas? {
        if (origen.readText() == "MALA") return null
        destino.writeBytes(origen.readBytes().copyOf(bytesCopia))
        destinoChica.writeBytes(ByteArray(10))
        return Medidas(40, 30)
    }

    override fun tapa(origen: File, destino: File, ladoLargo: Int): Boolean {
        if (origen.readText() == "MALA") return false
        destino.writeBytes(origen.readBytes().copyOf(20))
        return true
    }
}

class SincronizadorTest {
    private lateinit var padre: File
    private lateinit var base: File
    private lateinit var api: ApiFalsa
    private var reloj = 1000L

    @Before
    fun preparar() {
        padre = Files.createTempDirectory("sync-test").toFile()
        base = File(padre, "galerias")
        api = ApiFalsa()
        reloj = 1000L
    }

    private fun sync(procesador: ProcesadorImagen = ProcesadorFalso()) =
        Sincronizador(api, base, procesador) { reloj }.sincronizar(CARPETA)

    private fun carpeta(id: String, nombre: String) = ArchivoDrive(id, nombre, ArchivoDrive.MIME_CARPETA, null, null, null)
    private fun imagen(id: String, nombre: String, md5: String, tam: Long? = 500, ancho: Int? = 4000, alto: Int? = 3000, rot: Int? = null) =
        ArchivoDrive(id, nombre, MIME_JPG, md5, "t", tam, ancho, alto, rot)
    private fun json(id: String, md5: String = "mj", tam: Long? = 50) = ArchivoDrive(id, "galeria.json", "application/json", md5, "t", tam)

    /** Pone una copia ya bajada (como la dejaría la reserva). */
    private fun copiaBajada(id: String) {
        File(base, "obras").mkdirs()
        File(base, "obras/$id.jpg").writeBytes(ByteArray(100))
        File(base, "obras/$id.chica.jpg").writeBytes(ByteArray(10))
    }

    /** Una galería "G1" con galeria.json y dos imágenes. */
    private fun escenarioBasico() {
        api.carpetas[CARPETA] = listOf(carpeta("G1", "Impresionismo"))
        api.carpetas["G1"] = listOf(json("J1"), imagen("A1", "a.jpg", "ma"), imagen("B1", "b.jpg", "mb"))
        api.contenido["J1"] = """{"formato":1,"titulo":"Impre","obras":[{"archivo":"b.jpg","titulo":"Bé"},{"archivo":"a.jpg","titulo":"Á"}]}""".toByteArray()
        api.contenido["A1"] = ByteArray(500) { 1 }
        api.contenido["B1"] = ByteArray(500) { 2 }
    }

    @Test
    fun sincronizaElCatalogoYNoBajaLasObras() {
        escenarioBasico()
        val r = sync()
        assertTrue(r.problemas.toString(), r.problemas.isEmpty())
        // solo el galeria.json y la tapa (la primera obra según el JSON: B1)
        assertEquals(listOf("J1", "B1"), api.bajadas)
        assertEquals(2, r.bajadas)
        assertFalse(File(base, "obras/A1.jpg").exists())
        assertFalse(File(base, "obras/B1.jpg").exists())
        assertTrue(File(base, "json/J1.json").isFile)
        assertTrue(File(base, "tapas/G1.jpg").isFile)
        assertEquals(0, (File(base, "tmp").listFiles()?.size ?: 0))

        val c = Catalogo.cargar(base)
        assertEquals(1, c.galerias.size)
        assertEquals("Impre", c.galerias[0].titulo)
        assertEquals(listOf("b.jpg", "a.jpg"), c.galerias[0].obras.map { it.archivo })
        assertEquals("Bé", c.galerias[0].obras[0].ficha!!.titulo)
        // la obra existe en el catálogo aunque no esté bajada, con las medidas de Drive
        assertEquals(4000, c.galerias[0].obras[0].ancho)
        assertEquals(3000, c.galerias[0].obras[0].alto)
        assertTrue(c.galerias[0].obras.none { it.bajada })
        assertEquals(1000L, c.ultimaSincronizacionBuena)
    }

    @Test
    fun medidasDeDriveConRotacionIntercambianAnchoYAlto() {
        api.carpetas[CARPETA] = listOf(carpeta("G1", "X"))
        api.carpetas["G1"] = listOf(
            imagen("R90", "a.jpg", "m1", ancho = 4000, alto = 3000, rot = 90),
            imagen("R270", "b.jpg", "m2", ancho = 4000, alto = 3000, rot = 270),
            imagen("R180", "c.jpg", "m3", ancho = 4000, alto = 3000, rot = 180),
            imagen("SIN", "d.jpg", "m4", ancho = null, alto = null),
        )
        for (id in listOf("R90", "R270", "R180", "SIN")) api.contenido[id] = ByteArray(10)
        sync()
        val o = Catalogo.cargar(base).galerias[0].obras.associateBy { it.driveId }
        assertEquals(3000 to 4000, o["R90"]!!.ancho to o["R90"]!!.alto)
        assertEquals(3000 to 4000, o["R270"]!!.ancho to o["R270"]!!.alto)
        assertEquals(4000 to 3000, o["R180"]!!.ancho to o["R180"]!!.alto)
        assertEquals(0 to 0, o["SIN"]!!.ancho to o["SIN"]!!.alto)
        assertFalse(o["SIN"]!!.conMedidas)
    }

    @Test
    fun sinMedidasDeDriveUsaLasDeLaCopiaSiEstaBajada() {
        api.carpetas[CARPETA] = listOf(carpeta("G1", "X"))
        api.carpetas["G1"] = listOf(imagen("SIN", "d.jpg", "m4", ancho = null, alto = null))
        api.contenido["SIN"] = ByteArray(10)
        sync()
        assertFalse(Catalogo.cargar(base).galerias[0].obras[0].conMedidas)
        copiaBajada("SIN")
        val c = Catalogo.cargar(base) { f -> if (f.name == "SIN.jpg") 1920 to 1080 else null }
        val o = c.galerias[0].obras[0]
        assertTrue(o.bajada)
        assertEquals(1920 to 1080, o.ancho to o.alto)
    }

    @Test
    fun noRebajaLoIgualNiLaTapa() {
        escenarioBasico()
        sync()
        api.bajadas.clear()
        reloj = 2000L
        val r = sync()
        assertEquals(0, r.bajadas)
        assertTrue(api.bajadas.isEmpty())
        assertEquals(2000L, Catalogo.cargar(base).ultimaSincronizacionBuena)
        assertEquals(2, Catalogo.cargar(base).galerias[0].obras.size)
    }

    @Test
    fun cuandoCambiaLaObraDeLaTapaSeRehaceLaTapa() {
        escenarioBasico()
        sync()
        api.bajadas.clear()
        api.carpetas["G1"] = listOf(json("J1"), imagen("A1", "a.jpg", "ma"), imagen("B1", "b.jpg", "mb-NUEVO"))
        val r = sync()
        assertEquals(listOf("B1"), api.bajadas)
        assertEquals(1, r.bajadas)
    }

    @Test
    fun cuandoCambiaOtraObraNoSeTocaLaTapa() {
        escenarioBasico()
        sync()
        api.bajadas.clear()
        api.carpetas["G1"] = listOf(json("J1"), imagen("A1", "a.jpg", "ma-NUEVO"), imagen("B1", "b.jpg", "mb"))
        val r = sync()
        assertTrue(api.bajadas.isEmpty())
        assertEquals(0, r.bajadas)
    }

    @Test
    fun laCopiaBajadaQueCambioEnDriveSeBorra() {
        escenarioBasico()
        sync()
        copiaBajada("A1")
        copiaBajada("B1")
        api.carpetas["G1"] = listOf(json("J1"), imagen("A1", "a.jpg", "ma-NUEVO"), imagen("B1", "b.jpg", "mb"))
        val r = sync()
        assertFalse(File(base, "obras/A1.jpg").exists()) // la copia vieja ya no vale: la reserva baja la nueva
        assertFalse(File(base, "obras/A1.chica.jpg").exists())
        assertTrue(File(base, "obras/B1.jpg").isFile) // la que no cambió se reusa
        assertTrue(r.borradas >= 1)
    }

    @Test
    fun laTapaSeHaceDeLaCopiaBajadaSinVolverABajarla() {
        escenarioBasico()
        sync() // primera vez: sin copia, baja el original de B1
        File(base, "tapas/G1.jpg").delete()
        copiaBajada("B1")
        // el índice todavía tiene la tapa anotada, pero el archivo no está: se rehace desde la copia local
        api.bajadas.clear()
        sync()
        assertTrue(api.bajadas.isEmpty())
        assertTrue(File(base, "tapas/G1.jpg").isFile)
    }

    @Test
    fun sinMd5ComparaPorModificado() {
        val sinMd5 = ArchivoDrive("A1", "a.jpg", MIME_JPG, null, "v1", 500, 100, 100, null)
        api.carpetas[CARPETA] = listOf(carpeta("G1", "X"))
        api.carpetas["G1"] = listOf(sinMd5)
        api.contenido["A1"] = ByteArray(10)
        sync()
        copiaBajada("A1")
        api.bajadas.clear()
        sync()
        assertTrue(File(base, "obras/A1.jpg").isFile)
        assertTrue(api.bajadas.isEmpty())
        api.carpetas["G1"] = listOf(sinMd5.copy(modificado = "v2"))
        sync()
        assertFalse(File(base, "obras/A1.jpg").exists())
        assertEquals(listOf("A1"), api.bajadas) // la tapa se rehace de la obra nueva
    }

    @Test
    fun borraLoQueSeBorroEnDriveObrasJsonTapasYGaleriaEntera() {
        escenarioBasico()
        api.carpetas[CARPETA] = listOf(carpeta("G1", "Impresionismo"), carpeta("G2", "Otra"))
        api.carpetas["G2"] = listOf(imagen("C1", "c.jpg", "mc"))
        api.contenido["C1"] = ByteArray(500)
        sync()
        copiaBajada("A1"); copiaBajada("B1"); copiaBajada("C1")
        assertTrue(File(base, "tapas/G2.jpg").isFile)

        api.carpetas["G1"] = listOf(json("J1"), imagen("A1", "a.jpg", "ma")) // B1 ya no está
        api.carpetas[CARPETA] = listOf(carpeta("G1", "Impresionismo")) // G2 ya no está
        val r = sync()
        assertFalse(File(base, "obras/B1.jpg").exists())
        assertFalse(File(base, "obras/B1.chica.jpg").exists())
        assertFalse(File(base, "obras/C1.jpg").exists())
        assertFalse(File(base, "tapas/G2.jpg").exists())
        assertTrue(File(base, "obras/A1.jpg").isFile)
        assertTrue(r.borradas >= 3)
        assertEquals(1, Catalogo.cargar(base).galerias.size)
    }

    @Test
    fun siSeVaElJsonSeBorraYLaGaleriaVuelveAOrdenPorNombre() {
        escenarioBasico()
        sync()
        api.carpetas["G1"] = listOf(imagen("A1", "a.jpg", "ma"), imagen("B1", "b.jpg", "mb"))
        sync()
        assertFalse(File(base, "json/J1.json").exists())
        val c = Catalogo.cargar(base)
        assertEquals("Impresionismo", c.galerias[0].titulo)
        assertEquals(listOf("a.jpg", "b.jpg"), c.galerias[0].obras.map { it.archivo })
    }

    @Test
    fun salteaMasDe50MbYNoImagenesYJsonGrande() {
        api.carpetas[CARPETA] = listOf(carpeta("G1", "Mix"))
        api.carpetas["G1"] = listOf(
            imagen("GRANDE", "grande.jpg", "m1", tam = 51L * 1024 * 1024),
            ArchivoDrive("PDF", "nota.pdf", "application/pdf", "m2", "t", 100),
            json("JG", tam = 2L * 1024 * 1024),
            imagen("OK", "ok.jpg", "m3"),
        )
        api.contenido["OK"] = ByteArray(500)
        val r = sync()
        val tipos = r.problemas.map { it.tipo }.toSet()
        assertEquals(setOf(TipoProblema.IMAGEN_MUY_GRANDE, TipoProblema.NO_ES_IMAGEN, TipoProblema.JSON_MUY_GRANDE), tipos)
        assertEquals(listOf("OK"), api.bajadas) // la tapa
        assertEquals(listOf("ok.jpg"), Catalogo.cargar(base).galerias[0].obras.map { it.archivo })
        // los problemas del sincronizador llegan al catálogo
        assertEquals(3, Catalogo.cargar(base).problemas.size)
    }

    @Test
    fun jsonQueExcedeElTopeMientrasBajaSeSaltea() {
        api.carpetas[CARPETA] = listOf(carpeta("G1", "X"))
        api.carpetas["G1"] = listOf(json("JG", tam = null), imagen("I", "i.jpg", "m"))
        api.contenido["JG"] = ByteArray(1_100_000)
        api.contenido["I"] = ByteArray(10)
        val r = sync()
        assertEquals(listOf(TipoProblema.JSON_MUY_GRANDE), r.problemas.map { it.tipo })
        assertFalse(File(base, "json/JG.json").exists())
    }

    @Test
    fun tapaQueNoDecodificaSeAnotaYLaObraSigueEnElCatalogo() {
        api.carpetas[CARPETA] = listOf(carpeta("G1", "X"))
        api.carpetas["G1"] = listOf(imagen("ROTA", "rota.jpg", "m"))
        api.contenido["ROTA"] = "MALA".toByteArray()
        val r = sync()
        assertEquals(listOf(TipoProblema.IMAGEN_NO_DECODIFICA), r.problemas.map { it.tipo })
        assertFalse(File(base, "tapas/G1.jpg").exists())
        assertEquals(0, (File(base, "tmp").listFiles()?.size ?: 0))
        assertEquals(1, Catalogo.cargar(base).galerias[0].obras.size)
    }

    @Test
    fun fallaAlListarLaRaizNoTocaGaleriasNiAvanzaLaHoraBuenaPeroQuedaAnotada() {
        escenarioBasico()
        sync()
        copiaBajada("A1")
        val antes = Catalogo.leerIndice(base)
        api.falla += CARPETA
        reloj = 9999L
        val r = sync()
        assertFalse(r.ok)
        assertEquals(listOf(TipoProblema.ERROR_RED), r.problemas.map { it.tipo })
        assertEquals("503", r.problemas[0].detalle)
        val despues = Catalogo.leerIndice(base)
        assertEquals(antes.galerias, despues.galerias)
        assertEquals(antes.problemas, despues.problemas)
        assertEquals(1000L, despues.ultimaSincronizacionBuena)
        assertTrue(File(base, "obras/A1.jpg").isFile)
        // lo nuevo: el problema y la hora del intento llegan a Estado
        val c = Catalogo.cargar(base)
        assertEquals(1000L, c.ultimaSincronizacionBuena)
        assertEquals(9999L, c.ultimoIntento)
        assertEquals(false, c.ultimoIntentoOk)
        assertEquals(TipoProblema.ERROR_RED, c.problemaUltimoIntento!!.tipo)
        assertEquals("503", c.problemaUltimoIntento!!.detalle)
        assertEquals(2, c.galerias[0].obras.size)
        assertTrue(c.problemas.isEmpty())
    }

    @Test
    fun unaSincronizacionBuenaDespuesDeUnaFallidaLimpiaElProblemaDelIntento() {
        escenarioBasico()
        sync()
        api.falla += CARPETA
        sync()
        api.falla.clear()
        reloj = 20000L
        sync()
        val c = Catalogo.cargar(base)
        assertNull(c.problemaUltimoIntento)
        assertEquals(true, c.ultimoIntentoOk)
        assertEquals(20000L, c.ultimoIntento)
        assertEquals(20000L, c.ultimaSincronizacionBuena)
    }

    @Test
    fun sinClaveTambienQuedaAnotadoComoIntento() {
        escenarioBasico()
        api.disponible = false
        reloj = 777L
        sync()
        val c = Catalogo.cargar(base)
        assertEquals(TipoProblema.SIN_CLAVE, c.problemaUltimoIntento!!.tipo)
        assertEquals(777L, c.ultimoIntento)
        assertNull(c.ultimaSincronizacionBuena)
    }

    @Test
    fun fallaLaBajadaDelJsonConservaElViejo() {
        escenarioBasico()
        sync()
        api.carpetas["G1"] = listOf(json("J1", md5 = "mj-NUEVO"), imagen("A1", "a.jpg", "ma"), imagen("B1", "b.jpg", "mb"))
        api.falla += "J1"
        reloj = 5000L
        val r = sync()
        assertEquals(listOf(TipoProblema.ERROR_RED), r.problemas.map { it.tipo })
        assertTrue(File(base, "json/J1.json").isFile)
        val c = Catalogo.cargar(base)
        assertEquals("Impre", c.galerias[0].titulo)
        assertEquals(1000L, c.ultimaSincronizacionBuena) // no fue una sincronización buena
        api.falla.clear()
        assertTrue(sync().ok)
    }

    @Test
    fun falloElListadoDeUnaGaleriaConservaLoViejoDeEsaGaleria() {
        escenarioBasico()
        sync()
        copiaBajada("A1")
        api.falla += "G1"
        val r = sync()
        assertEquals(listOf(TipoProblema.ERROR_RED), r.problemas.map { it.tipo })
        assertTrue(File(base, "obras/A1.jpg").isFile)
        assertTrue(File(base, "tapas/G1.jpg").isFile)
        assertEquals(2, Catalogo.cargar(base).galerias[0].obras.size)
    }

    @Test
    fun unNombreConPuntosPuntoBarraNoEscribeFueraDeLaCarpeta() {
        api.carpetas[CARPETA] = listOf(carpeta("G1", "../../escape"))
        api.carpetas["G1"] = listOf(imagen("X1", "../malo.jpg", "m"), imagen("../EVIL", "ok.jpg", "m2"))
        api.contenido["X1"] = ByteArray(500)
        api.contenido["../EVIL"] = ByteArray(500)
        sync()
        assertTrue(File(base, "tapas/G1.jpg").isFile)
        assertEquals(listOf("galerias"), padre.list()!!.toList())
        assertEquals(listOf("X1"), api.bajadas)
        assertFalse(File(padre, "malo.jpg").exists())
        assertEquals("malo.jpg", File(Catalogo.cargar(base).galerias[0].obras[0].archivo).name)
    }

    @Test
    fun sinClaveNoSincroniza() {
        escenarioBasico()
        api.disponible = false
        val r = sync()
        assertFalse(r.ok)
        assertEquals(listOf(TipoProblema.SIN_CLAVE), r.problemas.map { it.tipo })
        assertTrue(api.bajadas.isEmpty())
        assertNull(Catalogo.cargar(base).ultimaSincronizacionBuena)
    }

    @Test
    fun catalogoSinNadaEstaVacio() {
        val c = Catalogo.cargar(base)
        assertTrue(c.galerias.isEmpty())
        assertTrue(c.grupos.isEmpty())
        assertNotNull(c.problemas)
    }

    @Test
    fun errorDriveNoLlevaLaUrl() {
        val e = ErrorDrive(403, "ABC")
        assertFalse(e.message!!.contains("key="))
        assertFalse(e.message!!.contains("http"))
    }

    // ------------------------------------------------------------------------------------------------- el árbol

    /** Renacimiento (grupo) > Rafael (galería) y Leonardo (grupo) > Bocetos (galería); Barroco (galería sola) y Mixta (galería Y grupo). */
    private fun escenarioArbol() {
        api.carpetas[CARPETA] = listOf(carpeta("REN", "Renacimiento"), carpeta("BAR", "Barroco"), carpeta("MIX", "Mixta"), carpeta("VAC", "Vacio"))
        api.carpetas["REN"] = listOf(carpeta("RAF", "Rafael"), carpeta("LEO", "Leonardo"))
        api.carpetas["RAF"] = listOf(imagen("R1", "r1.jpg", "m"))
        api.carpetas["LEO"] = listOf(carpeta("BOC", "Bocetos"))
        api.carpetas["BOC"] = listOf(imagen("B1", "b1.jpg", "m"), imagen("B2", "b2.jpg", "m"))
        api.carpetas["BAR"] = listOf(imagen("BA1", "ba1.jpg", "m"))
        api.carpetas["MIX"] = listOf(imagen("M1", "m1.jpg", "m"), carpeta("MIXHIJA", "Hija"))
        api.carpetas["MIXHIJA"] = listOf(imagen("H1", "h1.jpg", "m"))
        api.carpetas["VAC"] = listOf(carpeta("VAC2", "Sub vacia"))
        api.carpetas["VAC2"] = emptyList()
        for (id in listOf("R1", "B1", "B2", "BA1", "M1", "H1")) api.contenido[id] = ByteArray(10)
    }

    @Test
    fun recorreElArbolConGruposAnidadosYUnaCarpetaQueEsGaleriaYGrupo() {
        escenarioArbol()
        val r = sync()
        assertTrue(r.problemas.toString(), r.problemas.isEmpty())
        val c = Catalogo.cargar(base)
        val gal = c.galerias.associateBy { it.driveId }
        assertEquals(setOf("RAF", "BOC", "BAR", "MIX", "MIXHIJA"), gal.keys)
        assertEquals(emptyList<String>(), gal["BAR"]!!.ruta)
        assertEquals(listOf("Renacimiento"), gal["RAF"]!!.ruta)
        assertEquals(listOf("Renacimiento", "Leonardo"), gal["BOC"]!!.ruta)
        assertEquals("LEO", gal["BOC"]!!.padreId)
        // Mixta es galería (sus 1 obra) y a la vez grupo de Hija
        assertEquals(1, gal["MIX"]!!.obras.size)
        assertEquals(listOf("Mixta"), gal["MIXHIJA"]!!.ruta)
        assertEquals("MIX", gal["MIXHIJA"]!!.padreId)
        // grupos: solo los que tienen subcarpetas y alguna galería adentro (Vacio no)
        assertEquals(setOf("REN", "LEO", "MIX"), c.grupos.map { it.driveId }.toSet())
        assertEquals(listOf("Renacimiento"), c.grupos.first { it.driveId == "LEO" }.ruta)
        // una tapa por galería, tildada o no
        for (id in gal.keys) assertTrue("tapa de $id", File(base, "tapas/$id.jpg").isFile)
    }

    @Test
    fun noPasaDeLaProfundidadMaxima() {
        // RAIZ > N1 > N2 > N3 > N4 > N5 (la N5 queda afuera)
        api.carpetas[CARPETA] = listOf(carpeta("N1", "n1"))
        api.carpetas["N1"] = listOf(carpeta("N2", "n2"), imagen("I1", "i.jpg", "m"))
        api.carpetas["N2"] = listOf(carpeta("N3", "n3"), imagen("I2", "i.jpg", "m"))
        api.carpetas["N3"] = listOf(carpeta("N4", "n4"), imagen("I3", "i.jpg", "m"))
        api.carpetas["N4"] = listOf(carpeta("N5", "n5"), imagen("I4", "i.jpg", "m"))
        api.carpetas["N5"] = listOf(imagen("I5", "i.jpg", "m"))
        for (id in listOf("I1", "I2", "I3", "I4", "I5")) api.contenido[id] = ByteArray(10)
        sync()
        val ids = Catalogo.cargar(base).galerias.map { it.driveId }.toSet()
        assertEquals(setOf("N1", "N2", "N3", "N4"), ids)
        assertFalse("N5" in api.listadas)
    }

    @Test
    fun siFallaElListadoDeUnGrupoSeConservaTodoLoQueColgabaDeEl() {
        escenarioArbol()
        sync()
        copiaBajada("B1")
        api.falla += "LEO"
        val r = sync()
        assertEquals(listOf(TipoProblema.ERROR_RED), r.problemas.map { it.tipo })
        val c = Catalogo.cargar(base)
        assertTrue("BOC" in c.galerias.map { it.driveId }) // Bocetos sigue
        assertTrue(File(base, "obras/B1.jpg").isFile)
        assertTrue(File(base, "tapas/BOC.jpg").isFile)
        assertTrue("LEO" in c.grupos.map { it.driveId })
        assertFalse(r.ok)
    }

    @Test
    fun siSeBorraUnGrupoSeBorraTodoLoDeAdentro() {
        escenarioArbol()
        sync()
        copiaBajada("B1"); copiaBajada("R1")
        api.carpetas[CARPETA] = listOf(carpeta("BAR", "Barroco"))
        sync()
        val c = Catalogo.cargar(base)
        assertEquals(listOf("BAR"), c.galerias.map { it.driveId })
        assertTrue(c.grupos.isEmpty())
        assertFalse(File(base, "obras/B1.jpg").exists())
        assertFalse(File(base, "obras/R1.jpg").exists())
        assertFalse(File(base, "tapas/BOC.jpg").exists())
        assertFalse(File(base, "json").listFiles()!!.any())
    }

    @Test
    fun mueveUnaCarpetaDeGrupoYActualizaLaRuta() {
        escenarioArbol()
        sync()
        // Rafael pasa de Renacimiento a la raíz
        api.carpetas[CARPETA] = listOf(carpeta("REN", "Renacimiento"), carpeta("RAF", "Rafael"))
        api.carpetas["REN"] = listOf(carpeta("LEO", "Leonardo"))
        sync()
        val raf = Catalogo.cargar(base).galerias.first { it.driveId == "RAF" }
        assertEquals(emptyList<String>(), raf.ruta)
        assertNull(raf.padreId)
    }

    // ------------------------------------------------------------------------------------------------- índice viejo

    private val indiceViejo = """
        {"galerias":[{"id":"G1","nombre":"Impresionismo",
          "json":{"id":"J1","nombre":"galeria.json","md5":"mj","modificado":"t"},
          "imagenes":[{"id":"A1","nombre":"a.jpg","md5":"ma","modificado":"t","ancho":40,"alto":30},
                      {"id":"B1","nombre":"b.jpg","md5":"mb","modificado":"t","ancho":40,"alto":30}]}],
         "problemas":[],"ultimaSincronizacionBuena":500,"ultimoIntento":500,"ultimoIntentoOk":true}
    """.trimIndent()

    @Test
    fun leeElIndiceViejoYReusaLasCopiasQueYaEstan() {
        escenarioBasico()
        base.mkdirs()
        File(base, "indice.json").writeText(indiceViejo)
        File(base, "json").mkdirs()
        File(base, "json/J1.json").writeText(String(api.contenido["J1"]!!))
        copiaBajada("A1"); copiaBajada("B1")

        // sin sincronizar: se lee tal cual (medidas de la copia, ya bajadas)
        val antes = Catalogo.cargar(base)
        assertEquals(2, antes.galerias[0].obras.size)
        assertTrue(antes.galerias[0].obras.all { it.bajada && it.conMedidas })
        assertTrue(antes.grupos.isEmpty())
        assertNull(antes.galerias[0].padreId)

        sync()
        // las copias que ya estaban (mismo md5) se reusan, no se bajan de nuevo
        assertTrue(File(base, "obras/A1.jpg").isFile)
        assertTrue(File(base, "obras/B1.jpg").isFile)
        assertFalse("A1" in api.bajadas)
        assertTrue(File(base, "tapas/G1.jpg").isFile)
        assertTrue(Catalogo.cargar(base).galerias[0].obras.all { it.bajada })
    }

    @Test
    fun unIndiceIlegibleSeRehaceEnLaPrimeraSincronizacion() {
        escenarioBasico()
        base.mkdirs()
        File(base, "indice.json").writeText("{esto no es json")
        assertTrue(Catalogo.cargar(base).galerias.isEmpty())
        val r = sync()
        assertTrue(r.ok)
        assertEquals(1, Catalogo.cargar(base).galerias.size)
    }
}
