package com.lab.edms.search;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    SearchRepository searchRepository;

    @InjectMocks
    SearchService searchService;

    @Test
    void search_returnsOnlyPermittedDocuments() {
        // given
        SearchResultDto dto = new SearchResultDto(
                1L, 10L, "SOP-001", "Test SOP", "APPROVED",
                "SOP", "QA", OffsetDateTime.now(), "john.doe", 0.85f
        );
        Pageable pageable = PageRequest.of(0, 20);
        Page<SearchResultDto> mockPage = new PageImpl<>(List.of(dto), pageable, 1);

        when(searchRepository.search(
                eq("test"), isNull(), isNull(), isNull(),
                isNull(), isNull(), eq(1L), eq(pageable)))
                .thenReturn(mockPage);

        // when
        Page<SearchResultDto> result = searchService.search(
                "test", null, null, null,
                null, null, 1L, pageable);

        // then
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).docNumber()).isEqualTo("SOP-001");
    }

    @Test
    void search_withFilters_delegatesToRepository() {
        // given
        Pageable pageable = PageRequest.of(0, 10);
        Page<SearchResultDto> emptyPage = Page.empty(pageable);
        OffsetDateTime from = OffsetDateTime.parse("2024-01-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2024-12-31T23:59:59Z");

        when(searchRepository.search(
                eq("procedure"), eq("SOP"), eq("QA"), eq("APPROVED"),
                eq(from), eq(to), eq(2L), eq(pageable)))
                .thenReturn(emptyPage);

        // when
        Page<SearchResultDto> result = searchService.search(
                "procedure", "SOP", "QA", "APPROVED",
                from, to, 2L, pageable);

        // then
        assertThat(result.getTotalElements()).isEqualTo(0);
        assertThat(result.isEmpty()).isTrue();
    }
}
