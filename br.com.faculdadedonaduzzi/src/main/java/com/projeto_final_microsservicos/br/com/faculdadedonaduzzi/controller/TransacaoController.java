package com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.controller;

import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.dto.ErroResponseDTO;
import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.dto.TransacaoRequestDTO;
import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.dto.TransacaoResponseDTO;
import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.service.TransacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transacoes")
@Tag(name = "Antifraude", description = "Endpoints de análise e detecção de fraudes em transações")
public class TransacaoController {

    private final TransacaoService transacaoService;

    public TransacaoController(TransacaoService transacaoService) {
        this.transacaoService = transacaoService;
    }

    @PostMapping("/analisar")
    @Operation(
            summary = "Analisar Transação",
            description = "Recebe os dados de uma transação financeira em tempo real e indica o status de fraude."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Análise processada com sucesso",
                    content = @Content(schema = @Schema(implementation = TransacaoResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Dados da transação mal formatados ou ausentes",
                    content = @Content(schema = @Schema(implementation = ErroResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Erro interno ao processar a análise",
                    content = @Content(schema = @Schema(implementation = ErroResponseDTO.class))
            )
    })
    public ResponseEntity<TransacaoResponseDTO> analisar(@Valid @RequestBody TransacaoRequestDTO dto) {
        TransacaoResponseDTO resposta = transacaoService.analisarTransacao(dto);
        return ResponseEntity.ok(resposta);
    }

    @GetMapping("/{idTransacao}")
    @Operation(
            summary = "Consultar análise",
            description = "Retorna o resultado persistido de uma análise pelo identificador da transação."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Análise encontrada",
                    content = @Content(schema = @Schema(implementation = TransacaoResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Análise não encontrada",
                    content = @Content(schema = @Schema(implementation = ErroResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Erro interno ao consultar a análise",
                    content = @Content(schema = @Schema(implementation = ErroResponseDTO.class))
            )
    })
    public ResponseEntity<TransacaoResponseDTO> buscar(
            @Parameter(description = "Identificador único da transação", example = "TX-987654", required = true)
            @PathVariable String idTransacao
    ) {
        return ResponseEntity.ok(transacaoService.buscarPorIdTransacao(idTransacao));
    }
}
