package com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.ml;

import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.dto.FeaturesTransacaoDTO;
import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.exception.InferenciaException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModeloFraudeClientTest {

    private static final String PYTHON = System.getProperty("ml.test.python", "python");
    private static String script;

    @BeforeAll
    static void configurarFixture() throws URISyntaxException {
        Assumptions.assumeTrue(pythonDisponivel(), "Python indisponível; testes de subprocesso ignorados");
        script = Path.of(ModeloFraudeClientTest.class.getResource("/python/fake_predict.py").toURI()).toString();
    }

    @Test
    void deveSerializarAsFeaturesEInterpretarResultadoComPrecisao() {
        ResultadoInferencia resultado = cliente("success", 5).inferir(features());

        assertEquals("FRAUDE", resultado.status());
        assertEquals(0.9999999999964237, resultado.scoreFraude());
        assertEquals(0.9999922476121025, resultado.limiar());
        assertEquals("fixture-v1", resultado.modelo());
    }

    @Test
    void deveTratarCodigosDeSaidaDoPython() {
        InferenciaException entrada = assertThrows(
                InferenciaException.class,
                () -> cliente("exit-2", 5).inferir(features())
        );
        assertTrue(entrada.isEntradaRecusada());
        assertTrue(entrada.getMessage().contains("erro deterministico da fixture"));

        InferenciaException modelo = assertThrows(
                InferenciaException.class,
                () -> cliente("exit-1", 5).inferir(features())
        );
        assertFalse(modelo.isEntradaRecusada());
        assertTrue(modelo.getMessage().contains("erro deterministico da fixture"));
    }

    @Test
    void deveRejeitarSaidaForaDoContrato() {
        for (String saida : new String[]{"invalid-json", "missing-field", "inconsistent", "out-of-range", "extra-field"}) {
            assertThrows(InferenciaException.class, () -> cliente(saida, 5).inferir(features()), saida);
        }
    }

    @Test
    void deveEncerrarProcessoQueExcedeOTempoLimite() {
        InferenciaException exception = assertThrows(
                InferenciaException.class,
                () -> cliente("timeout", 1).inferir(features())
        );

        assertTrue(exception.getMessage().contains("Tempo limite"));
    }

    @Test
    void deveTratarExecutavelIndisponivel(@TempDir Path diretorio) {
        ModeloFraudeClient cliente = new ModeloFraudeClient(
                JsonMapper.builder().build(),
                diretorio.resolve("python-inexistente").toString(),
                script,
                "success",
                5
        );

        assertThrows(InferenciaException.class, () -> cliente.inferir(features()));
    }

    private static ModeloFraudeClient cliente(String modo, long timeout) {
        return new ModeloFraudeClient(JsonMapper.builder().build(), PYTHON, script, modo, timeout);
    }

    private static FeaturesTransacaoDTO features() {
        return new FeaturesTransacaoDTO(
                146022.0,
                0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0,
                1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 2.0,
                2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8,
                1.18
        );
    }

    private static boolean pythonDisponivel() {
        try {
            Process processo = new ProcessBuilder(PYTHON, "--version").redirectErrorStream(true).start();
            return processo.waitFor(5, TimeUnit.SECONDS) && processo.exitValue() == 0;
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }
}