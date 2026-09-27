package com.fieldops.identity.domain;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class UserTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    void normalizesProfileWithoutRewritingProviderSpecificEmailAliases() {
        var user = User.create("\u00a0 Alex Rivera \u2003", " Alex.Rivera+Work@Example.COM ");
        assertThat(user.getId().version()).isEqualTo(4);
        assertThat(user.getDisplayName()).isEqualTo("Alex Rivera");
        assertThat(user.getEmail()).isEqualTo("alex.rivera+work@example.com");
        assertThat(validator.validate(user)).isEmpty();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getCreatedAt()).isEqualTo(user.getUpdatedAt());
        var id = user.getId();
        user.disable();
        user.disable();
        assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
        user.reactivate();
        user.rename("New Name");
        assertThat(user.getId()).isEqualTo(id);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getDisplayName()).isEqualTo("New Name");
        assertThat(user.getEmail()).isEqualTo("alex.rivera+work@example.com");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\u00a0\u2003", "Bad\nName", "Bad\u0000Name"})
    void rejectsInvalidNames(String name) {
        assertThatIllegalArgumentException().isThrownBy(() -> User.create(name, "alex@example.com"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "alex rivera@example.com", "áléx@example.com", "\u212a@example.com", "alex@exämple.com", "alex\n@example.com"})
    void rejectsUnsupportedEmailInput(String email) {
        assertThatIllegalArgumentException().isThrownBy(() -> User.create("Alex", email));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-email", "alex@", "@example.com", "alex@@example.com", "a..b@example.com"})
    void standardValidatorRejectsInvalidAddressSyntax(String email) {
        assertThat(validator.validate(User.create("Alex", email))).isNotEmpty();
    }

    @Test
    void enforcesLengthLimitsAndLeavesNameUnchangedAfterInvalidRename() {
        var user = User.create("名".repeat(200), "alex@example.com");
        assertThatIllegalArgumentException().isThrownBy(() -> user.rename("x".repeat(201)));
        assertThat(user.getDisplayName()).isEqualTo("名".repeat(200));
        assertThatIllegalArgumentException().isThrownBy(() -> User.create("Alex", "x".repeat(255)));
    }
}
