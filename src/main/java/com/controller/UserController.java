package com.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dto.UserProfileResponse;
import com.security.CustomUserDetails;

@RestController
@RequestMapping("/user")
@CrossOrigin(origins = "*")
public class UserController {

    @GetMapping("/profile")
    public UserProfileResponse profile(Authentication authentication) {

        CustomUserDetails user =
                (CustomUserDetails) authentication.getPrincipal();

        UserProfileResponse response = new UserProfileResponse();
        response.setUserId(user.getUserId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setRole(
            user.getAuthorities().iterator().next().getAuthority()
        );

        return response;
    }
}
