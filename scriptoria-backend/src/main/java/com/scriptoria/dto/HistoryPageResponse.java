package com.scriptoria.dto;

import lombok.Data;

import java.util.List;

@Data
public class HistoryPageResponse {
    private List<HistoryItemResponse> items;
    private long totalItems;
    private int totalPages;
    private int currentPage;
    private int pageSize;
}
