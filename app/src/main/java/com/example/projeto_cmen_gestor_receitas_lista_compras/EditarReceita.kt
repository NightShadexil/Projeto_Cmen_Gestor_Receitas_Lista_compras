package com.example.projeto_cmen_gestor_receitas_lista_compras

import Ingrediente
import Receita
import ReceitaIngrediente
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.projeto_cmen_gestor_receitas_lista_compras.databinding.ActivityEditarReceitaBinding
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch

class EditarReceita : AppCompatActivity() {
    private lateinit var binding: ActivityEditarReceitaBinding
    private var receitaId: String = ""
    private val listaIngredientesTemp = mutableListOf<Triple<String, Double, String>>()
    private val selecione = "--- Selecione um ingrediente ---"
    private val novo = "--- + Adicionar Novo ---"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditarReceitaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        receitaId = intent.getStringExtra("RECEITA_ID") ?: ""

        binding.btnVoltar.setOnClickListener { finish() }

        setupSpinnersEstaticos()
        carregarIngredientesDaBD()

        binding.spIngredientesBD.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                val selecionado = binding.spIngredientesBD.selectedItem.toString()
                binding.etNovoIngredienteNome.visibility = if (selecionado == novo) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        if (receitaId.isNotEmpty()) carregarDadosParaEdicao()

        binding.btnAdicionarIngrediente.setOnClickListener { adicionarIngredienteLocal() }
        binding.btnGuardarReceita.setOnClickListener { atualizarReceitaNoSupabase() }

        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutEditarRoot) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun carregarIngredientesDaBD(callback: (() -> Unit)? = null) {
        lifecycleScope.launch {
            try {
                val existentes = SupabaseManager.client.from("ingredientes").select().decodeList<Ingrediente>()
                val nomes = existentes.map { it.nome }.sorted().toMutableList()
                nomes.add(0, selecione)
                nomes.add(novo)

                val adapter = ArrayAdapter(this@EditarReceita, android.R.layout.simple_spinner_dropdown_item, nomes)
                binding.spIngredientesBD.adapter = adapter
                callback?.invoke()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun carregarDadosParaEdicao() {
        lifecycleScope.launch {
            try {
                setLoadingState(true)
                val receita = SupabaseManager.client.from("receitas").select { filter { eq("id", receitaId) } }.decodeSingle<Receita>()

                binding.etNome.setText(receita.nome)
                binding.etTempo.setText(receita.tempo.toString())
                binding.etPorcoes.setText(receita.porcoes.toString())
                binding.etPreparacao.setText(receita.preparacao)

                val catAdapter = binding.spCategoria.adapter as? ArrayAdapter<String>
                binding.spCategoria.setSelection(catAdapter?.getPosition(receita.categoria) ?: 0)

                val difAdapter = binding.spDificuldade.adapter as? ArrayAdapter<String>
                binding.spDificuldade.setSelection(difAdapter?.getPosition(receita.dificuldade) ?: 0)

                val itens = SupabaseManager.client.from("receita_ingredientes").select(Columns.raw("*, ingredientes(nome)")) {
                    filter { eq("receita_id", receitaId) }
                }.decodeList<ReceitaIngrediente>()

                itens.forEach { item ->
                    listaIngredientesTemp.add(Triple(item.ingredientes?.nome ?: "", item.quantidade, item.medida))
                }
                atualizarTextoLista()
                setLoadingState(false)
            } catch (e: Exception) {
                e.printStackTrace()
                setLoadingState(false)
                Toast.makeText(this@EditarReceita, "Erro ao carregar dados", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSpinnersEstaticos() {
        val dificuldades = arrayOf("baixa", "média", "alta")
        val categorias = arrayOf("carne", "peixe", "vegetariana", "sobremesas", "outro")
        val medidas = arrayOf("unidade", "g", "kg", "ml", "L", "colher", "chávena", "qb")

        binding.spDificuldade.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, dificuldades)
        binding.spCategoria.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categorias)
        binding.spMedidaIngrediente.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, medidas)
    }

    private fun adicionarIngredienteLocal() {
        val selecao = binding.spIngredientesBD.selectedItem.toString()
        val nomeLimpo = if (selecao == novo) {
            binding.etNovoIngredienteNome.text.toString().trim().lowercase()
        } else if (selecao != selecione) {
            selecao.lowercase()
        } else ""

        val qtd = binding.etQtdIngrediente.text.toString().toDoubleOrNull() ?: 0.0
        val med = binding.spMedidaIngrediente.selectedItem.toString()

        if (nomeLimpo.isNotEmpty() && qtd > 0.0) {
            listaIngredientesTemp.add(Triple(nomeLimpo, qtd, med))
            atualizarTextoLista()
            binding.etNovoIngredienteNome.text.clear()
            binding.etQtdIngrediente.text.clear()
            binding.spIngredientesBD.setSelection(0)
            carregarIngredientesDaBD()
        }
    }

    private fun atualizarTextoLista() {
        val resumo = listaIngredientesTemp.joinToString("\n") { "• ${it.first}: ${it.second} ${it.third}" }
        binding.tvListaTemp.text = resumo.ifEmpty { getString(R.string.msg_nenhum_ingrediente) }
        binding.tvListaTemp.setOnClickListener { if (listaIngredientesTemp.isNotEmpty()) mostrarMenuOpcoesIngrediente() }
    }

    private fun mostrarMenuOpcoesIngrediente() {
        val nomesItens = listaIngredientesTemp.map { "${it.first} (${it.second} ${it.third})" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Editar Ingrediente").setItems(nomesItens) { _, index ->
            val item = listaIngredientesTemp[index]
            val opcoes = arrayOf("Editar", "Remover")
            AlertDialog.Builder(this).setTitle(item.first.uppercase()).setItems(opcoes) { _, acao ->
                if (acao == 0) {
                    val adapter = binding.spIngredientesBD.adapter as? ArrayAdapter<String>
                    val pos = adapter?.getPosition(item.first) ?: -1
                    if (pos != -1) {
                        binding.spIngredientesBD.setSelection(pos)
                    } else {
                        binding.spIngredientesBD.setSelection(adapter?.getPosition(novo) ?: 0)
                        binding.etNovoIngredienteNome.setText(item.first)
                    }
                    binding.etQtdIngrediente.setText(item.second.toString())
                    val medAdapter = binding.spMedidaIngrediente.adapter as? ArrayAdapter<String>
                    binding.spMedidaIngrediente.setSelection(medAdapter?.getPosition(item.third) ?: 0)
                    listaIngredientesTemp.removeAt(index)
                    atualizarTextoLista()
                } else {
                    listaIngredientesTemp.removeAt(index)
                    atualizarTextoLista()
                }
            }.show()
        }.show()
    }

    private fun atualizarReceitaNoSupabase() {
        val nome = binding.etNome.text.toString()
        val tempo = binding.etTempo.text.toString().toIntOrNull() ?: 0
        val porcoes = binding.etPorcoes.text.toString().toIntOrNull() ?: 0
        val dificuldade = binding.spDificuldade.selectedItem.toString()
        val categoria = binding.spCategoria.selectedItem.toString()
        val preparacao = binding.etPreparacao.text.toString()

        if (nome.isEmpty() || listaIngredientesTemp.isEmpty()) {
            Toast.makeText(this, "Dados incompletos", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                setLoadingState(true)

                val receitaAtualizada = Receita(
                    nome = nome,
                    tempo = tempo,
                    porcoes = porcoes,
                    dificuldade = dificuldade,
                    categoria = categoria,
                    preparacao = preparacao
                )

                SupabaseManager.client.from("receitas").update(receitaAtualizada) {
                    filter { eq("id", receitaId) }
                }

                SupabaseManager.client.from("receita_ingredientes").delete {
                    filter { eq("receita_id", receitaId) }
                }

                listaIngredientesTemp.forEach { (nomeIng, qtd, med) ->
                    val existente = SupabaseManager.client.from("ingredientes")
                        .select { filter { eq("nome", nomeIng) } }.decodeList<Ingrediente>().firstOrNull()

                    val idIng = existente?.id ?: SupabaseManager.client.from("ingredientes")
                        .insert(Ingrediente(nome = nomeIng)) { select() }.decodeSingle<Ingrediente>().id!!

                    SupabaseManager.client.from("receita_ingredientes").insert(
                        ReceitaIngrediente(receitaId = receitaId, ingredienteId = idIng, quantidade = qtd, medida = med)
                    )
                }

                Toast.makeText(this@EditarReceita, "Receita atualizada!", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                e.printStackTrace()
                setLoadingState(false)
                Toast.makeText(this@EditarReceita, "Erro de serialização: Tente novamente", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setLoadingState(loading: Boolean) {
        binding.pbEditar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnGuardarReceita.isEnabled = !loading
        binding.scrollViewEditar.alpha = if (loading) 0.5f else 1.0f
    }
}