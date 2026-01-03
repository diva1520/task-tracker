package com.util;

import java.util.Date;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import com.security.CustomUserDetails;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;

@Component
public class JwtUtil {

	private static final String SECRET = "myjwtsecretkeyformyapplication123456";

	private boolean isTokenExpired(String token) {
		return extractExpiration(token).before(new Date());
	}

	private Date extractExpiration(String token) {
		return getClaims(token).getExpiration();
	}

	
	private Key getSigningKey() {
		return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
	}

	public boolean validateToken(String token, UserDetails userDetails) {
		String username = extractUsername(token);
		return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
	}
	
	private Claims extractAllClaims(String token) {
	    return Jwts.parserBuilder()
	            .setSigningKey(getSigningKey())
	            .build()
	            .parseClaimsJws(token)
	            .getBody();
	}


	public String generateToken(CustomUserDetails user) {
		return Jwts.builder().setSubject(user.getUsername())
				.claim("role", user.getAuthorities().iterator().next().getAuthority()).claim("userId", user.getUserId())
				.setIssuedAt(new Date()).setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60))
				.signWith(getSigningKey(), SignatureAlgorithm.HS256).compact();
	}

	public String extractUsername(String token) {
		return getClaims(token).getSubject();
	}

	public Long extractUserId(String token) {
		return getClaims(token).get("userId", Long.class);
	}

	
	
	private Claims getClaims(String token) {
		return Jwts.parserBuilder().setSigningKey(SECRET.getBytes()).build().parseClaimsJws(token).getBody();
	}

	public String extractRole(String token) {
	    Claims claims = extractAllClaims(token);
	    return claims.get("role", String.class);
	}

}
