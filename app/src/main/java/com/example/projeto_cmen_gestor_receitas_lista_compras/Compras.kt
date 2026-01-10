package com.example.projeto_cmen_gestor_receitas_lista_compras

// --- IMPORTS OBRIGATÓRIOS DO ANDROID ---
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch

// --- IMPORTS DO SUPABASE ---
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order

// --- IMPORTS DO PROJETO ---
import com.example.projeto_cmen_gestor_receitas_lista_compras.databinding.ActivityComprasBinding
import com.example.projeto_cmen_gestor_receitas_lista_compras.model.Ingrediente
import com.example.projeto_cmen_gestor_receitas_lista_compras.model.ListaComprasItem
import com.example.projeto_cmen_gestor_receitas_lista_compras.ui.ComprasAdapter

class Compras : AppCompatActivity() {

    private lateinit var binding: ActivityComprasBinding
    private lateinit var adapter: ComprasAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityComprasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configura a lista visual (RecyclerView) e carrega os dados
        setupRecyclerView()
        carregarListaCompras()

        // Configura o botão flutuante (+)
        binding.fabAdicionarItem.setOnClickListener {
            mostrarDialogoAdicionar()
        }
    }

    // --- CONFIGURAÇÃO DA LISTA ---
    private fun setupRecyclerView() {
        adapter = ComprasAdapter(emptyList()) { item ->
            // Define o que acontece ao clicar no botão de apagar (lixo)
            confirmarRemocao(item)
        }
        binding.rvCompras.layoutManager = LinearLayoutManager(this)
        binding.rvCompras.adapter = adapter
    }

    // --- CARREGAMENTO DE DADOS (SUPABASE) ---
    private fun carregarListaCompras() {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE

                // Faz o SELECT na tabela "lista_compras_itens"
                // Usa "ingredientes(*)" para fazer o JOIN e trazer o nome do ingrediente automaticamente
                val lista = SupabaseManager.client.from("lista_compras_itens")
                    .select(columns = Columns.list("*, ingredientes(*)"))
                    .decodeList<ListaComprasItem>()

                adapter.atualizarLista(lista)

                // Mostra ou esconde a mensagem "Lista Vazia"
                binding.tvVazio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@Compras, "Erro ao carregar: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    // ========================================================================
    // LÓGICA DE ADICIONAR (COM DOIS SPINNERS)
    // ========================================================================

    // 1. Busca ingredientes disponíveis na BD antes de abrir a janela
    private fun mostrarDialogoAdicionar() {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE

                // Busca ingredientes ordenados alfabeticamente
                val listaIngredientes = SupabaseManager.client.from("ingredientes")
                    .select {
                        order("nome", order = Order.ASCENDING)
                    }
                    .decodeList<Ingrediente>()

                binding.progressBar.visibility = View.GONE

                if (listaIngredientes.isEmpty()) {
                    Toast.makeText(this@Compras, "Crie ingredientes primeiro no Gestor!", Toast.LENGTH_LONG).show()
                    return@launch
                }

                // Se houver ingredientes, abre a janela de escolha
                abrirDialogoComSpinner(listaIngredientes)

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                e.printStackTrace()
                Toast.makeText(this@Compras, "Erro ao buscar ingredientes", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 2. Constrói a janela (Dialog) com: Ingrediente (Spinner), Quantidade (Input), Medida (Spinner)
    private fun abrirDialogoComSpinner(ingredientes: List<Ingrediente>) {
        val context = this
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 40, 50, 10)

        // --- A. SPINNER DE INGREDIENTES ---
        val spinnerIngredientes = Spinner(context)
        val nomesIngredientes = ingredientes.map { it.nome }
        val adapterIngredientes = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, nomesIngredientes)
        spinnerIngredientes.adapter = adapterIngredientes
        layout.addView(spinnerIngredientes)

        // Espaço estético
        layout.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(1, 30) })

        // --- B. INPUT DE QUANTIDADE ---
        val inputQtd = EditText(context)
        inputQtd.hint = "Quantidade (ex: 1.5)"
        inputQtd.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        layout.addView(inputQtd)

        // Espaço estético
        layout.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(1, 30) })

        // --- C. SPINNER DE MEDIDAS (ALTERAÇÃO NOVA) ---
        val spinnerMedidas = Spinner(context)
        // Lista fixa de opções
        val opcoesMedidas = listOf("un", "kg", "g", "L", "ml")
        val adapterMedidas = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, opcoesMedidas)
        spinnerMedidas.adapter = adapterMedidas
        layout.addView(spinnerMedidas)

        AlertDialog.Builder(context)
            .setTitle("Adicionar à Lista")
            .setView(layout)
            .setPositiveButton("Adicionar") { _, _ ->
                val qtdStr = inputQtd.text.toString()

                // Pega a posição e o valor selecionado nos Spinners
                val posicaoIngrediente = spinnerIngredientes.selectedItemPosition
                val medidaSelecionada = spinnerMedidas.selectedItem.toString()

                if (qtdStr.isNotEmpty() && posicaoIngrediente >= 0) {
                    val ingredienteEscolhido = ingredientes[posicaoIngrediente]
                    val qtd = qtdStr.toDoubleOrNull() ?: 1.0

                    // Envia o ID, a quantidade e a medida escolhida para a BD
                    salvarItem(ingredienteEscolhido.id!!, qtd, medidaSelecionada)
                } else {
                    Toast.makeText(context, "Preencha a quantidade", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // 3. Guarda o item na base de dados
    private fun salvarItem(ingredienteId: String, quantidade: Double, medida: String) {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE

                // Cria o objeto para enviar ao Supabase
                val novoItem = ListaComprasItem(
                    ingredienteId = ingredienteId,
                    quantidade = quantidade,
                    medida = medida
                )

                SupabaseManager.client.from("lista_compras_itens").insert(novoItem)

                Toast.makeText(this@Compras, "Adicionado!", Toast.LENGTH_SHORT).show()
                carregarListaCompras() // Recarrega a lista para mostrar o novo item

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@Compras, "Erro ao adicionar: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    // ========================================================================
    // LÓGICA DE REMOVER
    // ========================================================================

    private fun confirmarRemocao(item: ListaComprasItem) {
        AlertDialog.Builder(this)
            .setTitle("Remover")
            .setMessage("Já comprou este item?")
            .setPositiveButton("Sim") { _, _ ->
                item.id?.let { apagarItem(it) }
            }
            .setNegativeButton("Não", null)
            .show()
    }

    private fun apagarItem(id: String) {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                SupabaseManager.client.from("lista_compras_itens").delete {
                    filter { eq("id", id) }
                }
                carregarListaCompras() // Atualiza a lista visualmente
            } catch (e: Exception) {
                Toast.makeText(this@Compras, "Erro ao remover", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
}