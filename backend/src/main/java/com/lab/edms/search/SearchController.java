package com.lab.edms.search;

import com.lab.edms.common.NotFoundException;
import com.lab.edms.user.User;
import com.lab.edms.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService searchService;
    private final UserRepository userRepository;

    public SearchController(SearchService searchService, UserRepository userRepository) {
        this.searchService = searchService;
        this.userRepository = userRepository;
    }

    /**
     * GET /api/v1/search?q=&category=&dept=&state=&from=&to=&page=0&size=20
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<SearchResultDto>> search(
            @RequestParam String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String dept,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {

        if (q.trim().length() < 2) {
            return ResponseEntity.badRequest().build();
        }

        User user = userRepository.findByUserId(auth.getName())
                .orElseThrow(() -> new NotFoundException("User not found: " + auth.getName()));

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));

        Page<SearchResultDto> result = searchService.search(
                q.trim(), category, dept, state, from, to,
                user.getId(), pageable);

        return ResponseEntity.ok(result);
    }
}
