package com.example.app;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/")
    public String hello() {
        return "Hello from Jenkins CI/CD pipeline!";
    }

    @GetMapping("/health")
    public String health() {
        return "UP";
    }
}
