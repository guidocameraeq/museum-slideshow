package local.museumslideshow.ajustes

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStoreAjustes by preferencesDataStore(name = "ajustes")

/**
 * Los ajustes guardados (DataStore Preferences). Un solo almacén por proceso: la galería, la Barra, Ajustes y el Worker
 * leen y escriben por acá. Lo que falta o no se entiende vuelve al valor por defecto ([CodecPreferencias]).
 */
class AlmacenAjustes private constructor(private val contexto: Context) {
    /** Emite los ajustes al abrir y cada vez que cambian. */
    val flow: Flow<Preferencias> = contexto.dataStoreAjustes.data.map { prefs ->
        CodecPreferencias.decodificar(prefs.asMap().entries.associate { (k, v) -> k.name to v.toString() })
    }

    /** Aplica el cambio sobre lo guardado y lo guarda (atómico: dos cambios seguidos no se pisan). */
    suspend fun cambiar(transform: (Preferencias) -> Preferencias) {
        contexto.dataStoreAjustes.edit { prefs ->
            val actual = CodecPreferencias.decodificar(prefs.asMap().entries.associate { (k, v) -> k.name to v.toString() })
            val nuevo = transform(actual)
            prefs.clear()
            for ((k, v) in CodecPreferencias.codificar(nuevo)) prefs[stringPreferencesKey(k)] = v
        }
    }

    companion object {
        @Volatile private var instancia: AlmacenAjustes? = null

        fun de(context: Context): AlmacenAjustes =
            instancia ?: synchronized(this) {
                instancia ?: AlmacenAjustes(context.applicationContext).also { instancia = it }
            }
    }
}
