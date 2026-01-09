package com.example.projeto_cmen_gestor_receitas_lista_compras

import Receita
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReceitasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()

        binding.btnIrParaNovaReceita.setOnClickListener {
            startActivity(Intent(this, NovaReceita::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        carregarReceitas()
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

    private fun carregarReceitas() {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                binding.rvReceitas.visibility = View.GONE
                binding.layoutVazio.visibility = View.GONE

                val lista = SupabaseManager.client.from("receitas")
                    .select()
                    .decodeList<Receita>()

                binding.progressBar.visibility = View.GONE

                if (lista.isEmpty()) {
                    mostrarEstadoVazio(true)
                } else {
                    mostrarEstadoVazio(false)
                    receitaAdapter.atualizarLista(lista)
                }
            } catch (exception: Exception) {
                exception.printStackTrace()
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@Receitas, "Problema ao ligar ao servidor", Toast.LENGTH_SHORT).show()
                mostrarEstadoVazio(true)
            }
        }
    }

    private fun confirmarRemocao(receita: Receita) {
        AlertDialog.Builder(this)
            .setTitle("Remover Receita")
            .setMessage("Deseja eliminar '${receita.nome}'?")
            .setPositiveButton("Sim") { _, _ -> executarRemocao(receita.id) }
            .setNegativeButton("Não", null)
            .show()
    }

    private fun executarRemocao(id: String?) {
        if (id == null) return
        lifecycleScope.launch {
            try {
                SupabaseManager.client.from("receita_ingredientes").delete { filter { eq("receita_id", id) } }
                SupabaseManager.client.from("receitas").delete { filter { eq("id", id) } }
                Toast.makeText(this@Receitas, "Receita eliminada!", Toast.LENGTH_SHORT).show()
                carregarReceitas()
            } catch (e: Exception) {
                Toast.makeText(this@Receitas, "Erro ao eliminar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun mostrarEstadoVazio(vazio: Boolean) {
        if (vazio) {
            binding.rvReceitas.visibility = View.GONE
            binding.layoutVazio.visibility = View.VISIBLE
        } else {
            binding.rvReceitas.visibility = View.VISIBLE
            binding.layoutVazio.visibility = View.GONE
        }
    }
}