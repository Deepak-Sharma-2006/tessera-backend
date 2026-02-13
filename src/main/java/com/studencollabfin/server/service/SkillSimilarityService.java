package com.studencollabfin.server.service;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SkillSimilarityService: Calculates Jaccard similarity between two users'
 * skill sets.
 * 
 * Jaccard Similarity = |Intersection| / |Union|
 * Range: 0.0 (no common skills) to 1.0 (identical skill sets)
 * 
 * Used by DiscoveryController to rank global mesh matches.
 */
@Service
public class SkillSimilarityService {

    /**
     * Calculates Jaccard similarity between two skill lists.
     * 
     * @param skills1 First user's skills (case-insensitive)
     * @param skills2 Second user's skills (case-insensitive)
     * @return Similarity score between 0.0 and 1.0
     */
    public double calculateSimilarity(List<String> skills1, List<String> skills2) {
        // Handle null or empty cases
        if (skills1 == null || skills2 == null || (skills1.isEmpty() && skills2.isEmpty())) {
            return 0.0;
        }

        // Convert to lowercase sets for case-insensitive comparison
        Set<String> set1 = skills1.stream()
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        Set<String> set2 = skills2.stream()
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        // Both empty after filtering
        if (set1.isEmpty() && set2.isEmpty()) {
            return 0.0;
        }

        // Calculate intersection
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        // Calculate union
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        // Jaccard Similarity = |Intersection| / |Union|
        return (double) intersection.size() / union.size();
    }
}
