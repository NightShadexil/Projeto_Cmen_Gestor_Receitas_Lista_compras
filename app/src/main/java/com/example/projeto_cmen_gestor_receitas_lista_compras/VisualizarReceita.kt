package com.example.projeto_cmen_gestor_receitas_lista_compras

import Receita
import ReceitaIngrediente
import ListaComprasItem
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.projeto_cmen_gestor_receitas_lista_compras.databinding.ActivityVisualizarReceitaBinding
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch

class VisualizarReceita : AppCompatActivity() {
    private lateinit var binding: ActivityVisualizarReceitaBinding
    private var receitaId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVisualizarReceitaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        receitaId = intent.getStringExtra("RECEITA_ID") ?: ""

        binding.btnVoltar.setOnClickListener {
            finish()
        }

        binding.btnEditar.setOnClickListener {
            val intent = Intent(this, EditarReceita::class.java)
            intent.putExtra("RECEITA_ID", receitaId)
            startActivity(intent)
        }

        if (receitaId.isNotEmpty()) {
            carregarDadosReceita()
        }

        binding.btnGerarLista.setOnClickListener {
            gerarListaCompras()
        }
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

                binding.tvTituloDetalhe.text = receita.nome
                binding.tvTempoDetalhe.text = "🕒 ${receita.tempo} min"
                binding.tvCategoriaDetalhe.text = receita.categoria
                binding.tvPreparacaoDetalhe.text = receita.preparacao

                val textoIngredientes = listaItens.joinToString("\n") { item ->
                    "• ${item.quantidade} ${item.medida} de ${item.ingredientes?.nome ?: "Desconhecido"}"
                }
                binding.tvIngredientesDetalhe.text = textoIngredientes.ifEmpty { "Sem ingredientes." }

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@VisualizarReceita, "Erro ao carregar detalhes", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun gerarListaCompras() {
        lifecycleScope.launch {
            try {
                val itensReceita = SupabaseManager.client.from("receita_ingredientes")
                    .select { filter { eq("receita_id", receitaId) } }
                    .decodeList<ReceitaIngrediente>()

                itensReceita.forEach { item ->
                    val novoItem = ListaComprasItem(
                        ingredienteId = item.ingredienteId,
                        quantidade = item.quantidade,
                        medida = item.medida
                    )
                    SupabaseManager.client.from("lista_compras_itens").insert(novoItem)
                }

                Toast.makeText(this@VisualizarReceita, "Ingredientes adicionados à lista!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}