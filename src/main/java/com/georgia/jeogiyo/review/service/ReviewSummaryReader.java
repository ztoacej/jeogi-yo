package com.georgia.jeogiyo.review.service;

import com.georgia.jeogiyo.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewSummaryReader {

    private final ReviewRepository reviewRepository;

    public ReviewSummary getSummary(UUID storeId) {
        if (storeId == null) {
            return new ReviewSummary(0, 0.0);
        }

        int reviewCount = Math.toIntExact(
                reviewRepository.countByStore_StoreIdAndIsDeletedFalse(storeId)
        );

        Double averageRating = reviewRepository.findAverageRatingByStoreId(storeId);

        return new ReviewSummary(
                reviewCount,
                roundAverage(averageRating)
        );
    }

    private Double roundAverage(Double averageRating) {
        return averageRating == null ? 0.0 : Math.round(averageRating * 10.0) / 10.0;
    }
}