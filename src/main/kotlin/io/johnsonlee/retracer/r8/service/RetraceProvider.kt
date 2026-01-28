package io.johnsonlee.retracer.r8.service

import com.android.tools.r8.DiagnosticsHandler
import com.android.tools.r8.retrace.RetraceCommand
import com.android.tools.r8.retrace.StringRetrace
import io.johnsonlee.retracer.MAPPING_TXT
import io.johnsonlee.retracer.config.RetraceConfig
import io.johnsonlee.retracer.r8.partition.PartitionedStringRetrace
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.util.ConcurrentLruCache
import java.io.Closeable
import java.io.File

/**
 * Functional interface for retracing stack traces.
 * This allows both StringRetrace and PartitionedStringRetrace to be used interchangeably.
 */
fun interface RetraceFunction {
    fun retrace(stackTrace: List<String>): List<String>
}

/**
 * Wrapper for holding a retrace function along with optional closeable resource.
 */
private class RetraceHolder(
    val retraceFunction: RetraceFunction,
    private val closeable: Closeable? = null
) : RetraceFunction, Closeable {
    override fun retrace(stackTrace: List<String>): List<String> = retraceFunction.retrace(stackTrace)
    override fun close() {
        closeable?.close()
    }
}

@Service
class RetraceProvider(
    @Autowired private val diagnosticsHandler: DiagnosticsHandler,
    @Autowired private val proguardService: ProguardService,
    @Autowired private val options: RetraceConfig.Options
) {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    private val loader: RetraceLoader by lazy {
        RetraceLoader(diagnosticsHandler, proguardService, options)
    }

    private val cache = ConcurrentLruCache(options.maxCacheSize, loader)

    val caches: List<String>
        get() = loadCache().filter(cache::contains).map(CacheKey::toString)

    operator fun get(appId: String, appVersionName: String, appVersionCode: Long): RetraceFunction {
        val key = CacheKey(appId, appVersionName, appVersionCode)
        return cache.get(key)
    }

    fun refresh(appId: String, appVersionName: String, appVersionCode: Long) {
        val key = CacheKey(appId, appVersionName, appVersionCode)
        return cache.run {
            remove(key)
            get(key)
        }
    }

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationEvent(event: ApplicationReadyEvent) {
        loadCache().groupBy(CacheKey::appId).map { (_, mappings) ->
            mappings.sortedWith(Comparator { l, r ->
                compareValuesBy(r, l, { it.appVersionCode }, { it.appVersionName })
            }).take(options.minCacheSize)
        }.flatten().forEach { key ->
            get(key.appId, key.appVersionName, key.appVersionCode)
        }
    }

    private fun loadCache(): List<CacheKey> = runBlocking {
        options.dataDir.walkTopDown().asFlow().filter {
            MAPPING_TXT == it.name
        }.mapNotNull {
            val appId = it.parentFile?.parentFile?.parentFile?.name ?: return@mapNotNull null
            val appVersionName = it.parentFile?.parentFile?.name ?: return@mapNotNull null
            val appVersionCode = it.parentFile?.name?.toLongOrNull() ?: return@mapNotNull null
            CacheKey(appId, appVersionName, appVersionCode)
        }.toList()
    }

}

private data class CacheKey(
    val appId: String,
    val appVersionName: String,
    val appVersionCode: Long
) {
    override fun toString() = "$appId/$appVersionName/$appVersionCode"
}

private val CacheKey.mappingPath
    get() = "$appId/$appVersionName/$appVersionCode/$MAPPING_TXT"

private data class RetraceLoader(
    val diagnosticsHandler: DiagnosticsHandler,
    val proguardService: ProguardService,
    val options: RetraceConfig.Options
) : java.util.function.Function<CacheKey, RetraceHolder> {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    override fun apply(key: CacheKey): RetraceHolder {
        val mappingFile = proguardService.getMappingFile(key.appId, key.appVersionName, key.appVersionCode)

        return if (shouldUsePartitionedMode(mappingFile)) {
            createPartitionedRetrace(key, mappingFile)
        } else {
            createStandardRetrace(key)
        }
    }

    /**
     * Determine whether to use partitioned mode based on file size and configuration.
     */
    private fun shouldUsePartitionedMode(mappingFile: File): Boolean {
        if (!options.usePartitionedMapping) {
            return false
        }
        if (!mappingFile.exists()) {
            return false
        }
        val thresholdBytes = options.partitionedThresholdMb.toLong() * 1024 * 1024
        return mappingFile.length() > thresholdBytes
    }

    /**
     * Create a standard StringRetrace that loads the entire mapping file.
     */
    private fun createStandardRetrace(key: CacheKey): RetraceHolder {
        val producer = proguardService.getProguardMapProducer(key.appId, key.appVersionName, key.appVersionCode)
        val cmd = RetraceCommand.builder(diagnosticsHandler)
            .setStackTrace(emptyList())
            .setProguardMapProducer(producer)
            .setRetracedStackTraceConsumer {
                it.joinToString("\n")
            }.build()
        logger.info("Caching ${key.mappingPath} (standard mode) ...")
        val stringRetrace = StringRetrace.create(cmd.options).apply {
            retrace(listOf(""))
        }
        return RetraceHolder(
            retraceFunction = { stackTrace -> stringRetrace.retrace(stackTrace) }
        )
    }

    /**
     * Create a partitioned StringRetrace that loads class mappings on-demand.
     */
    private fun createPartitionedRetrace(key: CacheKey, mappingFile: File): RetraceHolder {
        logger.info("Caching ${key.mappingPath} (partitioned mode, ${mappingFile.length() / 1024 / 1024}MB) ...")
        val partitionedRetrace = PartitionedStringRetrace.create(
            mappingFile = mappingFile,
            diagnosticsHandler = diagnosticsHandler,
            maxCachedClasses = options.maxCachedClassesPerMapping
        )
        return RetraceHolder(
            retraceFunction = { stackTrace -> partitionedRetrace.retrace(stackTrace) },
            closeable = partitionedRetrace
        )
    }
}
