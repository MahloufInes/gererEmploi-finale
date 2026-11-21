
package com.edt.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.edt.security.JwtAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(
                List.of("http://localhost:4200")
        );

        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        );

        configuration.setAllowedHeaders(
                List.of("Authorization", "Content-Type")
        );

        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http)
            throws Exception {

        http
                .csrf(csrf -> csrf.disable())

                .cors(cors -> {})

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(auth -> auth

                        // CORS
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Authentication
                        .requestMatchers("/api/auth/**", "/error").permitAll()

                        // Student registration
                        .requestMatchers(HttpMethod.POST, "/api/etudiants")
                        .permitAll()

                        // Administrators: view all students
                        .requestMatchers(HttpMethod.GET, "/api/etudiants")
                        .hasAnyRole(
                                "ADMINISTRATEUR",
                                "SOUS_ADMINISTRATEUR"
                        )

                        // Professors: personal timetable
                        .requestMatchers(HttpMethod.GET, "/api/seances/mon-emploi")
                        .hasRole("PROFESSEUR")

                        // Subject preferences
                        .requestMatchers("/api/preferences-matieres/**")
                        .hasAnyRole(
                                "ADMINISTRATEUR",
                                "SOUS_ADMINISTRATEUR",
                                "PROFESSEUR"
                        )

                        // Professors: save availability
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/disponibilites/professeur/**"
                        )
                        .hasRole("PROFESSEUR")

                        // Students: consult their own timetable
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/consultation/mon-emploi/**"
                        )
                        .hasRole("ETUDIANT")

                        // Administrators: old consultation by group
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/consultation/groupe/**"
                        )
                        .hasAnyRole(
                                "ADMINISTRATEUR",
                                "SOUS_ADMINISTRATEUR"
                        )

                        // Administrators: other POST requests
                        .requestMatchers(HttpMethod.POST, "/api/**")
                        .hasAnyRole(
                                "ADMINISTRATEUR",
                                "SOUS_ADMINISTRATEUR"
                        )

                        // Administrators: PUT
                        .requestMatchers(HttpMethod.PUT, "/api/**")
                        .hasAnyRole(
                                "ADMINISTRATEUR",
                                "SOUS_ADMINISTRATEUR"
                        )

                        // Administrators: PATCH
                        .requestMatchers(HttpMethod.PATCH, "/api/**")
                        .hasAnyRole(
                                "ADMINISTRATEUR",
                                "SOUS_ADMINISTRATEUR"
                        )

                        // Administrators: DELETE
                        .requestMatchers(HttpMethod.DELETE, "/api/**")
                        .hasAnyRole(
                                "ADMINISTRATEUR",
                                "SOUS_ADMINISTRATEUR"
                        )

                        // Other endpoints
                        .anyRequest().authenticated()
                )

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)
                        )
                )

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}
