package com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * As 30 features exigidas pelo modelo (contrato ml/CONTRACT.md v1).
 * Serializado sem envelope e com os nomes exatos do dataset para o predict.py.
 */
@Schema(description = "Features da transação no formato do dataset ULB/Worldline: Time, V1 a V28 e Amount")
public record FeaturesTransacaoDTO(
    @JsonProperty("Time")
    @NotNull(message = "Time é obrigatório")
    @PositiveOrZero(message = "Time deve ser maior ou igual a zero")
    @Schema(description = "Segundos desde a primeira transação do dataset (não é timestamp Unix)", example = "146022.0", requiredMode = Schema.RequiredMode.REQUIRED)
    Double time,

    @JsonProperty("V1")
    @NotNull(message = "V1 é obrigatório")
    @Schema(description = "Componente PCA anonimizado V1", requiredMode = Schema.RequiredMode.REQUIRED)
    Double v1,

    @JsonProperty("V2")
    @NotNull(message = "V2 é obrigatório")
    @Schema(description = "Componente PCA anonimizado V2", requiredMode = Schema.RequiredMode.REQUIRED)
    Double v2,

    @JsonProperty("V3")
    @NotNull(message = "V3 é obrigatório")
    @Schema(description = "Componente PCA anonimizado V3", requiredMode = Schema.RequiredMode.REQUIRED)
    Double v3,

    @JsonProperty("V4")
    @NotNull(message = "V4 é obrigatório")
    @Schema(description = "Componente PCA anonimizado V4", requiredMode = Schema.RequiredMode.REQUIRED)
    Double v4,

    @JsonProperty("V5")
    @NotNull(message = "V5 é obrigatório")
    @Schema(description = "Componente PCA anonimizado V5", requiredMode = Schema.RequiredMode.REQUIRED)
    Double v5,

    @JsonProperty("V6")
    @NotNull(message = "V6 é obrigatório")
    @Schema(description = "Componente PCA anonimizado V6", requiredMode = Schema.RequiredMode.REQUIRED)
    Double v6,

    @JsonProperty("V7")
    @NotNull(message = "V7 é obrigatório")
    @Schema(description = "Componente PCA anonimizado V7", requiredMode = Schema.RequiredMode.REQUIRED)
    Double v7,

    @JsonProperty("V8")
    @NotNull(message = "V8 é obrigatório")
    @Schema(description = "Componente PCA anonimizado V8", requiredMode = Schema.RequiredMode.REQUIRED)
    Double v8,

    @JsonProperty("V9")
    @NotNull(message = "V9 é obrigatório")
    @Schema(description = "Componente PCA anonimizado V9", requiredMode = Schema.RequiredMode.REQUIRED)
    Double v9,

    @JsonProperty("V10")
    @NotNull(message = "V10 é obrigatório")
    @Schema(description = "Componente PCA anonimizado V10", requiredMode = Schema.RequiredMode.REQUIRED)
    Double v10,

    @JsonProperty("V11")
    @NotNull(message = "V11 é obrigatório")
    @Schema(description = "Componente PCA anonimizado V11", requiredMode = Schema.RequiredMode.REQUIRED)
    Double v11,

    @JsonProperty("V12")
    @NotNull(message = "V12 é obrigatório")
    @Schema(description = "Componente PCA anonimizado V12", requiredMode = Schema.RequiredMode.REQUIRED)
    Double v12,

    @JsonProperty("V13")
    @NotNull(message = "V13 é obrigatório")
    @Schema(description = "Componente PCA anonimizado V13", requiredMode = Schema.RequiredMode.REQUIRED)
    Double v13,

    @JsonProperty("V14")
    @NotNull(message = "V14 é obrigatório")
    @Schema(description = "Componente PCA anonimizado V14", requiredMode = Schema.RequiredMode.REQUIRED)
    Double v14,

    @JsonProperty("V15")
    @NotNull(message = "V15 é obrigatório")
    @Schema(description = "Componente PCA anonimizado V15", requiredMode = Schema.RequiredMode.REQUIRED)
    Double v15,

    @JsonProperty("V16")
    @NotNull(message = "V16 é obrigatório")
    @Schema(description = "Componente PCA anonimizado V16", requiredMode = Schema.RequiredMode.REQUIRED)
    Double v16,

    @JsonProperty("V17")
    @NotNull(message = "V17 é obrigatório")
    @Schema(description = "Componente PCA anonimizado V17", requiredMode = Schema.RequiredMode.REQUIRED)
    Double v17,

    @JsonProperty("V18")
    @NotNull(message = "V18 é obrigatório")
    @Schema(description = "Componente PCA anonimizado V18", requiredMode = Schema.RequiredMode.REQUIRED)
    Double v18,

    @JsonProperty("V19")
    @NotNull(message = "V19 é obrigatório")
    @Schema(description = "Componente PCA anonimizado V19", requiredMode = Schema.RequiredMode.REQUIRED)
    Double v19,

    @JsonProperty("V20")
    @NotNull(message = "V20 é obrigatório")
    @Schema(description = "Componente PCA anonimizado V20", requiredMode = Schema.RequiredMode.REQUIRED)
    Double v20,

    @JsonProperty("V21")
    @NotNull(message = "V21 é obrigatório")
    @Schema(description = "Componente PCA anonimizado V21", requiredMode = Schema.RequiredMode.REQUIRED)
    Double v21,

    @JsonProperty("V22")
    @NotNull(message = "V22 é obrigatório")
    @Schema(description = "Componente PCA anonimizado V22", requiredMode = Schema.RequiredMode.REQUIRED)
    Double v22,

    @JsonProperty("V23")
    @NotNull(message = "V23 é obrigatório")
    @Schema(description = "Componente PCA anonimizado V23", requiredMode = Schema.RequiredMode.REQUIRED)
    Double v23,

    @JsonProperty("V24")
    @NotNull(message = "V24 é obrigatório")
    @Schema(description = "Componente PCA anonimizado V24", requiredMode = Schema.RequiredMode.REQUIRED)
    Double v24,

    @JsonProperty("V25")
    @NotNull(message = "V25 é obrigatório")
    @Schema(description = "Componente PCA anonimizado V25", requiredMode = Schema.RequiredMode.REQUIRED)
    Double v25,

    @JsonProperty("V26")
    @NotNull(message = "V26 é obrigatório")
    @Schema(description = "Componente PCA anonimizado V26", requiredMode = Schema.RequiredMode.REQUIRED)
    Double v26,

    @JsonProperty("V27")
    @NotNull(message = "V27 é obrigatório")
    @Schema(description = "Componente PCA anonimizado V27", requiredMode = Schema.RequiredMode.REQUIRED)
    Double v27,

    @JsonProperty("V28")
    @NotNull(message = "V28 é obrigatório")
    @Schema(description = "Componente PCA anonimizado V28", requiredMode = Schema.RequiredMode.REQUIRED)
    Double v28,

    @JsonProperty("Amount")
    @NotNull(message = "Amount é obrigatório")
    @PositiveOrZero(message = "Amount deve ser maior ou igual a zero")
    @Schema(description = "Valor da transação na escala do dataset", example = "1.18", requiredMode = Schema.RequiredMode.REQUIRED)
    Double amount
) {
}
