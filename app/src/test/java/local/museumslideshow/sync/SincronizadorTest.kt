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

    override fun listar(carpetaId: String): List<ArchivoDrive> {
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

    private fun sync(tope: Long = 1_000_000L, procesador: ProcesadorImagen = ProcesadorFalso()) =
        Sincronizador(api, base, procesador, tope) { reloj }.sincronizar(CARPETA)

    private fun carpeta(id: String, nombre: String) = ArchivoDrive(id, nombre, ArchivoDrive.MIME_CARPETA, null, null, null)
    private fun imagen(id: String, nombre: String, md5: String, tam: Long? = 500) = ArchivoDrive(id, nombre, MIME_JPG, md5, "t", tam)
    private fun json(id: String, md5: String = "mj", tam: Long? = 50) = ArchivoDrive(id, "galeria.json", "application/json", md5, "t", tam)

    /** Una galería "G1" con galeria.json y dos imágenes. */
    private fun escenarioBasico() {
        api.carpetas[CARPETA] = listOf(carpeta("G1", "Impresionismo"))
        api.carpetas["G1"] = listOf(json("J1"), imagen("A1", "a.jpg", "ma"), imagen("B1", "b.jpg", "mb"))
        api.contenido["J1"] = """{"formato":1,"titulo":"Impre","obras":[{"archivo":"b.jpg","titulo":"Bé"},{"archivo":"a.jpg","titulo":"Á"}]}""".toByteArray()
        api.contenido["A1"] = ByteArray(500) { 1 }
        api.contenido["B1"] = ByteArray(500) { 2 }
    }

    @Test
    fun bajaLoNuevoYElCatalogoLoLee() {
        escenarioBasico()
        val r = sync()
        assertTrue(r.problemas.toString(), r.problemas.isEmpty())
        assertEquals(3, r.bajadas)
        assertTrue(File(base, "obras/A1.jpg").isFile)
        assertTrue(File(base, "obras/A1.chica.jpg").isFile)
        assertTrue(File(base, "json/J1.json").isFile)
        assertTrue(File(base, "indice.json").isFile)
        assertEquals(0, (File(base, "tmp").listFiles()?.size ?: 0)) // el original no se guarda

        val c = Catalogo.cargar(base)
        assertEquals(1, c.galerias.size)
        assertEquals("Impre", c.galerias[0].titulo)
        assertEquals(listOf("b.jpg", "a.jpg"), c.galerias[0].obras.map { it.archivo })
        assertEquals("Bé", c.galerias[0].obras[0].ficha!!.titulo)
        assertEquals(40, c.galerias[0].obras[0].ancho)
        assertEquals(1000L, c.ultimaSincronizacionBuena)
    }

    @Test
    fun noRebajaLoIgual() {
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
    fun rebajaLoCambiado() {
        escenarioBasico()
        sync()
        api.bajadas.clear()
        api.carpetas["G1"] = listOf(json("J1"), imagen("A1", "a.jpg", "ma-NUEVO"), imagen("B1", "b.jpg", "mb"))
        val r = sync()
        assertEquals(listOf("A1"), api.bajadas)
        assertEquals(1, r.bajadas)
    }

    @Test
    fun sinMd5ComparaPorModificado() {
        escenarioBasico()
        val sinMd5 = ArchivoDrive("A1", "a.jpg", MIME_JPG, null, "v1", 500)
        api.carpetas["G1"] = listOf(sinMd5)
        sync()
        api.bajadas.clear()
        sync()
        assertTrue(api.bajadas.isEmpty())
        api.carpetas["G1"] = listOf(sinMd5.copy(modificado = "v2"))
        sync()
        assertEquals(listOf("A1"), api.bajadas)
    }

    @Test
    fun borraLoQueSeBorroEnDriveArchivoSueltoYGaleriaEntera() {
        escenarioBasico()
        api.carpetas[CARPETA] = listOf(carpeta("G1", "Impresionismo"), carpeta("G2", "Otra"))
        api.carpetas["G2"] = listOf(imagen("C1", "c.jpg", "mc"))
        api.contenido["C1"] = ByteArray(500)
        sync()
        assertTrue(File(base, "obras/C1.jpg").isFile)

        api.carpetas["G1"] = listOf(json("J1"), imagen("A1", "a.jpg", "ma")) // B1 ya no está
        api.carpetas[CARPETA] = listOf(carpeta("G1", "Impresionismo")) // G2 ya no está
        val r = sync()
        assertFalse(File(base, "obras/B1.jpg").exists())
        assertFalse(File(base, "obras/B1.chica.jpg").exists())
        assertFalse(File(base, "obras/C1.jpg").exists())
        assertTrue(File(base, "obras/A1.jpg").isFile)
        assertTrue(r.borradas >= 2)
        assertEquals(1, Catalogo.cargar(base).galerias.size)
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
        assertEquals(listOf("OK"), api.bajadas)
        assertFalse(File(base, "obras/GRANDE.jpg").exists())
        // los problemas del sincronizador llegan al catálogo
        assertEquals(3, Catalogo.cargar(base).problemas.size)
    }

    @Test
    fun jsonQueExcedeElTopeMientrasBajaSeSaltea() {
        api.carpetas[CARPETA] = listOf(carpeta("G1", "X"))
        api.carpetas["G1"] = listOf(json("JG", tam = null))
        api.contenido["JG"] = ByteArray(1_100_000)
        val r = sync()
        assertEquals(listOf(TipoProblema.JSON_MUY_GRANDE), r.problemas.map { it.tipo })
        assertFalse(File(base, "json/JG.json").exists())
    }

    @Test
    fun imagenQueNoDecodificaSeAnota() {
        api.carpetas[CARPETA] = listOf(carpeta("G1", "X"))
        api.carpetas["G1"] = listOf(imagen("ROTA", "rota.jpg", "m"))
        api.contenido["ROTA"] = "MALA".toByteArray()
        val r = sync()
        assertEquals(listOf(TipoProblema.IMAGEN_NO_DECODIFICA), r.problemas.map { it.tipo })
        assertFalse(File(base, "obras/ROTA.jpg").exists())
        assertEquals(0, (File(base, "tmp").listFiles()?.size ?: 0))
    }

    @Test
    fun conElTopeDeEspacioLlenoDejaDeBajarSinBorrar() {
        api.carpetas[CARPETA] = listOf(carpeta("G1", "X"))
        api.carpetas["G1"] = listOf(imagen("I1", "1.jpg", "m1"), imagen("I2", "2.jpg", "m2"), imagen("I3", "3.jpg", "m3"))
        for (id in listOf("I1", "I2", "I3")) api.contenido[id] = ByteArray(500)
        val r = sync(tope = 200)
        assertEquals(2, r.bajadas)
        assertEquals(1, r.problemas.count { it.tipo == TipoProblema.ESPACIO_LLENO })
        assertFalse(File(base, "obras/I3.jpg").exists())
        assertTrue(File(base, "obras/I1.jpg").isFile)

        // y con tope 0 no baja nada
        val otra = Files.createTempDirectory("sync-test2").toFile()
        val r2 = Sincronizador(api, File(otra, "g"), ProcesadorFalso(), 0L) { 1L }.sincronizar(CARPETA)
        assertEquals(0, r2.bajadas)
        assertEquals(TipoProblema.ESPACIO_LLENO, r2.problemas[0].tipo)
    }

    @Test
    fun fallaAlListarLaRaizNoTocaGaleriasNiAvanzaLaHoraBuenaPeroQuedaAnotada() {
        escenarioBasico()
        sync()
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
    fun fallaDeUnArchivoConservaLaVersionVieja() {
        escenarioBasico()
        sync()
        val viejo = File(base, "obras/A1.jpg").readBytes()
        api.carpetas["G1"] = listOf(json("J1"), imagen("A1", "a.jpg", "ma-NUEVO"), imagen("B1", "b.jpg", "mb"))
        api.contenido["A1"] = ByteArray(500) { 9 }
        api.falla += "A1"
        reloj = 5000L
        val r = sync()
        assertEquals(listOf(TipoProblema.ERROR_RED), r.problemas.map { it.tipo })
        assertTrue(viejo.contentEquals(File(base, "obras/A1.jpg").readBytes()))
        val c = Catalogo.cargar(base)
        assertEquals(2, c.galerias[0].obras.size)
        assertEquals(1000L, c.ultimaSincronizacionBuena) // no fue una sincronización buena
        // cuando vuelve la red, se rebaja
        api.falla.clear()
        sync()
        assertFalse(viejo.contentEquals(File(base, "obras/A1.jpg").readBytes()))
    }

    @Test
    fun falloElListadoDeUnaGaleriaConservaLoViejoDeEsaGaleria() {
        escenarioBasico()
        sync()
        api.falla += "G1"
        val r = sync()
        assertEquals(listOf(TipoProblema.ERROR_RED), r.problemas.map { it.tipo })
        assertTrue(File(base, "obras/A1.jpg").isFile)
        assertEquals(2, Catalogo.cargar(base).galerias[0].obras.size)
    }

    @Test
    fun unNombreConPuntosPuntoBarraNoEscribeFueraDeLaCarpeta() {
        api.carpetas[CARPETA] = listOf(carpeta("G1", "../../escape"))
        api.carpetas["G1"] = listOf(imagen("X1", "../malo.jpg", "m"), imagen("../EVIL", "ok.jpg", "m2"))
        api.contenido["X1"] = ByteArray(500)
        api.contenido["../EVIL"] = ByteArray(500)
        sync()
        assertTrue(File(base, "obras/X1.jpg").isFile)
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
        assertNotNull(c.problemas)
    }

    @Test
    fun errorDriveNoLlevaLaUrl() {
        val e = ErrorDrive(403, "ABC")
        assertFalse(e.message!!.contains("key="))
        assertFalse(e.message!!.contains("http"))
    }
}
