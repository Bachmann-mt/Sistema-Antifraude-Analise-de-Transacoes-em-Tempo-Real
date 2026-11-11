package com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TransacaoRequestDTO(
    @Schema(description = "Identificador único da transação", example = "TX-987654")
    String idTransacao,

    @Schema(description = "Identificador do cliente ou usuário", example = "USR-4321")
    String idUsuario,

    @Schema(description = "Valor monetário da transação", example = "1250.00")
    Double valor,

    @Schema(description = "Localização de origem da transação", example = "São Paulo - SP")
    String localizacao
) {}