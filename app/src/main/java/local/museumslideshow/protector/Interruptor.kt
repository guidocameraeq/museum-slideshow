package local.museumslideshow.protector

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import local.museumslideshow.pantallas.nombreProtectorLegible

/** La clave del ajuste del sistema que elige el protector. Es lo ÚNICO del sistema que esta app escribe, y solo desde [Interruptor.escribirAjuste]. */
private const val CLAVE_PROTECTOR = "screensaver_components"
private const val PERMISO_ESCRITURA = "android.permission.WRITE_SECURE_SETTINGS"

/** "El anterior" vive aparte de los ajustes visibles (esos se borran enteros al guardar). */
private val Context.dataStoreProtector by preferencesDataStore(name = "protector")
private val CLAVE_ANTERIOR = stringPreferencesKey("anterior")

/** Qué pasó al tocar Usar / Volver. */
sealed class ResultadoInterruptor {
    /** Se escribió: [antes] y [despues] son el valor del sistema antes y después. */
    class Hecho(val antes: String?, val despues: String?) : ResultadoInterruptor()
    /** Falta el permiso `WRITE_SECURE_SETTINGS` (se da una vez por adb). */
    object SinPermiso : ResultadoInterruptor()
    object Fallo : ResultadoInterruptor()
}

/** Lo que la pantalla Protector necesita saber. */
class EstadoProtector(
    val permisoDado: Boolean,
    /** El valor de `screensaver_components` hoy (null si está vacío). */
    val activoHoy: String?,
    val esElNuestro: Boolean,
    /** Nombre legible del protector guardado como "el anterior"; null si no hay nada guardado. */
    val anteriorNombre: String?,
)

/**
 * El interruptor "Usar como protector / Volver al anterior". Las decisiones salen de [PlanInterruptor]; acá solo se lee,
 * se guarda "el anterior" y se escribe el ajuste. No ejecuta comandos de sistema.
 */
class Interruptor(context: Context) {
    private val ctx = context.applicationContext

    /** Nuestro componente completo: `local.museumslideshow/local.museumslideshow.protector.ProtectorMuseo`. */
    private val nuestro = ComponentName(ctx, ProtectorMuseo::class.java).flattenToString()

    private val paquete = ctx.packageName

    private fun leerActual(): String? = try {
        Settings.Secure.getString(ctx.contentResolver, CLAVE_PROTECTOR)
    } catch (e: Exception) {
        null
    }

    private fun tienePermiso(): Boolean = try {
        ctx.checkSelfPermission(PERMISO_ESCRITURA) == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) {
        false
    }

    /** LA función que escribe un ajuste del sistema: solo `screensaver_components`. null = escribió; si no, por qué no. */
    private fun escribirAjuste(valor: String): ResultadoInterruptor? = try {
        if (Settings.Secure.putString(ctx.contentResolver, CLAVE_PROTECTOR, valor)) null else ResultadoInterruptor.Fallo
    } catch (e: SecurityException) {
        ResultadoInterruptor.SinPermiso
    } catch (e: Exception) {
        ResultadoInterruptor.Fallo
    }

    private suspend fun leerGuardado(): String? = ctx.dataStoreProtector.data.map { it[CLAVE_ANTERIOR] }.first()

    private suspend fun guardar(valor: String?) {
        ctx.dataStoreProtector.edit { if (valor == null) it.remove(CLAVE_ANTERIOR) else it[CLAVE_ANTERIOR] = valor }
    }

    suspend fun estado(): EstadoProtector = withContext(Dispatchers.IO) {
        val activo = leerActual()?.takeIf { it.isNotBlank() }
        val guardado = leerGuardado()
        EstadoProtector(
            permisoDado = tienePermiso(),
            activoHoy = activo,
            esElNuestro = PlanInterruptor.esNuestro(activo, paquete),
            anteriorNombre = guardado?.let { nombreProtectorLegible(ctx, it.takeIf { v -> v.isNotBlank() }) },
        )
    }

    /** Usar como protector. Si lo de hoy no es lo nuestro, se guarda como "el anterior" (antes de escribir). */
    suspend fun usar(): ResultadoInterruptor = withContext(Dispatchers.IO) {
        val antes = leerActual()
        val plan = PlanInterruptor.usar(antes, nuestro, paquete)
        val habiaGuardado = leerGuardado()
        if (plan.guardarAnterior) guardar(plan.anterior)
        val error = escribirAjuste(plan.escribir)
        if (error != null) {
            // No se escribió: lo guardado vuelve a como estaba.
            if (plan.guardarAnterior) guardar(habiaGuardado)
            return@withContext error
        }
        ResultadoInterruptor.Hecho(antes, leerActual())
    }

    /** Volver al anterior: escribe exactamente lo guardado (o el Ambient de Google si no hay nada) y después borra lo guardado. */
    suspend fun volverAlAnterior(): ResultadoInterruptor = withContext(Dispatchers.IO) {
        val antes = leerActual()
        val valor = PlanInterruptor.volver(leerGuardado(), paquete)
        val error = escribirAjuste(valor)
        if (error != null) return@withContext error
        guardar(null)
        ResultadoInterruptor.Hecho(antes, leerActual())
    }

    /** Arranca el protector ya (el sistema abre el que esté elegido). false si no se pudo. */
    fun probarAhora(): Boolean = try {
        ctx.startActivity(
            Intent().setComponent(ComponentName("com.android.systemui", "com.android.systemui.Somnambulator"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (e: Exception) {
        false
    }
}
