package local.museumslideshow.ajustes

/** La clave de Drive cargada en la tele (lógica pura): validarla, mostrarla tapada y heredar la de fábrica. */
object ClaveDrive {
    private val FORMA = Regex("^[A-Za-z0-9_-]{20,100}$")

    /** La clave sin espacios ni saltos (lo que se pega a veces los trae). Null si no tiene forma de clave. */
    fun limpiar(texto: String?): String? {
        val t = texto?.replace(Regex("\\s+"), "").orEmpty()
        return t.takeIf { FORMA.matches(it) }
    }

    /** Para mostrar en pantalla: solo el principio, nunca entera (`AIza…•••`). */
    fun tapar(clave: String): String = clave.take(4) + "…•••"

    /**
     * Si el APK se compiló con clave o carpeta de fábrica (`local.properties`) y la tele todavía no tiene una cargada,
     * se copian a los ajustes. Así, cuando se actualice con el APK de GitHub (que no trae ninguna), la tele sigue andando.
     * Devuelve las mismas preferencias si no hay nada que copiar.
     */
    fun conLasDeFabrica(p: Preferencias, claveFabrica: String, carpetaFabrica: String): Preferencias {
        val clave = if (p.claveDrive == null) limpiar(claveFabrica) else null
        val carpeta = if (p.carpetaDrive == null) CarpetaDrive.extraerId(carpetaFabrica) else null
        if (clave == null && carpeta == null) return p
        return p.copy(claveDrive = clave ?: p.claveDrive, carpetaDrive = carpeta ?: p.carpetaDrive)
    }
}
