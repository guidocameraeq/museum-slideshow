package local.museumslideshow.ajustes

import local.museumslideshow.sync.Sincronizacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Un "almacén" en memoria: lo que guarda DataStore es justamente este mapa de texto. */
private class MemoriaFalsa {
    var mapa: Map<String, String> = emptyMap()
    fun leer() = CodecPreferencias.decodificar(mapa)
    fun cambiar(t: (Preferencias) -> Preferencias) {
        mapa = CodecPreferencias.codificar(t(leer()))
    }
}

class CodecPreferenciasTest {
    private val distintas = Preferencias(
        tema = Tema.INMERSIVO,
        modo = Modo.OSCURO,
        horaDiaMin = 6 * 60 + 30,
        horaNocheMin = 21 * 60,
        colorParedClaro = "piedra",
        colorParedOscuro = "bordo",
        marco = MarcoElegido.FIJO("madera"),
        tamano = Tamano.MAXIMO,
        pantallaCompletaConMarco = true,
        explicacionEnPantalla = ExplicacionEnPantalla.NUNCA,
        nombreSala = NombreSala.SOLO_CONTADOR,
        calidad = Calidad.HD1080,
        tiempoPorObraSeg = 180,
        orden = Orden.MEZCLAR,
        galeria = "1AbC_dEf-GhI",
        pared = Pared.HASTA_2,
        cartelObra = CartelObra.UNOS_SEGUNDOS,
        transicion = Transicion.NINGUNA,
        duracionTransicionMs = 2500,
        sincronizarCada = SincronizarCada.SOLO_A_MANO,
        espacioMaximo = EspacioMaximo.DOS_GB,
        carpetaDrive = "1CarpetaDeDriveLarga123",
        seleccionGalerias = mapOf("G-1" to false, "G_2" to true),
        galeriasNuevas = GaleriasNuevas.DESTILDADAS,
        reservaObras = 100,
    )

    @Test
    fun idaYVueltaConTodosLosCamposCambiados() {
        assertEquals(distintas, CodecPreferencias.decodificar(CodecPreferencias.codificar(distintas)))
    }

    @Test
    fun idaYVueltaConLosValoresPorDefecto() {
        val p = Preferencias()
        assertEquals(p, CodecPreferencias.decodificar(CodecPreferencias.codificar(p)))
    }

    @Test
    fun cadaCampoTieneSuClave() {
        // Con todos los campos "no nulos" cambiados, el mapa trae todas las claves conocidas y ninguna otra.
        assertEquals(CodecPreferencias.K.TODAS.toSet(), CodecPreferencias.codificar(distintas).keys)
    }

    @Test
    fun losEnumsSeGuardanPorNombre() {
        val m = CodecPreferencias.codificar(distintas)
        assertEquals("INMERSIVO", m["tema"])
        assertEquals("HD1080", m["calidad"])
        assertEquals("SOLO_A_MANO", m["sincronizar_cada"])
        assertEquals("fijo:madera", m["marco"])
    }

    @Test
    fun faltanteODesconocidoVuelveAlValorPorDefecto() {
        assertEquals(Preferencias(), CodecPreferencias.decodificar(emptyMap()))
        val basura = mapOf(
            "tema" to "NO_EXISTE", "modo" to "", "hora_dia" to "abc", "hora_noche" to "99999", "pared_claro" to "fucsia",
            "pared_oscuro" to "blanco-galeria", "marco" to "fijo:inventado", "tamano" to "GIGANTE", "completa_con_marco" to "quizas",
            "tiempo" to "3", "duracion" to "100000", "sincronizar_cada" to "CADA_MINUTO", "espacio_maximo" to "9TB", "carpeta" to "no es un id",
        )
        assertEquals(Preferencias(), CodecPreferencias.decodificar(basura))
    }

    @Test
    fun unaClaveBuenaSobreviveAlLadoDeUnaMala() {
        val p = CodecPreferencias.decodificar(mapOf("tema" to "SALA_OSCURA", "modo" to "ROSA"))
        assertEquals(Tema.SALA_OSCURA, p.tema)
        assertEquals(Modo.AUTOMATICO, p.modo)
    }

    @Test
    fun losMarcosSeGuardanTodos() {
        for (m in listOf(MarcoElegido.DEL_TEMA, MarcoElegido.AL_AZAR, MarcoElegido.DE_LA_GALERIA, MarcoElegido.FIJO("sin"), MarcoElegido.FIJO("dorado-redondo"))) {
            assertEquals(m, CodecPreferencias.decodificar(CodecPreferencias.codificar(Preferencias(marco = m))).marco)
        }
    }

    @Test
    fun losNulosNoTienenClaveYVuelvenNulos() {
        val m = CodecPreferencias.codificar(Preferencias())
        assertFalse(m.containsKey("galeria"))
        assertFalse(m.containsKey("carpeta"))
        assertFalse(m.containsKey("pared_claro"))
        val p = CodecPreferencias.decodificar(m)
        assertNull(p.galeria); assertNull(p.carpetaDrive); assertNull(p.colorParedClaro); assertNull(p.colorParedOscuro)
    }

    @Test
    fun cambiarSobreLaMemoriaGuardaYSeLeeIgualDespues() {
        val a = MemoriaFalsa()
        assertEquals(Preferencias(), a.leer())
        a.cambiar { it.copy(tema = Tema.LIBRO_DE_ARTE) }
        a.cambiar { it.copy(tiempoPorObraSeg = 300) }
        // "reiniciar la tele": se vuelve a leer del mapa guardado
        val despues = CodecPreferencias.decodificar(a.mapa)
        assertEquals(Tema.LIBRO_DE_ARTE, despues.tema)
        assertEquals(300, despues.tiempoPorObraSeg)
        // reset = todo a los valores por defecto, también guardado
        a.cambiar { Preferencias() }
        assertEquals(Preferencias(), a.leer())
    }

    @Test
    fun laCarpetaGuardadaGanaSobreLaDeFabrica() {
        assertEquals("FABRICA123", Sincronizacion.carpetaEfectiva(Preferencias(), "FABRICA123"))
        assertEquals("MIA4567890", Sincronizacion.carpetaEfectiva(Preferencias(carpetaDrive = "MIA4567890"), "FABRICA123"))
        assertEquals("FABRICA123", Sincronizacion.carpetaEfectiva(Preferencias(carpetaDrive = " "), "FABRICA123"))
    }

    @Test
    fun paraEscenaIgnoraLoQueNoCambiaLaVista() {
        val a = Preferencias()
        val b = a.copy(tiempoPorObraSeg = 10, transicion = Transicion.NINGUNA, duracionTransicionMs = 500,
            sincronizarCada = SincronizarCada.UNA_HORA, espacioMaximo = EspacioMaximo.DOS_GB, carpetaDrive = "XXXXXXXXXXXX")
        assertEquals(a.paraEscena(), b.paraEscena())
        assertTrue(a.paraEscena() != a.copy(tema = Tema.SALA_OSCURA).paraEscena())
        assertTrue(a.paraEscena() != a.copy(calidad = Calidad.HD1080).paraEscena())
    }
}

class CarpetaDriveTest {
    @Test
    fun sacaElIdDeUnLink() {
        assertEquals("1AbCdEfGhIjKlMnOp", CarpetaDrive.extraerId("https://drive.google.com/drive/folders/1AbCdEfGhIjKlMnOp?usp=sharing"))
        assertEquals("1AbCdEfGhIjKlMnOp", CarpetaDrive.extraerId("https://drive.google.com/drive/u/0/folders/1AbCdEfGhIjKlMnOp"))
        assertEquals("1AbCdEfGhIjKlMnOp", CarpetaDrive.extraerId("  https://drive.google.com/open?id=1AbCdEfGhIjKlMnOp&usp=drive_link  "))
    }

    @Test
    fun unIdSinLinkSeRespeta() {
        assertEquals("1AbCdEfGhIjKlMnOp_-", CarpetaDrive.extraerId(" 1AbCdEfGhIjKlMnOp_- "))
    }

    @Test
    fun loQueNoSirveDaNulo() {
        assertNull(CarpetaDrive.extraerId(null))
        assertNull(CarpetaDrive.extraerId(""))
        assertNull(CarpetaDrive.extraerId("   "))
        assertNull(CarpetaDrive.extraerId("corto"))
        assertNull(CarpetaDrive.extraerId("esto no es un id ni un link"))
        assertNull(CarpetaDrive.extraerId("../../etc/passwd"))
    }

    @Test
    fun abreviaParaMostrar() {
        assertEquals("corto", CarpetaDrive.abreviar("corto"))
        assertEquals("1AbCdE…Mn_p", CarpetaDrive.abreviar("1AbCdEfGhIjKlMn_p"))
    }

    @Test
    fun elIntentGuardaLaCarpetaComoId() {
        val p = PreferenciasDesdeTexto.aplicar(
            Preferencias(),
            mapOf("carpeta" to "https://drive.google.com/drive/folders/1AbCdEfGhIjKlMnOp?usp=sharing"),
        )
        assertEquals("1AbCdEfGhIjKlMnOp", p.carpetaDrive)
        assertNull(PreferenciasDesdeTexto.aplicar(p, mapOf("carpeta" to "defecto")).carpetaDrive)
        assertEquals("1AbCdEfGhIjKlMnOp", PreferenciasDesdeTexto.aplicar(p, mapOf("carpeta" to "basura")).carpetaDrive)
    }

    @Test
    fun elIntentCambiaSincronizarYEspacio() {
        val p = PreferenciasDesdeTexto.aplicar(Preferencias(), mapOf("sincronizar" to "mano", "espacio" to "2"))
        assertEquals(SincronizarCada.SOLO_A_MANO, p.sincronizarCada)
        assertEquals(EspacioMaximo.DOS_GB, p.espacioMaximo)
    }
}
