package com.f1strategy.backend.repository;

import com.f1strategy.backend.model.Race;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RaceRepository extends JpaRepository<Race, String> {

    List<Race> findByYearOrderByRound(Integer year);
}
