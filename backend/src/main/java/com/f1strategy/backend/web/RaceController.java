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

import java.util.HashSet;
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

    /** Only drivers that actually have lap rows in this race. */
    @GetMapping("/races/{raceId}/drivers")
    public List<Driver> driversOfRace(@PathVariable String raceId) {
        var ids = new HashSet<>(laps.findDriverIdsByRaceId(raceId));
        return drivers.findAll().stream()
                .filter(d -> ids.contains(d.getDriverId()))
                .toList();
    }

    @GetMapping("/races/{raceId}/laps/{driverId}")
    public List<Lap> lapsOfDriver(@PathVariable String raceId, @PathVariable String driverId) {
        return laps.findByRaceIdAndDriverIdOrderByLapNumber(raceId, driverId);
    }
}
