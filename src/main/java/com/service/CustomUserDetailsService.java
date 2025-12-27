package com.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.entity.User;
import com.repo.UserRepository;
import com.security.CustomUserDetails;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository repo;

    @Override
    public UserDetails loadUserByUsername(String username) {
        Optional<User> u = repo.findByUsername(username);
        User user;
        if(!u.isPresent()) {
			throw new UsernameNotFoundException("User not found");
		}else {
		    user = u.get();
			return new CustomUserDetails(user);
		}

//        return new CustomUserDetails(
//        		user.orElseThrow(() -> new UsernameNotFoundException("User not found")));
        
    }
    
    

}
