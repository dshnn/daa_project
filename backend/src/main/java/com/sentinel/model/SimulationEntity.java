package com.sentinel.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "simulations")
public class SimulationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "network_id", nullable = false)
    private Long networkId;

    @Column(name = "initial_compromised_device_id")
    private Long initialCompromisedDeviceId;

    @Column(name = "propagation_probability")
    private Double propagationProbability;

    @Column(name = "seed")
    private Long seed;

    @Column(name = "strategy")
    private String strategy;

    @Column(name = "rounds")
    private Integer rounds;

    @Column(name = "num_compromised")
    private Integer numCompromised;

    @Column(name = "num_isolated")
    private Integer numIsolated;

    @Column(name = "execution_time_ms")
    private Double executionTimeMs;

    @Column(name = "note")
    private String note;

    @Column(name = "created_at", insertable = false, updatable = false)
    private String createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getNetworkId() {
        return networkId;
    }

    public void setNetworkId(Long networkId) {
        this.networkId = networkId;
    }

    public Long getInitialCompromisedDeviceId() {
        return initialCompromisedDeviceId;
    }

    public void setInitialCompromisedDeviceId(Long initialCompromisedDeviceId) {
        this.initialCompromisedDeviceId = initialCompromisedDeviceId;
    }

    public Double getPropagationProbability() {
        return propagationProbability;
    }

    public void setPropagationProbability(Double propagationProbability) {
        this.propagationProbability = propagationProbability;
    }

    public Long getSeed() {
        return seed;
    }

    public void setSeed(Long seed) {
        this.seed = seed;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public Integer getRounds() {
        return rounds;
    }

    public void setRounds(Integer rounds) {
        this.rounds = rounds;
    }

    public Integer getNumCompromised() {
        return numCompromised;
    }

    public void setNumCompromised(Integer numCompromised) {
        this.numCompromised = numCompromised;
    }

    public Integer getNumIsolated() {
        return numIsolated;
    }

    public void setNumIsolated(Integer numIsolated) {
        this.numIsolated = numIsolated;
    }

    public Double getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(Double executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
