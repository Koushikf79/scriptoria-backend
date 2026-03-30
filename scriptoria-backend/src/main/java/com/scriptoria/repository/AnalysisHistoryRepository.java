package com.scriptoria.repository;

import com.scriptoria.entity.AnalysisHistory;
import com.scriptoria.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AnalysisHistoryRepository extends JpaRepository<AnalysisHistory, UUID> {
    List<AnalysisHistory> findByUserOrderByCreatedAtDesc(User user);
    Page<AnalysisHistory> findByUser(User user, Pageable pageable);
    List<AnalysisHistory> findTop5ByUserOrderByCreatedAtDesc(User user);
    long countByUser(User user);
}
