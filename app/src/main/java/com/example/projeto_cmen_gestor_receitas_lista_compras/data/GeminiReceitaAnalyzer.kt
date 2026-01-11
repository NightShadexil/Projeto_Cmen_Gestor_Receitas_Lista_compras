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
    
                    **REGRA DE OURO (SKU E QUANTIDADE):**
                    1. **Inteiros Apenas:** O campo 'quantidade' deve ser SEMPRE um número inteiro (1.0, 2.0, 3.0). Nunca uses frações como 0.5 ou 1.5.
                    2. **Arredondamento por Excesso:** Se a receita precisa de 300g e o pacote é de 500g, a quantidade é 1.0. Se precisa de 600g e o pacote é de 500g, a quantidade é 2.0.
                    
                    **REGRAS PARA HORTÍCOLAS (Batata, Cebola, Alho):**
                    - Nunca sugiras "unidade" isolada para estes itens. Usa "rede de 1kg", "saco de 2kg" ou "cabeça" (alho).
                    
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

                val cleanJson = response.text?.trim()?.removePrefix("```json")?.removeSuffix("```")?.trim()
                    ?: throw Exception("IA vazia")

                Log.d("GeminiAnalyzer", "JSON Recebido: $cleanJson")

                val decoded = json.decodeFromString<NormalizacaoResponse>(cleanJson)

                if (decoded.itens.size != listaItens.size) {
                    throw Exception("IA alterou o número de itens na lista")
                }

                Result.success(decoded.itens)

            } catch (e: Exception) {
                Log.e("GeminiAnalyzer", "Erro na normalização comercial: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun sugerirReceitaCriativa(ingredientesDisponiveis: List<String>): String {
        return "Lógica para sugerir nova receita baseada em ${ingredientesDisponiveis.joinToString()}"
    }
}