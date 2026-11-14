package com.projeto_final_microsservicos.br.com.faculdadedonaduzzi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationTests {

	@LocalServerPort
	private int port;

	@Test
	void contextLoads() {
	}

	@Test
	void devePublicarOContratoOpenApi() throws Exception {
		HttpResponse<String> response = enviar(HttpRequest.newBuilder(uri("/api-docs")).GET().build());

		assertEquals(200, response.statusCode());
		assertTrue(response.body().contains("/api/v1/transacoes/analisar"));
		assertTrue(response.body().contains("/api/v1/transacoes/{idTransacao}"));
	}

	@Test
	void deveAnalisarEPersistirUmaTransacao() throws Exception {
		String json = """
				{
				  "idTransacao": "TX-INTEGRACAO-1",
				  "idUsuario": "USR-1",
				  "valor": 15000.00,
				  "localizacao": "Toledo - PR",
				  "tipoTransacao": "PIX"
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
		assertTrue(analise.body().contains("SUSPEITO"));
		assertEquals(200, consulta.statusCode());
		assertTrue(consulta.body().contains("TX-INTEGRACAO-1"));
	}

	private URI uri(String caminho) {
		return URI.create("http://localhost:" + port + caminho);
	}

	private HttpResponse<String> enviar(HttpRequest request) throws Exception {
		return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
	}
}
