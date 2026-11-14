package com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

@Schema(description = "Detalhes de um erro retornado pela API")
public record ErroResponseDTO(
        @Schema(example = "400") int status,
        @Schema(example = "Requisição inválida") String erro,
        @Schema(example = "/api/v1/transacoes/analisar") String caminho,
        @Schema(description = "Erros de validação por campo") Map<String, String> campos,
        @Schema(example = "2026-11-10T14:30:00Z") Instant timestamp
) {
}
