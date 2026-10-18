package com.f1strategy.backend.repository;

import com.f1strategy.backend.model.Driver;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DriverRepository extends JpaRepository<Driver, String> {

    List<Driver> findByTeam(String team);
}
