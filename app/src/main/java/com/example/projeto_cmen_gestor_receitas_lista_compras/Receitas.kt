package com.example.projeto_cmen_gestor_receitas_lista_compras

import Receita
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.projeto_cmen_gestor_receitas_lista_compras.databinding.ActivityReceitasBinding
import com.example.projeto_cmen_gestor_receitas_lista_compras.ui.ReceitaAdapter
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

class Receitas : AppCompatActivity() {

    private lateinit var binding: ActivityReceitasBinding
    private lateinit var receitaAdapter: ReceitaAdapter
    private var listaOriginal: List<Receita> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReceitasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupFiltros()

        binding.btnIrParaNovaReceita.setOnClickListener {
            startActivity(Intent(this, NovaReceita::class.java))
        }

        binding.btnRecarregar.setOnClickListener { carregarReceitas() }
    }

    override fun onResume() {
        super.onResume()
        carregarReceitas()
    }

    private fun setupFiltros() {
        val categorias = arrayOf("Todas as Categorias", "Carne", "Peixe", "Vegetariana", "Sobremesas", "Outro")
        val dificuldades = arrayOf("Todas as Dificuldades", "Baixa", "Média", "Alta")

        binding.spFiltroCategoria.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categorias)
        binding.spFiltroDificuldade.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, dificuldades)

        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) { aplicarFiltros() }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        binding.spFiltroCategoria.onItemSelectedListener = listener
        binding.spFiltroDificuldade.onItemSelectedListener = listener
    }

    private fun aplicarFiltros() {
        val catSelt = binding.spFiltroCategoria.selectedItem.toString().lowercase()
        val difSelt = binding.spFiltroDificuldade.selectedItem.toString().lowercase()

        val listaFiltrada = listaOriginal.filter { receita ->
            (catSelt == "todas as categorias" || receita.categoria.lowercase() == catSelt) &&
                    (difSelt == "todas as dificuldades" || receita.dificuldade.lowercase() == difSelt)
        }

        receitaAdapter.atualizarLista(listaFiltrada)
        mostrarEstadoVazio(listaFiltrada.isEmpty())
    }

    private fun carregarReceitas() {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                val lista = SupabaseManager.client.from("receitas").select().decodeList<Receita>()
                listaOriginal = lista
                binding.progressBar.visibility = View.GONE
                aplicarFiltros()
            } catch (e: Exception) {
                e.printStackTrace()
                binding.progressBar.visibility = View.GONE
                mostrarEstadoVazio(true)
            }
        }
    }

    private fun setupRecyclerView() {
        receitaAdapter = ReceitaAdapter(emptyList()) { receita, _ ->
            confirmarRemocao(receita)
        }
        binding.rvReceitas.apply {
            adapter = receitaAdapter
            layoutManager = LinearLayoutManager(this@Receitas)
        }
    }

    private fun confirmarRemocao(receita: Receita) {
        AlertDialog.Builder(this)
            .setTitle("Remover Receita")
            .setMessage("Deseja eliminar '${receita.nome}'? Esta ação não pode ser desfeita.")
            .setPositiveButton("Sim") { _, _ -> executarRemocao(receita.id) }
            .setNegativeButton("Não", null)
            .show()
    }

    private fun executarRemocao(id: String?) {
        if (id == null) return
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                SupabaseManager.client.from("receita_ingredientes").delete { filter { eq("receita_id", id) } }
                SupabaseManager.client.from("receitas").delete { filter { eq("id", id) } }

                Toast.makeText(this@Receitas, "Eliminada com sucesso!", Toast.LENGTH_SHORT).show()
                carregarReceitas()
            } catch (e: Exception) {
                e.printStackTrace()
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@Receitas, "Erro ao eliminar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun mostrarEstadoVazio(vazio: Boolean) {
        binding.rvReceitas.visibility = if (vazio) View.GONE else View.VISIBLE
        binding.layoutVazio.visibility = if (vazio) View.VISIBLE else View.GONE
    }
}