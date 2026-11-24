
package com.edt.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.edt.security.JwtAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
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

                        // Professeurs : consulter leur profil
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/professeurs/mon-profil"
                        ).hasRole("PROFESSEUR")

                        // Étudiants : consulter leur profil
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/etudiants/mon-profil"
                        ).hasRole("ETUDIANT")

                        // Inscription étudiant
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/etudiants"
                        ).permitAll()

                        // Administrateurs : consulter les étudiants
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/etudiants"
                        ).hasAnyRole(
                                "ADMINISTRATEUR",
                                "SOUS_ADMINISTRATEUR"
                        )

                        // Professeurs : consulter leur emploi du temps
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/seances/mon-prof-emploi"
                        ).hasRole("PROFESSEUR")

                        // Préférences des matières
                        .requestMatchers("/api/preferences-matieres/**")
                        .hasAnyRole(
                                "ADMINISTRATEUR",
                                "SOUS_ADMINISTRATEUR",
                                "PROFESSEUR"
                        )

                        // Préférences des groupes : professeur
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/preferences-groupes"
                        ).hasRole("PROFESSEUR")

                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/preferences-groupes"
                        ).hasRole("PROFESSEUR")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/preferences-groupes/**"
                        ).hasAnyRole(
                                "PROFESSEUR",
                                "ADMINISTRATEUR",
                                "SOUS_ADMINISTRATEUR"
                        )

                        // Professeurs : enregistrer leurs disponibilités
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/disponibilites/professeur/**"
                        ).hasRole("PROFESSEUR")

                        // Étudiants : consulter leur emploi du temps
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/consultation/mon-emploi/**"
                        ).hasRole("ETUDIANT")

                        // Administrateurs : consulter les emplois par groupe
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/consultation/groupe/**"
                        ).hasAnyRole(
                                "ADMINISTRATEUR",
                                "SOUS_ADMINISTRATEUR"
                        )

                        // Administrateurs : autres requêtes POST
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/**"
                        ).hasAnyRole(
                                "ADMINISTRATEUR",
                                "SOUS_ADMINISTRATEUR"
                        )

                        // Administrateurs : requêtes PUT
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/**"
                        ).hasAnyRole(
                                "ADMINISTRATEUR",
                                "SOUS_ADMINISTRATEUR"
                        )

                        // Administrateurs : requêtes PATCH
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/**"
                        ).hasAnyRole(
                                "ADMINISTRATEUR",
                                "SOUS_ADMINISTRATEUR"
                        )

                        // Administrateurs : requêtes DELETE
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/**"
                        ).hasAnyRole(
                                "ADMINISTRATEUR",
                                "SOUS_ADMINISTRATEUR"
                        )

                        // Autres endpoints
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
