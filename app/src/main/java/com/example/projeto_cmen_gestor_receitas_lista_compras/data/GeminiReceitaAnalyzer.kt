package com.example.projeto_cmen_gestor_receitas_lista_compras.data

import android.util.Log
import com.example.projeto_cmen_gestor_receitas_lista_compras.BuildConfig
import com.example.projeto_cmen_gestor_receitas_lista_compras.model.ListaComprasItem
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class NormalizacaoResponse(
    val itens: List<ItemOtimizado>
)

@Serializable
data class ItemOtimizado(
    val nome_original: String,
    val quantidade: Double,
    val medida: String,
    val nota_ia: String? = null
)

@Serializable
data class SugestaoIA(
    val nome: String,
    val tempo: Int,
    val porcoes: Int,
    val dificuldade: String,
    val categoria: String,
    val preparacao: String,
    val ingredientes: List<IngredienteSugeridoIA>
)

@Serializable
data class IngredienteSugeridoIA(val nome: String, val quantidade: Double, val medida: String)

@Serializable
data class ItemCulinarizado(
    val ingrediente_id: String,
    val quantidade_estimada: Double,
    val medida_base: String
)

@Serializable
data class TraducaoResponse(val itens: List<ItemCulinarizado>)

class GeminiReceitaAnalyzer {
    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = BuildConfig.CLOUD_API_KEY,
        generationConfig = generationConfig {
            temperature = 0.2f
            responseMimeType = "application/json"
        }
    )

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    suspend fun normalizarQuantidades(listaItens: List<ListaComprasItem>): Result<List<ItemOtimizado>> {
        if (listaItens.isEmpty()) return Result.success(emptyList())

        return withContext(Dispatchers.IO) {
            try {
                val listaParaIA = listaItens.joinToString("\n") {
                    "- ${it.quantidade} ${it.medida} de ${it.ingredientes?.nome}"
                }

                val prompt = """
                    Atua como um chef de cozinha portuguesa experiente e gestor de compras.
                    
                    **REGRA DE OURO PARA NOMES:**
                    - No campo 'nome_original', deves colocar APENAS o nome do ingrediente (ex: "cebola", "arroz carolino"), ignorando as quantidades e medidas que te foram enviadas.
    
                    **FASE 1: CONVERSÃO CULINÁRIA (Tradução de medidas):**
                    Antes de sugerires o pacote, converte as medidas abstratas seguindo estas equivalências:
                    1. **Chávenas para Gramas:** Arroz/Grão/Farinha: 1 chávena ≈ 200g. Açúcar: 1 chávena ≈ 250g.
                    2. **Colheres para Gramas/ML:** 1 colher de sopa ≈ 15g ou 15ml. 1 colher de chá ≈ 5g ou 5ml.
                    3. **Líquidos:** Converte sempre para litros (L) ou mililitros (ml) antes de escolher o pacote.
                
                    **FASE 2: REGRA DE OURO (SKU E QUANTIDADE COMERCIAL):**
                    1. **Inteiros Apenas:** O campo 'quantidade' deve ser SEMPRE um número inteiro (1.0, 2.0). Nunca uses frações.
                    2. **Arredondamento por Excesso:** Se a receita pede 1.5 chávenas de arroz (300g) e o pacote comercial é de 0.5kg, a quantidade é 1.0 pacote.
                    3. **Hortícolas:** Para Batata/Cebola, usa "rede de 1kg" ou "saco de 2kg". Para Alho, usa "cabeça".
                    
                    **REGRAS PARA ERVAS E TEMPEROS:**
                    - Frescos: Usa 'ramada'. Secos/Frasco: Usa 'frasco'. 'qb' mantém-se 'qb'.
                    
                    **UNIDADES DE VENDA PADRÃO (PORTUGAL):**
                    - Arroz/Massa/Preparados Congelados: Unidades de 0.5kg ou 1kg.
                    - Ovos: 'meia-dúzia' (6 un) ou 'dúzia' (12 un).
                    - Leite/Natas: 'pacote de 0.2L', 'pacote de 0.5L' ou 'pacote de 1L'.
                    
                    **MANTÉM O NOME ORIGINAL:**
                    - O campo 'nome_original' deve ser idêntico ao enviado.
                    
                    **LISTA:**
                    $listaParaIA
                    
                    **FORMATO DE SAÍDA (JSON):**
                    {
                      "itens": [
                        { "nome_original": "nome exato enviado", "quantidade": 1.0, "medida": "medida comercial", "nota_ia": "razão" }
                      ]
                    }
                """.trimIndent()

                val response = generativeModel.generateContent(prompt)
                val cleanJson = response.text?.trim()?.removePrefix("```json")?.removeSuffix("```")?.trim() ?: throw Exception("IA vazia")

                Log.d("GeminiAnalyzer", "JSON Recebido: $cleanJson")
                val decoded = json.decodeFromString<NormalizacaoResponse>(cleanJson)
                Result.success(decoded.itens)

            } catch (e: Exception) {
                Log.e("GeminiAnalyzer", "Erro na normalização comercial: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun gerarSugestaoCriativa(itensCarrinho: List<ListaComprasItem>): Result<SugestaoIA> {
        return withContext(Dispatchers.IO) {
            try {
                val baseIngredientes = itensCarrinho.joinToString(", ") {
                    "${it.quantidade} ${it.medida} de ${it.ingredientes?.nome}"
                }

                val prompt = """
                    Atua como um chef de cozinha portuguesa. Com base nestes ingredientes: $baseIngredientes.
                    Cria uma receita inédita e deliciosa.
                    
                    REGRAS:
                    1. Dificuldade: apenas "baixa", "média" ou "alta".
                    2. Categoria: apenas "carne", "peixe", "vegetariana", "sobremesas" ou "outro".
                    3. Preparação: Texto detalhado com passos numerados.
                    
                    FORMATO JSON:
                    {
                      "nome": "Nome da Receita",
                      "tempo": 30,
                      "porcoes": 2,
                      "dificuldade": "média",
                      "categoria": "outro",
                      "preparacao": "1. Passo um...",
                      "ingredientes": [
                        { "nome": "nome do ingrediente", "quantidade": 1.0, "medida": "un" }
                      ]
                    }
                """.trimIndent()

                val response = generativeModel.generateContent(prompt)
                val cleanJson = response.text?.trim()?.removePrefix("```json")?.removeSuffix("```")?.trim() ?: ""
                val sugestao = json.decodeFromString<SugestaoIA>(cleanJson)
                Result.success(sugestao)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun traduzirListaParaCulinar(listaAtual: List<ListaComprasItem>): Result<List<ItemCulinarizado>> {
        if (listaAtual.isEmpty()) return Result.success(emptyList())
        return withContext(Dispatchers.IO) {
            try {
                val listaParaIA = listaAtual.joinToString("\n") {
                    "- ${it.quantidade} ${it.medida} de ${it.ingredientes?.nome} (ID: ${it.ingredienteId})"
                }

                val prompt = """
                    Atua como um Chef de Cozinha especialista em pesos e medidas.
                    Converte os itens comerciais da lista de compras para medidas culinárias exatas.
                    
                    REGRAS DE CONVERSÃO (DEVES ESTIMAR):
                    1. Arroz/Grão/Massa: 1 pacote de 0.5kg = 500.0 g (aprox. 2.5 chávenas).
                    2. Líquidos (Azeite/Leite): 1 garrafa de 0.75L = 750.0 ml (aprox. 50 colheres de sopa).
                    3. Vegetais: 1 rede de 1kg de cebola = 10.0 un.
                    4. Temperos/qb: Define sempre como 999.0 e medida "qb".
                    
                    IMPORTANTE: Para cada item, escolhe a medida mais provável que uma receita usaria (g, ml, un, chávena ou colher).
                    
                    LISTA:
                    $listaParaIA
                    
                    RETORNA APENAS JSON:
                    { "itens": [ { "ingrediente_id": "ID", "quantidade_estimada": 2.5, "medida_base": "chávena" } ] }
                """.trimIndent()

                val response = generativeModel.generateContent(prompt)
                val cleanJson = response.text?.trim()?.removePrefix("```json")?.removeSuffix("```")?.trim() ?: ""

                Log.d("IA_TRADUCAO", "JSON Recebido da IA: $cleanJson")
                val decoded = json.decodeFromString<TraducaoResponse>(cleanJson)
                Result.success(decoded.itens)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}