# Dataset: ULB/Worldline Credit Card Fraud Detection

O projeto usa transações de cartões realizadas por titulares europeus em setembro de 2013, disponibilizadas pela colaboração entre o Machine Learning Group da Université Libre de Bruxelles (ULB) e a Worldline. O arquivo contém **284.807 transações, das quais 492 são fraudes**, em um período de dois dias.

| Propriedade | Valor |
| --- | --- |
| Arquivo local | `ml/data/creditcard.csv` |
| Tamanho verificado | 150.828.752 bytes (aproximadamente 151 MB) |
| Colunas | `Time`, `V1` a `V28`, `Amount`, `Class` |
| Alvo | `Class`: `0` para legítima e `1` para fraude |
| SHA-256 | `76274b691b16a6c49d3f159c883398e03ccd6d1ee12d9d8ee38f4b4b98551a89` |

`Time` representa os segundos decorridos desde a primeira transação. `Amount` é o valor original da transação. As outras 28 features são componentes obtidos por PCA para preservar a confidencialidade; as variáveis originais e a transformação não estão disponíveis. Portanto, novas entradas para esta demonstração precisam seguir o mesmo formato anonimizado.

## Obtenção

Na raiz do repositório, execute:

```powershell
.\ml\.venv\Scripts\python.exe ml/download_data.py
```

O script usa o [espelho público do TensorFlow](https://storage.googleapis.com/download.tensorflow.org/data/creditcard.csv), indicado no [tutorial oficial sobre classificação com dados desbalanceados](https://www.tensorflow.org/tutorials/structured_data/imbalanced_data). Não exige uma conta Kaggle. O download é gravado em arquivo temporário e só é instalado depois de verificar o cabeçalho e o SHA-256. Uma cópia existente também passa por essa verificação.

O CSV completo não é versionado no Git. O treinamento usa o arquivo baixado, remove duplicatas exatas e registra as contagens efetivamente utilizadas em `ml/reports/metrics.json`. Não confunda essas contagens com as dimensões do arquivo original.

## Origem, atribuição e licença

A [página original da ULB no Kaggle](https://www.kaggle.com/mlg-ulb/creditcardfraud/) identifica os termos como **“Database: Open Database, Contents: Database Contents”**: Open Database License (ODbL) para o banco e Database Contents License (DbCL) para seu conteúdo. Consulte os termos na página de origem ao reutilizar os dados; o conjunto não é apresentado aqui como CC0.

Os arquivos `ml/examples/legitima.json` e `ml/examples/fraude.json` são pequenas extrações dessa base, com atribuição à **ULB/Worldline** e sujeitas aos termos do dataset. O relatório de treinamento informa a linha original de cada exemplo e seu rótulo verdadeiro. Eles não são dados sintéticos gerados pelo projeto.
