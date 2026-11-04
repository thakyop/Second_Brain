package com.Second_Brain.Second_Brain.Document.service;

import java.util.UUID;

/**
 * Published by DocumentService.upload(). IngestionService handles it only
 * after the upload's transaction has committed, so the document row exists
 * for the background thread that ingests it.
 */
public record DocumentUploadedEvent(UUID documentId) {
}
