package com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.ml;

import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.dto.FeaturesTransacaoDTO;
import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.exception.InferenciaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/**
 * Executa o predict.py conforme ml/CONTRACT.md: envia somente as 30 features,
 * mantém stdout e stderr separados e só interpreta stdout quando o exit code é 0.
 */
@Component
public class ModeloFraudeClient {

    private static final Set<String> CAMPOS_SAIDA = Set.of("status", "score_fraude", "limiar", "modelo");

    private final JsonMapper jsonMapper;
    private final String pythonExecutavel;
    private final String scriptPredicao;
    private final String caminhoModelo;
    private final long timeoutSegundos;

    public ModeloFraudeClient(
            JsonMapper jsonMapper,
            @Value("${ml.python-executable}") String pythonExecutavel,
            @Value("${ml.script-path}") String scriptPredicao,
            @Value("${ml.model-path}") String caminhoModelo,
            @Value("${ml.timeout-segundos}") long timeoutSegundos
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
        } catch (IOException e) {
            throw new InferenciaException("Não foi possível iniciar o Python em " + pythonExecutavel, e);
        }
        try {
            processo.getOutputStream().close();
        } catch (IOException e) {
            finalizar(processo);
            throw new InferenciaException("Falha ao fechar a entrada do processo Python", e);
        }

        CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> ler(processo.getInputStream()));
        CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> ler(processo.getErrorStream()));

        boolean concluido;
        try {
            concluido = processo.waitFor(timeoutSegundos, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            encerrar(processo, stdout, stderr);
            Thread.currentThread().interrupt();
            throw new InferenciaException("Inferência interrompida", e);
        }
        if (!concluido) {
            encerrar(processo, stdout, stderr);
            throw new InferenciaException("Tempo limite de " + timeoutSegundos + "s excedido na inferência", false);
        }

        int exitCode = processo.exitValue();
        String erro = obterSaida(stderr, "stderr").strip();
        if (exitCode != 0) {
            throw new InferenciaException("Modelo retornou código " + exitCode + ": " + erro, exitCode == 2);
        }
        return interpretar(obterSaida(stdout, "stdout"));
    }

    private ResultadoInferencia interpretar(String saida) {
        JsonNode json;
        try {
            json = jsonMapper.readTree(saida);
        } catch (JacksonException e) {
            throw new InferenciaException("Saída do modelo fora do contrato: " + saida.strip(), e);
        }
        if (json == null || !json.isObject()) {
            throw new InferenciaException("Saída do modelo fora do contrato: " + saida.strip(), false);
        }
        if (json.size() != CAMPOS_SAIDA.size()
                || json.get("status") == null
                || json.get("score_fraude") == null
                || json.get("limiar") == null
                || json.get("modelo") == null) {
            throw new InferenciaException("Campos da saída do modelo fora do contrato: " + saida.strip(), false);
        }

        JsonNode statusNode = json.get("status");
        JsonNode scoreNode = json.get("score_fraude");
        JsonNode limiarNode = json.get("limiar");
        JsonNode modeloNode = json.get("modelo");
        if (statusNode == null || !statusNode.isString()
                || scoreNode == null || !scoreNode.isNumber()
                || limiarNode == null || !limiarNode.isNumber()
                || modeloNode == null || !modeloNode.isString()) {
            throw new InferenciaException("Saída do modelo incompleta: " + saida.strip(), false);
        }

        String status = statusNode.stringValue();
        double score = scoreNode.doubleValue();
        double limiar = limiarNode.doubleValue();
        String modelo = modeloNode.stringValue();
        if (!Double.isFinite(score) || score < 0 || score > 1
                || !Double.isFinite(limiar) || limiar < 0 || limiar > 1
                || modelo.isEmpty()
                || !(status.equals("FRAUDE") || status.equals("APROVADO"))) {
            throw new InferenciaException("Valores da saída do modelo fora do contrato: " + saida.strip(), false);
        }
        String statusEsperado = score >= limiar ? "FRAUDE" : "APROVADO";
        if (!status.equals(statusEsperado)) {
            throw new InferenciaException("Decisão do modelo inconsistente com score_fraude e limiar", false);
        }
        return new ResultadoInferencia(status, score, limiar, modelo);
    }

    private String obterSaida(CompletableFuture<String> saida, String canal) {
        try {
            return saida.join();
        } catch (CompletionException e) {
            throw new InferenciaException("Falha ao ler " + canal + " do processo Python", e.getCause());
        }
    }

    private static void encerrar(
            Process processo,
            CompletableFuture<String> stdout,
            CompletableFuture<String> stderr
    ) {
        finalizar(processo);
        stdout.cancel(true);
        stderr.cancel(true);
        try {
            processo.getInputStream().close();
            processo.getErrorStream().close();
        } catch (IOException ignored) {
            // O processo já foi finalizado; streams são apenas recursos auxiliares.
        }
    }

    private static void finalizar(Process processo) {
        processo.destroyForcibly();
        try {
            processo.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String ler(InputStream stream) {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
