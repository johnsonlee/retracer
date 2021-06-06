package io.johnsonlee.retracer.r8.service

import com.android.tools.r8.DiagnosticsHandler
import com.android.tools.r8.retrace.ProguardMapProducer
import com.android.tools.r8.retrace.Retrace
import com.android.tools.r8.retrace.RetraceCommand
import com.android.tools.r8.retrace.StringRetrace
import io.johnsonlee.retracer.MAPPING_TXT
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.io.IOException

@Service
class ProguardService(
        @Value("${'$'}{retracer.dataDir}") private val dataDir: String,
        @Autowired private val diagnosticsHandler: DiagnosticsHandler
) {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    @Synchronized
    fun getProguardMapProducer(appId: String, appVersionName: String, appVersionCode: Long): ProguardMapProducer {
        val dir = File(dataDir, arrayOf(appId, appVersionName, appVersionCode).joinToString(File.separator))
        val mapping = File(dir, MAPPING_TXT)
        return if (!mapping.exists()) {
            logger.error("$mapping doesn't exist")
            ProguardMapProducer.fromString("")
        } else {
            logger.info("Apply mapping cache $mapping")
            Retrace.getMappingSupplier(mapping.canonicalPath, diagnosticsHandler)
        }
    }

    fun saveMapping(appId: String, appVersionName: String, appVersionCode: Long, action: (File) -> Unit): File {
        val dir = File(dataDir, arrayOf(appId, appVersionName, appVersionCode).joinToString(File.separator))
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                logger.error("Failed to mkdirs `$dir`")
            }
        }

        val mapping = File(dir, MAPPING_TXT)
        if (mapping.exists()) {
            logger.warn("$mapping already exists")
            mapping.delete()
        }

        try {
            mapping.createNewFile()
        } catch (e: IOException) {
            logger.error("Create mapping file `${mapping.canonicalPath}` failed", e)
            throw e
        }

        logger.info("Saving mapping file `${mapping.canonicalPath}`")

        try {
            return mapping.also(action).also {
                loadMapping(appId, appVersionName, appVersionCode)
            }
        } catch (e: Throwable) {
            logger.error("Save mapping file `${mapping.canonicalPath}` failed", e)
            throw e
        }

    }

    fun loadMapping(appId: String, appVersionName: String, appVersionCode: Long) {
        val producer = getProguardMapProducer(appId, appVersionName, appVersionCode)
        val cmd = RetraceCommand.builder()
                .setStackTrace(listOf(""))
                .setProguardMapProducer(producer)
                .setRetracedStackTraceConsumer {
                    it.joinToString("\n")
                }.build()
        StringRetrace.create(cmd.options)
    }

}