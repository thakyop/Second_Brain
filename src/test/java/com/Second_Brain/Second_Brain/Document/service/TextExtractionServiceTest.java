package com.Second_Brain.Second_Brain.Document.service;

import com.Second_Brain.Second_Brain.Document.Document;
import com.Second_Brain.Second_Brain.Document.service.TextExtractionService.ExtractionException;
import com.Second_Brain.Second_Brain.Document.storage.LocalFileStorageService;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import java.util.zip.DeflaterOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextExtractionServiceTest {

    @TempDir
    Path storageRoot;

    private LocalFileStorageService storage;
    private TextExtractionService extractionService;

    @BeforeEach
    void setUp() {
        storage = new LocalFileStorageService(storageRoot.toString());
        extractionService = new TextExtractionService(storage);
    }

    // --- text and Markdown ---

    @Test
    void readsPlainText() throws IOException {
        Document document = upload("notes.txt", "text/plain", utf8("Round-robin scheduling"));

        assertThat(extractionService.extract(document))
                .isEqualTo("Round-robin scheduling");
    }

    @Test
    void readsMarkdownAsRawText() throws IOException {
        String markdown = "# Scheduling\n\n**Round robin** uses a time quantum.";
        Document document = upload("notes.md", "text/markdown", utf8(markdown));

        assertThat(extractionService.extract(document)).isEqualTo(markdown);
    }

    @Test
    void readsMarkdownByContentTypeWhenTheNameDoesNotEndInMd() throws IOException {
        Document document = upload("notes.markdown", "text/markdown", utf8("# Title"));

        assertThat(extractionService.extract(document)).isEqualTo("# Title");
    }

    @Test
    void treatsMdFilesAsMarkdownWhateverTheContentType() throws IOException {
        Document document = upload("notes.md", "application/octet-stream", utf8("# Notes"));

        assertThat(extractionService.extract(document)).isEqualTo("# Notes");
    }

    @Test
    void emptyTextFileGivesEmptyText() throws IOException {
        Document document = upload("empty.txt", "text/plain", new byte[0]);

        assertThat(extractionService.extract(document)).isEmpty();
    }

    @Test
    void removesNulCharacters() throws IOException {
        Document document = upload("notes.txt", "text/plain", utf8("abc\u0000def"));

        assertThat(extractionService.extract(document)).isEqualTo("abcdef");
    }

    @Test
    void readsUtf16WithAByteOrderMark() throws IOException {
        byte[] littleEndian = concat(new byte[] {(byte) 0xFF, (byte) 0xFE},
                "café".getBytes(StandardCharsets.UTF_16LE));
        byte[] bigEndian = concat(new byte[] {(byte) 0xFE, (byte) 0xFF},
                "café".getBytes(StandardCharsets.UTF_16BE));

        assertThat(extractionService.extract(upload("le.txt", "text/plain", littleEndian)))
                .isEqualTo("café");
        assertThat(extractionService.extract(upload("be.txt", "text/plain", bigEndian)))
                .isEqualTo("café");
    }

    @Test
    void dropsTheUtf8ByteOrderMark() throws IOException {
        byte[] content = concat(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, utf8("# Title"));

        assertThat(extractionService.extract(upload("notes.md", "text/markdown", content)))
                .isEqualTo("# Title");
    }

    @Test
    void fallsBackToWindows1252ForOlderTextFiles() throws IOException {
        String text = "Café – it’s fine"; // é, an en dash and a curly apostrophe
        byte[] content = text.getBytes(Charset.forName("windows-1252"));

        assertThat(extractionService.extract(upload("old.txt", "text/plain", content)))
                .isEqualTo(text);
    }

    @Test
    void binaryDataLabelledAsTextFails() throws IOException {
        byte[] content = {(byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 13, (byte) 0xFF};
        Document document = upload("image.txt", "text/plain", content);

        assertThatThrownBy(() -> extractionService.extract(document))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("not a text file");
    }

    // --- PDF ---

    @Test
    void extractsTextFromPdf() throws IOException {
        Document document = upload("notes.pdf", "application/pdf",
                pdf("Operating system scheduling"));

        assertThat(extractionService.extract(document))
                .contains("Operating system scheduling");
    }

    @Test
    void extractsTextFromEveryPdfPage() throws IOException {
        Document document = upload("two.pdf", "application/pdf",
                pdf("first page text", "second page text"));

        assertThat(extractionService.extract(document))
                .contains("first page text")
                .contains("second page text");
    }

    @Test
    void pdfWithoutTextGivesBlankText() throws IOException {
        Document document = upload("scan.pdf", "application/pdf", pdf(""));

        assertThat(extractionService.extract(document)).isBlank();
    }

    @Test
    void removesNulCharactersFromPdfText() throws IOException {
        // <61626300646566> draws "abc", byte 0 and "def"; PDFBox turns the 0 into U+0000.
        Document document = upload("nul.pdf", "application/pdf",
                pdfWithRawText("<61626300646566> Tj"));

        assertThat(extractionService.extract(document))
                .doesNotContain("\u0000")
                .contains("abcdef");
    }

    @Test
    void malformedPdfFails() throws IOException {
        Document document = upload("broken.pdf", "application/pdf", utf8("this is not a pdf"));

        assertThatThrownBy(() -> extractionService.extract(document))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("Failed to extract text");
    }

    @Test
    void pdfThatDecompressesToAHugeSizeFails() throws IOException {
        // About 130 KB on disk, 128 MB once decompressed.
        Document document = upload("bomb.pdf", "application/pdf", pdfBomb(128L * 1024 * 1024));

        assertThatThrownBy(() -> extractionService.extract(document))
                .isInstanceOf(ExtractionException.class)
                .hasStackTraceContaining("Maximum allowed scratch file memory exceeded");
    }

    @Test
    void pdfPageWithTooMuchTextFails() throws IOException {
        Document document = upload("dense.pdf", "application/pdf", pdf("A".repeat(200_001)));

        assertThatThrownBy(() -> extractionService.extract(document))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("too much text");
    }

    // --- errors ---

    @Test
    void missingFileFails() {
        Document document = document("missing/file.txt", "file.txt", "text/plain");

        assertThatThrownBy(() -> extractionService.extract(document))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("Stored file not found");
    }

    @Test
    void unsupportedContentTypeFails() throws IOException {
        Document document = upload("photo.png", "image/png", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> extractionService.extract(document))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("Unsupported content type");
    }

    // Stores the file the same way an upload does, so the test uses the
    // real storage path format (relative to the storage root).
    private Document upload(String filename, String contentType, byte[] content)
            throws IOException {
        String storagePath = storage.store(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new MockMultipartFile("file", filename, contentType, content));
        return document(storagePath, filename, contentType);
    }

    private static Document document(String storagePath, String filename, String contentType) {
        Document document = new Document();
        document.setStoragePath(storagePath);
        document.setFilename(filename);
        document.setContentType(contentType);
        return document;
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    // A PDF with one page per argument; "" gives a page without text, like a scan.
    private static byte[] pdf(String... pageTexts) throws IOException {
        try (PDDocument pdf = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String pageText : pageTexts) {
                PDPage page = new PDPage();
                pdf.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                    content.beginText();
                    content.setFont(PDType1Font.HELVETICA, 12);
                    content.newLineAtOffset(72, 720);
                    content.showText(pageText);
                    content.endText();
                }
            }
            pdf.save(out);
            return out.toByteArray();
        }
    }

    // One-page PDF whose text is drawn by a raw PDF operator, for bytes that
    // showText() refuses to write (such as 0).
    @SuppressWarnings("deprecation")
    private static byte[] pdfWithRawText(String showTextOperator) throws IOException {
        try (PDDocument pdf = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            pdf.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                content.beginText();
                content.setFont(PDType1Font.HELVETICA, 12);
                content.newLineAtOffset(72, 720);
                content.appendRawCommands(showTextOperator + "\n");
                content.endText();
            }
            pdf.save(out);
            return out.toByteArray();
        }
    }

    // One page whose compressed content stream inflates to `decodedBytes` spaces.
    private static byte[] pdfBomb(long decodedBytes) throws IOException {
        byte[] spaces = new byte[1024 * 1024];
        Arrays.fill(spaces, (byte) ' ');
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (OutputStream deflate = new DeflaterOutputStream(compressed)) {
            for (long written = 0; written < decodedBytes; written += spaces.length) {
                deflate.write(spaces);
            }
        }
        try (PDDocument pdf = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            pdf.addPage(page);
            COSStream contents = pdf.getDocument().createCOSStream();
            contents.setItem(COSName.FILTER, COSName.FLATE_DECODE);
            try (OutputStream raw = contents.createRawOutputStream()) {
                raw.write(compressed.toByteArray());
            }
            page.setContents(new PDStream(contents));
            pdf.save(out);
            return out.toByteArray();
        }
    }
}
