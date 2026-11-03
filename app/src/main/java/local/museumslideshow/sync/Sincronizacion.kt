package local.museumslideshow.sync

import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.coroutines.flow.first
import local.museumslideshow.BuildConfig
import local.museumslideshow.Registro
import local.museumslideshow.ajustes.AlmacenAjustes
import local.museumslideshow.ajustes.CarpetaDrive
import local.museumslideshow.ajustes.Preferencias

/** Punto único para sincronizar de verdad (lo usan la pantalla, la Barra y el Worker). Lee los ajustes del almacén. */
object Sincronizacion {
    fun dirBase(context: Context) = File(context.filesDir, "galerias")

    /** La carpeta que se sincroniza: la de los ajustes si tiene valor, si no la de `local.properties` (al compilar). */
    fun carpetaEfectiva(p: Preferencias, deFabrica: String): String = p.carpetaDrive?.takeIf { it.isNotBlank() } ?: deFabrica

    /**
     * La API de Drive con la que se pide TODO (sincronización y reserva): respeta la pausa que pidió Google, baja de a una con
     * el "Ritmo de bajada" y, si Google pide una pausa, la anota en el registro y agenda el reintento. `prefs` se lee en cada uso.
     */
    fun crearApi(context: Context, prefs: () -> Preferencias): DriveApi {
        val ctx = context.applicationContext
        return DriveApiConPausa(
            api = DriveApiHttp(BuildConfig.DRIVE_API_KEY),
            pausa = PausaDrive.de(ctx),
            ritmoMs = { prefs().ritmoBajada.pausaMs },
            registrar = { Log.i(Registro.TAG, Registro.limpiar(it)) },
            alPausar = { hasta -> SincronizarWorker.reintentarTrasPausa(ctx, prefs().sincronizarCada, hasta) },
        )
    }

    suspend fun ejecutar(context: Context): ResultadoSync {
        val ctx = context.applicationContext
        val p = try {
            AlmacenAjustes.de(ctx).flow.first()
        } catch (e: Exception) {
            Log.w(Registro.TAG, "No se pudieron leer los ajustes (${e.javaClass.simpleName}); se usan los de fábrica")
            Preferencias()
        }
        return ejecutar(ctx, carpetaEfectiva(p, CarpetaDrive.extraerId(BuildConfig.DRIVE_CARPETA) ?: BuildConfig.DRIVE_CARPETA), p)
    }

    fun ejecutar(context: Context, carpeta: String, p: Preferencias = Preferencias()): ResultadoSync {
        val ctx = context.applicationContext
        val s = Sincronizador(
            api = crearApi(ctx) { p },
            dirBase = dirBase(ctx),
            procesador = ProcesadorImagenAndroid(),
            ahora = { System.currentTimeMillis() },
        )
        val r = try {
            s.sincronizar(carpeta)
        } catch (e: Exception) {
            Log.e(Registro.TAG, "Sincronización abortada (${e.javaClass.simpleName})")
            ResultadoSync(false, 0, 0, 0, emptyList())
        }
        val pausa = r.pausaHastaMs
        if (pausa != null) {
            Log.i(Registro.TAG, Registro.limpiar("Sincronización en pausa hasta ${PausaDrive.horaTexto(pausa)}: no se pidió nada más a Drive"))
            try { SincronizarWorker.reintentarTrasPausa(ctx, p.sincronizarCada, pausa) } catch (e: Exception) {
                Log.w(Registro.TAG, "No se pudo agendar el reintento (${e.javaClass.simpleName})")
            }
            return r
        }
        if (r.ok) PausaDrive.de(ctx).reiniciar()
        Log.i(Registro.TAG, Registro.limpiar("Sincronización: ok=${r.ok} bajadas=${r.bajadas} borradas=${r.borradas} salteadas=${r.salteadas} problemas=${r.problemas.size}"))
        for (pr in r.problemas) {
            Log.i(Registro.TAG, Registro.limpiar("  problema ${pr.tipo} galería='${pr.galeria}' archivo='${pr.archivo ?: ""}' detalle='${pr.detalle ?: ""}'"))
        }
        return r
    }
}
