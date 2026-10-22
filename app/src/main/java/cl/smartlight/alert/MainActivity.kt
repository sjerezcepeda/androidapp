package cl.smartlight.alert

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class MainActivity : AppCompatActivity() {

    private val db = FirebaseDatabase.getInstance()
    private val ref = db.getReference("smartlight")

    private lateinit var tvEstado: TextView
    private lateinit var tvDistancia: TextView
    private lateinit var tvPresencia: TextView
    private lateinit var tvLuz: TextView
    private lateinit var tvIntruso: TextView

    private lateinit var btnLuz: Button
    private lateinit var btnAlarma: Button
    private lateinit var btnConexion: Button

    private var luzManual = false
    private var alarmaManual = false

    private var sesionActiva = false
    private var datosRecibidos = false

    private var listenerDatos: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvEstado = findViewById(R.id.tvEstado)
        tvDistancia = findViewById(R.id.tvDistancia)
        tvPresencia = findViewById(R.id.tvPresencia)
        tvLuz = findViewById(R.id.tvLuz)
        tvIntruso = findViewById(R.id.tvIntruso)

        btnLuz = findViewById(R.id.btnLuz)
        btnAlarma = findViewById(R.id.btnAlarma)
        btnConexion = findViewById(R.id.btnConexion)

        configurarBotones()
        conectarApp()
    }

    private fun configurarBotones() {
        btnLuz.setOnClickListener {
            if (sesionActiva && datosRecibidos) {
                escribirOrden("luzEncendida", !luzManual)
            }
        }

        btnAlarma.setOnClickListener {
            if (sesionActiva && datosRecibidos) {
                escribirOrden("alarmaActiva", !alarmaManual)
            }
        }

        btnConexion.setOnClickListener {
            if (sesionActiva) {
                desconectarApp()
            } else {
                conectarApp()
            }
        }
    }

    private fun escribirOrden(campo: String, valor: Boolean) {
        ref.child(campo).setValue(valor)
            .addOnFailureListener { error ->
                Toast.makeText(
                    this,
                    "No se pudo enviar la orden: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun conectarApp() {
        if (listenerDatos != null) return

        sesionActiva = true
        datosRecibidos = false

        habilitarControles(false)
        btnConexion.text = "Desconectar app"
        tvEstado.text = "Conectando: esperando datos..."

        val nuevoListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!sesionActiva) return

                if (!snapshot.exists()) {
                    datosRecibidos = false
                    habilitarControles(false)
                    tvEstado.text =
                        "No hay datos en smartlight. Inicia el simulador Python."
                    return
                }

                datosRecibidos = true

                val distancia = snapshot.child("distancia")
                    .getValue(Int::class.java)

                val presencia = snapshot.child("presencia")
                    .getValue(Boolean::class.java) ?: false

                val luzAmbiente = snapshot.child("luzAmbiente")
                    .getValue(Int::class.java)

                val intruso = snapshot.child("intrusoDetectado")
                    .getValue(Boolean::class.java) ?: false

                luzManual = snapshot.child("luzEncendida")
                    .getValue(Boolean::class.java) ?: false

                alarmaManual = snapshot.child("alarmaActiva")
                    .getValue(Boolean::class.java) ?: false

                val luzReal = snapshot.child("luzAutomatica")
                    .getValue(Boolean::class.java)

                val alarmaReal = snapshot.child("alarmaSonando")
                    .getValue(Boolean::class.java)

                tvDistancia.text =
                    "Distancia: ${distancia?.toString() ?: "--"} cm"

                tvPresencia.text = if (presencia) {
                    "Presencia: detectada"
                } else {
                    "Presencia: no detectada"
                }

                tvLuz.text =
                    "Luz ambiente: ${luzAmbiente?.toString() ?: "--"}"

                tvIntruso.text = if (intruso) {
                    "Intruso detectado: SÍ"
                } else {
                    "Intruso detectado: NO"
                }

                val textoLuz = when (luzReal) {
                    true -> if (intruso) {
                        "Luz: ENCENDIDA — protección automática"
                    } else {
                        "Luz: ENCENDIDA"
                    }

                    false -> "Luz: APAGADA"
                    null -> "Luz: esperando estado del simulador"
                }

                val textoAlarma = when (alarmaReal) {
                    true -> "Alarma: SONANDO"
                    false -> "Alarma: en silencio"
                    null -> "Alarma: esperando estado del simulador"
                }

                tvEstado.text = "$textoLuz\n$textoAlarma"

                actualizarBotonesManuales()
                habilitarControles(true)
            }

            override fun onCancelled(error: DatabaseError) {
                desconectarApp()
                tvEstado.text = "Error de Firebase: ${error.message}"
            }
        }

        listenerDatos = nuevoListener
        ref.addValueEventListener(nuevoListener)
    }

    private fun desconectarApp() {
        sesionActiva = false
        datosRecibidos = false

        listenerDatos?.let {
            ref.removeEventListener(it)
        }

        listenerDatos = null

        habilitarControles(false)
        btnConexion.text = "Conectar app"

        tvEstado.text = "App desconectada"
        tvDistancia.text = "Distancia: -- cm"
        tvPresencia.text = "Presencia: --"
        tvLuz.text = "Luz ambiente: --"
        tvIntruso.text = "Intruso detectado: --"
    }

    private fun actualizarBotonesManuales() {
        btnLuz.text = if (luzManual) {
            "Desactivar encendido manual de luz"
        } else {
            "Encender luz manualmente"
        }

        btnAlarma.text = if (alarmaManual) {
            "Desactivar alarma manual"
        } else {
            "Activar alarma manual"
        }
    }

    private fun habilitarControles(habilitados: Boolean) {
        btnLuz.isEnabled = habilitados
        btnAlarma.isEnabled = habilitados
    }

    override fun onDestroy() {
        sesionActiva = false

        listenerDatos?.let {
            ref.removeEventListener(it)
        }

        listenerDatos = null
        super.onDestroy()
    }
}