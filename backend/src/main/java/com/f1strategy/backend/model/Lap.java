package com.f1strategy.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "laps")
public class Lap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String raceId;
    private String driverId;
    private Integer lapNumber;
    private Integer lapTimeMs;
    private Integer sector1Ms;
    private Integer sector2Ms;
    private Integer sector3Ms;
    private String tyreCompound;
    private Integer tyreAge;
    private String trackStatus;
    private Integer isPitLap;
    private Double fuelKg;
    private Integer position;

    public Long getId() { return id; }
    public String getRaceId() { return raceId; }
    public String getDriverId() { return driverId; }
    public Integer getLapNumber() { return lapNumber; }
    public Integer getLapTimeMs() { return lapTimeMs; }
    public Integer getSector1Ms() { return sector1Ms; }
    public Integer getSector2Ms() { return sector2Ms; }
    public Integer getSector3Ms() { return sector3Ms; }
    public String getTyreCompound() { return tyreCompound; }
    public Integer getTyreAge() { return tyreAge; }
    public String getTrackStatus() { return trackStatus; }
    public Integer getIsPitLap() { return isPitLap; }
    public Double getFuelKg() { return fuelKg; }
    public Integer getPosition() { return position; }
}
