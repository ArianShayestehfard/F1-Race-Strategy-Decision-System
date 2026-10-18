package com.f1strategy.backend.repository;

import com.f1strategy.backend.model.Lap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LapRepository extends JpaRepository<Lap, Long> {

    List<Lap> findByRaceIdAndDriverIdOrderByLapNumber(String raceId, String driverId);

    List<Lap> findByRaceIdOrderByLapNumber(String raceId);
}
