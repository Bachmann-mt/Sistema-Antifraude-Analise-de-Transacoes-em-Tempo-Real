# Modelo inicial de detecção de fraude

Este módulo entrega a parte de Dados/MLOps do projeto: obtenção do dataset, treinamento, modelo salvo em `.pkl` e inferência por linha de comando com saída JSON. O backend Java poderá executar `predict.py` com `ProcessBuilder`.

O dataset escolhido é o **Credit Card Fraud Detection, da ULB/Worldline**: 284.807 transações de cartões, incluindo 492 fraudes. A origem, a licença e a verificação do arquivo estão em [data/README.md](data/README.md).

**Limitação do contrato:** `V1` a `V28` são componentes PCA anonimizados que já vêm no dataset. As variáveis originais e a transformação não são públicas. A demonstração deve usar transações nesse formato; não é possível obter essas features somente de valor, tipo ou localização de uma transação comum. Este é um modelo acadêmico inicial, avaliado offline, sem validação para decisões bancárias em produção ou garantia de latência de tempo real.

## Executar no Windows

Use Python **3.11 a 3.14** e execute os comandos no PowerShell, a partir da raiz do repositório. O ambiente local foi preparado com Python 3.14.7; as versões das bibliotecas estão fixadas em `requirements.txt`.

```powershell
python -m venv ml/.venv
.\ml\.venv\Scripts\python.exe -m pip install -r ml/requirements.txt
.\ml\.venv\Scripts\python.exe ml/download_data.py
.\ml\.venv\Scripts\python.exe ml/train.py
.\ml\.venv\Scripts\python.exe ml/predict.py --input ml/examples/legitima.json
.\ml\.venv\Scripts\python.exe ml/predict.py --input ml/examples/fraude.json
```

Não é necessário ativar o ambiente virtual. O download requer internet e ocupa aproximadamente 151 MB. Nas próximas execuções, o downloader verifica e reutiliza o CSV já existente. Treinamento e predição funcionam localmente, sem serviços externos.

Em Linux/macOS, crie o ambiente com `python3 -m venv ml/.venv` e substitua `.\ml\.venv\Scripts\python.exe` por `./ml/.venv/bin/python` nos comandos seguintes.

## Executar em container

O [Dockerfile da raiz](../Dockerfile) possui dois alvos de execução: `ml-inference`, para chamar o modelo isoladamente, e `api`, alvo final padrão com Java, Python e o modelo no mesmo container. Ambos disponibilizam `/opt/venv/bin/python`, `/app/ml/predict.py` e `/app/ml/models/fraud_model.pkl`.

Os caminhos também estão disponíveis nas variáveis `ML_PYTHON_EXECUTABLE`,
`ML_PREDICT_SCRIPT` e `ML_MODEL_PATH`. Não monte `ml/models` como volume no
Compose: isso ocultaria o modelo incorporado à imagem.

O build instala as dependências fixadas, baixa o CSV, verifica seu SHA-256 e treina o modelo em Linux. Assim, não depende de um `.pkl`, dataset ou ambiente virtual existente no computador. O relatório dessa execução fica em `/app/ml/reports/metrics.json`, dentro da imagem. O CSV, `train.py`, `download_data.py` e ambientes virtuais do host não integram as imagens finais; o runtime usa o ambiente criado em `/opt/venv` e o usuário `10001:10001`.

Com Docker disponível, execute na raiz do repositório:

```powershell
docker compose -f docker-compose.ml.yml build ml
docker compose -f docker-compose.ml.yml run --rm -T ml --input examples/legitima.json
docker compose -f docker-compose.ml.yml run --rm -T ml --input examples/fraude.json
```

O [Compose de ML](../docker-compose.ml.yml) usa a imagem `antifraude-ml:local` e executa a inferência sem rede, com sistema de arquivos somente leitura e sem capabilities Linux. Ele não monta diretórios do host nem inicia API ou PostgreSQL. O build requer rede para obter imagens, dependências e dataset; a predição usa os artefatos já presentes na imagem.

Para construir também a imagem da API com um nome explícito:

```powershell
docker build --target api -t antifraude-api:ml .
```

O alvo `api` compila o projeto Java com `mvn package`, executando os testes Maven e gerando o JAR durante o build. O fluxo habitual também continua disponível:

```powershell
docker compose up --build
```

Esse comando usa o alvo padrão `api` e sobe a API junto ao PostgreSQL. Com o serviço `api` já em execução, a presença do runtime Python pode ser verificada por uma chamada direta:

```powershell
docker compose exec -T api /opt/venv/bin/python /app/ml/predict.py --input /app/ml/examples/fraude.json
```

Essa chamada executa a CLI dentro do container da API. O código Java ainda precisa ser adaptado para receber as features e invocar o modelo; a execução acima não demonstra uma integração pelo endpoint HTTP. Nesta branch, nenhuma alteração de Java implementa essa chamada.

## Verificar as imagens construídas

Após construir as duas imagens, execute:

```powershell
python ml/test_container.py --image antifraude-ml:local --api-image antifraude-api:ml --report ml/reports/container-tests.json
```

`--api-image` é opcional: omita-o para testar somente a imagem de ML. O script usa a biblioteca padrão do Python no host e imagens já existentes; não faz build nem baixa imagens. Cada container de teste roda sem rede, com sistema de arquivos somente leitura, sem capabilities e sem volumes do host.

As verificações incluem o usuário de execução, dependências, modelo e schemas presentes, ausência do CSV e dos scripts de treino, entradas válidas e inválidas, separação dos streams, exit codes e ajuda. Quando a imagem da API é informada, o script também verifica o Java e executa a inferência nessa imagem, sem iniciar o servidor HTTP.

O relatório `ml/reports/container-tests.json` registra o resultado de cada verificação, os comandos, `stdout`, `stderr`, códigos de saída, IDs das imagens e SHA-256 dos modelos. Consulte `passed`, `passed_checks` e `total_checks` para saber o resultado da execução real. Esse relatório mede o comportamento da CLI nas imagens; não verifica o fluxo HTTP, banco de dados e inferência em conjunto.

Na execução de 17/11/2026, as **33 verificações das duas imagens passaram**.
O [registro adicional da API](reports/api-container-smoke.json) confirma a subida
do servidor com PostgreSQL temporário (`/actuator/health` retornou `UP`) e as duas
predições executadas via `docker exec` no mesmo container da API. Os containers
e a rede usados nesse teste foram removidos ao terminar. O endpoint de análise
continua dependendo da integração Java descrita em [CONTRACT.md](CONTRACT.md).

O modelo da imagem foi treinado em Linux com Python 3.14.10; seu hash e limiar
podem diferir ligeiramente do artefato local de Windows. Use os valores do
relatório incluído na própria imagem, sem copiar manualmente o limiar do README.

## Arquivos e treinamento

| Arquivo | Responsabilidade |
| --- | --- |
| `download_data.py` | Baixar o CSV, verificar cabeçalho e SHA-256. |
| `common.py` | Definir e validar o contrato compartilhado. |
| `train.py` | Treinar, selecionar o limiar e avaliar o modelo. |
| `predict.py` | Receber uma transação e emitir o resultado. |
| `models/fraud_model.pkl` | Pipeline treinado, limiar, features e versões. |
| `reports/metrics.json` | Métricas, divisão dos dados, versões e hashes. |
| `examples/legitima.json`, `examples/fraude.json` | Entradas completas para demonstração. |
| `CONTRACT.md`, `contracts/*.schema.json` | Contrato v1 e schemas de entrada, sucesso e erro. |
| `tests/` | Testes do contrato e do fluxo de ML. |
| `test_container.py` | Verificar imagens construídas e registrar evidências de execução. |

O treinamento usa `StandardScaler` seguido de `LogisticRegression` com `class_weight="balanced"`. O peso das classes compensa a quantidade pequena de fraudes durante o ajuste. O scaler e o classificador são persistidos juntos, garantindo o mesmo processamento na inferência.

O fluxo é reproduzível:

1. Validar as 31 colunas do CSV, os valores numéricos e os rótulos `Class` (`0` = legítima; `1` = fraude).
2. Remover duplicatas exatas e ordenar por `Time`.
3. Separar aproximadamente 60% para treino, 20% para validação e 20% para teste, mantendo cada timestamp em um único conjunto.
4. Ajustar scaler e regressão somente no treino.
5. Escolher o limiar que maximiza F1 somente na validação; em empate, usar o menor limiar.
6. Avaliar uma vez no teste, sem reajustar o modelo depois da seleção do limiar.

O relatório registra precisão, recall, F1, average precision (AP), ROC AUC, acurácia e matriz de confusão, além da comparação com a regra que sempre prevê uma transação legítima. Precisão indica quantos alertas de fraude estavam corretos; recall indica quantas fraudes existentes foram detectadas. A acurácia isolada é insuficiente porque a grande maioria das transações é legítima.

A matriz usa linhas como classes reais e colunas como classes previstas, na ordem `[0, 1]`: `[[verdadeiros negativos, falsos positivos], [falsos negativos, verdadeiros positivos]]`. AP resume o desempenho da ordenação dos scores na relação entre precisão e recall.

Os exemplos são a primeira transação de cada classe verdadeira encontrada no teste, sem seleção pelos acertos do modelo. O relatório identifica a linha original, o rótulo e a predição. O nome `fraude.json` descreve o rótulo real da amostra; o modelo pode errar.

O CSV, o ambiente virtual e o `.pkl` são arquivos locais ignorados pelo Git. Após clonar o projeto, execute o download e o treinamento para recriar o modelo. As métricas e os exemplos podem ser versionados para registrar a execução. Arquivos `.pkl` carregados por `joblib` devem vir de uma origem confiável; use o artefato produzido por este treinamento.

Para caminhos alternativos:

```powershell
.\ml\.venv\Scripts\python.exe ml/train.py --data ml/data/creditcard.csv --model ml/models/fraud_model.pkl --report ml/reports/metrics.json --examples-dir ml/examples
.\ml\.venv\Scripts\python.exe ml/predict.py --input ml/examples/legitima.json --model ml/models/fraud_model.pkl
```

Os caminhos padrão são resolvidos a partir da pasta dos scripts. Caminhos informados nos argumentos são relativos ao diretório atual, salvo quando absolutos.

## Resultados da execução local

Os resultados abaixo vêm do treinamento no host Windows. O build do container treina novamente em Linux e mantém seu próprio relatório em `/app/ml/reports/metrics.json`; consulte o artefato correspondente ao ambiente avaliado.

O [relatório medido](reports/metrics.json) registra a remoção de **1.081 duplicatas exatas**, restando 283.726 transações. No teste temporal com **56.746 transações e 74 fraudes**, o modelo detectou **49 fraudes**, deixou **25 sem detectar** e gerou **3 falsos alertas** em transações legítimas.

| Métrica da classe fraude | Resultado no teste |
| --- | --- |
| Precisão | 94,23% |
| Recall | 66,22% |
| F1 | 0,7778 |
| Average precision (AP) | 0,7475 |

Os alertas emitidos tiveram boa precisão, mas cerca de um terço das fraudes passou despercebido. O baseline inicial demonstra o fluxo solicitado; melhorar o recall é uma prioridade para a próxima etapa, avaliando também o aumento de falsos alertas.

O limiar selecionado na validação foi **`0.9999922476121025`**. Esse valor elevado resulta da distribuição dos scores da regressão com pesos de classe e da seleção pelo F1. Não o arredonde para `1` nem interprete o score como um percentual confiável de chance de fraude: a decisão usa a precisão completa salva no modelo, e os scores não foram calibrados.

## Contrato para a API Java

O contrato Python v1 está definido em [CONTRACT.md](CONTRACT.md), com schemas JSON em `contracts/`. O `TransacaoRequestDTO` atual recebe campos comerciais como `idTransacao`, `idUsuario`, `valor` e `localizacao`; ele ainda não fornece as features exigidas pelo modelo. O backend deverá aceitar essas features em um DTO específico ou objeto aninhado e enviar somente o objeto completo de features ao Python. As componentes PCA não podem ser inferidas a partir dos campos comerciais disponíveis.

A entrada é um único objeto JSON com **exatamente 30 campos obrigatórios**:

| Campo | Tipo e significado |
| --- | --- |
| `Time` | Número finito, maior ou igual a zero; segundos desde a primeira transação do dataset. |
| `V1` a `V28` | Números finitos; componentes PCA já fornecidos pelo dataset, podendo ser negativos. |
| `Amount` | Número finito, maior ou igual a zero; valor da transação na escala original do dataset. |

Os nomes diferenciam maiúsculas e minúsculas. A ordem das propriedades JSON não importa. Não envie `Class`: ele é a resposta conhecida usada no treinamento. Campos ausentes, extras, repetidos, valores `null`, booleanos, strings numéricas, `NaN` e infinitos são recusados. Consulte os arquivos em `examples/` para um objeto completo.

`predict.py` aceita o JSON como **um único argumento posicional**, ou um arquivo por `--input`. Escolha apenas uma dessas formas. A opção de arquivo evita diferenças de escape entre versões do PowerShell; o Java pode passar o JSON diretamente como um argumento de `ProcessBuilder`.

No sucesso, `stdout` contém somente uma linha JSON com estes campos:

| Campo | Valor |
| --- | --- |
| `status` | `FRAUDE` quando o score for maior ou igual ao limiar; caso contrário, `APROVADO`. |
| `score_fraude` | Score entre 0 e 1 retornado pelo modelo. Como o treinamento usa pesos de classe, **não representa uma probabilidade calibrada**. |
| `limiar` | Limiar escolhido na validação e salvo no artefato. |
| `modelo` | Identificador da versão, inicialmente `ulb-logreg-v1`. |

Não há mensagens de progresso em `stdout` na predição. O script retorna código `0` no sucesso, `2` em erro de entrada ou de uso da CLI e `1` em falha de modelo. Todos esses erros são objetos JSON com a chave `erro` em `stderr`, mantendo `stdout` vazio. `-h` e `--help` imprimem texto de ajuda em `stderr`, retornam `0` e não fazem predição.

Se a API externa preservar `SUSPEITO`, o backend deverá mapear `FRAUDE` para `SUSPEITO` e manter `APROVADO`. A decisão deve vir do modelo: não recalculá-la pela regra atual `scoreRisco >= 70`, nem tratar uma conversão do score para `0` a `100` como probabilidade calibrada.

Exemplo de chamada para orientar a futura implementação Java 21, executada a partir da raiz do projeto:

```java
String python = Path.of("ml", ".venv", "Scripts", "python.exe")
        .toAbsolutePath().toString();
String script = Path.of("ml", "predict.py").toAbsolutePath().toString();
String entrada = mapper.writeValueAsString(featuresDto); // Exatamente as 30 features.

Process processo = new ProcessBuilder(python, script, entrada)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start();
if (!processo.waitFor(30, TimeUnit.SECONDS)) {
    processo.destroyForcibly();
    throw new IllegalStateException("Tempo limite da predição excedido");
}
if (processo.exitValue() != 0) {
    throw new IllegalStateException("Predição falhou: " + processo.exitValue());
}
String saida = new String(processo.getInputStream().readAllBytes(),
        StandardCharsets.UTF_8);
JsonNode resultado = mapper.readTree(saida);
```

O exemplo pressupõe Jackson (`mapper`) e imports de `Path`, `TimeUnit`, `StandardCharsets` e `JsonNode`. `featuresDto` deve conter as 30 features; não é o `TransacaoRequestDTO` comercial atual. O `stderr` é encaminhado ao processo Java, separado do JSON. Não use `redirectErrorStream(true)`: isso misturaria erros com o resultado. No Linux local, ajuste o executável para `ml/.venv/bin/python`; no container, use `/opt/venv/bin/python` e `/app/ml/predict.py`, conforme o contrato. O limite de 30 segundos é apenas uma proteção da chamada; não é uma medida de latência do modelo.

## Verificação e evidência da atividade

Execute os testes com as dependências instaladas:

```powershell
.\ml\.venv\Scripts\python.exe -m unittest discover -s ml/tests -v
```

Para o print solicitado na atividade, use o terminal real após treinar o modelo:

```powershell
.\ml\.venv\Scripts\python.exe ml/predict.py --input ml/examples/legitima.json
.\ml\.venv\Scripts\python.exe ml/predict.py --input ml/examples/fraude.json
```

Capture a tela mostrando os comandos e as respostas JSON. O relatório `reports/metrics.json` complementa a evidência com os resultados medidos; as duas amostras de demonstração não substituem a avaliação no conjunto de teste.

O arquivo [reports/evidencia-terminal.txt](reports/evidencia-terminal.txt) preserva o registro textual original das chamadas no host, com respostas e códigos de saída. A evidência das imagens fica separada, em `reports/container-tests.json`, gerado pelo comando de verificação dos containers. Ambos devem refletir suas execuções reais; o print solicitado na atividade deve ser capturado no terminal mostrando os comandos e suas respostas.
