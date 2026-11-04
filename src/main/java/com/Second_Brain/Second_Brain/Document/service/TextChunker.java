package com.Second_Brain.Second_Brain.Document.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a large text into fixed-size chunks with configurable overlap.
 *
 * Chunk size and overlap are read from application properties:
 *   document.ingestion.chunk-size (default 1000)
 *   document.ingestion.chunk-overlap (default 200)
 */
@Component
public class TextChunker {

    private final int chunkSize;
    private final int overlap;

    public TextChunker(
            @Value("${document.ingestion.chunk-size:1000}") int chunkSize,
            @Value("${document.ingestion.chunk-overlap:200}") int overlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("Chunk size must be positive");
        }
        if (overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("Overlap must be non‑negative and smaller than chunk size");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    /**
     * Split the given text into ordered, non‑empty chunks of at most
     * {@code chunkSize} characters. Chunks end on whitespace when possible,
     * and each chunk starts up to {@code overlap} characters before the
     * previous one ended, at the start of a word.
     *
     * @param text full document text (may be empty or null)
     * @return list of chunk strings, ordered from start to end
     */
    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        int length = text.length();
        int start = skipWhitespace(text, 0);
        int previousEnd = 0; // where the previous chunk's text ended
        int lastStart = -1;  // start of the previous pass through the loop
        while (start < length) {
            if (start <= lastStart) {
                // Can't happen today. Fail loudly instead of looping until memory
                // runs out if a later change breaks the progress logic below.
                throw new IllegalStateException("Chunker made no progress at index " + start);
            }
            lastStart = start;
            // (written this way so a huge chunkSize can't overflow int)
            int end = start + Math.min(chunkSize, length - start);
            if (end < length) {
                end = adjustEnd(text, start, end);
            }
            String chunk = text.substring(start, end).strip();
            // start is never whitespace, so the chunk's text ends here:
            int chunkEnd = start + chunk.length();
            if (chunkEnd <= previousEnd) {
                // Only whitespace or a word too long to fit came after the
                // overlap, so this chunk would just repeat the previous one's
                // tail. Skip it and continue after the previous chunk instead.
                start = skipWhitespace(text, previousEnd);
                continue;
            }
            chunks.add(chunk);
            previousEnd = chunkEnd;
            if (end == length) {
                break; // that was the last piece of the text
            }
            // Step back by the overlap, but always move forward overall:
            // starting at or before the previous start would repeat forever.
            int next = end - overlap;
            start = next > start ? skipToWordStart(text, next, end) : end;
            // Begin at a word: leading whitespace would only waste chunk space.
            start = skipWhitespace(text, start);
        }
        return chunks;
    }

    private static int skipWhitespace(String text, int index) {
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    /**
     * Moves the end of a chunk back to the last whitespace inside it, so no
     * word is cut in half. A single "word" longer than the chunk size (a URL,
     * say) has no whitespace and is cut at the size limit instead.
     */
    private static int adjustEnd(String text, int start, int end) {
        for (int i = end; i > start; i--) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        // Don't cut between the two chars of an emoji (a surrogate pair):
        // half an emoji is invalid text that the embedding request rejects.
        if (end - 1 > start && Character.isHighSurrogate(text.charAt(end - 1))) {
            return end - 1;
        }
        return end;
    }

    /**
     * If {@code index} is in the middle of a word, moves it forward to the
     * next whitespace so the chunk doesn't start with a partial word.
     */
    private static int skipToWordStart(String text, int index, int limit) {
        while (index < limit
                && !Character.isWhitespace(text.charAt(index - 1))
                && !Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }
}
