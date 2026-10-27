package com.valorant.leaderboard;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class LeaderboardSecurity {
 @Bean PasswordEncoder observatoryPasswords(){return new BCryptPasswordEncoder();}
 @Bean UserDetailsService observatoryUsers(JdbcTemplate db,PasswordEncoder encoder,
   @Value("${spring.security.user.name:admin}")String admin,@Value("${spring.security.user.password}")String password){
  String administratorHash=encoder.encode(password);
  return identifier->{
   if(admin.equalsIgnoreCase(identifier.trim()))return User.withUsername(admin).password(administratorHash).roles("ADMIN").build();
   var users=db.query("select username,password_hash from observatory_accounts where username=lower(?) or email=lower(?)",(rs,n)->User.withUsername(rs.getString("username")).password(rs.getString("password_hash")).roles("USER").build(),identifier.trim(),identifier.trim());
   if(users.isEmpty())throw new UsernameNotFoundException("Invalid sign-in");return users.getFirst();
  };
 }
 @Bean SecurityFilterChain leaderboardFilterChain(HttpSecurity http)throws Exception {
  return http.authorizeHttpRequests(auth->auth.requestMatchers(HttpMethod.POST,"/api/leaderboard/collect").hasRole("ADMIN")
   .requestMatchers("/api/match-intelligence/**").authenticated().anyRequest().permitAll())
   .formLogin(form->form.loginPage("/signin.html").loginProcessingUrl("/login").failureUrl("/signin.html?error=1").defaultSuccessUrl("/leaderboard.html",true).permitAll())
   .logout(logout->logout.logoutSuccessUrl("/signin.html?logout=1"))
   .httpBasic(Customizer.withDefaults())
   .exceptionHandling(errors->errors.defaultAuthenticationEntryPointFor((request,response,error)->response.sendError(401,"Sign in required"),request->request.getRequestURI().startsWith("/api/"))).build();
 }
}
