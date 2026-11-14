package com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.service;

import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.dto.TransacaoRequestDTO;
import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.dto.TransacaoResponseDTO;
import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.exception.AnaliseNaoEncontradaException;
import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.model.AnaliseTransacao;
import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.repository.TransacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransacaoServiceTest {

    private TransacaoRepository repository;
    private TransacaoService service;

    @BeforeEach
    void setUp() {
        repository = mock(TransacaoRepository.class);
        service = new TransacaoService(repository);
    }

    @Test
    void deveMarcarComoSuspeitaUmaTransacaoDeValorElevado() {
        TransacaoRequestDTO request = request("TX-1", "15000.00");
        when(repository.findByIdTransacao("TX-1")).thenReturn(Optional.empty());
        when(repository.save(any(AnaliseTransacao.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransacaoResponseDTO response = service.analisarTransacao(request);

        assertEquals("TX-1", response.idTransacao());
        assertEquals("SUSPEITO", response.status());
        assertEquals(85, response.scoreRisco());
        verify(repository).save(any(AnaliseTransacao.class));
    }

    @Test
    void deveReutilizarAnaliseExistenteParaAMesmaTransacao() {
        AnaliseTransacao existente = new AnaliseTransacao(
                "TX-2", "USR-1", new BigDecimal("50.00"), "Toledo - PR",
                Instant.parse("2026-11-10T12:00:00Z"), "PIX", "DEV-1", "LOJA-1",
                "APROVADO", 10, "Transação dentro do padrão regular", Instant.now()
        );
        when(repository.findByIdTransacao("TX-2")).thenReturn(Optional.of(existente));

        TransacaoResponseDTO response = service.analisarTransacao(request("TX-2", "50.00"));

        assertEquals("APROVADO", response.status());
        verify(repository, never()).save(any(AnaliseTransacao.class));
    }

    @Test
    void deveFalharAoBuscarAnaliseInexistente() {
        when(repository.findByIdTransacao("INEXISTENTE")).thenReturn(Optional.empty());

        assertThrows(
                AnaliseNaoEncontradaException.class,
                () -> service.buscarPorIdTransacao("INEXISTENTE")
        );
    }

    private TransacaoRequestDTO request(String id, String valor) {
        return new TransacaoRequestDTO(
                id,
                "USR-1",
                new BigDecimal(valor),
                "Toledo - PR",
                Instant.parse("2026-11-10T12:00:00Z"),
                "PIX",
                "DEV-1",
                "LOJA-1"
        );
    }
}
