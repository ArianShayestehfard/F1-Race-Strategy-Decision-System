package com.f1strategy.backend.repository;

import com.f1strategy.backend.model.Lap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LapRepository extends JpaRepository<Lap, Long> {

    List<Lap> findByRaceIdAndDriverIdOrderByLapNumber(String raceId, String driverId);

    List<Lap> findByRaceIdOrderByLapNumber(String raceId);

    @Query("select distinct l.driverId from Lap l where l.raceId = :raceId")
    List<String> findDriverIdsByRaceId(@Param("raceId") String raceId);
}
