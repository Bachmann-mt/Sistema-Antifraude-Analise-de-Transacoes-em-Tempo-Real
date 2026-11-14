package com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Resultado da análise antifraude")
public record TransacaoResponseDTO(
    @Schema(description = "Identificador da transação analisada", example = "TX-987654")
    String idTransacao,

    @Schema(description = "Status retornado pela análise de antifraude", example = "APROVADO")
    String status,

    @Schema(description = "Pontuação de risco de 0 a 100", example = "15")
    Integer scoreRisco,

    @Schema(description = "Mensagem explicativa da decisão", example = "Transação analisada com sucesso")
    String mensagem,

    @Schema(description = "Instante em que a análise foi concluída", example = "2026-11-10T14:30:01Z")
    Instant analisadaEm
) {
}
