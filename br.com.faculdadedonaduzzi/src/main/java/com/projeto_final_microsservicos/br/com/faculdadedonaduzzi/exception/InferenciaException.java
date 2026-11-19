package com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.exception;

/** Falha ao obter a inferência do modelo; nenhuma análise é persistida nesse caso. */
public class InferenciaException extends RuntimeException {

    private final boolean entradaRecusada;

    public InferenciaException(String message, boolean entradaRecusada) {
        super(message);
        this.entradaRecusada = entradaRecusada;
    }

    public InferenciaException(String message, Throwable cause) {
        super(message, cause);
        this.entradaRecusada = false;
    }

    /** true quando o predict.py recusou a entrada (exit code 2). */
    public boolean isEntradaRecusada() {
        return entradaRecusada;
    }
}
