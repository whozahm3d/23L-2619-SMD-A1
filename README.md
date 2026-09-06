# LogForge - System Log Analyzer

LogForge is a command-line Java tool that analyzes distributed application logs. It was built incrementally across 9 questions (Q1–Q9) as part of the Software for Mobile Devices course assignment.

## Usage

Compile and run the program using standard Java tools:

```bash
javac LogForge.java
java LogForge <inputfile> [outputfile]
```

> **Note:** If `[outputfile]` is omitted, the report defaults to `logforge_report.txt`.

## Features

The tool was built incrementally across nine sub-tasks:

- **Q1: Log Parsing** - Custom pipe-delimited (`|`) field parsing without using regex or `String.split()`.
- **Q2: Line Validation** - Validation of line format, timestamps (`YYYY-MM-DD HH:MM:SS`), log levels (`INFO`, `WARN`, `ERROR`), and numeric request IDs.
- **Q3: LogEntry Modeling** - Encapsulates log fields into custom `LogEntry` objects stored in manually resized arrays.
- **Q4: Per-Service Statistics** - Aggregates total lines and level counts (`INFO`, `WARN`, `ERROR`) per service.
- **Q5: Error-Rate Sorting** - Sorts service statistics by error rate descending (alphabetical ascending for ties) using Insertion Sort and a custom stable Radix Sort.
- **Q6: Incident Detection** - Detects incidents per service defined as 3 or more `ERROR` logs occurring within a 60-second window.
- **Q7: Per-Request Statistics** - Tracks total records, error counts, failure status (`SUCCESS`/`FAILED`), and participating services for each request ID.
- **Q8: Chronological Sorting** - Sorts log entries by timestamp using a custom Merge Sort with original position tie-breaking.
- **Q9: Formatted Report Generation** - Generates the final analysis report to console and file, formatted per specification.

## Constraints & Compatibility

- **Custom Algorithms & Data Structures**: Per assignment restrictions, this program avoids Java collections (`ArrayList`, `HashMap`), regular expressions, and built-in library sorting methods (`Arrays.sort()`), relying entirely on manually-resized arrays and custom algorithms.
- **Specification**: The output format matches the provided `format.txt` specification exactly.