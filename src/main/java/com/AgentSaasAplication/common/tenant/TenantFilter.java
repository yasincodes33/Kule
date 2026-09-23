package com.AgentSaasAplication.common.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class TenantFilter extends OncePerRequestFilter {

	private static final String ORG_HEADER = "X-Organization-Id";

	private final TenantAccessValidator tenantAccessValidator;
	private final CurrentUserResolver currentUserResolver;

	public TenantFilter(TenantAccessValidator tenantAccessValidator,
	                     CurrentUserResolver currentUserResolver) {
		this.tenantAccessValidator = tenantAccessValidator;
		this.currentUserResolver = currentUserResolver;
	}

	@Override
	protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
	    String path = request.getRequestURI();
	    return (path.equals("/api/v1/organizations") && "POST".equals(request.getMethod()))
	            || path.startsWith("/api/v1/invitations")
	            || path.equals("/api/v1/me/organizations")
	            || path.equals("/api/v1/me/invitations")
	            || path.equals("/api/v1/me")
	            // Profil (ad-soyad) ve şifre değişikliği org bağlamından bağımsız —
	            // kullanıcının hiç organizasyonu olmasa bile kendi hesabını yönetebilmesi gerekir.
	            || path.equals("/api/v1/me/change-password")
	            || path.equals("/ws/agent-bridge")
	            // Bu uç noktanın kendi org/yetki doğrulaması TaskLogStreamAuthInterceptor'da
	            // yapılıyor (query param tabanlı — tarayıcı WS handshake'ine X-Organization-Id
	            // header'ı koyamaz), TenantFilter'ın header zorunluluğu buraya uygulanmamalı.
	            || path.startsWith("/ws/tasks/")
		            // Aynı gerekçe: runner terminal WS handshake'i de bilet tabanlı, org/yetki
		            // doğrulaması RunnerTerminalAuthInterceptor'da yapılıyor — bu istisna
		            // eklenmeden önce her bağlantı denemesi burada "X-Organization-Id header
		            // eksik" ile 400'e düşüyordu (DispatcherServlet'e hiç ulaşmadan).
		            || path.startsWith("/ws/runners/")
	            // register/login/refresh henüz hiçbir
	            // organizasyona ait DEĞİL (kimlik doğrulamanın kendisi bu uç noktalarda yapılıyor)
	            // — SecurityContext'te henüz bir Jwt principal'ı bile yok, currentUserId() burada
	            // çalışırsa ClassCastException atardı. Bu istisna eklenmeden önce her istek
	            // "X-Organization-Id header eksik" ile 400'e düşüyordu (response.sendError, Spring
	            // MVC/Security exception handling'ini tamamen atlayarak) — AuthController'ın
	            // kendisi hiç çağrılmıyordu bile.
	            || path.startsWith("/api/v1/auth/");
	}
	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request,
	                                 @NonNull HttpServletResponse response,
	                                 @NonNull FilterChain filterChain)
			throws ServletException, IOException {
		try {
			String orgHeader = request.getHeader(ORG_HEADER);
			if (orgHeader == null) {
				response.sendError(HttpServletResponse.SC_BAD_REQUEST, ORG_HEADER + " header eksik");
				return;
			}

			UUID organizationId = UUID.fromString(orgHeader);
			UUID userId = currentUserId();

			boolean hasAccess = tenantAccessValidator.hasActiveAccess(organizationId, userId);

			if (!hasAccess) {
				response.sendError(HttpServletResponse.SC_FORBIDDEN, "Bu organizasyona erişim yetkiniz yok");
				return;
			}

			TenantContext.set(organizationId);
			filterChain.doFilter(request, response);
		} finally {
			TenantContext.clear();
		}
	}

	private UUID currentUserId() {
		Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		return currentUserResolver.resolve(jwt);
	}
}