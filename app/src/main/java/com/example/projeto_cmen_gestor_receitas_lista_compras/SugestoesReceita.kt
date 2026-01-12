package com.example.projeto_cmen_gestor_receitas_lista_compras

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.projeto_cmen_gestor_receitas_lista_compras.data.GeminiReceitaAnalyzer
import com.example.projeto_cmen_gestor_receitas_lista_compras.data.SugestaoIA
import com.example.projeto_cmen_gestor_receitas_lista_compras.databinding.ActivitySugestoesReceitaBinding
import com.example.projeto_cmen_gestor_receitas_lista_compras.model.Ingrediente
import com.example.projeto_cmen_gestor_receitas_lista_compras.model.ListaComprasItem
import com.example.projeto_cmen_gestor_receitas_lista_compras.model.Receita
import com.example.projeto_cmen_gestor_receitas_lista_compras.model.ReceitaIngrediente
import com.example.projeto_cmen_gestor_receitas_lista_compras.ui.ReceitaAdapter
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch

class SugestoesReceita : AppCompatActivity() {

    private lateinit var binding: ActivitySugestoesReceitaBinding
    private lateinit var adapter: ReceitaAdapter
    private val analyzer = GeminiReceitaAnalyzer()
    private var sugestaoAtual: SugestaoIA? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySugestoesReceitaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        iniciarProcesso()
    }

    private fun setupRecyclerView() {
        adapter = ReceitaAdapter(emptyList(), podeRemover = false) { _, _ -> }
        binding.rvSugestoesBD.layoutManager = LinearLayoutManager(this)
        binding.rvSugestoesBD.adapter = adapter
    }

    private fun iniciarProcesso() {
        lifecycleScope.launch {
            binding.pbLoading.visibility = View.VISIBLE
            binding.scrollSugestaoIA.visibility = View.GONE
            binding.rvSugestoesBD.visibility = View.GONE
            binding.tvStatus.text = getString(R.string.tv_analisar_ingredientes)

            try {
                val compras = SupabaseManager.client.from("lista_compras_itens")
                    .select(Columns.list("*, ingredientes(*)"))
                    .decodeList<ListaComprasItem>()

                if (compras.isEmpty()) {
                    binding.tvStatus.text = getString(R.string.lbl_lista_vazia)
                    binding.pbLoading.visibility = View.GONE
                    return@launch
                }

                val idsNoStock = compras.mapNotNull { it.ingredienteId }.toSet()
                Log.d("DEBUG_MATCH", "=== INÍCIO DA ANÁLISE ===")
                Log.d("DEBUG_MATCH", "Stock disponível (IDs): $idsNoStock")

                val todasReceitas = SupabaseManager.client.from("receitas")
                    .select(Columns.list("*, receita_ingredientes(*)"))
                    .decodeList<Receita>()

                val receitasViaveis = todasReceitas.filter { receita ->
                    val reqs = receita.receita_ingredientes ?: emptyList()
                    if (reqs.isEmpty()) {
                        Log.w("DEBUG_MATCH", "Receita '${receita.nome}' sem ingredientes no BD.")
                        return@filter false
                    }

                    Log.d("DEBUG_MATCH", "A analisar: ${receita.nome}")

                    val temTudo = reqs.all { req ->
                        val existe = idsNoStock.contains(req.ingredienteId)
                        val ehTempero = req.medida.lowercase().contains("qb") ||
                                req.medida.lowercase().contains("colher")

                        val resultado = existe || ehTempero

                        if (!resultado) {
                            Log.d("DEBUG_MATCH", "  -> FALHA: Falta o ingrediente ID [${req.ingredienteId}]")
                        } else if (ehTempero && !existe) {
                            Log.d("DEBUG_MATCH", "  -> AVISO: Falta tempero [${req.ingredienteId}], mas a lógica permitiu (qb/colher).")
                        }

                        resultado
                    }

                    if (temTudo) Log.i("DEBUG_MATCH", "  -> SUCESSO: Receita viável.")
                    temTudo
                }

                if (receitasViaveis.isNotEmpty()) {
                    Log.d("DEBUG_MATCH", "Total de matches encontrados: ${receitasViaveis.size}")
                    binding.tvStatus.text = getString(R.string.msg_matches_encontrados)
                    binding.rvSugestoesBD.visibility = View.VISIBLE
                    adapter.atualizarLista(receitasViaveis)
                } else {
                    Log.d("DEBUG_MATCH", "Nenhum match na BD. A chamar IA...")
                    binding.tvStatus.text = getString(R.string.msg_gerando_ia)
                    obterSugestaoIA(compras)
                }

            } catch (e: Exception) {
                Log.e("DEBUG_MATCH", "Erro crítico no processo: ${e.message}")
                binding.tvStatus.text = getString(R.string.btn_tentar_novamente)
            } finally {
                binding.pbLoading.visibility = View.GONE
                Log.d("DEBUG_MATCH", "=== FIM DA ANÁLISE ===")
            }
        }
    }

    private suspend fun obterSugestaoIA(itens: List<ListaComprasItem>) {
        analyzer.gerarSugestaoCriativa(itens).onSuccess { sugestao ->
            sugestaoAtual = sugestao
            binding.scrollSugestaoIA.visibility = View.VISIBLE

            val textoIngredientes = sugestao.ingredientes.joinToString("\n") {
                "• ${it.quantidade} ${it.medida} de ${it.nome}"
            }

            binding.tvCorpoSugestaoIA.text = getString(
                R.string.template_sugestao_ia,
                sugestao.nome,
                sugestao.categoria.uppercase(),
                sugestao.tempo,
                textoIngredientes,
                sugestao.preparacao
            )

            binding.btnGerarAlternativa.visibility = View.VISIBLE
            binding.btnGuardarSugestao.visibility = View.VISIBLE

            binding.btnGerarAlternativa.setOnClickListener { iniciarProcesso() }
            binding.btnGuardarSugestao.setOnClickListener { guardarReceitaIA(sugestao) }
        }.onFailure {
            binding.tvStatus.text = getString(R.string.msg_ia_erro)
        }
    }

    private fun guardarReceitaIA(s: SugestaoIA) {
        lifecycleScope.launch {
            binding.pbLoading.visibility = View.VISIBLE
            try {
                val novaReceita = Receita(
                    nome = s.nome,
                    tempo = s.tempo,
                    porcoes = s.porcoes,
                    dificuldade = s.dificuldade,
                    categoria = s.categoria,
                    preparacao = s.preparacao,
                    imagemCaminho = "imagem_padrao.png"
                )

                val guardada = SupabaseManager.client.from("receitas")
                    .insert(novaReceita) { select() }
                    .decodeSingle<Receita>()

                s.ingredientes.forEach { ing ->
                    val idIngrediente = SupabaseManager.client.from("ingredientes")
                        .select { filter { eq("nome", ing.nome.lowercase()) } }
                        .decodeList<Ingrediente>().firstOrNull()?.id
                        ?: SupabaseManager.client.from("ingredientes")
                            .insert(Ingrediente(nome = ing.nome.lowercase())) { select() }
                            .decodeSingle<Ingrediente>().id!!

                    SupabaseManager.client.from("receita_ingredientes").insert(
                        ReceitaIngrediente(
                            receitaId = guardada.id!!,
                            ingredienteId = idIngrediente,
                            quantidade = ing.quantidade,
                            medida = ing.medida
                        )
                    )
                }
                Toast.makeText(this@SugestoesReceita, getString(R.string.msg_guardar_sucesso), Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Log.e("SugestoesReceita", "Erro ao guardar: ${e.message}")
                Toast.makeText(this@SugestoesReceita, getString(R.string.msg_guardar_erro), Toast.LENGTH_SHORT).show()
            } finally {
                binding.pbLoading.visibility = View.GONE
            }
        }
    }
}