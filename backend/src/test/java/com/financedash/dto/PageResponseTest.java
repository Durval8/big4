package com.financedash.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageResponseTest {

    @Test
    void mapsContentAndPageMetadata() {
        PageImpl<Integer> source = new PageImpl<>(List.of(1, 2, 3), PageRequest.of(1, 3), 10);

        PageResponse<String> result = PageResponse.from(source, i -> "n" + i);

        assertThat(result.content()).containsExactly("n1", "n2", "n3");
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(3);
        assertThat(result.totalElements()).isEqualTo(10);
        assertThat(result.totalPages()).isEqualTo(4);
    }

    @Test
    void handlesEmptyPage() {
        PageImpl<Integer> source = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);

        PageResponse<String> result = PageResponse.from(source, i -> "n" + i);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
    }
}
