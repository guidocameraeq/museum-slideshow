package local.museumslideshow.sync

import local.museumslideshow.ajustes.SincronizarCada

/** Cuándo toca sincronizar al arrancar el protector (no en cada arranque: según "Sincronizar cada"). */
object ReglaSincronizacion {
    /**
     * "Solo a mano" nunca; sin ninguna sincronización buena todavía, sí; si no, cuando pasó el plazo desde la última buena.
     * Si la última buena figura en el futuro (se movió el reloj) no se puede confiar en ella: toca.
     */
    fun tocaAlArrancar(cada: SincronizarCada, ultimaBuenaMs: Long?, ahoraMs: Long): Boolean {
        val horas = cada.horas ?: return false
        if (ultimaBuenaMs == null) return true
        val pasado = ahoraMs - ultimaBuenaMs
        return pasado < 0 || pasado >= horas * 3_600_000L
    }
}
