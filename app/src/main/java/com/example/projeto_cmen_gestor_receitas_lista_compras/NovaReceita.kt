package com.example.projeto_cmen_gestor_receitas_lista_compras

import Ingrediente
import Receita
import ReceitaIngrediente
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.projeto_cmen_gestor_receitas_lista_compras.databinding.ActivityNovaReceitaBinding
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

class NovaReceita : AppCompatActivity() {

    private lateinit var binding: ActivityNovaReceitaBinding
    private val listaIngredientesTemp = mutableListOf<Triple<String, Double, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNovaReceitaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSpinners()
        carregarSugestoesIngredientes()

        binding.btnAdicionarIngrediente.setOnClickListener {
            adicionarIngredienteAListaLocal()
        }

        binding.btnGuardarReceita.setOnClickListener {
            salvarReceitaFinal()
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutNovaReceita) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
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

    private fun normalizar(texto: String) = texto.trim().lowercase()

    private fun carregarSugestoesIngredientes() {
        lifecycleScope.launch {
            try {
                val existentes = SupabaseManager.client.from("ingredientes")
                    .select().decodeList<Ingrediente>()

                if (existentes.isNotEmpty()) {
                    val nomes = existentes.map { it.nome }.distinct()
                    val adapter = ArrayAdapter(this@NovaReceita, android.R.layout.simple_dropdown_item_1line, nomes)
                    binding.autoCompleteIngrediente.setAdapter(adapter)
                    binding.autoCompleteIngrediente.threshold = 1
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun adicionarIngredienteAListaLocal() {
        val nomeRaw = binding.autoCompleteIngrediente.text.toString()
        val nomeLimpo = normalizar(nomeRaw)
        val qtd = binding.etQtdIngrediente.text.toString().toDoubleOrNull() ?: 0.0
        val medida = binding.spMedidaIngrediente.selectedItem.toString()

        if (nomeLimpo.isNotEmpty() && qtd > 0.0) {
            listaIngredientesTemp.add(Triple(nomeLimpo, qtd, medida))
            atualizarTextoLista()
            binding.autoCompleteIngrediente.text.clear()
            binding.etQtdIngrediente.text.clear()
        } else {
            Toast.makeText(this, "Preencha o nome e quantidade", Toast.LENGTH_SHORT).show()
        }
    }

    private fun atualizarTextoLista() {
        val resumo = listaIngredientesTemp.joinToString("\n") {
            "• ${it.first}: ${it.second} ${it.third}"
        }
        binding.tvListaTemp.text = resumo.ifEmpty { getString(R.string.msg_nenhum_ingrediente) }

        binding.tvListaTemp.setOnClickListener {
            if (listaIngredientesTemp.isNotEmpty()) {
                mostrarDialogoOpcoesIngrediente()
            }
        }
    }

    private fun mostrarDialogoOpcoesIngrediente() {
        val nomesItens = listaIngredientesTemp.map { "${it.first} (${it.second} ${it.third})" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Escolha um ingrediente para alterar")
            .setItems(nomesItens) { _, index ->
                mostrarMenuAcao(index)
            }
            .show()
    }

    private fun mostrarMenuAcao(index: Int) {
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
                        val pos = adapter.getPosition(item.third)
                        binding.spMedidaIngrediente.setSelection(pos)

                        listaIngredientesTemp.removeAt(index)
                        atualizarTextoLista()
                        Toast.makeText(this, "Edite os valores e clique em adicionar", Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        listaIngredientesTemp.removeAt(index)
                        atualizarTextoLista()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun salvarReceitaFinal() {
        val nome = binding.etNome.text.toString()
        val tempo = binding.etTempo.text.toString().toIntOrNull() ?: 0
        val porcoes = binding.etPorcoes.text.toString().toIntOrNull() ?: 0
        val dificuldade = binding.spDificuldade.selectedItem.toString()
        val categoria = binding.spCategoria.selectedItem.toString()
        val preparacao = binding.etPreparacao.text.toString()

        if (nome.isEmpty() || listaIngredientesTemp.isEmpty()) {
            Toast.makeText(this, "Dados incompletos!", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val receitaGuardada = SupabaseManager.client.from("receitas")
                    .insert(Receita(nome=nome, tempo=tempo, porcoes=porcoes, dificuldade=dificuldade, categoria=categoria, preparacao=preparacao)) { select() }
                    .decodeSingle<Receita>()

                val idReceita = receitaGuardada.id ?: return@launch

                listaIngredientesTemp.forEach { (nomeIng, qtd, med) ->
                    val existente = SupabaseManager.client.from("ingredientes")
                        .select { filter { eq("nome", nomeIng) } }
                        .decodeList<Ingrediente>().firstOrNull()

                    val idIng = if (existente != null) existente.id!! else {
                        val novo = SupabaseManager.client.from("ingredientes")
                            .insert(Ingrediente(nome = nomeIng)) { select() }
                            .decodeSingle<Ingrediente>()
                        novo.id!!
                    }

                    SupabaseManager.client.from("receita_ingredientes").insert(
                        ReceitaIngrediente(receitaId = idReceita, ingredienteId = idIng, quantidade = qtd, medida = med)
                    )
                }
                Toast.makeText(this@NovaReceita, "Receita guardada com sucesso!", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
}