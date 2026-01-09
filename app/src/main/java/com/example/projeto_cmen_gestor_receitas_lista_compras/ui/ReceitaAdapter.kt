package com.example.projeto_cmen_gestor_receitas_lista_compras.ui

import Receita
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
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
            tvCategoria.text = receita.categoria.uppercase()
            tvTempo.text = "🕒 ${receita.tempo} min"

            val label = "Dificuldade: "
            val valor = receita.dificuldade.uppercase()
            val sb = SpannableStringBuilder(label + valor)
            val corDif = when (receita.dificuldade.lowercase()) {
                "baixa" -> "#2ECC71".toColorInt()
                "média" -> "#F1C40F".toColorInt()
                "alta"  -> "#E74C3C".toColorInt()
                else    -> Color.BLACK
            }
            sb.setSpan(ForegroundColorSpan(corDif), label.length, (label + valor).length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(StyleSpan(Typeface.BOLD), label.length, (label + valor).length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            tvDificuldade.text = sb

            val corCat = when (receita.categoria.lowercase()) {
                "carne" -> "#E57373".toColorInt()
                "peixe" -> "#64B5F6".toColorInt()
                "vegetariana" -> "#81C784".toColorInt()
                "sobremesas" -> "#F06292".toColorInt()
                else -> "#FFB74D".toColorInt()
            }
            val shape = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(corCat)
            }
            tvCategoria.background = shape

            btnRemover.setOnClickListener { onRemoverClick(receita, holder.adapterPosition) }
        }

        holder.itemView.setOnClickListener {
            val intent = Intent(holder.itemView.context, VisualizarReceita::class.java)
            intent.putExtra("RECEITA_ID", receita.id)
            holder.itemView.context.startActivity(intent)
        }
    }

    override fun getItemCount() = lista.size

    @SuppressLint("NotifyDataSetChanged")
    fun atualizarLista(novaLista: List<Receita>) {
        this.lista = novaLista
        notifyDataSetChanged()
    }
}