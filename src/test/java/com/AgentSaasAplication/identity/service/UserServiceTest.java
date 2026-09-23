package com.AgentSaasAplication.identity.service;

import com.AgentSaasAplication.common.exceptions.NotFoundException;
import com.AgentSaasAplication.identity.domain.User;
import com.AgentSaasAplication.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository);
    }

    private User savedUser() {
        User user = User.register("dev@test.com", "$2a$12$test", "Eski İsim");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    @Test
    void getById_var_olan_kullaniciyi_dondurur() {
        User user = savedUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThat(service.getById(user.getId())).isEqualTo(user);
    }

    @Test
    void getById_kullanici_yoksa_notfound_firlatir() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(userId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateProfile_displayname_gunceller() {
        User user = savedUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        User result = service.updateProfile(user.getId(), "Yeni İsim");

        assertThat(result.getDisplayName()).isEqualTo("Yeni İsim");
    }

    @Test
    void updateProfile_kullanici_yoksa_notfound_firlatir() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateProfile(userId, "Yeni İsim"))
                .isInstanceOf(NotFoundException.class);
    }
}
