package com.studencollabfin.server.repository;

import com.studencollabfin.server.model.SystemSettings;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SystemSettingsRepository extends MongoRepository<SystemSettings, String> {
}
