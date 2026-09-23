package com.AgentSaasAplication.identity.controller;

import com.AgentSaasAplication.common.tenant.CurrentUserResolver;
import com.AgentSaasAplication.identity.domain.User;
import com.AgentSaasAplication.identity.dto.ChangePasswordRequest;
import com.AgentSaasAplication.identity.dto.UpdateProfileRequest;
import com.AgentSaasAplication.identity.service.AuthenticationService;
import com.AgentSaasAplication.identity.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final CurrentUserResolver currentUserResolver;
    private final UserService userService;
    private final AuthenticationService authenticationService;

    public MeController(CurrentUserResolver currentUserResolver,
                         UserService userService, AuthenticationService authenticationService) {
        this.currentUserResolver = currentUserResolver;
        this.userService = userService;
        this.authenticationService = authenticationService;
    }

    @GetMapping
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = currentUserResolver.resolve(jwt);
        User user = userService.getById(userId);
        return toResponse(user);
    }

    @PutMapping
    public Map<String, Object> updateMe(@Valid @RequestBody UpdateProfileRequest request, @AuthenticationPrincipal Jwt jwt) {
        UUID userId = currentUserResolver.resolve(jwt);
        User user = userService.updateProfile(userId, request.displayName());
        return toResponse(user);
    }

    /** Token tabanlı /auth/reset-password'den FARKLI — burada kullanıcı zaten oturum açmış,
     * mevcut şifresini bilerek değiştiriyor. */
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                                @AuthenticationPrincipal Jwt jwt) {
        UUID userId = currentUserResolver.resolve(jwt);
        authenticationService.changePassword(userId, request.currentPassword(), request.newPassword());
        return ResponseEntity.ok().build();
    }

    private Map<String, Object> toResponse(User user) {
        return Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "displayName", user.getDisplayName() == null ? "" : user.getDisplayName()
        );
    }
}
