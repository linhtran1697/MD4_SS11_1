package com.example.hrm.model;

import java.math.BigDecimal;

public class Employee {

    private Long id;
    private String fullName;
    private BigDecimal salary;

    public Employee() {
    }

    public Employee(Long id, String fullName, BigDecimal salary) {
        this.id = id;
        this.fullName = fullName;
        this.salary = salary;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public BigDecimal getSalary() {
        return salary;
    }

    public void setSalary(BigDecimal salary) {
        this.salary = salary;
    }
}
