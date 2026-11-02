package local.museumslideshow.galeria

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableFloatStateOf
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import local.museumslideshow.R
import local.museumslideshow.acomodo.Acomodador
import local.museumslideshow.acomodo.Constantes
import local.museumslideshow.acomodo.Disposicion
import local.museumslideshow.acomodo.NivelInfo
import local.museumslideshow.acomodo.ObraColocada
import local.museumslideshow.acomodo.PaginaLibro
import local.museumslideshow.acomodo.PedidoEscena
import local.museumslideshow.acomodo.Rect
import local.museumslideshow.acomodo.Texto
import local.museumslideshow.ajustes.Calidad
import local.museumslideshow.ajustes.CartelObra
import local.museumslideshow.ajustes.ExplicacionEnPantalla
import local.museumslideshow.ajustes.Modo
import local.museumslideshow.ajustes.NombreSala
import local.museumslideshow.ajustes.Preferencias
import local.museumslideshow.ajustes.Tamano
import local.museumslideshow.ajustes.Tema
import local.museumslideshow.catalogo.Forma
import local.museumslideshow.catalogo.Obra
import local.museumslideshow.temas.CatalogoMarcos
import local.museumslideshow.temas.ColorPared
import local.museumslideshow.temas.RegistroTemas
import local.museumslideshow.temas.Rol
import local.museumslideshow.temas.TemaDef

/** Mantener OK: lo que pidió el usuario sobre la explicación de la escena. */
enum class EstadoExplicacion { AUTO, FORZADA, OCULTA }

/** Todo lo que define una escena: si cambia algo de esto, hay que armar otra. */
data class ClaveEscena(
    val def: EscenaDef,
    val completa: Boolean,
    val expl: EstadoExplicacion,
    val elegida: Int,
    val modo: Modo,
    val prefs: Preferencias,
)

enum class TipoDibujable { CARTEL, NOMBRE, CHIP, SALA, CONTADOR }

/** Un texto ya medido y colocado (con su cajita de fondo si la tiene). `obraIndice` solo vale para los nombres. */
class Dibujable(
    val tipo: TipoDibujable,
    val rect: Rect,
    val bloques: List<BloqueTexto>,
    val fondo: Int?,
    val radioFondo: Float,
    val obraIndice: Int,
    val sombraTexto: Boolean,
)

/** La columna de texto de la página derecha del Libro de arte. */
class LibroDibujable(val pagina: PaginaMedida, val columna: Rect, val reglaColor: Int, val reglaGrosor: Float)

/**
 * Una escena lista para mostrar: la disposición (lugares), las obras abiertas, los textos ya medidos y todo lo que
 * hace falta para dibujar las dos capas sin volver a pensar nada. `cartelAlpha` y `salaAlpha` bajan solos con
 * los ajustes "Unos segundos".
 */
class EscenaLista(
    val clave: ClaveEscena,
    val disp: Disposicion,
    val tema: TemaDef,
    val modo: Modo,
    val colorPared: ColorPared,
    val abiertas: ObrasAbiertas,
    val dibujables: List<Dibujable>,
    val libro: LibroDibujable?,
    val esVista: Boolean,
    val lomoX: Float,
    val marcos: List<DibujanteMarco>,
    val sombras: List<Sombra>,
    val ovalos: List<Path?>,
) {
    val def: EscenaDef get() = clave.def
    val cartelAlpha = mutableFloatStateOf(1f)
    val salaAlpha = mutableFloatStateOf(1f)
    private val trabajos = ArrayList<Job>()

    /** Recursos de la capa de superficie (se arman la primera vez que se dibuja). */
    var recursosSuperficie: Any? = null

    /** ¿Se está viendo la explicación de la obra (al lado, en el libro o en la vista Explicación)? */
    val muestraExplicacion: Boolean
        get() = esVista || disp.nivel == NivelInfo.COMPLETA || disp.libro?.tamanoExplicacionPx != null

    fun cargadasPorId(): Map<String, ObraCargada> {
        val m = HashMap<String, ObraCargada>()
        disp.obras.forEachIndexed { i, o -> abiertas.obras.getOrNull(i)?.let { m[o.obra.driveId] = it } }
        return m
    }


    fun iniciarTemporizadores(scope: CoroutineScope, prefs: Preferencias) {
        if (prefs.cartelObra == CartelObra.UNOS_SEGUNDOS) {
            trabajos += scope.launch {
                cartelAlpha.floatValue = 1f
                delay(ConstantesGaleria.CARTEL_UNOS_SEGUNDOS_MS)
                animate(1f, 0f, animationSpec = tween(ConstantesGaleria.DESVANECER_TEXTO_MS)) { v, _ -> cartelAlpha.floatValue = v }
            }
        }
        if (prefs.nombreSala == NombreSala.UNOS_SEGUNDOS) {
            trabajos += scope.launch {
                salaAlpha.floatValue = 1f
                delay(ConstantesGaleria.SALA_UNOS_SEGUNDOS_MS)
                animate(1f, 0f, animationSpec = tween(ConstantesGaleria.DESVANECER_TEXTO_MS)) { v, _ -> salaAlpha.floatValue = v }
            }
        }
    }

    fun liberar() {
        trabajos.forEach { it.cancel() }
        trabajos.clear()
        abiertas.soltar()
    }
}

/**
 * Arma una [EscenaLista]: acomoda (con el Acomodador y el medidor de texto de verdad), abre las obras al tamaño
 * de su lugar y mide los textos. `construir` hay que llamarlo desde el hilo principal (el texto lo pide).
 */
class ConstructorEscena(
    private val ctx: Context,
    dirBase: File,
    private val medidor: MedidorTextoCompose,
    private val composicion: ComposicionTexto,
) {
    private val cargador = CargadorObras(dirBase)

    private fun escala(c: Calidad) = if (c == Calidad.HD1080) 1f else 2f
    private fun presupuestoPx(c: Calidad): Long =
        if (c == Calidad.HD1080) ConstantesGaleria.SUPERFICIE_1080_ANCHO.toLong() * ConstantesGaleria.SUPERFICIE_1080_ALTO
        else ConstantesGaleria.SUPERFICIE_4K_ANCHO.toLong() * ConstantesGaleria.SUPERFICIE_4K_ALTO

    suspend fun construir(clave: ClaveEscena, previa: EscenaLista?): EscenaLista {
        val prefs = clave.prefs
        val def = clave.def
        val modo = clave.modo
        val contador = ctx.getString(R.string.formato_contador, def.posicion, def.total)
        val nombreSala = ctx.getString(R.string.formato_sala_contador, def.galeriaTitulo, contador)
        val elegida = clave.elegida.coerceIn(0, def.obras.lastIndex)
        val prefsPedido = if (clave.expl == EstadoExplicacion.OCULTA) prefs.copy(explicacionEnPantalla = ExplicacionEnPantalla.NUNCA) else prefs
        var tema = RegistroTemas.de(prefs.tema)
        val pedido = PedidoEscena(
            def.obras, elegida, tema, modo, prefsPedido, clave.completa, clave.expl == EstadoExplicacion.FORZADA, nombreSala, contador,
        )
        var disp = Acomodador.acomodar(pedido, medidor)
        var esVista = false
        var lomo = Constantes.LIBRO.x + 960f

        if (clave.expl == EstadoExplicacion.FORZADA && disp.explicacionNoEntra) {
            // La vista Explicación: la obra elegida y su texto completo, estilo Libro de arte.
            esVista = true
            val libroTema = RegistroTemas.de(Tema.LIBRO_DE_ARTE)
            val obra = def.obras[elegida]
            val prefsVista = prefs.copy(
                tema = Tema.LIBRO_DE_ARTE, tamano = Tamano.GRANDE, explicacionEnPantalla = ExplicacionEnPantalla.CUANDO_ENTRA,
                cartelObra = CartelObra.SIEMPRE, nombreSala = NombreSala.SIEMPRE,
            )
            val dV = Acomodador.acomodar(
                PedidoEscena(listOf(obra), 0, libroTema, modo, prefsVista, false, true, nombreSala, contador), medidor,
            )
            tema = libroTema
            if (dV.libro?.tamanoExplicacionPx != null) {
                disp = dV
            } else {
                lomo = ConstantesGaleria.VISTA_LOMO_X
                disp = vistaAncha(obra, modo, nombreSala)
            }
        }

        val colorPared = tema.colorPared(modo, if (modo == Modo.OSCURO) prefs.colorParedOscuro else prefs.colorParedClaro)

        // Las obras (y el fondo de Inmersivo) se abren en un hilo de fondo.
        val k = escala(prefs.calidad)
        val previas = previa?.cargadasPorId() ?: emptyMap()
        val fondoObra = if (tema.fondoDesenfocado) disp.obras.getOrNull(disp.obras.size / 2)?.obra else null
        var hecho: ObrasAbiertas? = null
        try {
            withContext(Dispatchers.IO) {
                hecho = ObrasAbiertas(cargador.abrir(disp, k, presupuestoPx(prefs.calidad), previas), fondoObra?.let { cargador.abrirFondo(it) })
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            hecho?.soltar()   // si se canceló justo al terminar, no quedan imágenes colgadas
            throw e
        }
        val abiertas = hecho!!

        val dibujables = ArrayList<Dibujable>()
        val tip = tema.tipografia(modo)
        val sombraTexto = tema.fondoDesenfocado && modo == Modo.OSCURO
        for (t in disp.textos) {
            when (t) {
                is Texto.Cartel -> disp.obras.firstOrNull()?.obra?.ficha?.let { f ->
                    val m = medidor.cartel(f, t.nivel, t.avisoOk, t.rect.ancho, tema, modo)
                    dibujables += Dibujable(TipoDibujable.CARTEL, t.rect, m.bloques, tip.fondoCartel?.toInt(), 0f, 0, sombraTexto)
                }
                is Texto.Nombre -> disp.obras.getOrNull(t.obraIndice)?.obra?.ficha?.let { f ->
                    val m = medidor.nombre(f, t.rect.ancho, tema, modo)
                    dibujables += Dibujable(TipoDibujable.NOMBRE, t.rect, m.bloques, null, 0f, t.obraIndice, sombraTexto)
                }
                is Texto.Chip -> disp.obras.firstOrNull()?.obra?.ficha?.let { f ->
                    val m = medidor.chip(f, tema, modo)
                    dibujables += Dibujable(TipoDibujable.CHIP, t.rect, m.bloques, tip.fondoChip.toInt(), 6f, 0, false)
                }
                is Texto.Sala -> {
                    val m = medidor.sala(nombreSala, tema, modo)
                    val e = if (tip.tiene(Rol.SALA)) tip[Rol.SALA] else null
                    dibujables += Dibujable(TipoDibujable.SALA, t.rect, m.bloques, e?.fondo?.toInt(), 4f, 0, false)
                }
                is Texto.Contador -> {
                    val m = medidor.contador(contador, tema, modo)
                    val e = if (tip.tiene(Rol.CONTADOR)) tip[Rol.CONTADOR] else null
                    dibujables += Dibujable(TipoDibujable.CONTADOR, t.rect, m.bloques, e?.fondo?.toInt(), 4f, 0, false)
                }
            }
        }

        var libro: LibroDibujable? = null
        val pl = disp.libro
        val fichaLibro = disp.obras.firstOrNull()?.obra?.ficha
        if (pl != null && fichaLibro != null) {
            val p = medidor.pagina(fichaLibro, pl.corrido, pl.tamanoExplicacionPx, pl.avisoOk, pl.columna.ancho, modo)
            val regla = tip[Rol.LIBRO_REGLA]
            libro = LibroDibujable(p, pl.columna, regla.color.toInt(), regla.tamanoPx)
        }

        val ovalos = disp.obras.map { o ->
            if (o.forma == Forma.RECTANGULAR) null
            else Path().apply { addOval(RectF(o.interior.x, o.interior.y, o.interior.derecha, o.interior.abajo), Path.Direction.CW) }
        }
        return EscenaLista(
            clave, disp, tema, modo, colorPared, abiertas, dibujables, libro, esVista, lomo,
            disp.obras.map { Marcos.para(it.marco, ctx) }, Formas.sombrasDe(tema, modo), ovalos,
        )
    }

    /**
     * Ni con la letra más chica del Libro entra el texto: se corre el lomo, la obra queda en una página más
     * angosta y el texto usa una columna más ancha. La letra baja de 26 a lo que haga falta (mínimo
     * [ConstantesGaleria.VISTA_EXPLICACION_MIN_PX]); el texto nunca se corta.
     */
    private fun vistaAncha(obra: Obra, modo: Modo, nombreSala: String): Disposicion {
        val lomo = ConstantesGaleria.VISTA_LOMO_X
        val libro = Constantes.LIBRO
        val area = Rect(libro.x + 44f, Constantes.LIBRO_OBRA.y, lomo - libro.x - 44f - 64f, Constantes.LIBRO_OBRA.alto)
        val columna = Rect(lomo + 64f, Constantes.LIBRO_COLUMNA.y, libro.derecha - 56f - (lomo + 64f), Constantes.LIBRO_COLUMNA.alto)
        val r = if (obra.alto > 0 && obra.ancho > 0 && obra.ficha?.forma != Forma.REDONDA) obra.ancho.toFloat() / obra.alto else 1f
        val iw = minOf(area.ancho, area.alto * r).let { Math.round(it).toFloat() }
        val ih = Math.round(iw / r).toFloat()
        val interior = Rect(area.x + (area.ancho - iw) / 2, area.y + (area.alto - ih) / 2, iw, ih)
        val colocada = ObraColocada(
            obra, interior, interior, CatalogoMarcos.resolver("sin"), 0f, 0f, 0f, obra.ficha?.forma ?: Forma.RECTANGULAR,
        )
        val ficha = obra.ficha
        var tam = ConstantesGaleria.VISTA_EXPLICACION_MIN_PX.toFloat()
        if (ficha != null) {
            for (s in Constantes.LIBRO_EXPLICACION_MAX downTo ConstantesGaleria.VISTA_EXPLICACION_MIN_PX) {
                val m = medidor.pagina(ficha, nombreSala, s.toFloat(), false, columna.ancho, modo)
                if (m.entra && m.alto <= columna.alto) { tam = s.toFloat(); break }
            }
        }
        return Disposicion(listOf(colocada), emptyList(), PaginaLibro(area, columna, tam, false, nombreSala), false, NivelInfo.COMPLETA)
    }
}
