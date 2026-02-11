package com.studencollabfin.server.repository;

import com.studencollabfin.server.model.Report;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends MongoRepository<Report, String> {
    List<Report> findByReportedUserId(String reportedUserId);

    List<Report> findByReportedUserIdAndResolvedFalse(String reportedUserId);
}
