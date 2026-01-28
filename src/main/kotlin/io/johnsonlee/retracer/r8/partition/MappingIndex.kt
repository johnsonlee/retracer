package io.johnsonlee.retracer.r8.partition

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Entry in the mapping index representing a single class mapping.
 *
 * @property obfuscatedName The obfuscated class name
 * @property originalName The original class name
 * @property byteOffset The byte offset in the mapping file where this class starts
 * @property byteLength The length in bytes of this class's mapping data
 */
data class MappingIndexEntry(
    val obfuscatedName: String,
    val originalName: String,
    val byteOffset: Long,
    val byteLength: Int
)

/**
 * Index data structure for lazy loading of class mappings from R8/ProGuard mapping files.
 *
 * The index maps obfuscated class names to their positions in the mapping file,
 * enabling on-demand loading of individual class mappings without loading the entire file.
 *
 * Memory footprint is approximately 50 bytes per class entry.
 */
class MappingIndex private constructor(
    private val entries: Map<String, MappingIndexEntry>,
    val mappingFileSize: Long,
    val mappingFileLastModified: Long
) {

    /**
     * Number of classes in the index
     */
    val size: Int get() = entries.size

    /**
     * Get the index entry for an obfuscated class name
     */
    operator fun get(obfuscatedName: String): MappingIndexEntry? = entries[obfuscatedName]

    /**
     * Check if the index contains the given obfuscated class name
     */
    operator fun contains(obfuscatedName: String): Boolean = obfuscatedName in entries

    /**
     * Get all obfuscated class names in the index
     */
    val obfuscatedClassNames: Set<String> get() = entries.keys

    /**
     * Save the index to a binary file
     */
    fun saveTo(file: File) {
        DataOutputStream(FileOutputStream(file).buffered()).use { out ->
            // Header
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.writeLong(mappingFileSize)
            out.writeLong(mappingFileLastModified)

            // Entry count
            out.writeInt(entries.size)

            // Entries
            for (entry in entries.values) {
                out.writeUTF(entry.obfuscatedName)
                out.writeUTF(entry.originalName)
                out.writeLong(entry.byteOffset)
                out.writeInt(entry.byteLength)
            }
        }
    }

    /**
     * Check if this index is valid for the given mapping file
     */
    fun isValidFor(mappingFile: File): Boolean {
        return mappingFile.exists() &&
                mappingFile.length() == mappingFileSize &&
                mappingFile.lastModified() == mappingFileLastModified
    }

    companion object {
        private const val MAGIC = 0x4D415049 // "MAPI" in hex
        private const val VERSION = 1
        const val INDEX_FILE_NAME = "mapping.idx"

        /**
         * Create a new MappingIndex from entries
         */
        fun create(
            entries: List<MappingIndexEntry>,
            mappingFileSize: Long,
            mappingFileLastModified: Long
        ): MappingIndex {
            val map = entries.associateBy { it.obfuscatedName }
            return MappingIndex(map, mappingFileSize, mappingFileLastModified)
        }

        /**
         * Load a MappingIndex from a binary file
         */
        fun loadFrom(file: File): MappingIndex {
            DataInputStream(FileInputStream(file).buffered()).use { input ->
                // Header
                val magic = input.readInt()
                if (magic != MAGIC) {
                    throw IllegalStateException("Invalid index file magic: ${magic.toString(16)}")
                }

                val version = input.readInt()
                if (version != VERSION) {
                    throw IllegalStateException("Unsupported index version: $version")
                }

                val mappingFileSize = input.readLong()
                val mappingFileLastModified = input.readLong()

                // Entry count
                val count = input.readInt()

                // Entries
                val entries = mutableMapOf<String, MappingIndexEntry>()
                repeat(count) {
                    val obfuscatedName = input.readUTF()
                    val originalName = input.readUTF()
                    val byteOffset = input.readLong()
                    val byteLength = input.readInt()
                    entries[obfuscatedName] = MappingIndexEntry(
                        obfuscatedName, originalName, byteOffset, byteLength
                    )
                }

                return MappingIndex(entries, mappingFileSize, mappingFileLastModified)
            }
        }

        /**
         * Get the index file path for a mapping file
         */
        fun getIndexFile(mappingFile: File): File {
            return File(mappingFile.parentFile, INDEX_FILE_NAME)
        }
    }
}
