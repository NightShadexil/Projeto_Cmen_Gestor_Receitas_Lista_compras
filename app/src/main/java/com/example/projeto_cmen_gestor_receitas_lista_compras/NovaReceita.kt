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
import com.example.projeto_cmen_gestor_receitas_lista_compras.databinding.ActivityNovaReceitaBinding
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

class NovaReceita : AppCompatActivity() {

    private lateinit var binding: ActivityNovaReceitaBinding
    private val listaIngredientesTemp = mutableListOf<Triple<String, Double, String>>()
    private val selecione = "--- Selecione um ingrediente ---"
    private val novo = "--- + Adicionar Novo ---"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNovaReceitaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSpinnersEstaticos()
        carregarIngredientesDaBD()

        binding.spIngredientesBD.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                val selecionado = binding.spIngredientesBD.selectedItem.toString()
                binding.etNovoIngredienteNome.visibility = if (selecionado == novo) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        binding.btnAdicionarIngrediente.setOnClickListener { adicionarIngredienteAListaLocal() }
        binding.btnGuardarReceita.setOnClickListener { salvarReceitaFinal() }

        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutNovaReceita) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
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

    private fun carregarIngredientesDaBD() {
        lifecycleScope.launch {
            try {
                val existentes = SupabaseManager.client.from("ingredientes").select().decodeList<Ingrediente>()
                val nomes = existentes.map { it.nome }.sorted().toMutableList()

                nomes.add(0, selecione)
                nomes.add(novo)

                val adapter = ArrayAdapter(this@NovaReceita, android.R.layout.simple_spinner_dropdown_item, nomes)
                binding.spIngredientesBD.adapter = adapter
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun normalizar(texto: String) = texto.trim().lowercase()

    private fun adicionarIngredienteAListaLocal() {
        val selecao = binding.spIngredientesBD.selectedItem.toString()
        val nomeLimpo = if (selecao == novo) {
            normalizar(binding.etNovoIngredienteNome.text.toString())
        } else if (selecao != selecione) {
            normalizar(selecao)
        } else ""

        val qtd = binding.etQtdIngrediente.text.toString().toDoubleOrNull() ?: 0.0
        val medida = binding.spMedidaIngrediente.selectedItem.toString()

        if (nomeLimpo.isNotEmpty() && qtd > 0.0) {
            listaIngredientesTemp.add(Triple(nomeLimpo, qtd, medida))
            atualizarTextoLista()

            binding.etNovoIngredienteNome.text.clear()
            binding.etQtdIngrediente.text.clear()
            binding.spIngredientesBD.setSelection(0)
            carregarIngredientesDaBD()
        } else {
            Toast.makeText(this, "Selecione um ingrediente e quantidade válida", Toast.LENGTH_SHORT).show()
        }
    }

    private fun atualizarTextoLista() {
        val resumo = listaIngredientesTemp.joinToString("\n") { "• ${it.first}: ${it.second} ${it.third}" }
        binding.tvListaTemp.text = resumo.ifEmpty { getString(R.string.msg_nenhum_ingrediente) }

        binding.tvListaTemp.setOnClickListener {
            if (listaIngredientesTemp.isNotEmpty()) mostrarDialogoOpcoesIngrediente()
        }
    }

    private fun mostrarDialogoOpcoesIngrediente() {
        val nomesItens = listaIngredientesTemp.map { "${it.first} (${it.second} ${it.third})" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Escolha um ingrediente para alterar")
            .setItems(nomesItens) { _, index -> mostrarMenuAcao(index) }.show()
    }

    private fun mostrarMenuAcao(index: Int) {
        val item = listaIngredientesTemp[index]
        val opcoes = arrayOf("Editar", "Remover")
        AlertDialog.Builder(this)
            .setTitle(item.first.uppercase())
            .setItems(opcoes) { _, acao ->
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
                setLoadingState(true)
                val receitaGuardada = SupabaseManager.client.from("receitas")
                    .insert(Receita(nome=nome, tempo=tempo, porcoes=porcoes, dificuldade=dificuldade, categoria=categoria, preparacao=preparacao)) { select() }
                    .decodeSingle<Receita>()

                val idReceita = receitaGuardada.id ?: return@launch

                listaIngredientesTemp.forEach { (nomeIng, qtd, med) ->
                    val existente = SupabaseManager.client.from("ingredientes")
                        .select { filter { eq("nome", nomeIng) } }.decodeList<Ingrediente>().firstOrNull()

                    val idIng = existente?.id ?: SupabaseManager.client.from("ingredientes")
                        .insert(Ingrediente(nome = nomeIng)) { select() }.decodeSingle<Ingrediente>().id!!

                    SupabaseManager.client.from("receita_ingredientes").insert(
                        ReceitaIngrediente(receitaId = idReceita, ingredienteId = idIng, quantidade = qtd, medida = med)
                    )
                }
                Toast.makeText(this@NovaReceita, "Guardado!", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                e.printStackTrace()
                setLoadingState(false)
                Toast.makeText(this@NovaReceita, "Erro ao guardar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setLoadingState(loading: Boolean) {
        binding.pbGuardar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnGuardarReceita.isEnabled = !loading
        binding.scrollViewNovaReceita.alpha = if (loading) 0.5f else 1.0f
    }
}