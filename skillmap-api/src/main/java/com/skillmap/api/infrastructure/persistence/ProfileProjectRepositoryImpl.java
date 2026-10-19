package com.skillmap.api.infrastructure.persistence;

import com.skillmap.api.domain.model.ProfileProject;
import com.skillmap.api.domain.repository.ProfileProjectRepository;
import com.skillmap.api.infrastructure.persistence.entity.ProfileProjectEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class ProfileProjectRepositoryImpl implements ProfileProjectRepository {

    private final ProfileProjectJpaRepository jpaRepository;

    public ProfileProjectRepositoryImpl(ProfileProjectJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<ProfileProject> findByUserId(Long userId) {
        return jpaRepository.findByUserIdOrderByIdAsc(userId).stream().map(ProfileProjectRepositoryImpl::toDomain).toList();
    }

    @Override
    public Optional<ProfileProject> findByIdAndUserId(Long id, Long userId) {
        return jpaRepository.findByIdAndUserId(id, userId).map(ProfileProjectRepositoryImpl::toDomain);
    }

    @Override
    public long countByUserId(Long userId) {
        return jpaRepository.countByUserId(userId);
    }

    @Override
    public ProfileProject save(ProfileProject project) {
        return toDomain(jpaRepository.save(new ProfileProjectEntity(project.id(), project.userId(), project.name(),
                project.description(), project.url(), project.tags())));
    }

    @Override
    @Transactional
    public boolean deleteByIdAndUserId(Long id, Long userId) {
        return jpaRepository.deleteByIdAndUserId(id, userId) > 0;
    }

    @Override
    @Transactional
    public void deleteByUserId(Long userId) {
        jpaRepository.deleteByUserId(userId);
    }

    private static ProfileProject toDomain(ProfileProjectEntity entity) {
        return new ProfileProject(entity.getId(), entity.getUserId(), entity.getName(), entity.getDescription(),
                entity.getUrl(), List.copyOf(entity.getTags()));
    }
}
