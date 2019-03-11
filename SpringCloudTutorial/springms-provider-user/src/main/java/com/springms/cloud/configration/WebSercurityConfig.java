package com.springms.cloud.configration;

import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfiguration;

public class WebSercurityConfig extends WebSecurityConfiguration{

        protected void configure(HttpSecurity security) throws Exception {
            security.authorizeRequests()
                    .antMatchers("/","/home").permitAll()
                    .anyRequest().authenticated()
                    .and()
                    .formLogin()
                    .loginPage("/login")
                    .permitAll()
                    .and()
                    .logout()
                    .permitAll();
        }

        public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
            auth.inMemoryAuthentication().withUser("user").password("password").roles("USERS");
        }
}
