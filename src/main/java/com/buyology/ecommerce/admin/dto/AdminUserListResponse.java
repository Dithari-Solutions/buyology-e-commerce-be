package com.buyology.ecommerce.admin.dto;

import java.util.List;

public class AdminUserListResponse {

    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private List<AdminUserSummaryResponse> users;

    public AdminUserListResponse() {
    }

    public AdminUserListResponse(int page, int size, long totalElements, int totalPages,
                                 List<AdminUserSummaryResponse> users) {
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.users = users;
    }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }

    public long getTotalElements() { return totalElements; }
    public void setTotalElements(long totalElements) { this.totalElements = totalElements; }

    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }

    public List<AdminUserSummaryResponse> getUsers() { return users; }
    public void setUsers(List<AdminUserSummaryResponse> users) { this.users = users; }
}
