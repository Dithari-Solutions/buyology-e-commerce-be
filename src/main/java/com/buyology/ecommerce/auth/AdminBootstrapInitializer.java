package com.buyology.ecommerce.auth;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.common.utils.PasswordUtils;
import com.buyology.ecommerce.role.domain.UserRole;
import com.buyology.ecommerce.role.domain.UserRoleId;
import com.buyology.ecommerce.role.repository.RoleRepository;
import com.buyology.ecommerce.role.repository.UserRoleRepository;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provisions the first SUPERADMIN account on a fresh database.
 *
 * <p>Public admin self-registration was removed for security — {@code /auth/admin/signup}
 * now requires an existing ADMIN/SUPERADMIN. To break that chicken-and-egg on a clean
 * deployment, set the env vars {@code BOOTSTRAP_ADMIN_EMAIL} and
 * {@code BOOTSTRAP_ADMIN_PASSWORD}; this runner creates the account once (idempotent —
 * it is a no-op if a LOCAL credential with that email already exists, or if the vars
 * are blank). Runs after {@link com.buyology.ecommerce.role.RoleDataInitializer}.
 */
@Component
@Order(100)
public class AdminBootstrapInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapInitializer.class);

    @Value("${app.bootstrap.admin.email:}")
    private String bootstrapEmail;

    @Value("${app.bootstrap.admin.password:}")
    private String bootstrapPassword;

    @Value("${app.bootstrap.admin.first-name:Super}")
    private String firstName;

    @Value("${app.bootstrap.admin.last-name:Admin}")
    private String lastName;

    private final UserRepository userRepository;
    private final AuthCredentialRepository authCredentialRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;

    public AdminBootstrapInitializer(UserRepository userRepository,
                                     AuthCredentialRepository authCredentialRepository,
                                     RoleRepository roleRepository,
                                     UserRoleRepository userRoleRepository) {
        this.userRepository = userRepository;
        this.authCredentialRepository = authCredentialRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (bootstrapEmail == null || bootstrapEmail.isBlank()
                || bootstrapPassword == null || bootstrapPassword.isBlank()) {
            return; // not configured — nothing to do
        }

        String email = bootstrapEmail.trim().toLowerCase();

        if (authCredentialRepository.findByEmailAndProvider(email, "LOCAL").isPresent()) {
            log.info("[BOOTSTRAP] Admin '{}' already exists; skipping bootstrap.", email);
            return;
        }

        Users user = new Users();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setUserType(Users.UserType.ADMIN);
        user.setIsGuest(false);
        user.setStatus("ACTIVE");
        userRepository.save(user);

        AuthCredentials credentials = new AuthCredentials();
        credentials.setUserId(user.getId());
        credentials.setEmail(email);
        credentials.setPasswordHash(PasswordUtils.hashPassword(bootstrapPassword));
        credentials.setProvider("LOCAL");
        credentials.setIsActive(true);
        authCredentialRepository.save(credentials);

        // Grant the full-access SUPERADMIN role if it has been seeded.
        roleRepository.findByName("SUPERADMIN").ifPresent(role -> {
            if (!userRoleRepository.existsByIdUserIdAndIdRoleId(user.getId(), role.getId())) {
                UserRole ur = new UserRole();
                ur.setId(new UserRoleId(user.getId(), role.getId()));
                ur.setUser(user);
                ur.setRole(role);
                userRoleRepository.save(ur);
            }
        });

        log.warn("[BOOTSTRAP] Created bootstrap SUPERADMIN '{}'. Rotate this password after first login.", email);
    }
}
