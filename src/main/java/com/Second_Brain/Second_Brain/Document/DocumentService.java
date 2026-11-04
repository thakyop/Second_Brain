package com.Second_Brain.Second_Brain.Document;

import com.Second_Brain.Second_Brain.Document.storage.FileStorageService;
import com.Second_Brain.Second_Brain.User.User;
import com.Second_Brain.Second_Brain.User.UserRepository;
import com.Second_Brain.Second_Brain.workspace.workspace;
import com.Second_Brain.Second_Brain.workspace.WorkspaceMemberId;
import com.Second_Brain.Second_Brain.workspace.WorkspaceMemberRepository;
import com.Second_Brain.Second_Brain.workspace.WorkspaceRepository;
import com.Second_Brain.Second_Brain.Document.service.DocumentUploadedEvent;

import jakarta.persistence.EntityManager;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher eventPublisher;

    public DocumentService(
            DocumentRepository documentRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            UserRepository userRepository,
            FileStorageService fileStorageService,
            EntityManager entityManager,
            ApplicationEventPublisher eventPublisher) {
        this.documentRepository = documentRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
        this.entityManager = entityManager;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Document upload(
            UUID workspaceId,
            UUID userId,
            MultipartFile file) {

        // 1. Validate file
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required");
        }

        // 2. Find workspace
        workspace workspace = workspaceRepository
                .findById(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));

        // 3. Verify workspace membership
        WorkspaceMemberId memberId = new WorkspaceMemberId(workspaceId, userId);
        if (!workspaceMemberRepository.existsById(memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this workspace");
        }

        // 4. Find authenticated user
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        // 5. Validate file type
        String contentType = file.getContentType();
        if (!isSupportedContentType(contentType, file.getOriginalFilename())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported file type");
        }

        // 6. Validate filename
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filename is required");
        }

        // 7. Create document entity
        Document document = new Document();
        UUID documentId = UUID.randomUUID();
        document.setId(documentId);
        document.setWorkspace(workspace);
        document.setUploadedBy(user);
        document.setFilename(originalFilename);
        document.setContentType(contentType);
        document.setSizeBytes(file.getSize());
        document.setStatus(Document.Status.UPLOADED);

        // 8. Store physical file
        String storagePath;
        try {
            storagePath = fileStorageService.store(workspaceId, documentId, file);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file", e);
        }
        document.setStoragePath(storagePath);

        // 9. Persist document; ingestion starts once this transaction commits
        try {
            entityManager.persist(document);
            entityManager.flush();
            // Handled by IngestionService.onDocumentUploaded after the commit
            eventPublisher.publishEvent(new DocumentUploadedEvent(documentId));
            return document;
        } catch (RuntimeException e) {
            // Cleanup stored file on DB failure
            try {
                fileStorageService.delete(storagePath);
            } catch (IOException ignored) {
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<Document> listDocuments(UUID workspaceId, UUID userId) {
        // Verify workspace exists
        workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));
        // Verify membership
        WorkspaceMemberId memberId = new WorkspaceMemberId(workspaceId, userId);
        if (!workspaceMemberRepository.existsById(memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this workspace");
        }
        return documentRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
    }

    @Transactional(readOnly = true)
    public Document getDocument(UUID documentId, UUID userId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        UUID workspaceId = document.getWorkspace().getId();
        WorkspaceMemberId memberId = new WorkspaceMemberId(workspaceId, userId);
        if (!workspaceMemberRepository.existsById(memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this workspace");
        }
        return document;
    }

    private boolean isSupportedContentType(String contentType, String filename) {
        if ("application/pdf".equalsIgnoreCase(contentType)) {
            return true;
        }
        if ("text/plain".equalsIgnoreCase(contentType)) {
            return true;
        }
        if ("text/markdown".equalsIgnoreCase(contentType)) {
            return true;
        }
        return filename != null && filename.toLowerCase().endsWith(".md");
    }
}
