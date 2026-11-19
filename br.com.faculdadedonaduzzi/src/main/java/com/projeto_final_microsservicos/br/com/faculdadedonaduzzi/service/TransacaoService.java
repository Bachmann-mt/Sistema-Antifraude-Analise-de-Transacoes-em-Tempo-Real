package com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.service;

import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.dto.TransacaoRequestDTO;
import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.dto.TransacaoResponseDTO;
import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.exception.AnaliseNaoEncontradaException;
import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.ml.ModeloFraudeClient;
import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.ml.ResultadoInferencia;
import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.model.AnaliseTransacao;
import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.repository.TransacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class TransacaoService {

    private final TransacaoRepository transacaoRepository;
    private final ModeloFraudeClient modeloFraudeClient;

    public TransacaoService(TransacaoRepository transacaoRepository, ModeloFraudeClient modeloFraudeClient) {
        this.transacaoRepository = transacaoRepository;
        this.modeloFraudeClient = modeloFraudeClient;
    }

    /** Sem transação aberta durante o subprocesso; o save do repositório é transacional. */
    public TransacaoResponseDTO analisarTransacao(TransacaoRequestDTO request) {
        return transacaoRepository.findByIdTransacao(request.idTransacao())
                .map(this::toResponse)
                .orElseGet(() -> executarAnalise(request));
    }

    @Transactional(readOnly = true)
    public TransacaoResponseDTO buscarPorIdTransacao(String idTransacao) {
        return transacaoRepository.findByIdTransacao(idTransacao)
                .map(this::toResponse)
                .orElseThrow(() -> new AnaliseNaoEncontradaException(idTransacao));
    }

    private TransacaoResponseDTO executarAnalise(TransacaoRequestDTO request) {
        ResultadoInferencia resultado = modeloFraudeClient.inferir(request.features());

        AnaliseTransacao analise = new AnaliseTransacao(
                request.idTransacao(),
                request.idUsuario(),
                resultado.status(),
                resultado.scoreFraude(),
                resultado.limiar(),
                resultado.modelo(),
                Instant.now()
        );

        return toResponse(transacaoRepository.save(analise));
    }

    private TransacaoResponseDTO toResponse(AnaliseTransacao analise) {
        return new TransacaoResponseDTO(
                analise.getIdTransacao(),
                analise.getIdUsuario(),
                analise.getStatus(),
                analise.getScoreFraude(),
                analise.getLimiar(),
                analise.getModelo(),
                analise.getAnalisadaEm()
        );
    }
}
