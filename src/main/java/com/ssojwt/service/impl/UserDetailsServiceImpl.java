package com.ssojwt.service.impl;

import com.ssojwt.model.factory.SpringUserDetailsFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.ssojwt.domain.entity.User;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    //@Autowired
    //private UserRepository userRepository;

    // Long id, String username, String password, String email, Date lastPasswordReset, String authorities)

    public final static Map<String, User> USER_MAP = new HashMap<String, User>() {
        {
            put("admin", new User(0l, "admin", "admin", "admin@ssojwt.com", new Date(), "ADMIN,ROOT"));
            put("anonymous", new User(1l, "anonymous", "anonymous", "email", new Date(), "ANONYM"));
            put("user", new User(2l, "user", "user", "user@ssojwt.com", new Date(), "USER"));
        }
    };

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        //User user = this.userRepository.findByUsername(username);
        User user = USER_MAP.get(username);

        if (user == null) {
            throw new UsernameNotFoundException(String.format("No user found with username '%s'.", username));
        } else {
            return SpringUserDetailsFactory.create(user);
        }
    }

}
