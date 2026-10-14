package com.skillmap.api.application.service.impl;

import com.skillmap.api.application.service.AuthService;
import com.skillmap.api.domain.exception.AuthenticationRequiredException;
import com.skillmap.api.domain.exception.EmailAlreadyRegisteredException;
import com.skillmap.api.domain.exception.InvalidCredentialsException;
import com.skillmap.api.domain.exception.TooManyLoginAttemptsException;
import com.skillmap.api.domain.factory.UserFactory;
import com.skillmap.api.domain.model.IssuedSession;
import com.skillmap.api.domain.model.Session;
import com.skillmap.api.domain.model.User;
import com.skillmap.api.domain.port.LoginAttemptLimiter;
import com.skillmap.api.domain.port.PasswordHasher;
import com.skillmap.api.domain.port.TokenGenerator;
import com.skillmap.api.domain.repository.SessionRepository;
import com.skillmap.api.domain.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Registro, login y sesiones. Ni la contraseña ni el token se registran en logs ni se
 * guardan en claro: de la contraseña queda el hash BCrypt y del token su hash SHA-256.
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final PasswordHasher passwordHasher;
    private final TokenGenerator tokenGenerator;
    private final LoginAttemptLimiter attemptLimiter;
    private final Clock clock;
    private final Duration sessionTtl;
    /** Si el correo no existe se compara igual contra este hash: la respuesta tarda lo mismo. */
    private final String dummyHash;

    public AuthServiceImpl(UserRepository userRepository,
                           SessionRepository sessionRepository,
                           PasswordHasher passwordHasher,
                           TokenGenerator tokenGenerator,
                           LoginAttemptLimiter attemptLimiter,
                           Clock clock,
                           @Value("${skillmap.auth.session-ttl:30d}") Duration sessionTtl) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordHasher = passwordHasher;
        this.tokenGenerator = tokenGenerator;
        this.attemptLimiter = attemptLimiter;
        this.clock = clock;
        this.sessionTtl = sessionTtl;
        this.dummyHash = passwordHasher.hash("dummy-password-for-timing");
    }

    @Override
    public IssuedSession register(String name, String email, String password) {
        UserFactory.validateName(name);
        UserFactory.validateEmail(email);
        UserFactory.validatePassword(password);
        if (userRepository.existsByEmail(UserFactory.normalizeEmail(email))) {
            throw new EmailAlreadyRegisteredException();
        }
        User user = userRepository.save(
                UserFactory.createNew(name, email, passwordHasher.hash(password), clock.instant()));
        return openSession(user);
    }

    @Override
    public IssuedSession login(String email, String password, String clientIp) {
        String normalizedEmail = UserFactory.normalizeEmail(email);
        String attemptKey = normalizedEmail + "|" + clientIp;
        if (attemptLimiter.isBlocked(attemptKey)) {
            throw new TooManyLoginAttemptsException();
        }
        Optional<User> user = userRepository.findByEmail(normalizedEmail);
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean matches = password != null && passwordHasher.matches(password, hash);
        if (user.isEmpty() || !matches) {
            attemptLimiter.recordFailure(attemptKey);
            throw new InvalidCredentialsException();
        }
        attemptLimiter.reset(attemptKey);
        return openSession(user.get());
    }

    @Override
    public void logout(String token) {
        sessionRepository.deleteByTokenHash(tokenGenerator.hash(token));
    }

    @Override
    public Optional<Long> authenticate(String token) {
        if (token == null || token.isEmpty()) {
            return Optional.empty();
        }
        String tokenHash = tokenGenerator.hash(token);
        Optional<Session> session = sessionRepository.findByTokenHash(tokenHash);
        if (session.isEmpty()) {
            return Optional.empty();
        }
        if (session.get().isExpired(clock.instant())) {
            sessionRepository.deleteByTokenHash(tokenHash);
            return Optional.empty();
        }
        return Optional.of(session.get().userId());
    }

    @Override
    public User getUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(AuthenticationRequiredException::new);
    }

    private IssuedSession openSession(User user) {
        Instant now = clock.instant();
        sessionRepository.deleteExpired(now);
        String token = tokenGenerator.generate();
        sessionRepository.save(new Session(tokenGenerator.hash(token), user.getId(), now.plus(sessionTtl)));
        return new IssuedSession(token, user);
    }
}
