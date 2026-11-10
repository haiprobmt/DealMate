package com.dealmate.app.data

import com.dealmate.app.data.remote.dto.AssistantResponseDto
import com.dealmate.app.data.remote.dto.DealMateJson
import com.dealmate.app.data.remote.dto.HistoryResponseDto
import com.dealmate.app.data.remote.dto.NotificationListDto
import com.dealmate.app.data.remote.dto.PriceAlertListDto
import com.dealmate.app.data.remote.dto.ProductDetailDto
import com.dealmate.app.data.remote.dto.ProductOffersResponseDto
import com.dealmate.app.data.remote.dto.SearchResponseDto
import com.dealmate.app.data.remote.dto.SourcesStatusResponseDto
import com.dealmate.app.data.remote.dto.TrendingResponseDto
import com.dealmate.app.data.remote.dto.WishlistListDto
import kotlinx.serialization.DeserializationStrategy
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Parses every real backend response dropped into `src/test/resources/contract/`. The filename prefix
 * selects the response type. Passes trivially while the directory holds no JSON files.
 */
class ContractFixturesTest {

    /** prefix -> (deserializer, mapping to the domain model so mapper crashes are caught too). */
    private val parsers: Map<String, Pair<DeserializationStrategy<*>, (Any) -> Any>> = mapOf(
        "search" to (SearchResponseDto.serializer() to { it: Any -> (it as SearchResponseDto).toDomain() }),
        "offers" to (ProductOffersResponseDto.serializer() to { it: Any -> (it as ProductOffersResponseDto).toDomain() }),
        "wishlist" to (WishlistListDto.serializer() to { it: Any -> (it as WishlistListDto).items.map { i -> i.toDomain() } }),
        "history" to (HistoryResponseDto.serializer() to { it: Any -> (it as HistoryResponseDto).toDomain() }),
        "sources" to (SourcesStatusResponseDto.serializer() to { it: Any -> (it as SourcesStatusResponseDto).toDomain() }),
        "explore" to (TrendingResponseDto.serializer() to { it: Any -> (it as TrendingResponseDto).toDomain() }),
        "assistant" to (AssistantResponseDto.serializer() to { it: Any -> (it as AssistantResponseDto).toDomain() }),
        "notifications" to (NotificationListDto.serializer() to { it: Any -> (it as NotificationListDto).items.map { i -> i.toDomain() } }),
        "alerts" to (PriceAlertListDto.serializer() to { it: Any -> (it as PriceAlertListDto).items.map { i -> i.toDomain() } }),
        "product" to (ProductDetailDto.serializer() to { it: Any -> (it as ProductDetailDto).toDomain() }),
    )

    private fun contractFiles(): List<File> {
        val dir = javaClass.classLoader?.getResource("contract")?.let { File(it.toURI()) } ?: return emptyList()
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.sortedBy { it.name }.orEmpty()
    }

    @Test
    fun `every contract file parses according to its filename prefix`() {
        val failures = mutableListOf<String>()
        for (file in contractFiles()) {
            val prefix = file.name.substringBefore('_').substringBefore('.')
            val parser = parsers[prefix]
            if (parser == null) {
                failures += "${file.name}: unknown prefix '$prefix' (expected one of ${parsers.keys.sorted()})"
                continue
            }
            runCatching {
                val dto = DealMateJson.decodeFromString(parser.first, file.readText())
                parser.second(requireNotNull(dto))
            }.onFailure { failures += "${file.name}: ${it::class.simpleName}: ${it.message}" }
        }
        if (failures.isNotEmpty()) fail("Contract files that do not parse:\n" + failures.joinToString("\n"))
    }

    @Test
    fun `the app's own fixtures pass the same prefix-based check`() {
        // Guards the mechanism itself: the hand-written fixtures use the same naming scheme.
        val dir = File(requireNotNull(javaClass.classLoader?.getResource("fixtures")).toURI())
        val checked = dir.listFiles()!!.filter { it.name.substringBefore('_') in parsers.keys && it.name.endsWith(".json") }
        assertTrue(checked.size >= 10)
        checked.forEach { file ->
            val parser = parsers.getValue(file.name.substringBefore('_'))
            parser.second(requireNotNull(DealMateJson.decodeFromString(parser.first, file.readText())))
        }
    }
}
