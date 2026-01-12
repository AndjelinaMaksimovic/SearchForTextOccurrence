package com.v1

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchForTextOccurrenceTest {

    @TempDir
    lateinit var tempDir: Path

    /**
     * Helper to quickly create a file with content
     */
    private fun createTestFile(relativePath: String, content: String): Path {
        val file = tempDir.resolve(relativePath)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
        return file
    }

    @Test
    fun `basic search finds multiple occurrences in one file`() = runTest {
        createTestFile("test.txt", "hello world\nhello again")

        val results = searchForTextOccurrence("hello", tempDir)
            .toList()
            .sortedWith(compareBy({ it.line }, { it.offset }))

        assertEquals(2, results.size)

        assertEquals(1, results[0].line)
        assertEquals(0, results[0].offset)

        assertEquals(2, results[1].line)
        assertEquals(0, results[1].offset)
    }

    @Test
    fun `recursive search finds files in subdirectories`() = runTest {
        createTestFile("folderA/file1.txt", "target")
        createTestFile("folderA/subFolderB/file2.txt", "target")

        val results = searchForTextOccurrence("target", tempDir)
            .toList()

        assertEquals(2, results.size)

        val fileNames = results.map { it.file.fileName.toString() }
        assertTrue("file1.txt" in fileNames)
        assertTrue("file2.txt" in fileNames)
    }

    @Test
    fun `searching for newline character works`() = runTest {
        createTestFile("newline.txt", "line1\nline2")

        val results = searchForTextOccurrence("\n", tempDir).toList()

        assertEquals(1, results.size)
        assertEquals(1, results[0].line)
        assertEquals(5, results[0].offset)
    }

    @Test
    fun `overlapping occurrences are all found`() = runTest {
        createTestFile("overlap.txt", "aaaa")

        val results = searchForTextOccurrence("aaa", tempDir)
            .toList()
            .sortedBy { it.offset }

        assertEquals(2, results.size)
        assertEquals(0, results[0].offset)
        assertEquals(1, results[1].offset)
    }

    @Test
    fun `empty search string returns empty flow`() = runTest {
        createTestFile("file.txt", "content")

        val results = searchForTextOccurrence("", tempDir).toList()

        assertTrue(results.isEmpty())
    }

    @Test
    fun `search in empty file does not crash and returns empty`() = runTest {
        createTestFile("empty.txt", "")

        val results = searchForTextOccurrence("query", tempDir).toList()

        assertTrue(results.isEmpty())
    }

    @Test
    fun `hidden files are ignored`() = runTest {
        createTestFile("visible.txt", "findme")

        val hiddenFile = tempDir.resolve(".hidden.txt")
        Files.writeString(hiddenFile, "findme")

        val results = searchForTextOccurrence("findme", tempDir).toList()

        assertEquals(1, results.size)
        assertEquals("visible.txt", results[0].file.fileName.toString())
    }

    @Test
    fun `invalid directory returns empty flow`() = runTest {
        val invalidPath = tempDir.resolve("does_not_exist")

        val results = searchForTextOccurrence("query", invalidPath).toList()

        assertTrue(results.isEmpty())
    }

    @Test
    fun `searching for string spanning multiple lines works`() = runTest {
        createTestFile("multiline.txt", "Hello\nWorld")

        val results = searchForTextOccurrence("Hello\nWorld", tempDir).toList()

        assertEquals(1, results.size)

        val occurrence = results[0]
        assertEquals(1, occurrence.line)
        assertEquals(0, occurrence.offset)
        assertEquals("multiline.txt", occurrence.file.fileName.toString())
    }
}
