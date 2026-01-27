package com.studencollabfin.server.service;

import com.studencollabfin.server.model.Project;
import com.studencollabfin.server.repository.ProjectRepository;
import com.studencollabfin.server.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProjectService {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    public Project createProject(String leaderId, Project project) {
        if (leaderId != null) {
            userRepository.findById((String) leaderId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
        }

        project.setLeaderId(leaderId);
        project.setStatus(Project.ProjectStatus.PLANNING);
        project.setStartDate(LocalDateTime.now());

        if (project.getMemberIds() == null) {
            project.setMemberIds(new java.util.ArrayList<>());
        }
        project.getMemberIds().add(leaderId);

        return projectRepository.save(project);
    }

    public Project joinProject(String projectId, String userId) {
        if (projectId != null) {
            Project project = projectRepository.findById((String) projectId)
                    .orElseThrow(() -> new RuntimeException("Project not found"));

            if (project.getMemberIds().size() >= project.getMaxTeamSize()) {
                throw new RuntimeException("Project team is full");
            }

            if (!project.getMemberIds().contains(userId)) {
                project.getMemberIds().add(userId);
            }

            return projectRepository.save(project);
        }
        throw new RuntimeException("Project not found");
    }

    public Project addMilestone(String projectId, String leaderId, Project.Milestone milestone) {
        if (projectId != null) {
            Project project = projectRepository.findById((String) projectId)
                    .orElseThrow(() -> new RuntimeException("Project not found"));

            if (!project.getLeaderId().equals(leaderId)) {
                throw new RuntimeException("Only project leader can add milestones");
            }

            if (project.getMilestones() == null) {
                project.setMilestones(new java.util.ArrayList<>());
            }
            project.getMilestones().add(milestone);

            return projectRepository.save(project);
        }
        throw new RuntimeException("Project not found");
    }

    public Project completeProject(String projectId, String leaderId) {
        if (projectId != null) {
            Project project = projectRepository.findById((String) projectId)
                    .orElseThrow(() -> new RuntimeException("Project not found"));

            if (!project.getLeaderId().equals(leaderId)) {
                throw new RuntimeException("Only project leader can complete the project");
            }

            project.setStatus(Project.ProjectStatus.COMPLETED);
            project.setEndDate(LocalDateTime.now());

            // Award XP to all team members
            for (String memberId : project.getMemberIds()) {
                userService.awardProjectCompletionXP(memberId);
            }

            return projectRepository.save(project);
        }
        throw new RuntimeException("Project not found");
    }

    public List<Project> getProjectsByUser(String userId) {
        List<Project> ledProjects = projectRepository.findByLeaderId(userId);
        List<Project> memberProjects = projectRepository.findByMemberIdsContaining(userId);
        ledProjects.addAll(memberProjects);
        return ledProjects;
    }
}
