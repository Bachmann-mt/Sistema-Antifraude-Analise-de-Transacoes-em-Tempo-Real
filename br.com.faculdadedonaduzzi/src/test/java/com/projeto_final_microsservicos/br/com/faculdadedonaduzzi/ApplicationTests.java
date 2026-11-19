package com.projeto_final_microsservicos.br.com.faculdadedonaduzzi;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationTests {

	@LocalServerPort
	private int port;

	@DynamicPropertySource
	static void configurarModeloDaFixture(DynamicPropertyRegistry registry) {
		registry.add("ml.python-executable", () -> System.getProperty("ml.test.python", "python"));
		registry.add("ml.script-path", ApplicationTests::scriptFixture);
		registry.add("ml.model-path", () -> "api-success");
		registry.add("ml.timeout-segundos", () -> "5");
	}

	@Test
	void contextLoads() {
	}

	@Test
	void devePublicarOContratoOpenApi() throws Exception {
		HttpResponse<String> response = enviar(HttpRequest.newBuilder(uri("/api-docs")).GET().build());

		assertEquals(200, response.statusCode());
		assertTrue(response.body().contains("/api/v1/transacoes/analisar"));
		assertTrue(response.body().contains("/api/v1/transacoes/{idTransacao}"));
		assertTrue(response.body().contains("\"V28\""));
		assertTrue(response.body().contains("\"Amount\""));
	}

	@Test
	void deveAnalisarEPersistirOResultadoDaInferencia() throws Exception {
		Assumptions.assumeTrue(pythonDisponivel(), "Python indisponível; teste de integração ignorado");
		String json = requisicaoJson("TX-INTEGRACAO-1", 146022.0);
		HttpRequest post = HttpRequest.newBuilder(uri("/api/v1/transacoes/analisar"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json))
				.build();

		HttpResponse<String> analise = enviar(post);
		HttpResponse<String> consulta = enviar(HttpRequest.newBuilder(
				uri("/api/v1/transacoes/TX-INTEGRACAO-1")).GET().build());

		assertEquals(200, analise.statusCode());
		assertTrue(analise.body().contains("\"status\":\"FRAUDE\""));
		assertTrue(analise.body().contains("\"scoreFraude\":0.9999999999964237"));
		assertTrue(analise.body().contains("\"limiar\":0.9999922476121025"));
		assertEquals(200, consulta.statusCode());
		assertTrue(consulta.body().contains("\"idTransacao\":\"TX-INTEGRACAO-1\""));
		assertTrue(consulta.body().contains("\"status\":\"FRAUDE\""));
		assertTrue(consulta.body().contains("\"scoreFraude\":0.9999999999964237"));
		assertTrue(consulta.body().contains("\"modelo\":\"fixture-v1\""));
	}

	@Test
	void naoDevePersistirQuandoProcessoPythonFalha() throws Exception {
		Assumptions.assumeTrue(pythonDisponivel(), "Python indisponível; teste de integração ignorado");
		String json = requisicaoJson("TX-INTEGRACAO-ERRO", 0.0);
		HttpRequest post = HttpRequest.newBuilder(uri("/api/v1/transacoes/analisar"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json))
				.build();

		HttpResponse<String> falha = enviar(post);
		HttpResponse<String> consulta = enviar(HttpRequest.newBuilder(
				uri("/api/v1/transacoes/TX-INTEGRACAO-ERRO")).GET().build());

		assertEquals(503, falha.statusCode());
		assertEquals(404, consulta.statusCode());
	}

	@Test
	void deveRecusarRequisicaoSemFeatures() throws Exception {
		HttpRequest post = HttpRequest.newBuilder(uri("/api/v1/transacoes/analisar"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(
						"{\"idTransacao\":\"TX-SEM-FEATURES\",\"idUsuario\":\"USR-1\"}"))
				.build();

		assertEquals(400, enviar(post).statusCode());
	}

	private URI uri(String caminho) {
		return URI.create("http://localhost:" + port + caminho);
	}

	private static String scriptFixture() {
		try {
			return Path.of(ApplicationTests.class.getResource("/python/fake_predict.py").toURI()).toString();
		} catch (Exception exception) {
			throw new IllegalStateException("Fixture Python não encontrada", exception);
		}
	}

	private static boolean pythonDisponivel() {
		try {
			Process processo = new ProcessBuilder(System.getProperty("ml.test.python", "python"), "--version")
					.redirectErrorStream(true).start();
			return processo.waitFor(5, TimeUnit.SECONDS) && processo.exitValue() == 0;
		} catch (IOException | InterruptedException exception) {
			if (exception instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			return false;
		}
	}

	private static String requisicaoJson(String idTransacao, double time) {
		return """
				{
				  "idTransacao": "%s",
				  "idUsuario": "USR-001",
				  "features": {
				    "Time": %s,
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
				""".formatted(idTransacao, time);
	}

	private HttpResponse<String> enviar(HttpRequest request) throws Exception {
		return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
	}
}
