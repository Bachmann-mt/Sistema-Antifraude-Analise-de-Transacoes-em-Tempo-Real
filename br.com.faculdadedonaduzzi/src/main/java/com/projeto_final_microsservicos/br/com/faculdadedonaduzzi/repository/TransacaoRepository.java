package com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.repository;

import com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.model.AnaliseTransacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransacaoRepository extends JpaRepository<AnaliseTransacao, UUID> {

    Optional<AnaliseTransacao> findByIdTransacao(String idTransacao);
}
