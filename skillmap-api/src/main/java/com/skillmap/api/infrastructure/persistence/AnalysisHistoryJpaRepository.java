package com.skillmap.api.infrastructure.persistence;

import com.skillmap.api.infrastructure.persistence.entity.AnalysisHistoryEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AnalysisHistoryJpaRepository extends JpaRepository<AnalysisHistoryEntity, Long> {
    List<AnalysisHistoryEntity> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);
    Optional<AnalysisHistoryEntity> findByIdAndUserId(Long id, Long userId);
    Optional<AnalysisHistoryEntity> findFirstByUserIdAndTextHashOrderByCreatedAtDescIdDesc(Long userId, String textHash);
    @Query("select h.id from AnalysisHistoryEntity h where h.userId = ?1 order by h.createdAt desc, h.id desc")
    List<Long> findIdsNewestFirst(Long userId);
    long countByUserId(Long userId);
    long deleteByIdAndUserId(Long id, Long userId);
    void deleteByUserIdAndTextHashAndIdNot(Long userId, String textHash, Long id);
    void deleteByUserId(Long userId);
}
