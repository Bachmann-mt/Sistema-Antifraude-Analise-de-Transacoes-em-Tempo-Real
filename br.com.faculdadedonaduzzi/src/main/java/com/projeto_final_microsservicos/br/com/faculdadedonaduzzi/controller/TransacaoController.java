package com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.controller;

import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.dto.ErroResponseDTO;
import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.dto.TransacaoRequestDTO;
import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.dto.TransacaoResponseDTO;
import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.service.TransacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
            description = "Recebe os identificadores do histórico e as features Time, V1 a V28 e Amount, "
                    + "executa o modelo de detecção de fraude e persiste o resultado real da inferência. "
                    + "Se a transação já foi analisada, retorna a análise salva sem nova inferência."
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    schema = @Schema(implementation = TransacaoRequestDTO.class),
                    examples = {
                            @ExampleObject(
                                    name = "fraude",
                                    summary = "Amostra real do dataset rotulada como fraude",
                                    value = """
                                    {
                                      "idTransacao": "TX-ML-FRAUDE-001",
                                      "idUsuario": "USR-001",
                                      "features": {
                                        "Time": 146022.0,
                                        "V1": 0.908636658181293,
                                        "V2": 2.84902401493181,
                                        "V3": -5.64734296336341,
                                        "V4": 6.00941477810058,
                                        "V5": 0.216656395035464,
                                        "V6": -2.39701442397293,
                                        "V7": -1.81930788593969,
                                        "V8": 0.338526987742346,
                                        "V9": -2.81988277297887,
                                        "V10": -4.06309810827338,
                                        "V11": 2.94119009271712,
                                        "V12": -6.15136219091534,
                                        "V13": -1.98952853525001,
                                        "V14": -9.15095100562097,
                                        "V15": -0.604289998808266,
                                        "V16": -1.95229039984463,
                                        "V17": -2.89255533222526,
                                        "V18": -0.912057960709434,
                                        "V19": -1.56373996709776,
                                        "V20": 0.241921294746754,
                                        "V21": 0.407260461266497,
                                        "V22": -0.397434852530952,
                                        "V23": -0.0800058534116311,
                                        "V24": -0.168596545191964,
                                        "V25": 0.465058497798457,
                                        "V26": 0.210509756241616,
                                        "V27": 0.648704799002062,
                                        "V28": 0.360224330288316,
                                        "Amount": 1.18
                                      }
                                    }
                                    """
                            ),
                            @ExampleObject(
                                    name = "legitima",
                                    summary = "Amostra real do dataset rotulada como legítima",
                                    value = """
                                    {
                                      "idTransacao": "TX-ML-LEGITIMA-001",
                                      "idUsuario": "USR-002",
                                      "features": {
                                        "Time": 145234.0,
                                        "V1": 0.142992201953079,
                                        "V2": 1.06540505706892,
                                        "V3": -0.407431623326328,
                                        "V4": -0.562354885314715,
                                        "V5": 1.00179051170196,
                                        "V6": -0.726233416127765,
                                        "V7": 0.987183491731403,
                                        "V8": -0.108878671343682,
                                        "V9": -0.115907938884984,
                                        "V10": -0.855008993686116,
                                        "V11": -0.591849419202952,
                                        "V12": 0.135208553083701,
                                        "V13": 0.466587931587459,
                                        "V14": -1.10494580299518,
                                        "V15": -0.239877723428563,
                                        "V16": 0.260985815816867,
                                        "V17": 0.384325447655063,
                                        "V18": -0.350801438237199,
                                        "V19": -0.290875252783854,
                                        "V20": 0.0398899468832053,
                                        "V21": -0.333581863108201,
                                        "V22": -0.779618333308082,
                                        "V23": 0.089250169042436,
                                        "V24": 0.512206931953748,
                                        "V25": -0.416654233038179,
                                        "V26": 0.117893068769471,
                                        "V27": 0.228983572496098,
                                        "V28": 0.0843445937999741,
                                        "Amount": 1.79
                                      }
                                    }
                                    """
                            )
                    }
            )
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Análise processada com sucesso",
                    content = @Content(schema = @Schema(implementation = TransacaoResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Identificadores ou features ausentes, inválidos ou recusados pelo modelo",
                    content = @Content(schema = @Schema(implementation = ErroResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Modelo de inferência indisponível; nenhuma análise é persistida",
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
            description = "Retorna do histórico o resultado persistido da inferência pelo identificador da transação."
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
