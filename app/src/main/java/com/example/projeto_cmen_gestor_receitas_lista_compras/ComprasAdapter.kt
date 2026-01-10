package com.example.projeto_cmen_gestor_receitas_lista_compras.ui

import ListaComprasItem
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.projeto_cmen_gestor_receitas_lista_compras.databinding.ItemCompraBinding


class ComprasAdapter(
    private var lista: List<ListaComprasItem>,
    private val onDeleteClick: (ListaComprasItem) -> Unit
) : RecyclerView.Adapter<ComprasAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemCompraBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCompraBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = lista[position]

        // Tenta obter o nome do objeto aninhado 'ingredientes'.
        // Se for null, usa "Item..."
        val nomeIngrediente = item.ingredientes?.nome ?: "Ingrediente..."

        holder.binding.tvNomeProduto.text = nomeIngrediente
        holder.binding.tvQuantidade.text = "${item.quantidade} ${item.medida}"

        holder.binding.btnRemoverCompra.setOnClickListener {
            onDeleteClick(item)
        }
    }

    override fun getItemCount(): Int = lista.size

    fun atualizarLista(novaLista: List<ListaComprasItem>) {
        lista = novaLista
        notifyDataSetChanged()
    }
}