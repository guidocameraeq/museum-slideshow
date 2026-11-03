package local.museumslideshow.ajustes

import local.museumslideshow.temas.CatalogoColores
import local.museumslideshow.temas.CatalogoMarcos

/**
 * Pasa [Preferencias] a un mapa de texto (clave -> valor) y de vuelta. Es lo que guarda el almacén: cada campo con su
 * clave, los enums por nombre, lo que no se conoce o falta vuelve a su valor por defecto. Lógica pura (se prueba sin DataStore).
 */
object CodecPreferencias {
    object K {
        const val TEMA = "tema"
        const val MODO = "modo"
        const val HORA_DIA = "hora_dia"
        const val HORA_NOCHE = "hora_noche"
        const val PARED_CLARO = "pared_claro"
        const val PARED_OSCURO = "pared_oscuro"
        const val MARCO = "marco"
        const val TAMANO = "tamano"
        const val COMPLETA_CON_MARCO = "completa_con_marco"
        const val EXPLICACION = "explicacion"
        const val SALA = "sala"
        const val CALIDAD = "calidad"
        const val TIEMPO = "tiempo"
        const val ORDEN = "orden"
        const val GALERIA = "galeria"
        const val PARED = "pared"
        const val CARTEL = "cartel"
        const val TRANSICION = "transicion"
        const val DURACION = "duracion"
        const val SINCRONIZAR_CADA = "sincronizar_cada"
        const val ESPACIO_MAXIMO = "espacio_maximo"
        const val CARPETA = "carpeta"
        const val GALERIAS_SELECCION = "galerias_seleccion"
        const val GALERIAS_NUEVAS = "galerias_nuevas"
        const val RESERVA = "reserva"
        const val VISTA_GALERIAS = "vista_galerias"
        const val RITMO_BAJADA = "ritmo_bajada"

        val TODAS = listOf(
            TEMA, MODO, HORA_DIA, HORA_NOCHE, PARED_CLARO, PARED_OSCURO, MARCO, TAMANO, COMPLETA_CON_MARCO, EXPLICACION,
            SALA, CALIDAD, TIEMPO, ORDEN, GALERIA, PARED, CARTEL, TRANSICION, DURACION, SINCRONIZAR_CADA, ESPACIO_MAXIMO, CARPETA,
            GALERIAS_SELECCION, GALERIAS_NUEVAS, RESERVA, VISTA_GALERIAS, RITMO_BAJADA,
        )
    }

    private const val MARCO_DEL_TEMA = "del-tema"
    private const val MARCO_AL_AZAR = "al-azar"
    private const val MARCO_DE_LA_GALERIA = "de-la-galeria"
    private const val MARCO_FIJO = "fijo:"

    /** Un valor nulo (galería "Todas", pared "del tema", carpeta de fábrica) no tiene clave: falta = por defecto. */
    fun codificar(p: Preferencias): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        m[K.TEMA] = p.tema.name
        m[K.MODO] = p.modo.name
        m[K.HORA_DIA] = p.horaDiaMin.toString()
        m[K.HORA_NOCHE] = p.horaNocheMin.toString()
        p.colorParedClaro?.let { m[K.PARED_CLARO] = it }
        p.colorParedOscuro?.let { m[K.PARED_OSCURO] = it }
        m[K.MARCO] = when (val x = p.marco) {
            MarcoElegido.DEL_TEMA -> MARCO_DEL_TEMA
            MarcoElegido.AL_AZAR -> MARCO_AL_AZAR
            MarcoElegido.DE_LA_GALERIA -> MARCO_DE_LA_GALERIA
            is MarcoElegido.FIJO -> MARCO_FIJO + x.id
        }
        m[K.TAMANO] = p.tamano.name
        m[K.COMPLETA_CON_MARCO] = p.pantallaCompletaConMarco.toString()
        m[K.EXPLICACION] = p.explicacionEnPantalla.name
        m[K.SALA] = p.nombreSala.name
        m[K.CALIDAD] = p.calidad.name
        m[K.TIEMPO] = p.tiempoPorObraSeg.toString()
        m[K.ORDEN] = p.orden.name
        p.galeria?.let { m[K.GALERIA] = it }
        m[K.PARED] = p.pared.name
        m[K.CARTEL] = p.cartelObra.name
        m[K.TRANSICION] = p.transicion.name
        m[K.DURACION] = p.duracionTransicionMs.toString()
        m[K.SINCRONIZAR_CADA] = p.sincronizarCada.name
        m[K.ESPACIO_MAXIMO] = p.espacioMaximo.name
        p.carpetaDrive?.let { m[K.CARPETA] = it }
        if (p.seleccionGalerias.isNotEmpty()) m[K.GALERIAS_SELECCION] = codificarSeleccion(p.seleccionGalerias)
        m[K.GALERIAS_NUEVAS] = p.galeriasNuevas.name
        m[K.RESERVA] = p.reservaObras.toString()
        m[K.VISTA_GALERIAS] = p.vistaGalerias.name
        m[K.RITMO_BAJADA] = p.ritmoBajada.name
        return m
    }

    fun decodificar(m: Map<String, String>): Preferencias {
        val d = Preferencias()
        return Preferencias(
            tema = enum<Tema>(m[K.TEMA]) ?: d.tema,
            modo = enum<Modo>(m[K.MODO]) ?: d.modo,
            horaDiaMin = m[K.HORA_DIA]?.toIntOrNull()?.takeIf { it in 0 until 24 * 60 } ?: d.horaDiaMin,
            horaNocheMin = m[K.HORA_NOCHE]?.toIntOrNull()?.takeIf { it in 0 until 24 * 60 } ?: d.horaNocheMin,
            colorParedClaro = m[K.PARED_CLARO]?.takeIf { id -> CatalogoColores.claros.any { it.id == id } },
            colorParedOscuro = m[K.PARED_OSCURO]?.takeIf { id -> CatalogoColores.oscuros.any { it.id == id } },
            marco = marco(m[K.MARCO]) ?: d.marco,
            tamano = enum<Tamano>(m[K.TAMANO]) ?: d.tamano,
            pantallaCompletaConMarco = when (m[K.COMPLETA_CON_MARCO]) {
                "true" -> true
                "false" -> false
                else -> d.pantallaCompletaConMarco
            },
            explicacionEnPantalla = enum<ExplicacionEnPantalla>(m[K.EXPLICACION]) ?: d.explicacionEnPantalla,
            nombreSala = enum<NombreSala>(m[K.SALA]) ?: d.nombreSala,
            calidad = enum<Calidad>(m[K.CALIDAD]) ?: d.calidad,
            tiempoPorObraSeg = m[K.TIEMPO]?.toIntOrNull()?.takeIf { it in Preferencias.TIEMPO_POR_OBRA_SEG } ?: d.tiempoPorObraSeg,
            orden = enum<Orden>(m[K.ORDEN]) ?: d.orden,
            galeria = m[K.GALERIA]?.takeIf { it.isNotBlank() },
            pared = enum<Pared>(m[K.PARED]) ?: d.pared,
            cartelObra = enum<CartelObra>(m[K.CARTEL]) ?: d.cartelObra,
            transicion = enum<Transicion>(m[K.TRANSICION]) ?: d.transicion,
            duracionTransicionMs = m[K.DURACION]?.toIntOrNull()?.takeIf { it in Preferencias.DURACION_TRANSICION_MS }
                ?: d.duracionTransicionMs,
            sincronizarCada = enum<SincronizarCada>(m[K.SINCRONIZAR_CADA]) ?: d.sincronizarCada,
            espacioMaximo = enum<EspacioMaximo>(m[K.ESPACIO_MAXIMO]) ?: d.espacioMaximo,
            carpetaDrive = CarpetaDrive.extraerId(m[K.CARPETA]),
            seleccionGalerias = decodificarSeleccion(m[K.GALERIAS_SELECCION]),
            galeriasNuevas = enum<GaleriasNuevas>(m[K.GALERIAS_NUEVAS]) ?: d.galeriasNuevas,
            reservaObras = m[K.RESERVA]?.toIntOrNull()?.takeIf { it in Preferencias.RESERVA_OBRAS } ?: d.reservaObras,
            vistaGalerias = enum<VistaGalerias>(m[K.VISTA_GALERIAS]) ?: d.vistaGalerias,
            ritmoBajada = enum<RitmoBajada>(m[K.RITMO_BAJADA]) ?: d.ritmoBajada,
        )
    }

    /** La selección de galerías como un solo texto: `id:1,id:0` (los ids de Drive son [A-Za-z0-9_-]: nunca traen ':' ni ','). */
    fun codificarSeleccion(sel: Map<String, Boolean>): String =
        sel.entries.filter { idValido(it.key) }.joinToString(",") { it.key + ":" + (if (it.value) "1" else "0") }

    fun decodificarSeleccion(texto: String?): Map<String, Boolean> {
        if (texto.isNullOrBlank()) return emptyMap()
        val salida = LinkedHashMap<String, Boolean>()
        for (par in texto.split(',')) {
            val partes = par.split(':')
            if (partes.size != 2 || !idValido(partes[0])) continue
            when (partes[1]) {
                "1" -> salida[partes[0]] = true
                "0" -> salida[partes[0]] = false
            }
        }
        return salida
    }

    private fun idValido(id: String) =
        id.isNotEmpty() && id.length <= 200 && id.all { it.isLetterOrDigit() && it.code < 128 || it == '_' || it == '-' }

    private inline fun <reified E : Enum<E>> enum(nombre: String?): E? =
        if (nombre == null) null else enumValues<E>().firstOrNull { it.name == nombre }

    private fun marco(v: String?): MarcoElegido? = when {
        v == null -> null
        v == MARCO_DEL_TEMA -> MarcoElegido.DEL_TEMA
        v == MARCO_AL_AZAR -> MarcoElegido.AL_AZAR
        v == MARCO_DE_LA_GALERIA -> MarcoElegido.DE_LA_GALERIA
        v.startsWith(MARCO_FIJO) -> v.removePrefix(MARCO_FIJO).takeIf { it in CatalogoMarcos.ids }?.let { MarcoElegido.FIJO(it) }
        else -> null
    }
}
