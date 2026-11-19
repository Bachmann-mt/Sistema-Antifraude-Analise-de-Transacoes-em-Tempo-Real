# Contrato de inferência Python — v1

Este documento define a fronteira entre o processo Java e `predict.py`. Os schemas JSON usam Draft 2020-12 e estão versionados no repositório: [entrada](contracts/input.schema.json), [sucesso](contracts/output.schema.json) e [erro](contracts/error.schema.json). A versão do contrato corresponde a `SCHEMA_VERSION = 1` em [common.py](common.py); não acrescenta um campo ao payload. O identificador atual do modelo é `ulb-logreg-v1`. Novos modelos podem preservar este contrato, desde que mantenham seu schema e as mesmas features.

## Entrada

Envie **um único objeto JSON com exatamente 30 campos**, conforme `FEATURES` em `common.py`:

| Campos obrigatórios | Regra |
| --- | --- |
| `Time` | Número finito, maior ou igual a zero; segundos desde a primeira transação do dataset. Não é um timestamp Unix. |
| `V1`, `V2`, …, `V28` | Números finitos; componentes PCA anonimizados fornecidos pelo dataset ULB/Worldline. Podem ser negativos. |
| `Amount` | Número finito, maior ou igual a zero; valor da transação na escala do dataset. |

Os nomes diferenciam maiúsculas e minúsculas; a ordem das propriedades não importa. Todos os campos são obrigatórios e nenhum campo adicional é aceito. **`Class` não é entrada**: é o rótulo usado no treinamento. Strings numéricas, booleanos, `null`, nomes repetidos, `NaN` e infinitos são recusados.

O schema descreve números JSON; a validação Python também exige que sejam representáveis como valores finitos no runtime. Por exemplo, um número como `1e309` pode ser JSON sintaticamente válido, mas excede o intervalo aceito. A detecção de nomes repetidos ocorre ao ler o texto JSON, antes que sejam perdidos pela conversão para um objeto. Essas verificações não são substituídas pelo schema.

Exemplos completos: [legitima.json](examples/legitima.json) e [fraude.json](examples/fraude.json). São amostras reais da base de teste; os nomes indicam o rótulo conhecido, não uma garantia de acerto do modelo.

## Chamada e canais de saída

Há duas formas de fornecer a entrada, mutuamente exclusivas:

- JSON serializado em **um único argumento posicional**: é a forma indicada para `ProcessBuilder`.
- `--input caminho.json`: lê um arquivo UTF-8 com o mesmo objeto, útil no terminal.

`--model caminho.pkl` seleciona um artefato local confiável. Sem essa opção, o caminho padrão é `models/fraud_model.pkl`, relativo à pasta de `predict.py`.

| Situação | Exit code | `stdout` | `stderr` |
| --- | --- | --- | --- |
| Predição concluída | `0` | Uma única linha JSON de sucesso, seguida de quebra de linha | Vazio na execução normal |
| Entrada inválida ou uso incorreto da CLI | `2` | Vazio | JSON de erro |
| Modelo ausente, inválido, incompatível ou falha de inferência | `1` | Vazio | JSON de erro |
| `-h` ou `--help` | `0` | Vazio | Texto de ajuda; não executa predição |

Não há logs ou mensagens de progresso em `stdout`. Erros têm a forma `{"erro": "mensagem explicativa"}`. O Java deve verificar o código de saída antes de interpretar o resultado e manter `stdout` e `stderr` separados. Não use `redirectErrorStream(true)`.

## Saída de sucesso

O objeto possui exatamente estes quatro campos:

| Campo | Tipo e significado |
| --- | --- |
| `status` | String `FRAUDE` ou `APROVADO`. |
| `score_fraude` | Número finito entre `0` e `1`, inclusive; score retornado pelo modelo. |
| `limiar` | Número finito entre `0` e `1`, inclusive; limiar salvo no artefato. |
| `modelo` | String não vazia que identifica a versão do artefato; atualmente `ulb-logreg-v1`. |

A decisão é `FRAUDE` quando `score_fraude >= limiar`; caso contrário, é `APROVADO`. Essa comparação usa a precisão completa antes de qualquer formatação. O schema valida os campos e intervalos; a relação entre `status`, `score_fraude` e `limiar` é garantida pelo código e pelos testes.

O modelo usa pesos de classe e seus scores **não são probabilidades calibradas**. Não apresente `score_fraude * 100` como uma chance confiável de fraude. O limiar atualmente treinado está muito próximo de `1`; preserve o valor completo, sem arredondá-lo para `1`.

## Compatibilidade com o backend

O `TransacaoRequestDTO` recebe `idTransacao`, `idUsuario` e um objeto `features` do tipo `FeaturesTransacaoDTO`, com exatamente as 30 propriedades definidas neste contrato. Os valores devem vir de transações compatíveis com a base ULB/Worldline; as features PCA não podem ser calculadas a partir de valor comercial, localização ou IDs. Preenchê-las com zeros ou valores inventados não cria uma integração válida.

O serviço Java serializa **somente o objeto completo `features`** para `predict.py`, usando `--input` e um arquivo UTF-8 temporário. A API devolve e persiste a decisão `FRAUDE` ou `APROVADO`, além de `score_fraude`, `limiar` e `modelo`, sem recalcular o status por uma regra baseada em score arredondado. A execução usa `ProcessBuilder`, mantém `stdout` e `stderr` separados, aplica timeout e rejeita saída incompleta, fora dos limites ou inconsistente com a comparação `score_fraude >= limiar`.

Para analisar transações comerciais sem as features PCA, será necessário outro dataset e um modelo treinado com campos disponíveis na aplicação.

## Caminhos e chamada no container

O contrato de execução no container usa estes caminhos fixos:

| Recurso | Caminho |
| --- | --- |
| Python do ambiente | `/opt/venv/bin/python` |
| Script | `/app/ml/predict.py` |
| Modelo | `/app/ml/models/fraud_model.pkl` |

Esses mesmos valores são publicados na imagem como `ML_PYTHON_EXECUTABLE`,
`ML_PREDICT_SCRIPT` e `ML_MODEL_PATH`. O Compose não precisa montar um volume
para o modelo: ele é criado no estágio `ml-train` e copiado para a imagem final.

O processo Java deve ter acesso ao executável, ao script e ao modelo no mesmo ambiente em que cria o subprocesso. A existência de outro container com Python, por si só, não permite usar `ProcessBuilder` para executá-lo.

Exemplo da construção dos argumentos, sem passar por um shell:

```java
String entradaJson = mapper.writeValueAsString(featuresDto);
ProcessBuilder builder = new ProcessBuilder(
        "/opt/venv/bin/python",
        "/app/ml/predict.py",
        entradaJson,
        "--model", "/app/ml/models/fraud_model.pkl"
);
```

`featuresDto` representa exatamente os 30 campos do schema; não é o `TransacaoRequestDTO` atual. Preserve maiúsculas e minúsculas na serialização. Não adicione aspas de shell ao JSON: cada item de `ProcessBuilder` já é um argumento. Ao executar, consuma os dois streams separadamente, estabeleça timeout, finalize o processo se ele exceder esse limite e só desserialize a saída de sucesso quando o exit code for `0`.

Este contrato define caminhos e comportamento esperado; resultados da verificação do container devem ser registrados a partir da execução real.
