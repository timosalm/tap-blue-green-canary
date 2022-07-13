package com.example.helloworld;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloWorldResource {

    @GetMapping
    public ResponseEntity<String> fetchGreeting() {
        return ResponseEntity.ok("Hello Blue!");
    }
}
