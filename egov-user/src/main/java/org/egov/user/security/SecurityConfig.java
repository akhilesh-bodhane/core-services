package org.egov.user.security;

import org.egov.user.security.oauth2.custom.IdleSessionFilter;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

	private final IdleSessionFilter idleSessionFilter;

	public SecurityConfig(IdleSessionFilter idleSessionFilter) {
		this.idleSessionFilter = idleSessionFilter;
	}

//	@Autowired
//	@Qualifier("customAuthProvider")
//	private AuthenticationProvider customAuthProvider;
//
//	@Autowired
//	@Qualifier("preAuthProvider")
//	private AuthenticationProvider preAuthProvider;
//
//	@Override
//	protected void configure(AuthenticationManagerBuilder auth) throws Exception {
//
//		auth.authenticationProvider(customAuthProvider).authenticationProvider(preAuthProvider);
//	}

	@Override
	protected void configure(HttpSecurity http) throws Exception {
	    http
	    	.addFilterBefore(idleSessionFilter, UsernamePasswordAuthenticationFilter.class)
	        .sessionManagement()
	            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
	        .and()
	        .csrf().disable();
	}
}
