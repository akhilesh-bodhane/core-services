package org.egov.filestore.domain.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.egov.filestore.domain.exception.InvalidFileUploadException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class FileUploadValidator {

    // configurable comma-separated list of allowed extensions (lowercase, without dot)
    @Value("${filestore.allowed.extensions:pdf,png,jpg,jpeg,txt,doc,docx,xls,xlsx,csv}")
    private String allowedExtensionsConfig;

    private static final Set<String> DEFAULT_EXECUTABLE_EXTENSIONS = Collections
            .unmodifiableSet(new HashSet<>(Arrays.asList("exe", "sh", "bat", "cmd", "msi", "php", "pl", "py", "jar", "cgi", "jsp", "asp", "aspx")));

    private volatile Set<String> allowedExtensions;

    private volatile Map<String, Set<String>> extensionToMime;

    public FileUploadValidator() {
        // lazy init in validate
    }

    private void initIfNeeded() {
        if (allowedExtensions == null) {
            synchronized (this) {
                if (allowedExtensions == null) {
                    allowedExtensions = Arrays.stream(allowedExtensionsConfig.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .map(String::toLowerCase)
                            .collect(Collectors.toSet());

                    extensionToMime = new HashMap<>();
                    // Basic mapping — extend as required
                    extensionToMime.put("pdf", Collections.singleton("application/pdf"));
                    extensionToMime.put("png", Collections.singleton("image/png"));
                    extensionToMime.put("jpg", Collections.singleton("image/jpeg"));
                    extensionToMime.put("jpeg", Collections.singleton("image/jpeg"));
                    extensionToMime.put("txt", Collections.singleton("text/plain"));
                    extensionToMime.put("csv", Collections.singleton("text/csv"));
                    extensionToMime.put("doc", Collections.singleton("application/msword"));
                    extensionToMime.put("docx", Collections.singleton("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
                    extensionToMime.put("xls", Collections.singleton("application/vnd.ms-excel"));
                    extensionToMime.put("xlsx", Collections.singleton("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
                }
            }
        }
    }

    public void validate(MultipartFile file) {
        initIfNeeded();

        if (file == null || file.isEmpty())
            throw new InvalidFileUploadException("Empty file provided");

        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || originalFileName.trim().isEmpty())
            throw new InvalidFileUploadException("File name is required");

        // Prevent path traversal or suspicious characters
        if (originalFileName.contains("..") || originalFileName.contains("/") || originalFileName.contains("\\")
                || originalFileName.contains(";")) {
            throw new InvalidFileUploadException("Invalid file name");
        }

        String lower = originalFileName.toLowerCase();
        String extension = null;
        int lastDot = lower.lastIndexOf('.');
        if (lastDot > 0 && lastDot < lower.length() - 1) {
            extension = lower.substring(lastDot + 1);
        }

        if (extension == null || !allowedExtensions.contains(extension)) {
            throw new InvalidFileUploadException("File extension not allowed: " + originalFileName);
        }

        
        String[] parts = lower.split("\\.");
        if (parts.length > 2) {
            for (int i = 1; i < parts.length - 1; i++) {
                if (DEFAULT_EXECUTABLE_EXTENSIONS.contains(parts[i])) {
                    throw new InvalidFileUploadException("Suspicious double extension in file name: " + originalFileName);
                }
            }
        }

        String detectedMime = null;

        try (InputStream is = file.getInputStream()) {
            detectedMime = URLConnection.guessContentTypeFromStream(is);
        } catch (IOException e) {
            throw new InvalidFileUploadException("Unable to read file for type validation", e);
        }

        if (detectedMime == null) {
            detectedMime = file.getContentType();
        }

        if (detectedMime == null) {
            throw new InvalidFileUploadException(
                    "Unable to determine file content type for: " + originalFileName);
        }

        
        final String finalDetectedMime = detectedMime;

        Set<String> expectedMimes = extensionToMime.get(extension);

        if (expectedMimes != null && !expectedMimes.isEmpty()) {
            boolean match = expectedMimes.stream()
                    .anyMatch(m -> finalDetectedMime.toLowerCase().startsWith(m));

            if (!match) {
                throw new InvalidFileUploadException(
                        "File content does not match extension. Detected: "
                                + finalDetectedMime + " for file: " + originalFileName);
            }
        }

        
    }

    public void validate(Iterable<MultipartFile> files) {
        for (MultipartFile f : files) {
            validate(f);
        }
    }
}
