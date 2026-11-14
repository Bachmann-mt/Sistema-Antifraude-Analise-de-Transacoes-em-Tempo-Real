package com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Dados da transação usados pelo modelo de detecção de fraude")
public record TransacaoRequestDTO(
    @NotBlank(message = "O identificador da transação é obrigatório")
    @Size(max = 100, message = "O identificador da transação deve ter no máximo 100 caracteres")
    @Schema(description = "Identificador único da transação", example = "TX-987654", requiredMode = Schema.RequiredMode.REQUIRED)
    String idTransacao,

    @NotBlank(message = "O identificador do usuário é obrigatório")
    @Size(max = 100, message = "O identificador do usuário deve ter no máximo 100 caracteres")
    @Schema(description = "Identificador do cliente ou usuário", example = "USR-4321", requiredMode = Schema.RequiredMode.REQUIRED)
    String idUsuario,

    @NotNull(message = "O valor da transação é obrigatório")
    @DecimalMin(value = "0.01", message = "O valor da transação deve ser maior que zero")
    @Digits(integer = 17, fraction = 2, message = "O valor da transação deve possuir no máximo duas casas decimais")
    @Schema(description = "Valor monetário da transação", example = "1250.00", requiredMode = Schema.RequiredMode.REQUIRED)
    BigDecimal valor,

    @NotBlank(message = "A localização da transação é obrigatória")
    @Size(max = 150, message = "A localização deve ter no máximo 150 caracteres")
    @Schema(description = "Localização de origem da transação", example = "São Paulo - SP", requiredMode = Schema.RequiredMode.REQUIRED)
    String localizacao,

    @PastOrPresent(message = "A data e hora da transação não pode estar no futuro")
    @Schema(description = "Instante em que a transação ocorreu, no padrão ISO-8601", example = "2026-11-10T14:30:00Z")
    Instant dataHora,

    @Size(max = 50, message = "O tipo da transação deve ter no máximo 50 caracteres")
    @Schema(description = "Canal ou tipo da transação", example = "PIX")
    String tipoTransacao,

    @Size(max = 100, message = "O identificador do dispositivo deve ter no máximo 100 caracteres")
    @Schema(description = "Identificador do dispositivo que originou a transação", example = "DEVICE-A1B2C3")
    String dispositivoId,

    @Size(max = 100, message = "O identificador do estabelecimento deve ter no máximo 100 caracteres")
    @Schema(description = "Identificador do favorecido ou estabelecimento", example = "LOJA-123")
    String estabelecimentoId
) {
    public TransacaoRequestDTO(String idTransacao, String idUsuario, BigDecimal valor, String localizacao) {
        this(idTransacao, idUsuario, valor, localizacao, null, null, null, null);
    }
}
