package com.studencollabfin.server.repository;

import com.studencollabfin.server.model.Project;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProjectRepository extends MongoRepository<Project, String> {
    List<Project> findByLeaderId(String leaderId);
    List<Project> findByMemberIdsContaining(String userId);
    List<Project> findByStatus(Project.ProjectStatus status);
    List<Project> findByTagsContaining(String tag);
    List<Project> findByRequiredSkillsContaining(String skill);
}

