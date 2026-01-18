package com.studencollabfin.server.repository;

import com.studencollabfin.server.model.Application;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface ApplicationRepository extends MongoRepository<Application, String> {
    // Find all applications for a specific beacon post
    List<Application> findByBeaconId(String beaconId);

    // Find all applications by applicant
    List<Application> findByApplicantId(String applicantId);
}
