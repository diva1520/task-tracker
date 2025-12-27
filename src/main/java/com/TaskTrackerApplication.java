package com;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootApplication
public class TaskTrackerApplication {

	public static void main(String[] args) {
		    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
		    System.out.println(encoder.encode("admin123"));
		SpringApplication.run(TaskTrackerApplication.class, args);
	}

}
