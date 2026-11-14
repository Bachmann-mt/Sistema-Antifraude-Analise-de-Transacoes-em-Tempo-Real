package com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.exception;

import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.dto.ErroResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponseDTO> tratarValidacao(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> campos = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> campos.putIfAbsent(error.getField(), error.getDefaultMessage()));

        return resposta(HttpStatus.BAD_REQUEST, "Requisição inválida", request, campos);
    }

    @ExceptionHandler(AnaliseNaoEncontradaException.class)
    public ResponseEntity<ErroResponseDTO> tratarNaoEncontrada(
            AnaliseNaoEncontradaException exception,
            HttpServletRequest request
    ) {
        return resposta(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResponseDTO> tratarCorpoInvalido(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return resposta(HttpStatus.BAD_REQUEST, "Corpo da requisição inválido", request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponseDTO> tratarErroInterno(
            Exception exception,
            HttpServletRequest request
    ) {
        LOGGER.error("Erro não tratado ao processar {}", request.getRequestURI(), exception);
        return resposta(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno ao processar a requisição", request, Map.of());
    }

    private ResponseEntity<ErroResponseDTO> resposta(
            HttpStatus status,
            String erro,
            HttpServletRequest request,
            Map<String, String> campos
    ) {
        ErroResponseDTO body = new ErroResponseDTO(
                status.value(),
                erro,
                request.getRequestURI(),
                campos,
                Instant.now()
        );
        return ResponseEntity.status(status).body(body);
    }
}
