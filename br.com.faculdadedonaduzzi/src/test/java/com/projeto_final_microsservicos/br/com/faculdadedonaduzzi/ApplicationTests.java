package com.projeto_final_microsservicos.br.com.faculdadedonaduzzi;

import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.ml.ModeloFraudeClient;
import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.ml.ResultadoInferencia;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationTests {

	@LocalServerPort
	private int port;

	// O predict.py não existe no ambiente de testes Maven; a CLI é verificada em ml/test_container.py.
	@MockitoBean
	private ModeloFraudeClient modeloFraudeClient;

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
		when(modeloFraudeClient.inferir(any())).thenReturn(
				new ResultadoInferencia("FRAUDE", 0.9999999999964237, 0.9999922476121025, "ulb-logreg-v1"));
		// Mesmo formato de exemplos/requisicao-analisar.json (amostra ml/examples/fraude.json).
		String json = """
				{
				  "idTransacao": "TX-INTEGRACAO-1",
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
				""";
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
		assertEquals(200, consulta.statusCode());
		assertTrue(consulta.body().contains("TX-INTEGRACAO-1"));
		assertTrue(consulta.body().contains("\"modelo\":\"ulb-logreg-v1\""));
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

	private HttpResponse<String> enviar(HttpRequest request) throws Exception {
		return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
	}
}
