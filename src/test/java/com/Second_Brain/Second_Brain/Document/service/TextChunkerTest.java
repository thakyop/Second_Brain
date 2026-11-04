package com.Second_Brain.Second_Brain.Document.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// A chunking bug usually shows up as an infinite loop. SEPARATE_THREAD lets
// the timeout fail the test even if the loop never checks for interrupts.
@Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class TextChunkerTest {

    @Test
    void rejectsInvalidSettings() {
        assertThatThrownBy(() -> new TextChunker(0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TextChunker(100, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TextChunker(100, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankOrNullTextGivesNoChunks() {
        TextChunker chunker = new TextChunker(100, 20);

        assertThat(chunker.chunk(null)).isEmpty();
        assertThat(chunker.chunk("")).isEmpty();
        assertThat(chunker.chunk(" \n\t ")).isEmpty();
    }

    @Test
    void shortTextGivesOneTrimmedChunk() {
        TextChunker chunker = new TextChunker(1000, 200);

        assertThat(chunker.chunk("  Operating system scheduling \n"))
                .containsExactly("Operating system scheduling");
    }

    @Test
    void textShorterThanChunkSizeButLongerThanOverlapGivesOneChunk() {
        String text = String.join(" ", words(100)); // 389 chars

        assertThat(new TextChunker(1000, 200).chunk(text)).containsExactly(text);
    }

    @Test
    void overlapRepeatsTheLastWordsOfThePreviousChunk() {
        assertThat(new TextChunker(14, 4).chunk("aaaa bbbb cccc dddd eeee ffff"))
                .containsExactly("aaaa bbbb cccc", "cccc dddd eeee", "eeee ffff");
    }

    @Test
    void wordEndingExactlyAtTheLimitStaysInTheChunk() {
        assertThat(new TextChunker(5, 0).chunk("ab cd ef"))
                .containsExactly("ab cd", "ef");
    }

    @Test
    void leadingWhitespaceDoesNotTakeChunkRoom() {
        assertThat(new TextChunker(5, 0).chunk(" abcde fgh"))
                .containsExactly("abcde", "fgh");
    }

    @Test
    void unicodeWhitespaceIsRemovedFromChunkEdges() {
        // U+3000 is the ideographic space used in Chinese and Japanese text.
        assertThat(new TextChunker(100, 0).chunk("abc　")).containsExactly("abc");
        assertThat(new TextChunker(4, 0).chunk("abc　　def"))
                .containsExactly("abc", "def");
    }

    @Test
    void textOfExactlyChunkSizeGivesOneChunk() {
        String text = "a".repeat(100);

        assertThat(new TextChunker(100, 20).chunk(text)).containsExactly(text);
    }

    @Test
    void longTextIsSplitIntoOverlappingRunsOfWholeWords() {
        String text = String.join(" ", words(500)); // "w0 w1 ... w499"

        List<String> chunks = new TextChunker(100, 30).chunk(text);

        assertThat(chunks).hasSizeGreaterThan(1);
        int previousFirst = -1;
        int previousLast = -1;
        for (String chunk : chunks) {
            assertThat(chunk.length()).isLessThanOrEqualTo(100);

            List<Integer> ids = wordIds(chunk);
            for (int k = 1; k < ids.size(); k++) {
                assertThat(ids.get(k)).as("words stay in order")
                        .isEqualTo(ids.get(k - 1) + 1);
            }
            if (previousLast >= 0) {
                assertThat(ids.getFirst()).as("moves forward")
                        .isGreaterThan(previousFirst);
                assertThat(ids.getFirst()).as("overlaps the previous chunk")
                        .isLessThanOrEqualTo(previousLast);
                assertThat(ids.getLast()).as("adds new words")
                        .isGreaterThan(previousLast);
            }
            previousFirst = ids.getFirst();
            previousLast = ids.getLast();
        }
        assertThat(wordIds(chunks.getFirst()).getFirst()).isZero();
        assertThat(previousLast).isEqualTo(499);
    }

    @Test
    void withoutOverlapChunksJoinBackIntoTheOriginalText() {
        String text = String.join(" ", words(300));

        List<String> chunks = new TextChunker(50, 0).chunk(text);

        assertThat(String.join(" ", chunks)).isEqualTo(text);
    }

    @Test
    void wordsExactlyAsLongAsTheChunkSizeAreNotCut() {
        assertThat(new TextChunker(5, 0).chunk("abcde fghij  klmno"))
                .containsExactly("abcde", "fghij", "klmno");
    }

    @Test
    void trailingNewlineDoesNotAddAChunkThatRepeatsTheOverlap() {
        String text = "abcd ".repeat(200) + "\n"; // 1001 chars

        assertThat(new TextChunker(1000, 200).chunk(text))
                .containsExactly(text.strip());
    }

    @Test
    void wordThatDoesNotFitAfterTheOverlapIsNotPrecededByARepeat() {
        String start = "abcd ".repeat(200).strip();
        String longWord = "x".repeat(900);

        List<String> chunks = new TextChunker(1000, 200)
                .chunk(start + " " + longWord + " end");

        assertThat(chunks).containsExactly(start, longWord + " end");
    }

    @Test
    void hugeChunkSizeDoesNotOverflow() {
        assertThat(new TextChunker(Integer.MAX_VALUE, 0).chunk(" a"))
                .containsExactly("a");
    }

    @Test
    void newlinesAndTabsAreWordBoundaries() {
        String text = "alpha\nbeta\tgamma\ndelta";

        assertThat(new TextChunker(12, 0).chunk(text))
                .containsExactly("alpha\nbeta", "gamma\ndelta");
    }

    @Test
    void wordLongerThanChunkSizeIsCutWithoutLosingText() {
        String longWord = "x".repeat(250);

        List<String> chunks = new TextChunker(100, 0).chunk("start " + longWord + " end");

        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.length()).isLessThanOrEqualTo(100));
        assertThat(String.join("", chunks).replace(" ", ""))
                .isEqualTo("start" + longWord + "end");
    }

    @Test
    void longWordWithOverlapStillTerminates() {
        List<String> chunks = new TextChunker(100, 50).chunk("x".repeat(1000));

        assertThat(chunks).isNotEmpty().allSatisfy(chunk ->
                assertThat(chunk.length()).isBetween(1, 100));
        assertThat(chunks.stream().mapToInt(String::length).sum())
                .isGreaterThanOrEqualTo(1000);
    }

    @Test
    void doesNotCutAnEmojiInHalf() {
        String emoji = "\uD83D\uDE00"; // one emoji is two Java chars
        String text = emoji.repeat(10);

        List<String> chunks = new TextChunker(5, 0).chunk(text);

        assertThat(String.join("", chunks)).isEqualTo(text);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(Character.isLowSurrogate(chunk.charAt(0))).isFalse();
            assertThat(Character.isHighSurrogate(chunk.charAt(chunk.length() - 1))).isFalse();
        });
    }

    @Test
    void randomTextsAlwaysTerminateWithoutLosingText() {
        Random random = new Random(42); // fixed seed: same inputs on every run
        for (int run = 0; run < 1_000; run++) {
            int chunkSize = 1 + random.nextInt(40);
            int overlap = random.nextInt(chunkSize);
            String text = randomText(random, random.nextInt(400));
            String compact = withoutWhitespace(text);
            String input = "chunkSize=" + chunkSize + " overlap=" + overlap
                    + " text=" + text.replace("\n", "\\n").replace("\t", "\\t");

            List<String> overlapping = new TextChunker(chunkSize, overlap).chunk(text);
            assertThat(overlapping).as(input).allSatisfy(chunk -> {
                assertThat(chunk).isNotBlank();
                assertThat(chunk.length()).isLessThanOrEqualTo(chunkSize);
                assertThat(compact).contains(withoutWhitespace(chunk));
            });
            if (!compact.isEmpty()) {
                assertThat(compact).as(input)
                        .startsWith(withoutWhitespace(overlapping.getFirst()))
                        .endsWith(withoutWhitespace(overlapping.getLast()));
            }

            // Without overlap the chunks split the text exactly, nothing repeated or lost.
            List<String> partition = new TextChunker(chunkSize, 0).chunk(text);
            assertThat(withoutWhitespace(String.join("", partition))).as(input)
                    .isEqualTo(compact);
        }
    }

    private static List<String> words(int count) {
        return IntStream.range(0, count).mapToObj(i -> "w" + i).toList();
    }

    // Parses "w12 w13 w14" into [12, 13, 14], failing on any cut-off word.
    private static List<Integer> wordIds(String chunk) {
        List<Integer> ids = new ArrayList<>();
        for (String token : chunk.split("\\s+")) {
            assertThat(token).as("whole word").matches("w\\d+");
            ids.add(Integer.parseInt(token.substring(1)));
        }
        return ids;
    }

    // Letters, whitespace and the occasional emoji (two Java chars).
    private static String randomText(Random random, int tokens) {
        String[] alphabet = {"a", "b", "c", "d", "e", "f", "g", "h", "i", "j",
                " ", " ", " ", "\n", "\t", "😀"};
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < tokens; i++) {
            text.append(alphabet[random.nextInt(alphabet.length)]);
        }
        return text.toString();
    }

    private static String withoutWhitespace(String text) {
        return text.replaceAll("\\s", "");
    }
}
