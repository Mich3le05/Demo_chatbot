package com.chatbot.chatbot_backend.document;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/document")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @Operation(summary = "Carica un documento PDF o Excel")
    @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> uploadDocument(
            @RequestParam("file") MultipartFile file) throws IOException {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Il file non può essere vuoto");
        }
        return ResponseEntity.ok(documentService.processFile(file));
    }

    @Operation(summary = "Elimina un documento dal VectorStore")
    @DeleteMapping("/{filename}")
    public ResponseEntity<Void> deleteDocument(@PathVariable String filename) {
        documentService.deleteDocument(filename);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Lista documenti indicizzati")
    @GetMapping("/list")
    public ResponseEntity<List<String>> listDocuments() {
        return ResponseEntity.ok(documentService.getIndexedDocuments());
    }
}