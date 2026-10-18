package com.f1strategy.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "races")
public class Race {

    @Id
    private String raceId;
    private Integer year;
    private Integer round;
    private String name;
    private String circuit;
    private String raceDate;
    private Integer totalLaps;

    public String getRaceId() { return raceId; }
    public void setRaceId(String raceId) { this.raceId = raceId; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public Integer getRound() { return round; }
    public void setRound(Integer round) { this.round = round; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCircuit() { return circuit; }
    public void setCircuit(String circuit) { this.circuit = circuit; }
    public String getRaceDate() { return raceDate; }
    public void setRaceDate(String raceDate) { this.raceDate = raceDate; }
    public Integer getTotalLaps() { return totalLaps; }
    public void setTotalLaps(Integer totalLaps) { this.totalLaps = totalLaps; }
}
