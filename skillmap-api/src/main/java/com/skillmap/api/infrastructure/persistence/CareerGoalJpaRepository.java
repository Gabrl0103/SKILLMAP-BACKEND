package com.skillmap.api.infrastructure.persistence;

import com.skillmap.api.infrastructure.persistence.entity.CareerGoalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareerGoalJpaRepository extends JpaRepository<CareerGoalEntity, Long> {
}
