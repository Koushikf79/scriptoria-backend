package com.scriptoria.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "analysis_history")
public class AnalysisHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String screenplayTitle;
    private String genre;
    private String scriptTone;
    private int totalScenes;
    private String market;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(columnDefinition = "TEXT")
    private String analysisJson;

    @Column(columnDefinition = "TEXT")
    private String emotionJson;

    @Column(columnDefinition = "TEXT")
    private String budgetJson;

    @Column(columnDefinition = "TEXT")
    private String storyboardSnapshots;

    private Double avgActionIntensity;
    private Double avgEmotionalIntensity;
    private String dominantEmotion;
    private Integer peakTensionScene;
    private Long totalBudgetLow;
    private Long totalBudgetMid;
    private Long totalBudgetHigh;
    private String currency;

    private String dominantTone;
    private String emotionalJourney;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
