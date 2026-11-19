package com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Identificadores do histórico e features da transação enviadas ao modelo de detecção de fraude")
public record TransacaoRequestDTO(
    @NotBlank(message = "O identificador da transação é obrigatório")
    @Size(max = 100, message = "O identificador da transação deve ter no máximo 100 caracteres")
    @Schema(description = "Identificador único da transação no histórico", example = "TX-987654", requiredMode = Schema.RequiredMode.REQUIRED)
    String idTransacao,

    @NotBlank(message = "O identificador do usuário é obrigatório")
    @Size(max = 100, message = "O identificador do usuário deve ter no máximo 100 caracteres")
    @Schema(description = "Identificador do cliente ou usuário", example = "USR-4321", requiredMode = Schema.RequiredMode.REQUIRED)
    String idUsuario,

    @Valid
    @NotNull(message = "As features da transação são obrigatórias")
    @Schema(description = "Time, V1 a V28 e Amount, enviados ao modelo sem alteração", requiredMode = Schema.RequiredMode.REQUIRED)
    FeaturesTransacaoDTO features
) {
}
