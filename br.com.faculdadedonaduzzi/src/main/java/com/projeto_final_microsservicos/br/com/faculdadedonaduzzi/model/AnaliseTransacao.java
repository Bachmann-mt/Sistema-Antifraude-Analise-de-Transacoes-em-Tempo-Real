package com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "analises_transacao")
public class AnaliseTransacao {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String idTransacao;

    @Column(nullable = false, length = 100)
    private String idUsuario;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false, length = 150)
    private String localizacao;

    private Instant dataHora;

    @Column(length = 50)
    private String tipoTransacao;

    @Column(length = 100)
    private String dispositivoId;

    @Column(length = 100)
    private String estabelecimentoId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private Integer scoreRisco;

    @Column(nullable = false, length = 255)
    private String mensagem;

    @Column(nullable = false)
    private Instant analisadaEm;

    protected AnaliseTransacao() {
    }

    public AnaliseTransacao(
            String idTransacao,
            String idUsuario,
            BigDecimal valor,
            String localizacao,
            Instant dataHora,
            String tipoTransacao,
            String dispositivoId,
            String estabelecimentoId,
            String status,
            Integer scoreRisco,
            String mensagem,
            Instant analisadaEm
    ) {
        this.idTransacao = idTransacao;
        this.idUsuario = idUsuario;
        this.valor = valor;
        this.localizacao = localizacao;
        this.dataHora = dataHora;
        this.tipoTransacao = tipoTransacao;
        this.dispositivoId = dispositivoId;
        this.estabelecimentoId = estabelecimentoId;
        this.status = status;
        this.scoreRisco = scoreRisco;
        this.mensagem = mensagem;
        this.analisadaEm = analisadaEm;
    }

    public String getIdTransacao() {
        return idTransacao;
    }

    public String getStatus() {
        return status;
    }

    public Integer getScoreRisco() {
        return scoreRisco;
    }

    public String getMensagem() {
        return mensagem;
    }

    public Instant getAnalisadaEm() {
        return analisadaEm;
    }
}
