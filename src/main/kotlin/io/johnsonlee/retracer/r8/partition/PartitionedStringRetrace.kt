package io.johnsonlee.retracer.r8.partition

import com.android.tools.r8.DiagnosticsHandler
import com.android.tools.r8.retrace.ProguardMapProducer
import com.android.tools.r8.retrace.RetraceOptions
import com.android.tools.r8.retrace.StringRetrace
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.File
import java.util.regex.Pattern

/**
 * A StringRetrace implementation that uses partitioned class loading for memory efficiency.
 *
 * Instead of loading the entire mapping file upfront, this implementation:
 * 1. Parses the stack trace to identify referenced class names
 * 2. Loads only those class mappings from the partitioned file
 * 3. Creates a minimal ClassNameMapper for the retrace operation
 * 4. Delegates to R8's StringRetrace for the actual retracing
 *
 * This significantly reduces memory usage for large mapping files (100MB+).
 *
 * @property mapper The partitioned class name mapper for lazy loading
 * @property diagnosticsHandler Handler for R8 diagnostics
 */
class PartitionedStringRetrace(
    private val mapper: PartitionedClassNameMapper,
    private val diagnosticsHandler: DiagnosticsHandler
) : Closeable {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    /**
     * Retrace a list of stack trace lines.
     *
     * @param stackTrace List of stack trace lines
     * @return List of retraced lines (may contain multiple lines per input for inlined frames)
     */
    fun retrace(stackTrace: List<String>): List<String> {
        if (stackTrace.isEmpty()) {
            return emptyList()
        }

        // Extract class names from the stack trace
        val classNames = extractClassNames(stackTrace)
        logger.debug("Extracted {} unique class names from stack trace", classNames.size)

        // Create a minimal mapping with only the needed classes
        val classNameMapper = mapper.createMapperForClasses(classNames)

        // Create R8's StringRetrace with the minimal mapping
        val options = RetraceOptions.builder(diagnosticsHandler)
            .setProguardMapProducer(ProguardMapProducer.fromString(classNameMapper.toString()))
            .build()

        val stringRetrace = StringRetrace.create(options)
        return stringRetrace.retrace(stackTrace)
    }

    /**
     * Retrace a single stack trace line.
     *
     * @param stackTraceLine A single stack trace line
     * @return List of retraced lines (may contain multiple lines for inlined frames)
     */
    fun retrace(stackTraceLine: String): List<String> {
        return retrace(listOf(stackTraceLine))
    }

    /**
     * Extract all class names mentioned in the stack trace.
     *
     * Looks for patterns like:
     * - "at a.b.c.method(File.java:123)" - class is "a.b.c"
     * - "Caused by: a.b.c: message" - class is "a.b.c"
     * - "a.b.c" in method return types/parameters
     */
    private fun extractClassNames(stackTrace: List<String>): Set<String> {
        val classNames = mutableSetOf<String>()

        for (line in stackTrace) {
            // Pattern 1: "at com.example.Class.method(File.java:123)"
            val atMatch = AT_PATTERN.matcher(line)
            while (atMatch.find()) {
                val fullMethod = atMatch.group(1)
                val className = extractClassFromMethod(fullMethod)
                if (className != null) {
                    addClassAndOuters(className, classNames)
                }
            }

            // Pattern 2: "Caused by: com.example.Exception: message"
            val causedByMatch = CAUSED_BY_PATTERN.matcher(line)
            if (causedByMatch.find()) {
                val exceptionClass = causedByMatch.group(1)
                addClassAndOuters(exceptionClass, classNames)
            }

            // Pattern 3: Exception at start of stack trace "com.example.Exception: message"
            val exceptionMatch = EXCEPTION_PATTERN.matcher(line)
            if (exceptionMatch.find()) {
                val exceptionClass = exceptionMatch.group(1)
                addClassAndOuters(exceptionClass, classNames)
            }

            // Pattern 4: Generic class names (a.b.c pattern) for catching edge cases
            val genericMatch = CLASS_NAME_PATTERN.matcher(line)
            while (genericMatch.find()) {
                val potentialClass = genericMatch.group()
                // Only add if it looks like a class name (has at least one dot, not too long)
                if (potentialClass.contains('.') && potentialClass.length < 200) {
                    addClassAndOuters(potentialClass, classNames)
                }
            }
        }

        // Filter to only include classes that are actually in our mapping
        return classNames.filter { it in mapper }.toSet()
    }

    /**
     * Extract the class name from a full method reference like "com.example.Class.method"
     */
    private fun extractClassFromMethod(fullMethod: String): String? {
        val lastDot = fullMethod.lastIndexOf('.')
        return if (lastDot > 0) {
            fullMethod.substring(0, lastDot)
        } else {
            null
        }
    }

    /**
     * Add a class name and all its potential outer classes to the set.
     * This handles inner classes like "a.b.Outer$Inner" -> adds both "a.b.Outer$Inner" and "a.b.Outer"
     */
    private fun addClassAndOuters(className: String, classNames: MutableSet<String>) {
        classNames.add(className)

        // Add potential outer classes
        var outerClass = className
        while (true) {
            val lastDollar = outerClass.lastIndexOf('$')
            if (lastDollar <= 0) break
            outerClass = outerClass.substring(0, lastDollar)
            classNames.add(outerClass)
        }
    }

    override fun close() {
        mapper.close()
    }

    companion object {
        // Pattern to match "at com.example.Class.method(File.java:123)"
        private val AT_PATTERN: Pattern = Pattern.compile("""at\s+([a-zA-Z0-9_.$]+)\s*\(""")

        // Pattern to match "Caused by: com.example.Exception: message"
        private val CAUSED_BY_PATTERN: Pattern = Pattern.compile("""Caused by:\s+([a-zA-Z0-9_.$]+)""")

        // Pattern to match exception class at start "com.example.Exception: message"
        private val EXCEPTION_PATTERN: Pattern = Pattern.compile("""^([a-zA-Z0-9_.$]+(?:Exception|Error|Throwable)):\s""")

        // Generic pattern for class names (conservative)
        private val CLASS_NAME_PATTERN: Pattern = Pattern.compile("""[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_$]*)+""", Pattern.CASE_INSENSITIVE)

        /**
         * Create a PartitionedStringRetrace from a mapping file.
         *
         * @param mappingFile The R8/ProGuard mapping file
         * @param diagnosticsHandler Handler for R8 diagnostics
         * @param maxCachedClasses Maximum number of class mappings to cache
         * @return A new PartitionedStringRetrace instance
         */
        fun create(
            mappingFile: File,
            diagnosticsHandler: DiagnosticsHandler,
            maxCachedClasses: Int = PartitionedClassNameMapper.DEFAULT_MAX_CACHED_CLASSES
        ): PartitionedStringRetrace {
            val mapper = PartitionedClassNameMapper.create(mappingFile, maxCachedClasses)
            return PartitionedStringRetrace(mapper, diagnosticsHandler)
        }
    }
}
