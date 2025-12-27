package com.security;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.service.CustomUserDetailsService;
import com.util.JwtUtil;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private CustomUserDetailsService userDetailsService;

//    @Override
//    protected void doFilterInternal(HttpServletRequest request,
//                                    HttpServletResponse response,
//                                    FilterChain chain)
//            throws ServletException, IOException {
//    	
//        String path = request.getServletPath();
//
//    	
//    	 if (path.equals("/auth/login") || path.startsWith("/h2-console")) {
//    	        chain.doFilter(request, response);
//    	        return;
//    	    }
//
//        String header = request.getHeader("Authorization");
//
//        if (header != null && header.startsWith("Bearer ")
//        	    && SecurityContextHolder.getContext().getAuthentication() == null) {
//
//        	    String token = header.substring(7);
//        	    String username = jwtUtil.extractUsername(token);
//
//        	    UserDetails userDetails =
//        	            userDetailsService.loadUserByUsername(username);
//
//        	    UsernamePasswordAuthenticationToken auth =
//        	            new UsernamePasswordAuthenticationToken(
//        	                    userDetails,
//        	                    null,
//        	                    userDetails.getAuthorities()
//        	            );
//
//        	    SecurityContextHolder.getContext().setAuthentication(auth);
//        	}
//        
//        
//
//        chain.doFilter(request, response);
//    }
    
    
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getServletPath().equals("/auth/login");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String path = request.getServletPath();
        
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }


        if (path.startsWith("/auth") ||
            path.endsWith(".html") ||
            path.startsWith("/css") ||
            path.startsWith("/js")) {
        	chain.doFilter(request, response);
            return;
        }


        // 🔥 LOGIN API & H2 SKIP
        if (path.equals("/auth/login") || path.startsWith("/h2-console")) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            String token = header.substring(7);
            String username = jwtUtil.extractUsername(token);

            UserDetails userDetails =
                    userDetailsService.loadUserByUsername(username);

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        chain.doFilter(request, response);
    }

}
