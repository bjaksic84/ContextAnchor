package com.ragengine.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;

/**
 * Service responsible for extracting text content from uploaded documents.
 * Uses Apache Tika to support multiple file formats (PDF, DOCX, TXT, etc.)
 */
@Service
@Slf4j
public class DocumentExtractionService {

    private final Tika tika = new Tika();

    /**
     * Extracts text content from a multipart file using Apache Tika.
     *
     * @param file the uploaded file
     * @return extracted text content
     */
    public String extractText(MultipartFile file) {
        log.info("Extracting text from file: {}", file.getOriginalFilename());

        try (InputStream inputStream = file.getInputStream()) {
            // Use BodyContentHandler with -1 to allow unlimited content length
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            metadata.set(Metadata.CONTENT_TYPE, file.getContentType());

            AutoDetectParser parser = new AutoDetectParser();
            ParseContext context = new ParseContext();

            parser.parse(inputStream, handler, metadata, context);

            String extractedText = handler.toString().trim();
            log.info("Extracted {} characters from '{}'", extractedText.length(), file.getOriginalFilename());

            return extractedText;

        } catch (IOException | TikaException | SAXException e) {
            log.error("Failed to extract text from file: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("Failed to extract text from document: " + e.getMessage(), e);
        }
    }

    /**
     * Detects the MIME type of a file.
     *
     * @param file the uploaded file
     * @return the detected MIME type
     */
    public String detectMimeType(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return tika.detect(inputStream, file.getOriginalFilename());
        } catch (IOException e) {
            log.error("Failed to detect MIME type for file: {}", file.getOriginalFilename(), e);
            return file.getContentType();
        }
    }

    /**
     * Extracts page count from document metadata (primarily for PDFs).
     *
     * @param file the uploaded file
     * @return page count, or null if not applicable
     */
    public Integer extractPageCount(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            Metadata metadata = new Metadata();
            AutoDetectParser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(0);

            parser.parse(inputStream, handler, metadata, new ParseContext());

            String pages = metadata.get("xmpTPg:NPages");
            if (pages == null) {
                pages = metadata.get("meta:page-count");
            }
            return pages != null ? Integer.parseInt(pages) : null;

        } catch (Exception e) {
            log.debug("Could not extract page count from file: {}", file.getOriginalFilename());
            return null;
        }
    }
}
