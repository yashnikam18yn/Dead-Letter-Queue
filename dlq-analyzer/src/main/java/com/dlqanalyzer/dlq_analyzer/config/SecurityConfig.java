package com.dlqanalyzer.dlq_analyzer.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception{
        httpSecurity
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                                //public endpoints
                                .requestMatchers("/actuator/health").permitAll()
                                .requestMatchers("/ws/**").permitAll()
                                 //only admin can replay and discard message
                                .requestMatchers(HttpMethod.POST, "/api/v1/replay/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/messages/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .httpBasic(basic -> basic
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(401);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\": \"Unauthorized\"}");
                        })
                );

        return httpSecurity.build();
    }

    @Bean
    public UserDetailsService userDetailsService(){
        var admin = User.withDefaultPasswordEncoder()
                .username("admin")
                .password("admin")
                .roles("ADMIN", "VIEWER")
                .build();

        var viewer = User.withDefaultPasswordEncoder()
                .username("viewer")
                .password("viewer123")
                .roles("VIEWER")
                .build();

        return new InMemoryUserDetailsManager(admin, viewer);
    }
}
