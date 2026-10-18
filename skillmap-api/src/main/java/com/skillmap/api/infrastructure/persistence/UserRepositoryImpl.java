package com.skillmap.api.infrastructure.persistence;

import com.skillmap.api.domain.exception.EmailAlreadyRegisteredException;
import com.skillmap.api.domain.factory.UserFactory;
import com.skillmap.api.domain.model.User;
import com.skillmap.api.domain.repository.UserRepository;
import com.skillmap.api.infrastructure.persistence.entity.UserEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository jpaRepository;

    public UserRepositoryImpl(UserJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<User> findById(Long id) {
        return jpaRepository.findById(id).map(UserRepositoryImpl::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email).map(UserRepositoryImpl::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }

    @Override
    public User save(User user) {
        try {
            return toDomain(jpaRepository.saveAndFlush(new UserEntity(user.getId(), user.getName(), user.getEmail(),
                    user.getPasswordHash(), user.getCreatedAt(), user.getHeadline())));
        } catch (DataIntegrityViolationException e) {
            // Dos registros a la vez con el mismo correo: lo frena el índice único.
            throw new EmailAlreadyRegisteredException();
        }
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    private static User toDomain(UserEntity e) {
        return UserFactory.reconstruct(e.getId(), e.getName(), e.getEmail(), e.getPasswordHash(), e.getCreatedAt(),
                e.getHeadline());
    }
}
