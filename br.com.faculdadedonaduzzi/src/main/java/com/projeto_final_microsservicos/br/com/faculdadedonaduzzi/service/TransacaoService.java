package com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.service;

import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.dto.TransacaoRequestDTO;
import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.dto.TransacaoResponseDTO;
import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.exception.AnaliseNaoEncontradaException;
import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.model.AnaliseTransacao;
import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.repository.TransacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class TransacaoService {

    private static final BigDecimal LIMITE_VALOR_ELEVADO = new BigDecimal("10000.00");
    private final TransacaoRepository transacaoRepository;

    public TransacaoService(TransacaoRepository transacaoRepository) {
        this.transacaoRepository = transacaoRepository;
    }

    @Transactional
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
        int scoreRisco = calcularScore(request);
        String status = scoreRisco >= 70 ? "SUSPEITO" : "APROVADO";
        String mensagem = scoreRisco >= 70
                ? "Alerta: transação com valor elevado"
                : "Transação dentro do padrão regular";
        Instant analisadaEm = Instant.now();

        AnaliseTransacao analise = new AnaliseTransacao(
                request.idTransacao(),
                request.idUsuario(),
                request.valor(),
                request.localizacao(),
                request.dataHora(),
                request.tipoTransacao(),
                request.dispositivoId(),
                request.estabelecimentoId(),
                status,
                scoreRisco,
                mensagem,
                analisadaEm
        );

        return toResponse(transacaoRepository.save(analise));
    }

    /* Ponto de integração a ser substituído pela inferência do modelo da equipe de ML. */
    private int calcularScore(TransacaoRequestDTO request) {
        return request.valor().compareTo(LIMITE_VALOR_ELEVADO) > 0 ? 85 : 10;
    }

    private TransacaoResponseDTO toResponse(AnaliseTransacao analise) {
        return new TransacaoResponseDTO(
                analise.getIdTransacao(),
                analise.getStatus(),
                analise.getScoreRisco(),
                analise.getMensagem(),
                analise.getAnalisadaEm()
        );
    }
}
