package com.f1strategy.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "drivers")
public class Driver {

    @Id
    private String driverId;
    private String fullName;
    private String code;
    private String team;
    private String country;

    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getTeam() { return team; }
    public void setTeam(String team) { this.team = team; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
}
