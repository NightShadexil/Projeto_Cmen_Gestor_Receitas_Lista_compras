package com.example.projeto_cmen_gestor_receitas_lista_compras

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.projeto_cmen_gestor_receitas_lista_compras.databinding.ActivityNovaReceitaBinding
import com.example.projeto_cmen_gestor_receitas_lista_compras.model.Ingrediente
import com.example.projeto_cmen_gestor_receitas_lista_compras.model.Receita
import com.example.projeto_cmen_gestor_receitas_lista_compras.model.ReceitaIngrediente
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.launch

class NovaReceita : AppCompatActivity() {

    private lateinit var binding: ActivityNovaReceitaBinding
    private val listaIngredientesTemp = mutableListOf<Triple<String, Double, String>>()
    private var imageUri: Uri? = null

    private val URL_IMAGEM_PADRAO = "https://irdivilrzypwtetakgdu.supabase.co/storage/v1/object/public/imagens_receita/imagem_padrao.png"

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            imageUri = it
            binding.ivReceitaPreview.setImageURI(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNovaReceitaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.ivReceitaPreview.load(URL_IMAGEM_PADRAO) {
            crossfade(true)
            placeholder(android.R.drawable.ic_menu_gallery)
            error(android.R.drawable.ic_menu_report_image)
        }

        setupSpinnersEstaticos()
        carregarIngredientesDaBD()

        binding.btnSelecionarImagem.setOnClickListener { pickImageLauncher.launch("image/*") }

        binding.spIngredientesBD.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                val sel = binding.spIngredientesBD.selectedItem.toString()
                binding.etNovoIngredienteNome.visibility = if (sel == "--- + Adicionar Novo ---") View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        binding.btnAdicionarIngrediente.setOnClickListener { adicionarIngredienteAListaLocal() }
        binding.btnGuardarReceita.setOnClickListener { salvarReceitaFinal() }
    }

    private fun carregarIngredientesDaBD() {
        lifecycleScope.launch {
            try {
                val existentes = SupabaseManager.client.from("ingredientes").select().decodeList<Ingrediente>()
                val nomes = existentes.map { it.nome }.sorted().toMutableList()
                nomes.add(0, "--- Selecione um ingrediente ---")
                nomes.add("--- + Adicionar Novo ---")
                binding.spIngredientesBD.adapter = ArrayAdapter(this@NovaReceita, android.R.layout.simple_spinner_dropdown_item, nomes)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun salvarReceitaFinal() {
        val nome = binding.etNome.text.toString()
        if (nome.isEmpty() || listaIngredientesTemp.isEmpty()) {
            Toast.makeText(this, "Preencha o nome e adicione ingredientes", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                setLoadingState(true)
                var nomeArquivo = "imagem_padrao.png"

                imageUri?.let { uri ->
                    val extensao = contentResolver.getType(uri)?.split("/")?.last() ?: "jpg"
                    nomeArquivo = "img_${System.currentTimeMillis()}.$extensao"
                    val bytes = contentResolver.openInputStream(uri)?.readBytes()
                    bytes?.let { SupabaseManager.client.storage.from("imagens_receita").upload(nomeArquivo, it) }
                }

                val novaReceita = Receita(
                    nome = nome,
                    tempo = binding.etTempo.text.toString().toIntOrNull() ?: 0,
                    porcoes = binding.etPorcoes.text.toString().toIntOrNull() ?: 0,
                    dificuldade = binding.spDificuldade.selectedItem.toString(),
                    categoria = binding.spCategoria.selectedItem.toString(),
                    preparacao = binding.etPreparacao.text.toString(),
                    imagemCaminho = nomeArquivo
                )

                val guardada = SupabaseManager.client.from("receitas").insert(novaReceita) { select() }.decodeSingle<Receita>()

                listaIngredientesTemp.forEach { (n, q, m) ->
                    val idIng = SupabaseManager.client.from("ingredientes").select { filter { eq("nome", n) } }.decodeList<Ingrediente>().firstOrNull()?.id
                        ?: SupabaseManager.client.from("ingredientes").insert(Ingrediente(nome = n)) { select() }.decodeSingle<Ingrediente>().id!!

                    SupabaseManager.client.from("receita_ingredientes").insert(
                        ReceitaIngrediente(
                            receitaId = guardada.id!!,
                            ingredienteId = idIng,
                            quantidade = q,
                            medida = m
                        )
                    )
                }
                Toast.makeText(this@NovaReceita, "Receita guardada com sucesso!", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                e.printStackTrace()
                setLoadingState(false)
                Toast.makeText(this@NovaReceita, "Erro ao guardar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSpinnersEstaticos() {
        binding.spDificuldade.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("baixa", "média", "alta"))
        binding.spCategoria.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("carne", "peixe", "vegetariana", "sobremesas", "outro"))
        binding.spMedidaIngrediente.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("unidade", "g", "kg", "ml", "L", "colher", "chávena", "qb"))
    }

    private fun adicionarIngredienteAListaLocal() {
        val sel = binding.spIngredientesBD.selectedItem.toString()
        val nome = if (sel == "--- + Adicionar Novo ---") binding.etNovoIngredienteNome.text.toString().lowercase() else sel.lowercase()
        if (nome.isNotEmpty() && !nome.contains("---")) {
            listaIngredientesTemp.add(Triple(nome, binding.etQtdIngrediente.text.toString().toDoubleOrNull() ?: 0.0, binding.spMedidaIngrediente.selectedItem.toString()))
            atualizarTextoLista()

            binding.etNovoIngredienteNome.text.clear()
            binding.etQtdIngrediente.text.clear()
            binding.spIngredientesBD.setSelection(0)
        }
    }

    private fun atualizarTextoLista() {
        binding.tvListaTemp.text = listaIngredientesTemp.joinToString("\n") { "• ${it.first}: ${it.second} ${it.third}" }
    }

    private fun setLoadingState(l: Boolean) {
        binding.pbGuardar.visibility = if (l) View.VISIBLE else View.GONE
        binding.btnGuardarReceita.isEnabled = !l
    }
}