package com.f1strategy.backend.repository;

import com.f1strategy.backend.model.Strategy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StrategyRepository extends JpaRepository<Strategy, Long> {

    List<Strategy> findByRaceIdAndDriverId(String raceId, String driverId);
}
