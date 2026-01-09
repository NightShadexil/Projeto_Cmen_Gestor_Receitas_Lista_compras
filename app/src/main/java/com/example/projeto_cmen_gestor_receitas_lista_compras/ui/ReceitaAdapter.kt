package com.example.projeto_cmen_gestor_receitas_lista_compras.ui

import Receita
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.projeto_cmen_gestor_receitas_lista_compras.VisualizarReceita
import com.example.projeto_cmen_gestor_receitas_lista_compras.databinding.ItemReceitaBinding
import androidx.core.graphics.toColorInt

@Suppress("DEPRECATION")
class ReceitaAdapter(
    private var lista: List<Receita>,
    private val onRemoverClick: (Receita, Int) -> Unit
) : RecyclerView.Adapter<ReceitaAdapter.ReceitaViewHolder>() {

    inner class ReceitaViewHolder(val binding: ItemReceitaBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReceitaViewHolder {
        val binding = ItemReceitaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReceitaViewHolder(binding)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ReceitaViewHolder, position: Int) {
        val receita = lista[position]
        with(holder.binding) {
            tvNomeReceita.text = receita.nome
            tvCategoria.text = receita.categoria
            tvDificuldade.text = "Dificuldade: ${receita.dificuldade}"
            tvTempo.text = "🕒 ${receita.tempo} min"

            // Aplica cor dinâmica à categoria
            tvCategoria.setTextColor(Color.WHITE)
            tvCategoria.setBackgroundColor(obterCorCategoria(receita.categoria))

            btnRemover.setOnClickListener { onRemoverClick(receita, holder.adapterPosition) }
        }

        holder.itemView.setOnClickListener {
            val intent = Intent(holder.itemView.context, VisualizarReceita::class.java)
            intent.putExtra("RECEITA_ID", receita.id)
            holder.itemView.context.startActivity(intent)
        }
    }

    private fun obterCorCategoria(categoria: String?): Int {
        return when (categoria?.lowercase()) {
            "carne" -> "#E57373".toColorInt()        // Vermelho
            "peixe" -> "#64B5F6".toColorInt()        // Azul
            "vegetariana" -> "#81C784".toColorInt()  // Verde
            "sobremesas" -> "#F06292".toColorInt()   // Rosa
            else -> "#FFB74D".toColorInt()           // Laranja padrão
        }
    }

    override fun getItemCount(): Int = lista.size

    @SuppressLint("NotifyDataSetChanged")
    fun atualizarLista(novaLista: List<Receita>) {
        this.lista = novaLista
        notifyDataSetChanged()
    }
}