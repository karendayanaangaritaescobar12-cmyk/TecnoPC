package com.tecnopc.admin;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(org.springframework.dao.TransientDataAccessException.class)
    ResponseEntity<?> temporaryDatabase(Exception ignored) {
        return unavailable();
    }
    @ExceptionHandler({org.springframework.dao.DataAccessResourceFailureException.class,org.springframework.dao.RecoverableDataAccessException.class})
    ResponseEntity<?> disconnectedDatabase(Exception ignored) {
        return unavailable();
    }
    @ExceptionHandler({org.springframework.transaction.CannotCreateTransactionException.class,
            org.springframework.transaction.TransactionTimedOutException.class})
    ResponseEntity<?> transactionUnavailable(Exception ignored){return unavailable();}
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ResponseEntity<?> conflict(Exception ignored) {
        return ResponseEntity.status(409).body(Map.of("mensaje","Los datos cambiaron o no cumplen las restricciones. Actualiza antes de continuar."));
    }
    private ResponseEntity<?> unavailable(){
        return ResponseEntity.status(503).header("Retry-After","5").header("Cache-Control","no-store")
                .body(Map.of("mensaje","El servicio de datos no está disponible temporalmente. Si enviaste una compra, comprueba su estado antes de repetirla."));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<?> invalid(Exception ignored) {
        return ResponseEntity.unprocessableEntity().body(Map.of("mensaje", "Revisa el formato y los campos de la solicitud"));
    }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> status(ResponseStatusException ex) {
        var response=ResponseEntity.status(ex.getStatusCode()).header("Cache-Control","no-store");
        if(ex.getStatusCode().value()==503)response.header("Retry-After","5");
        return response.body(Map.of("mensaje", ex.getReason() == null ? "Solicitud no disponible" : ex.getReason()));
    }
}
