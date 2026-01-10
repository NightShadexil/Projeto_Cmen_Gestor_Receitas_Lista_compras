package com.example.projeto_cmen_gestor_receitas_lista_compras

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
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.projeto_cmen_gestor_receitas_lista_compras.databinding.ActivityVisualizarReceitaBinding
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import androidx.core.graphics.toColorInt
import com.example.projeto_cmen_gestor_receitas_lista_compras.model.ListaComprasItem
import com.example.projeto_cmen_gestor_receitas_lista_compras.model.Receita
import com.example.projeto_cmen_gestor_receitas_lista_compras.model.ReceitaIngrediente

class VisualizarReceita : AppCompatActivity() {
    private lateinit var binding: ActivityVisualizarReceitaBinding
    private var receitaId: String = ""
    private val TAG = "DEBUG_IMAGEM"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVisualizarReceitaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        receitaId = intent.getStringExtra("RECEITA_ID") ?: ""
        Log.d(TAG, "Activity iniciada para a receita ID: $receitaId")

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
        if (receitaId.isNotEmpty()) {
            carregarDadosReceita()
        }
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

                Log.d(TAG, "Nome do ficheiro na BD: ${receita.imagemCaminho}")
                Log.d(TAG, "URL Completa gerada: ${receita.urlCompleta}")

                binding.ivReceitaHeader.load(receita.urlCompleta) {
                    crossfade(true)
                    placeholder(android.R.drawable.ic_menu_gallery)
                    listener(
                        onStart = { Log.d(TAG, "Iniciando carregamento da imagem...") },
                        onSuccess = { _, _ -> Log.d(TAG, "Imagem carregada com sucesso!") },
                        onError = { _, result ->
                            Log.e(TAG, "Erro no Coil: ${result.throwable.message}")
                        }
                    )
                }

                binding.tvTituloDetalhe.text = receita.nome
                binding.tvTempoDetalhe.text = "🕒 ${receita.tempo} min"
                binding.tvCategoriaDetalhe.text = receita.categoria.uppercase()
                binding.tvPreparacaoDetalhe.text = receita.preparacao

                configurarTextoDificuldade(receita.dificuldade)

                aplicarCorBadge(receita.categoria)

                val textoIngredientes = listaItens.joinToString("\n") { item ->
                    "• ${item.quantidade} ${item.medida} de ${item.ingredientes?.nome ?: "Desconhecido"}"
                }
                binding.tvIngredientesDetalhe.text = textoIngredientes.ifEmpty { "Sem ingredientes registados." }

            } catch (e: Exception) {
                Log.e(TAG, "Falha geral ao carregar receita: ${e.message}")
                e.printStackTrace()
                Toast.makeText(this@VisualizarReceita, "Erro ao carregar detalhes", Toast.LENGTH_SHORT).show()
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

                Toast.makeText(this@VisualizarReceita, "Ingredientes adicionados à lista de compras!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao gerar lista: ${e.message}")
                e.printStackTrace()
                Toast.makeText(this@VisualizarReceita, "Erro ao adicionar itens", Toast.LENGTH_SHORT).show()
            }
        }
    }
}