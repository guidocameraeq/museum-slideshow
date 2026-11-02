package local.museumslideshow.temas

import local.museumslideshow.ajustes.Modo

/** La tipografía de cada tema y modo, copiada del CSS de `docs/diseno/03-temas-v3.html`. */
internal object TipografiasDeTemas {
    private const val LH_TITULO = 1.18f
    private const val LH = 1.3f

    /** Colores por rol del cartel (variables --lt, --la, --ld, --lc, --ls del CSS). */
    private class Paleta(val lt: Long, val la: Long, val ld: Long, val lc: Long, val ls: Long)

    /** Tamaños del tema (--ts, --as, --ds, --ms, --cs, --xs, --os, --nts, --nas). */
    private class Tamanos(
        val ts: Float, val a: Float, val ds: Float, val ms: Float, val cs: Float,
        val xs: Float, val os: Float, val nts: Float, val nas: Float,
    )

    private class Variante(
        val tituloFam: Familia, val tituloPeso: Int, val tituloItalica: Boolean,
        val autorPeso: Int, val autorMayusculas: Boolean, val autorEspaciado: Float, val nombreAutorEspaciado: Float,
        val salaColor: Long? = null, val salaFondo: Long? = null,
    )

    private fun e(
        fam: Familia, px: Float, peso: Int, color: Long, lh: Float = LH, italica: Boolean = false,
        mayusculas: Boolean = false, esp: Float = 0f, mt: Float = 0f, fondo: Long? = null, rh: Float = 0f, rv: Float = 0f,
    ) = EstiloTexto(fam, px, peso, italica, px * lh, color, mayusculas, esp, mt, fondo, rh, rv)

    private fun cartel(t: Tamanos, c: Paleta, v: Variante): Tipografia {
        val sala = v.salaColor ?: c.ls
        val estilos = mapOf(
            Rol.TITULO to e(v.tituloFam, t.ts, v.tituloPeso, c.lt, LH_TITULO, v.tituloItalica),
            Rol.AUTOR to e(Familia.SANS, t.a, v.autorPeso, c.la, LH, false, v.autorMayusculas, v.autorEspaciado, 6f),
            Rol.ANIO to e(Familia.SANS, t.ds, 400, c.ld, mt = 3f),
            Rol.MOVIMIENTO to e(Familia.SANS, t.ms, 400, c.lc, italica = true, esp = 0.03f, mt = 2f),
            Rol.DATOS to e(Familia.SANS, t.ds, 400, c.ld, mt = 6f),   // la 2.ª línea (ubicación) lleva 2 px de aire
            Rol.EXPLICACION to e(Familia.SANS, t.xs, 400, c.ld, 1.45f, mt = 16f),
            Rol.CREDITO to e(Familia.SANS, t.cs, 400, c.lc, mt = 12f),
            Rol.AVISO_OK to e(Familia.SANS, t.os, 400, c.ld, mt = 14f),
            Rol.NOMBRE_TITULO to e(v.tituloFam, t.nts, v.tituloPeso, c.lt, LH_TITULO, v.tituloItalica),
            Rol.NOMBRE_AUTOR to e(Familia.SANS, t.nas, v.autorPeso, c.la, LH, false, v.autorMayusculas, v.nombreAutorEspaciado, 4f),
            Rol.CHIP_TITULO to e(Familia.SANS, 24f, 600, 0xFFFFFFFFL, 1.2f),
            Rol.CHIP_AUTOR to e(Familia.SANS, 18f, 400, 0xE6FFFFFFL, 1.3f, mt = 4f),
            Rol.SALA to e(Familia.SANS, 21f, 500, sala, 1.2f, mayusculas = true, esp = 0.18f, fondo = v.salaFondo,
                rh = if (v.salaFondo != null) 12f else 0f, rv = if (v.salaFondo != null) 3f else 0f),
            // El contador no está en el CSS del borrador: es la sala en chico (inventado, ajustable).
            Rol.CONTADOR to e(Familia.SANS, 18f, 500, sala, 1.2f, esp = 0.12f, fondo = v.salaFondo,
                rh = if (v.salaFondo != null) 12f else 0f, rv = if (v.salaFondo != null) 3f else 0f),
        )
        return Tipografia(estilos)
    }

    fun galeriaModerna(): Map<Modo, Tipografia> {
        val t = Tamanos(31f, 24f, 20f, 18f, 16f, 20f, 19f, 27f, 20f)
        val v = Variante(Familia.SANS, 600, false, 400, false, 0f, 0f)
        return mapOf(
            Modo.CLARO to cartel(t, Paleta(0xFF222222L, 0xFF2A2A2AL, 0xFF555555L, 0xFF7A7770L, 0x66000000L), v),
            Modo.OSCURO to cartel(t, Paleta(0xFFECE9E2L, 0xFFE0DDD5L, 0xFFA8A59DL, 0xFF8A877FL, 0x61FFFFFFL), v),
        )
    }

    fun salaOscura(): Map<Modo, Tipografia> {
        val t = Tamanos(40f, 21f, 20f, 18f, 16f, 20f, 19f, 33f, 19f)
        // Título en Garamond cursiva 400; autor en mayúsculas, peso 500, 0.06 em (0.05 em bajo cada obra de una Pared).
        val v = Variante(Familia.SERIF, 400, true, 500, true, 0.06f, 0.05f)
        return mapOf(
            Modo.CLARO to cartel(t, Paleta(0xFF2A2622L, 0xFF4A443CL, 0xFF5F594FL, 0xFF7A746AL, 0x803C2D1EL), v),
            Modo.OSCURO to cartel(t, Paleta(0xFFE9E4DAL, 0xFFD8CDB6L, 0xFFA8A196L, 0xFF8A847AL, 0x61E6DCC8L), v),
        )
    }

    fun inmersivo(): Map<Modo, Tipografia> {
        val t = Tamanos(32f, 24f, 20f, 17f, 16f, 20f, 18f, 26f, 20f)
        // La sala lleva cajita negra translúcida y letra blanca en los dos modos.
        val v = Variante(Familia.SANS, 600, false, 400, false, 0f, 0f, salaColor = 0xFFFFFFFFL, salaFondo = 0x73000000L)
        return mapOf(
            Modo.CLARO to cartel(t, Paleta(0xFF14120FL, 0xFF1D1B18L, 0xFF2E2B26L, 0xFF4A463FL, 0xFFFFFFFFL), v),
            Modo.OSCURO to cartel(t, Paleta(0xFFFFFFFFL, 0xE6FFFFFFL, 0xCCFFFFFFL, 0x9EFFFFFFL, 0xFFFFFFFFL), v),
        )
    }

    private fun libro(tx: Long, tx2: Long, tx2Suave: Long, ls: Long, regla: Long): Tipografia {
        val s = Familia.SERIF
        val estilos = mapOf(
            Rol.LIBRO_CORRIDO to e(Familia.SANS, 17f, 500, tx2, 1.53f, mayusculas = true, esp = 0.16f),   // alto fijo de 26 px
            Rol.LIBRO_TITULO to e(s, 44f, 500, tx, 1.08f, italica = true, mt = 12f),                       // 12 arriba, 6 abajo
            Rol.LIBRO_AUTOR to e(s, 29f, 500, tx),
            Rol.LIBRO_DATO to e(s, 21f, 400, tx2),
            Rol.LIBRO_MOVIMIENTO to e(s, 19f, 400, tx2Suave, italica = true, esp = 0.03f),
            Rol.LIBRO_EXPLICACION to e(s, 26f, 400, tx, 1.42f),     // arranca en 26 px y baja hasta 17
            Rol.LIBRO_CREDITO to e(s, 17f, 400, tx2, italica = true, mt = 12f),
            Rol.LIBRO_AVISO_OK to e(s, 21f, 400, tx2, mt = 18f),
            Rol.LIBRO_REGLA to e(s, 1f, 400, regla, 1f, mt = 14f),
            Rol.SALA to e(Familia.SANS, 21f, 500, ls, 1.2f, mayusculas = true, esp = 0.18f),
        )
        return Tipografia(estilos)
    }

    fun libroDeArte(): Map<Modo, Tipografia> = mapOf(
        Modo.CLARO to libro(0xFF2A241BL, 0xFF6B6049L, 0xE66B6049L, 0x803C2D1EL, 0x736B6049L),
        Modo.OSCURO to libro(0xFFDDD7CAL, 0xFF938C7EL, 0xE6938C7EL, 0x61E6DCC8L, 0x73938C7EL),
    )
}
