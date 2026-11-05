package local.museumslideshow.acomodo

import local.museumslideshow.ajustes.CartelObra
import local.museumslideshow.ajustes.ExplicacionEnPantalla
import local.museumslideshow.ajustes.MarcoElegido
import local.museumslideshow.ajustes.NombreSala
import local.museumslideshow.ajustes.Preferencias
import local.museumslideshow.ajustes.Tamano
import local.museumslideshow.catalogo.Forma
import local.museumslideshow.catalogo.Obra
import local.museumslideshow.temas.CatalogoMarcos
import local.museumslideshow.temas.MarcoDef
import local.museumslideshow.temas.TemaDef
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * El que "piensa antes de dibujar": decide dónde va cada obra, su marco y sus textos, en px del escenario
 * de 1920x1080. Puerto del acomodador de `docs/diseno/03-temas-v3.html` (planSingle, planPared, R.lb, placeSala)
 * con los cambios de `decisiones.md` (cartel centrado en altura, pantalla completa, contador, ajustes).
 * No dibuja ni mide texto: para eso recibe un [MedidorTexto].
 */
object Acomodador {

    /** El marco de una obra según el tema y los ajustes (ya resuelto con la regla de marcos sin foto). */
    fun marcoDe(obra: Obra, tema: TemaDef, prefs: Preferencias): MarcoDef =
        CatalogoMarcos.resolver(idMarco(obra, tema, prefs))

    /** El id del marco elegido para la obra, antes de resolver la regla de marcos sin foto. */
    fun idMarco(obra: Obra, tema: TemaDef, prefs: Preferencias): String {
        if (tema.sinMarcoSiempre) return "sin"
        val forma = obra.ficha?.forma ?: Forma.RECTANGULAR
        if (forma != Forma.RECTANGULAR) return "dorado-redondo"
        val id = when (val m = prefs.marco) {
            MarcoElegido.DEL_TEMA -> tema.marcoDelTema
            is MarcoElegido.FIJO -> if (m.id in CatalogoMarcos.ids) m.id else tema.marcoDelTema
            MarcoElegido.AL_AZAR -> {
                val l = CatalogoMarcos.rectangulares
                l[Math.floorMod(obra.driveId.hashCode(), l.size)]
            }
            MarcoElegido.DE_LA_GALERIA ->
                obra.ficha?.marco?.trim()?.lowercase()?.takeIf { it in CatalogoMarcos.ids } ?: tema.marcoDelTema
        }
        // el redondo es solo para obras redondas u ovaladas: una rectangular que lo pide usa el dorado
        return if (id == "dorado-redondo") "dorado" else id
    }

    fun acomodar(p: PedidoEscena, medidor: MedidorTexto): Disposicion {
        require(p.obras.isNotEmpty()) { "Sin obras no hay escena" }
        // OK alterna: Tamaño normal -> completa; Tamaño "completa" -> vuelve a Grande.
        val completa = (p.prefs.tamano == Tamano.PANTALLA_COMPLETA) != p.pantallaCompleta
        val elegida = p.obras[p.elegida.coerceIn(0, p.obras.size - 1)]
        if (completa) return pantallaCompleta(p, elegida)
        val tamano = p.tema.tamanoFijo ?: if (p.prefs.tamano == Tamano.PANTALLA_COMPLETA) Tamano.GRANDE else p.prefs.tamano
        return when {
            p.tema.esLibro -> libro(p, medidor, elegida, tamano)
            p.obras.size >= 2 && p.tema.admitePared -> pared(p, medidor, tamano)
            else -> solo(p, medidor, elegida, tamano)
        }
    }

    // ---------------------------------------------------------------- piezas comunes

    private class Dims(val marco: MarcoDef, val f: Float, val fil: Float, val m: Float) {
        val e: Float get() = 2 * (f + fil + m)
        val aro: Float get() = f + fil + m
    }

    private fun dims(marco: MarcoDef, tema: TemaDef, t: Tamano): Dims {
        val m = if (tema.conPaspartu && marco.id != "sin") Constantes.paspartu(t) else 0
        return Dims(marco, marco.grosorDe(t).toFloat(), marco.fileteDe(t).toFloat(), m.toFloat())
    }

    private fun forma(o: Obra) = o.ficha?.forma ?: Forma.RECTANGULAR

    private fun proporcion(o: Obra): Float = if (o.alto > 0 && o.ancho > 0) o.ancho.toFloat() / o.alto else 1f

    /** Lado interno de la obra que cabe en una caja bw x bh con marco `e`, sin deformar. Redonda = cuadrada. */
    private fun ajustar(o: Obra, bw: Float, bh: Float, e: Float): Pair<Float, Float> {
        val aw = max(1f, bw - e)
        val ah = max(1f, bh - e)
        if (forma(o) == Forma.REDONDA) {
            val s = min(aw, ah).roundToInt().toFloat()
            return s to s
        }
        val r = proporcion(o)
        val w = min(aw, ah * r)
        return w.roundToInt().toFloat() to (w / r).roundToInt().toFloat()
    }

    private fun colocada(o: Obra, d: Dims, x: Float, y: Float, iw: Float, ih: Float) = ObraColocada(
        obra = o,
        interior = Rect(x + d.aro, y + d.aro, iw, ih),
        exterior = Rect(x, y, iw + d.e, ih + d.e),
        marco = d.marco, grosorMarco = d.f, filete = d.fil, paspartu = d.m, forma = forma(o),
    )

    private fun tieneExplicacion(o: Obra) = o.ficha?.explicacion?.isNotBlank() == true

    // ---------------------------------------------------------------- pantalla completa

    private fun pantallaCompleta(p: PedidoEscena, o: Obra): Disposicion {
        val marco = if (p.prefs.pantallaCompletaConMarco) marcoDe(o, p.tema, p.prefs) else CatalogoMarcos.resolver("sin")
        val d = dims(marco, p.tema, Tamano.PANTALLA_COMPLETA)
        val (iw, ih) = ajustar(o, Constantes.ANCHO, Constantes.ALTO, d.e)
        val x = (Constantes.ANCHO - (iw + d.e)) / 2
        val y = (Constantes.ALTO - (ih + d.e)) / 2
        return Disposicion(listOf(colocada(o, d, x, y, iw, ih)), emptyList(), null, false, null)
    }

    // ---------------------------------------------------------------- una obra sola

    private enum class Lugar { DERECHA, IZQUIERDA, ABAJO }

    private fun solo(p: PedidoEscena, medidor: MedidorTexto, o: Obra, tamano: Tamano): Disposicion {
        val prefs = p.prefs
        val S = Constantes.SAFE
        val d = dims(marcoDe(o, p.tema, prefs), p.tema, tamano)
        val (cw, ch) = Constantes.cajaSola(tamano)
        val (iw, ih) = ajustar(o, cw, ch, d.e)
        val w = iw + d.e
        val h = ih + d.e
        val ficha = o.ficha
        val hayExp = tieneExplicacion(o)
        val pidioExp = p.forzarExplicacion && hayExp
        val sinCartel = prefs.cartelObra == CartelObra.NUNCA

        fun terminar(x: Float, y: Float, textos: List<Texto>, nivel: NivelInfo?): Disposicion {
            val obra = colocada(o, d, x, y, iw, ih)
            val todos = textos + textosDeSala(p, medidor, listOf(obra.exterior) + textos.map { it.rect })
            return Disposicion(listOf(obra), todos, null, pidioExp && nivel != NivelInfo.COMPLETA, nivel)
        }

        val centroX = S.x + (S.ancho - w) / 2
        val centroY = S.y + (S.alto - h) / 2
        if (ficha == null) return terminar(centroX, centroY, emptyList(), null)

        // Niveles a probar, del más completo al más mínimo (par = nivel, con aviso "OK: leer sobre la obra").
        val niveles = mutableListOf<Pair<NivelInfo, Boolean>>()
        if (hayExp && ((!sinCartel && prefs.explicacionEnPantalla == ExplicacionEnPantalla.CUANDO_ENTRA) || p.forzarExplicacion)) {
            niveles += NivelInfo.COMPLETA to false
        }
        if (!sinCartel) {
            for (lv in listOf(NivelInfo.FICHA, NivelInfo.MINIMA)) {
                if (hayExp) niveles += lv to true
                niveles += lv to false
            }
        }
        if (niveles.isEmpty()) return terminar(centroX, centroY, emptyList(), null)

        // Espacio libre dentro del margen de seguridad; el lugar más grande primero (empate: derecha).
        val ladoW = S.ancho - w - Constantes.GAP
        val abajoH = S.alto - h - Constantes.GAP
        val lugares = listOf(
            Triple(Lugar.DERECHA, ladoW, S.alto),
            Triple(Lugar.IZQUIERDA, ladoW, S.alto),
            Triple(Lugar.ABAJO, S.ancho, abajoH),
        ).sortedByDescending { max(0f, it.second) * max(0f, it.third) }

        for ((nivel, ok) in niveles) for ((lugar, aw, ah) in lugares) {
            if (aw <= 0f || ah <= 0f) continue
            val lado = lugar != Lugar.ABAJO
            val (anchoMin, anchoMax) = Constantes.anchoCartel(nivel)
            val lw = floor(
                if (lado) min(aw, anchoMax)
                else min(min(aw, max(w, 640f)), if (nivel == NivelInfo.COMPLETA) 1000f else 820f),
            )
            if (lw < anchoMin) continue
            val m = medidor.medirCartel(ficha, nivel, ok, lw, p.tema, p.modo, prefs.mostrarCredito)
            if (!m.entra || m.alto > ah) continue
            val ox: Float
            val oy: Float
            val lx: Float
            val ly: Float
            if (lado) {
                val x0 = S.x + (S.ancho - (w + Constantes.GAP + lw)) / 2
                oy = S.y + (S.alto - h) / 2
                ox = if (lugar == Lugar.DERECHA) x0 else x0 + lw + Constantes.GAP
                lx = if (lugar == Lugar.DERECHA) x0 + w + Constantes.GAP else x0
                // El cartel al costado va centrado en altura con la obra (decisiones.md), siempre dentro del margen.
                ly = (oy + (h - m.alto) / 2).coerceIn(S.y, max(S.y, S.abajo - m.alto))
            } else {
                val y0 = S.y + (S.alto - (h + Constantes.GAP + m.alto)) / 2
                ox = S.x + (S.ancho - w) / 2
                oy = y0
                ly = y0 + h + Constantes.GAP
                lx = ox.coerceIn(S.x, max(S.x, S.derecha - lw))
            }
            return terminar(ox, oy, listOf(Texto.Cartel(Rect(lx, ly, lw, m.alto), nivel, ok)), nivel)
        }

        // Ningún lugar libre alcanza: cuadrito flotante con lo mínimo, en una esquina de la obra. No aplica con cartel NUNCA.
        if (sinCartel) return terminar(centroX, centroY, emptyList(), null)
        val chip = medidor.medirChip(ficha, p.tema, p.modo)
        val chipRect = Rect(centroX + 20f, centroY + h - 20f - chip.alto, chip.ancho, chip.alto)
        return terminar(centroX, centroY, listOf(Texto.Chip(chipRect)), NivelInfo.MINIMA)
    }

    // ---------------------------------------------------------------- Pared de 2 o 3 obras

    private fun pared(p: PedidoEscena, medidor: MedidorTexto, tamano: Tamano): Disposicion {
        val prefs = p.prefs
        val S = Constantes.SAFE
        val obras = p.obras.take(3)
        val n = obras.size
        val (cw, ch) = Constantes.cajaPared(tamano)
        val gap = Constantes.airePared(tamano, n)
        val ds = obras.map { dims(marcoDe(it, p.tema, prefs), p.tema, tamano) }
        val redonda = obras.map { forma(it) == Forma.REDONDA }
        val rs = obras.mapIndexed { i, o -> if (redonda[i]) 1f else proporcion(o) }
        val conNombres = prefs.cartelObra != CartelObra.NUNCA
        val eMax = ds.maxOf { it.e }

        var ih = ch - eMax - Constantes.AIRE_NOMBRES - 90f
        var iws = FloatArray(n)
        var cws = FloatArray(n)
        var ihI = 1f
        var strip = 0f
        var aire = 0f
        var tw = 0f
        var rowH = 0f
        var th = 0f
        for (vuelta in 0 until 60) {
            ihI = max(1f, ih.roundToInt().toFloat())
            iws = FloatArray(n) { i -> (ihI * rs[i]).roundToInt().toFloat() }
            cws = FloatArray(n) { i -> max(iws[i] + ds[i].e, Constantes.ANCHO_MIN_COLUMNA) }
            strip = 0f
            if (conNombres) obras.forEachIndexed { i, o ->
                val f = o.ficha
                if (f != null) strip = max(strip, medidor.medirNombre(f, cws[i], p.tema, p.modo).alto)
            }
            aire = if (strip > 0f) Constantes.AIRE_NOMBRES else 0f
            tw = cws.sum() + (n - 1) * gap
            rowH = ds.maxOf { ihI + it.e }
            th = rowH + aire + strip
            if (tw <= cw + 0.5f && th <= ch + 0.5f) break
            // Las obras se achican lo justo para que entren con su franja de nombres.
            ih = ihI * min(cw / tw, ch / th) * 0.995f
        }

        val x0 = S.x + (S.ancho - tw) / 2
        val y0 = S.y + (S.alto - th) / 2
        var cx = x0
        val colocadas = mutableListOf<ObraColocada>()
        val textos = mutableListOf<Texto>()
        obras.forEachIndexed { i, o ->
            val d = ds[i]
            val extW = iws[i] + d.e
            val extH = ihI + d.e
            colocadas += colocada(o, d, cx + (cws[i] - extW) / 2, y0 + (rowH - extH) / 2, iws[i], ihI)
            if (conNombres && o.ficha != null && strip > 0f) {
                textos += Texto.Nombre(Rect(cx, y0 + rowH + aire, cws[i], strip), i)
            }
            cx += cws[i] + gap
        }
        val elegida = p.obras[p.elegida.coerceIn(0, p.obras.size - 1)]
        val rects = colocadas.map { it.exterior } + textos.map { it.rect }
        return Disposicion(
            colocadas, textos + textosDeSala(p, medidor, rects), null,
            explicacionNoEntra = p.forzarExplicacion && tieneExplicacion(elegida), nivel = null,
        )
    }

    // ---------------------------------------------------------------- Libro de arte

    private fun libro(p: PedidoEscena, medidor: MedidorTexto, o: Obra, tamano: Tamano): Disposicion {
        val prefs = p.prefs
        val base = Constantes.LIBRO_OBRA
        val caja = when (tamano) {
            Tamano.NORMAL -> {
                val f = Constantes.LIBRO_FACTOR_NORMAL
                Rect(base.x + base.ancho * (1 - f) / 2, base.y + base.alto * (1 - f) / 2, base.ancho * f, base.alto * f)
            }
            Tamano.GRANDE -> base
            else -> Constantes.LIBRO_OBRA_MAXIMA
        }
        val d = dims(CatalogoMarcos.resolver("sin"), p.tema, tamano)
        val (iw, ih) = ajustar(o, caja.ancho, caja.alto, 0f)
        val obra = colocada(o, d, caja.x + (caja.ancho - iw) / 2, caja.y + (caja.alto - ih) / 2, iw, ih)

        val corrido: String? = when (prefs.nombreSala) {
            NombreSala.NUNCA -> null
            NombreSala.SOLO_CONTADOR -> p.contador
            else -> p.nombreSala ?: p.contador
        }
        val columna = Constantes.LIBRO_COLUMNA
        val ficha = o.ficha
        val hayExp = tieneExplicacion(o)
        val pidioExp = p.forzarExplicacion && hayExp
        val sinCartel = prefs.cartelObra == CartelObra.NUNCA

        fun pagina(tam: Float?, aviso: Boolean) = PaginaLibro(caja, columna, tam, aviso, corrido)

        if (ficha == null) return Disposicion(listOf(obra), emptyList(), pagina(null, false), false, null)

        if (hayExp && ((!sinCartel && prefs.explicacionEnPantalla == ExplicacionEnPantalla.CUANDO_ENTRA) || p.forzarExplicacion)) {
            // La letra de la explicación baja de 26 a 17 hasta que entra entera; si no entra, no se muestra.
            for (s in Constantes.LIBRO_EXPLICACION_MAX downTo Constantes.LIBRO_EXPLICACION_MIN) {
                val m = medidor.medirPaginaLibro(ficha, corrido, true, s.toFloat(), false, columna.ancho, p.modo, prefs.mostrarCredito)
                if (m.entra && m.alto <= columna.alto) {
                    return Disposicion(listOf(obra), emptyList(), pagina(s.toFloat(), false), false, NivelInfo.COMPLETA)
                }
            }
        }
        if (sinCartel) return Disposicion(listOf(obra), emptyList(), pagina(null, false), pidioExp, null)
        return Disposicion(listOf(obra), emptyList(), pagina(null, hayExp), pidioExp, NivelInfo.FICHA)
    }

    // ---------------------------------------------------------------- nombre de la sala y contador

    /**
     * Nombre de la sala arriba a la izquierda si no pisa nada; si pisa (o no hay nombre), un contador chico
     * en el rincón libre más grande. `obstaculos` = obras (con su marco) y carteles ya colocados.
     */
    private fun textosDeSala(p: PedidoEscena, medidor: MedidorTexto, obstaculos: List<Rect>): List<Texto> {
        val pol = p.prefs.nombreSala
        if (pol == NombreSala.NUNCA) return emptyList()
        if (pol != NombreSala.SOLO_CONTADOR && !p.nombreSala.isNullOrBlank()) {
            val m = medidor.medirSala(p.nombreSala, p.tema, p.modo)
            val r = Rect(Constantes.SALA_X, Constantes.SALA_Y, m.ancho, m.alto)
            val caja = r.agrandar(Constantes.SALA_AIRE_H, Constantes.SALA_AIRE_V)
            val dentro = r.derecha <= Constantes.SAFE.derecha
            if (dentro && m.entra && obstaculos.none { caja.pisa(it) }) return listOf(Texto.Sala(r))
        }
        if (p.contador.isBlank()) return emptyList()
        val m = medidor.medirContador(p.contador, p.tema, p.modo)
        val w = m.ancho
        val h = m.alto
        val izq = Constantes.SALA_X
        val der = Constantes.ANCHO - Constantes.SALA_X - w
        val arr = Constantes.SALA_Y
        val aba = Constantes.ALTO - Constantes.SALA_Y - h
        val candidatos = listOf(Rect(izq, arr, w, h), Rect(der, arr, w, h), Rect(izq, aba, w, h), Rect(der, aba, w, h))
        val libres = candidatos.filter { c ->
            val caja = c.agrandar(Constantes.SALA_AIRE_H, Constantes.SALA_AIRE_V)
            obstaculos.none { caja.pisa(it) }
        }
        // "Más grande" = el que tiene más aire hasta la cosa más cercana (sin nada cerca: el primero).
        val mejor = libres.maxByOrNull { c ->
            if (obstaculos.isEmpty()) 0f else obstaculos.minOf { distancia(c, it) }
        } ?: return emptyList()
        return listOf(Texto.Contador(mejor))
    }

    private fun distancia(a: Rect, b: Rect): Float {
        val dx = max(0f, max(a.x, b.x) - min(a.derecha, b.derecha))
        val dy = max(0f, max(a.y, b.y) - min(a.abajo, b.abajo))
        return hypot(dx, dy)
    }
}
