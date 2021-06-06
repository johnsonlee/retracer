package io.johnsonlee.retracer.config

import com.android.tools.r8.DiagnosticsHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RetraceConfig {

    @Bean
    fun getDiagnosticsHandler(): DiagnosticsHandler = object : DiagnosticsHandler {}

}