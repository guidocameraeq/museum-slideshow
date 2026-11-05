package local.museumslideshow.temas

import local.museumslideshow.ajustes.Modo
import local.museumslideshow.ajustes.Tamano
import local.museumslideshow.ajustes.Tema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogosTest {
    @Test
    fun coloresDeParedTienenLosIdsDelSpec() {
        assertEquals(listOf("blanco-galeria", "gris-calido", "piedra"), CatalogoColores.claros.map { it.id })
        assertEquals(listOf("carbon", "negro", "azul-noche", "bordo"), CatalogoColores.oscuros.map { it.id })
        assertEquals(0xFF1B2744L, CatalogoColores.porId("azul-noche")!!.c1)
    }

    @Test
    fun grosoresDeMarcosSalenDelBorrador() {
        val fino = CatalogoMarcos.porId("fino")!!
        assertEquals(listOf(14, 10, 5), listOf(Tamano.NORMAL, Tamano.GRANDE, Tamano.MAXIMO).map { fino.grosorDe(it) })
        val filete = CatalogoMarcos.porId("filete")!!
        assertEquals(listOf(4, 3, 2), listOf(Tamano.NORMAL, Tamano.GRANDE, Tamano.MAXIMO).map { filete.fileteDe(it) })
        assertEquals(listOf(44, 30, 16), listOf(Tamano.NORMAL, Tamano.GRANDE, Tamano.MAXIMO).map { CatalogoMarcos.porId("dorado")!!.grosorDe(it) })
        assertTrue(CatalogoMarcos.porId("dorado-redondo")!!.redondo)
    }

    @Test
    fun pantallaCompletaUsaLosGrosoresDeMaximo() {
        for (m in CatalogoMarcos.todos) {
            assertEquals(m.id, m.grosorDe(Tamano.MAXIMO), m.grosorDe(Tamano.PANTALLA_COMPLETA))
            assertEquals(m.id, m.fileteDe(Tamano.MAXIMO), m.fileteDe(Tamano.PANTALLA_COMPLETA))
        }
    }

    @Test
    fun tienenFotoTodosLosMarcosSalvoFino() {
        assertTrue(CatalogoMarcos.todos.filter { it.id != "fino" }.all { it.imagenDisponible })
        assertFalse(CatalogoMarcos.porId("fino")!!.imagenDisponible)
    }

    @Test
    fun unMarcoConFotoSeDevuelveTalCualYUnoDesconocidoCaeEnFino() {
        for (id in listOf("fino", "filete", "madera", "dorado", "dorado-redondo", "sin")) assertEquals(id, id, CatalogoMarcos.resolver(id).id)
        assertEquals("fino", CatalogoMarcos.resolver("inexistente").id)
    }

    @Test
    fun elRegistroTieneLosCuatroTemasConSusReglas() {
        assertEquals(4, RegistroTemas.todos.size)
        val gm = RegistroTemas.de(Tema.GALERIA_MODERNA)
        assertEquals("fino", gm.marcoDelTema)
        assertTrue(gm.conPaspartu && gm.admitePared && !gm.esLibro)
        assertEquals("filete", RegistroTemas.de(Tema.SALA_OSCURA).marcoDelTema)
        val im = RegistroTemas.de(Tema.INMERSIVO)
        assertTrue(im.sinMarcoSiempre && im.fondoDesenfocado)
        assertEquals(Tamano.MAXIMO, im.tamanoFijo)
        val lb = RegistroTemas.de(Tema.LIBRO_DE_ARTE)
        assertTrue(lb.esLibro && !lb.admitePared && lb.sinMarcoSiempre)
    }

    @Test
    fun cadaTemaTieneTipografiaParaLosDosModosYSusRoles() {
        val comunes = listOf(
            Rol.TITULO, Rol.AUTOR, Rol.ANIO, Rol.MOVIMIENTO, Rol.DATOS, Rol.EXPLICACION, Rol.CREDITO, Rol.AVISO_OK,
            Rol.NOMBRE_TITULO, Rol.NOMBRE_AUTOR, Rol.CHIP_TITULO, Rol.CHIP_AUTOR, Rol.SALA, Rol.CONTADOR,
        )
        val libro = listOf(
            Rol.LIBRO_CORRIDO, Rol.LIBRO_TITULO, Rol.LIBRO_AUTOR, Rol.LIBRO_DATO, Rol.LIBRO_MOVIMIENTO,
            Rol.LIBRO_EXPLICACION, Rol.LIBRO_CREDITO, Rol.LIBRO_AVISO_OK, Rol.LIBRO_REGLA,
        )
        for (t in RegistroTemas.todos) for (m in listOf(Modo.CLARO, Modo.OSCURO)) {
            val tipo = t.tipografia(m)
            for (r in if (t.esLibro) libro else comunes) assertTrue("${t.id}/$m/$r", tipo.tiene(r))
        }
    }

    @Test
    fun laTipografiaSaleDelCssDelBorrador() {
        val so = RegistroTemas.de(Tema.SALA_OSCURA).tipografia(Modo.OSCURO)
        assertEquals(40f, so[Rol.TITULO].tamanoPx, 0.01f)
        assertEquals(Familia.SERIF, so[Rol.TITULO].familia)
        assertTrue(so[Rol.TITULO].italica)
        assertTrue(so[Rol.AUTOR].mayusculas)
        val gm = RegistroTemas.de(Tema.GALERIA_MODERNA).tipografia(Modo.CLARO)
        assertEquals(31f, gm[Rol.TITULO].tamanoPx, 0.01f)
        assertEquals(31f * 1.18f, gm[Rol.TITULO].interlineadoPx, 0.01f)
        assertEquals(0xFF222222L, gm[Rol.TITULO].color)
        val lb = RegistroTemas.de(Tema.LIBRO_DE_ARTE).tipografia(Modo.CLARO)
        assertEquals(44f, lb[Rol.LIBRO_TITULO].tamanoPx, 0.01f)
        assertEquals(26f, lb[Rol.LIBRO_EXPLICACION].tamanoPx, 0.01f)
        val im = RegistroTemas.de(Tema.INMERSIVO).tipografia(Modo.OSCURO)
        assertNotNull(im[Rol.SALA].fondo)
        assertFalse(gm[Rol.SALA].fondo != null)
    }

    @Test
    fun colorDeParedDelTemaRespetaElElegidoDelMismoModo() {
        val gm = RegistroTemas.de(Tema.GALERIA_MODERNA)
        assertEquals("blanco-galeria", gm.colorPared(Modo.CLARO, null).id)
        assertEquals("carbon", gm.colorPared(Modo.OSCURO, null).id)
        assertEquals("piedra", gm.colorPared(Modo.CLARO, "piedra").id)
        assertEquals("blanco-galeria", gm.colorPared(Modo.CLARO, "negro").id)   // uno oscuro no vale de día
        assertEquals("bordo", gm.colorPared(Modo.OSCURO, "bordo").id)
    }
}
