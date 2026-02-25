package com.studencollabfin.server;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ServerApplication {

	@PostConstruct
	public void init() {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
		System.out.println("Current Server Time: " + LocalDateTime.now());
	}

	public static void main(String[] args) {
		SpringApplication.run(ServerApplication.class, args);
	}

}
