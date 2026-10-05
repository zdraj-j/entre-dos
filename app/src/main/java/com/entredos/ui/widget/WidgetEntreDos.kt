package com.entredos.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.entredos.MainActivity
import com.entredos.core.FormatoPesos
import com.entredos.data.repository.DatosWidget
import com.entredos.data.repository.WidgetRepositorio
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun repositorio(): WidgetRepositorio
}

/** Colores fijos del boceto, con versión clara y oscura. */
private val Fondo = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF1F1C26))
private val Tinta = ColorProvider(day = Color(0xFF1C1A24), night = Color(0xFFECE9F3))
private val Suave = ColorProvider(day = Color(0xFF5E5A6B), night = Color(0xFFB4AFC2))
private val Acento = ColorProvider(day = Color(0xFF5B3FB0), night = Color(0xFFB9A3FF))
private val SobreAcento = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF1C1433))
private val Alerta = ColorProvider(day = Color(0xFFB5541C), night = Color(0xFFFFB08A))

/**
 * Widget de inicio: lo disponible del periodo, lo gastado hoy, los pendientes por clasificar
 * y un botón "Nuevo gasto" que abre el teclado. Tocar cualquier parte abre la app.
 */
class WidgetEntreDos : GlanceAppWidget() {

    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).repositorio()
        val datos = runCatching { repo.actual() }.getOrNull()
        provideContent { GlanceTheme { Contenido(datos) } }
    }

    @Composable
    private fun Contenido(d: DatosWidget?) {
        val abrir = actionStartActivity<MainActivity>()
        Column(
            GlanceModifier.fillMaxSize().background(Fondo).cornerRadius(20.dp).padding(14.dp).clickable(abrir),
            verticalAlignment = Alignment.Vertical.Top,
        ) {
            Text("Entre dos", style = TextStyle(color = Suave, fontSize = 12.sp, fontWeight = FontWeight.Medium))
            if (d == null || !d.hayHogar) {
                Text("Abre la app para empezar", style = TextStyle(color = Tinta, fontSize = 14.sp))
                return@Column
            }
            Text(
                if (d.disponible >= 0) "Quedan" else "Se pasaron",
                style = TextStyle(color = Suave, fontSize = 12.sp),
            )
            Text(
                FormatoPesos.pesos(kotlin.math.abs(d.disponible)),
                style = TextStyle(color = if (d.disponible >= 0) Tinta else Alerta, fontSize = 24.sp, fontWeight = FontWeight.Bold),
            )
            Text(
                "Hoy: ${FormatoPesos.pesos(d.gastadoHoy)}" + if (d.pendientes > 0) " · ${d.pendientes} por confirmar" else "",
                style = TextStyle(color = if (d.pendientes > 0) Alerta else Suave, fontSize = 12.sp),
            )
            Spacer(GlanceModifier.height(10.dp))
            Box(
                GlanceModifier.fillMaxWidth().background(Acento).cornerRadius(14.dp).padding(vertical = 10.dp).clickable(abrir),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("+  Nuevo gasto", style = TextStyle(color = SobreAcento, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

class ReceptorWidget : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WidgetEntreDos()
}

/** Redibuja el widget cuando cambian el presupuesto, los gastos de hoy o los pendientes. */
@Singleton
class ActualizadorWidget @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val repo: WidgetRepositorio,
) {
    @OptIn(FlowPreview::class)
    fun iniciar() {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            repo.observar().debounce(1_000).collect { runCatching { WidgetEntreDos().updateAll(context) } }
        }
    }
}
