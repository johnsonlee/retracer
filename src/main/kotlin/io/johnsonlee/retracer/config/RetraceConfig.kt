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
        val maxCacheSize: Int
    )

    @Bean
    fun getDiagnosticsHandler(): DiagnosticsHandler = object : DiagnosticsHandler {}

    @Bean
    fun getOptions(
        @Value("${'$'}{retracer.dataDir:/data}") dataDir: String,
        @Value("${'$'}{retracer.minCacheSize:5}") minCacheSize: Int,
        @Value("${'$'}{retracer.maxCacheSize:20}") maxCacheSize: Int
    ): Options = Options(
        dataDir = File(dataDir),
        minCacheSize = minCacheSize,
        maxCacheSize = maxCacheSize
    )

}