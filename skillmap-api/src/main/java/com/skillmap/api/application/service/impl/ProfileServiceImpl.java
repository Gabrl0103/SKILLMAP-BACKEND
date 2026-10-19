package com.skillmap.api.application.service.impl;

import com.skillmap.api.application.service.ProfileService;
import com.skillmap.api.domain.exception.InvalidProfileDataException;
import com.skillmap.api.domain.exception.ProfileItemNotFoundException;
import com.skillmap.api.domain.factory.ProfileItemFactory;
import com.skillmap.api.domain.model.ProfileLink;
import com.skillmap.api.domain.model.ProfileProject;
import com.skillmap.api.domain.repository.ProfileLinkRepository;
import com.skillmap.api.domain.repository.ProfileProjectRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProfileServiceImpl implements ProfileService {

    private final ProfileLinkRepository linkRepository;
    private final ProfileProjectRepository projectRepository;

    public ProfileServiceImpl(ProfileLinkRepository linkRepository, ProfileProjectRepository projectRepository) {
        this.linkRepository = linkRepository;
        this.projectRepository = projectRepository;
    }

    @Override
    public List<ProfileLink> getLinks(Long userId) {
        return linkRepository.findByUserId(userId);
    }

    @Override
    public ProfileLink addLink(Long userId, String label, String url) {
        if (linkRepository.countByUserId(userId) >= ProfileItemFactory.MAX_LINKS) {
            throw new InvalidProfileDataException("Puedes tener como máximo 8 enlaces. Borra uno para agregar otro.");
        }
        return linkRepository.save(ProfileItemFactory.link(null, userId, label, url));
    }

    @Override
    public ProfileLink updateLink(Long userId, Long linkId, String label, String url) {
        linkRepository.findByIdAndUserId(linkId, userId)
                .orElseThrow(() -> new ProfileItemNotFoundException(ProfileItemNotFoundException.LINK));
        return linkRepository.save(ProfileItemFactory.link(linkId, userId, label, url));
    }

    @Override
    public void deleteLink(Long userId, Long linkId) {
        if (!linkRepository.deleteByIdAndUserId(linkId, userId)) {
            throw new ProfileItemNotFoundException(ProfileItemNotFoundException.LINK);
        }
    }

    @Override
    public List<ProfileProject> getProjects(Long userId) {
        return projectRepository.findByUserId(userId);
    }

    @Override
    public ProfileProject addProject(Long userId, String name, String description, String url, List<String> tags) {
        if (projectRepository.countByUserId(userId) >= ProfileItemFactory.MAX_PROJECTS) {
            throw new InvalidProfileDataException("Puedes tener como máximo 10 proyectos. Borra uno para agregar otro.");
        }
        return projectRepository.save(ProfileItemFactory.project(null, userId, name, description, url, tags));
    }

    @Override
    public ProfileProject updateProject(Long userId, Long projectId, String name, String description, String url,
                                        List<String> tags) {
        projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ProfileItemNotFoundException(ProfileItemNotFoundException.PROJECT));
        return projectRepository.save(ProfileItemFactory.project(projectId, userId, name, description, url, tags));
    }

    @Override
    public void deleteProject(Long userId, Long projectId) {
        if (!projectRepository.deleteByIdAndUserId(projectId, userId)) {
            throw new ProfileItemNotFoundException(ProfileItemNotFoundException.PROJECT);
        }
    }
}
