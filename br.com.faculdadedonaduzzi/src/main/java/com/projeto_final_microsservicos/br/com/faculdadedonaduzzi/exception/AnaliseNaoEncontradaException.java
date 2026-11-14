package com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.exception;

public class AnaliseNaoEncontradaException extends RuntimeException {

    public AnaliseNaoEncontradaException(String idTransacao) {
        super("Nenhuma análise encontrada para a transação " + idTransacao);
    }
}
