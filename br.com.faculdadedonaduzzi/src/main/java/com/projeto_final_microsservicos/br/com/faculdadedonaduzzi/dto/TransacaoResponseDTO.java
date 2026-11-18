package com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Resultado persistido da inferência do modelo antifraude")
public record TransacaoResponseDTO(
    @Schema(description = "Identificador da transação analisada", example = "TX-987654")
    String idTransacao,

    @Schema(description = "Identificador do cliente ou usuário", example = "USR-4321")
    String idUsuario,

    @Schema(description = "Decisão do modelo: FRAUDE quando scoreFraude >= limiar", allowableValues = {"FRAUDE", "APROVADO"}, example = "FRAUDE")
    String status,

    @Schema(description = "Score retornado pelo modelo, entre 0 e 1 (não é probabilidade calibrada)", example = "0.9999999999964237")
    Double scoreFraude,

    @Schema(description = "Limiar de decisão salvo no artefato do modelo, sem arredondamento", example = "0.9999922476121025")
    Double limiar,

    @Schema(description = "Identificador da versão do modelo", example = "ulb-logreg-v1")
    String modelo,

    @Schema(description = "Instante em que a análise foi concluída", example = "2026-11-10T14:30:01Z")
    Instant analisadaEm
) {
}
