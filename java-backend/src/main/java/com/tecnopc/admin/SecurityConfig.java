package com.tecnopc.admin;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
@Configuration
public class SecurityConfig {
 @Bean RuntimeCapacity runtimeCapacity(){return new RuntimeCapacity();}
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(12);}
 @Bean UserDetailsService users(JdbcTemplate jdbc){return username->{
  var rows=jdbc.queryForList("SELECT usuario,password_hash,rol,activo FROM usuarios WHERE usuario=?",username);
  if(rows.isEmpty())throw new UsernameNotFoundException("Usuario no encontrado");
  var row=rows.get(0);
  return User.withUsername((String)row.get("usuario")).password((String)row.get("password_hash"))
   .roles((String)row.get("rol")).disabled(!((Boolean)row.get("activo"))).build();
 };}
 @Bean SecurityFilterChain security(HttpSecurity http,RuntimeCapacity capacity) throws Exception {
  var loginAttempts=new LoginAttemptFilter();
  var unauthorized=new org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint();
  unauthorized.setRealmName("TecnoPC");
  return http.csrf(csrf->csrf.disable())
   .addFilterBefore(new ApiCapacityFilter(capacity), org.springframework.security.web.context.SecurityContextHolderFilter.class)
   .headers(headers->headers
       .httpStrictTransportSecurity(hsts->hsts
           .maxAgeInSeconds(31536000)
           .includeSubDomains(false)
           .preload(false))
       .contentSecurityPolicy(csp->csp.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'"))
       .referrerPolicy(policy->policy.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
       .permissionsPolicy(policy->policy.policy("camera=(), microphone=(), geolocation=()")))
   .addFilterBefore(new PublicRequestFilter(), BasicAuthenticationFilter.class)
   .addFilterBefore(new DesktopRequestFilter(), BasicAuthenticationFilter.class)
   .addFilterBefore(loginAttempts, BasicAuthenticationFilter.class)
   .addFilterAfter(loginAttempts.authenticationCompletedFilter(), BasicAuthenticationFilter.class)
   .sessionManagement(session->session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .requestCache(cache->cache.disable())
   .authorizeHttpRequests(auth->auth
   .requestMatchers(HttpMethod.GET,"/","/index.html","/css/**","/js/**","/images/**","/favicon.ico","/api/public/productos","/healthz","/livez").permitAll()
   .requestMatchers(HttpMethod.POST,"/api/public/solicitudes","/api/public/chatbot","/api/public/seguimiento","/api/public/pagos","/api/public/v2/pagos","/api/public/pagos/recibo","/api/public/pagos/webhook").permitAll()
   .requestMatchers("/api/admin/**").hasRole("ADMIN")
   .requestMatchers("/api/vendedor/**").hasAnyRole("VENDEDOR","ADMIN")
   .requestMatchers("/api/tecnico/**").hasAnyRole("TECNICO","ADMIN")
   .anyRequest().denyAll()).httpBasic(basic->basic.authenticationEntryPoint((request,response,error)->{
       if(error instanceof org.springframework.security.authentication.InternalAuthenticationServiceException){
           response.setStatus(503);response.setHeader("Retry-After","5");response.setHeader("Cache-Control","no-store");
           response.setContentType("application/json;charset=UTF-8");
           response.getWriter().write("{\"mensaje\":\"El servicio de acceso no está disponible temporalmente. Intenta más tarde.\"}");
       }else unauthorized.commence(request,response,error);
   })).build();
 }
}
