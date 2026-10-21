package com.truthguard.exception;

import com.truthguard.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDocumentNotFound(DocumentNotFoundException ex, HttpServletRequest request) {
        log.warn("Document not found: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.of(HttpStatus.NOT_FOUND.value(), "NotFound", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(DocumentVersionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleVersionNotFound(DocumentVersionNotFoundException ex, HttpServletRequest request) {
        log.warn("Document version not found: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.of(HttpStatus.NOT_FOUND.value(), "NotFound", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(DuplicateContentException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateContent(DuplicateContentException ex, HttpServletRequest request) {
        log.warn("Duplicate content rejected: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.of(HttpStatus.CONFLICT.value(), "DuplicateContent", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(DuplicateDocumentTitleException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateTitle(DuplicateDocumentTitleException ex, HttpServletRequest request) {
        log.warn("Duplicate title rejected: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.of(HttpStatus.CONFLICT.value(), "DuplicateTitle", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(InvalidSupersessionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSupersession(InvalidSupersessionException ex, HttpServletRequest request) {
        log.warn("Invalid supersession request: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "InvalidSupersession", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(EmptyFileException.class)
    public ResponseEntity<ErrorResponse> handleEmptyFile(EmptyFileException ex, HttpServletRequest request) {
        log.warn("Empty file upload rejected: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "EmptyFile", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(UnsupportedFileFormatException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedFormat(UnsupportedFileFormatException ex, HttpServletRequest request) {
        log.warn("Unsupported file format rejected: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.of(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), "UnsupportedFormat", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(error);
    }

    @ExceptionHandler(DocumentParsingException.class)
    public ResponseEntity<ErrorResponse> handleDocumentParsing(DocumentParsingException ex, HttpServletRequest request) {
        log.warn("Document parsing failed: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.of(HttpStatus.UNPROCESSABLE_ENTITY.value(), "DocumentParsingError", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    @ExceptionHandler(DocumentIngestionException.class)
    public ResponseEntity<ErrorResponse> handleDocumentIngestion(DocumentIngestionException ex, HttpServletRequest request) {
        log.warn("Document ingestion failed: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.of(HttpStatus.UNPROCESSABLE_ENTITY.value(), "IngestionFailed", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(org.springframework.web.multipart.MaxUploadSizeExceededException ex, HttpServletRequest request) {
        log.warn("Upload size exceeded: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.of(HttpStatus.PAYLOAD_TOO_LARGE.value(), "FileTooLarge", "File size exceeds the configured maximum upload limit", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Illegal argument: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "BadRequest", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        log.warn("Validation failed for request to {}: {}", request.getRequestURI(), fieldErrors);
        ErrorResponse error = ErrorResponse.of(
            HttpStatus.BAD_REQUEST.value(),
            "ValidationFailed",
            "Request validation failed for one or more fields",
            request.getRequestURI(),
            fieldErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled server exception processing request to {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        ErrorResponse error = ErrorResponse.of(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "InternalServerError",
            "An unexpected server error occurred",
            request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
