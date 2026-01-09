package com.example.projeto_cmen_gestor_receitas_lista_compras

import Ingrediente
import Receita
import ReceitaIngrediente
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.projeto_cmen_gestor_receitas_lista_compras.databinding.ActivityEditarReceitaBinding
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch

class EditarReceita : AppCompatActivity() {
    private lateinit var binding: ActivityEditarReceitaBinding
    private var receitaId: String = ""
    private val listaIngredientesTemp = mutableListOf<Triple<String, Double, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditarReceitaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        receitaId = intent.getStringExtra("RECEITA_ID") ?: ""

        binding.btnVoltar.setOnClickListener { finish() }

        setupSpinners()
        carregarSugestoesIngredientes()

        if (receitaId.isNotEmpty()) carregarDadosParaEdicao()

        binding.btnAdicionarIngrediente.setOnClickListener { adicionarIngredienteLocal() }
        binding.btnGuardarReceita.setOnClickListener { atualizarReceitaNoSupabase() }
    }

    private fun carregarDadosParaEdicao() {
        lifecycleScope.launch {
            try {
                val receita = SupabaseManager.client.from("receitas").select {
                    filter { eq("id", receitaId) }
                }.decodeSingle<Receita>()

                binding.etNome.setText(receita.nome)
                binding.etTempo.setText(receita.tempo.toString())
                binding.etPorcoes.setText(receita.porcoes.toString())
                binding.etPreparacao.setText(receita.preparacao)

                val itens = SupabaseManager.client.from("receita_ingredientes").select(Columns.raw("*, ingredientes(nome)")) {
                    filter { eq("receita_id", receitaId) }
                }.decodeList<ReceitaIngrediente>()

                itens.forEach { item ->
                    listaIngredientesTemp.add(Triple(item.ingredientes?.nome ?: "", item.quantidade, item.medida))
                }
                atualizarTextoLista()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@EditarReceita, "Erro ao carregar dados", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSpinners() {
        val dificuldades = arrayOf("baixa", "média", "alta")
        val categorias = arrayOf("carne", "peixe", "vegetariana", "sobremesas", "outro")
        val medidas = arrayOf("unidade", "g", "kg", "ml", "L", "colher", "chávena", "qb")

        binding.spDificuldade.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, dificuldades)
        binding.spCategoria.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categorias)
        binding.spMedidaIngrediente.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, medidas)
    }

    private fun carregarSugestoesIngredientes() {
        lifecycleScope.launch {
            try {
                val existentes = SupabaseManager.client.from("ingredientes").select().decodeList<Ingrediente>()
                if (existentes.isNotEmpty()) {
                    val nomes = existentes.map { it.nome }.distinct()
                    val adapter = ArrayAdapter(this@EditarReceita, android.R.layout.simple_dropdown_item_1line, nomes)
                    binding.autoCompleteIngrediente.setAdapter(adapter)
                    binding.autoCompleteIngrediente.threshold = 1
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun adicionarIngredienteLocal() {
        val nome = binding.autoCompleteIngrediente.text.toString().trim().lowercase()
        val qtd = binding.etQtdIngrediente.text.toString().toDoubleOrNull() ?: 0.0
        val med = binding.spMedidaIngrediente.selectedItem.toString()

        if (nome.isNotEmpty() && qtd > 0.0) {
            listaIngredientesTemp.add(Triple(nome, qtd, med))
            atualizarTextoLista()
            binding.autoCompleteIngrediente.text.clear()
            binding.etQtdIngrediente.text.clear()
        }
    }

    private fun atualizarTextoLista() {
        val resumo = listaIngredientesTemp.joinToString("\n") { "• ${it.first}: ${it.second} ${it.third}" }
        binding.tvListaTemp.text = resumo.ifEmpty { getString(R.string.msg_nenhum_ingrediente) }

        binding.tvListaTemp.setOnClickListener {
            if (listaIngredientesTemp.isNotEmpty()) mostrarMenuOpcoesIngrediente()
        }
    }

    private fun mostrarMenuOpcoesIngrediente() {
        val nomesItens = listaIngredientesTemp.map { "${it.first} (${it.second} ${it.third})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Escolha um ingrediente")
            .setItems(nomesItens) { _, index -> mostrarAcoes(index) }
            .show()
    }

    private fun mostrarAcoes(index: Int) {
        val opcoes = arrayOf("Editar Quantidade/Nome", "Remover da Lista")
        val item = listaIngredientesTemp[index]

        AlertDialog.Builder(this)
            .setTitle(item.first.uppercase())
            .setItems(opcoes) { _, acao ->
                when (acao) {
                    0 -> {
                        binding.autoCompleteIngrediente.setText(item.first)
                        binding.etQtdIngrediente.setText(item.second.toString())
                        val adapter = binding.spMedidaIngrediente.adapter as ArrayAdapter<String>
                        binding.spMedidaIngrediente.setSelection(adapter.getPosition(item.third))

                        listaIngredientesTemp.removeAt(index)
                        atualizarTextoLista()
                    }
                    1 -> {
                        listaIngredientesTemp.removeAt(index)
                        atualizarTextoLista()
                    }
                }
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
                // Atualizar receita
                SupabaseManager.client.from("receitas").update(
                    mapOf("nome" to nome, "tempo_preparacao" to tempo, "num_porcoes" to porcoes,
                        "dificuldade" to dificuldade, "categoria" to categoria, "preparacao" to preparacao)
                ) { filter { eq("id", receitaId) } }

                SupabaseManager.client.from("receita_ingredientes").delete { filter { eq("receita_id", receitaId) } }

                listaIngredientesTemp.forEach { (nomeIng, qtd, med) ->
                    val existente = SupabaseManager.client.from("ingredientes")
                        .select { filter { eq("nome", nomeIng) } }.decodeList<Ingrediente>().firstOrNull()

                    val idIng = existente?.id ?: SupabaseManager.client.from("ingredientes")
                        .insert(Ingrediente(nome = nomeIng)) { select() }.decodeSingle<Ingrediente>().id!!

                    SupabaseManager.client.from("receita_ingredientes").insert(
                        ReceitaIngrediente(receitaId = receitaId, ingredienteId = idIng, quantidade = qtd, medida = med)
                    )
                }
                Toast.makeText(this@EditarReceita, "Atualizado!", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
}