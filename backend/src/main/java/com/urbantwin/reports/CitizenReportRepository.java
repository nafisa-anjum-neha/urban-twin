package com.urbantwin.reports;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CitizenReportRepository extends JpaRepository<CitizenReport, UUID> {
    List<CitizenReport> findByStatusOrderByUpvotesDesc(String status);

    List<CitizenReport> findByCategory(String category);

    List<CitizenReport> findAllByOrderByUpvotesDesc();
}
