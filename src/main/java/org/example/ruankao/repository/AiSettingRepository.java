package org.example.ruankao.repository;

import org.example.ruankao.entity.AiSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiSettingRepository extends JpaRepository<AiSetting, Long> {

    Optional<AiSetting> findTopByOrderByIdAsc();
}
