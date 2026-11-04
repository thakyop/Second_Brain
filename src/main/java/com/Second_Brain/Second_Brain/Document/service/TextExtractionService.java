package com.Second_Brain.Second_Brain.Document.service;

import com.Second_Brain.Second_Brain.Document.Document;
import com.Second_Brain.Second_Brain.Document.storage.FileStorageService;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

/**
 * Service responsible for extracting plain text from the supported document types.
 *
 * Supported content types are:
 *   - application/pdf (PDFBox extraction)
 *   - text/plain (UTF‑8, UTF‑16 with a byte-order mark, or Windows-1252)
 *   - text/markdown or *.md (read the same way as text/plain)
 */
@Service
public class TextExtractionService {

    /*
     * A small PDF can decompress to gigabytes (a "PDF bomb") or draw millions
     * of characters on one page. Without limits that runs the whole app out of
     * memory. Real documents stay far below these.
     */
    private static final long MAX_PDF_MEMORY_BYTES = 64L * 1024 * 1024;
    private static final int MAX_CHARACTERS_PER_PDF_PAGE = 200_000;

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private final FileStorageService fileStorageService;

    public TextExtractionService(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    /**
     * Extracts readable text from the given {@link Document}.
     *
     * @param document the persisted document entity
     * @return extracted plain text (may be empty string)
     * @throws ExtractionException if the file cannot be read or the format is unsupported
     */
    public String extract(Document document) throws ExtractionException {
        String storagePath = document.getStoragePath();
        String contentType = document.getContentType();
        // storagePath is relative to the storage root, not the working directory.
        Path path = fileStorageService.resolve(storagePath);
        if (!Files.isRegularFile(path)) {
            throw new ExtractionException("Stored file not found: " + storagePath);
        }
        try {
            if ("application/pdf".equalsIgnoreCase(contentType)) {
                return removeNulCharacters(extractPdf(path));
            }
            if ("text/plain".equalsIgnoreCase(contentType) || "text/markdown".equalsIgnoreCase(contentType)
                    || (document.getFilename() != null && document.getFilename().toLowerCase().endsWith(".md"))) {
                return removeNulCharacters(decodeText(Files.readAllBytes(path)));
            }
            throw new ExtractionException("Unsupported content type for extraction: " + contentType);
        } catch (IOException e) {
            throw new ExtractionException("Failed to extract text from document", e);
        }
    }

    /*
     * PostgreSQL text columns reject NUL characters (code point 0). PDFBox
     * produces them for some PDFs, and so does a UTF-16 file without a
     * byte-order mark. Saving such a chunk would fail.
     */
    private static String removeNulCharacters(String text) {
        return text.replace("\u0000", "");
    }

    /*
     * Text files are usually UTF-8, but Windows tools often save UTF-16 with a
     * byte-order mark (BOM) at the start, and older files may be Windows-1252.
     */
    private static String decodeText(byte[] bytes) {
        if (startsWith(bytes, 0xEF, 0xBB, 0xBF)) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        if (startsWith(bytes, 0xFF, 0xFE)) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }
        if (startsWith(bytes, 0xFE, 0xFF)) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }
        try {
            // Unlike new String(bytes, UTF_8), the decoder fails on invalid UTF-8.
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException notUtf8) {
            // Old single-byte text never contains NUL bytes; binary files do.
            for (byte b : bytes) {
                if (b == 0) {
                    throw new ExtractionException("File is not a text file");
                }
            }
            return new String(bytes, WINDOWS_1252);
        }
    }

    private static boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private String extractPdf(Path path) throws IOException {
        // Loading from the file, not a stream, also keeps the PDF itself out of memory.
        MemoryUsageSetting memoryLimit = MemoryUsageSetting.setupMainMemoryOnly(MAX_PDF_MEMORY_BYTES);
        try (PDDocument pdf = PDDocument.load(path.toFile(), memoryLimit)) {
            return new LimitedTextStripper().getText(pdf);
        }
    }

    /**
     * A PDFTextStripper that gives up on a page with an absurd amount of text.
     */
    private static class LimitedTextStripper extends PDFTextStripper {

        private int charactersOnPage;

        LimitedTextStripper() throws IOException {
        }

        @Override
        protected void startPage(PDPage page) throws IOException {
            charactersOnPage = 0;
            super.startPage(page);
        }

        @Override
        protected void processTextPosition(TextPosition character) {
            if (++charactersOnPage > MAX_CHARACTERS_PER_PDF_PAGE) {
                throw new ExtractionException("PDF page has too much text");
            }
            super.processTextPosition(character);
        }
    }

    /**
     * Runtime exception used to signal extraction problems.
     */
    public static class ExtractionException extends ResponseStatusException {
        public ExtractionException(String reason) {
            super(HttpStatus.UNPROCESSABLE_ENTITY, reason);
        }
        public ExtractionException(String reason, Throwable cause) {
            super(HttpStatus.UNPROCESSABLE_ENTITY, reason, cause);
        }
    }
}
