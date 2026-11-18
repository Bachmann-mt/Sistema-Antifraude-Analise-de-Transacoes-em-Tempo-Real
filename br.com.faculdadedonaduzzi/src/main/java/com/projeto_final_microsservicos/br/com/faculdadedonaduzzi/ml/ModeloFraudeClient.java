package com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.ml;

import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.dto.FeaturesTransacaoDTO;
import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.exception.InferenciaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Executa o predict.py conforme ml/CONTRACT.md: envia somente as 30 features,
 * mantém stdout e stderr separados e só interpreta stdout quando o exit code é 0.
 */
@Component
public class ModeloFraudeClient {

    private final JsonMapper jsonMapper;
    private final String pythonExecutavel;
    private final String scriptPredicao;
    private final String caminhoModelo;
    private final long timeoutSegundos;

    public ModeloFraudeClient(
            JsonMapper jsonMapper,
            @Value("${ML_PYTHON_EXECUTABLE:/opt/venv/bin/python}") String pythonExecutavel,
            @Value("${ML_PREDICT_SCRIPT:/app/ml/predict.py}") String scriptPredicao,
            @Value("${ML_MODEL_PATH:/app/ml/models/fraud_model.pkl}") String caminhoModelo,
            @Value("${ML_TIMEOUT_SEGUNDOS:30}") long timeoutSegundos
    ) {
        this.jsonMapper = jsonMapper;
        this.pythonExecutavel = pythonExecutavel;
        this.scriptPredicao = scriptPredicao;
        this.caminhoModelo = caminhoModelo;
        this.timeoutSegundos = timeoutSegundos;
    }

    public ResultadoInferencia inferir(FeaturesTransacaoDTO features) {
        Path entrada = null;
        try {
            // Arquivo via --input (forma prevista no contrato): evita que o Windows
            // altere as aspas do JSON ao montar a linha de comando do subprocesso.
            entrada = Files.createTempFile("transacao-", ".json");
            Files.writeString(entrada, jsonMapper.writeValueAsString(features), StandardCharsets.UTF_8);
            return executar(entrada);
        } catch (IOException e) {
            throw new InferenciaException("Falha ao preparar a entrada do modelo", e);
        } finally {
            if (entrada != null) {
                try {
                    Files.deleteIfExists(entrada);
                } catch (IOException ignored) {
                    // arquivo temporário; a falha na remoção não afeta a análise
                }
            }
        }
    }

    private ResultadoInferencia executar(Path entrada) {
        Process processo;
        try {
            processo = new ProcessBuilder(List.of(
                    pythonExecutavel,
                    scriptPredicao,
                    "--input", entrada.toString(),
                    "--model", caminhoModelo
            )).start();
            processo.getOutputStream().close();
        } catch (IOException e) {
            throw new InferenciaException("Não foi possível iniciar o Python em " + pythonExecutavel, e);
        }

        CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> ler(processo.getInputStream()));
        CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> ler(processo.getErrorStream()));

        boolean concluido;
        try {
            concluido = processo.waitFor(timeoutSegundos, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            processo.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new InferenciaException("Inferência interrompida", e);
        }
        if (!concluido) {
            processo.destroyForcibly();
            throw new InferenciaException("Tempo limite de " + timeoutSegundos + "s excedido na inferência", false);
        }

        int exitCode = processo.exitValue();
        if (exitCode != 0) {
            String erro = stderr.join().strip();
            throw new InferenciaException("Modelo retornou código " + exitCode + ": " + erro, exitCode == 2);
        }
        return interpretar(stdout.join());
    }

    private ResultadoInferencia interpretar(String saida) {
        ResultadoInferencia resultado;
        try {
            resultado = jsonMapper.readValue(saida, ResultadoInferencia.class);
        } catch (JacksonException e) {
            throw new InferenciaException("Saída do modelo fora do contrato: " + saida.strip(), e);
        }
        if (resultado.status() == null || resultado.scoreFraude() == null
                || resultado.limiar() == null || resultado.modelo() == null) {
            throw new InferenciaException("Saída do modelo incompleta: " + saida.strip(), false);
        }
        return resultado;
    }

    private static String ler(InputStream stream) {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
