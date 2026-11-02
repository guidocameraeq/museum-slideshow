package local.museumslideshow.catalogo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LectorGaleriaTest {
    private fun recurso(carpeta: String): String =
        javaClass.getResourceAsStream("/galerias-prueba/$carpeta/galeria.json")!!.readBytes().toString(Charsets.UTF_8)

    private fun imgs(vararg nombres: String) = nombres.map { ImagenLocal("id-$it", it, 100, 80) }

    private fun tipos(r: ResultadoLectura) = r.problemas.map { it.tipo }

    @Test
    fun sanaTieneCincoObrasEnOrdenYLaDeRafaelEsRedonda() {
        val r = LectorGaleria.leer(
            "g1", "Sana", recurso("Sana"),
            imgs(
                "friedrich-caminante-mar-de-nubes.jpg", "monet-impresion-sol-naciente.jpg",
                "rafael-madonna-de-la-silla.jpg", "van-gogh-noche-estrellada.jpg", "vermeer-joven-perla.jpg",
            ),
        )
        assertTrue(r.problemas.toString(), r.problemas.isEmpty())
        assertEquals("Prueba sana", r.galeria.titulo)
        assertEquals(5, r.galeria.obras.size)
        assertEquals("monet-impresion-sol-naciente.jpg", r.galeria.obras[0].archivo)
        val rafael = r.galeria.obras.first { it.archivo.startsWith("rafael") }
        assertEquals(Forma.REDONDA, rafael.ficha!!.forma)
        val monet = r.galeria.obras[0].ficha!!
        assertEquals("Dominio público", monet.licencia)
        assertNotNull(monet.fuente)
        assertEquals("File:Monet - Impression, Sunrise.jpg", monet.id)
    }

    @Test
    fun jsonRotoDejaTodasSinFichaPorNombre() {
        val r = LectorGaleria.leer("g2", "Rota-json", recurso("Rota-json"), imgs("vermeer-joven-perla.jpg", "monet-impresion-sol-naciente.jpg"))
        assertEquals(listOf(TipoProblema.JSON_INVALIDO), tipos(r))
        assertNotNull(r.problemas[0].detalle)
        assertEquals("Rota-json", r.galeria.titulo)
        assertEquals(listOf("monet-impresion-sol-naciente.jpg", "vermeer-joven-perla.jpg"), r.galeria.obras.map { it.archivo })
        assertTrue(r.galeria.obras.all { it.ficha == null })
    }

    @Test
    fun archivoFaltanteSalteaLaObraYLaNoListadaVaAlFinal() {
        val r = LectorGaleria.leer("g3", "Rota-archivo", recurso("Rota-archivo"), imgs("friedrich-caminante-mar-de-nubes.jpg", "monet-impresion-sol-naciente.jpg"))
        assertEquals(listOf(TipoProblema.ARCHIVO_FALTANTE), tipos(r))
        assertEquals("no-existe-esta-imagen.jpg", r.problemas[0].archivo)
        assertEquals(2, r.galeria.obras.size)
        assertNotNull(r.galeria.obras[0].ficha)
        assertEquals("friedrich-caminante-mar-de-nubes.jpg", r.galeria.obras[1].archivo)
        assertNull(r.galeria.obras[1].ficha)
    }

    @Test
    fun archivoRepetidoSaltea() {
        val j = """{"formato":1,"obras":[{"archivo":"a.jpg","titulo":"uno"},{"archivo":"a.jpg","titulo":"dos"}]}"""
        val r = LectorGaleria.leer("g", "C", j, imgs("a.jpg"))
        assertEquals(listOf(TipoProblema.ARCHIVO_REPETIDO), tipos(r))
        assertEquals(1, r.galeria.obras.size)
        assertEquals("uno", r.galeria.obras[0].ficha!!.titulo)
    }

    @Test
    fun idRepetidoAvisaPeroMuestraAmbas() {
        val j = """{"formato":1,"obras":[{"archivo":"a.jpg","id":"Q1"},{"archivo":"b.jpg","id":"Q1"}]}"""
        val r = LectorGaleria.leer("g", "C", j, imgs("a.jpg", "b.jpg"))
        assertEquals(listOf(TipoProblema.ID_REPETIDO), tipos(r))
        assertEquals(2, r.galeria.obras.size)
    }

    @Test
    fun obraSinArchivoSeSaltea() {
        val j = """{"formato":1,"obras":[{"titulo":"sin archivo"},{"archivo":5},"texto",{"archivo":"a.jpg"}]}"""
        val r = LectorGaleria.leer("g", "C", j, imgs("a.jpg"))
        assertEquals(listOf(TipoProblema.OBRA_SIN_ARCHIVO, TipoProblema.OBRA_SIN_ARCHIVO, TipoProblema.OBRA_SIN_ARCHIVO), tipos(r))
        assertEquals(1, r.galeria.obras.size)
    }

    @Test
    fun sinGaleriaJsonUsaElNombreDeCarpetaSinProblema() {
        val r = LectorGaleria.leer("g", "Mi carpeta", null, imgs("z.jpg", "b.jpg"))
        assertTrue(r.problemas.isEmpty())
        assertEquals("Mi carpeta", r.galeria.titulo)
        assertEquals(listOf("b.jpg", "z.jpg"), r.galeria.obras.map { it.archivo })
        assertTrue(r.galeria.obras.all { it.ficha == null })
    }

    @Test
    fun formatoNuevoSeLeeIgualConAviso() {
        val j = """{"formato":2,"titulo":"Futura","obras":[{"archivo":"a.jpg","titulo":"T"}]}"""
        val r = LectorGaleria.leer("g", "C", j, imgs("a.jpg"))
        assertEquals(listOf(TipoProblema.FORMATO_NUEVO), tipos(r))
        assertEquals("Futura", r.galeria.titulo)
        assertEquals("T", r.galeria.obras[0].ficha!!.titulo)
    }

    @Test
    fun sinFormatoOFormatoDeTextoEsJsonSinFormato() {
        for (j in listOf("""{"titulo":"x","obras":[]}""", """{"formato":"1","obras":[]}""")) {
            val r = LectorGaleria.leer("g", "C", j, imgs("a.jpg"))
            assertEquals(listOf(TipoProblema.JSON_SIN_FORMATO), tipos(r))
            assertEquals("C", r.galeria.titulo)
            assertNull(r.galeria.obras[0].ficha)
        }
    }

    @Test
    fun raizQueNoEsObjetoEsJsonInvalido() {
        val r = LectorGaleria.leer("g", "C", "[1,2]", imgs("a.jpg"))
        assertEquals(listOf(TipoProblema.JSON_INVALIDO), tipos(r))
    }

    @Test
    fun campoDesconocidoYCampoConTipoEquivocadoSeIgnoran() {
        val j = """{"formato":1,"titulo":"","cosa_nueva":{"x":1},"obras":[
            {"archivo":"a.jpg","titulo":"Bien","autor":123,"anio":1872,"forma":"hexagonal","campo_raro":[1]}]}"""
        val r = LectorGaleria.leer("g", "Carpeta", j, imgs("a.jpg"))
        assertTrue(r.problemas.isEmpty())
        assertEquals("Carpeta", r.galeria.titulo)
        val f = r.galeria.obras[0].ficha!!
        assertEquals("Bien", f.titulo)
        assertNull(f.autor)
        assertNull(f.anio)
        assertEquals(Forma.RECTANGULAR, f.forma)
    }

    @Test
    fun ovaladaSeLee() {
        val j = """{"formato":1,"obras":[{"archivo":"a.jpg","forma":"ovalada"}]}"""
        assertEquals(Forma.OVALADA, LectorGaleria.leer("g", "C", j, imgs("a.jpg")).galeria.obras[0].ficha!!.forma)
    }
}
