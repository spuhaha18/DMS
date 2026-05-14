package com.lab.edms.search;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface SearchRepository {
    Page<SearchResultDto> search(
            String query,
            String categoryCode,
            String department,
            String state,
            OffsetDateTime from,
            OffsetDateTime to,
            List<String> permittedRoles,
            Long userId,
            Pageable pageable
    );
}
