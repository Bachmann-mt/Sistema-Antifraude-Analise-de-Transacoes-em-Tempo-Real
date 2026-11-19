package com.projeto_final_microsservicos.br.com.faculdadedonaduzzi.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

    /** Decisão retornada pelo modelo: FRAUDE ou APROVADO. */
    @Column(nullable = false, length = 20)
    private String status;

    /** Score do modelo com precisão completa (double precision). */
    @Column(nullable = false)
    private Double scoreFraude;

    @Column(nullable = false)
    private Double limiar;

    @Column(nullable = false, length = 100)
    private String modelo;

    @Column(nullable = false)
    private Instant analisadaEm;

    protected AnaliseTransacao() {
    }

    public AnaliseTransacao(
            String idTransacao,
            String idUsuario,
            String status,
            Double scoreFraude,
            Double limiar,
            String modelo,
            Instant analisadaEm
    ) {
        this.idTransacao = idTransacao;
        this.idUsuario = idUsuario;
        this.status = status;
        this.scoreFraude = scoreFraude;
        this.limiar = limiar;
        this.modelo = modelo;
        this.analisadaEm = analisadaEm;
    }

    public String getIdTransacao() {
        return idTransacao;
    }

    public String getIdUsuario() {
        return idUsuario;
    }

    public String getStatus() {
        return status;
    }

    public Double getScoreFraude() {
        return scoreFraude;
    }

    public Double getLimiar() {
        return limiar;
    }

    public String getModelo() {
        return modelo;
    }

    public Instant getAnalisadaEm() {
        return analisadaEm;
    }
}
