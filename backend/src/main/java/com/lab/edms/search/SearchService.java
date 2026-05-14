package com.lab.edms.search;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class SearchService {

    private final SearchRepository searchRepository;

    public SearchService(SearchRepository searchRepository) {
        this.searchRepository = searchRepository;
    }

    public Page<SearchResultDto> search(
            String query,
            String categoryCode,
            String department,
            String state,
            OffsetDateTime from,
            OffsetDateTime to,
            List<String> permittedRoles,
            Long userId,
            Pageable pageable) {
        return searchRepository.search(query, categoryCode, department, state, from, to,
                permittedRoles, userId, pageable);
    }
}
