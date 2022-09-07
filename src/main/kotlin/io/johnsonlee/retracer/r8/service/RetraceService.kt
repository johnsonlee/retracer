package io.johnsonlee.retracer.r8.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import io.johnsonlee.android.trace.JavaStackFrame
import io.johnsonlee.android.trace.NativeStackFrame
import io.johnsonlee.android.trace.identifyRootCause
import io.johnsonlee.android.trace.md5
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class RetraceService(
    @Autowired private val retraceProvider: RetraceProvider,
) {

    fun retrace(appId: String, appVersionName: String, appVersionCode: Long, body: ObjectNode): JsonNode = body.apply {
        val data = body.get(KEY_DATA) as? ObjectNode ?: return@apply
        val stackTrace = data.get(KEY_DATA_STACK_TRACE)?.asText()?.split("\n") ?: return@apply
        val retraced = retraceProvider[appId, appVersionName, appVersionCode].retrace(stackTrace).joinToString("\n")
        val lines = retraced.split('\n')
        val rootCause = identifyRootCause(lines)

        rootCause?.let {
            data.put(KEY_DATA_ROOT_CAUSE, it.toString())
        }
        data.put(KEY_DATA_FINGERPRINT, rootCause?.fingerprint ?: retraced.md5())
        data.put(KEY_DATA_STACK_TRACE, retraced)

        when (rootCause) {
            is JavaStackFrame -> {
                data.put(KEY_DATA_SCOPE, "java")
                data.put(KEY_DATA_FILE_NAME, rootCause.sourceFile)
                data.put(KEY_DATA_CLASS_NAME, rootCause.className)
                data.put(KEY_DATA_METHOD_NAME, rootCause.methodName)
                data.put(KEY_DATA_FILE_LINE, rootCause.lineNumber.toString())
            }
            is NativeStackFrame -> {
                data.put(KEY_DATA_SCOPE, "native")
                data.put(KEY_DATA_FILE_NAME, rootCause.mapName)
                data.put(KEY_DATA_METHOD_NAME, rootCause.functionName)
                data.put(KEY_DATA_FILE_LINE, rootCause.functionOffset.toString())
            }
            else -> {
                data.put(KEY_DATA_SCOPE, "unknown")
            }
        }
    }

}

private const val KEY_DATA = "data"
private const val KEY_DATA_FINGERPRINT = "fingerprint"
private const val KEY_DATA_FILE_NAME = "fileName"
private const val KEY_DATA_FILE_LINE = "fileLine"
private const val KEY_DATA_CLASS_NAME = "className"
private const val KEY_DATA_METHOD_NAME = "methodName"
private const val KEY_DATA_ROOT_CAUSE = "rootCause"
private const val KEY_DATA_SCOPE = "scope"
private const val KEY_DATA_STACK_TRACE = "stackTrace"
