package com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.controller;

import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.dto.TransacaoRequestDTO;
import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.dto.TransacaoResponseDTO;
import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.service.TransacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
            @ApiResponse(responseCode = "200", description = "Análise processada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados da transação mal formatados ou ausentes"),
            @ApiResponse(responseCode = "500", description = "Erro interno ao processar a análise")
    })
    public ResponseEntity<TransacaoResponseDTO> analisar(@RequestBody TransacaoRequestDTO dto) {
        TransacaoResponseDTO resposta = transacaoService.analisarTransacao(dto);
        return ResponseEntity.ok(resposta);
    }
}