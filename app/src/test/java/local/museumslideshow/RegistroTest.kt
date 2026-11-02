package local.museumslideshow

import org.junit.Assert.assertEquals
import org.junit.Test

class RegistroTest {
    @Test
    fun laEtiquetaDelRegistroEsLaDeLaApp() {
        assertEquals("MuseumSlideshow", Registro.TAG)
    }
}
