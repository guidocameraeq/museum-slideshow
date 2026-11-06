package local.museumslideshow.ajustes

import local.museumslideshow.temas.CatalogoColores
import local.museumslideshow.temas.CatalogoMarcos
import java.text.Normalizer

/**
 * Para probar en la tele por adb: `am start ... --es tema sala-oscura --es tam maximo`.
 * Claves: tema, modo, pared-claro, pared-oscuro, marco, tam, completa-con-marco, expl, sala, calidad,
 * tiempo, orden, galeria, pared, cartel, credito (si|no), transicion, duracion, sincronizar, espacio, ritmo, camino (copia|original), carpeta, nuevas, reserva, barra (5|10|20|nunca). Un valor inválido se ignora.
 * (`tildar` y `destildar` necesitan el catálogo: los resuelve la pantalla, no esta función.)
 * Estos cambios se GUARDAN en el almacén de ajustes (`reset=si` los vuelve todos a los valores por defecto).
 */
object PreferenciasDesdeTexto {
    private val MARCOS get() = CatalogoMarcos.ids
    private val PAREDES_CLARAS get() = CatalogoColores.claros.map { it.id }.toSet()
    private val PAREDES_OSCURAS get() = CatalogoColores.oscuros.map { it.id }.toSet()

    fun normalizar(s: String): String =
        Normalizer.normalize(s.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace('_', '-')
            .replace(Regex("\\s+"), "-")

    fun aplicar(base: Preferencias, valores: Map<String, String>): Preferencias {
        var p = base
        for ((clave, crudo) in valores) {
            val v = normalizar(crudo)
            p = when (normalizar(clave)) {
                "tema" -> Tema.deId(v)?.let { p.copy(tema = it) } ?: p
                "modo" -> when (v) {
                    "claro" -> p.copy(modo = Modo.CLARO)
                    "oscuro" -> p.copy(modo = Modo.OSCURO)
                    "automatico" -> p.copy(modo = Modo.AUTOMATICO)
                    else -> p
                }
                "pared-claro" -> when {
                    v == "tema" -> p.copy(colorParedClaro = null)
                    v in PAREDES_CLARAS -> p.copy(colorParedClaro = v)
                    else -> p
                }
                "pared-oscuro" -> when {
                    v == "tema" -> p.copy(colorParedOscuro = null)
                    v in PAREDES_OSCURAS -> p.copy(colorParedOscuro = v)
                    else -> p
                }
                "marco" -> when {
                    v == "tema" -> p.copy(marco = MarcoElegido.DEL_TEMA)
                    v == "azar" -> p.copy(marco = MarcoElegido.AL_AZAR)
                    v == "galeria" -> p.copy(marco = MarcoElegido.DE_LA_GALERIA)
                    v in MARCOS -> p.copy(marco = MarcoElegido.FIJO(v))
                    else -> p
                }
                "tam" -> when (v) {
                    "normal" -> p.copy(tamano = Tamano.NORMAL)
                    "grande" -> p.copy(tamano = Tamano.GRANDE)
                    "maximo" -> p.copy(tamano = Tamano.MAXIMO)
                    "completa" -> p.copy(tamano = Tamano.PANTALLA_COMPLETA)
                    else -> p
                }
                "completa-con-marco" -> booleano(v)?.let { p.copy(pantallaCompletaConMarco = it) } ?: p
                "expl" -> when (v) {
                    "entra", "cuando-entra" -> p.copy(explicacionEnPantalla = ExplicacionEnPantalla.CUANDO_ENTRA)
                    "nunca" -> p.copy(explicacionEnPantalla = ExplicacionEnPantalla.NUNCA)
                    else -> p
                }
                "sala" -> when (v) {
                    "siempre" -> p.copy(nombreSala = NombreSala.SIEMPRE)
                    "segundos", "unos-segundos" -> p.copy(nombreSala = NombreSala.UNOS_SEGUNDOS)
                    "contador", "solo-contador" -> p.copy(nombreSala = NombreSala.SOLO_CONTADOR)
                    "nunca" -> p.copy(nombreSala = NombreSala.NUNCA)
                    else -> p
                }
                "calidad" -> when (v) {
                    "4k" -> p.copy(calidad = Calidad.K4)
                    "1080" -> p.copy(calidad = Calidad.HD1080)
                    else -> p
                }
                "tiempo" -> v.toIntOrNull()?.takeIf { it in Preferencias.TIEMPO_POR_OBRA_SEG }
                    ?.let { p.copy(tiempoPorObraSeg = it) } ?: p
                "orden" -> when (v) {
                    "galeria" -> p.copy(orden = Orden.GALERIA)
                    "mezclar" -> p.copy(orden = Orden.MEZCLAR)
                    else -> p
                }
                // El id de la galería se respeta tal cual (puede tener mayúsculas); "todas" = ninguna en particular.
                "galeria" -> when {
                    crudo.isBlank() -> p
                    v == "todas" -> p.copy(galeria = null)
                    else -> p.copy(galeria = crudo.trim())
                }
                "pared" -> when (v) {
                    "apagada" -> p.copy(pared = Pared.APAGADA)
                    "2", "hasta-2" -> p.copy(pared = Pared.HASTA_2)
                    "3", "hasta-3" -> p.copy(pared = Pared.HASTA_3)
                    else -> p
                }
                "cartel" -> when (v) {
                    "siempre" -> p.copy(cartelObra = CartelObra.SIEMPRE)
                    "segundos", "unos-segundos" -> p.copy(cartelObra = CartelObra.UNOS_SEGUNDOS)
                    "nunca" -> p.copy(cartelObra = CartelObra.NUNCA)
                    else -> p
                }
                "credito" -> booleano(v)?.let { p.copy(mostrarCredito = it) } ?: p
                "transicion" -> when (v) {
                    "fundido" -> p.copy(transicion = Transicion.FUNDIDO)
                    "ninguna" -> p.copy(transicion = Transicion.NINGUNA)
                    else -> p
                }
                "duracion" -> v.toIntOrNull()?.takeIf { it in Preferencias.DURACION_TRANSICION_MS }
                    ?.let { p.copy(duracionTransicionMs = it) } ?: p
                "sincronizar" -> when (v) {
                    "1h", "1" -> p.copy(sincronizarCada = SincronizarCada.UNA_HORA)
                    "6h", "6" -> p.copy(sincronizarCada = SincronizarCada.SEIS_HORAS)
                    "24h", "24" -> p.copy(sincronizarCada = SincronizarCada.VEINTICUATRO_HORAS)
                    "mano", "a-mano", "solo-a-mano" -> p.copy(sincronizarCada = SincronizarCada.SOLO_A_MANO)
                    else -> p
                }
                "espacio" -> when (v) {
                    "0.5", "0,5", "medio" -> p.copy(espacioMaximo = EspacioMaximo.MEDIO_GB)
                    "1" -> p.copy(espacioMaximo = EspacioMaximo.UN_GB)
                    "2" -> p.copy(espacioMaximo = EspacioMaximo.DOS_GB)
                    else -> p
                }
                // ID o link de Drive (se respeta el texto tal cual: tiene mayúsculas). "defecto" = el de local.properties.
                "carpeta" -> when {
                    v == "defecto" || v == "fabrica" -> p.copy(carpetaDrive = null)
                    else -> CarpetaDrive.extraerId(crudo)?.let { p.copy(carpetaDrive = it) } ?: p
                }
                "nuevas", "galerias-nuevas" -> when (v) {
                    "tildadas" -> p.copy(galeriasNuevas = GaleriasNuevas.TILDADAS)
                    "destildadas" -> p.copy(galeriasNuevas = GaleriasNuevas.DESTILDADAS)
                    else -> p
                }
                "vista", "vista-galerias" -> when (v) {
                    "tarjetas" -> p.copy(vistaGalerias = VistaGalerias.TARJETAS)
                    "lista" -> p.copy(vistaGalerias = VistaGalerias.LISTA)
                    else -> p
                }
                "ritmo", "ritmo-de-bajada" -> when (v) {
                    "tranquilo" -> p.copy(ritmoBajada = RitmoBajada.TRANQUILO)
                    "normal" -> p.copy(ritmoBajada = RitmoBajada.NORMAL)
                    else -> p
                }
                "camino", "camino-obras" -> when (v) {
                    "copia", "copia-primero" -> p.copy(caminoObras = CaminoObras.COPIA_PRIMERO)
                    "original", "original-primero" -> p.copy(caminoObras = CaminoObras.ORIGINAL_PRIMERO)
                    else -> p
                }
                "barra", "cierre-barra" -> when (v) {
                    "5", "5s" -> p.copy(cierreBarra = CierreBarra.S5)
                    "10", "10s" -> p.copy(cierreBarra = CierreBarra.S10)
                    "20", "20s" -> p.copy(cierreBarra = CierreBarra.S20)
                    "nunca" -> p.copy(cierreBarra = CierreBarra.NUNCA)
                    else -> p
                }
                // Solo para probar por adb (la pantalla de Ajustes ofrece 20 / 50 / 100 / 200).
                "reserva" -> v.toIntOrNull()?.takeIf { it in Preferencias.RESERVA_OBRAS }?.let { p.copy(reservaObras = it) } ?: p
                else -> p
            }
        }
        return p
    }

    private fun booleano(v: String): Boolean? = when (v) {
        "si", "true", "1", "yes" -> true
        "no", "false", "0" -> false
        else -> null
    }
}
