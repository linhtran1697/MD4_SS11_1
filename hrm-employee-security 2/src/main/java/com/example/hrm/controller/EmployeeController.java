package com.example.hrm.controller;

import com.example.hrm.model.Employee;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/employees")
public class EmployeeController {

    @GetMapping
    public List<Employee> getAllEmployees() {
        return List.of(
                new Employee(1L, "Nguyễn Văn An", new BigDecimal("18000000")),
                new Employee(2L, "Trần Thị Bình", new BigDecimal("22000000")),
                new Employee(3L, "Lê Văn Cường", new BigDecimal("25000000"))
        );
    }
}
