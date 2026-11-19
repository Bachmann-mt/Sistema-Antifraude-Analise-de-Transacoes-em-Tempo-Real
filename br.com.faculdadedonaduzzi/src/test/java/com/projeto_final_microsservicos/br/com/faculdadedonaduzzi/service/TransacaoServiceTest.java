package com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.service;

import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.dto.FeaturesTransacaoDTO;
import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.dto.TransacaoRequestDTO;
import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.dto.TransacaoResponseDTO;
import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.exception.AnaliseNaoEncontradaException;
import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.exception.InferenciaException;
import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.ml.ModeloFraudeClient;
import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.ml.ResultadoInferencia;
import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.model.AnaliseTransacao;
import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.repository.TransacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TransacaoServiceTest {

    private TransacaoRepository repository;
    private ModeloFraudeClient modelo;
    private TransacaoService service;

    @BeforeEach
    void setUp() {
        repository = mock(TransacaoRepository.class);
        modelo = mock(ModeloFraudeClient.class);
        service = new TransacaoService(repository, modelo);
    }

    @Test
    void devePersistirOResultadoRealDaInferencia() {
        TransacaoRequestDTO request = request("TX-1");
        when(repository.findByIdTransacao("TX-1")).thenReturn(Optional.empty());
        when(modelo.inferir(request.features())).thenReturn(
                new ResultadoInferencia("FRAUDE", 0.9999999999964237, 0.9999922476121025, "ulb-logreg-v1"));
        when(repository.save(any(AnaliseTransacao.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransacaoResponseDTO response = service.analisarTransacao(request);

        assertEquals("TX-1", response.idTransacao());
        assertEquals("USR-1", response.idUsuario());
        assertEquals("FRAUDE", response.status());
        assertEquals(0.9999999999964237, response.scoreFraude());
        assertEquals(0.9999922476121025, response.limiar());
        assertEquals("ulb-logreg-v1", response.modelo());
        verify(repository).save(any(AnaliseTransacao.class));
    }

    @Test
    void deveReutilizarAnaliseExistenteParaAMesmaTransacao() {
        AnaliseTransacao existente = new AnaliseTransacao(
                "TX-2", "USR-1", "APROVADO", 0.017396687368755874, 0.9999922476121025,
                "ulb-logreg-v1", Instant.now()
        );
        when(repository.findByIdTransacao("TX-2")).thenReturn(Optional.of(existente));

        TransacaoResponseDTO response = service.analisarTransacao(request("TX-2"));

        assertEquals("APROVADO", response.status());
        verifyNoInteractions(modelo);
        verify(repository, never()).save(any(AnaliseTransacao.class));
    }

    @Test
    void naoDevePersistirQuandoAInferenciaFalha() {
        TransacaoRequestDTO request = request("TX-3");
        when(repository.findByIdTransacao("TX-3")).thenReturn(Optional.empty());
        when(modelo.inferir(request.features())).thenThrow(new InferenciaException("Modelo ausente", false));

        assertThrows(InferenciaException.class, () -> service.analisarTransacao(request));
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

    private TransacaoRequestDTO request(String id) {
        FeaturesTransacaoDTO features = new FeaturesTransacaoDTO(
                146022.0,
                0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0,
                1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 2.0,
                2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8,
                1.18
        );
        return new TransacaoRequestDTO(id, "USR-1", features);
    }
}
