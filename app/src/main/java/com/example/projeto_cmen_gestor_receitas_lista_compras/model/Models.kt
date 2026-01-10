import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class Receita(
    val id: String? = null,
    val nome: String,
    @SerialName("tempo_preparacao") val tempo: Int,
    @SerialName("num_porcoes") val porcoes: Int,
    val dificuldade: String,
    val categoria: String,
    val preparacao: String
)

@Serializable
data class Ingrediente(
    val id: String? = null,
    val nome: String
)

@Serializable
data class ReceitaIngrediente(
    val id: String? = null,
    @SerialName("receita_id") val receitaId: String,
    @SerialName("ingrediente_id") val ingredienteId: String,
    val quantidade: Double,
    val medida: String,
    val ingredientes: Ingrediente? = null
)

@Serializable
data class ListaComprasItem(
    val id: String? = null,
    @SerialName("lista_id") val listaId: String? = null, // Pode ser null se for lista única
    @SerialName("ingrediente_id") val ingredienteId: String,
    val quantidade: Double,
    val medida: String,
    val ingredientes: Ingrediente? = null // Permite mostrar o nome (ex: "Arroz") na lista
)