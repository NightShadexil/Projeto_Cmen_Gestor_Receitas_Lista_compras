package com.example.projeto_cmen_gestor_receitas_lista_compras

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.projeto_cmen_gestor_receitas_lista_compras.data.GeminiReceitaAnalyzer
import com.example.projeto_cmen_gestor_receitas_lista_compras.databinding.ActivityVisualizarReceitaBinding
import com.example.projeto_cmen_gestor_receitas_lista_compras.model.ListaComprasItem
import com.example.projeto_cmen_gestor_receitas_lista_compras.model.Receita
import com.example.projeto_cmen_gestor_receitas_lista_compras.model.ReceitaIngrediente
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class VisualizarReceita : AppCompatActivity() {
    private lateinit var binding: ActivityVisualizarReceitaBinding
    private var receitaId: String = ""
    private val tag = "DEBUG_IMAGEM"

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

        binding.btnExportarPdf.setOnClickListener {
            exportarParaPdf()
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

                binding.ivReceitaHeader.load(receita.urlCompleta) {
                    crossfade(true)
                    allowHardware(false)
                    placeholder(android.R.drawable.ic_menu_gallery)
                }

                binding.tvTituloDetalhe.text = receita.nome
                binding.tvTempoDetalhe.text = "🕒 ${receita.tempo} min"
                binding.tvPorcoesDetalhe.text = "🍽️ ${receita.porcoes} porções"
                binding.tvCategoriaDetalhe.text = receita.categoria.uppercase()
                binding.tvPreparacaoDetalhe.text = receita.preparacao

                configurarTextoDificuldade(receita.dificuldade)
                aplicarCorBadge(receita.categoria)

                val textoIngredientes = listaItens.joinToString("\n") { item ->
                    "• ${item.quantidade} ${item.medida} de ${item.ingredientes?.nome ?: "Desconhecido"}"
                }
                binding.tvIngredientesDetalhe.text = textoIngredientes.ifEmpty { "Sem ingredientes registados." }

            } catch (e: Exception) {
                Log.e(tag, "Falha ao carregar receita: ${e.message}")
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

        spannable.setSpan(ForegroundColorSpan(cor), label.length, textoCompleto.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(StyleSpan(Typeface.BOLD), label.length, textoCompleto.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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
        binding.tvCategoriaDetalhe.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f
            setColor(cor)
        }
    }

    private fun exportarParaPdf() {
        try {
            val pdfDocument = PdfDocument()
            val content = binding.scrollViewConteudo
            val view = content.getChildAt(0)

            val pageInfo = PdfDocument.PageInfo.Builder(view.width, view.height, 1).create()
            val page = pdfDocument.startPage(pageInfo)

            view.draw(page.canvas)
            pdfDocument.finishPage(page)

            val nomeFicheiro = "Receita_${binding.tvTituloDetalhe.text.toString().replace(" ", "_")}.pdf"
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), nomeFicheiro)

            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()

            Toast.makeText(this, "PDF salvo em Downloads", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erro ao gerar PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun gerarListaCompras() {
        lifecycleScope.launch {
            try {
                binding.viewLoadingOverlay.visibility = View.VISIBLE
                binding.progressBarProcessamento.visibility = View.VISIBLE

                val itensReceita = SupabaseManager.client.from("receita_ingredientes")
                    .select { filter { eq("receita_id", receitaId) } }
                    .decodeList<ReceitaIngrediente>()

                for (item in itensReceita) {
                    val itemExistente = SupabaseManager.client.from("lista_compras_itens")
                        .select {
                            filter { eq("ingrediente_id", item.ingredienteId) }
                        }.decodeSingleOrNull<ListaComprasItem>()

                    if (itemExistente != null) {
                        if (itemExistente.quantidade < item.quantidade) {
                            val novaQtd = item.quantidade // Define a quantidade para o mínimo necessário da receita
                            SupabaseManager.client.from("lista_compras_itens").update(
                                { set("quantidade", novaQtd) }
                            ) { filter { eq("id", itemExistente.id!!) } }
                        }
                    } else {
                        val novoItem = ListaComprasItem(
                            ingredienteId = item.ingredienteId,
                            quantidade = item.quantidade,
                            medida = item.medida,
                            listaId = null
                        )
                        SupabaseManager.client.from("lista_compras_itens").insert(novoItem)
                    }
                }

                executarNormalizacaoInteligente()

                binding.viewLoadingOverlay.visibility = View.GONE
                binding.progressBarProcessamento.visibility = View.GONE

                startActivity(Intent(this@VisualizarReceita, Compras::class.java))
                Toast.makeText(this@VisualizarReceita, "Ingredientes adicionados e otimizados!", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                binding.viewLoadingOverlay.visibility = View.GONE
                binding.progressBarProcessamento.visibility = View.GONE
                Log.e(tag, "Erro ao gerar lista: ${e.message}")
                Toast.makeText(this@VisualizarReceita, "Erro: Tabela não encontrada ou falha na rede", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun executarNormalizacaoInteligente() {
        val analyzer = GeminiReceitaAnalyzer()

        val listaAtual = SupabaseManager.client.from("lista_compras_itens")
            .select(columns = Columns.list("*, ingredientes(*)"))
            .decodeList<ListaComprasItem>()

        val resultado = analyzer.normalizarQuantidades(listaAtual)

        resultado.onSuccess { itensIA ->
            listaAtual.forEach { itemBD ->
                val nomeProcurado = itemBD.ingredientes?.nome

                val correspondencia = itensIA.find {
                    it.nome_original.equals(nomeProcurado, ignoreCase = true)
                }

                if (correspondencia != null) {
                    SupabaseManager.client.from("lista_compras_itens").update(
                        {
                            set("quantidade", correspondencia.quantidade)
                            set("medida", correspondencia.medida)
                        }
                    ) {
                        filter { eq("id", itemBD.id!!) }
                    }
                }
            }
        }.onFailure {
            Log.e("VisualizarReceita", "IA falhou, mantendo valores originais.")
        }
    }
}