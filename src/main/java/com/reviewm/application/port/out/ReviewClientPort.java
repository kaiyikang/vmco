package com.reviewm.application.port.out;

import com.reviewm.domain.model.ReviewResult;

public interface ReviewClientPort {
    ReviewResult review(String prompt);
}
