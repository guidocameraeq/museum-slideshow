package local.museumslideshow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistroTest {
    @Test
    fun laEtiquetaDelRegistroEsLaDeLaApp() {
        assertEquals("MuseumSlideshow", Registro.TAG)
    }

    @Test
    fun limpiarBorraLaClaveDeUnaUrl() {
        val url = "https://www.googleapis.com/drive/v3/files?q=abc&key=AIzaSECRETA123&pageSize=10"
        val limpio = Registro.limpiar(url)
        assertFalse(limpio.contains("AIzaSECRETA123"))
        assertTrue(limpio.contains("key=***&pageSize=10"))
    }

    @Test
    fun limpiarBorraLaClaveAlFinalDelTextoYEnMensajes() {
        val msg = "FileNotFoundException: https://x/y?alt=media&key=SECRETO fallo"
        assertEquals("FileNotFoundException: https://x/y?alt=media&key=*** fallo", Registro.limpiar(msg))
        assertEquals("sin clave acá", Registro.limpiar("sin clave acá"))
    }
}
