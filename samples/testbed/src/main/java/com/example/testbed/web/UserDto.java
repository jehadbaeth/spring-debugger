package com.example.testbed.web;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
public class UserDto {
    @NotNull(message = "name must not be null")
    public String name;
    @Size(min = 8, message = "password must be at least 8 characters")
    public String password;
}
