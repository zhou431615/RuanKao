package org.example.ruankao.repository;

import org.example.ruankao.entity.SampleSeedRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SampleSeedRecordRepository extends JpaRepository<SampleSeedRecord, Long> {

    Optional<SampleSeedRecord> findByStem(String stem);
}
