package local.museumslideshow.actualizacion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import local.museumslideshow.Registro

/**
 * Lo que Android contesta al instalar una versión nueva: primero pide confirmar (se abre su cartel "¿Instalar?"),
 * después avisa si salió bien o no. Si sale bien, Android cierra la app y la abre ya actualizada.
 */
class ReceptorInstalacion : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val estado = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (estado) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmar: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirmar == null) {
                    Log.w(Registro.TAG, "Actualizaciones: Android pidió confirmar pero no mandó el cartel")
                    Actualizador.de(context).alTerminarInstalacion(cancelada = false)
                    return
                }
                try {
                    context.startActivity(confirmar.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    Log.w(Registro.TAG, "Actualizaciones: no se pudo mostrar el cartel de instalar (${e.javaClass.simpleName})")
                    Actualizador.de(context).alTerminarInstalacion(cancelada = false)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Log.i(Registro.TAG, "Actualizaciones: instalada")
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                Log.i(Registro.TAG, "Actualizaciones: se canceló el cartel de instalar")
                Actualizador.de(context).alTerminarInstalacion(cancelada = true)
            }
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                Log.w(Registro.TAG, Registro.limpiar("Actualizaciones: falló la instalación ($estado $msg)"))
                Actualizador.de(context).alTerminarInstalacion(cancelada = false)
            }
        }
    }
}
