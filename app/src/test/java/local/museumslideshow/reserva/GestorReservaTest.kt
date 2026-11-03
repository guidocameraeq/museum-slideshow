package local.museumslideshow.reserva

import java.io.File
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import local.museumslideshow.catalogo.Catalogo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Un descargador que escribe copias de mentira. */
private class DescargadorFalso(private val dir: File, private val bytes: Int = 1000) : DescargadorObras {
    val pedidas = Collections.synchronizedList(ArrayList<String>())
    val fallan = Collections.synchronizedMap(HashMap<String, ErrorReserva>())
    var demoraMs = 0L
    private val enCurso = AtomicInteger(0)
    val maximoSimultaneas = AtomicInteger(0)

    override fun bajar(obra: ObraRef): ResultadoBajada {
        val ahora = enCurso.incrementAndGet()
        maximoSimultaneas.updateAndGet { maxOf(it, ahora) }
        try {
            if (demoraMs > 0) Thread.sleep(demoraMs)
            fallan[obra.id]?.let { return ResultadoBajada.Falla(it, if (it == ErrorReserva.DRIVE) "503" else null) }
            pedidas += obra.id
            val f = Catalogo.archivoObra(dir, obra.id)
            f.parentFile.mkdirs()
            f.writeBytes(ByteArray(bytes))
            Catalogo.archivoObraChica(dir, obra.id).writeBytes(ByteArray(10))
            return ResultadoBajada.Lista(12L)
        } finally {
            enCurso.decrementAndGet()
        }
    }
}

class GestorReservaTest {
    private lateinit var dir: File
    private lateinit var descargador: DescargadorFalso
    private val registro = Collections.synchronizedList(ArrayList<String>())
    private var n = 5
    private var tope = 100L * 1024 * 1024

    @Before
    fun preparar() {
        dir = Files.createTempDirectory("reserva-test").toFile()
        descargador = DescargadorFalso(dir)
        registro.clear()
        n = 5
        tope = 100L * 1024 * 1024
    }

    private fun gestor() = GestorReserva(
        scope = CoroutineScope(Dispatchers.Default),
        dirBase = dir,
        descargador = descargador,
        parametros = { ParametrosReserva(n, tope) },
        registrar = { registro += it },
        iniciar = false,
    )

    private fun orden(cuantas: Int) = (0 until cuantas).map { ObraRef("o$it", "obra$it.jpg") }

    private fun pedido(orden: List<ObraRef>, pos: Int, extras: List<ObraRef> = emptyList()) =
        PedidoReserva(orden, pos, ParametrosReserva(n, tope), extras)

    private fun enDisco() = File(dir, "obras").listFiles().orEmpty().map { it.name }.filter { !it.endsWith(".chica.jpg") }.map { it.removeSuffix(".jpg") }.toSet()

    @Test
    fun conDriveEnPausaLaReservaNoBajaNadaYLaGaleriaSigueConLoGuardado() = runBlocking {
        val g = GestorReserva(
            scope = CoroutineScope(Dispatchers.Default), dirBase = dir, descargador = descargador,
            parametros = { ParametrosReserva(n, tope) }, registrar = { registro += it }, enPausa = { true }, iniciar = false,
        )
        // Una obra ya guardada: no se borra por estar en pausa.
        val previa = Catalogo.archivoObra(dir, "o0")
        previa.parentFile.mkdirs()
        previa.writeBytes(ByteArray(10))
        g.procesar(pedido(orden(30), 0))
        assertTrue("no se pidió nada al descargador", descargador.pedidas.isEmpty())
        assertEquals(setOf("o0"), enDisco())
        assertTrue(registro.any { it.contains("pausa") })
    }

    @Test
    fun siElDescargadorAvisaQueHayPausaSeCortaLaTandaSinMarcarError() = runBlocking {
        val avisa = DescargadorObras { ResultadoBajada.Falla(ErrorReserva.PAUSA, null) }
        val pedidas = AtomicInteger(0)
        val contando = DescargadorObras { pedidas.incrementAndGet(); avisa.bajar(it) }
        val g = GestorReserva(
            scope = CoroutineScope(Dispatchers.Default), dirBase = dir, descargador = contando,
            parametros = { ParametrosReserva(n, tope) }, registrar = { registro += it }, iniciar = false,
        )
        g.procesar(pedido(orden(30), 0))
        assertEquals("de a una y corta al primer aviso", 1, pedidas.get())
        assertNull(g.estado.value.ultimoError)
    }

    @Test
    fun alAbrirBajaLasNProximas() = runBlocking {
        val g = gestor()
        g.procesar(pedido(orden(30), 0))
        assertEquals((0 until 5).map { "o$it" }.toSet(), enDisco())
        assertEquals(5, g.estado.value.listasPorDelante)
        assertEquals(5, g.estado.value.porDelante)
        assertTrue(g.estaBajada("o0"))
        assertFalse(g.estaBajada("o9"))
        assertTrue(g.estado.value.conPedido)
    }

    @Test
    fun noBajaDeMasHastaQueQuedenMenosDeLaMitad() = runBlocking {
        val g = gestor()
        g.procesar(pedido(orden(30), 0))
        descargador.pedidas.clear()
        // avanza una obra: quedan 4 por delante de 5 -> no hace falta nada
        g.procesar(pedido(orden(30), 1))
        assertTrue(descargador.pedidas.isEmpty())
        // avanza hasta que quedan menos de la mitad (2 de 5): baja la tanda hasta completar N
        g.procesar(pedido(orden(30), 3))
        assertEquals(listOf("o5", "o6", "o7"), descargador.pedidas.sorted())
        assertEquals(5, g.estado.value.listasPorDelante)
    }

    @Test
    fun nuncaGuardaMasDeNMasDiezCopias() = runBlocking {
        n = 20
        val g = gestor()
        var maximo = 0
        for (pos in 0 until 100) {
            g.procesar(pedido(orden(100), pos))
            maximo = maxOf(maximo, enDisco().size)
        }
        assertTrue("máximo $maximo", maximo <= 30)
        assertTrue(maximo > 20)
        // y siempre estuvo bajada la que está en pantalla
        assertTrue(g.estaBajada("o99"))
    }

    @Test
    fun borraLasCopiasQueQuedaronFueraDeLaVentana() = runBlocking {
        val g = gestor()
        g.procesar(pedido(orden(100), 0))
        // salta lejos (como si cambiara de galería): las viejas ya no valen
        g.procesar(pedido(orden(100), 50))
        val esperado = (50 until 55).map { "o$it" }.toSet()
        assertEquals(esperado, enDisco())
        assertFalse(File(dir, "obras/o0.chica.jpg").exists())
    }

    @Test
    fun loQueSeDestildaDejaDeEstarEnElPaseYSeBorra() = runBlocking {
        val g = gestor()
        val todas = orden(30)
        g.procesar(pedido(todas, 0))
        // se destilda la galería de o0..o9: el pase queda con o10..o29
        g.procesar(pedido(todas.drop(10), 0))
        assertTrue(enDisco().none { it in (0 until 10).map { i -> "o$i" } })
        assertEquals((10 until 15).map { "o$it" }.toSet(), enDisco())
    }

    @Test
    fun conLaGaleriaVaciaDePedidoNoBorraNada() = runBlocking {
        val g = gestor()
        g.procesar(pedido(orden(30), 0))
        g.procesar(pedido(emptyList(), 0))
        assertEquals(5, enDisco().size)
    }

    @Test
    fun reusaLasCopiasQueYaEstanSinRebajar() = runBlocking {
        val g = gestor()
        g.procesar(pedido(orden(30), 0))
        descargador.pedidas.clear()
        // otra instancia (la app se reabrió): lo que ya está bajado no se vuelve a bajar
        gestor().procesar(pedido(orden(30), 0))
        assertTrue(descargador.pedidas.isEmpty())
    }

    @Test
    fun bajaDeAUnaALaVezPorDefecto() = runBlocking {
        n = 12
        descargador.demoraMs = 40
        val g = gestor()
        g.procesar(pedido(orden(50), 0))
        assertEquals(12, enDisco().size)
        assertEquals(1, descargador.maximoSimultaneas.get())
    }

    @Test
    fun conElTopeDeEspacioBajaMenosYLoInforma() = runBlocking {
        n = 10
        tope = 3_500L // entran 3 obras de 1000 bytes (más las chicas)
        val g = gestor()
        g.procesar(pedido(orden(30), 0))
        assertTrue("bajó ${enDisco().size}", enDisco().size in 1..4)
        assertTrue(enDisco().size < 10)
        assertEquals(ErrorReserva.ESPACIO_LLENO, g.estado.value.ultimoError)
        assertTrue(registro.any { it.contains("espacio máximo") })
    }

    @Test
    fun sinInternetCortaLaTandaYQuedaAnotado() = runBlocking {
        val g = gestor()
        for (i in 0 until 5) descargador.fallan["o$i"] = ErrorReserva.SIN_INTERNET
        g.procesar(pedido(orden(30), 0))
        assertTrue(enDisco().isEmpty())
        assertTrue(g.estado.value.sinInternet)
        assertEquals(ErrorReserva.SIN_INTERNET, g.estado.value.ultimoError)
        // vuelve internet: la próxima tanda sigue y limpia el aviso
        descargador.fallan.clear()
        g.procesar(pedido(orden(30), 0))
        assertEquals(5, enDisco().size)
        assertFalse(g.estado.value.sinInternet)
        assertNull(g.estado.value.ultimoError)
    }

    @Test
    fun siUnaFallaPorDriveSeAnotaElCodigoYSeSigueConLasDemas() = runBlocking {
        val g = gestor()
        descargador.fallan["o1"] = ErrorReserva.DRIVE
        g.procesar(pedido(orden(30), 0))
        assertEquals(setOf("o0", "o2", "o3", "o4"), enDisco())
        assertEquals(ErrorReserva.DRIVE, g.estado.value.ultimoError)
        assertEquals("503", g.estado.value.detalleError)
    }

    @Test
    fun cuentaLasObrasListasConCadaBajada() = runBlocking {
        val g = gestor()
        val antes = g.estado.value.version
        g.procesar(pedido(orden(30), 0))
        assertEquals(antes + 5, g.estado.value.version)
    }

    @Test
    fun elRegistroDiceCuantasHayYCuantoTardoCadaUna() = runBlocking {
        val g = gestor()
        g.procesar(pedido(orden(30), 0))
        assertTrue(registro.any { Regex("""Reserva: listas \d+ de 5 por delante, \d+ vistas guardadas, \d+ MB; bajando \d+""").matches(it) })
        assertTrue(registro.any { it == "Reserva: listas 5 de 5 por delante, 0 vistas guardadas, 0 MB; bajando 0" })
        assertTrue(registro.any { it.startsWith("Reserva: obra lista obra0.jpg en 12 ms") })
        // no repite la misma línea si nada cambió
        val cuantas = registro.size
        g.procesar(pedido(orden(30), 0))
        assertEquals(cuantas, registro.size)
    }

    @Test
    fun lasObrasSinMedidasSeBajanAunqueNoEstenEnElPase() = runBlocking {
        val g = gestor()
        g.procesar(pedido(orden(30), 0, extras = listOf(ObraRef("sm1", "sin1.jpg"))))
        assertTrue("sm1" in enDisco())
        // y la siguiente vez no se borra
        g.procesar(pedido(orden(30), 0, extras = listOf(ObraRef("sm1", "sin1.jpg"))))
        assertTrue("sm1" in enDisco())
    }

    @Test
    fun alCrearseLimpiaLosRestosDeUnaBajadaCortada() {
        val tmp = File(dir, DescargadorDrive.TMP)
        tmp.mkdirs()
        File(tmp, "x.orig").writeBytes(ByteArray(10))
        gestor()
        assertFalse(tmp.exists())
    }
}
