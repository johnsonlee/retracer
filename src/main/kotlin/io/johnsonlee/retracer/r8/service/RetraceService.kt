package io.johnsonlee.retracer.r8.service

import io.johnsonlee.android.trace.JavaStackFrame
import io.johnsonlee.android.trace.NativeStackFrame
import io.johnsonlee.android.trace.identifyRootCause
import io.johnsonlee.retracer.r8.dto.StackTraceDTO
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class RetraceService(
        @Autowired private val retraceProvider: RetraceProvider,
) {

    fun retrace(appId: String, appVersionName: String, appVersionCode: Long, dto: StackTraceDTO): StackTraceDTO {
        val retrace = retraceProvider[appId, appVersionName, appVersionCode]
        val stackTrace = retrace.retrace(dto.stackTrace.split('\n')).joinToString("\n")
        val lines = stackTrace.split('\n')
        val rootCause = identifyRootCause(lines)
        val (symbol, scope) = when (rootCause) {
            is JavaStackFrame -> Triple(rootCause.sourceFile, rootCause.lineNumber, rootCause.methodName) to "java"
            is NativeStackFrame -> Triple(rootCause.mapName, rootCause.functionOffset, rootCause.functionName) to "native"
            else -> Triple(null, null, null) to "unknown"
        }
        return StackTraceDTO(
                stackTrace = stackTrace,
                fingerprint = rootCause?.fingerprint ?: dto.fingerprint,
                rootCause = rootCause.toString(),
                fileName = symbol.first,
                fileLine = symbol.second?.toString(),
                functionName = symbol.third,
                scope = scope
        )
    }

}