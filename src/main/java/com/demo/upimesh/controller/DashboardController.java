package com.demo.upimesh.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class DashboardController {

    @GetMapping("/")
    public String home() {
        return "dashboard";
    }
    @GetMapping("/test")
@ResponseBody
public String test() {
    return "Hello";
}
}
