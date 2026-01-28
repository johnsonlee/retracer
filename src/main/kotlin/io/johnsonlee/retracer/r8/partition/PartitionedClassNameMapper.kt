package io.johnsonlee.retracer.r8.partition

import com.android.tools.r8.naming.ClassNameMapper
import com.android.tools.r8.naming.ClassNamingForNameMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.util.ConcurrentLruCache
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.function.Function

/**
 * A class name mapper that loads class mappings on-demand from a partitioned mapping file.
 *
 * Uses an index to locate class mappings within the file and a RandomAccessFile to read
 * only the needed portions. Loaded mappings are cached in an LRU cache.
 *
 * @property mappingFile The R8/ProGuard mapping file
 * @property index The pre-built index for the mapping file
 * @property maxCachedClasses Maximum number of class mappings to cache
 */
class PartitionedClassNameMapper(
    private val mappingFile: File,
    private val index: MappingIndex,
    private val maxCachedClasses: Int = DEFAULT_MAX_CACHED_CLASSES
) : Closeable {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    private val raf: RandomAccessFile = RandomAccessFile(mappingFile, "r")

    private val cache = ConcurrentLruCache<String, ClassNamingForNameMapper>(
        maxCachedClasses,
        ClassMappingLoader()
    )

    /**
     * Get the ClassNamingForNameMapper for an obfuscated class name.
     * Returns null if the class is not in the mapping.
     */
    fun getClassNaming(obfuscatedClassName: String): ClassNamingForNameMapper? {
        if (obfuscatedClassName !in index) {
            return null
        }
        return cache.get(obfuscatedClassName)
    }

    /**
     * Check if the mapping contains the given obfuscated class name.
     */
    operator fun contains(obfuscatedClassName: String): Boolean = obfuscatedClassName in index

    /**
     * Get all obfuscated class names in the mapping.
     */
    val obfuscatedClassNames: Set<String> get() = index.obfuscatedClassNames

    /**
     * Get the original class name for an obfuscated name.
     * Returns the obfuscated name if not found in mapping.
     */
    fun deobfuscateClassName(obfuscatedClassName: String): String {
        val entry = index[obfuscatedClassName]
        return entry?.originalName ?: obfuscatedClassName
    }

    /**
     * Create a ClassNameMapper containing only the specified classes.
     * This is useful for creating a minimal mapper for a specific retrace operation.
     */
    fun createMapperForClasses(obfuscatedClassNames: Set<String>): ClassNameMapper {
        val mappingContent = StringBuilder()
        for (className in obfuscatedClassNames) {
            val entry = index[className] ?: continue
            val classMapping = readClassMapping(entry)
            mappingContent.append(classMapping)
        }
        return if (mappingContent.isEmpty()) {
            ClassNameMapper.mapperFromString("")
        } else {
            ClassNameMapper.mapperFromString(mappingContent.toString())
        }
    }

    /**
     * Read the raw mapping data for a class from the file.
     */
    @Synchronized
    fun readClassMapping(entry: MappingIndexEntry): String {
        val bytes = ByteArray(entry.byteLength)
        raf.seek(entry.byteOffset)
        raf.readFully(bytes)
        return String(bytes, StandardCharsets.UTF_8)
    }

    override fun close() {
        try {
            raf.close()
        } catch (e: Exception) {
            logger.warn("Error closing RandomAccessFile", e)
        }
    }

    private inner class ClassMappingLoader : Function<String, ClassNamingForNameMapper> {
        override fun apply(obfuscatedClassName: String): ClassNamingForNameMapper {
            val entry = index[obfuscatedClassName]
                ?: throw IllegalArgumentException("Class not in index: $obfuscatedClassName")

            val mappingContent = readClassMapping(entry)
            val mapper = ClassNameMapper.mapperFromString(mappingContent)
            val classNaming = mapper.getClassNaming(obfuscatedClassName)
                ?: throw IllegalStateException("Failed to parse mapping for: $obfuscatedClassName")

            logger.debug("Loaded class mapping for {} ({} bytes)", obfuscatedClassName, entry.byteLength)
            return classNaming
        }
    }

    companion object {
        const val DEFAULT_MAX_CACHED_CLASSES = 1000

        /**
         * Create a PartitionedClassNameMapper for a mapping file.
         * Builds or loads the index as needed.
         */
        fun create(
            mappingFile: File,
            maxCachedClasses: Int = DEFAULT_MAX_CACHED_CLASSES,
            forceRebuildIndex: Boolean = false
        ): PartitionedClassNameMapper {
            val indexBuilder = MappingIndexBuilder()
            val index = indexBuilder.build(mappingFile, forceRebuildIndex)
            return PartitionedClassNameMapper(mappingFile, index, maxCachedClasses)
        }
    }
}
