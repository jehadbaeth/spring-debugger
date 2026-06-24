package com.example.testbed.domain;
import java.util.ArrayList;
import java.util.List;
// Bidirectional reference with no @JsonIgnore/@JsonManagedReference -> Jackson infinite recursion.
public class Author {
    public String name = "Ada";
    public List<Book> books = new ArrayList<>();
}
