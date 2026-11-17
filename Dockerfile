FROM python:3.14-slim-bookworm AS ml-base

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONUTF8=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    OMP_NUM_THREADS=2 \
    OPENBLAS_NUM_THREADS=2 \
    PATH="/opt/venv/bin:${PATH}"

WORKDIR /app/ml
COPY ml/requirements.txt ./requirements.txt
RUN python -m venv /opt/venv \
    && /opt/venv/bin/python -m pip install --no-cache-dir -r requirements.txt

FROM ml-base AS ml-train
COPY ml/common.py ml/download_data.py ml/train.py ./
RUN python download_data.py \
    && python train.py --model /artifacts/models/fraud_model.pkl \
        --report /artifacts/reports/metrics.json --examples-dir /artifacts/examples

FROM ml-base AS ml-inference
ENV ML_PYTHON_EXECUTABLE=/opt/venv/bin/python \
    ML_PREDICT_SCRIPT=/app/ml/predict.py \
    ML_MODEL_PATH=/app/ml/models/fraud_model.pkl
COPY ml/common.py ml/predict.py ./
COPY ml/contracts/ ./contracts/
COPY --from=ml-train --chown=10001:10001 /artifacts/ ./
USER 10001:10001
ENTRYPOINT ["/opt/venv/bin/python", "/app/ml/predict.py"]

FROM maven:3.9-eclipse-temurin-21 AS java-build
WORKDIR /build
COPY br.com.faculdadedonaduzzi/pom.xml ./
COPY br.com.faculdadedonaduzzi/src/ ./src/
RUN mvn --batch-mode --no-transfer-progress package

FROM eclipse-temurin:21-jre-jammy AS java-runtime

FROM ml-inference AS api
USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends libstdc++6 zlib1g \
    && rm -rf /var/lib/apt/lists/*
COPY --from=java-runtime /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk \
    PATH="/opt/java/openjdk/bin:${PATH}"
WORKDIR /app
COPY --from=java-build --chown=10001:10001 /build/target/*.jar ./app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["/opt/java/openjdk/bin/java", "-jar", "/app/app.jar"]
