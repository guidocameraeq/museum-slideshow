package local.museumslideshow.pantallas

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import local.museumslideshow.R
import local.museumslideshow.ajustes.IdAjuste
import local.museumslideshow.ajustes.ItemTexto
import local.museumslideshow.ajustes.Preferencias

/**
 * La ventanita para escribir un ajuste de texto (carpeta o clave de Drive). Es una ventana de Android con un campo
 * común: así aparece el teclado de la tele y también se puede escribir (o pegar) desde la app Google TV del teléfono.
 * "Guardar" aplica lo escrito si sirve (si no, avisa y deja la ventana abierta); "Borrar" lo deja sin cargar.
 * `aplicar` recibe el cambio ya armado (el mismo `cambiar` de la pantalla de ajustes).
 */
fun abrirVentanaTexto(
    ctx: Context,
    item: ItemTexto,
    prefs: Preferencias,
    aplicar: ((Preferencias) -> Preferencias) -> Unit,
    alGuardar: () -> Unit,
) {
    val res = ctx.resources
    val margen = (24 * res.displayMetrics.density).toInt()
    val campo = EditText(ctx).apply {
        setSingleLine(true)
        inputType = if (item.secreto) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        setText(item.inicial(prefs))
        setSelection(text.length)
    }
    val ayuda = TextView(ctx).apply {
        text = res.getString(R.string.texto_ayuda_telefono)
        setPadding(0, margen / 2, 0, 0)
    }
    val caja = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(margen, margen / 2, margen, 0)
        addView(campo)
        addView(ayuda)
    }
    val ventana = AlertDialog.Builder(ctx)
        .setTitle(item.nombre)
        .setMessage(item.ayuda?.let { res.getString(it) })
        .setView(caja)
        .setPositiveButton(R.string.texto_guardar, null)
        .setNeutralButton(R.string.texto_borrar, null)
        .setNegativeButton(R.string.texto_cancelar, null)
        .create()

    fun guardar(texto: String) {
        // Se valida contra lo de ahora; el cambio se aplica sobre lo guardado (por si otro ajuste cambió en el medio).
        if (item.poner(prefs, texto) == null) {
            campo.error = res.getString(if (item.id == IdAjuste.CLAVE) R.string.clave_no_sirve else R.string.carpeta_no_sirve)
            return
        }
        aplicar { p -> item.poner(p, texto) ?: p }
        ventana.dismiss()
        alGuardar()
    }

    ventana.setOnShowListener {
        ventana.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { guardar(campo.text.toString()) }
        ventana.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { guardar("") }
        campo.requestFocus()
    }
    ventana.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    ventana.show()
}
