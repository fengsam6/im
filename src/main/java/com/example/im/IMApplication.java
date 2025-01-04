package com.example.im;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.example.im")
public class IMApplication {
    public static void main(String[] args) {
        SpringApplication.run(IMApplication.class, args);
    }
} 