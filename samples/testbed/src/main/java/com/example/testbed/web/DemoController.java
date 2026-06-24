package com.example.testbed.web;
import com.example.testbed.domain.Author;
import com.example.testbed.domain.Book;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
public class DemoController {

    // 5.5 MethodArgumentNotValidException: POST an invalid body.
    @PostMapping("/users")
    public String create(@Valid @RequestBody UserDto user) { return "ok"; }

    // 7.2 Jackson infinite recursion on a bidirectional relationship.
    @GetMapping("/recursion")
    public Author recursion() {
        Author a = new Author();
        Book b = new Book();
        b.author = a;
        a.books.add(b);
        return a;
    }
}
