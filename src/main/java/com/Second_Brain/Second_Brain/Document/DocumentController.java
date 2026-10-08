package com.Second_Brain.Second_Brain.Document;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(
            value = "/workspaces/{workspaceId}/documents",
            consumes = "multipart/form-data"
    )
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse uploadDocument(
            @PathVariable UUID workspaceId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt
    ) {

        UUID userId = UUID.fromString(jwt.getSubject());

        Document document = documentService.upload(
                workspaceId,
                userId,
                file
        );

        return DocumentResponse.from(document);
    }

    @GetMapping("/workspaces/{workspaceId}/documents")
    public List<DocumentResponse> listDocuments(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal Jwt jwt
    ) {

        UUID userId = UUID.fromString(jwt.getSubject());

        return documentService
                .listDocuments(workspaceId, userId)
                .stream()
                .map(DocumentResponse::from)
                .toList();
    }

    @GetMapping("/documents/{documentId}")
    public DocumentResponse getDocument(
            @PathVariable UUID documentId,
            @AuthenticationPrincipal Jwt jwt
    ) {

        UUID userId = UUID.fromString(jwt.getSubject());

        Document document = documentService.getDocument(
                documentId,
                userId
        );

        return DocumentResponse.from(document);
    }
}