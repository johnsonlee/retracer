package io.johnsonlee.retracer.r8.partition

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

/**
 * Builds a MappingIndex from an R8/ProGuard mapping file.
 *
 * Scans the mapping file to identify class boundaries and records byte positions
 * for random access. Class boundaries are identified by lines that:
 * - Don't start with whitespace
 * - Contain " -> "
 * - End with ":"
 */
class MappingIndexBuilder {

    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    /**
     * Build an index for the given mapping file.
     *
     * If a valid cached index exists, it will be returned instead of rebuilding.
     *
     * @param mappingFile The R8/ProGuard mapping file
     * @param forceRebuild If true, always rebuild the index even if a cached version exists
     * @return The built MappingIndex
     */
    fun build(mappingFile: File, forceRebuild: Boolean = false): MappingIndex {
        val indexFile = MappingIndex.getIndexFile(mappingFile)

        // Try to load cached index
        if (!forceRebuild && indexFile.exists()) {
            try {
                val cached = MappingIndex.loadFrom(indexFile)
                if (cached.isValidFor(mappingFile)) {
                    logger.info("Loaded cached index with ${cached.size} classes from ${indexFile.path}")
                    return cached
                }
                logger.info("Cached index is stale, rebuilding...")
            } catch (e: Exception) {
                logger.warn("Failed to load cached index, rebuilding...", e)
            }
        }

        // Build new index
        logger.info("Building index for ${mappingFile.path} (${mappingFile.length()} bytes)...")
        val startTime = System.currentTimeMillis()

        val entries = scanMappingFile(mappingFile)
        val index = MappingIndex.create(
            entries = entries,
            mappingFileSize = mappingFile.length(),
            mappingFileLastModified = mappingFile.lastModified()
        )

        // Save to cache
        try {
            index.saveTo(indexFile)
            logger.info("Saved index to ${indexFile.path}")
        } catch (e: Exception) {
            logger.warn("Failed to save index cache", e)
        }

        val elapsed = System.currentTimeMillis() - startTime
        logger.info("Built index with ${index.size} classes in ${elapsed}ms")

        return index
    }

    /**
     * Scan the mapping file and extract class entries with their byte positions.
     */
    private fun scanMappingFile(mappingFile: File): List<MappingIndexEntry> {
        val entries = mutableListOf<MappingIndexEntry>()
        var currentClassStart: Long = -1
        var currentObfuscatedName: String? = null
        var currentOriginalName: String? = null

        RandomAccessFile(mappingFile, "r").use { raf ->
            var lineStartOffset = 0L
            var line: String?

            while (true) {
                lineStartOffset = raf.filePointer
                line = raf.readLine() ?: break

                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue
                }

                // Check if this is a class definition line
                // Class lines: don't start with whitespace, contain " -> ", end with ":"
                if (!line[0].isWhitespace() && line.contains(CLASS_MAPPING_SEPARATOR) && line.endsWith(":")) {
                    // Save the previous class entry if we have one
                    if (currentObfuscatedName != null && currentOriginalName != null) {
                        val length = (lineStartOffset - currentClassStart).toInt()
                        entries.add(
                            MappingIndexEntry(
                                obfuscatedName = currentObfuscatedName!!,
                                originalName = currentOriginalName!!,
                                byteOffset = currentClassStart,
                                byteLength = length
                            )
                        )
                    }

                    // Parse the new class mapping: "original.ClassName -> obfuscated.Name:"
                    val (original, obfuscated) = parseClassMapping(line)
                    currentClassStart = lineStartOffset
                    currentOriginalName = original
                    currentObfuscatedName = obfuscated
                }
            }

            // Don't forget the last class
            if (currentObfuscatedName != null && currentOriginalName != null) {
                val length = (raf.length() - currentClassStart).toInt()
                entries.add(
                    MappingIndexEntry(
                        obfuscatedName = currentObfuscatedName!!,
                        originalName = currentOriginalName!!,
                        byteOffset = currentClassStart,
                        byteLength = length
                    )
                )
            }
        }

        return entries
    }

    /**
     * Parse a class mapping line and extract original and obfuscated names.
     *
     * Format: "com.original.ClassName -> a.b.c:"
     * Returns: Pair(originalName, obfuscatedName)
     */
    private fun parseClassMapping(line: String): Pair<String, String> {
        val separatorIndex = line.indexOf(CLASS_MAPPING_SEPARATOR)
        val original = line.substring(0, separatorIndex).trim()
        // Remove the trailing ":"
        val obfuscated = line.substring(separatorIndex + CLASS_MAPPING_SEPARATOR.length).trimEnd(':').trim()
        return Pair(original, obfuscated)
    }

    companion object {
        private const val CLASS_MAPPING_SEPARATOR = " -> "
    }
}
