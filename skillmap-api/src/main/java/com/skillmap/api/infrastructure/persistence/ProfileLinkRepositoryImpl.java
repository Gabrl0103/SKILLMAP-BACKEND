package com.skillmap.api.infrastructure.persistence;

import com.skillmap.api.domain.model.ProfileLink;
import com.skillmap.api.domain.repository.ProfileLinkRepository;
import com.skillmap.api.infrastructure.persistence.entity.ProfileLinkEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class ProfileLinkRepositoryImpl implements ProfileLinkRepository {

    private final ProfileLinkJpaRepository jpaRepository;

    public ProfileLinkRepositoryImpl(ProfileLinkJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<ProfileLink> findByUserId(Long userId) {
        return jpaRepository.findByUserIdOrderByIdAsc(userId).stream().map(ProfileLinkRepositoryImpl::toDomain).toList();
    }

    @Override
    public Optional<ProfileLink> findByIdAndUserId(Long id, Long userId) {
        return jpaRepository.findByIdAndUserId(id, userId).map(ProfileLinkRepositoryImpl::toDomain);
    }

    @Override
    public long countByUserId(Long userId) {
        return jpaRepository.countByUserId(userId);
    }

    @Override
    public ProfileLink save(ProfileLink link) {
        return toDomain(jpaRepository.save(new ProfileLinkEntity(link.id(), link.userId(), link.label(), link.url())));
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

    private static ProfileLink toDomain(ProfileLinkEntity entity) {
        return new ProfileLink(entity.getId(), entity.getUserId(), entity.getLabel(), entity.getUrl());
    }
}
