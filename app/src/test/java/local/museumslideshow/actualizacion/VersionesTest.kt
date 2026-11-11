package local.museumslideshow.actualizacion

import local.museumslideshow.ajustes.BuscarActualizaciones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionesTest {
    @Test
    fun elCodigoEsElMismoQueElDeGradle() {
        assertEquals(200, Versiones.codigo("0.2"))
        assertEquals(300, Versiones.codigo("v0.3"))
        assertEquals(10204, Versiones.codigo("1.2.4"))
        assertEquals(10000, Versiones.codigo("1"))
        assertNull(Versiones.codigo("v0.3-beta"))
        assertNull(Versiones.codigo("1.2.3.4"))
        assertNull(Versiones.codigo(""))
        assertTrue(Versiones.codigo("0.10")!! > Versiones.codigo("0.9")!!)
    }

    private val respuesta = """
        {
          "tag_name": "v0.3",
          "body": "Pantalla Actualizaciones.\r\nArreglos en la Barra.\n\n**Full Changelog**: https://github.com/x/y/compare/v0.2...v0.3",
          "assets": [
            {"name": "notas.txt", "browser_download_url": "https://x/notas.txt", "size": 10},
            {"name": "museum-slideshow-0.3.apk", "browser_download_url": "https://github.com/x/y/releases/download/v0.3/museum-slideshow-0.3.apk", "size": 4567890}
          ]
        }
    """.trimIndent()

    @Test
    fun leeLaUltimaVersionConSuApk() {
        val v = Versiones.leerUltima(respuesta)!!
        assertEquals("0.3", v.version)
        assertEquals(300, v.codigo)
        assertTrue(v.apkUrl.endsWith("museum-slideshow-0.3.apk"))
        assertEquals(4567890L, v.apkBytes)
        assertEquals("Pantalla Actualizaciones.\nArreglos en la Barra.", v.notas)
    }

    @Test
    fun sinApkOConUnaRespuestaRaraNoHayVersion() {
        assertNull(Versiones.leerUltima("""{"tag_name":"v0.3","assets":[]}"""))
        assertNull(Versiones.leerUltima("""{"tag_name":"cualquiera","assets":[{"name":"a.apk","browser_download_url":"u","size":1}]}"""))
        assertNull(Versiones.leerUltima("no es json"))
        assertEquals("", Versiones.leerUltima("""{"tag_name":"v1","body":null,"assets":[{"name":"a.apk","browser_download_url":"u","size":1}]}""")!!.notas)
    }

    @Test
    fun hayNuevaSoloSiEsMasGrande() {
        val v = Versiones.leerUltima(respuesta)
        assertTrue(Versiones.hayNueva(200, v))
        assertFalse(Versiones.hayNueva(300, v))
        assertFalse(Versiones.hayNueva(400, v))
        assertFalse(Versiones.hayNueva(200, null))
    }

    @Test
    fun buscaSolaUnaVezPorDiaYNuncaConSoloAMano() {
        val h = 60L * 60 * 1000
        val ahora = 1_000L * h
        assertTrue(Versiones.tocaBuscar(BuscarActualizaciones.UNA_VEZ_POR_DIA, null, ahora))
        assertFalse(Versiones.tocaBuscar(BuscarActualizaciones.UNA_VEZ_POR_DIA, ahora - 3 * h, ahora))
        assertTrue(Versiones.tocaBuscar(BuscarActualizaciones.UNA_VEZ_POR_DIA, ahora - 21 * h, ahora))
        // el reloj de la tele fue para atrás: busca
        assertTrue(Versiones.tocaBuscar(BuscarActualizaciones.UNA_VEZ_POR_DIA, ahora + h, ahora))
        assertFalse(Versiones.tocaBuscar(BuscarActualizaciones.SOLO_A_MANO, null, ahora))
    }
}
