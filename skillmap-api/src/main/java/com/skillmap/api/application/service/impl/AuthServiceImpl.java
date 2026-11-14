package com.skillmap.api.application.service.impl;

import com.skillmap.api.application.service.AuthService;
import com.skillmap.api.application.service.ReadinessHistoryService;
import com.skillmap.api.domain.exception.AuthenticationRequiredException;
import com.skillmap.api.domain.exception.CareerGoalNotFoundException;
import com.skillmap.api.domain.exception.EmailAlreadyRegisteredException;
import com.skillmap.api.domain.exception.IncorrectPasswordException;
import com.skillmap.api.domain.exception.InvalidUserDataException;
import com.skillmap.api.domain.exception.InvalidCredentialsException;
import com.skillmap.api.domain.exception.TooManyLoginAttemptsException;
import com.skillmap.api.domain.factory.UserFactory;
import com.skillmap.api.domain.model.IssuedSession;
import com.skillmap.api.domain.model.Session;
import com.skillmap.api.domain.model.User;
import com.skillmap.api.domain.port.LoginAttemptLimiter;
import com.skillmap.api.domain.port.PasswordHasher;
import com.skillmap.api.domain.port.TokenGenerator;
import com.skillmap.api.domain.model.CareerGoal;
import com.skillmap.api.domain.repository.AnalysisHistoryRepository;
import com.skillmap.api.domain.repository.CustomGoalRepository;
import com.skillmap.api.domain.repository.ProfileLinkRepository;
import com.skillmap.api.domain.repository.ProfileProjectRepository;
import com.skillmap.api.domain.repository.ReadinessSnapshotRepository;
import com.skillmap.api.domain.repository.SessionRepository;
import com.skillmap.api.domain.repository.UserRepository;
import com.skillmap.api.domain.repository.UserSkillRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Registro, login, sesiones y cuenta (perfil, contraseña y borrado). Ni la contraseña ni el token
 * se registran en logs ni se guardan en claro: de la contraseña queda el hash BCrypt (costo 12) y del token
 * su hash SHA-256. Los intentos fallidos de login, de cambio de contraseña y de borrado cuentan
 * juntos en el mismo limitador (por correo y por IP, con bloqueo creciente).
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final UserSkillRepository userSkillRepository;
    private final ProfileLinkRepository profileLinkRepository;
    private final ProfileProjectRepository profileProjectRepository;
    private final GoalCatalog goalCatalog;
    private final CustomGoalRepository customGoalRepository;
    private final ReadinessSnapshotRepository readinessSnapshotRepository;
    private final AnalysisHistoryRepository analysisHistoryRepository;
    private final ReadinessHistoryService readinessHistory;
    private final PasswordHasher passwordHasher;
    private final TokenGenerator tokenGenerator;
    private final LoginAttemptLimiter attemptLimiter;
    private final Clock clock;
    private final Duration sessionTtl;
    /** Si el correo no existe se compara igual contra este hash: la respuesta tarda lo mismo. */
    private final String dummyHash;

    public AuthServiceImpl(UserRepository userRepository,
                           SessionRepository sessionRepository,
                           UserSkillRepository userSkillRepository,
                           ProfileLinkRepository profileLinkRepository,
                           ProfileProjectRepository profileProjectRepository,
                           GoalCatalog goalCatalog,
                           CustomGoalRepository customGoalRepository,
                           ReadinessSnapshotRepository readinessSnapshotRepository,
                           AnalysisHistoryRepository analysisHistoryRepository,
                           ReadinessHistoryService readinessHistory,
                           PasswordHasher passwordHasher,
                           TokenGenerator tokenGenerator,
                           LoginAttemptLimiter attemptLimiter,
                           Clock clock,
                           @Value("${skillmap.auth.session-ttl:30d}") Duration sessionTtl) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.userSkillRepository = userSkillRepository;
        this.profileLinkRepository = profileLinkRepository;
        this.profileProjectRepository = profileProjectRepository;
        this.goalCatalog = goalCatalog;
        this.customGoalRepository = customGoalRepository;
        this.readinessSnapshotRepository = readinessSnapshotRepository;
        this.analysisHistoryRepository = analysisHistoryRepository;
        this.readinessHistory = readinessHistory;
        this.passwordHasher = passwordHasher;
        this.tokenGenerator = tokenGenerator;
        this.attemptLimiter = attemptLimiter;
        this.clock = clock;
        this.sessionTtl = sessionTtl;
        this.dummyHash = passwordHasher.hash("dummy-password-for-timing");
    }

    /**
     * El hash se calcula antes de mirar si el correo existe: así un correo ya registrado tarda lo mismo en responder
     * que uno nuevo y el tiempo no lo delata.
     */
    @Override
    public IssuedSession register(String name, String email, String password) {
        UserFactory.validateName(name);
        UserFactory.validateEmail(email);
        UserFactory.validateNewPassword(password, email, UserFactory.normalizeName(name));
        String passwordHash = passwordHasher.hash(password);
        if (userRepository.existsByEmail(UserFactory.normalizeEmail(email))) {
            throw new EmailAlreadyRegisteredException();
        }
        User user = userRepository.save(UserFactory.createNew(name, email, passwordHash, clock.instant()));
        return openSession(user);
    }

    /**
     * Correo inexistente y contraseña incorrecta dan el mismo mensaje y tardan lo mismo (se compara contra un hash
     * ficticio del mismo costo). Si el hash guardado es de un costo anterior, se rehace con la contraseña recién
     * comprobada.
     */
    @Override
    public IssuedSession login(String email, String password, String clientIp) {
        String normalizedEmail = UserFactory.normalizeEmail(email);
        checkNotBlocked(normalizedEmail, clientIp);
        Optional<User> user = userRepository.findByEmail(normalizedEmail);
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean matches = password != null && passwordHasher.matches(password, hash);
        if (user.isEmpty() || !matches) {
            attemptLimiter.recordFailure(normalizedEmail, clientIp);
            throw new InvalidCredentialsException();
        }
        attemptLimiter.reset(normalizedEmail);
        User account = user.get();
        if (passwordHasher.needsRehash(account.getPasswordHash())) {
            account = userRepository.save(account.withPasswordHash(passwordHasher.hash(password)));
        }
        return openSession(account);
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

    @Override
    public User updateProfile(Long userId, String name, String headline) {
        User user = getUser(userId);
        // Un nombre sin cambios no se revisa: una cuenta de antes de las reglas actuales puede seguir editando su titular.
        boolean nameChanged = name != null && !UserFactory.normalizeName(name).equals(user.getName());
        if (nameChanged) {
            UserFactory.validateName(name);
        }
        if (headline != null) {
            UserFactory.validateHeadline(headline);
        }
        return userRepository.save(user.withProfile(
                nameChanged ? UserFactory.normalizeName(name) : user.getName(),
                headline == null ? user.getHeadline() : UserFactory.normalizeHeadline(headline)));
    }

    @Override
    public User updateGoal(Long userId, Long goalId) {
        User user = getUser(userId);
        if (goalId == null) {
            throw new InvalidUserDataException("Elige un objetivo profesional.");
        }
        // Un objetivo base o uno propio del usuario; el propio de otro usuario no existe para él (404)
        CareerGoal goal = goalCatalog.find(userId, goalId).orElseThrow(() -> new CareerGoalNotFoundException(goalId));
        User updated = userRepository.save(user.withGoal(goal));
        readinessHistory.recordGoal(userId, goalId);
        return updated;
    }

    @Override
    public void changePassword(Long userId, String token, String currentPassword, String newPassword,
                               String clientIp) {
        User user = getUser(userId);
        checkNotBlocked(UserFactory.normalizeEmail(user.getEmail()), clientIp);
        // Antes de comprobar la actual: una nueva inválida no gasta un intento.
        UserFactory.validateNewPassword(newPassword, user.getEmail(), user.getName());
        checkPassword(user, currentPassword, clientIp);
        userRepository.save(user.withPasswordHash(passwordHasher.hash(newPassword)));
        sessionRepository.deleteByUserIdExcept(userId, tokenGenerator.hash(token));
    }

    @Override
    @Transactional
    public void deleteAccount(Long userId, String password, String clientIp) {
        User user = getUser(userId);
        checkNotBlocked(UserFactory.normalizeEmail(user.getEmail()), clientIp);
        checkPassword(user, password, clientIp);
        userSkillRepository.deleteByUserId(userId);
        profileLinkRepository.deleteByUserId(userId);
        profileProjectRepository.deleteByUserId(userId);
        readinessSnapshotRepository.deleteByUserId(userId);
        analysisHistoryRepository.deleteByUserId(userId);
        customGoalRepository.deleteByUserId(userId); // la base también los borra en cascada con la cuenta
        sessionRepository.deleteByUserId(userId);
        userRepository.deleteById(userId);
    }

    /** Como en el login: un fallo suma en el limitador y un acierto pone a cero el correo. */
    private void checkPassword(User user, String password, String clientIp) {
        if (password == null || !passwordHasher.matches(password, user.getPasswordHash())) {
            attemptLimiter.recordFailure(UserFactory.normalizeEmail(user.getEmail()), clientIp);
            throw new IncorrectPasswordException();
        }
        attemptLimiter.reset(UserFactory.normalizeEmail(user.getEmail()));
    }

    private void checkNotBlocked(String normalizedEmail, String clientIp) {
        attemptLimiter.blockedFor(normalizedEmail, clientIp).ifPresent(remaining -> {
            throw new TooManyLoginAttemptsException(remaining);
        });
    }

    private IssuedSession openSession(User user) {
        Instant now = clock.instant();
        sessionRepository.deleteExpired(now);
        String token = tokenGenerator.generate();
        sessionRepository.save(new Session(tokenGenerator.hash(token), user.getId(), now.plus(sessionTtl)));
        return new IssuedSession(token, user);
    }
}
