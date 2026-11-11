package com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.service;

import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.dto.TransacaoRequestDTO;
import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.dto.TransacaoResponseDTO;
import org.springframework.stereotype.Service;

@Service
public class TransacaoService {

    public TransacaoResponseDTO analisarTransacao(TransacaoRequestDTO request) {
        if (request.valor() != null && request.valor() > 10000.0) {
            return new TransacaoResponseDTO("SUSPEITO", 85, "Alerta: Transação com valor elevado");
        }
        
        return new TransacaoResponseDTO("APROVADO", 10, "Transação dentro do padrão regular");
    }
}