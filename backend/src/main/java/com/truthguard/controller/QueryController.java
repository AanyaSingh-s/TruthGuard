package com.truthguard.controller;

import com.truthguard.dto.query.QuestionQueryRequest;
import com.truthguard.dto.query.QuestionQueryResponse;
import com.truthguard.service.QueryOrchestrationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/queries")
@CrossOrigin(origins = "${truthguard.cors.allowed-origins:http://localhost:3000,http://localhost:5173,http://127.0.0.1:3000,http://127.0.0.1:5173}")
public class QueryController {

    private static final Logger log = LoggerFactory.getLogger(QueryController.class);

    private final QueryOrchestrationService orchestrationService;

    public QueryController(QueryOrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }

    @PostMapping
    public ResponseEntity<QuestionQueryResponse> askQuestion(@Valid @RequestBody QuestionQueryRequest request) {
        log.info("REST request to ask question: '{}' (correlationId={})", request.getQuestion(), request.getCorrelationId());
        QuestionQueryResponse response = orchestrationService.processQuery(request);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/{queryId}")
    public ResponseEntity<QuestionQueryResponse> getQuery(@PathVariable UUID queryId) {
        log.debug("REST request to get query details for ID: {}", queryId);
        return ResponseEntity.ok(orchestrationService.getQuery(queryId));
    }

    @GetMapping
    public ResponseEntity<List<QuestionQueryResponse>> listQueries() {
        log.debug("REST request to list recent queries");
        return ResponseEntity.ok(orchestrationService.listQueries());
    }
}
