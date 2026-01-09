package com.example.projeto_cmen_gestor_receitas_lista_compras

import Receita
import ReceitaIngrediente
import ListaComprasItem
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.projeto_cmen_gestor_receitas_lista_compras.databinding.ActivityVisualizarReceitaBinding
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import androidx.core.graphics.toColorInt

class VisualizarReceita : AppCompatActivity() {
    private lateinit var binding: ActivityVisualizarReceitaBinding
    private var receitaId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVisualizarReceitaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        receitaId = intent.getStringExtra("RECEITA_ID") ?: ""

        binding.btnVoltar.setOnClickListener { finish() }

        binding.btnEditar.setOnClickListener {
            val intent = Intent(this, EditarReceita::class.java)
            intent.putExtra("RECEITA_ID", receitaId)
            startActivity(intent)
        }

        binding.btnGerarLista.setOnClickListener {
            gerarListaCompras()
        }
    }

    override fun onResume() {
        super.onResume()
        if (receitaId.isNotEmpty()) carregarDadosReceita()
    }

    @SuppressLint("SetTextI18n")
    private fun carregarDadosReceita() {
        lifecycleScope.launch {
            try {
                val receita = SupabaseManager.client.from("receitas")
                    .select { filter { eq("id", receitaId) } }
                    .decodeSingle<Receita>()

                val listaItens = SupabaseManager.client.from("receita_ingredientes")
                    .select(Columns.raw("*, ingredientes(nome)")) {
                        filter { eq("receita_id", receitaId) }
                    }.decodeList<ReceitaIngrediente>()

                binding.tvTituloDetalhe.text = receita.nome
                binding.tvTempoDetalhe.text = "🕒 ${receita.tempo} min"
                binding.tvCategoriaDetalhe.text = receita.categoria.uppercase()
                binding.tvPreparacaoDetalhe.text = receita.preparacao

                configurarTextoDificuldade(receita.dificuldade)

                aplicarCorBadge(receita.categoria)

                val textoIngredientes = listaItens.joinToString("\n") { item ->
                    "• ${item.quantidade} ${item.medida} de ${item.ingredientes?.nome ?: "Desconhecido"}"
                }
                binding.tvIngredientesDetalhe.text = textoIngredientes.ifEmpty { "Sem ingredientes." }

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@VisualizarReceita, "Erro ao carregar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun configurarTextoDificuldade(dificuldade: String?) {
        val label = "Dificuldade: "
        val valor = dificuldade?.uppercase() ?: "---"
        val textoCompleto = label + valor

        val spannable = SpannableStringBuilder(textoCompleto)

        val cor = when (dificuldade?.lowercase()) {
            "baixa" -> "#2ECC71".toColorInt()
            "média" -> "#F1C40F".toColorInt()
            "alta"  -> "#E74C3C".toColorInt()
            else    -> Color.BLACK
        }

        val inicioValor = label.length
        val fimValor = textoCompleto.length

        spannable.setSpan(ForegroundColorSpan(cor), inicioValor, fimValor, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        spannable.setSpan(StyleSpan(Typeface.BOLD), inicioValor, fimValor, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        binding.tvDificuldadeDetalhe.text = spannable
    }

    private fun aplicarCorBadge(categoria: String?) {
        val cor = when (categoria?.lowercase()) {
            "carne" -> "#E57373".toColorInt()
            "peixe" -> "#64B5F6".toColorInt()
            "vegetariana" -> "#81C784".toColorInt()
            "sobremesas" -> "#F06292".toColorInt()
            else -> "#FFB74D".toColorInt()
        }
        val shape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f
            setColor(cor)
        }
        binding.tvCategoriaDetalhe.background = shape
        binding.tvCategoriaDetalhe.setTextColor(Color.WHITE)
    }

    private fun gerarListaCompras() {
        lifecycleScope.launch {
            try {
                val itensReceita = SupabaseManager.client.from("receita_ingredientes")
                    .select { filter { eq("receita_id", receitaId) } }
                    .decodeList<ReceitaIngrediente>()

                itensReceita.forEach { item ->
                    val novoItem = ListaComprasItem(
                        ingredienteId = item.ingredienteId,
                        quantidade = item.quantidade,
                        medida = item.medida
                    )
                    SupabaseManager.client.from("lista_compras_itens").insert(novoItem)
                }
                Toast.makeText(this@VisualizarReceita, "Adicionado à lista!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
}