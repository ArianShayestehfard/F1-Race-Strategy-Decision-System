package com.f1strategy.backend.web;

import com.f1strategy.backend.model.Driver;
import com.f1strategy.backend.model.Lap;
import com.f1strategy.backend.model.Race;
import com.f1strategy.backend.repository.DriverRepository;
import com.f1strategy.backend.repository.LapRepository;
import com.f1strategy.backend.repository.RaceRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class RaceController {

    private final RaceRepository races;
    private final DriverRepository drivers;
    private final LapRepository laps;

    public RaceController(RaceRepository races, DriverRepository drivers, LapRepository laps) {
        this.races = races;
        this.drivers = drivers;
        this.laps = laps;
    }

    @GetMapping("/drivers")
    public List<Driver> allDrivers() {
        return drivers.findAll();
    }

    @GetMapping("/races")
    public List<Race> allRaces() {
        return races.findAll();
    }

    @GetMapping("/races/{raceId}/drivers")
    public List<Driver> driversOfRace(@PathVariable String raceId) {
        return drivers.findAll().stream()
                .filter(d -> !laps.findByRaceIdAndDriverIdOrderByLapNumber(raceId, d.getDriverId()).isEmpty())
                .toList();
    }

    @GetMapping("/races/{raceId}/laps/{driverId}")
    public List<Lap> lapsOfDriver(@PathVariable String raceId, @PathVariable String driverId) {
        return laps.findByRaceIdAndDriverIdOrderByLapNumber(raceId, driverId);
    }
}
