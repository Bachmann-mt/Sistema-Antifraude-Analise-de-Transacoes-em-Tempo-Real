# Sistema Antifraude — Análise de Transações

Projeto acadêmico com uma API REST em Java para registrar e consultar análises de transações e um módulo Python para treinar e executar um modelo de detecção de fraude.

> **Integração:** o endpoint envia as 30 features da requisição para `ml/predict.py`, devolve a decisão e os scores do modelo e persiste esses mesmos valores. A API não deriva as features do valor comercial da transação.

## Equipe

| Integrante | Papel |
| --- | --- |
| Isaac Nilson | Tech Lead / DevOps |
| Matheus | Engenharia de Backend |
| Kawany | Engenharia de Backend |
| Dalton | Engenharia de Backend |
| Gabriel Jurack | Engenharia de Dados / MLOps |

## Estrutura

| Caminho | Conteúdo |
| --- | --- |
| [`br.com.faculdadedonaduzzi/`](br.com.faculdadedonaduzzi/) | API Spring Boot, persistência JPA e testes Java. |
| [`ml/`](ml/README.md) | Download dos dados, treinamento, predição, exemplos e métricas do modelo. |
| [`docker-compose.yml`](docker-compose.yml) | PostgreSQL e API em contêineres. |

## API Java

### Requisitos

- Git e Docker com o plugin Compose. A execução em contêineres não exige Java, Maven ou Python instalados no computador.
- Na primeira construção da imagem, é necessária conexão à internet para baixar imagens, dependências e o dataset usado no treinamento do modelo.
- Para executar a API fora do Docker: Java 21, Python 3.11 a 3.14 com as dependências de `ml/requirements.txt`, modelo treinado em `ml/models/fraud_model.pkl` e PostgreSQL disponível em `localhost:5432` (banco, usuário e senha `antifraude`).

### Executar com Docker Compose

Clone o repositório e execute o Compose na raiz:

```bash
git clone https://github.com/Bachmann-mt/Sistema-Antifraude-Analise-de-Transacoes-em-Tempo-Real.git
cd Sistema-Antifraude-Analise-de-Transacoes-em-Tempo-Real
docker compose up
```

O Compose constrói a imagem da API, incluindo o JAR Java, o Python e o modelo treinado, e inicia o PostgreSQL. A primeira execução pode demorar por causa do download do dataset e do treinamento. A API fica em `http://localhost:8080` e a documentação interativa em `http://localhost:8080/swagger-ui.html`. Para acompanhar os serviços em outra janela, execute `docker compose ps` e `docker compose logs -f api postgres`.

Para executar somente o banco em Docker e a API localmente, inicie `docker compose up -d postgres` e, dentro de `br.com.faculdadedonaduzzi/`, execute `./mvnw spring-boot:run`.

### Endpoints

| Método | Rota | Função |
| --- | --- | --- |
| `POST` | `/api/v1/transacoes/analisar` | Analisa e salva uma transação; se o ID já existir, retorna a análise salva. |
| `GET` | `/api/v1/transacoes/{idTransacao}` | Consulta uma análise pelo ID; retorna `404` se não existir. |

O `POST` exige `idTransacao`, `idUsuario` e um objeto `features` com exatamente `Time`, `V1` a `V28` e `Amount`. Use o [exemplo completo de requisição](br.com.faculdadedonaduzzi/exemplos/requisicao-analisar.json):

```bash
curl -X POST http://localhost:8080/api/v1/transacoes/analisar \
  -H 'Content-Type: application/json' \
  -d @br.com.faculdadedonaduzzi/exemplos/requisicao-analisar.json
```

Uma resposta contém `idTransacao`, `idUsuario`, `status`, `scoreFraude`, `limiar`, `modelo` e `analisadaEm`. `status` é a decisão produzida pelo Python (`FRAUDE` ou `APROVADO`); o backend preserva o score e o limiar sem recalcular a decisão por uma regra comercial. Features recusadas pelo modelo retornam HTTP 400; falhas de execução ou timeout retornam HTTP 503 e não criam registro.

## Modelo Python e evidência de MLOps

O módulo usa o dataset **Credit Card Fraud Detection (ULB/Worldline)**. Seu contrato exige `Time`, `V1` a `V28` e `Amount`; esses campos precisam vir de dados compatíveis com o dataset e não podem ser deduzidos de valores comerciais, localização ou IDs. Consulte o [guia do módulo ML](ml/README.md) para detalhes do dataset, métricas, validações e limitações.

Com Python **3.11 a 3.14**, execute na raiz do repositório:

```bash
python3 -m venv ml/.venv
./ml/.venv/bin/python -m pip install -r ml/requirements.txt
./ml/.venv/bin/python ml/download_data.py
./ml/.venv/bin/python ml/train.py
./ml/.venv/bin/python ml/predict.py --input ml/examples/legitima.json
./ml/.venv/bin/python ml/predict.py --input ml/examples/fraude.json
```

No Windows PowerShell, crie o ambiente com `python -m venv ml/.venv` e substitua `./ml/.venv/bin/python` por `.\ml\.venv\Scripts\python.exe`. O download requer internet; o treinamento gera `ml/models/fraud_model.pkl` localmente. O ambiente virtual, o CSV e o modelo não são versionados e precisam ser recriados em uma nova instalação.

As duas chamadas acima demonstram apenas a inferência isolada. O arquivo [`ml/reports/evidencia-terminal.txt`](ml/reports/evidencia-terminal.txt) registra uma execução anterior; ele não substitui a evidência de uma requisição passando pela API e sendo persistida no PostgreSQL. Fora do Docker, configure `ML_PYTHON_EXECUTABLE`, `ML_PREDICT_SCRIPT`, `ML_MODEL_PATH` e `ML_TIMEOUT_SEGUNDOS` se os defaults locais não corresponderem ao ambiente.

## Verificação da entrega final

Com a API e o modelo disponíveis, envie o exemplo completo pelo Swagger ou pelo `curl` acima e confirme que a resposta contém `status`, `scoreFraude`, `limiar` e `modelo`. Consulte o registro correspondente no PostgreSQL:

```bash
docker compose exec -T postgres psql -U antifraude -d antifraude -c 'SELECT id_transacao, status, score_fraude, limiar, modelo, analisada_em FROM analises_transacao ORDER BY analisada_em DESC;'
```

Para a submissão no D2L, reúna em um PDF o link público do repositório, um print do Swagger com HTTP 200 e dos logs do Compose em execução, e um print do `SELECT` com o resultado da mesma transação.

## Testes

```bash
cd br.com.faculdadedonaduzzi
./mvnw test
```

Para os testes Python, a partir da raiz do repositório e depois de instalar as dependências:

```bash
./ml/.venv/bin/python -m unittest discover -s ml/tests -v
```
