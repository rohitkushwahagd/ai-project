package dev.rohit.rag;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.dao.DataAccessException;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ProblemDetail> unavailable(DataAccessException ex) {
        return ResponseEntity.status(503).body(ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,"Storage temporarily unavailable"));
    }
}
