package com.skillmap.api.application.service;

import com.skillmap.api.domain.model.ProfileLink;
import com.skillmap.api.domain.model.ProfileProject;

import java.util.List;

/**
 * Enlaces (máx. 8) y proyectos (máx. 10) del perfil del usuario de la sesión. Un id que no existe
 * o que es de otro usuario lanza ProfileItemNotFoundException.
 */
public interface ProfileService {
    List<ProfileLink> getLinks(Long userId);

    ProfileLink addLink(Long userId, String label, String url);

    ProfileLink updateLink(Long userId, Long linkId, String label, String url);

    void deleteLink(Long userId, Long linkId);

    List<ProfileProject> getProjects(Long userId);

    ProfileProject addProject(Long userId, String name, String description, String url, List<String> tags);

    ProfileProject updateProject(Long userId, Long projectId, String name, String description, String url,
                                 List<String> tags);

    void deleteProject(Long userId, Long projectId);
}
