package io.johnsonlee.retracer.r8.service

import com.android.tools.r8.DiagnosticsHandler
import com.android.tools.r8.retrace.RetraceCommand
import com.android.tools.r8.retrace.StringRetrace
import io.johnsonlee.retracer.r8.dto.StackTraceDTO
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class RetraceService(
        @Autowired private val diagnosticsHandler: DiagnosticsHandler,
        @Autowired private val proguardService: ProguardService
) {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    private val cache: MutableMap<String, StringRetrace> = mutableMapOf()

    fun retrace(appId: String, appVersionName: String, appVersionCode: Long, dto: StackTraceDTO): StackTraceDTO {
        val producer = proguardService.getProguardMapProducer(appId, appVersionName, appVersionCode)
        val cmd = RetraceCommand.builder(diagnosticsHandler)
                .setStackTrace(emptyList())
                .setProguardMapProducer(producer)
                .setRetracedStackTraceConsumer {
                    it.joinToString("\n")
                }.build()

        val retraced = synchronized(cache) {
            cache.getOrPut("${appId}:${appVersionName}:${appVersionCode}") {
                StringRetrace.create(cmd.options)
            }.retrace(dto.stackTrace.split("\n"))
        }

        return StackTraceDTO(
                stackTrace = retraced.joinToString("\n")
        )
    }

}