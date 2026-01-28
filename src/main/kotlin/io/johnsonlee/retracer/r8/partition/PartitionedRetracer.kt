package io.johnsonlee.retracer.r8.partition

import com.android.tools.r8.DiagnosticsHandler
import com.android.tools.r8.references.ClassReference
import com.android.tools.r8.references.FieldReference
import com.android.tools.r8.references.MethodReference
import com.android.tools.r8.references.TypeReference
import com.android.tools.r8.retrace.Retracer
import com.android.tools.r8.retrace.RetraceClassResult
import com.android.tools.r8.retrace.RetraceFieldResult
import com.android.tools.r8.retrace.RetraceFrameResult
import com.android.tools.r8.retrace.RetraceMethodResult
import com.android.tools.r8.retrace.RetraceTypeResult
import com.android.tools.r8.retrace.internal.RetracerImpl
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Closeable

/**
 * A Retracer implementation that uses partitioned class loading for memory efficiency.
 *
 * Instead of loading the entire mapping file, this retracer loads class mappings
 * on-demand using the PartitionedClassNameMapper. Each lookup creates a minimal
 * ClassNameMapper containing only the needed classes and delegates to R8's RetracerImpl.
 *
 * @property mapper The partitioned class name mapper for lazy loading
 * @property diagnosticsHandler Handler for R8 diagnostics
 */
class PartitionedRetracer(
    private val mapper: PartitionedClassNameMapper,
    private val diagnosticsHandler: DiagnosticsHandler
) : Retracer, Closeable {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    /**
     * Create a RetracerImpl with only the classes needed for a specific class reference.
     */
    private fun createRetracerForClass(classReference: ClassReference): Retracer {
        val obfuscatedClassName = classReference.binaryName.replace('/', '.')
        val classesNeeded = collectClassesForLookup(obfuscatedClassName)
        val minimalMapper = mapper.createMapperForClasses(classesNeeded)
        return RetracerImpl(minimalMapper)
    }

    /**
     * Collect all class names that might be needed for a lookup.
     * This includes the target class and any nested/inner class patterns.
     */
    private fun collectClassesForLookup(obfuscatedClassName: String): Set<String> {
        val classes = mutableSetOf<String>()

        // Add the main class
        if (obfuscatedClassName in mapper) {
            classes.add(obfuscatedClassName)
        }

        // Add potential outer classes (for inner class lookups)
        var outerClass = obfuscatedClassName
        while (true) {
            val lastDollar = outerClass.lastIndexOf('$')
            if (lastDollar <= 0) break
            outerClass = outerClass.substring(0, lastDollar)
            if (outerClass in mapper) {
                classes.add(outerClass)
            }
        }

        return classes
    }

    /**
     * Collect all class names that might be needed for a method reference lookup.
     */
    private fun collectClassesForMethod(methodReference: MethodReference): Set<String> {
        val classes = mutableSetOf<String>()

        // Add the holder class
        val holderClass = methodReference.holderClass.binaryName.replace('/', '.')
        classes.addAll(collectClassesForLookup(holderClass))

        // Add return type class if it's a class type
        methodReference.returnType?.let { returnType ->
            if (returnType.isClass) {
                val returnClass = returnType.asClass().binaryName.replace('/', '.')
                classes.addAll(collectClassesForLookup(returnClass))
            }
        }

        // Add parameter type classes
        methodReference.formalTypes?.forEach { paramType ->
            if (paramType.isClass) {
                val paramClass = paramType.asClass().binaryName.replace('/', '.')
                classes.addAll(collectClassesForLookup(paramClass))
            }
        }

        return classes
    }

    /**
     * Collect all class names that might be needed for a field reference lookup.
     */
    private fun collectClassesForField(fieldReference: FieldReference): Set<String> {
        val classes = mutableSetOf<String>()

        // Add the holder class
        val holderClass = fieldReference.holderClass.binaryName.replace('/', '.')
        classes.addAll(collectClassesForLookup(holderClass))

        // Add field type class if it's a class type
        if (fieldReference.fieldType.isClass) {
            val fieldTypeClass = fieldReference.fieldType.asClass().binaryName.replace('/', '.')
            classes.addAll(collectClassesForLookup(fieldTypeClass))
        }

        return classes
    }

    override fun retraceClass(classReference: ClassReference): RetraceClassResult {
        val retracer = createRetracerForClass(classReference)
        return retracer.retraceClass(classReference)
    }

    override fun retraceMethod(methodReference: MethodReference): RetraceMethodResult {
        val classesNeeded = collectClassesForMethod(methodReference)
        val minimalMapper = mapper.createMapperForClasses(classesNeeded)
        val retracer = RetracerImpl(minimalMapper)
        return retracer.retraceMethod(methodReference)
    }

    override fun retraceFrame(methodReference: MethodReference, lineNumber: Int): RetraceFrameResult {
        val classesNeeded = collectClassesForMethod(methodReference)
        val minimalMapper = mapper.createMapperForClasses(classesNeeded)
        val retracer = RetracerImpl(minimalMapper)
        return retracer.retraceFrame(methodReference, lineNumber)
    }

    override fun retraceField(fieldReference: FieldReference): RetraceFieldResult {
        val classesNeeded = collectClassesForField(fieldReference)
        val minimalMapper = mapper.createMapperForClasses(classesNeeded)
        val retracer = RetracerImpl(minimalMapper)
        return retracer.retraceField(fieldReference)
    }

    override fun retraceType(typeReference: TypeReference): RetraceTypeResult {
        if (typeReference.isClass) {
            val retracer = createRetracerForClass(typeReference.asClass())
            return retracer.retraceType(typeReference)
        }
        // For non-class types (primitives, arrays), use an empty mapper
        val emptyMapper = mapper.createMapperForClasses(emptySet())
        val retracer = RetracerImpl(emptyMapper)
        return retracer.retraceType(typeReference)
    }

    override fun close() {
        mapper.close()
    }

    companion object {
        /**
         * Create a PartitionedRetracer from a mapping file.
         */
        fun create(
            mappingFile: java.io.File,
            diagnosticsHandler: DiagnosticsHandler,
            maxCachedClasses: Int = PartitionedClassNameMapper.DEFAULT_MAX_CACHED_CLASSES
        ): PartitionedRetracer {
            val mapper = PartitionedClassNameMapper.create(mappingFile, maxCachedClasses)
            return PartitionedRetracer(mapper, diagnosticsHandler)
        }
    }
}
