package cl.pedidos360.bff;

import jakarta.servlet.DispatcherType;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Cadena de seguridad del BFF. El decodificador de JWT lo aporta el perfil activo
 * (ver {@link IdpSecurityConfiguration} y {@link LocalSecurityConfiguration}).
 */
@Configuration
class SecurityConfiguration {
  static final String ADMIN_ROLE = "admin";
  static final String SCOPE_ORDERS_READ = "SCOPE_orders.read";
  static final String SCOPE_ORDERS_WRITE = "SCOPE_orders.write";
  static final String SCOPE_EVENTS_READ = "SCOPE_events.read";

  @Bean
  SecurityFilterChain security(HttpSecurity http, JwtAuthenticationConverter converter) throws Exception {
    return http
        .csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            // Sin esto, el contenedor redirige los errores del framework (400, 406, 415...) a /error, que
            // caeria en denyAll y los convertiria en 401/403. Solo aplica al despacho de errores: una
            // peticion directa a /error sigue denegada.
            .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
            .requestMatchers("/actuator/health").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/work-orders").hasAuthority(SCOPE_ORDERS_READ)
            .requestMatchers(HttpMethod.GET, "/api/events").hasAuthority(SCOPE_EVENTS_READ)
            .requestMatchers(HttpMethod.POST, "/api/work-orders")
                .hasAllAuthorities("ROLE_" + ADMIN_ROLE, SCOPE_ORDERS_WRITE)
            .anyRequest().denyAll())
        .oauth2ResourceServer(oauth -> oauth
            .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
            .authenticationEntryPoint(SecurityErrorResponses.unauthorized())
            .accessDeniedHandler(SecurityErrorResponses.forbidden()))
        .build();
  }

  /** Scopes (claims "scope" o "scp", este ultimo el de Entra) como SCOPE_x; roles como ROLE_x. */
  @Bean
  JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
    JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
    roles.setAuthoritiesClaimName("roles");
    roles.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(jwt -> Stream
        .concat(scopes.convert(jwt).stream(), roles.convert(jwt).stream())
        .collect(Collectors.toUnmodifiableSet()));
    return converter;
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origin}") String origin,
      Environment environment) {
    if (origin.isBlank() || origin.contains("*")) {
      throw new IllegalStateException(
          "FRONTEND_ORIGIN debe ser un origen explicito (por ejemplo https://app.example.com), sin comodines");
    }
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of(origin));
    configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    configuration.setAllowCredentials(false);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    if (environment.acceptsProfiles(Profiles.of("local"))) {
      source.registerCorsConfiguration("/dev/**", configuration);
    }
    return source;
  }
}
