package com.example.projeto_cmen_gestor_receitas_lista_compras

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.projeto_cmen_gestor_receitas_lista_compras.databinding.ActivityEditarReceitaBinding
import com.example.projeto_cmen_gestor_receitas_lista_compras.model.Ingrediente
import com.example.projeto_cmen_gestor_receitas_lista_compras.model.Receita
import com.example.projeto_cmen_gestor_receitas_lista_compras.model.ReceitaIngrediente
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.launch

class EditarReceita : AppCompatActivity() {
    private lateinit var binding: ActivityEditarReceitaBinding
    private var receitaId: String = ""
    private var imagemAtual: String = "imagem_padrao.png"
    private var novaImageUri: Uri? = null
    private val listaIngredientesTemp = mutableListOf<Triple<String, Double, String>>()

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            novaImageUri = it
            binding.ivEditarPreview.setImageURI(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditarReceitaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        receitaId = intent.getStringExtra("RECEITA_ID") ?: ""
        setupSpinnersEstaticos()
        carregarIngredientesDaBD()

        binding.btnVoltar.setOnClickListener { finish() }
        binding.btnAlterarImagem.setOnClickListener { pickImageLauncher.launch("image/*") }

        binding.spIngredientesBD.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                binding.etNovoIngredienteNome.visibility = if (binding.spIngredientesBD.selectedItem == "--- + Adicionar Novo ---") View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        if (receitaId.isNotEmpty()) carregarDadosParaEdicao()

        binding.btnAdicionarIngrediente.setOnClickListener { adicionarIngredienteLocal() }
        binding.btnGuardarReceita.setOnClickListener { atualizarReceitaNoSupabase() }
    }

    private fun Spinner.setSelectionByValue(value: String?) {
        val adapter = this.adapter ?: return
        for (i in 0 until adapter.count) {
            if (adapter.getItem(i).toString().equals(value, ignoreCase = true)) {
                this.setSelection(i)
                break
            }
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

                binding.spDificuldade.setSelectionByValue(receita.dificuldade)
                binding.spCategoria.setSelectionByValue(receita.categoria)

                imagemAtual = receita.imagemCaminho
                binding.ivEditarPreview.load(receita.urlCompleta)

                val itens = SupabaseManager.client.from("receita_ingredientes").select(Columns.raw("*, ingredientes(nome)")) {
                    filter { eq("receita_id", receitaId) }
                }.decodeList<ReceitaIngrediente>()

                itens.forEach { listaIngredientesTemp.add(Triple(it.ingredientes?.nome ?: "", it.quantidade, it.medida)) }
                atualizarTextoLista()
                setLoadingState(false)
            } catch (e: Exception) {
                e.printStackTrace()
                setLoadingState(false)
            }
        }
    }

    private fun atualizarReceitaNoSupabase() {
        lifecycleScope.launch {
            try {
                setLoadingState(true)
                var caminhoFinal = imagemAtual

                novaImageUri?.let { uri ->
                    caminhoFinal = "img_${System.currentTimeMillis()}.jpg"
                    val bytes = contentResolver.openInputStream(uri)?.readBytes()
                    bytes?.let { SupabaseManager.client.storage.from("imagens_receita").upload(caminhoFinal, it) }
                }

                val receitaAtualizada = Receita(
                    nome = binding.etNome.text.toString(),
                    tempo = binding.etTempo.text.toString().toIntOrNull() ?: 0,
                    porcoes = binding.etPorcoes.text.toString().toIntOrNull() ?: 0,
                    dificuldade = binding.spDificuldade.selectedItem.toString(),
                    categoria = binding.spCategoria.selectedItem.toString(),
                    preparacao = binding.etPreparacao.text.toString(),
                    imagemCaminho = caminhoFinal
                )

                SupabaseManager.client.from("receitas").update(receitaAtualizada) { filter { eq("id", receitaId) } }
                SupabaseManager.client.from("receita_ingredientes").delete { filter { eq("receita_id", receitaId) } }

                listaIngredientesTemp.forEach { (n, q, m) ->
                    val idIng = SupabaseManager.client.from("ingredientes").select { filter { eq("nome", n) } }.decodeList<Ingrediente>().firstOrNull()?.id
                        ?: SupabaseManager.client.from("ingredientes").insert(Ingrediente(nome = n)) { select() }.decodeSingle<Ingrediente>().id!!
                    SupabaseManager.client.from("receita_ingredientes").insert(ReceitaIngrediente(receitaId = receitaId, ingredienteId = idIng, quantidade = q, medida = m))
                }
                finish()
            } catch (e: Exception) {
                setLoadingState(false)
                e.printStackTrace()
            }
        }
    }

    private fun carregarIngredientesDaBD() {
        lifecycleScope.launch {
            val existentes = SupabaseManager.client.from("ingredientes").select().decodeList<Ingrediente>()
            val nomes = existentes.map { it.nome }.sorted().toMutableList()
            nomes.add(0, "--- Selecione um ingrediente ---")
            nomes.add("--- + Adicionar Novo ---")
            binding.spIngredientesBD.adapter = ArrayAdapter(this@EditarReceita, android.R.layout.simple_spinner_dropdown_item, nomes)
        }
    }

    private fun setupSpinnersEstaticos() {
        binding.spDificuldade.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("baixa", "média", "alta"))
        binding.spCategoria.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("carne", "peixe", "vegetariana", "sobremesas", "outro"))
        binding.spMedidaIngrediente.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("unidade", "g", "kg", "ml", "L", "colher", "chávena", "qb"))
    }

    private fun adicionarIngredienteLocal() {
        val sel = binding.spIngredientesBD.selectedItem.toString()
        val nome = if (sel == "--- + Adicionar Novo ---") binding.etNovoIngredienteNome.text.toString().lowercase() else sel.lowercase()
        if (nome.isNotEmpty() && !nome.contains("---")) {
            listaIngredientesTemp.add(Triple(nome, binding.etQtdIngrediente.text.toString().toDoubleOrNull() ?: 0.0, binding.spMedidaIngrediente.selectedItem.toString()))
            atualizarTextoLista()
        }
    }

    private fun atualizarTextoLista() {
        binding.tvListaTemp.text = listaIngredientesTemp.joinToString("\n") { "• ${it.first}: ${it.second} ${it.third}" }
    }

    private fun setLoadingState(l: Boolean) {
        binding.pbEditar.visibility = if (l) View.VISIBLE else View.GONE
        binding.btnGuardarReceita.isEnabled = !l
    }
}