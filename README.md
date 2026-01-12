# Concurrent Text Search Engine Implementation

This project provides a robust solution for searching text occurrences within a directory structure using Kotlin Coroutines and Flows. The implementation prioritizes memory efficiency, concurrent execution, and deterministic behavior across various file system scenarios.

## Core Concept
The implementation is based on a **Concurrent Stream Processing** model. Instead of treating files as static blocks of data, the engine treats the entire directory tree as a stream of characters.

**The process follows three stages:**
1.  **Discovery:** Recursive traversal of the directory tree using `Path.walk()`.
2.  **Orchestration:** A `channelFlow` coordinates multiple coroutines, where a `Semaphore(16)` ensures that a maximum of 16 files are being processed in parallel to protect system resources.
3.  **Scanning:** Each file is processed character-by-character using a sliding window. This allows the engine to find matches even if they span across multiple lines, while maintaining a near-zero memory footprint.

## Technical Design & Strategy

### 1. Sliding Window Algorithm (Streaming)
Instead of standard line-by-line reading (`readLine`), which strips newline characters and can lead to excessive memory usage, this implementation utilizes a character-based sliding window approach via `BufferedReader`.

*   **Memory Efficiency**: Space complexity is constant at $O(M)$, where $M$ is the length of the search string. This ensures the engine can process multi-gigabyte files without risking `OutOfMemoryError`.
*   **Newline Integrity**: Since the stream is processed character-by-character, line terminators (`\n`) are preserved. This allows the engine to successfully match strings that span across multiple lines and accurately locate the `\n` character itself.

### 2. Concurrency
The system orchestrates file processing using `channelFlow` for asynchronous result streaming.

*   **Resource Management**: A `Semaphore` with a limit of 16 permits is utilized to implement bounded parallelism. This prevents the application from exceeding Operating System limits on simultaneous open file descriptors and minimizes disk thrashing.

## Edge Case Handling (Predictability)

| Scenario | Strategy | Resulting Behavior |
| :--- | :--- | :--- |
| **Empty Search String** | Early validation check. | `emptyFlow()` return. |
| **Newline (\n) Query** | Manual tracking of `\n` in the stream. | Line/Offset mapping. |
| **Overlapping Matches** | Single-character window shift. | Identifies "aaa" in "aaaa" at indices 0 and 1. |
| **Access Denied** | `runCatching` blocks within workers. | Silently skips restricted files and continues scan. |
| **Subdirectories** | `Path.walk()` extension. | Recursive traversal of all nested levels. |
| **Hidden Files** | Metadata-based filtering. | Ignores system/hidden files by default. |

## Verification (Testing)
The included JUnit 5 suite validates the engine's behavior through the following test cases:
*   Recursive search accuracy across complex directory trees.
*   Coordinate precision (1-based line counting and 0-based offsets).
*   Correctness of multi-line pattern matching.
*   Boundary condition handling (empty files, invalid paths, and overlapping occurrences).

## Future Improvements
*   **Encoding Support**: Currently defaults to UTF-8. Implementation could be extended to support explicit `Charset` parameters (e.g., UTF-16).
*   **Dynamic Concurrency Tuning**: Adjusting the semaphore size based on `Runtime.getRuntime().availableProcessors()` to optimize performance for high-core-count environments.
