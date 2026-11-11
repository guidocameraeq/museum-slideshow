package local.museumslideshow.ajustes

import local.museumslideshow.sync.Sincronizacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ClaveDriveTest {
    private val k = "AIzaSyClaveDePruebaQueNoEsReal_12345"

    @Test
    fun limpiarSacaEspaciosYRechazaLoQueNoEsClave() {
        assertEquals(k, ClaveDrive.limpiar("  $k\n"))
        assertNull(ClaveDrive.limpiar(""))
        assertNull(ClaveDrive.limpiar(null))
        assertNull(ClaveDrive.limpiar("corta"))
        assertNull(ClaveDrive.limpiar("con/barra/que/no/va/en/una/clave"))
    }

    @Test
    fun taparNuncaMuestraLaClaveEntera() {
        val t = ClaveDrive.tapar(k)
        assertEquals("AIza…•••", t)
        assertFalse(t.contains(k.substring(4, 10)))
    }

    @Test
    fun lasDeFabricaSeCopianSoloSiNoHayNadaCargado() {
        val vacia = Preferencias()
        val copiada = ClaveDrive.conLasDeFabrica(vacia, k, "https://drive.google.com/drive/folders/1CarpetaFabrica123")
        assertEquals(k, copiada.claveDrive)
        assertEquals("1CarpetaFabrica123", copiada.carpetaDrive)

        // Lo cargado en la tele gana.
        val cargada = Preferencias(claveDrive = "AIzaOtraClaveCargadaEnLaTele_999", carpetaDrive = "1CarpetaDeLaTele99")
        assertSame(cargada, ClaveDrive.conLasDeFabrica(cargada, k, "1CarpetaFabrica123"))

        // APK de GitHub: no trae nada de fábrica, no cambia nada.
        assertSame(vacia, ClaveDrive.conLasDeFabrica(vacia, "", ""))
    }

    @Test
    fun laClaveEfectivaPrefiereLaCargada() {
        assertEquals(k, Sincronizacion.claveEfectiva(Preferencias(claveDrive = k), "FABRICA"))
        assertEquals("FABRICA", Sincronizacion.claveEfectiva(Preferencias(), "FABRICA"))
        assertEquals("", Sincronizacion.claveEfectiva(Preferencias(), ""))
    }

    @Test
    fun porAdbSeCargaYSeBorra() {
        val p = PreferenciasDesdeTexto.aplicar(Preferencias(), mapOf("clave" to k))
        assertEquals(k, p.claveDrive)
        // Una clave mal escrita no pisa la buena.
        assertEquals(k, PreferenciasDesdeTexto.aplicar(p, mapOf("clave" to "no sirve")).claveDrive)
        assertNull(PreferenciasDesdeTexto.aplicar(p, mapOf("clave" to "borrar")).claveDrive)
    }
}
