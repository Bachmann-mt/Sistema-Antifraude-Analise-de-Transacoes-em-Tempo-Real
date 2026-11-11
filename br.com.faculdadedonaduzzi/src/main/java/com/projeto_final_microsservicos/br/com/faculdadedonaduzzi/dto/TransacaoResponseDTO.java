package com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TransacaoResponseDTO(
    @Schema(description = "Status retornado pela análise de antifraude", example = "APROVADO")
    String status,

    @Schema(description = "Pontuação de risco de 0 a 100", example = "15")
    Integer scoreRisco,

    @Schema(description = "Mensagem explicativa da decisão", example = "Transação analisada com sucesso")
    String mensagem
) {}