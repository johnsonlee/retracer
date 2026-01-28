package io.johnsonlee.retracer.config

import com.android.tools.r8.DiagnosticsHandler
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.File

@Configuration
class RetraceConfig {

    data class Options(
        val dataDir: File,
        val minCacheSize: Int,
        val maxCacheSize: Int,
        /**
         * Enable partitioned mapping mode for large mapping files.
         * When enabled, mapping files larger than [partitionedThresholdMb] will
         * be loaded on-demand instead of entirely into memory.
         */
        val usePartitionedMapping: Boolean,
        /**
         * Threshold in megabytes above which to use partitioned mapping mode.
         * Mapping files larger than this size will use lazy loading.
         */
        val partitionedThresholdMb: Int,
        /**
         * Maximum number of class mappings to cache per mapping file in partitioned mode.
         * Higher values use more memory but may improve performance for stack traces
         * that reference many different classes.
         */
        val maxCachedClassesPerMapping: Int
    )

    @Bean
    fun getDiagnosticsHandler(): DiagnosticsHandler = object : DiagnosticsHandler {}

    @Bean
    fun getOptions(
        @Value("${'$'}{retracer.dataDir:/data}") dataDir: String,
        @Value("${'$'}{retracer.minCacheSize:5}") minCacheSize: Int,
        @Value("${'$'}{retracer.maxCacheSize:20}") maxCacheSize: Int,
        @Value("${'$'}{retracer.usePartitionedMapping:true}") usePartitionedMapping: Boolean,
        @Value("${'$'}{retracer.partitionedThresholdMb:50}") partitionedThresholdMb: Int,
        @Value("${'$'}{retracer.maxCachedClassesPerMapping:1000}") maxCachedClassesPerMapping: Int
    ): Options = Options(
        dataDir = File(dataDir),
        minCacheSize = minCacheSize,
        maxCacheSize = maxCacheSize,
        usePartitionedMapping = usePartitionedMapping,
        partitionedThresholdMb = partitionedThresholdMb,
        maxCachedClassesPerMapping = maxCachedClassesPerMapping
    )

}