package com.v1

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.walk

interface Occurrence {
    val file: Path
    val line: Int
    val offset: Int
}

private data class OccurrenceImpl(
    override val file: Path,
    override val line: Int,
    override val offset: Int
) : Occurrence

fun searchForTextOccurrence(
    stringToSearch: String,
    directory: Path,
): Flow<Occurrence> {

    // Corner Case: Search string is empty
    if (stringToSearch.isEmpty()) return emptyFlow()

    // Corner Case: Directory validation
    if (!Files.exists(directory) || !Files.isDirectory(directory)) {
        return emptyFlow()
    }

    // Performance: Limit concurrent open files (OS limit safety)
    val semaphore = Semaphore(16)

    return channelFlow {
        // Move to IO thread pool for disk operations
        directory.walk()
            .filter { it.isValidSearchTarget() }
            .forEach { file ->
                launch(Dispatchers.IO) {
                    semaphore.withPermit {
                        processFileStreaming(file, stringToSearch)
                    }
                }
            }
    }
}


// Worker function to handle Tip 1 (RAM), Tip 2 (Streaming), and Tip 3 (Newlines).
private suspend fun ProducerScope<Occurrence>.processFileStreaming(
    file: Path,
    query: String
) {
    runCatching {
        file.toFile().bufferedReader().use { reader ->
            var currentLine = 1
            var currentOffsetInLine = 0

            // "Window" tracking: Where does the match START?
            var windowStartLine = 1
            var windowStartOffset = 0

            val buffer = StringBuilder()

            while (true) {
                val charInt = reader.read()
                if (charInt == -1) break
                val char = charInt.toChar()

                buffer.append(char)

                // Slide the window if it exceeds query length
                if (buffer.length > query.length) {
                    val droppedChar = buffer[0]
                    if (droppedChar == '\n') {
                        currentLine++
                        currentOffsetInLine = 0
                    } else {
                        currentOffsetInLine++
                    }

                    // The "window" (start of potential match) moves to the new positions
                    windowStartLine = currentLine
                    windowStartOffset = currentOffsetInLine

                    buffer.deleteCharAt(0)
                }

                // Found a match? Send it immediately (Streaming)
                if (buffer.toString() == query) {
                    send(OccurrenceImpl(file, windowStartLine, windowStartOffset))
                }
            }
        }
    }.onFailure {
        /*
         * Files that cannot be opened (permission denied, locked)
         * are silently skipped to ensure predictable streaming behavior.
        */
    }
}

//Helper to ensure we don't try to search binary/hidden system files
private fun Path.isValidSearchTarget(): Boolean =
    runCatching {
        this.isRegularFile() && !Files.isHidden(this) && Files.isReadable(this)
    }.getOrDefault(false)