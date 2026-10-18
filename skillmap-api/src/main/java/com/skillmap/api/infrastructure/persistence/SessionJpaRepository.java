package com.skillmap.api.infrastructure.persistence;

import com.skillmap.api.infrastructure.persistence.entity.SessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface SessionJpaRepository extends JpaRepository<SessionEntity, String> {

    @Transactional
    @Modifying
    @Query("delete from SessionEntity s where s.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);

    @Transactional
    @Modifying
    @Query("delete from SessionEntity s where s.userId = :userId and s.tokenHash <> :keep")
    int deleteByUserIdExcept(@Param("userId") Long userId, @Param("keep") String keepTokenHash);

    @Transactional
    @Modifying
    @Query("delete from SessionEntity s where s.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
