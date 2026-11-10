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
| `tests/test_ml.py` | Testes do contrato e do fluxo de ML. |

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

Não há mensagens de progresso em `stdout` na predição. O script retorna código `0` no sucesso, `2` em erro de entrada e `1` em falha de modelo. Erros de leitura/validação ou inferência são objetos JSON com a chave `erro` em `stderr`; erros de sintaxe da CLI usam a mensagem de uso do `argparse` em `stderr`. `--help` imprime ajuda em `stdout` e não faz predição.

Exemplo de chamada para orientar a futura implementação Java 21, executada a partir da raiz do projeto:

```java
String python = Path.of("ml", ".venv", "Scripts", "python.exe")
        .toAbsolutePath().toString();
String script = Path.of("ml", "predict.py").toAbsolutePath().toString();
String entrada = mapper.writeValueAsString(dto); // Os 30 campos do contrato.

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

O exemplo pressupõe Jackson (`mapper`) e imports de `Path`, `TimeUnit`, `StandardCharsets` e `JsonNode`. O `stderr` é encaminhado ao processo Java, separado do JSON. Não use `redirectErrorStream(true)`: isso misturaria erros com o resultado. No Linux, ajuste o caminho do executável para `ml/.venv/bin/python`. O limite de 30 segundos é apenas uma proteção da chamada; não é uma medida de latência do modelo.

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

O arquivo [reports/evidencia-terminal.txt](reports/evidencia-terminal.txt) registra
as duas chamadas executadas localmente, suas respostas e os códigos de saída.
Ele é um registro textual; o print solicitado na atividade deve ser capturado
no seu terminal.
