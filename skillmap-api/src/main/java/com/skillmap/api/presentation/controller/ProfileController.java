package com.skillmap.api.presentation.controller;

import com.skillmap.api.application.service.ProfileService;
import com.skillmap.api.domain.model.ProfileLink;
import com.skillmap.api.domain.model.ProfileProject;
import com.skillmap.api.presentation.dto.ProfileLinkRequest;
import com.skillmap.api.presentation.dto.ProfileLinkResponse;
import com.skillmap.api.presentation.dto.ProfileProjectRequest;
import com.skillmap.api.presentation.dto.ProfileProjectResponse;
import com.skillmap.api.presentation.filter.AuthenticationFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Enlaces y proyectos del perfil del usuario de la sesión (exigen sesión, ver AuthenticationFilter).
 * Un id de otro usuario responde 404, igual que uno que no existe.
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/links")
    public List<ProfileLinkResponse> getLinks(@RequestAttribute(AuthenticationFilter.USER_ID) Long userId) {
        return profileService.getLinks(userId).stream().map(ProfileController::toResponse).toList();
    }

    @PostMapping("/links")
    public ResponseEntity<ProfileLinkResponse> addLink(@RequestAttribute(AuthenticationFilter.USER_ID) Long userId,
                                                       @RequestBody ProfileLinkRequest request) {
        ProfileLink link = profileService.addLink(userId, request.label(), request.url());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(link));
    }

    @PutMapping("/links/{id}")
    public ProfileLinkResponse updateLink(@RequestAttribute(AuthenticationFilter.USER_ID) Long userId,
                                          @PathVariable Long id, @RequestBody ProfileLinkRequest request) {
        return toResponse(profileService.updateLink(userId, id, request.label(), request.url()));
    }

    @DeleteMapping("/links/{id}")
    public ResponseEntity<Void> deleteLink(@RequestAttribute(AuthenticationFilter.USER_ID) Long userId,
                                           @PathVariable Long id) {
        profileService.deleteLink(userId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/projects")
    public List<ProfileProjectResponse> getProjects(@RequestAttribute(AuthenticationFilter.USER_ID) Long userId) {
        return profileService.getProjects(userId).stream().map(ProfileController::toResponse).toList();
    }

    @PostMapping("/projects")
    public ResponseEntity<ProfileProjectResponse> addProject(@RequestAttribute(AuthenticationFilter.USER_ID) Long userId,
                                                             @RequestBody ProfileProjectRequest request) {
        ProfileProject project = profileService.addProject(userId, request.name(), request.description(),
                request.url(), request.tags());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(project));
    }

    @PutMapping("/projects/{id}")
    public ProfileProjectResponse updateProject(@RequestAttribute(AuthenticationFilter.USER_ID) Long userId,
                                                @PathVariable Long id, @RequestBody ProfileProjectRequest request) {
        return toResponse(profileService.updateProject(userId, id, request.name(), request.description(),
                request.url(), request.tags()));
    }

    @DeleteMapping("/projects/{id}")
    public ResponseEntity<Void> deleteProject(@RequestAttribute(AuthenticationFilter.USER_ID) Long userId,
                                              @PathVariable Long id) {
        profileService.deleteProject(userId, id);
        return ResponseEntity.noContent().build();
    }

    private static ProfileLinkResponse toResponse(ProfileLink link) {
        return new ProfileLinkResponse(link.id(), link.label(), link.url());
    }

    private static ProfileProjectResponse toResponse(ProfileProject project) {
        return new ProfileProjectResponse(project.id(), project.name(), project.description(), project.url(),
                project.tags());
    }
}
