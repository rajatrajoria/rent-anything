package com.rajat.rent_anything.user.records.response;

import java.util.List;

public record AdminUserListResponse(
        List<AdminUserSummaryResponse> users,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
