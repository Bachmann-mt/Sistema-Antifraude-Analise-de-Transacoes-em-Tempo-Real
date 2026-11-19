package com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.ml;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Saída de sucesso do predict.py (ml/contracts/output.schema.json). */
public record ResultadoInferencia(
        String status,
        @JsonProperty("score_fraude") Double scoreFraude,
        Double limiar,
        String modelo
) {
}
