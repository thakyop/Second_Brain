package com.Second_Brain.Second_Brain.Document;

import com.Second_Brain.Second_Brain.Document.storage.FileStorageService;
import com.Second_Brain.Second_Brain.User.User;
import com.Second_Brain.Second_Brain.User.UserRepository;
import com.Second_Brain.Second_Brain.workspace.workspace;
import com.Second_Brain.Second_Brain.workspace.WorkspaceMemberId;
import com.Second_Brain.Second_Brain.workspace.WorkspaceMemberRepository;
import com.Second_Brain.Second_Brain.workspace.WorkspaceRepository;

import jakarta.persistence.EntityManager;

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

    public DocumentService(
            DocumentRepository documentRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            UserRepository userRepository,
            FileStorageService fileStorageService,
            EntityManager entityManager
    ) {
        this.documentRepository = documentRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
        this.entityManager = entityManager;
    }

    @Transactional
    public Document upload(
            UUID workspaceId,
            UUID userId,
            MultipartFile file
    ) {

        // 1. Validate file
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "File is required"
            );
        }

        // 2. Find workspace
        workspace workspace = workspaceRepository
                .findById(workspaceId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Workspace not found"
                        )
                );

        // 3. Verify workspace membership
        WorkspaceMemberId memberId =
                new WorkspaceMemberId(workspaceId, userId);

        boolean isMember =
                workspaceMemberRepository.existsById(memberId);

        if (!isMember) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not a member of this workspace"
            );
        }

        // 4. Find authenticated user
        User user = userRepository
                .findById(userId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "User not found"
                        )
                );

        // 5. Validate file type
        String contentType = file.getContentType();

        if (!isSupportedContentType(
                contentType,
                file.getOriginalFilename()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported file type"
            );
        }

        // 6. Validate filename
        String originalFilename = file.getOriginalFilename();

        if (originalFilename == null ||
                originalFilename.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Filename is required"
            );
        }

        // 7. Create document
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

            storagePath = fileStorageService.store(
                    workspaceId,
                    documentId,
                    file
            );

        } catch (IOException e) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to store file",
                    e
            );
        }

        // 9. Save storage path
        document.setStoragePath(storagePath);

        // 10. Persist document
        try {

            entityManager.persist(document);
            entityManager.flush();

            return document;

        } catch (RuntimeException e) {

            // Delete physical file if database persistence fails
            try {
                fileStorageService.delete(storagePath);
            } catch (IOException cleanupException) {
                // Do not hide original exception
            }

            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<Document> listDocuments(
            UUID workspaceId,
            UUID userId
    ) {

        // 1. Verify workspace exists
        workspaceRepository
                .findById(workspaceId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Workspace not found"
                        )
                );

        // 2. Verify workspace membership
        WorkspaceMemberId memberId =
                new WorkspaceMemberId(workspaceId, userId);

        boolean isMember =
                workspaceMemberRepository.existsById(memberId);

        if (!isMember) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not a member of this workspace"
            );
        }

        // 3. Return documents newest first
        return documentRepository
                .findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
    }

    @Transactional(readOnly = true)
    public Document getDocument(
            UUID documentId,
            UUID userId
    ) {

        // 1. Find document
        Document document = documentRepository
                .findById(documentId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Document not found"
                        )
                );

        // 2. Get workspace
        UUID workspaceId =
                document.getWorkspace().getId();

        // 3. Verify workspace membership
        WorkspaceMemberId memberId =
                new WorkspaceMemberId(workspaceId, userId);

        boolean isMember =
                workspaceMemberRepository.existsById(memberId);

        if (!isMember) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not a member of this workspace"
            );
        }

        return document;
    }

    private boolean isSupportedContentType(
            String contentType,
            String filename
    ) {

        if ("application/pdf".equalsIgnoreCase(contentType)) {
            return true;
        }

        if ("text/plain".equalsIgnoreCase(contentType)) {
            return true;
        }

        if ("text/markdown".equalsIgnoreCase(contentType)) {
            return true;
        }

        // Some clients send Markdown as application/octet-stream
        return filename != null &&
                filename.toLowerCase().endsWith(".md");
    }
}